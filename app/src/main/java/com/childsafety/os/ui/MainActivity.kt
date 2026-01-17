package com.childsafety.os.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.childsafety.os.browser.SafeBrowserActivity
import com.childsafety.os.policy.AgeGroup
import com.childsafety.os.ui.theme.ChildSafetyOSTheme
import com.childsafety.os.vpn.SafeVpnService
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

/**
 * Main launcher activity.
 * 
 * Provides:
 * - VPN protection status and control
 * - Age group selection
 * - Safe Browser launcher
 * - Protection status dashboard
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var vpnEnabled by mutableStateOf(false)
    private var selectedAgeGroup by mutableStateOf(AgeGroup.CHILD)

    private val adminComponentName by lazy {
        android.content.ComponentName(this, com.childsafety.os.admin.ChildSafetyAdminReceiver::class.java)
    }
    
    // VPN permission launcher
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnService()
        } else {
            Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // Device Admin permission launcher
    private val deviceAdminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "Uninstall Protection Enabled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Protection setup cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display for proper status bar and notch handling
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Initialize image cache for instant recognition
        com.childsafety.os.cache.ImageHashCache.init(this)
        
        // Check if launched as Lock Screen
        val isLockMode = intent?.action == "ACTION_LOCK_SCREEN" || intent?.action == "ACTION_LOCK_APP"

        setContent {
            ChildSafetyOSTheme {
                
                // If in Lock Mode, show full screen PIN
                if (isLockMode) {
                    LockScreen(onUnlock = {
                        // Go home effectively "minimizing" our app and the settings app under it
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(homeIntent)
                        finish()
                    })
                    return@ChildSafetyOSTheme
                }
            
                var showPinDialog by remember { mutableStateOf(false) }
                var pinError by remember { mutableStateOf<String?>(null) }
                var pendingAction by remember { mutableStateOf<String?>(null) } // "DISABLE_ADMIN", "DISABLE_VPN", "CHANGE_AGE_MODE"
                var pendingAgeGroup by remember { mutableStateOf<AgeGroup?>(null) } // For age mode changes
                
                // Track admin status
                val devicePolicyManager = getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                var isAdminActive by remember { mutableStateOf(devicePolicyManager.isAdminActive(adminComponentName)) }
                
                // Track Accessibility (App Lock) status
                val isAccessibilityActive by remember { 
                    derivedStateOf { isAccessibilityServiceEnabled(this, com.childsafety.os.service.AppLockService::class.java) } 
                }

                // Track Usage Access status
                var isUsageActive by remember { mutableStateOf(com.childsafety.os.manager.UsageManager.hasPermission(this)) }

                // [NEW] Check if App Tour is needed
                val sharedPrefs = remember { getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
                var showTour by remember { mutableStateOf(!sharedPrefs.getBoolean("tour_completed", false)) }

                if (showTour) {
                    AppTourScreen(onFinish = {
                        sharedPrefs.edit().putBoolean("tour_completed", true).apply()
                        showTour = false
                    })
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                    ModernMainScreen(
                        vpnEnabled = vpnEnabled,
                        isAdminActive = isAdminActive,
                        isAccessibilityActive = isAccessibilityActive,
                        selectedAgeGroup = selectedAgeGroup,
                        isUsageActive = isUsageActive,
                        onRequestUsageAccess = {
                             com.childsafety.os.manager.UsageManager.requestPermission(this@MainActivity)
                             Toast.makeText(this@MainActivity, "Find 'ChildSafetyOS' and allow usage tracking", Toast.LENGTH_LONG).show()
                        },
                        onVpnToggle = { 
                            if (vpnEnabled) {
                                // Require PIN to disable VPN
                                pendingAction = "DISABLE_VPN"
                                showPinDialog = true
                            } else {
                                toggleVpn() 
                            }
                        },
                        onAdminToggle = { 
                            if (isAdminActive) {
                                // Request PIN to disable
                                pendingAction = "DISABLE_ADMIN"
                                showPinDialog = true
                            } else {
                                // Enable Admin
                                val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                    putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponentName)
                                    putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Protects the app from unauthorized removal.")
                                }
                                deviceAdminLauncher.launch(intent)
                            }
                        },
                        onAccessToggle = {
                             val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                             startActivity(intent)
                             Toast.makeText(this@MainActivity, "Find 'Child Safety OS' and enable it.", Toast.LENGTH_LONG).show()
                        },
                        onOpenBrowser = { openSafeBrowser() },
                        onAgeGroupChange = { newAgeGroup ->
                            // Require PIN for ANY age mode change (parent must authorize)
                            pendingAgeGroup = newAgeGroup
                            pendingAction = "CHANGE_AGE_MODE"
                            showPinDialog = true
                        },
                        onRequestDataDeletion = {
                            // Open email client to request data deletion
                            val deviceId = com.childsafety.os.ChildSafetyApp.appDeviceId
                            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                data = android.net.Uri.parse("mailto:")
                                putExtra(Intent.EXTRA_EMAIL, arrayOf("rjii89143@gmail.com"))
                                putExtra(Intent.EXTRA_SUBJECT, "[DPDP] Data Deletion Request - Device: $deviceId")
                                putExtra(Intent.EXTRA_TEXT, """
                                    Data Deletion Request (DPDP Act Compliance)
                                    
                                    Device ID: $deviceId
                                    Request Type: Delete All My Data
                                    
                                    I hereby request the deletion of all data associated with the above Device ID as per the Digital Personal Data Protection Act.
                                    
                                    Please confirm once the data has been deleted.
                                    
                                    Thank you.
                                """.trimIndent())
                            }
                            try {
                                startActivity(Intent.createChooser(emailIntent, "Send deletion request via..."))
                            } catch (e: Exception) {
                                Toast.makeText(this@MainActivity, "No email app found", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    
                    if (showPinDialog) {
                        PinDialog(
                            onDismiss = { 
                                showPinDialog = false 
                                pinError = null
                                pendingAction = null
                                pendingAgeGroup = null
                            },
                            onPinEntered = { pin ->
                                if (pin == "1234") { // Hardcoded Parent PIN
                                    when (pendingAction) {
                                        "DISABLE_ADMIN" -> {
                                            // Remove Admin
                                            devicePolicyManager.removeActiveAdmin(adminComponentName)
                                            isAdminActive = false
                                            Toast.makeText(this@MainActivity, "Admin Protection Disabled by Parent", Toast.LENGTH_SHORT).show()
                                            // Log to Firebase
                                            com.childsafety.os.cloud.FirebaseManager.logSettingDisabledByParent("ADMIN_PROTECTION")
                                        }
                                        "DISABLE_VPN" -> {
                                            // Stop VPN
                                            stopVpnService()
                                            Toast.makeText(this@MainActivity, "VPN Protection Disabled by Parent", Toast.LENGTH_SHORT).show()
                                            // Log to Firebase
                                            com.childsafety.os.cloud.FirebaseManager.logSettingDisabledByParent("VPN_PROTECTION")
                                        }
                                        "CHANGE_AGE_MODE" -> {
                                            // Change age mode after parent verification
                                            pendingAgeGroup?.let { newAge ->
                                                selectedAgeGroup = newAge
                                                Toast.makeText(this@MainActivity, "Age mode changed to: ${newAge.name}", Toast.LENGTH_SHORT).show()
                                                // Log to Firebase
                                                com.childsafety.os.cloud.FirebaseManager.logAgeModeChange(newAge.name)
                                            }
                                        }
                                    }
                                    showPinDialog = false
                                    pinError = null
                                    pendingAction = null
                                    pendingAgeGroup = null
                                } else {
                                    pinError = "Incorrect PIN"
                                }
                            },
                            error = pinError
                        )
                    }
                }
            } // End of else block
                
                // Refresh admin status on resume (lifecycle effect)
                DisposableEffect(Unit) {
                    val listener = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                            val adminStatus = devicePolicyManager.isAdminActive(adminComponentName)
                            val accessStatus = isAccessibilityServiceEnabled(this@MainActivity, com.childsafety.os.service.AppLockService::class.java)
                            val usageStatus = com.childsafety.os.manager.UsageManager.hasPermission(this@MainActivity)
                            
                            isAdminActive = adminStatus
                            isUsageActive = usageStatus
                            // Force Recomposition for Accessibility is handled by derivedStateOf but we need to verify sync
                            
                            // SYNC TO FIREBASE
                            com.childsafety.os.cloud.FirebaseManager.updateDeviceStatus(
                                adminEnabled = adminStatus,
                                settingsLockEnabled = accessStatus
                            )
                        }
                    }
                    lifecycle.addObserver(listener)
                    onDispose { lifecycle.removeObserver(listener) }
                }
            }
        }

        Log.i(TAG, "MainActivity created")
    }
    
    // Helper to check service status
    private fun isAccessibilityServiceEnabled(context: android.content.Context, service: Class<out android.accessibilityservice.AccessibilityService>): Boolean {
        val am = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (enabledService in enabledServices) {
            val enabledServiceInfo = enabledService.resolveInfo.serviceInfo
            if (enabledServiceInfo.packageName == context.packageName && enabledServiceInfo.name == service.name)
                return true
        }
        return false
    }

    private fun toggleVpn() {
        if (vpnEnabled) {
            stopVpnService()
        } else {
            requestVpnPermission()
        }
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            // Already have permission
            startVpnService()
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, SafeVpnService::class.java).apply {
            action = SafeVpnService.ACTION_START
        }
        startService(intent)
        vpnEnabled = true
        
        // Log VPN started event
        com.childsafety.os.cloud.FirebaseManager.logVpnEvent(started = true)
        
        Toast.makeText(this, "Protection enabled", Toast.LENGTH_SHORT).show()
        Log.i(TAG, "VPN service started")
    }

    private fun stopVpnService() {
        val intent = Intent(this, SafeVpnService::class.java).apply {
            action = SafeVpnService.ACTION_STOP
        }
        startService(intent)
        vpnEnabled = false
        
        // Log VPN stopped event
        com.childsafety.os.cloud.FirebaseManager.logVpnEvent(started = false)
        
        Toast.makeText(this, "Protection disabled", Toast.LENGTH_SHORT).show()
        Log.i(TAG, "VPN service stopped")
    }

    private fun openSafeBrowser() {
        val intent = Intent(this, SafeBrowserActivity::class.java).apply {
            putExtra(SafeBrowserActivity.EXTRA_AGE_GROUP, selectedAgeGroup.name)
        }
        startActivity(intent)
        Log.i(TAG, "Safe Browser opened with age group: ${selectedAgeGroup.name}")
    }
}

// MainScreen removed as it is replaced by ModernMainScreen
