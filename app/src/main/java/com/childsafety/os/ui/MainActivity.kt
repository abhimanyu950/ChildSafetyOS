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
                
                // Track admin status
                val devicePolicyManager = getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                var isAdminActive by remember { mutableStateOf(devicePolicyManager.isAdminActive(adminComponentName)) }
                
                // Track Accessibility (App Lock) status
                val isAccessibilityActive by remember { 
                    derivedStateOf { isAccessibilityServiceEnabled(this, com.childsafety.os.service.AppLockService::class.java) } 
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        vpnEnabled = vpnEnabled,
                        isAdminActive = isAdminActive,
                        isAccessibilityActive = isAccessibilityActive,
                        selectedAgeGroup = selectedAgeGroup,
                        onVpnToggle = { toggleVpn() },
                        onAdminToggle = { 
                            if (isAdminActive) {
                                // Request PIN to disable
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
                            selectedAgeGroup = newAgeGroup
                            Toast.makeText(this@MainActivity, "Age mode: ${newAgeGroup.name}", Toast.LENGTH_SHORT).show()
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
                            },
                            onPinEntered = { pin ->
                                if (pin == "1234") { // Hardcoded Parent PIN
                                    // Remove Admin
                                    devicePolicyManager.removeActiveAdmin(adminComponentName)
                                    isAdminActive = false
                                    showPinDialog = false
                                    pinError = null
                                    Toast.makeText(this@MainActivity, "Protection Disabled", Toast.LENGTH_SHORT).show()
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
                            isAdminActive = devicePolicyManager.isAdminActive(adminComponentName)
                            // Trigger recomposition for accessibility check
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
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
                    OutlinedButton(
                        onClick = onAccessToggle,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isAccessibilityActive) Color(0xFF38A169) else Color(0xFFE53E3E)
                        )
                    ) {
                        Text(if (isAccessibilityActive) "3. Settings Lock: ON" else "3. Enable Settings Lock")
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
            
            // Privacy & Data Controls
            TextButton(
                onClick = { /* TODO: Launch Privacy Policy */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🔒 Privacy Policy", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
            }
            
            // Request Data Deletion via Email (DPDP Compliance)
            TextButton(
                onClick = onRequestDataDeletion,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("📧 Request Data Deletion (DPDP)", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            }
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
                        onUnlock() // Actually we usually want to allow access, but here we just go Home to "Block" it.
                        // Ideally we would finish() and let them proceed, but `startActivity(Settings)` brings us back in loop.
                        // For SIMPLE blocking: We just block it. "Settings are disabled".
                        // To allow parents: We would need a "Snooze" mechanics in Service.
                        // user asked to "Protect uninstallation". Blocking Settings does that.
                    } else {
                        error = "Incorrect PIN"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Unlock")
            }
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
