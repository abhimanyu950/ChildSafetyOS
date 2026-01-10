package com.childsafety.os.policy

/**
 * Provides age-appropriate content filtering thresholds.
 * Lower thresholds = more strict filtering (block more content).
 */
object ThresholdProvider {

    /**
     * Get text classification threshold.
     */
    fun get(ageGroup: AgeGroup): Float =
        when (ageGroup) {
            AgeGroup.CHILD -> 0.65f
            AgeGroup.TEEN -> 0.80f
            AgeGroup.ADULT -> 1.0f
        }

    /**
     * Get image classification thresholds.
     * Returns thresholds for porn, hentai, and sexy categories.
     */
    fun getThresholds(ageGroup: AgeGroup): ImageThresholds =
        when (ageGroup) {
            AgeGroup.CHILD -> ImageThresholds(
                pornThreshold = 0.20f,    // Very strict
                hentaiThreshold = 0.20f,
                sexyThreshold = 0.35f
            )
            AgeGroup.TEEN -> ImageThresholds(
                pornThreshold = 0.40f,    // Moderate
                hentaiThreshold = 0.40f,
                sexyThreshold = 0.60f
            )
            AgeGroup.ADULT -> ImageThresholds(
                pornThreshold = 1.0f,     // Never block
                hentaiThreshold = 1.0f,
                sexyThreshold = 1.0f
            )
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
