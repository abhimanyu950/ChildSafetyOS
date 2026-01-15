package com.childsafety.os.ai

import android.content.Context
import android.util.Log
import com.childsafety.os.policy.AgeGroup

/**
 * Text risk classifier with context-aware semantic analysis.
 *
 * Pipeline:
 * String → Context Analysis → Rule-based scan → ML (TFLite) → policy decision
 * 
 * Key improvement: Understands context to avoid false positives
 * - "I want to kill this game boss" → SAFE (gaming context)
 * - "How to kill someone" → BLOCKED (no safe context)
 */
object TextRiskClassifier {

    private const val TAG = "TextRiskClassifier"
    @Volatile
    private var bertClassifier: BertTextClassifier? = null
    private val lock = Any()

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

        // Initialize BERT Classifier (Thread-Safe Singleton)
        if (bertClassifier == null) {
            synchronized(lock) {
                if (bertClassifier == null) {
                    bertClassifier = BertTextClassifier(context.applicationContext)
                }
            }
        }

        val normalized = text.lowercase()

        // ---------------- CONTEXT ANALYSIS (NEW) ----------------
        val contextResult = ContextTextAnalyzer.analyze(text)
        
        // If context analysis found safe context, reduce risk
        if (!contextResult.isRisky && contextResult.detectedContext != null) {
            Log.i(TAG, "Context made text safe: ${contextResult.detectedContext} - ${contextResult.reason}")
            return ClassificationResult(
                isRisky = false,
                confidence = 0.1f,
                matchedKeywords = listOfNotNull(contextResult.triggerWord),
                contextOverride = contextResult.reason
            )
        }
        
        // If context analysis found explicitly dangerous, block immediately
        if (contextResult.isRisky && contextResult.confidence > 0.9f) {
            Log.w(TAG, "Context blocked: ${contextResult.reason}")
            return ClassificationResult(
                isRisky = true,
                confidence = contextResult.confidence,
                matchedKeywords = listOfNotNull(contextResult.triggerWord),
                contextOverride = contextResult.reason
            )
        }

        // ---------------- RULE-BASED SIGNAL ----------------
        val matchedHigh = KeywordRepository.highRisk.filter { normalized.contains(it) }
        val matchedMedium = KeywordRepository.mediumRisk.filter { normalized.contains(it) }

        // Rule Confidence
        var ruleConfidence = when {
            matchedHigh.isNotEmpty() -> 0.9f
            matchedMedium.isNotEmpty() -> 0.6f
            else -> 0.0f
        }

        // ---------------- BERT SIGNAL (NEW) ----------------
        var bertConfidence = 0.0f
        try {
            bertConfidence = bertClassifier?.classifyRisk(text) ?: 0.0f
        } catch (e: Exception) {
            Log.e(TAG, "BERT inference failed", e)
        }

        // ---------------- ML SIGNAL (TFLite LSTM - Legacy) ----------------
        // We keep this as backup or combine it
        val lstmConfidence = TfLiteManager.classifyText(
            context = context,
            text = normalized
        )
        
        Log.d(TAG, "Analysis: rule=$ruleConfidence bert=$bertConfidence lstm=$lstmConfidence context=${contextResult.detectedContext}")

        // ---------------- FINAL DECISION ----------------
        // Weighted Average: BERT (High priority) > Rule > LSTM
        // If BERT is confidently safe (e.g. 0.01) but Rule says Risky (0.9), check context magnitude.
        
        // Strategy: Max of signals, but if BERT is present and very confident, it can influence result.
        // For safety, we take MAX to be conservative (Fail Closed).
        // Exception: If ContextAnalyzer said SAFE explicitly (already handled above).
        
        val maxConfidence = maxOf(ruleConfidence, bertConfidence, lstmConfidence)

        // Thresholds based on Age Group
        val isRisky = when (ageGroup) {
            AgeGroup.CHILD -> maxConfidence >= 0.6f
            AgeGroup.TEEN -> maxConfidence >= 0.75f
            AgeGroup.ADULT -> maxConfidence >= 0.95f
        }

        return ClassificationResult(
            isRisky = isRisky,
            confidence = maxConfidence,
            matchedKeywords = matchedHigh + matchedMedium,
            contextOverride = if (bertConfidence > 0.8f) "AI Detected Risk" else null
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

