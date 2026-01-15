package com.childsafety.os.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log

/**
 * Video frame analysis result with consistency tracking.
 */
data class VideoRiskResult(
    val maxPorn: Float,
    val maxSexy: Float,
    val maxHentai: Float,
    val maxDrawing: Float,
    /** Number of consecutive frames flagged as NSFW */
    val consecutiveNsfwFrames: Int,
    /** Average skin ratio across sampled frames */
    val avgSkinRatio: Float,
    /** Average edge density across sampled frames */
    val avgEdgeDensity: Float,
    /** Total frames analyzed */
    val totalFrames: Int
)

/**
 * Analyzes video frames for NSFW content with stability tracking.
 * 
 * Key principle: Require multiple consecutive NSFW frames before
 * flagging - this prevents false positives from single frame glitches.
 */
object VideoRiskAnalyzer {

    private const val TAG = "VideoRiskAnalyzer"
    private const val EARLY_STOP_THRESHOLD = 0.65f
    private const val NSFW_FRAME_THRESHOLD = 0.40f

    /**
     * Legacy analyze function - returns max score for backward compatibility.
     */
    fun analyze(
        context: Context,
        frames: List<Bitmap>
    ): Float {
        val result = analyzeWithConsistency(context, frames)
        return maxOf(result.maxPorn, result.maxSexy, result.maxHentai)
    }

    /**
     * Full analysis with consistency tracking for the decision engine.
     * 
     * @param context Android context for ML model access
     * @param frames List of video frames to analyze
     * @return VideoRiskResult with all signals
     */
    fun analyzeWithConsistency(
        context: Context,
        frames: List<Bitmap>
    ): VideoRiskResult {

        if (frames.isEmpty()) {
            Log.w(TAG, "No frames provided")
            return VideoRiskResult(
                maxPorn = 0f,
                maxSexy = 0f,
                maxHentai = 0f,
                maxDrawing = 0f,
                consecutiveNsfwFrames = 0,
                avgSkinRatio = 0f,
                avgEdgeDensity = 0f,
                totalFrames = 0
            )
        }

        var maxPorn = 0f
        var maxSexy = 0f
        var maxHentai = 0f
        var maxDrawing = 0f
        
        var currentConsecutive = 0
        var maxConsecutive = 0
        
        var totalSkinRatio = 0f
        var totalEdgeDensity = 0f
        var analyzedFrames = 0

        for (frame in frames) {
            // Get NSFW scores
            val result = ImageRiskClassifier.analyze(context, frame)
            
            // Get auxiliary signals (sample every other frame for performance)
            val skinRatio = if (analyzedFrames % 2 == 0) {
                SkinRatioAnalyzer.analyze(frame)
            } else {
                0f
            }
            
            val edgeDensity = if (analyzedFrames % 2 == 0) {
                EdgeDensityAnalyzer.analyze(frame)
            } else {
                0f
            }
            
            // Track maximums
            maxPorn = maxOf(maxPorn, result.porn)
            maxSexy = maxOf(maxSexy, result.sexy)
            maxHentai = maxOf(maxHentai, result.hentai)
            maxDrawing = maxOf(maxDrawing, result.drawing)
            
            totalSkinRatio += skinRatio
            totalEdgeDensity += edgeDensity
            analyzedFrames++
            
            // Track consecutive NSFW frames
            val frameScore = maxOf(result.porn, result.hentai)
            if (frameScore >= NSFW_FRAME_THRESHOLD) {
                currentConsecutive++
                maxConsecutive = maxOf(maxConsecutive, currentConsecutive)
            } else {
                currentConsecutive = 0
            }

            // Early stop for obvious NSFW with multiple confirmed frames
            if (maxPorn >= EARLY_STOP_THRESHOLD && maxConsecutive >= 2) {
                Log.i(TAG, "Early stop: porn=$maxPorn, consecutive=$maxConsecutive")
                break
            }
        }

        val auxFrameCount = (analyzedFrames + 1) / 2 // Only sampled every other frame
        
        val finalResult = VideoRiskResult(
            maxPorn = maxPorn,
            maxSexy = maxSexy,
            maxHentai = maxHentai,
            maxDrawing = maxDrawing,
            consecutiveNsfwFrames = maxConsecutive,
            avgSkinRatio = if (auxFrameCount > 0) totalSkinRatio / auxFrameCount else 0f,
            avgEdgeDensity = if (auxFrameCount > 0) totalEdgeDensity / auxFrameCount else 0f,
            totalFrames = analyzedFrames
        )

        Log.i(TAG, "Video analysis: frames=$analyzedFrames, consecutive=$maxConsecutive, " +
                   "porn=$maxPorn, drawing=$maxDrawing")
        
        return finalResult
    }
}
