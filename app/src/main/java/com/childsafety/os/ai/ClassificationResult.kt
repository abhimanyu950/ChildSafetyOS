package com.childsafety.os.ai

data class ClassificationResult(
    val isRisky: Boolean,
    val confidence: Float,
    val matchedKeywords: List<String>
)
