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

                Box(modifier = Modifier.fillMaxSize()) {
                    ModernMainScreen(
                        vpnEnabled = vpnEnabled,
                        isAdminActive = isAdminActive,
                        isAccessibilityActive = isAccessibilityActive,
                        selectedAgeGroup = selectedAgeGroup,
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
                
                // Refresh admin status on resume (lifecycle effect)
                DisposableEffect(Unit) {
                    val listener = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                            val adminStatus = devicePolicyManager.isAdminActive(adminComponentName)
                            val accessStatus = isAccessibilityServiceEnabled(this@MainActivity, com.childsafety.os.service.AppLockService::class.java)
                            
                            isAdminActive = adminStatus
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

@Composable
fun MainScreen(
    vpnEnabled: Boolean,
    isAdminActive: Boolean,
    isAccessibilityActive: Boolean,
    selectedAgeGroup: AgeGroup,
    onVpnToggle: () -> Unit,
    onAdminToggle: () -> Unit,
    onAccessToggle: () -> Unit,
    onOpenBrowser: () -> Unit,
    onAgeGroupChange: (AgeGroup) -> Unit,
    onRequestDataDeletion: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF667EEA),
                        Color(0xFF764BA2)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()) // Enable scrolling
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            // Vertical arrangement defaults to Top, which is better for scrolling
        ) {
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 48.dp)
            ) {
                Text(
                    text = "🛡️",
                    fontSize = 64.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "ChildSafetyOS",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isAdminActive && isAccessibilityActive) "Maximum Security" else "Setup Incomplete",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            // Protection Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // VPN Status
                    // ... (Keeping mostly same, simplified for brevity in this view)
                    
                    Button(
                        onClick = onVpnToggle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (vpnEnabled) Color(0xFFE53E3E) else Color(0xFF38A169)
                        )
                    ) {
                        Text(if (vpnEnabled) "1. Pause Filtering" else "1. Start Filtering")
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Admin Toggle
                    OutlinedButton(
                        onClick = onAdminToggle,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isAdminActive) Color(0xFF38A169) else Color(0xFFE53E3E)
                        )
                    ) {
                        Text(if (isAdminActive) "2. Uninstall Protection: ON" else "2. Enable Uninstall Protection")
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    // App Lock / Settings Lock
                    // Play Store Compliance: Must show disclosure BEFORE redirection
                    var showAccessDisclosure by remember { mutableStateOf(false) }

                    OutlinedButton(
                        onClick = { 
                            if (!isAccessibilityActive) {
                                showAccessDisclosure = true 
                            } else {
                                onAccessToggle() 
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isAccessibilityActive) Color(0xFF38A169) else Color(0xFFE53E3E)
                        )
                    ) {
                        Text(if (isAccessibilityActive) "3. Settings Lock: ON" else "3. Enable Settings Lock")
                    }
                    
                    if (showAccessDisclosure) {
                        AlertDialog(
                            onDismissRequest = { showAccessDisclosure = false },
                            title = { Text("Permission Required") },
                            text = {
                                Text(
                                    "ChildSafetyOS needs the Accessibility Service permission to function.\n\n" +
                                    "Why do we need it?\n" +
                                    "1. To detect when restricted apps are opened and lock them.\n" +
                                    "2. To prevent the app from being force-stopped or uninstalled without a PIN.\n\n" +
                                    "Privacy:\n" +
                                    "This service runs LOCALLY. Your screen content is NOT sent to any server.\n\n" +
                                    "Please find 'Child Safety OS' in the list and enable it."
                                )
                            },
                            confirmButton = {
                                Button(onClick = { 
                                    showAccessDisclosure = false
                                    onAccessToggle() 
                                }) {
                                    Text("I Understand")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showAccessDisclosure = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }

            // Safe Browser Button
            Button(
                onClick = onOpenBrowser,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF764BA2)
                )
            ) {
                Text("🌐 Open Safe Browser", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // --- PHASE 2: ENGAGEMENT ---
            
            // 1. Gamification / Rewards Card
            val streak = com.childsafety.os.gamification.GamificationManager.getStreak()
            val points = com.childsafety.os.gamification.GamificationManager.getPoints()
            
            Card(
                modifier = Modifier.fillMaxWidth().clickable { /* Show detailed dashboard later */ },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF6AD55)) // Orange for fun
            ) {
                Row(
                   modifier = Modifier.padding(16.dp),
                   verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🥷", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Cyber Ninja Status", fontWeight = FontWeight.Bold, color = Color.White)
                        Text("$streak Day Streak • $points Points", fontSize = 12.sp, color = Color.White)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 2. Focus Mode Toggle
            var isFocusMode by remember { mutableStateOf(false) }
            
            Button(
                onClick = { 
                    isFocusMode = !isFocusMode
                    com.childsafety.os.policy.BlockedAppsConfig.isFocusModeEnabled = isFocusMode
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isFocusMode) Color(0xFF805AD5) else Color.White.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (isFocusMode) "🧠 Focus Mode: ON" else "🧠 Start Focus Mode",
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Age Mode Selector Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.15f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🎯 Protection Mode",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Age mode buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        AgeGroup.values().forEach { ageGroup ->
                            val isSelected = selectedAgeGroup == ageGroup
                            val emoji = when (ageGroup) {
                                AgeGroup.CHILD -> "👶"
                                AgeGroup.TEEN -> "🧑"
                                AgeGroup.ADULT -> "👤"
                            }
                            Button(
                                onClick = { onAgeGroupChange(ageGroup) },
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.3f),
                                    contentColor = if (isSelected) Color(0xFF764BA2) else Color.White
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("$emoji ${ageGroup.name}", fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Description based on selected mode
                    val description = com.childsafety.os.policy.ThresholdProvider.getDescription(selectedAgeGroup)
                    Text(
                        text = description,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                    
                    // Important note for Adult mode
                    if (selectedAgeGroup == AgeGroup.ADULT) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "⚠️ Even in Adult mode, highly explicit content is blocked for your wellbeing.",
                            fontSize = 10.sp,
                            color = Color(0xFFFFD700),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Privacy Policy Dialog
            var showPrivacyDialog by remember { mutableStateOf(false) }
            
            TextButton(
                onClick = { showPrivacyDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🔒 Privacy Policy", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
            }
            
            if (showPrivacyDialog) {
                AlertDialog(
                    onDismissRequest = { showPrivacyDialog = false },
                    title = { Text("Privacy Policy") },
                    text = {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text(
                                text = """
                                    Last Updated: January 2026
                                    
                                    1. Data Cleanliness
                                    ChildSafetyOS prioritizes your privacy. We process text and images LOCALLY on your device whenever possible.
                                    
                                    2. Data Collection
                                    We collect:
                                    - Domain names (to block harmful sites)
                                    - App usage stats (to lock apps)
                                    - Device identifiers (for dashboard sync)
                                    
                                    3. Data Sharing
                                    We do NOT sell your data. Data is only synced to your personal Parental Dashboard via secure Firebase connection.
                                    
                                    4. Permissions
                                    We use high-privilege permissions (VPN, Accessibility, Admin) solely to enforce parental controls and prevent unauthorized removal.
                                    
                                    5. Contact
                                    For data deletion or inquiries, use the 'Request Data Deletion' button.
                                """.trimIndent(),
                                fontSize = 13.sp
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = { showPrivacyDialog = false }) {
                            Text("Close")
                        }
                    }
                )
            }
            
            // Request Data Deletion via Email (DPDP Compliance)
            TextButton(
                onClick = onRequestDataDeletion,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("📧 Request Data Deletion (DPDP)", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // (Advanced Management / Uninstall button removed by request)
        }
    }
}

@Composable
fun LockScreen(
    onUnlock: () -> Unit
) {
    // A Red full-screen overlay for locking Settings
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE53E3E)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp).background(Color.White, RoundedCornerShape(16.dp)).padding(24.dp)
        ) {
            Text("🔒 Restricted Access", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Settings are locked.", color = Color.Gray)
            Text("Enter Parent PIN to proceed.", color = Color.Gray)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 4) pin = it },
                label = { Text("PIN") },
                isError = error != null,
                singleLine = true
            )
            if (error != null) Text(error!!, color = Color.Red, fontSize = 12.sp)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { 
                    if (pin == "1234") {
                        // ENABLE BYPASS MODE - allows parent to access Settings for 30 seconds
                        com.childsafety.os.service.AppLockService.enableBypassMode()
                        onUnlock()
                    } else {
                        error = "Incorrect PIN"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Unlock (30 sec access)")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "After unlocking, you have 30 seconds to change settings.",
                fontSize = 11.sp,
                color = Color.Gray,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun PinDialog(
    onDismiss: () -> Unit,
    onPinEntered: (String) -> Unit,
    error: String? = null
) {
    var pin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Parent PIN Required") },
        text = {
            Column {
                Text("Enter PIN to disable protection (Default: 1234)")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4) pin = it },
                    singleLine = true,
                    isError = error != null,
                    label = { Text("PIN") },
                    supportingText = { if (error != null) Text(error, color = MaterialTheme.colorScheme.error) }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onPinEntered(pin) }) {
                Text("Verify")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
