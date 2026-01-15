package com.childsafety.os.browser

import android.content.Context
import android.webkit.JavascriptInterface

class JsBridge(private val context: Context) {

    @JavascriptInterface
    fun onImageFound(imageId: String, url: String) {
        ImageMlQueue.enqueue(context, imageId, url)
    }
    
    @JavascriptInterface
    fun onImageFoundPriority(imageId: String, url: String, priority: Int, isInViewport: Boolean) {
        ImageMlQueue.enqueuePriority(context, imageId, url, priority, isInViewport)
    }

    /* -------- VIDEO SUPPORT -------- */
    
    @JavascriptInterface
    fun onVideoFrame(videoId: String, frameDataUrl: String, sourceUrl: String) {
        // Delegate to VideoFrameAnalyzer
        // Blocking is handled internally via WebView reference
        VideoFrameAnalyzer.processFrame(context, videoId, frameDataUrl, sourceUrl)
    }

    @JavascriptInterface
    fun onVideoCount(count: Int) {
        // Optional: log video count or adjust sampling rate
    }

    @JavascriptInterface
    fun onVideoBlocked(videoId: String) {
        // Callback from JS when video is successfully blocked in DOM
    }

    @JavascriptInterface
    fun onVideoReported(videoId: String, reason: String) {
        // Log report to Firebase or handle it
        // For now, just a stub or basic log
        android.util.Log.i("JsBridge", "Video reported as mistake: $videoId ($reason)")
    }
}
