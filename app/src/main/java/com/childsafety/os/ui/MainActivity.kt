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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ChildSafetyOSTheme {
                MainScreen(
                    vpnEnabled = vpnEnabled,
                    selectedAgeGroup = selectedAgeGroup,
                    onVpnToggle = { toggleVpn() },
                    onAgeGroupChange = { selectedAgeGroup = it },
                    onOpenBrowser = { openSafeBrowser() }
                )
            }
        }

        Log.i(TAG, "MainActivity created")
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
        Toast.makeText(this, "Protection enabled", Toast.LENGTH_SHORT).show()
        Log.i(TAG, "VPN service started")
    }

    private fun stopVpnService() {
        val intent = Intent(this, SafeVpnService::class.java).apply {
            action = SafeVpnService.ACTION_STOP
        }
        startService(intent)
        vpnEnabled = false
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
    selectedAgeGroup: AgeGroup,
    onVpnToggle: () -> Unit,
    onAgeGroupChange: (AgeGroup) -> Unit,
    onOpenBrowser: () -> Unit
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
                    text = "Protecting young minds",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            // Protection Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
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
                    Text(
                        text = if (vpnEnabled) "Protected" else "Not Protected",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (vpnEnabled) Color(0xFF38A169) else Color(0xFFE53E3E)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (vpnEnabled) 
                            "VPN active • DNS filtering on" 
                        else 
                            "Tap below to enable protection",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // VPN Toggle Button
                    Button(
                        onClick = onVpnToggle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (vpnEnabled) Color(0xFFE53E3E) else Color(0xFF38A169)
                        )
                    ) {
                        Text(
                            text = if (vpnEnabled) "Disable Protection" else "Enable Protection",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Age Group Selection
                    Text(
                        text = "Age Group",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        AgeGroup.entries.forEach { ageGroup ->
                            FilterChip(
                                selected = selectedAgeGroup == ageGroup,
                                onClick = { onAgeGroupChange(ageGroup) },
                                label = { 
                                    Text(
                                        ageGroup.name,
                                        fontSize = 12.sp
                                    ) 
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF667EEA),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            // Safe Browser Button
            Button(
                onClick = onOpenBrowser,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White
                )
            ) {
                Text(
                    text = "🌐 Open Safe Browser",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF667EEA)
                )
            }

            // Footer
            Text(
                text = "Protected by ML-powered content filtering",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}
