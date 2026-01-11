package com.childsafety.os.cloud

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.text.SimpleDateFormat
import java.util.*

/**
 * Firebase Cloud Messaging handler + Email Alert System.
 * 
 * Handles:
 * 1. Push notifications from parent dashboard
 * 2. Critical email alerts for highly explicit content
 * 
 * Email Alerts Triggered When:
 * - Porn score > 0.8 (very explicit)
 * - 5+ blocks within 10 minutes (suspicious activity)
 * - Page-level blocks (multiple explicit images)
 */
class FcmHandler : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FcmHandler"
        private const val CRITICAL_PORN_THRESHOLD = 0.8 // Highly explicit
        
        /**
         * Trigger email alert for critical explicit content detection.
         * Called from ImageMlQueue when very explicit content is found.
         */
        fun sendCriticalAlert(
            context: Context,
            deviceId: String,
            childName: String,
            imageUrl: String,
            pornScore: Double,
            sexyScore: Double,
            hentaiScore: Double,
            blockedReason: String
        ) {
            try {
                val timestamp = System.currentTimeMillis()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val readableDate = dateFormat.format(Date(timestamp))
                
                // Create alert document
                val alert = hashMapOf(
                    "type" to "CRITICAL_EXPLICIT_CONTENT",
                    "severity" to "HIGH",
                    "deviceId" to deviceId,
                    "childName" to childName,
                    "timestamp" to timestamp,
                    "readableDate" to readableDate,
                    
                    // Content details
                    "imageUrl" to imageUrl,
                    "mlScores" to mapOf(
                        "porn" to pornScore,
                        "sexy" to sexyScore,
                        "hentai" to hentaiScore,
                        "totalRisk" to (pornScore + sexyScore + hentaiScore) / 3
                    ),
                    "reason" to blockedReason,
                    
                    // Email template data
                    "emailSubject" to "🚨 ALERT: Highly Explicit Content Detected",
                    "emailBody" to buildEmailBody(
                        childName, 
                        deviceId, 
                        readableDate, 
                        imageUrl, 
                        pornScore, 
                        sexyScore, 
                        hentaiScore,
                        blockedReason
                    ),
                    
                    // Status
                    "read" to false,
                    "emailSent" to false,
                    "notificationSent" to false
                )
                
                // Save to Firestore alerts collection
                FirebaseFirestore.getInstance()
                    .collection("alerts")
                    .add(alert)
                    .addOnSuccessListener { 
                        Log.i(TAG, "Critical alert saved: porn=$pornScore")
                        
                        // Trigger Cloud Function to send email
                        // (Cloud Function will watch alerts collection and send emails)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to save alert", e)
                    }
                    
            } catch (e: Exception) {
                Log.e(TAG, "Error creating critical alert", e)
            }
        }
        
        /**
         * Build HTML email body with all details.
         */
        private fun buildEmailBody(
            childName: String,
            deviceId: String,
            dateTime: String,
            imageUrl: String,
            pornScore: Double,
            sexyScore: Double,
            hentaiScore: Double,
            reason: String
        ): String {
            val riskLevel = when {
                pornScore > 0.9 -> "EXTREMELY HIGH"
                pornScore > 0.8 -> "VERY HIGH"
                pornScore > 0.6 -> "HIGH"
                else -> "MODERATE"
            }
            
            return """
                <!DOCTYPE html>
                <html>
                <head>
                    <style>
                        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                        .header { background: #dc2626; color: white; padding: 20px; border-radius: 8px 8px 0 0; }
                        .content { background: #f9f9f9; padding: 20px; border: 1px solid #ddd; }
                        .alert-box { background: #fef2f2; border-left: 4px solid #dc2626; padding: 15px; margin: 15px 0; }
                        .detail-row { margin: 10px 0; padding: 10px; background: white; border-radius: 4px; }
                        .label { font-weight: bold; color: #666; }
                        .value { color: #111; }
                        .score { font-size: 24px; font-weight: bold; color: #dc2626; }
                        .footer { background: #333; color: white; padding: 15px; text-align: center; border-radius: 0 0 8px 8px; }
                        .btn { display: inline-block; padding: 12px 24px; background: #6366f1; color: white; text-decoration: none; border-radius: 6px; margin-top: 15px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>🚨 Critical Safety Alert</h1>
                            <p>ChildSafetyOS has detected highly explicit content</p>
                        </div>
                        
                        <div class="content">
                            <div class="alert-box">
                                <strong>⚠️ IMMEDIATE ATTENTION REQUIRED</strong>
                                <p>Your child attempted to access content with an explicit content score of <span class="score">${(pornScore * 100).toInt()}%</span></p>
                                <p>Risk Level: <strong>$riskLevel</strong></p>
                            </div>
                            
                            <h3>Incident Details:</h3>
                            
                            <div class="detail-row">
                                <span class="label">Child Name:</span>
                                <span class="value">$childName</span>
                            </div>
                            
                            <div class="detail-row">
                                <span class="label">Device ID:</span>
                                <span class="value">$deviceId</span>
                            </div>
                            
                            <div class="detail-row">
                                <span class="label">Date & Time:</span>
                                <span class="value">$dateTime</span>
                            </div>
                            
                            <div class="detail-row">
                                <span class="label">Content Type:</span>
                                <span class="value">$reason</span>
                            </div>
                            
                            <h3>AI Analysis Results:</h3>
                            
                            <div class="detail-row">
                                <span class="label">Pornographic Content:</span>
                                <span class="value" style="color: ${if (pornScore > 0.8) "#dc2626" else "#666"};">${(pornScore * 100).toInt()}% confidence</span>
                            </div>
                            
                            <div class="detail-row">
                                <span class="label">Sexual Content:</span>
                                <span class="value">${(sexyScore * 100).toInt()}% confidence</span>
                            </div>
                            
                            <div class="detail-row">
                                <span class="label">Anime/Hentai:</span>
                                <span class="value">${(hentaiScore * 100).toInt()}% confidence</span>
                            </div>
                            
                            <div class="detail-row">
                                <span class="label">Source URL:</span>
                                <span class="value" style="font-size: 11px; word-break: break-all;">${imageUrl.take(100)}${if (imageUrl.length > 100) "..." else ""}</span>
                            </div>
                            
                            <h3>Actions Taken:</h3>
                            <ul>
                                <li>✅ Content immediately blocked and blurred</li>
                                <li>✅ Page access prevented</li>
                                <li>✅ Event logged to analytics dashboard</li>
                                <li>✅ Image added to blocked cache</li>
                            </ul>
                            
                            <h3>Recommendations:</h3>
                            <ul>
                                <li>🔍 Review the full activity log in your dashboard</li>
                                <li>💬 Have an age-appropriate conversation with your child</li>
                                <li>⚙️ Consider adjusting protection settings if needed</li>
                                <li>📱 Check for any bypassing attempts (VPN apps, etc.)</li>
                            </ul>
                            
                            <a href="http://localhost:8000" class="btn">View Dashboard</a>
                        </div>
                        
                        <div class="footer">
                            <p><strong>ChildSafetyOS</strong> - Protecting children in the digital world</p>
                            <p style="font-size: 12px; margin-top: 10px;">This is an automated alert. Do not reply to this email.</p>
                        </div>
                    </div>
                </body>
                </html>
            """.trimIndent()
        }
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
