package com.childsafety.os.policy

/**
 * Unified container for all content signals used by the decision engine.
 * 
 * This follows the multi-modal architecture where no single signal
 * can trigger a block decision alone.
 */
data class ContentSignals(
    // =============================================
    // VISUAL SIGNALS (Image/Video)
    // =============================================
    
    /** Pornographic content score (0.0 - 1.0) */
    val imagePorn: Float = 0f,
    
    /** Hentai/animated adult content score (0.0 - 1.0) */
    val imageHentai: Float = 0f,
    
    /** Sexy/suggestive content score (0.0 - 1.0) */
    val imageSexy: Float = 0f,
    
    /** Drawing/cartoon detection score (0.0 - 1.0) */
    val imageDrawing: Float = 0f,
    
    /** Neutral/safe content score (0.0 - 1.0) */
    val imageNeutral: Float = 0f,
    
    /** Ratio of skin-colored pixels (0.0 - 1.0) */
    val skinRatio: Float = 0f,
    
    /** Edge density - high = cartoon/drawing (0.0 - 1.0) */
    val edgeDensity: Float = 0f,
    
    /** Consecutive frames flagged as NSFW (for video) */
    val videoConsistency: Int = 0,
    
    // =============================================
    // TEXTUAL SIGNALS (Page/Caption/Metadata)
    // =============================================
    
    /** Text risk score from ML classifier (0.0 - 1.0) */
    val textRisk: Float = 0f,
    
    /** Emoji-based risk score (0.0 - 1.0) */
    val emojiRisk: Float = 0f,
    
    /** Keyword-based risk score (0.0 - 1.0) */
    val keywordRisk: Float = 0f,
    
    /** Whether safe context was detected (gaming, cooking, etc.) */
    val hasSafeContext: Boolean = false,
    
    /** Detected safe context category, if any */
    val safeContextType: String? = null,
    
    // =============================================
    // CONTEXT
    // =============================================
    
    /** Active filtering mode */
    val activeMode: AgeGroup = AgeGroup.CHILD,
    
    /** Content source type */
    val source: ContentSource = ContentSource.IMAGE
) {
    // =============================================
    // COMPUTED METRICS
    // =============================================
    
    /** Combined image risk: porn + hentai */
    val imageCore: Float
        get() = (imagePorn + imageHentai).coerceIn(0f, 1f)
    
    /** Maximum text-based risk */
    val textCore: Float
        get() = maxOf(textRisk, emojiRisk, keywordRisk)
    
    // =============================================
    // FALSE-POSITIVE COMPENSATION CONDITIONS
    // =============================================
    
    /**
     * Check if any false-positive compensation condition is met.
     * If true, severity should be downgraded by one level.
     */
    fun shouldDowngrade(): Boolean {
        return drawingDominates() ||
               lowSkinRatio() ||
               highEdgeDensity() ||
               unstableDetection() ||
               safeTextContext()
    }
    
    /** Drawing dominates hentai: likely anime/cartoon, not actual NSFW */
    fun drawingDominates(): Boolean =
        imageDrawing >= 0.60f && imagePorn < 0.25f
    
    /** Low skin ratio: unlikely to be nude content */
    fun lowSkinRatio(): Boolean =
        skinRatio < 0.15f
    
    /** High edge density: indicates cartoon/drawing style */
    fun highEdgeDensity(): Boolean =
        edgeDensity > 0.60f
    
    /** Unstable video detection: might be false positive */
    fun unstableDetection(): Boolean =
        source == ContentSource.VIDEO && videoConsistency < 2
    
    /** Safe text context: contextual indicator the content is safe */
    fun safeTextContext(): Boolean =
        textCore < 0.20f || hasSafeContext
    
    /**
     * Get the reason for downgrade, if any.
     */
    fun getDowngradeReason(): String? {
        return when {
            drawingDominates() -> "Drawing score (${String.format("%.2f", imageDrawing)}) dominates porn score (${String.format("%.2f", imagePorn)})"
            lowSkinRatio() -> "Low skin ratio (${String.format("%.2f", skinRatio)})"
            highEdgeDensity() -> "High edge density (${String.format("%.2f", edgeDensity)}) indicates cartoon/drawing"
            unstableDetection() -> "Unstable detection (only $videoConsistency consecutive frames)"
            safeTextContext() -> safeContextType?.let { "Safe context detected: $it" } ?: "Low text risk (${String.format("%.2f", textCore)})"
            else -> null
        }
    }
}

/**
 * Source type for content being analyzed.
 */
enum class ContentSource {
    IMAGE,
    VIDEO,
    WEBVIEW
}
