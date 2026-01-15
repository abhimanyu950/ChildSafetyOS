package com.childsafety.os.ai

import android.content.Context
import android.util.Log
import org.tensorflow.lite.task.text.nlclassifier.BertNLClassifier
import java.io.IOException

/**
 * MobileBERT Classifier for advanced contextual understanding.
 * Use TFLite Task Library for optimized inference.
 * 
 * Requires: 'mobilebert.tflite' in assets folder with metadata.
 * If model is missing, gracefully falls back to rule-based system.
 */
class BertTextClassifier(private val context: Context) {

    private var classifier: BertNLClassifier? = null
    private var isInitialized = false
    
    companion object {
        private const val TAG = "BertTextClassifier"
        private const val MODEL_FILE = "mobilebert.tflite"
    }

    init {
        initialize()
    }

    private fun initialize() {
        try {
            // Check if model exists in assets
            val assets = context.assets.list("")
            if (assets?.contains(MODEL_FILE) == true) {
                val options = BertNLClassifier.BertNLClassifierOptions.builder()
                    .build()
                
                classifier = BertNLClassifier.createFromFileAndOptions(
                    context, 
                    MODEL_FILE, 
                    options
                )
                isInitialized = true
                Log.i(TAG, "MobileBERT initialized successfully")
            } else {
                Log.w(TAG, "MobileBERT model ($MODEL_FILE) not found in assets. Running in fallback mode.")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error loading MobileBERT model", e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "MobileBERT model format error (missing metadata?)", e)
        }
    }

    /**
     * Classify text using BERT.
     * Returns a risk score between 0.0 (Safe) and 1.0 (Risky).
     */
    fun classifyRisk(text: String): Float {
        if (!isInitialized || classifier == null) {
            return 0.0f // Fallback
        }

        try {
            // Classify text
            // Returns list of Category (label, score)
            val results = classifier?.classify(text) ?: return 0.0f
            
            // Analyze results based on expected labels
            // Typical Toxicity Model Labels: "toxic", "severe_toxic", "obscene", "threat", "insult", "identity_hate"
            // Sentiment Model Labels: "negative", "positive"
            
            var maxToxicScore = 0.0f
            
            for (category in results) {
                val label = category.label.lowercase()
                val score = category.score
                
                // Check basically for "negative" or "toxic" classes
                if (label == "toxic" || label == "negative" || label == "risky" || 
                    label == "obscene" || label == "threat") {
                    if (score > maxToxicScore) {
                        maxToxicScore = score
                    }
                }
                
                // If label is "safe" or "positive", we might invert the score, but let's assume risk presence
            }
            
            // Log.v(TAG, "BERT Analysis: $text -> $maxToxicScore")
            return maxToxicScore

        } catch (e: Exception) {
            Log.e(TAG, "Inference error", e)
            return 0.0f
        }
    }
    
    fun close() {
        if (!isInitialized) return
        try {
            classifier?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing classifier", e)
        }
    }
}
