package com.childsafety.os.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.content.Intent
import android.util.Log
import com.childsafety.os.ui.MainActivity

/**
 * Service to lock sensitive apps (Settings, Play Store) to prevent uninstallation.
 */
class AppLockService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            val className = event.className?.toString() ?: ""
            
            Log.d("AppLockService", "App opened: $packageName / $className")

            // Critical Apps to Lock
            if (packageName == "com.android.settings" || 
                packageName == "com.android.vending") { // Play Store
                
                val appName = if (packageName == "com.android.settings") "Settings" else "Play Store"
                
                // Log app lock event to Firebase
                com.childsafety.os.cloud.FirebaseManager.logAppLock(
                    appPackage = packageName,
                    appName = appName,
                    reason = "Child attempted to access $appName (Protected app)"
                )
                
                // Launch PIN Lock Screen (MainActivity with LOCK intent)
                val lockIntent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    action = "ACTION_LOCK_SCREEN"
                }
                startActivity(lockIntent)
            }
        }
    }

    override fun onInterrupt() {
        // Required method
    }
}
