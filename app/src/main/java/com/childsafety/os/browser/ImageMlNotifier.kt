package com.childsafety.os.browser

import android.webkit.WebView

object ImageMlNotifier {

    fun notify(
        webView: WebView,
        imageUrl: String,
        blocked: Boolean
    ) {
        val js = """
            (function() {
                document.querySelectorAll("img").forEach(img => {
                    if (img.src === "$imageUrl") {
                        img.dataset.csBlocked = "${blocked}";
                    }
                });
            })();
        """.trimIndent()

        webView.post {
            webView.evaluateJavascript(js, null)
        }
    }
}
