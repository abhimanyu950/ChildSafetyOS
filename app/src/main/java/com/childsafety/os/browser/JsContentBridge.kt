package com.childsafety.os.browser

class JsContentBridge(
    private val onBlock: () -> Unit
) {

    @android.webkit.JavascriptInterface
    fun onExplicitTextDetected() {
        onBlock()
    }
}
