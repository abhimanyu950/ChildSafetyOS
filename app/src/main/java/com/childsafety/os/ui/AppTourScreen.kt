package com.childsafety.os.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * App Tour / Onboarding Screen.
 * Shown only on first launch.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTourScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1B2E),
                        Color(0xFF252640)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            // Skip Button (Top Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .statusBarsPadding(), // Handle notches
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onFinish) {
                    Text("Skip", color = Color.White.copy(alpha = 0.6f))
                }
            }

            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                TourPage(page)
            }

            // Indicators & Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pager Indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(4) { iteration ->
                        val color = if (pagerState.currentPage == iteration) Color(0xFF667EEA) else Color.White.copy(alpha = 0.2f)
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(color)
                                .size(if (pagerState.currentPage == iteration) 12.dp else 8.dp)
                        )
                    }
                }

                // Next / Finish Button
                Button(
                    onClick = {
                        if (pagerState.currentPage < 3) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            onFinish()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF667EEA))
                ) {
                    Text(if (pagerState.currentPage == 3) "Get Started" else "Next")
                }
            }
        }
    }
}

@Composable
fun TourPage(page: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val (emoji, title, desc) = when (page) {
            0 -> Triple(
                "🛡️",
                "Welcome to ChildSafetyOS",
                "Advanced protection for your child's digital life. Private, secure, and smart."
            )
            1 -> Triple(
                "🧠",
                "Smart AI Filtering",
                "We use on-device AI to detect and block harmful content in real-time, keeping photos and chats safe."
            )
            2 -> Triple(
                "⚡",
                "Local VPN Protection",
                "Our local VPN filters internet traffic directly on the phone. No data is sent to remote servers for filtering."
            )
            else -> Triple(
                "🔒",
                "Tamper Proof",
                "To ensure safety, we'll ask for Accessibility permissions to prevent unauthorized app removal."
            )
        }

        Text(
            text = emoji,
            fontSize = 100.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Text(
            text = title,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = desc,
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
    }
}
