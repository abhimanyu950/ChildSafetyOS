package com.childsafety.os.ai

/**
 * Result from text classification with optional context explanation
 */
data class ClassificationResult(
    val isRisky: Boolean,
    val confidence: Float,
    val matchedKeywords: List<String>,
    val contextOverride: String? = null  // Explanation when context changes the decision
)

