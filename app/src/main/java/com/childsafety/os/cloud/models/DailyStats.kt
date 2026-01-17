package com.childsafety.os.cloud.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

/**
 * Pre-aggregated daily statistics.
 * Maps to Firestore: devices/{deviceId}/daily_stats/{date}
 */
data class DailyStats(
    val date: String = "", // YYYY-MM-DD
    val totalEvents: Int = 0,
    val totalBlocks: Int = 0,
    
    // By Type
    val imageBlocks: Int = 0,
    val urlBlocks: Int = 0,
    val searchBlocks: Int = 0,
    val pageBlocks: Int = 0,
    val appLocks: Int = 0,
    
    // By Severity
    val criticalBlocks: Int = 0,
    val highBlocks: Int = 0,
    val mediumBlocks: Int = 0,
    val lowBlocks: Int = 0,
    
    // Top domains/reasons (top 10)
    val topBlockedDomains: Map<String, Int> = emptyMap(),
    val topReasons: Map<String, Int> = emptyMap(),
    
    // Browsing metrics
    val totalBrowsingMinutes: Int = 0,
    val totalAppsUsed: List<String> = emptyList(),
    
    // Usage Stats (PackageName -> Minutes)
    val appUsage: Map<String, Long> = emptyMap(),
    
    @ServerTimestamp
    val updatedAt: Timestamp? = null
) {
    constructor() : this(date = "")
}
