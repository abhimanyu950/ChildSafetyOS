package com.childsafety.os.policy

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import com.childsafety.os.ai.ImageRiskClassifier
import com.childsafety.os.ai.TfLiteManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.math.abs

enum class TrustLevel { HIGH, NEUTRAL, SUSPICIOUS }

data class RiskSignals(
    val networkScore: Float,   // 0-100 (VPN/DNS)
    val jsScore: Float,        // 0-100 (Page Text)
    val trust: TrustLevel      // Domain Reputation
)

/**
 * Antigravity Risk Engine (Kernel)
 * 
 * Central decision-maker fusing Network, Visual AI, and Text signals.
 * Implements "Flicker-Free" risk scoring with battery optimizations.
 */
class AntigravityRiskEngine(private val context: Context) {

    companion object {
        private const val TAG = "AntigravityEngine"
        private const val MAX_CACHE_SIZE = 100
    }

    // State
    // LruCache for pHash -> RiskScore (0-100)
    private val riskCache = LruCache<Long, Float>(MAX_CACHE_SIZE)
    
    // EMA State
    private var previousRiskScore = 0f

    // Dependencies
    // Note: In a real DI setup, these would be injected. 
    // We use the existing singletons/objects for now.
    private val imageClassifier = ImageRiskClassifier

    /**
     * Main Pipeline:
     * 1. Check Cache (pHash)
     * 2. Run OCR (Short-circuit if text is bad)
     * 3. Run AI (If needed)
     * 4. Apply Dynamic Weights -> Synergy -> EMA
     */
    suspend fun computeRisk(bitmap: Bitmap?, signals: RiskSignals, isVideo: Boolean, ageGroup: AgeGroup): Float = withContext(Dispatchers.Default) {
        // 0. Helper: If no image, just use context signals
        if (bitmap == null) {
            return@withContext calculateHybridScore(0f, signals, ageGroup)
        }

        // 1. pHash Caching (Memory L1)
        val pHash = computePHash(bitmap)
        val cachedScore = riskCache.get(pHash)
        if (cachedScore != null) {
            // Log.v(TAG, "Cache Hit: $pHash -> $cachedScore")
            // Recalculate hybrid with cached AI score in case signals changed
            val combined = calculateHybridScore(cachedScore, signals, ageGroup)
            return@withContext applyTemporalSmoothing(combined, isVideo)
        }

        // 2. OCR "Kill Switch" (Optimization)
        // Mocking OCR interaction for now as per instructions ("Inject or mock")
        // In production: val ocrResult = ocrScanner.process(bitmap)
        val hasExplicitKeywordsInImage = mockOcrCheck(bitmap) 
        if (hasExplicitKeywordsInImage) {
            Log.w(TAG, "OCR Kill Switch Triggered")
            val maxRisk = 100f
            riskCache.put(pHash, maxRisk)
            return@withContext applyTemporalSmoothing(maxRisk, isVideo)
        }

        // 3. Run AI (TFLite)
        // Only if not found in cache and OCR didn't kill it
        val aiResult = imageClassifier.analyze(context, bitmap)
        // Use max of relevant scores converted to 0-100
        val aiScoreRaw = maxOf(aiResult.porn, aiResult.hentai, aiResult.sexy) * 100f
        
        // Cache the raw AI score
        riskCache.put(pHash, aiScoreRaw)

        // 4. Apply Dynamic Weights -> Synergy
        val rawHybridRisk = calculateHybridScore(aiScoreRaw, signals, ageGroup)

        // 5. EMA Smoothing
        return@withContext applyTemporalSmoothing(rawHybridRisk, isVideo)
    }
    
    /**
     * Step A & B: Dynamic Weighting & Synergy Boosting
     */
    private fun calculateHybridScore(aiScore: Float, signals: RiskSignals, ageGroup: AgeGroup): Float {
        // Step A: Dynamic Weighting (Context Awareness)
        val (wAi, wNet, wJs) = when (signals.trust) {
            TrustLevel.HIGH -> {
                // Adaptive Trust Breakthrough:
                // If AI is extremely confident (> 85%), we must trust the pixels over the domain meta-trust.
                
                // CHILD GUARD: For Children, we trust the domain LESS for visual content.
                // Math: Weight 0.65 * AI 47% = 30.55 (Blocked).
                // This ensures we catch "soft" romantic/sexy content (45-60% confidence)
                // which is typical for "Hot Romantic Scenes" on YouTube.
                if (ageGroup == AgeGroup.CHILD) {
                    Triple(0.65f, 0.30f, 0.20f) // Strict Guard: Blocks >46% confident Sexy
                } else if (aiScore >= 85f) {
                    Triple(0.50f, 0.30f, 0.20f) // Breakthrough Weights for others
                } else {
                    Triple(0.30f, 0.40f, 0.30f) // Standard High Trust (Strong dampening)
                }
            }
            TrustLevel.NEUTRAL -> Triple(0.60f, 0.25f, 0.15f)    // Standard
            TrustLevel.SUSPICIOUS -> Triple(0.80f, 0.10f, 0.10f) // Paranoid
        }

        val weightedScore = (aiScore * wAi) + 
                            (signals.networkScore * wNet) + 
                            (signals.jsScore * wJs)

        // Step B: Synergy Boosting
        // If AI > 40 AND JS > 40 -> 1.25x Multiplier
        var rawRisk = weightedScore
        if (aiScore > 40f && signals.jsScore > 40f) {
            rawRisk *= 1.25f
        }

        return rawRisk.coerceIn(0f, 100f)
    }

    /**
     * Step C: Temporal Smoothing (EMA)
     */
    private fun applyTemporalSmoothing(currentRisk: Float, isVideo: Boolean): Float {
        val alpha = if (isVideo) 0.1f else 0.3f
        
        // First run init
        if (previousRiskScore == 0f) {
            previousRiskScore = currentRisk
            return currentRisk
        }

        val smoothedRisk = (alpha * currentRisk) + ((1 - alpha) * previousRiskScore)
        previousRiskScore = smoothedRisk
        
        return smoothedRisk
    }

    /**
     * Helper: Compute fast Perceptual Hash (64-bit)
     * Simplified implementation for performance (scaling to 8x8)
     */
    private fun computePHash(bitmap: Bitmap): Long {
        // 1. Scale to 8x8
        val scaled = Bitmap.createScaledBitmap(bitmap, 8, 8, false)
        // 2. Convert to grayscale & compute avg
        var total = 0L
        val pixels = IntArray(64)
        scaled.getPixels(pixels, 0, 8, 0, 0, 8, 8)
        
        // Simplified grayscale: R=0.3, G=0.59, B=0.11 or just sum/3
        val grays = pixels.map { color ->
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            (r + g + b) / 3
        }
        
        val avg = grays.sum() / 64
        
        // 3. Compute hash
        var hash = 0L
        grays.forEach { gray ->
            hash = hash shl 1
            if (gray >= avg) {
                hash = hash or 1L
            }
        }
        
        scaled.recycle()
        return hash
    }

    /**
     * Mock OCR Check (Optimization Layer 2)
     * In a real implementation this would use MLKit TextRecognizer
     */
    private fun mockOcrCheck(bitmap: Bitmap): Boolean {
        // Placeholder: "Inject or mock"
        // Return false to proceed to TFLite
        return false
    }
    
    /**
     * Reset state (useful for page navigation)
     */
    fun reset() {
        previousRiskScore = 0f
        // Optional: riskCache.evictAll() if memory is tight, but usually we keep it
    }
}
