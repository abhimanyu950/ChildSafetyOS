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
import com.childsafety.os.policy.AgeGroup

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
    onRequestDataDeletion: () -> Unit
) {
    // Calculate protection score
    val protectionScore = listOf(vpnEnabled, isAdminActive, isAccessibilityActive).count { it }
    val protectionStatus = when (protectionScore) {
        3 -> "Maximum Security"
        2 -> "Good Protection"
        1 -> "Basic Protection"
        else -> "Setup Required"
    }
    val statusColor = when (protectionScore) {
        3 -> Color(0xFF38A169)
        2 -> Color(0xFFF6AD55)
        else -> Color(0xFFE53E3E)
    }
    
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // ===== COMPACT HEADER =====
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
                    modifier = Modifier.size(48.dp).background(statusColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$protectionScore/3", color = statusColor, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // ===== MAIN ACTION BUTTON =====
            Button(
                onClick = onOpenBrowser,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF667EEA))
            ) {
                Text("🌐 Open Safe Browser", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // ===== STATUS GRID (2x2) =====
            Row(modifier = Modifier.fillMaxWidth()) {
                StatusCard(Modifier.weight(1f), if (vpnEnabled) "✅" else "⚡", "Filtering", vpnEnabled, onVpnToggle)
                Spacer(modifier = Modifier.width(12.dp))
                StatusCard(Modifier.weight(1f), if (isAdminActive) "🔒" else "🔓", "Uninstall Lock", isAdminActive, onAdminToggle)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            var showAccessDisclosure by remember { mutableStateOf(false) }
            var isFocusMode by remember { mutableStateOf(false) }
            
            Row(modifier = Modifier.fillMaxWidth()) {
                StatusCard(Modifier.weight(1f), "⚙️", "Settings Lock", isAccessibilityActive) { 
                    if (!isAccessibilityActive) showAccessDisclosure = true else onAccessToggle()
                }
                Spacer(modifier = Modifier.width(12.dp))
                StatusCard(Modifier.weight(1f), "🧠", "Focus Mode", isFocusMode) { 
                    isFocusMode = !isFocusMode
                    com.childsafety.os.policy.BlockedAppsConfig.isFocusModeEnabled = isFocusMode
                }
            }
            
            if (showAccessDisclosure) {
                AlertDialog(
                    onDismissRequest = { showAccessDisclosure = false },
                    title = { Text("Permission Required") },
                    text = { Text("ChildSafetyOS needs Accessibility to:\n• Lock restricted apps\n• Prevent uninstall\n\nData stays LOCAL.") },
                    confirmButton = { Button(onClick = { showAccessDisclosure = false; onAccessToggle() }) { Text("Enable") } },
                    dismissButton = { TextButton(onClick = { showAccessDisclosure = false }) { Text("Cancel") } }
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // ===== AGE MODE SELECTOR =====
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF252640))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Protection Level", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(12.dp))
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
                                Text("$emoji ${ageGroup.name}", fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // ===== GAMIFICATION STRIP =====
            val streak = com.childsafety.os.gamification.GamificationManager.getStreak()
            val points = com.childsafety.os.gamification.GamificationManager.getPoints()
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF6AD55))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🥷", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Cyber Ninja", fontWeight = FontWeight.Bold, color = Color.White)
                        Text("$streak Day Streak", fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f))
                    }
                    Text("$points pts", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // ===== FOOTER =====
            var showPrivacyDialog by remember { mutableStateOf(false) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { showPrivacyDialog = true }) { Text("Privacy", color = Color.Gray, fontSize = 12.sp) }
                TextButton(onClick = onRequestDataDeletion) { Text("Delete Data", color = Color.Gray, fontSize = 12.sp) }
            }
            
            if (showPrivacyDialog) {
                AlertDialog(
                    onDismissRequest = { showPrivacyDialog = false },
                    title = { Text("Privacy") },
                    text = { Text("• Content scanned LOCALLY\n• No data sold\n• Dashboard via secure Firebase") },
                    confirmButton = { Button(onClick = { showPrivacyDialog = false }) { Text("OK") } }
                )
            }
        }
    }
}

/**
 * Compact Status Card for 2x2 grid
 */
@Composable
private fun StatusCard(
    modifier: Modifier = Modifier,
    icon: String,
    title: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.height(80.dp).clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Color(0xFF38A169).copy(alpha = 0.15f) else Color(0xFF252640)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, 
                 color = if (isActive) Color(0xFF38A169) else Color.Gray, textAlign = TextAlign.Center)
        }
    }
}
