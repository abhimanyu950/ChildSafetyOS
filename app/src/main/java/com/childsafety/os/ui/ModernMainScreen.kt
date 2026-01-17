package com.childsafety.os.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.childsafety.os.policy.AgeGroup
import com.childsafety.os.cloud.FirebaseManager

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState

/**
 * Modern, compact MainScreen UI.
 * 
 * Features:
 * - Compact header with protection score
 * - 2x2 status grid for toggles
 * - No scrolling required
 * - Dark theme with accent colors
 * - Universal UI - works on all screen sizes and notch configurations
 */
@Composable
fun ModernMainScreen(
    vpnEnabled: Boolean,
    isAdminActive: Boolean,
    isAccessibilityActive: Boolean,
    selectedAgeGroup: AgeGroup,
    onVpnToggle: () -> Unit,
    onAdminToggle: () -> Unit,
    onAccessToggle: () -> Unit,
    onOpenBrowser: () -> Unit,
    onAgeGroupChange: (AgeGroup) -> Unit,
    onRequestDataDeletion: () -> Unit,
    // [NEW] Usage Permission
    isUsageActive: Boolean,
    onRequestUsageAccess: () -> Unit,
    onReplayTour: () -> Unit
) {
    // Calculate protection score (max 4 now)
    val protectionScore = listOf(vpnEnabled, isAdminActive, isAccessibilityActive, isUsageActive).count { it }
    val protectionStatus = when (protectionScore) {
        4 -> "Maximum Security"
        3 -> "High Protection"
        2 -> "Good Protection"
        1 -> "Basic Protection"
        else -> "Setup Required"
    }
    val statusColor = when (protectionScore) {
        4 -> Color(0xFF38A169)
        3 -> Color(0xFF38A169)
        2 -> Color(0xFFF6AD55)
        else -> Color(0xFFE53E3E)
    }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showConnectDialog by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1B2E))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding() // Handle status bar and notch
                .navigationBarsPadding() // Handle navigation bar
                .padding(horizontal = 16.dp)
                // Use a vertical scroll implementation if needed, but fitting to screen is nicer for dashboards
                .verticalScroll(rememberScrollState())
        ) {
            // ===== COMPACT HEADER =====
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🛡️", fontSize = 32.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("ChildSafetyOS", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(protectionStatus, fontSize = 13.sp, color = statusColor, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(statusColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$protectionScore/4", color = statusColor, fontWeight = FontWeight.Bold)
                }
            }
            
            // ===== PRIMARY ACTION =====
            Button(
                onClick = onOpenBrowser,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF667EEA))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🌐", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("Open Safe Browser", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Protected Web Surfing", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // ===== SECTION 1: CORE SECURITY (The "Shield") =====
            // These are set-and-forget permissions that form the backbone of protection
            SectionHeader("System Security", "Essential permissions for protection")
            
            Row(modifier = Modifier.fillMaxWidth()) {
                // filtering (VPN)
                StatusCard(
                    modifier = Modifier.weight(1f),
                    icon = if (vpnEnabled) "✅" else "⚡",
                    title = "Filtering",
                    subtitle = "Blocks bad sites",
                    isActive = vpnEnabled,
                    onClick = onVpnToggle
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Uninstall Lock (Admin)
                StatusCard(
                    modifier = Modifier.weight(1f),
                    icon = "🔒",
                    title = "Uninstall Lock",
                    subtitle = "Prevent removal",
                    isActive = isAdminActive,
                    onClick = onAdminToggle
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                 // Settings Lock (Accessibility)
                StatusCard(
                    modifier = Modifier.weight(1f),
                    icon = "⚙️", 
                    title = "Settings Lock",
                    subtitle = "Anti-tamper",
                    isActive = isAccessibilityActive,
                    onClick = { if (!isAccessibilityActive) onAccessToggle() /* Handle dialog in parent or logic hook */ else onAccessToggle() }
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Usage Stats
                StatusCard(
                    modifier = Modifier.weight(1f), 
                    icon = "📊", 
                    title = "Usage access", 
                    subtitle = "Track time",
                    isActive = isUsageActive,
                    onClick = onRequestUsageAccess
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // ===== SECTION 2: DIGITAL WELLBEING (Daily Tools) =====
            SectionHeader("Digital Wellbeing", "Tools for healthy habits")
            
            var isFocusMode by remember { mutableStateOf(com.childsafety.os.policy.BlockedAppsConfig.isFocusModeEnabled) }
            
            // Focus Mode Card (Full Width)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                         isFocusMode = !isFocusMode
                         com.childsafety.os.policy.BlockedAppsConfig.isFocusModeEnabled = isFocusMode
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isFocusMode) Color(0xFF805AD5).copy(alpha = 0.2f) else Color(0xFF252640)
                ),
                border = if (isFocusMode) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF805AD5)) else null
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🧠", fontSize = 32.sp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isFocusMode) "Focus Mode: ACTIVE" else "Start Focus Mode",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Blocks Games & Social Media. Great for study time!",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = isFocusMode,
                        onCheckedChange = { 
                            isFocusMode = it
                            com.childsafety.os.policy.BlockedAppsConfig.isFocusModeEnabled = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF805AD5),
                            checkedTrackColor = Color(0xFF805AD5).copy(alpha = 0.5f)
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // ===== SECTION 3: CONFIGURATION =====
            SectionHeader("Configuration", "Customize protection level")
            
            // Age Mode Selector
             Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF252640))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AgeGroup.values().forEach { ageGroup ->
                            val isSelected = selectedAgeGroup == ageGroup
                            val emoji = when (ageGroup) { AgeGroup.CHILD -> "👶"; AgeGroup.TEEN -> "🧑"; AgeGroup.ADULT -> "👤" }
                            Button(
                                onClick = { onAgeGroupChange(ageGroup) },
                                modifier = Modifier.weight(1f).height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = if (isSelected) Color(0xFF667EEA) else Color(0xFF3A3B5C)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(4.dp)
                            ) {
                                Text("$emoji ${ageGroup.name}", fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            // ===== SECTION 4: DASHBOARD =====
            SectionHeader("Parent Dashboard", "Monitor activity remotely")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showConnectDialog = true },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2D3748))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🖥️", fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Connect to Dashboard",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Get Device ID to view live details",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Text("👉", fontSize = 20.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))

            // Footer Links
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { /* Privacy Logic handled in Parent */ }) { Text("Privacy Policy", color = Color.Gray, fontSize = 12.sp) }
                TextButton(onClick = onReplayTour) { Text("Replay Tour", color = Color.Gray, fontSize = 12.sp) }
                TextButton(onClick = onRequestDataDeletion) { Text("Delete Data", color = Color.Gray, fontSize = 12.sp) }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (showConnectDialog) {
            AlertDialog(
                onDismissRequest = { showConnectDialog = false },
                title = { Text("Connect to Dashboard") },
                text = {
                    Column {
                        Text("Enter this Device ID in the web dashboard to view live activity:")
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF2D3748), RoundedCornerShape(8.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = FirebaseManager.getDeviceId(),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(FirebaseManager.getDeviceId()))
                            showConnectDialog = false
                        }
                    ) {
                        Text("Copy ID")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConnectDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(subtitle, color = Color.Gray, fontSize = 12.sp)
    }
}

/**
 * Compact Status Card
 */
@Composable
private fun StatusCard(
    modifier: Modifier = Modifier,
    icon: String,
    title: String,
    subtitle: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(100.dp) // Slightly taller for subtitle
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Color(0xFF38A169).copy(alpha = 0.15f) else Color(0xFF252640)
        ),
        border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38A169).copy(alpha = 0.3f)) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 28.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
            Text(subtitle, fontSize = 10.sp, color = Color.Gray, textAlign = TextAlign.Center, lineHeight = 12.sp, maxLines = 2)
        }
    }
}
