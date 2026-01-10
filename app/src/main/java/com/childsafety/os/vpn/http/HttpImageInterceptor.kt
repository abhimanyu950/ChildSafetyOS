package com.childsafety.os.vpn.http

import android.content.Context
import com.childsafety.os.browser.ImageMlQueue

object HttpImageInterceptor {

    /**
     * HTTP layer must never run ML.
     * It delegates image analysis to ImageMlQueue (async, fail-open).
     *
     * Always return false here to avoid blocking in transport layer.
     */
    fun shouldBlock(
        context: Context,
        imageId: String,
        imageUrl: String
    ): Boolean {

        // Delegate async ML processing
        ImageMlQueue.enqueue(
            context = context,
            imageId = imageId,
            url = imageUrl
        )

        // Never block synchronously at HTTP level
        return false
    }
}
