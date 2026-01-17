package com.childsafety.os.cloud

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

/**
 * Uploads safety events to Firebase Firestore.
 * Events are synced to Elasticsearch via Cloud Functions.
 * 
 * Fail-safe: All methods catch exceptions and log locally
 * to ensure the app never crashes due to network issues.
 */
object EventUploader {

    private const val TAG = "EventUploader"
    private const val COLLECTION_EVENTS = "events"

    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e(TAG, "Firestore not available", e)
            null
        }
    }

    fun logAppStart(
        apiLevel: String,
        deviceId: String
    ) {
        val payload = mutableMapOf<String, Any>(
            "apiLevel" to apiLevel,
            "deviceId" to deviceId
        )
        val event = mutableMapOf<String, Any>(
            "type" to "APP_START",
            "source" to "android",
            "timestamp" to Date(),
            "payload" to payload
        )
        uploadEvent(event)
        Log.i(TAG, "APP_START device=$deviceId api=$apiLevel")
    }

    fun logBlockedDomain(
        domain: String,
        deviceId: String
    ) {
        val payload = mutableMapOf<String, Any>(
            "domain" to domain,
            "deviceId" to deviceId
        )
        val event = mutableMapOf<String, Any>(
            "type" to "DOMAIN_BLOCKED",
            "source" to "android",
            "timestamp" to Date(),
            "payload" to payload
        )
        uploadEvent(event)
        Log.i(TAG, "DOMAIN_BLOCKED device=$deviceId domain=$domain")
    }

    fun logImageMlDecision(
        deviceId: String,
        imageId: String,
        url: String,
        blocked: Boolean,
        ageGroup: String,
        scores: Map<String, Float>
    ) {
        val payload = mutableMapOf<String, Any>(
            "deviceId" to deviceId,
            "imageId" to imageId,
            "url" to url,
            "blocked" to blocked,
            "ageGroup" to ageGroup,
            "scores" to scores
        )
        val event = mutableMapOf<String, Any>(
            "type" to "IMAGE_ML_DECISION",
            "source" to "android",
            "timestamp" to Date(),
            "payload" to payload
        )
        uploadEvent(event)
        Log.i(TAG, "IMAGE_ML_DECISION device=$deviceId id=$imageId blocked=$blocked")
    }

    fun logSafeBrowserBlock(
        deviceId: String,
        url: String,
        reason: String
    ) {
        val payload = mutableMapOf<String, Any>(
            "deviceId" to deviceId,
            "url" to url,
            "reason" to reason
        )
        val event = mutableMapOf<String, Any>(
            "type" to "SAFE_BROWSER_BLOCK",
            "source" to "android",
            "timestamp" to Date(),
            "payload" to payload
        )
        uploadEvent(event)
        Log.i(TAG, "SAFE_BROWSER_BLOCK device=$deviceId url=$url reason=$reason")
    }

    /**
     * Log structured Risk Engine decision.
     * Expects a JSON string which is stored as payload.
     */
    fun logRiskEvent(jsonPayload: String) {
        // Convert JSON string to Map if possible, or store as string
        // For simplicity we store as string in a field called "riskData"
        val event = mutableMapOf<String, Any>(
            "type" to "RISK_ENGINE_DECISION",
            "source" to "android",
            "timestamp" to Date(),
            "payload" to mapOf("riskData" to jsonPayload)
        )
        uploadEvent(event)
    }

    private fun uploadEvent(event: Map<String, Any>) {
        try {
            firestore?.collection(COLLECTION_EVENTS)
                ?.add(event)
                ?.addOnSuccessListener { doc ->
                    Log.d(TAG, "Event uploaded: ${doc.id}")
                }
                ?.addOnFailureListener { e ->
                    Log.w(TAG, "Event upload failed (will retry)", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Upload exception (continuing)", e)
        }
    }
}

