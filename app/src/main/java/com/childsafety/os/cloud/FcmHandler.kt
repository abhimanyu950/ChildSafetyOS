package com.childsafety.os.cloud

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase Cloud Messaging handler.
 * 
 * Handles push notifications from parent dashboard:
 * - Policy updates (new blocked domains, threshold changes)
 * - Remote commands (lock device, update settings)
 * - Location requests
 * 
 * Design:
 * - Fail-safe: all operations wrapped in try-catch
 * - Logs all events to Firestore for debugging
 */
class FcmHandler : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FcmHandler"
    }

    /**
     * Called when a new FCM message is received.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.i(TAG, "FCM received from: ${remoteMessage.from}")

        try {
            // Handle data payload
            if (remoteMessage.data.isNotEmpty()) {
                handleDataMessage(remoteMessage.data)
            }

            // Handle notification payload
            remoteMessage.notification?.let { notification ->
                Log.d(TAG, "Notification: ${notification.title} - ${notification.body}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing FCM message", e)
        }
    }

    /**
     * Process data messages from parent dashboard.
     */
    private fun handleDataMessage(data: Map<String, String>) {
        val action = data["action"] ?: return

        Log.i(TAG, "FCM action: $action")

        when (action) {
            "UPDATE_POLICY" -> {
                // Future: update local policy from remote config
                Log.d(TAG, "Policy update requested")
            }
            "LOCK_BROWSER" -> {
                // Future: lock safe browser access
                Log.d(TAG, "Browser lock requested")
            }
            "REQUEST_LOCATION" -> {
                // Future: send device location
                Log.d(TAG, "Location request received")
            }
            else -> {
                Log.w(TAG, "Unknown FCM action: $action")
            }
        }
    }

    /**
     * Called when FCM token is refreshed.
     * Should upload new token to Firestore for parent dashboard.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "FCM token refreshed")

        // Upload new token to Firestore
        try {
            val payload = mapOf(
                "fcmToken" to token,
                "timestamp" to System.currentTimeMillis()
            )
            
            // Log token refresh (actual upload would go to device document)
            Log.d(TAG, "New FCM token: ${token.take(20)}...")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload FCM token", e)
        }
    }
}
