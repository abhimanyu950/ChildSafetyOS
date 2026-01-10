package com.childsafety.os.browser

import android.os.SystemClock
import android.webkit.WebView
import android.webkit.WebViewClient

class WebViewInterceptor : WebViewClient() {

    private var lastInject = 0L

    override fun onPageFinished(view: WebView, url: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastInject < 500) return
        lastInject = now

        view.evaluateJavascript(
            JsScripts.IMAGE_DETECTOR,
            null
        )
    }
}
