package com.childsafety.os.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.webkit.WebView
import com.childsafety.os.ai.ImageRiskClassifier
import com.childsafety.os.cloud.FirebaseManager
import com.childsafety.os.policy.AgeGroup
import com.childsafety.os.policy.ThresholdProvider
import kotlinx.coroutines.*

/**
 * Video Frame Analyzer - Captures and analyzes frames from HTML5 <video> elements.
 * 
 * Why: Kids watch YouTube/TikTok - static image blocking isn't enough.
 * We sample video frames every 2-3 seconds and run through ML classifier.
 * 
 * How it works:
 * 1. JavaScript injection finds all <video> elements
 * 2. Canvas captures current frame as base64
 * 3. Frame sent to Kotlin via JavaScript bridge
 * 4. ImageRiskClassifier analyzes frame
 * 5. If explicit, pause/block video
 */
object VideoFrameAnalyzer {

    private const val TAG = "VideoFrameAnalyzer"
    private const val SAMPLE_INTERVAL_MS = 5000L // Sample every 5 seconds to reduce lag
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var isAnalyzing = false
    private var analysisJob: Job? = null
    private var currentAgeGroup: AgeGroup = AgeGroup.CHILD
    private var currentWebView: WebView? = null
    
    // Track analyzed video elements
    private val analyzedVideos = mutableSetOf<String>()
    
    // Stats
    private var framesAnalyzed = 0
    private var framesBlocked = 0

    /**
     * JavaScript to inject for video frame capture
     */
    val VIDEO_FRAME_CAPTURE_JS = """
        (function() {
            // Find all video elements
            const videos = document.querySelectorAll('video');
            
            if (videos.length === 0) {
                if (window.ChildSafety) {
                    ChildSafety.onVideoCount(0);
                }
                return;
            }
            
            // Send video count to Kotlin bridge
            if (window.ChildSafety) {
                ChildSafety.onVideoCount(videos.length);
                
                videos.forEach((video, index) => {
                    // Mark video with ID if not already
                    if (!video.dataset.csosId) {
                        video.dataset.csosId = 'video_' + index + '_' + Date.now();
                    }
                    
                    const videoId = video.dataset.csosId;
                    
                    // Skip if already has overlay (blocked)
                    if (video.dataset.csosBlocked === 'true') {
                        return;
                    }
                    
                    // Skip if video is not playing or not loaded
                    if (video.paused || video.readyState < 2) {
                        return;
                    }
                    
                    // Create canvas to capture frame
                    const canvas = document.createElement('canvas');
                    canvas.width = Math.min(video.videoWidth || 320, 320);
                    canvas.height = Math.min(video.videoHeight || 240, 240);
                    
                    const ctx = canvas.getContext('2d');
                    
                    try {
                        // Draw current frame to canvas
                        ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
                        
                        // Convert to base64 (JPEG for smaller size)
                        const frameData = canvas.toDataURL('image/jpeg', 0.6);
                        
                        // Send to Kotlin bridge
                        ChildSafety.onVideoFrame(videoId, frameData, video.src || window.location.href);
                    } catch (e) {
                        // CORS error - video from different origin
                        console.log('ChildSafetyOS: Cannot capture frame due to CORS:', e);
                    }
                });
            }
        })();
    """.trimIndent()

    /**
     * JavaScript to block/blur a video
     * Uses full-page overlay approach to handle iframe-embedded videos (like YouTube)
     */
    fun getBlockVideoJs(videoId: String, reason: String = "Explicit content detected"): String {
        return """
            (function() {
                // Check if already blocked
                if (document.getElementById('csos-block-overlay')) {
                    return;
                }
                
                // Try to pause any video elements we can access
                const videos = document.querySelectorAll('video');
                videos.forEach((video) => {
                    try {
                        video.pause();
                        video.muted = true;
                    } catch(e) {}
                });
                
                // Create FULL PAGE block overlay (works for YouTube iframes too)
                const overlay = document.createElement('div');
                overlay.id = 'csos-block-overlay';
                overlay.style.cssText = 'position:fixed!important;top:0!important;left:0!important;width:100vw!important;height:100vh!important;' +
                    'background:rgba(20,20,30,0.98)!important;display:flex!important;flex-direction:column!important;' +
                    'align-items:center!important;justify-content:center!important;color:white!important;font-family:-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif!important;' +
                    'z-index:2147483647!important;text-align:center!important;padding:20px!important;box-sizing:border-box!important;';
                    
                overlay.innerHTML = 
                    '<div style="font-size:64px;margin-bottom:20px;">🛡️</div>' +
                    '<div style="font-size:28px;font-weight:700;margin-bottom:12px;color:#ff4444;">Video Blocked</div>' +
                    '<div style="font-size:16px;margin-bottom:24px;opacity:0.9;max-width:350px;line-height:1.5;">$reason</div>' +
                    '<div style="font-size:13px;opacity:0.6;margin-bottom:32px;">Protected by ChildSafetyOS</div>' +
                    '<button id="btn_report_$videoId" style="background:rgba(255,255,255,0.15);border:1px solid rgba(255,255,255,0.3);color:white;padding:12px 24px;border-radius:25px;font-size:14px;cursor:pointer;">Report Mistake</button>' +
                    '<button id="btn_goback" style="background:#ff4444;border:none;color:white;padding:12px 24px;border-radius:25px;font-size:14px;cursor:pointer;margin-top:12px;">Go Back</button>';
                
                document.body.appendChild(overlay);
                
                // Handle buttons
                setTimeout(() => {
                    const reportBtn = document.getElementById('btn_report_$videoId');
                    if (reportBtn) {
                        reportBtn.onclick = function() {
                            reportBtn.innerText = "Report Sent ✓";
                            reportBtn.style.background = "#4CAF50";
                            reportBtn.style.borderColor = "#4CAF50";
                            if (window.ChildSafety) {
                                ChildSafety.onVideoReported('$videoId', '$reason');
                            }
                        };
                    }
                    
                    const backBtn = document.getElementById('btn_goback');
                    if (backBtn) {
                        backBtn.onclick = function() {
                            history.back();
                        };
                    }
                }, 100);
                
                // Notify bridge
                if (window.ChildSafety) {
                    ChildSafety.onVideoBlocked('$videoId');
                }
                
                console.log('ChildSafetyOS: Video blocked with full-page overlay');
            })();
        """.trimIndent()
    }

