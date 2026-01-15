package com.childsafety.os.policy

import android.util.Log

/**
 * Reliable Multi-Modal Content Safety Decision Engine
 * 
 * ROLE: Deterministic content-safety decision engine operating on
 * multiple imperfect ML models. Prioritizes consistency, explainability,
 * and false-positive resistance especially for drawings/cartoons/anime.
 * 
 * PRINCIPLE: You do not classify content. You decide based on evidence.
 * No single model may block content alone.
 */
object ContentDecisionEngine {

    private const val TAG = "ContentDecisionEngine"

    // =========================================================
    // DECISION ZONES
    // =========================================================
    private const val SAFE_THRESHOLD = 0.30f
    private const val UNCERTAIN_THRESHOLD = 0.75f
    // Above 0.75 = NSFW zone

    // =========================================================
    // MAIN DECISION FUNCTION
    // =========================================================
    
    /**
     * Make a content safety decision based on multiple signals.
     * 
     * @param signals All input signals (visual, textual, context)
     * @return DecisionResult with explainable decision
     */
    fun decide(signals: ContentSignals): DecisionResult {
        
        // 1. Check for false-positive compensation
        // CRITICAL: Do NOT downgrade in CHILD mode - safety is paramount
        val shouldDowngrade = signals.activeMode != AgeGroup.CHILD && signals.shouldDowngrade()
        val downgradeReason = if (shouldDowngrade) signals.getDowngradeReason() else null
        
        // 2. Apply mode-specific policy
        val rawDecision = when (signals.activeMode) {
            AgeGroup.CHILD -> applyChildPolicy(signals)
            AgeGroup.TEEN -> applyTeenPolicy(signals)
            AgeGroup.ADULT -> applyAdultPolicy(signals)
        }
        
        // 3. Apply downgrade if applicable (NOT for CHILD mode)
        val finalDecision = if (shouldDowngrade) {
            downgradeDecision(rawDecision)
        } else {
            rawDecision
        }
        
        val result = DecisionResult(
            decision = finalDecision.first,
            activeMode = signals.activeMode,
            dominantReason = finalDecision.second,
            downgradeReason = downgradeReason,
            signals = signals
        )
        
        // 4. Log BLOCK and UNCERTAIN decisions
        if (result.requiresLogging()) {
            logDecision(result)
        }
        
        return result
    }

    // =========================================================
    // CHILD MODE POLICY
    // STRICT: Block if ANY significant risk is detected
    // Children deserve maximum protection
    // =========================================================
    
    private fun applyChildPolicy(signals: ContentSignals): Pair<Decision, String> {
        
        // CHILD MODE THRESHOLDS - Very strict
        val PORN_THRESHOLD = 0.15f      // Block at very low porn confidence
        val HENTAI_THRESHOLD = 0.15f    // Block animated adult content
        val SEXY_THRESHOLD = 0.25f      // Block suggestive content (bikinis, etc.)
        
        // =====================================================
        // PRIORITY 1: BLOCK if ANY dangerous signal is high
        // =====================================================
        
        // Block if porn score is above threshold
        if (signals.imagePorn >= PORN_THRESHOLD) {
            return Decision.BLOCK to "Pornographic content detected (${String.format("%.0f", signals.imagePorn * 100)}% confidence)"
        }
        
        // Block if hentai score is above threshold (unless it's clearly a safe drawing)
        if (signals.imageHentai >= HENTAI_THRESHOLD && signals.imageDrawing < 0.70f) {
            return Decision.BLOCK to "Adult animated content detected (${String.format("%.0f", signals.imageHentai * 100)}% confidence)"
        }
        
        // Block if sexy/suggestive score is above threshold
        if (signals.imageSexy >= SEXY_THRESHOLD) {
            return Decision.BLOCK to "Suggestive content detected (${String.format("%.0f", signals.imageSexy * 100)}% confidence)"
        }
        
        // Block if combined image risk is high
        if (signals.imageCore >= 0.30f) {
            return Decision.BLOCK to "Combined risk score too high (${String.format("%.0f", signals.imageCore * 100)}%)"
        }
        
        // =====================================================
        // PRIORITY 2: UNCERTAIN for borderline cases
        // =====================================================
        
        // If any risk score is non-trivial, flag as uncertain
        if (signals.imagePorn >= 0.08f || signals.imageHentai >= 0.08f || signals.imageSexy >= 0.15f) {
            return Decision.UNCERTAIN to "Low-level risk detected - applying blur for safety"
        }
        
        // =====================================================
        // PRIORITY 3: ALLOW only if clearly safe
        // =====================================================
        
        // Safe drawings/cartoons with no sexual content
        if (signals.imageDrawing >= 0.60f && 
            signals.imagePorn < 0.05f && 
            signals.imageHentai < 0.05f &&
            signals.imageSexy < 0.10f) {
            return Decision.ALLOW to "Safe drawing/cartoon content"
        }
        
        // Neutral content
        if (signals.imageNeutral >= 0.70f) {
            return Decision.ALLOW to "Neutral content detected"
        }
        
        // Very low risk
        if (signals.imageCore < 0.10f && signals.imageSexy < 0.10f) {
            return Decision.ALLOW to "All scores in safe zone"
        }
        
        // Default: UNCERTAIN (better safe than sorry for children)
        return Decision.UNCERTAIN to "Cannot confirm safety - applying protective blur"
    }

    // =========================================================
    // TEEN MODE POLICY
    // =========================================================
    
