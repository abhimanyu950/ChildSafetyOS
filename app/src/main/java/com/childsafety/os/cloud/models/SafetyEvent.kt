package com.childsafety.os.cloud.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

/**
 * Unified event model for all safety/filtering events.
 * Maps to Firestore: devices/{deviceId}/events/{eventId}
 */
data class SafetyEvent(
    val eventId: String = "",
    @ServerTimestamp
    val timestamp: Timestamp? = null,
    val eventType: EventType = EventType.URL_BLOCKED,
    val category: EventCategory = EventCategory.CONTENT_FILTER,
    val severity: Severity = Severity.MEDIUM,
    
    // Event Details
    val url: String? = null,
    val domain: String? = null,
    val searchQuery: String? = null,
    val reason: String = "",
    val blockType: BlockType = BlockType.KEYWORD,
    
    // ML Specific (for images)
    val imageUrl: String? = null,
    val mlScores: Map<String, Double>? = null,
    val threshold: Map<String, Double>? = null,
    
    // Metadata
    val appPackage: String? = null,
    val appName: String? = null,
    val browserType: BrowserType = BrowserType.SAFE_BROWSER,
    val deviceLocation: String? = null
) {
    // Empty constructor for Firestore
    constructor() : this(eventId = "")
}

enum class EventType {
    IMAGE_BLOCKED,
    VIDEO_BLOCKED,
    URL_BLOCKED,
    SEARCH_BLOCKED,
    PAGE_BLOCKED,
    APP_LOCKED,
    DOMAIN_BLOCKED,
    VPN_STARTED,
    VPN_STOPPED,
    PROTECTION_ENABLED,
    PROTECTION_DISABLED
}

enum class EventCategory {
    CONTENT_FILTER,
    ACCESS_CONTROL,
    VPN_FILTER,
    SYSTEM_EVENT
}

enum class Severity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class BlockType {
    ML_IMAGE,
    KEYWORD,
    DNS,
    PAGE_TRIGGER,
    ACCESSIBILITY,
    URL_PATTERN
}

enum class BrowserType {
    SAFE_BROWSER,
    CHROME,
    OTHER
}