    /**
     * Start periodic video frame analysis
     */
    fun startAnalysis(context: Context, webView: WebView, ageGroup: AgeGroup) {
        if (isAnalyzing) return
        
        currentAgeGroup = ageGroup
        currentWebView = webView
        isAnalyzing = true
        
        analysisJob = scope.launch {
            while (isActive && isAnalyzing) {
                delay(SAMPLE_INTERVAL_MS)
                
                // Inject JavaScript to capture frames
                withContext(Dispatchers.Main) {
                    currentWebView?.evaluateJavascript(VIDEO_FRAME_CAPTURE_JS, null)
                }
            }
        }
        
        Log.i(TAG, "Video analysis started for age group: $ageGroup")
    }

    /**
     * Stop video frame analysis
     */
    fun stopAnalysis() {
        isAnalyzing = false
        analysisJob?.cancel()
        analysisJob = null
        currentWebView = null
        Log.i(TAG, "Video analysis stopped. Analyzed: $framesAnalyzed, Blocked: $framesBlocked")
    }

    /**
     * Process a captured video frame
     * Called from JavaScript bridge
     */
    // Lazy Antigravity Kernel
    @Volatile
    private var antigravityKernel: com.childsafety.os.policy.AntigravityRiskEngine? = null

    /**
     * Process a captured video frame
     * Called from JavaScript bridge
     */
    fun processFrame(
        context: Context,
        videoId: String,
        frameDataUrl: String,
        sourceUrl: String
    ) {
        scope.launch {
            try {
                // Initialize Kernel if needed
                if (antigravityKernel == null) {
                    synchronized(this) {
                        if (antigravityKernel == null) {
                            antigravityKernel = com.childsafety.os.policy.AntigravityRiskEngine(context.applicationContext)
                        }
                    }
                }

                // Skip if already analyzed this video (per-video block state)
                if (analyzedVideos.contains(videoId)) return@launch
                
                // Decode base64 image
                val base64Data = frameDataUrl.substringAfter("base64,")
                val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    ?: return@launch
                
                framesAnalyzed++
                
                // 1. Gather Signals
                val domain = try { java.net.URL(sourceUrl).host } catch (e: Exception) { "" }
                val trust = com.childsafety.os.policy.DomainPolicy.getTrustLevel(domain)
                val netScore = com.childsafety.os.policy.DomainPolicy.getDomainRiskScore(domain)
                
                val signals = com.childsafety.os.policy.RiskSignals(
                    networkScore = netScore.toFloat(),
                    jsScore = 0f, // Video context only
                    trust = trust
                )
                
                // 2. Compute Antigravity Risk (Video Mode = True)
                // This applies EMA smoothing and Trust dampening
                val riskScore = antigravityKernel!!.computeRisk(bitmap, signals, true, currentAgeGroup)
                
                // 3. Determine Action
                val shouldBlock = when (currentAgeGroup) {
                    AgeGroup.CHILD -> riskScore >= 30f
                    AgeGroup.TEEN -> riskScore >= 50f
                    AgeGroup.ADULT -> riskScore >= 80f
                }
                
                if (shouldBlock) {
                    framesBlocked++
                    analyzedVideos.add(videoId)
                    
                    val blockReason = "Video Blocked: Risk Score ${riskScore.toInt()}"

                    Log.w(TAG, "BLOCKED video frame: $videoId (Score: $riskScore)")
                    
                    // Log to Firebase
                    FirebaseManager.logVideoBlock(
                        videoUrl = sourceUrl,
                        mlScores = mapOf(
                            "antigravity_score" to riskScore.toDouble()
                        ),
                        threshold = mapOf(
                            "mode" to currentAgeGroup.ordinal.toDouble(),
                            "risk_score" to riskScore.toDouble()
                        ),
                        reason = blockReason,
                        url = sourceUrl,
                        domain = domain
                    )
                    
                    // Navigate WebView to blocked page
                    withContext(Dispatchers.Main) {
                        currentWebView?.let { webView ->
                            val blockedHtml = BlockedPageHtml.blockedVideoPage(
                                reason = blockReason,
                                domain = domain ?: ""
                            )
                            webView.loadDataWithBaseURL(
                                null, blockedHtml, "text/html", "UTF-8", null
                            )
                        }
                    }
                } else {
                    Log.d(TAG, "Video frame OK: $riskScore")
                }
                
                bitmap.recycle()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing video frame", e)
            }
        }
    }

    /**
     * Reset analysis state (call on new page load)
     */
    fun reset() {
        analyzedVideos.clear()
        framesAnalyzed = 0
        framesBlocked = 0
        // Reset kernel state (EMA/Cache) for new page
        antigravityKernel = null
    }

    /**
     * Get analysis stats
     */
    fun getStats(): Pair<Int, Int> = Pair(framesAnalyzed, framesBlocked)
}
