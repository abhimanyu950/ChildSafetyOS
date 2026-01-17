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

        // Use the mode-aware script, defaulting to Child/Teen mode (safe)
        // NOTE: This interceptor is a fallback. SafeBrowserActivity handles mode-awareness directly.
        view.evaluateJavascript(
            JsScripts.getImageDetectorScript(false),
            null
        )
    }
}
