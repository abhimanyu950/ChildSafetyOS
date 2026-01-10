package com.childsafety.os.browser

import android.content.Context
import android.webkit.JavascriptInterface

class JsBridge(private val context: Context) {

    @JavascriptInterface
    fun onImageFound(imageId: String, url: String) {
        ImageMlQueue.enqueue(context, imageId, url)
    }
}
