package com.childsafety.os.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.childsafety.os.ui.MainActivity
import com.childsafety.os.policy.AgeGroup
import com.childsafety.os.policy.BlockedAppsConfig

/**
 * AccessibilityService to lock blocked apps system-wide.
 * 
 * Blocks:
 * - System apps (Settings, Play Store) - prevents uninstallation
 * - Social media (Instagram, TikTok, Snapchat) - for children
 * - Dating apps (Tinder, Bumble) - for children and teens
 * - Custom apps added by parent
 * 
 * Supports:
 * - Bypass Mode: Allows temporary access to Settings for parents (30 seconds)
 */
class AppLockService : AccessibilityService() {

    companion object {
        private const val TAG = "AppLockService"
        private const val PREFS_NAME = "child_safety_prefs"
        private const val KEY_AGE_GROUP = "age_group"
        
        // Bypass Mode Duration (30 seconds for parent to disable service)
        private const val BYPASS_DURATION_MS = 30_000L
        
        // Static bypass mode flag - accessible from LockScreen
        @Volatile
        var bypassModeActive: Boolean = false
            private set
        
        private var bypassExpiryTime: Long = 0
        
        /**
         * Enable bypass mode for 30 seconds.
         * Called when parent enters correct PIN on lock screen.
         */
        fun enableBypassMode() {
            bypassModeActive = true
            bypassExpiryTime = System.currentTimeMillis() + BYPASS_DURATION_MS
            Log.i(TAG, "Bypass mode ENABLED for 30 seconds - parent can access Settings")
            
            // Auto-disable after 30 seconds
            Handler(Looper.getMainLooper()).postDelayed({
                disableBypassMode()
            }, BYPASS_DURATION_MS)
        }
        
        /**
         * Disable bypass mode (auto-called after 30 seconds or manually)
         */
        fun disableBypassMode() {
            bypassModeActive = false
            bypassExpiryTime = 0
            Log.i(TAG, "Bypass mode DISABLED - Settings lock resumed")
        }
        
        /**
         * Check if bypass mode is active and not expired
         */
        fun isBypassActive(): Boolean {
            if (!bypassModeActive) return false
            if (System.currentTimeMillis() > bypassExpiryTime) {
                bypassModeActive = false
                return false
            }
            return true
        }
    }

    private var currentAgeGroup: AgeGroup = AgeGroup.CHILD

    override fun onServiceConnected() {
        super.onServiceConnected()
        
        // Initialize BlockedAppsConfig
        BlockedAppsConfig.init(this)
        
        // Load current age group from preferences
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val ageGroupName = prefs.getString(KEY_AGE_GROUP, "CHILD") ?: "CHILD"
        currentAgeGroup = try {
            AgeGroup.valueOf(ageGroupName)
        } catch (e: Exception) {
            AgeGroup.CHILD
        }
        
        Log.i(TAG, "AppLockService connected. Age group: $currentAgeGroup")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // CHECK BYPASS MODE FIRST - if active, skip all blocking
        if (isBypassActive()) {
            Log.d(TAG, "Bypass mode active - allowing access")
            return
        }
        
        // 1. Standard Package Blocking
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            
            // Skip our own app
            if (packageName == "com.childsafety.os") return
            
            // Check if app should be blocked
            if (BlockedAppsConfig.isBlocked(packageName, currentAgeGroup)) {
                blockApp(packageName)
            }
        }
        
        // 2. Smart Settings Lock (Hierarchy Inspection)
        // Delegate to SettingsGuard for professional-grade protection
        val rootNode = rootInActiveWindow
        val (shouldBlock, reason) = SettingsGuard.shouldBlock(event, rootNode)
        
        // Important: Recycle the node to prevent memory leaks!
        rootNode?.recycle()

        if (shouldBlock) {
             blockApp(event.packageName?.toString() ?: "com.android.settings", reason)
        }
    }

    private fun blockApp(packageName: String, customReason: String? = null) {
        // Double-check bypass mode before blocking
        if (isBypassActive()) {
            Log.d(TAG, "Bypass active - skipping block for: $packageName")
            return
        }
        
        try {
            val appName = BlockedAppsConfig.getAppName(packageName)
            val reason = customReason ?: BlockedAppsConfig.getBlockReason(packageName, currentAgeGroup)
            
            Log.w(TAG, "BLOCKED: $appName ($packageName) - $reason")
            
            // Log to Firebase
            try {
                com.childsafety.os.cloud.FirebaseManager.logAppLock(
                    appPackage = packageName,
                    appName = appName,
                    reason = reason
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log app lock", e)
            }
            
            // SAFE APPROACH: Go to Home first, then launch our lock screen
            // This prevents conflicts with the foreground app
            performGlobalAction(GLOBAL_ACTION_HOME)
            
            // Small delay to ensure home is shown before launching lock screen
            Handler(mainLooper).postDelayed({
                try {
                    val lockIntent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        action = "ACTION_LOCK_APP"
                        putExtra("blocked_app_name", appName)
                        putExtra("blocked_app_package", packageName)
                        putExtra("block_reason", reason)
                    }
                    startActivity(lockIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to launch lock screen", e)
                }
            }, 100)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in blockApp", e)
            // Fallback: Just go home
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "AppLockService interrupted")
    }

    /**
     * Update the age group (called when parent changes settings)
     */
    fun updateAgeGroup(ageGroup: AgeGroup) {
        currentAgeGroup = ageGroup
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_AGE_GROUP, ageGroup.name)
            .apply()
        Log.i(TAG, "Age group updated to: $ageGroup")
    }
}
