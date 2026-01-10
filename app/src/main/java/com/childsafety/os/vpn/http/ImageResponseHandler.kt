package com.childsafety.os.vpn.http

import android.content.Context
import android.util.Log
import com.childsafety.os.browser.ImageMlQueue
import com.childsafety.os.policy.AgeGroup

/**
 * ImageResponseHandler
 *
 * HTTP layer MUST NOT run ML.
 * It delegates analysis to ImageMlQueue (async, fail-open).
 *
 * This handler NEVER blocks synchronously.
 */
object ImageResponseHandler {

    private const val TAG = "ImageResponseHandler"

    /**
     * @param imageBytes Raw image body
     * @return Always returns original bytes (blocking handled async)
     */
    fun handleImage(
        context: Context,
        imageBytes: ByteArray,
        imageId: String,
        imageUrl: String,
        ageGroup: AgeGroup
    ): ByteArray {

        // Delegate ML asynchronously
        ImageMlQueue.enqueue(
            context = context,
            imageId = imageId,
            url = imageUrl
        )

        Log.d(TAG, "Image delegated to ML queue (fail-open)")

        // NEVER block in HTTP pipeline
        return imageBytes
    }
}
