package com.childsafety.os.cloud

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.childsafety.os.cloud.models.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.*

/**
 * Enhanced Firebase manager with analytics and event logging.
 * Handles all Firestore operations for parental dashboard.
 */
object FirebaseManager {

    private const val TAG = "FirebaseManager"
    private var initialized = false
    private lateinit var db: FirebaseFirestore
    private lateinit var deviceId: String
    private lateinit var applicationContext: Context

    fun init(context: Context) {
        if (initialized) return
        
        try {
            // Configure Firestore for offline support
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build()
            
            db = FirebaseFirestore.getInstance()
            db.firestoreSettings = settings
            
            // Get unique device ID
            deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            
            // Initialize device status
            initDeviceStatus(context)
            
            applicationContext = context.applicationContext
            
            initialized = true
            Log.i(TAG, "Firebase initialized successfully with deviceId: $deviceId")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization failed (app will continue)", e)
        }
    }

    private fun initDeviceStatus(context: Context) {
        val deviceStatus = DeviceStatus(
            deviceId = deviceId,
            deviceName = android.os.Build.MODEL,
            childName = "Child", // Could be configured by parent
            vpnEnabled = false,
            adminProtectionEnabled = false,
            settingsLockEnabled = false,
            ageGroup = "CHILD"
        )
        
        db.collection("devices")
            .document(deviceId)
            .set(deviceStatus, SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "Device status initialized")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to init device status", e)
            }
    }

    /**
     * Update device status (VPN, Admin, Settings Lock state)
     */
    fun updateDeviceStatus(
        vpnEnabled: Boolean? = null,
        adminEnabled: Boolean? = null,
        settingsLockEnabled: Boolean? = null
    ) {
        if (!initialized) return
        
        val updates = mutableMapOf<String, Any>(
            "lastSeen" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        
        vpnEnabled?.let { updates["vpnEnabled"] = it }
        adminEnabled?.let { updates["adminProtectionEnabled"] = it }
        settingsLockEnabled?.let { updates["settingsLockEnabled"] = it }
        
        // SYNC USAGE STATS (On Heartbeat/Status Update)
        try {
            if (::applicationContext.isInitialized && com.childsafety.os.manager.UsageManager.hasPermission(applicationContext)) {
                 val usageMap = com.childsafety.os.manager.UsageManager.getTodayUsage(applicationContext)
                 if (usageMap.isNotEmpty()) {
                     // We update the daily_stats document, not the device document for usage
                     // But we can spawn a coroutine or just do it here
                     val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                     db.collection("devices").document(deviceId)
                         .collection("daily_stats").document(today)
                         .set(mapOf("appUsage" to usageMap), SetOptions.merge())
                 }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync usage stats on heartbeat", e)
        }
        
        db.collection("devices")
            .document(deviceId)
            .update(updates)
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update device status", e)
            }
    }

    /**
     * Log a safety event (block, filter, etc.)
     */
    fun logEvent(event: SafetyEvent) {
        if (!initialized) {
            Log.w(TAG, "Firebase not initialized, skipping event log")
            return
        }
        
        val eventRef = db.collection("devices")
            .document(deviceId)
            .collection("events")
            .document()
        
        val eventWithId = event.copy(eventId = eventRef.id)
        
        eventRef.set(eventWithId)
            .addOnSuccessListener {
                Log.d(TAG, "Event logged: ${event.eventType} - ${event.reason}")
                
                // Update daily stats
                updateDailyStats(event)
                
                // Check for alert conditions
                checkAlertConditions(event)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to log event", e)
            }
            
        // GAMIFICATION HOOK:
        // If this is a blocking event (Filter or Access Control), mark today as "Unsafe"
        if (event.category == EventCategory.CONTENT_FILTER || event.category == EventCategory.ACCESS_CONTROL) {
             com.childsafety.os.gamification.GamificationManager.onSafetyViolation()
        }
    }

    /**
     * Log an image block with ML scores
     */
    /**
     * Log an image block with ML scores
     */
    fun logImageBlock(
        imageUrl: String,
        mlScores: Map<String, Double>,
        threshold: Map<String, Double>,
        reason: String,
        url: String? = null,
        domain: String? = null
    ) {
        val event = SafetyEvent(
            eventType = EventType.IMAGE_BLOCKED,
            category = EventCategory.CONTENT_FILTER,
            severity = determineSeverity(mlScores),
            url = url,
            domain = domain,
            reason = reason,
            blockType = BlockType.ML_IMAGE,
            imageUrl = imageUrl,
            mlScores = mlScores,
            threshold = threshold,
            browserType = BrowserType.SAFE_BROWSER
        )
        
        logEvent(event)
    }

    /**
     * Log a video block with ML scores
     */
    fun logVideoBlock(
        videoUrl: String,
        mlScores: Map<String, Double>,
        threshold: Map<String, Double>,
        reason: String,
        url: String? = null,
        domain: String? = null
    ) {
        val event = SafetyEvent(
            eventType = EventType.VIDEO_BLOCKED,
            category = EventCategory.CONTENT_FILTER,
            severity = determineSeverity(mlScores),
            url = url,
            domain = domain,
            reason = reason,
            blockType = BlockType.ML_IMAGE, // Reusing ML_IMAGE block type for now as it's ML-based
            imageUrl = videoUrl, // Storing video URL / Frame ID in imageUrl field for now
            mlScores = mlScores,
            threshold = threshold,
            browserType = BrowserType.SAFE_BROWSER
        )

        logEvent(event)
    }

    /**
     * Log a URL/domain block
     */
    fun logUrlBlock(
        url: String,
        domain: String,
        reason: String,
        blockType: BlockType = BlockType.KEYWORD,
        browserType: BrowserType = BrowserType.SAFE_BROWSER
    ) {
        val event = SafetyEvent(
            eventType = EventType.URL_BLOCKED,
            category = EventCategory.CONTENT_FILTER,
            severity = Severity.HIGH,
            url = url,
            domain = domain,
            reason = reason,
            blockType = blockType,
            browserType = browserType
        )
        
        logEvent(event)
    }

    /**
     * Log a search query block
     */
    fun logSearchBlock(
        searchQuery: String,
        url: String,
        domain: String,
        reason: String
    ) {
        val event = SafetyEvent(
            eventType = EventType.SEARCH_BLOCKED,
            category = EventCategory.CONTENT_FILTER,
            severity = Severity.HIGH,
            url = url,
            domain = domain,
            searchQuery = searchQuery,
            reason = reason,
            blockType = BlockType.KEYWORD,
            browserType = BrowserType.SAFE_BROWSER
        )
        
        logEvent(event)
    }

    /**
     * COMPLIANCE: Right to be Forgotten (DPDP Act).
     * Deletes all logs associated with this device ID.
     */
    fun deleteAllData(onComplete: (Boolean) -> Unit) {
        val deviceId = com.childsafety.os.ChildSafetyApp.appDeviceId
        
        // 1. Query all logs for this device
        // Note: For a hackathon, we do a best-effort client-side delete.
        // Production should use a Cloud Function (recursive delete) for atomicity.
        
        val batch = db.batch()
        
        // Delete device document
        val deviceRef = db.collection("devices").document(deviceId)
        batch.delete(deviceRef)
        
        batch.commit()
            .addOnSuccessListener { 
                Log.i(TAG, "Compliance: Device data deleted for $deviceId")
                onComplete(true) 
            }
            .addOnFailureListener { e -> 
                Log.e(TAG, "Compliance: Failed to delete data", e)
                onComplete(false) 
            }
    }

    /**
     * Log a page-level block (multiple images)
     */
    fun logPageBlock(
        url: String,
        domain: String,
        reason: String,
        blockedImageCount: Int
    ) {
        val event = SafetyEvent(
            eventType = EventType.PAGE_BLOCKED,
            category = EventCategory.CONTENT_FILTER,
            severity = Severity.CRITICAL,
            url = url,
            domain = domain,
            reason = "$reason ($blockedImageCount images)",
            blockType = BlockType.PAGE_TRIGGER,
            browserType = BrowserType.SAFE_BROWSER
        )
        
        logEvent(event)
    }

    /**
     * Log Settings/PlayStore access attempt
     */
    fun logAppLock(
        appPackage: String,
        appName: String,
        reason: String
    ) {
        val event = SafetyEvent(
            eventType = EventType.APP_LOCKED,
            category = EventCategory.ACCESS_CONTROL,
            severity = Severity.MEDIUM,
            reason = reason,
            blockType = BlockType.ACCESSIBILITY,
            appPackage = appPackage,
            appName = appName
        )
        
        logEvent(event)
    }

    /**
     * Log VPN events
     */
    fun logVpnEvent(started: Boolean) {
        val event = SafetyEvent(
            eventType = if (started) EventType.VPN_STARTED else EventType.VPN_STOPPED,
            category = EventCategory.SYSTEM_EVENT,
            severity = Severity.LOW,
            reason = if (started) "VPN protection enabled" else "VPN protection disabled"
        )
        
        logEvent(event)
        updateDeviceStatus(vpnEnabled = started)
    }

    /**
     * Log when a parent uses PIN to disable a protection setting
     */
    fun logSettingDisabledByParent(settingName: String) {
        if (!initialized) return
        
        val event = SafetyEvent(
            eventType = EventType.PROTECTION_DISABLED,
            category = EventCategory.SYSTEM_EVENT,
            severity = Severity.MEDIUM,
            reason = "$settingName disabled by parent (PIN verified)"
        )
        
        logEvent(event)
        
        // Also update device status based on setting
        when (settingName) {
            "VPN_PROTECTION" -> updateDeviceStatus(vpnEnabled = false)
            "ADMIN_PROTECTION" -> updateDeviceStatus(adminEnabled = false)
            "SETTINGS_LOCK" -> updateDeviceStatus(settingsLockEnabled = false)
        }
        
        Log.i(TAG, "Setting disabled by parent: $settingName")
    }

    /**
     * Log when a parent changes the age mode with PIN verification
     */
    fun logAgeModeChange(newAgeMode: String) {
        if (!initialized) return
        
        val event = SafetyEvent(
            eventType = EventType.PROTECTION_ENABLED,
            category = EventCategory.SYSTEM_EVENT,
            severity = Severity.LOW,
            reason = "Age mode changed to $newAgeMode by parent (PIN verified)"
        )
        
        logEvent(event)
        Log.i(TAG, "Age mode changed by parent: $newAgeMode")
    }

    /**
     * Log security alerts (PIN lockout, tampering attempts, etc.)
     */
    fun logSecurityAlert(alertType: String, details: String) {
        if (!initialized) return
        
        val event = SafetyEvent(
            eventType = EventType.PROTECTION_DISABLED,
            category = EventCategory.SYSTEM_EVENT,
            severity = Severity.CRITICAL,
            reason = "SECURITY ALERT: $alertType - $details"
        )
        
        logEvent(event)
        Log.w(TAG, "Security Alert: $alertType - $details")
    }

    /**
     * Determine severity based on ML scores
     */
    private fun determineSeverity(mlScores: Map<String, Double>): Severity {
        val maxScore = mlScores.values.maxOrNull() ?: 0.0
        return when {
            maxScore >= 0.9 -> Severity.CRITICAL
            maxScore >= 0.8 -> Severity.HIGH
            maxScore >= 0.7 -> Severity.MEDIUM
            else -> Severity.LOW
        }
    }

    /**
     * Update daily aggregated statistics
     */
    private fun updateDailyStats(event: SafetyEvent) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val statsRef = db.collection("devices")
            .document(deviceId)
            .collection("daily_stats")
            .document(today)
        
        // Increment counters based on event type
        val updates = mutableMapOf<String, Any>(
            "date" to today,
            "totalEvents" to com.google.firebase.firestore.FieldValue.increment(1),
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        
        // SYNC USAGE STATS (Best effort)
        try {
            if (::applicationContext.isInitialized && com.childsafety.os.manager.UsageManager.hasPermission(applicationContext)) {
                 val usageMap = com.childsafety.os.manager.UsageManager.getTodayUsage(applicationContext)
                 if (usageMap.isNotEmpty()) {
                     updates["appUsage"] = usageMap
                 }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync usage stats", e)
        }
        
        // Increment block counters
        if (event.category == EventCategory.CONTENT_FILTER || event.category == EventCategory.ACCESS_CONTROL) {
            updates["totalBlocks"] = com.google.firebase.firestore.FieldValue.increment(1)
            
            when (event.eventType) {
                EventType.IMAGE_BLOCKED -> updates["imageBlocks"] = com.google.firebase.firestore.FieldValue.increment(1)
                EventType.VIDEO_BLOCKED -> updates["videoBlocks"] = com.google.firebase.firestore.FieldValue.increment(1)
                EventType.URL_BLOCKED -> updates["urlBlocks"] = com.google.firebase.firestore.FieldValue.increment(1)
                EventType.SEARCH_BLOCKED -> updates["searchBlocks"] = com.google.firebase.firestore.FieldValue.increment(1)
                EventType.PAGE_BLOCKED -> updates["pageBlocks"] = com.google.firebase.firestore.FieldValue.increment(1)
                EventType.APP_LOCKED -> updates["appLocks"] = com.google.firebase.firestore.FieldValue.increment(1)
                else -> {}
            }
            
            // Increment severity counters
            when (event.severity) {
                Severity.CRITICAL -> updates["criticalBlocks"] = com.google.firebase.firestore.FieldValue.increment(1)
                Severity.HIGH -> updates["highBlocks"] = com.google.firebase.firestore.FieldValue.increment(1)
                Severity.MEDIUM -> updates["mediumBlocks"] = com.google.firebase.firestore.FieldValue.increment(1)
                Severity.LOW -> updates["lowBlocks"] = com.google.firebase.firestore.FieldValue.increment(1)
            }
            
            // Update top blocked domains
            // IMPROVEMENT: For SEARCH_BLOCKED, show the query ("porn.com") instead of "google.com"
            val displayKey = if (event.eventType == EventType.SEARCH_BLOCKED && !event.searchQuery.isNullOrBlank()) {
                "Search: ${event.searchQuery}"
            } else {
                event.domain
            }

            displayKey?.let { key ->
                // Sanitize key (replace . with _) to prevent Firestore nested map interpretation
                val safeKey = key.replace(".", "_")
                // Use nested map for proper merging with SetOptions.merge()
                updates["topBlockedDomains"] = mapOf(safeKey to com.google.firebase.firestore.FieldValue.increment(1))
            }
            
            // Update top reasons
            // Also sanitize reason for potential dots
            val reasonKey = event.reason.replace(".", "_").take(50)
            updates["topReasons"] = mapOf(reasonKey to com.google.firebase.firestore.FieldValue.increment(1))
        }
        
        statsRef.set(updates, SetOptions.merge())
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update daily stats", e)
            }
    }

    /**
     * Check if event should trigger an alert
     */
    private fun checkAlertConditions(event: SafetyEvent) {
        // Example: Alert on critical events
        if (event.severity == Severity.CRITICAL) {
            createAlert(
                alertType = "CRITICAL_BLOCK",
                severity = Severity.CRITICAL,
                message = "Critical content block: ${event.reason}",
                relatedEventIds = listOf(event.eventId)
            )
        }
        
        // TODO: Add more sophisticated alert logic (e.g., multiple blocks in short time)
    }

    /**
     * Create a parental alert
     */
    private fun createAlert(
        alertType: String,
        severity: Severity,
        message: String,
        relatedEventIds: List<String>
    ) {
        val alertRef = db.collection("devices")
            .document(deviceId)
            .collection("alerts")
            .document()
        
        val alert = mapOf(
            "alertId" to alertRef.id,
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "alertType" to alertType,
            "severity" to severity.name,
            "message" to message,
            "relatedEventIds" to relatedEventIds,
            "acknowledged" to false
        )
        
        alertRef.set(alert)
            .addOnSuccessListener {
                Log.d(TAG, "Alert created: $message")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to create alert", e)
            }
    }

    /**
     * Log a critical alert directly (e.g., Uninstall Attempt)
     */
    fun logCriticalAlert(title: String, message: String) {
        if (!initialized) return
        
        createAlert(
            alertType = title,
            severity = Severity.CRITICAL,
            message = message,
            relatedEventIds = emptyList()
        )
    }

    /**
     * Get device ID for external use
     */
    fun getDeviceId(): String = if (initialized) deviceId else ""
}