    private fun applyTeenPolicy(signals: ContentSignals): Pair<Decision, String> {
        
        // ALLOW: Drawing with low porn score
        if (signals.imageDrawing >= 0.50f && signals.imagePorn < 0.25f) {
            return Decision.ALLOW to "Drawing/cartoon content"
        }
        
        // BLOCK: High core score with confirmation
        val blockCondition = signals.imageCore >= 0.75f &&
            (signals.videoConsistency >= 2 || signals.textCore >= 0.40f)
        
        if (blockCondition) {
            return Decision.BLOCK to "High risk with confirmation (imageCore=${String.format("%.2f", signals.imageCore)}, consistency=${signals.videoConsistency})"
        }
        
        // WARN: Sexy content without explicit porn
        if (signals.imageSexy >= 0.60f && signals.imagePorn < 0.40f) {
            return Decision.WARN to "Suggestive content detected (sexy=${String.format("%.2f", signals.imageSexy)})"
        }
        
        // UNCERTAIN: Moderate risk
        if (signals.imageCore >= 0.50f) {
            return Decision.UNCERTAIN to "Moderate risk - review recommended"
        }
        
        return Decision.ALLOW to "Content within acceptable range"
    }

    // =========================================================
    // ADULT MODE POLICY
    // Adult mode prioritizes autonomy
    // =========================================================
    
    private fun applyAdultPolicy(signals: ContentSignals): Pair<Decision, String> {
        
        // NEVER block for: Drawing, Hentai alone, Sexy content
        if (signals.imageDrawing >= 0.50f) {
            return Decision.ALLOW to "Drawing/animated content (adult autonomy)"
        }
        
        // Only hentai without porn = allow
        if (signals.imageHentai >= 0.50f && signals.imagePorn < 0.50f) {
            return Decision.ALLOW to "Animated content without explicit real material"
        }
        
        // Only sexy = allow
        if (signals.imageSexy >= 0.50f && signals.imagePorn < 0.60f) {
            return Decision.ALLOW to "Suggestive but not explicit content"
        }
        
        // BLOCK: Only very high porn with confirmed detection
        val blockCondition = signals.imagePorn >= 0.85f && signals.videoConsistency >= 2
        
        if (blockCondition) {
            return Decision.BLOCK to "Confirmed explicit content (porn=${String.format("%.2f", signals.imagePorn)}, consistency=${signals.videoConsistency})"
        }
        
        // WARN: High porn but not confirmed
        if (signals.imagePorn >= 0.70f) {
            return Decision.WARN to "Potentially explicit content"
        }
        
        return Decision.ALLOW to "Content acceptable for adult mode"
    }

    // =========================================================
    // UTILITIES
    // =========================================================
    
    /**
     * Downgrade decision severity by one level.
     */
    private fun downgradeDecision(decision: Pair<Decision, String>): Pair<Decision, String> {
        val downgraded = when (decision.first) {
            Decision.BLOCK -> Decision.UNCERTAIN
            Decision.UNCERTAIN -> Decision.WARN
            Decision.WARN -> Decision.ALLOW
            Decision.ALLOW -> Decision.ALLOW
        }
        return downgraded to decision.second
    }
    
    /**
     * Log decisions for tuning and debugging.
     */
    private fun logDecision(result: DecisionResult) {
        Log.w(TAG, result.toLogString())
    }

    // =========================================================
    // HELPER: Create signals from individual components
    // =========================================================
    
    /**
     * Create ContentSignals from image analysis results.
     */
    fun createSignalsFromImage(
        imageResult: com.childsafety.os.ai.ImageRiskResult,
        skinRatio: Float,
        edgeDensity: Float,
        textRisk: Float = 0f,
        emojiRisk: Float = 0f,
        keywordRisk: Float = 0f,
        hasSafeContext: Boolean = false,
        safeContextType: String? = null,
        activeMode: AgeGroup
    ): ContentSignals {
        return ContentSignals(
            imagePorn = imageResult.porn,
            imageHentai = imageResult.hentai,
            imageSexy = imageResult.sexy,
            imageDrawing = imageResult.drawing,
            imageNeutral = imageResult.neutral,
            skinRatio = skinRatio,
            edgeDensity = edgeDensity,
            videoConsistency = 1, // Single image = 1 frame
            textRisk = textRisk,
            emojiRisk = emojiRisk,
            keywordRisk = keywordRisk,
            hasSafeContext = hasSafeContext,
            safeContextType = safeContextType,
            activeMode = activeMode,
            source = ContentSource.IMAGE
        )
    }
    
    /**
     * Create ContentSignals from video analysis results.
     */
    fun createSignalsFromVideo(
        videoResult: com.childsafety.os.ai.VideoRiskResult,
        textRisk: Float = 0f,
        emojiRisk: Float = 0f,
        keywordRisk: Float = 0f,
        hasSafeContext: Boolean = false,
        safeContextType: String? = null,
        activeMode: AgeGroup
    ): ContentSignals {
        return ContentSignals(
            imagePorn = videoResult.maxPorn,
            imageHentai = videoResult.maxHentai,
            imageSexy = videoResult.maxSexy,
            imageDrawing = videoResult.maxDrawing,
            imageNeutral = 0f,
            skinRatio = videoResult.avgSkinRatio,
            edgeDensity = videoResult.avgEdgeDensity,
            videoConsistency = videoResult.consecutiveNsfwFrames,
            textRisk = textRisk,
            emojiRisk = emojiRisk,
            keywordRisk = keywordRisk,
            hasSafeContext = hasSafeContext,
            safeContextType = safeContextType,
            activeMode = activeMode,
            source = ContentSource.VIDEO
        )
    }
}
