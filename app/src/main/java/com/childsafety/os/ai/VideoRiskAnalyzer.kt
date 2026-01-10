package com.childsafety.os.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log

object VideoRiskAnalyzer {

    private const val TAG = "VideoRiskAnalyzer"
    private const val EARLY_STOP_THRESHOLD = 0.65f

    fun analyze(
        context: Context,
        frames: List<Bitmap>
    ): Float {

        if (frames.isEmpty()) {
            Log.w(TAG, "No frames provided")
            return 0f
        }

        var maxScore = 0f

        for (frame in frames) {
            val result = ImageRiskClassifier.analyze(context, frame)

            val score = maxOf(
                result.porn,
                result.sexy,
                result.hentai
            )

            maxScore = maxOf(maxScore, score)

            if (maxScore >= EARLY_STOP_THRESHOLD) {
                Log.i(TAG, "Early stop at score=$maxScore")
                break
            }
        }

        Log.i(TAG, "Final video risk=$maxScore")
        return maxScore
    }
}
