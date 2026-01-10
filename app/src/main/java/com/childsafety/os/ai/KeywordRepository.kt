package com.childsafety.os.ai

object KeywordRepository {

    // High-risk keywords (immediate concern)
    val highRisk = listOf(
        "porn",
        "sex",
        "rape",
        "nude",
        "kill",
        "suicide",
        "terror",
        "bomb"
    )

    // Medium-risk keywords (context dependent)
    val mediumRisk = listOf(
        "dating",
        "kiss",
        "romance",
        "violence",
        "fight",
        "blood"
    )

    /**
     * Converts text into a fixed-length numeric vector
     * suitable for TensorFlow Lite input.
     *
     * Vector format:
     * [ highRiskCount, mediumRiskCount, normalizedLength ]
     *
     * Shape: FloatArray(3)
     */
    fun vectorize(text: String): FloatArray {

        val normalized = text.lowercase()

        val highCount =
            highRisk.count { normalized.contains(it) }.toFloat()

        val mediumCount =
            mediumRisk.count { normalized.contains(it) }.toFloat()

        val lengthNorm =
            (normalized.length.coerceAtMost(500) / 500f)

        return floatArrayOf(
            highCount,
            mediumCount,
            lengthNorm
        )
    }
}
