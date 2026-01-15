package com.childsafety.os.browser

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import android.webkit.WebView
import com.childsafety.os.ChildSafetyApp
import com.childsafety.os.ai.ImageRiskClassifier
import com.childsafety.os.ai.SkinRatioAnalyzer
import com.childsafety.os.ai.EdgeDensityAnalyzer
import com.childsafety.os.ai.TfLiteManager
import com.childsafety.os.cache.ImageHashCache
import com.childsafety.os.cloud.FirebaseManager
import com.childsafety.os.policy.AgeGroup
import com.childsafety.os.policy.ContentDecisionEngine
import com.childsafety.os.policy.Decision
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
    
    // Cache of processed images: imageId -> blocked (LRU limited to 1000 items)
    private val processedCache = java.util.Collections.synchronizedMap(
        object : java.util.LinkedHashMap<String, Boolean>(1000, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                return size > 1000
            }
        }
    )
    
    // Pending images being analyzed
    private val pendingImages = ConcurrentHashMap<String, Boolean>()
    
    // Priority queue for image processing (higher priority = process first)
    private data class PriorityImage(
        val imageId: String,
        val url: String,
        val priority: Int,
        val isInViewport: Boolean
    ) : Comparable<PriorityImage> {
        override fun compareTo(other: PriorityImage): Int = other.priority - this.priority
    }
    
    private val priorityQueue = java.util.concurrent.PriorityBlockingQueue<PriorityImage>()
    
    // WebView reference for UI updates (set by SafeBrowserActivity)
    @Volatile
    private var webViewRef: WebView? = null
    
    // Current age group for threshold selection
    @Volatile
    private var currentAgeGroup: AgeGroup = AgeGroup.CHILD
    
    // Listener for blocked events
    interface BlockListener {
        fun onImageBlocked(count: Int)
    }
    
    @Volatile
    private var blockListener: BlockListener? = null
    
    // Counter for blocked images in current session
    private val blockedCount = java.util.concurrent.atomic.AtomicInteger(0)

    fun setWebView(webView: WebView?, listener: BlockListener? = null) {
        webViewRef = webView
        blockListener = listener
        if (webView == null) {
            blockedCount.set(0) // Reset on destroy
        }
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
    // Rate limiting for search engines to prevent bot detection
    private val lastRequestTime = ConcurrentHashMap<String, Long>()
    private val MIN_SEARCH_ENGINE_DELAY_MS = 2000L // 2 seconds between requests for Google/Bing

    /**
     * Enqueue an image for async ML analysis.
     */
    fun enqueue(
        context: Context,
        imageId: String,
        url: String
    ) {
        // Skip if already processed
        if (processedCache.containsKey(imageId)) {
            return
        }
        
        // Skip if currently processing
        if (pendingImages.putIfAbsent(imageId, true) != null) {
            return
        }
        
        // Trust check: We now ANALYZE trusted domains (like Google Images) but with Rate Limiting
        // unless it's a whitelisted SAFE CDN that we know doesn't need checking.
        // For now, we trust specific CDNs but analyze Search Engines.
        
        // Launch async analysis
        scope.launch {
            try {
                // Apply rate limiting for search engines
                val host = try { URL(url).host } catch (e: Exception) { "" }
                if (TrustedImageDomains.isHostTrusted(host)) {
                    // It's a "trusted" domain (likely a search engine or Big Tech CDN)
                    // We throttle it to avoid "Unusual Traffic" bans
                    val lastTime = lastRequestTime[host] ?: 0L
                    val currentTime = System.currentTimeMillis()
                    val timeSinceLast = currentTime - lastTime
                    
                    if (timeSinceLast < MIN_SEARCH_ENGINE_DELAY_MS) {
                        val delayMs = MIN_SEARCH_ENGINE_DELAY_MS - timeSinceLast
                        delay(delayMs)
                    }
                    lastRequestTime[host] = System.currentTimeMillis()
                }

                analyzeImage(context, imageId, url)
            } catch (e: Exception) {
                Log.e(TAG, "Analysis failed (fail-open): $imageId", e)
                processedCache[imageId] = false
            } finally {
                pendingImages.remove(imageId)
            }
        }
    }
    
    /**
     * Enqueue with priority for progressive loading (viewport-aware).
     */
    fun enqueuePriority(
        context: Context,
        imageId: String,
        url: String,
        priority: Int,
        isInViewport: Boolean
    ) {
        // Skip if already processed
        if (processedCache.containsKey(imageId)) {
            return
        }
        
        // Skip if currently processing
        if (pendingImages.putIfAbsent(imageId, true) != null) {
            return
        }
        
        val prefix = if (isInViewport) "👁️" else "📦"
        Log.d(TAG, "$prefix Enqueued (priority=$priority): $imageId")
        
        // Add to priority queue and process
        priorityQueue.offer(PriorityImage(imageId, url, priority, isInViewport))
        
        // Trigger processing
        scope.launch {
            val item = priorityQueue.poll()
            if (item != null) {
                try {
                    analyzeImage(context, item.imageId, item.url)
                } catch (e: Exception) {
                    Log.e(TAG, "Analysis failed: ${item.imageId}", e)
                    processedCache[item.imageId] = false
                } finally {
                    pendingImages.remove(item.imageId)
                }
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

        // 0. Battery Optimization Check
        // If battery is low (< 20%) and NOT charging, skip expensive ML to save power.
        if (isBatteryLow(context)) {
            Log.w(TAG, "🔋 Battery low! Skipping ML for: $imageId")
            processedCache[imageId] = false // Treat as safe (Fail Open)
            unblurSafeImage(imageId)
            return@withContext
        }

        // 1. Download image
        val imageBytes = downloadImage(url)
        if (imageBytes == null || imageBytes.isEmpty()) {
            Log.w(TAG, "Download failed: $url")
            processedCache[imageId] = false
            // Unblur anyway (download failure, not explicit content)
            unblurSafeImage(imageId)
            return@withContext
        }

        // 2. Decode to Bitmap
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        if (bitmap == null) {
            Log.w(TAG, "Decode failed: $url")
            processedCache[imageId] = false
            unblurSafeImage(imageId)
            return@withContext
        }

        // 3. Check if from trusted domain (INSTANT ~0ms)
        if (TrustedImageDomains.isTrusted(url)) {
            Log.i(TAG, "✅ TRUSTED DOMAIN: $imageId -> ${extractDomain(url)} (instant unblur!)")
            processedCache[imageId] = false // Mark as safe
            ImageDecisionCache.put(imageId, false)
            unblurSafeImage(imageId)
            bitmap.recycle()
            return@withContext
        }

        // 4. Calculate perceptual hash (FAST ~50ms)
        val imageHash = ImageHashCache.calculateHash(bitmap)
        
        // 5. Check cache for instant decision
        val cachedDecision = ImageHashCache.getDecision(imageHash)
        if (cachedDecision != null) {
            Log.i(TAG, "⚡ CACHE HIT: $imageId -> blocked=$cachedDecision (instant!)")
            processedCache[imageId] = cachedDecision
            ImageDecisionCache.put(imageId, cachedDecision)
            
            if (cachedDecision) {
                // Cached as blocked - keep blurred
                blockImageInWebView(imageId)
            } else {
                // Cached as safe - unblur
                unblurSafeImage(imageId)
            }
            
            bitmap.recycle()
            return@withContext
        }
        
        // 6. Cache MISS - Run ML classification with multi-modal analysis
        Log.i(TAG, "Cache MISS: $imageId. Running multi-modal analysis...")
        val riskResult = ImageRiskClassifier.analyze(context, bitmap)
        
        // Run auxiliary analyzers for false-positive prevention
        val skinRatio = SkinRatioAnalyzer.analyze(bitmap)
        val edgeDensity = EdgeDensityAnalyzer.analyze(bitmap)
        
        // 7. Create signals and run decision engine
        val signals = ContentDecisionEngine.createSignalsFromImage(
            imageResult = riskResult,
            skinRatio = skinRatio,
            edgeDensity = edgeDensity,
            activeMode = currentAgeGroup
        )
        
        val decision = ContentDecisionEngine.decide(signals)
        
        // CRITICAL: Block for both BLOCK and UNCERTAIN decisions in CHILD mode
        // Only ALLOW should unblur content
        val blocked = decision.decision == Decision.BLOCK || 
                      (decision.decision == Decision.UNCERTAIN && currentAgeGroup == AgeGroup.CHILD)
        val shouldBlur = decision.decision != Decision.ALLOW // Keep blurred for WARN and UNCERTAIN too
        
        Log.w(TAG, "🔍 DECISION: $imageId -> ${decision.decision} " +
                   "mode=$currentAgeGroup blocked=$blocked " +
                   "porn=${String.format("%.2f", riskResult.porn)} " +
                   "sexy=${String.format("%.2f", riskResult.sexy)} " +
                   "hentai=${String.format("%.2f", riskResult.hentai)} " +
                   "drawing=${String.format("%.2f", riskResult.drawing)} " +
                   "reason=\"${decision.dominantReason}\"")
        
        // 🚨 CRITICAL ALERT: If highly explicit (porn > 0.8), send email to parent
        if (riskResult.porn >= 0.8) {
            try {
                val deviceId = com.childsafety.os.ChildSafetyApp.appDeviceId
                val childName = "Child" // TODO: Get from SharedPreferences/profile
                
                Log.w(TAG, "⚠️ CRITICAL: Highly explicit content detected! porn=${riskResult.porn}")
                
                com.childsafety.os.cloud.FcmHandler.sendCriticalAlert(
                    context = context,
                    deviceId = deviceId,
                    childName = childName,
                    imageUrl = url,
                    pornScore = riskResult.porn.toDouble(),
                    sexyScore = riskResult.sexy.toDouble(),
                    hentaiScore = riskResult.hentai.toDouble(),
                    blockedReason = "Pornographic content (${(riskResult.porn * 100).toInt()}% confidence)"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send critical alert", e)
            }
        }
        
        // 5. Cache decision
        processedCache[imageId] = blocked
        ImageDecisionCache.put(imageId, blocked)

        // 6. Cache the decision for future instant recognition
        ImageHashCache.cacheDecision(imageHash, blocked)
        
        // 7. Update WebView based on decision
        when {
            blocked -> {
                val newCount = blockedCount.incrementAndGet()
                
                // Notify listener (SafeBrowserActivity) to potentially block the whole page
                blockListener?.onImageBlocked(newCount)
                
                // Keep blurred (already blurred by default)
                blockImageInWebView(imageId)
            }
            shouldBlur -> {
                // WARN or UNCERTAIN - keep blurred but don't count as blocked
                Log.i(TAG, "Keeping blur for WARN/UNCERTAIN: $imageId")
                // Don't unblur - leave image blurred
            }
            else -> {
                // ALLOW - Unblur with smooth transition
                unblurSafeImage(imageId)
            }
        }

        // 8. Log to Firebase with comprehensive analytics
        if (blocked) {
            val scores = mapOf(
                "porn" to riskResult.porn.toDouble(),
                "sexy" to riskResult.sexy.toDouble(),
                "hentai" to riskResult.hentai.toDouble(),
                "drawing" to riskResult.drawing.toDouble(),
                "skinRatio" to skinRatio.toDouble(),
                "edgeDensity" to edgeDensity.toDouble()
            )
            
            val reason = "Decision: ${decision.decision} - ${decision.dominantReason}" +
                         (decision.downgradeReason?.let { " [Downgraded: $it]" } ?: "")
            
            FirebaseManager.logImageBlock(
                imageUrl = url,
                mlScores = scores,
                threshold = mapOf(
                    "mode" to currentAgeGroup.ordinal.toDouble(),
                    "blocked" to if (blocked) 1.0 else 0.0
                ),
                reason = reason,
                url = null, // Page URL not available here
                domain = null
            )
        }
        
        // Clean up bitmap
        bitmap.recycle()
    }
    
    /**
     * Block an image in the WebView (keep blurred, add red overlay).
     */
    private suspend fun blockImageInWebView(imageId: String) {
        webViewRef?.let { wv ->
            withContext(Dispatchers.Main) {
                wv.evaluateJavascript(JsScripts.blockedImageScript(imageId), null)
            }
        }
    }
    
    /**
     * Unblur a safe image in the WebView with smooth transition.
     */
    private suspend fun unblurSafeImage(imageId: String) {
        webViewRef?.let { wv ->
            withContext(Dispatchers.Main) {
                wv.evaluateJavascript(JsScripts.unblurSafeImageScript(imageId), null)
            }
        }
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
    
    /**
     * Extract domain from URL for logging.
     */
    private fun extractDomain(url: String): String {
        return try {
            java.net.URL(url).host
        } catch (e: Exception) {
            url.take(50)
        }
    }

    /**
     * Check if battery is low (< 20%) and not charging.
     */
    private fun isBatteryLow(context: Context): Boolean {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val batteryLevel = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val isCharging = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val status = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_STATUS)
                status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || 
                status == android.os.BatteryManager.BATTERY_STATUS_FULL
            } else {
                false // Fallback for old devices
            }
            
            // If strictly low and not plugging in
            return batteryLevel < 20 && !isCharging
        } catch (e: Exception) {
            false // Process usually if we can't check
        }
    }
}
