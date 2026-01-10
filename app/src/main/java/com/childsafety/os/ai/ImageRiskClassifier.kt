package com.childsafety.os.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log

data class ImageRiskResult(
    val porn: Float,
    val sexy: Float,
    val hentai: Float
)

object ImageRiskClassifier {

    private const val TAG = "ImageRiskClassifier"

    fun analyze(
        context: Context,
        bitmap: Bitmap
    ): ImageRiskResult {
        return try {
            // Preprocess bitmap to ByteBuffer for TFLite
            val inputBuffer = ImagePreprocessor.preprocess(bitmap)
            
            // Run actual NSFW model inference
            val nsfwResult = TfLiteManager.classifyNsfwImage(context, inputBuffer)
            
            val result = ImageRiskResult(
                porn = nsfwResult.porn,
                sexy = nsfwResult.sexy,
                hentai = nsfwResult.hentai
            )

            Log.i(TAG, "ML scores - porn=${result.porn} sexy=${result.sexy} hentai=${result.hentai}")
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "NSFW model failed, using safe fallback", e)
            // Fail-safe: return low scores (allow content through)
            ImageRiskResult(
                porn = 0.0f,
                sexy = 0.0f,
                hentai = 0.0f
            )
        }
    }
}
