package com.childsafety.os.policy

/**
 * Output of the Content Decision Engine.
 * 
 * Provides explainable decisions with full logging support.
 */
data class DecisionResult(
    /** Final decision: ALLOW, WARN, BLOCK, or UNCERTAIN */
    val decision: Decision,
    
    /** Active filtering mode when decision was made */
    val activeMode: AgeGroup,
    
    /** Primary reason for this decision (plain language) */
    val dominantReason: String,
    
    /** Reason for downgrade, if severity was reduced */
    val downgradeReason: String? = null,
    
    /** Original signals used to make this decision */
    val signals: ContentSignals
) {
    /**
     * Generate a log-friendly summary for BLOCK/UNCERTAIN decisions.
     */
    fun toLogString(): String {
        return buildString {
            appendLine("=== Content Decision Log ===")
            appendLine("Decision: $decision")
            appendLine("Mode: $activeMode")
            appendLine("Reason: $dominantReason")
            downgradeReason?.let { appendLine("Downgrade: $it") }
            appendLine("--- Signals ---")
            appendLine("imagePorn: ${String.format("%.3f", signals.imagePorn)}")
            appendLine("imageHentai: ${String.format("%.3f", signals.imageHentai)}")
            appendLine("imageSexy: ${String.format("%.3f", signals.imageSexy)}")
            appendLine("imageDrawing: ${String.format("%.3f", signals.imageDrawing)}")
            appendLine("skinRatio: ${String.format("%.3f", signals.skinRatio)}")
            appendLine("edgeDensity: ${String.format("%.3f", signals.edgeDensity)}")
            appendLine("videoConsistency: ${signals.videoConsistency}")
            appendLine("textCore: ${String.format("%.3f", signals.textCore)}")
            appendLine("hasSafeContext: ${signals.hasSafeContext}")
            appendLine("imageCore: ${String.format("%.3f", signals.imageCore)}")
            appendLine("============================")
        }
    }
    
    /**
     * Check if this decision requires logging.
     */
    fun requiresLogging(): Boolean =
        decision == Decision.BLOCK || decision == Decision.UNCERTAIN
}

/**
 * Possible decisions from the content safety engine.
 */
enum class Decision {
    /** Content is safe to display */
    ALLOW,
    
    /** Content may be risky - show warning but allow */
    WARN,
    
    /** Content should be blocked */
    BLOCK,
    
    /** Unable to make confident decision - apply safe defaults (blur, hide preview) */
    UNCERTAIN
}
