package com.childsafety.os.ai

import android.content.Context
import com.childsafety.os.policy.AgeGroup

/**
 * Text risk classifier.
 *
 * Pipeline:
 * String → rule-based scan → ML (TFLite) → policy decision
 */
object TextRiskClassifier {

    fun classify(
        context: Context,
        text: String,
        ageGroup: AgeGroup
    ): ClassificationResult {

        if (text.isBlank()) {
            return ClassificationResult(
                isRisky = false,
                confidence = 0.0f,
                matchedKeywords = emptyList()
            )

        }

        val normalized = text.lowercase()

        // ---------------- RULE-BASED SIGNAL ----------------
        val matchedHigh =
            KeywordRepository.highRisk.filter { normalized.contains(it) }

        val matchedMedium =
            KeywordRepository.mediumRisk.filter { normalized.contains(it) }

        val ruleConfidence = when {
            matchedHigh.isNotEmpty() -> 0.9f
            matchedMedium.isNotEmpty() -> 0.6f
            else -> 0.0f
        }

        // ---------------- ML-BASED SIGNAL ----------------
        // IMPORTANT: text model expects STRING input
        val mlConfidence = TfLiteManager.classifyText(
            context = context,
            text = normalized
        )
        android.util.Log.w(
            "TEXT_ML",
            "text='$normalized' rule=$ruleConfidence ml=$mlConfidence"
        )


        // ---------------- FINAL DECISION ----------------
        val finalConfidence = maxOf(ruleConfidence, mlConfidence)

        val isRisky = when (ageGroup) {
            AgeGroup.CHILD -> finalConfidence >= 0.6f
            AgeGroup.TEEN -> finalConfidence >= 0.75f
            AgeGroup.ADULT -> false
        }

        return ClassificationResult(
            isRisky = isRisky,
            confidence = finalConfidence,
            matchedKeywords = matchedHigh + matchedMedium
        )
    }

    fun isRisky(
        context: Context,
        text: String,
        ageGroup: AgeGroup
    ): Boolean {
        return classify(context, text, ageGroup).isRisky
    }
}
