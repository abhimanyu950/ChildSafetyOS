package com.childsafety.os.browser

class JsContentBridge {

    interface Listener {
        fun onPageContext(text: String, imageCount: Int)
        fun onExplicitTextDetected()
    }

    private var listener: Listener? = null

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    @android.webkit.JavascriptInterface
    fun onPageContext(text: String, imageCount: Int) {
        listener?.onPageContext(text, imageCount)
    }

    @android.webkit.JavascriptInterface
    fun onExplicitTextDetected() {
        listener?.onExplicitTextDetected()
    }
}
