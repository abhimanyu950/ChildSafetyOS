package com.childsafety.os.browser

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import android.webkit.WebView
import com.childsafety.os.ChildSafetyApp
import com.childsafety.os.ai.ImageRiskClassifier
import com.childsafety.os.ai.TfLiteManager
import com.childsafety.os.cloud.EventUploader
import com.childsafety.os.policy.AgeGroup
import com.childsafety.os.policy.ThresholdProvider
import com.childsafety.os.policy.TrustedImageDomains
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Async image analysis queue for ML-based content filtering.
 * 
 * Pipeline:
 * 1. Image URL received from JsBridge/HTTP interceptors
 * 2. Check if already processed (cache)
 * 3. Check if from trusted domain (skip ML)
 * 4. Download image bytes
 * 5. Run NSFW classifier via TfLiteManager
 * 6. Apply age-appropriate thresholds
 * 7. Notify WebView if blocked
 * 8. Log decision to Firebase
 * 
 * Design: Fail-open (never blocks synchronously to avoid lag)
 */
object ImageMlQueue {

    private const val TAG = "ImageMlQueue"
    
    // Coroutine scope for async processing
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Cache of processed images: imageId -> blocked
    private val processedCache = ConcurrentHashMap<String, Boolean>()
    
    // Pending images being analyzed
    private val pendingImages = ConcurrentHashMap<String, Boolean>()
    
    // WebView reference for UI updates (set by SafeBrowserActivity)
    @Volatile
    private var webViewRef: WebView? = null
    
    // Current age group for threshold selection
    @Volatile
    private var currentAgeGroup: AgeGroup = AgeGroup.CHILD

    /**
     * Set the WebView reference for UI updates.
     * Called by SafeBrowserActivity when WebView is ready.
     */
    fun setWebView(webView: WebView?) {
        webViewRef = webView
    }

    /**
     * Set the current age group for threshold selection.
     */
    fun setAgeGroup(ageGroup: AgeGroup) {
        currentAgeGroup = ageGroup
    }

    /**
     * Enqueue an image for async ML analysis.
     * This is the main entry point called by:
     * - JsBridge (from WebView JavaScript)
     * - HttpImageInterceptor (from VPN layer)
     * - ImageResponseHandler (from HTTP responses)
     */
    fun enqueue(
        context: Context,
        imageId: String,
        url: String
    ) {
        // Skip if already processed
        if (processedCache.containsKey(imageId)) {
            Log.d(TAG, "Skip (cached): $imageId")
            return
        }
        
        // Skip if currently processing
        if (pendingImages.putIfAbsent(imageId, true) != null) {
            Log.d(TAG, "Skip (pending): $imageId")
            return
        }
        
        // Check trusted domains (skip ML for known-safe sources)
        if (TrustedImageDomains.isTrusted(url)) {
            Log.d(TAG, "Skip (trusted): $url")
            processedCache[imageId] = false
            pendingImages.remove(imageId)
            return
        }

        // Launch async analysis
        scope.launch {
            try {
                analyzeImage(context, imageId, url)
            } catch (e: Exception) {
                Log.e(TAG, "Analysis failed (fail-open): $imageId", e)
                // Fail-open: allow content on error
                processedCache[imageId] = false
            } finally {
                pendingImages.remove(imageId)
            }
        }
    }

    /**
     * Core ML analysis pipeline.
     */
    private suspend fun analyzeImage(
        context: Context,
        imageId: String,
        url: String
    ) = withContext(Dispatchers.IO) {
        
        Log.d(TAG, "Analyzing: $imageId -> $url")

        // 1. Download image
        val imageBytes = downloadImage(url)
        if (imageBytes == null || imageBytes.isEmpty()) {
            Log.w(TAG, "Download failed: $url")
            processedCache[imageId] = false
            return@withContext
        }

        // 2. Decode to Bitmap
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        if (bitmap == null) {
            Log.w(TAG, "Decode failed: $url")
            processedCache[imageId] = false
            return@withContext
        }

        // 3. Run NSFW classification
        val riskResult = ImageRiskClassifier.analyze(context, bitmap)
        
        // 4. Apply age-appropriate thresholds
        val thresholds = ThresholdProvider.getThresholds(currentAgeGroup)
        val blocked = riskResult.porn >= thresholds.pornThreshold ||
                      riskResult.hentai >= thresholds.hentaiThreshold ||
                      riskResult.sexy >= thresholds.sexyThreshold

        Log.i(TAG, "Result: $imageId blocked=$blocked " +
                   "porn=${riskResult.porn} sexy=${riskResult.sexy} hentai=${riskResult.hentai}")

        // 5. Cache decision
        processedCache[imageId] = blocked
        ImageDecisionCache.put(imageId, blocked)

        // 6. Update WebView if blocked
        if (blocked) {
            webViewRef?.let { wv ->
                withContext(Dispatchers.Main) {
                    val blockJs = BlockedPageHtml.blockedImageJs(imageId)
                    wv.evaluateJavascript(blockJs, null)
                }
            }
        }

        // 7. Log to Firebase
        EventUploader.logImageMlDecision(
            deviceId = ChildSafetyApp.appDeviceId,
            imageId = imageId,
            url = url,
            blocked = blocked,
            ageGroup = currentAgeGroup.name,
            scores = mapOf(
                "porn" to riskResult.porn,
                "sexy" to riskResult.sexy,
                "hentai" to riskResult.hentai
            )
        )
        
        // Clean up bitmap
        bitmap.recycle()
    }

    /**
     * Download image bytes from URL.
     */
    private fun downloadImage(urlString: String): ByteArray? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "ChildSafetyOS/1.0")
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { it.readBytes() }
            } else {
                Log.w(TAG, "HTTP ${connection.responseCode}: $urlString")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Download error: $urlString", e)
            null
        }
    }

    /**
     * Check if an image was blocked (for cache lookup).
     */
    fun isBlocked(imageId: String): Boolean {
        return processedCache[imageId] ?: ImageDecisionCache.get(imageId) ?: false
    }

    /**
     * Clear all caches (for testing/reset).
     */
    fun clearCache() {
        processedCache.clear()
        pendingImages.clear()
    }

    /**
     * Cancel all pending operations (for cleanup).
     */
    fun shutdown() {
        scope.cancel()
    }
}
