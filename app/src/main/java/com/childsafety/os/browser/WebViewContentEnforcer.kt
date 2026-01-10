package com.childsafety.os.browser

import android.webkit.WebView

object WebViewContentEnforcer {

    fun inject(webView: WebView) {
        webView.evaluateJavascript(
            JsScripts.TEXT_EMOJI_SCANNER,
            null
        )
    }
}
