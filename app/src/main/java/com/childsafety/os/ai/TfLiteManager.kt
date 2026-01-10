package com.childsafety.os.ai

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

object TfLiteManager {

    private val interpreters = mutableMapOf<String, Interpreter>()

    // ------------------------------------------------------------
    // Interpreter loader (cached, safe)
    // ------------------------------------------------------------
    private fun getInterpreter(
        context: Context,
        modelName: String
    ): Interpreter {
        return interpreters.getOrPut(modelName) {
            Log.i("TfLiteManager", "Loading model: $modelName")
            val modelBuffer = loadModelFile(context, modelName)
            Interpreter(modelBuffer)
        }
    }

    // ------------------------------------------------------------
    // TEXT RISK MODEL
    // ------------------------------------------------------------
    /**
     * Binary text risk classifier
     * Output: confidence 0.0 – 1.0
     * 
     * Uses vectorized input from KeywordRepository for TFLite compatibility.
     * Falls back to rule-based scoring if model inference fails.
     */
    fun classifyText(
        context: Context,
        text: String
    ): Float {
        return try {
            val interpreter = getInterpreter(context, "text_risk.tflite")

            // Vectorize text using KeywordRepository (3-dimensional vector)
            val vector = KeywordRepository.vectorize(text)
            
            // Create ByteBuffer for TFLite input
            val inputBuffer = ByteBuffer.allocateDirect(vector.size * 4)
            inputBuffer.order(ByteOrder.nativeOrder())
            vector.forEach { inputBuffer.putFloat(it) }
            inputBuffer.rewind()

            val output = Array(1) { FloatArray(1) }
            interpreter.run(inputBuffer, output)

            output[0][0].coerceIn(0f, 1f)
        } catch (e: Exception) {
            Log.e("TfLiteManager", "Text model failed, using rule-based fallback", e)
            // Fallback: use vectorized features directly for scoring
            val vector = KeywordRepository.vectorize(text)
            val highRiskCount = vector[0]
            val mediumRiskCount = vector[1]
            // Simple rule-based scoring as fallback
            when {
                highRiskCount >= 1 -> 0.9f
                mediumRiskCount >= 1 -> 0.6f
                else -> 0.1f
            }
        }
    }

    // ------------------------------------------------------------
    // NSFW IMAGE MODEL
    // ------------------------------------------------------------
    data class NsfwResult(
        val drawing: Float,
        val hentai: Float,
        val neutral: Float,
        val porn: Float,
        val sexy: Float
    ) {
        fun isBlockedForChild(): Boolean {
            return porn >= 0.20f ||
                    hentai >= 0.20f ||
                    sexy >= 0.35f
        }
    }

    fun classifyNsfwImage(
        context: Context,
        inputBuffer: ByteBuffer
    ): NsfwResult {

        val interpreter = getInterpreter(context, "nsfw_mobilenet.tflite")
        val output = Array(1) { FloatArray(5) }

        interpreter.run(inputBuffer, output)
        val s = output[0]

        return NsfwResult(
            drawing = s[0],
            hentai = s[1],
            neutral = s[2],
            porn = s[3],
            sexy = s[4]
        )
    }

    // ------------------------------------------------------------
    // Asset loader (compression-safe)
    // ------------------------------------------------------------
    private fun loadModelFile(
        context: Context,
        modelName: String
    ): ByteBuffer {

        val inputStream = context.assets.open(modelName)
        val bytes = inputStream.readBytes()
        inputStream.close()

        val buffer = ByteBuffer.allocateDirect(bytes.size)
        buffer.order(ByteOrder.nativeOrder())
        buffer.put(bytes)
        buffer.rewind()

        Log.i(
            "TfLiteManager",
            "Model loaded: $modelName (${bytes.size / 1024} KB)"
        )

        return buffer
    }
}
