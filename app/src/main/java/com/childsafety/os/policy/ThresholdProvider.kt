package com.childsafety.os.policy

/**
 * Provides age-appropriate content filtering thresholds.
 * 
 * PHILOSOPHY: Explicit content can be harmful or triggering at ANY age.
 * We provide progressively relaxed thresholds, but even Adults have
 * protection from highly explicit pornographic content.
 * 
 * Lower thresholds = more strict filtering (block more content).
 */
object ThresholdProvider {

    /**
     * Get text classification threshold.
     * Even adults are protected from extremely explicit text content.
     */
    fun get(ageGroup: AgeGroup): Float =
        when (ageGroup) {
            AgeGroup.CHILD -> 0.50f   // Very strict - blocks suggestive content
            AgeGroup.TEEN -> 0.70f    // Moderate - allows some mature themes
            AgeGroup.ADULT -> 0.90f   // Relaxed - but still blocks extremely explicit text
        }

    /**
     * Get image classification thresholds.
     * Returns thresholds for porn, hentai, and sexy categories.
     * 
     * NOTE: Even ADULT mode blocks highly explicit pornographic content (0.85+)
     * because such content can be:
     * - Psychologically triggering
     * - Addictive
     * - Potentially illegal (extreme content)
     * 
     * This is a SAFETY app, not an unrestricted browser.
     */
    fun getThresholds(ageGroup: AgeGroup): ImageThresholds =
        when (ageGroup) {
            // CHILD (Under 13): Maximum protection
            // Blocks anything remotely suggestive
            AgeGroup.CHILD -> ImageThresholds(
                pornThreshold = 0.15f,    // Block at very low confidence
                hentaiThreshold = 0.15f,  // Block animated adult content
                sexyThreshold = 0.25f     // Block suggestive content
            )
            
            // TEEN (13-17): Strong protection
            // Allows some mature themes but blocks explicit content
            AgeGroup.TEEN -> ImageThresholds(
                pornThreshold = 0.35f,    // Block moderate+ explicit
                hentaiThreshold = 0.35f,  // Block animated explicit
                sexyThreshold = 0.50f     // Allow mild suggestive, block explicit
            )
            
            // ADULT (18+): Relaxed but not unrestricted
            // Blocks only highly explicit pornographic content
            // Rationale: Explicit content can still be harmful/triggering
            AgeGroup.ADULT -> ImageThresholds(
                pornThreshold = 0.85f,    // Block only obvious porn
                hentaiThreshold = 0.85f,  // Block explicit animated
                sexyThreshold = 0.95f     // Allow most suggestive, block explicit
            )
        }
    
    /**
     * Get description for the age group (for UI display).
     */
    fun getDescription(ageGroup: AgeGroup): String =
        when (ageGroup) {
            AgeGroup.CHILD -> "Maximum protection. Blocks all suggestive and explicit content."
            AgeGroup.TEEN -> "Strong protection. Allows mature themes, blocks explicit content."
            AgeGroup.ADULT -> "Relaxed filtering. Still blocks highly explicit/harmful content."
        }
}

/**
 * Image classification thresholds for NSFW detection.
 */
data class ImageThresholds(
    val pornThreshold: Float,
    val hentaiThreshold: Float,
    val sexyThreshold: Float
)
