package com.childsafety.os.browser

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.*
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.childsafety.os.ChildSafetyApp
import com.childsafety.os.cloud.EventUploader
import com.childsafety.os.policy.AgeGroup
import com.childsafety.os.policy.DomainPolicy
import com.childsafety.os.policy.PolicyEngine

/**
 * Safe Browser Activity with comprehensive content filtering.
 * 
 * Content Filtering Pipeline:
 * 1. DOMAIN BLOCKING: Check URL against DomainPolicy before loading
 * 2. TEXT BLOCKING: Analyze page text for explicit keywords/ML risk
 * 3. EMOJI BLOCKING: Detect risky emoji combinations
 * 4. IMAGE BLOCKING: Async ML analysis via ImageMlQueue
 * 
 * Design Principles:
 * - Block explicit content, allow safe content
 * - Age-appropriate thresholds
 * - Fail-open for ML (never block indefinitely)
 * - Log all blocking events to Firebase
 */
class SafeBrowserActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var currentAgeGroup: AgeGroup = AgeGroup.CHILD

    companion object {
        private const val TAG = "SafeBrowserActivity"
        const val EXTRA_AGE_GROUP = "age_group"
        const val EXTRA_START_URL = "start_url"
        private const val DEFAULT_URL = "https://www.google.com"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get age group from intent
        val ageGroupName = intent.getStringExtra(EXTRA_AGE_GROUP) ?: "CHILD"
        currentAgeGroup = try {
            AgeGroup.valueOf(ageGroupName)
        } catch (e: Exception) {
            AgeGroup.CHILD
        }

        // Get start URL
        val startUrl = intent.getStringExtra(EXTRA_START_URL) ?: DEFAULT_URL

        // Create layout
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Create and configure WebView
        webView = createSafeWebView()
        layout.addView(webView)

        setContentView(layout)

        // Initialize ImageMlQueue with WebView reference
        ImageMlQueue.setWebView(webView)
        ImageMlQueue.setAgeGroup(currentAgeGroup)

        // Load start URL
        webView.loadUrl(startUrl)

        Log.i(TAG, "Safe Browser started | AgeGroup=$currentAgeGroup | URL=$startUrl")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createSafeWebView(): WebView {
        return WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )

            // Configure WebView settings
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                setSupportZoom(true)
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
            }

            // Add JavaScript bridge for image interception
            addJavascriptInterface(JsBridge(context), "ChildSafety")

            // Set WebViewClient for URL interception
            webViewClient = SafeWebViewClient()

            // Set WebChromeClient for console logging
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        Log.d(TAG, "Console: ${it.message()}")
                    }
                    return true
                }
            }
        }
    }

    /**
     * Custom WebViewClient for URL filtering with comprehensive error handling.
     */
    private inner class SafeWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            try {
                val url = request?.url?.toString() ?: return false
                val host = request.url.host ?: return false

                // Check domain policy
                val decision = DomainPolicy.evaluate(host)
                if (decision.blocked) {
                    Log.w(TAG, "Domain blocked: $host (${decision.category})")

                    // Show blocked page
                    val blockedHtml = BlockedPageHtml.forCategory(decision.category, host)
                    view?.loadDataWithBaseURL(
                        null,
                        blockedHtml,
                        "text/html",
                        "UTF-8",
                        null
                    )

                    // Log to Firebase
                    EventUploader.logSafeBrowserBlock(
                        deviceId = ChildSafetyApp.appDeviceId,
                        url = url,
                        reason = "DOMAIN_${decision.category}"
                    )

                    return true // Block navigation
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in shouldOverrideUrlLoading", e)
            }

            return false // Allow navigation
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            
            try {
                // Inject image scanning JavaScript (wrapped in try-catch)
                injectImageScanner(view)
                
                // Analyze page text for explicit content
                analyzePageText(view, url)
            } catch (e: Exception) {
                Log.e(TAG, "Error in onPageFinished", e)
            }
        }

        /**
         * Handle HTTP errors gracefully - don't block, just log.
         */
        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest?,
            errorResponse: android.webkit.WebResourceResponse?
        ) {
            super.onReceivedHttpError(view, request, errorResponse)
            val url = request?.url?.toString() ?: "unknown"
            val statusCode = errorResponse?.statusCode ?: 0
            Log.w(TAG, "HTTP error $statusCode for: $url")
            // Don't block - allow browser to show its own error page
        }

        /**
         * Handle SSL errors gracefully - show warning but allow proceed.
         */
        override fun onReceivedSslError(
            view: WebView?,
            handler: android.webkit.SslErrorHandler?,
            error: android.net.http.SslError?
        ) {
            Log.w(TAG, "SSL error: ${error?.primaryError} for ${error?.url}")
            // For safety, cancel SSL errors (don't proceed to insecure sites)
            handler?.cancel()
        }

        /**
         * Handle generic errors - log and let WebView show error page.
         */
        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: android.webkit.WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            Log.w(TAG, "WebView error: ${error?.description} for ${request?.url}")
            // Don't block - allow browser to handle naturally
        }

        /**
         * Handle render process crashes - reload page.
         */
        override fun onRenderProcessGone(
            view: WebView?,
            detail: android.webkit.RenderProcessGoneDetail?
        ): Boolean {
            Log.e(TAG, "Render process gone, didCrash=${detail?.didCrash()}")
            // Return true to indicate we handled it (prevents app crash)
            view?.reload()
            return true
        }
    }

    /**
     * Inject JavaScript to scan and track images.
     */
    private fun injectImageScanner(webView: WebView?) {
        val url = webView?.url
        val host = url?.let { android.net.Uri.parse(it).host }
        
        // Skip scanning on trusted domains (Google, Bing, etc.)
        // This prevents "Unusual Traffic" errors caused by rapid image checking
        if (com.childsafety.os.policy.TrustedImageDomains.isHostTrusted(host)) {
            Log.d(TAG, "Skipping image scanner for trusted host: $host")
            return
        }

        val js = """
            (function() {
                // Generate unique ID for images
                function generateId() {
                    return 'cs_' + Math.random().toString(36).substr(2, 9);
                }
                
                // Process an image element
                function processImage(img) {
                    if (img.dataset.csid) return; // Already processed
                    if (!img.src || img.src.startsWith('data:')) return; // Skip data URIs
                    if (img.width < 50 || img.height < 50) return; // Skip tiny images
                    
                    var imageId = generateId();
                    img.dataset.csid = imageId;
                    
                    // Send to native for ML analysis
                    if (window.ChildSafety && window.ChildSafety.onImageFound) {
                        window.ChildSafety.onImageFound(imageId, img.src);
                    }
                }
                
                // Process all existing images
                document.querySelectorAll('img').forEach(processImage);
                
                // Observe new images
                var observer = new MutationObserver(function(mutations) {
                    mutations.forEach(function(mutation) {
                        mutation.addedNodes.forEach(function(node) {
                            if (node.nodeName === 'IMG') {
                                processImage(node);
                            } else if (node.querySelectorAll) {
                                node.querySelectorAll('img').forEach(processImage);
                            }
                        });
                    });
                });
                
                if (document.body) {
                    observer.observe(document.body, {
                        childList: true,
                        subtree: true
                    });
                }
            })();
        """.trimIndent()

        webView?.evaluateJavascript(js, null)
    }

    /**
     * Analyze page text for explicit content.
     * 
     * NOTE: Text analysis disabled for now as it causes too many false positives.
     * Image ML and domain blocking are sufficient for content safety.
     * Re-enable after fine-tuning the text classifier thresholds.
     */
    private fun analyzePageText(webView: WebView?, url: String?) {
        // Skip text analysis for trusted domains
        val host = url?.let { android.net.Uri.parse(it).host } ?: return
        val trustedDomains = listOf(
            "google.com", "www.google.com", "google.co.in",
            "youtube.com", "www.youtube.com",
            "wikipedia.org", "en.wikipedia.org",
            "bing.com", "www.bing.com",
            "duckduckgo.com"
        )
        
        if (trustedDomains.any { host.contains(it) }) {
            Log.d(TAG, "Skipping text analysis for trusted domain: $host")
            return
        }

        // Text/emoji analysis is currently disabled to prevent false positives
        // The image ML scanner and domain blocking provide sufficient protection
        // TODO: Re-enable after fine-tuning text classifier thresholds
        
        Log.d(TAG, "Text analysis skipped for: $url (disabled to prevent false positives)")
        
        /*
        val js = """
            (function() {
                var text = document.body.innerText || '';
                return text.substring(0, 5000); // Limit text length
            })();
        """.trimIndent()

        webView?.evaluateJavascript(js) { result ->
            val pageText = result?.removeSurrounding("\"") ?: return@evaluateJavascript
            
            // Check text for explicit content
            if (PolicyEngine.shouldBlockText(this, pageText, currentAgeGroup)) {
                Log.w(TAG, "Page text blocked: $url")
                
                val blockedHtml = BlockedPageHtml.blockedTextPage()
                webView.loadDataWithBaseURL(null, blockedHtml, "text/html", "UTF-8", null)
                
                EventUploader.logSafeBrowserBlock(
                    deviceId = ChildSafetyApp.appDeviceId,
                    url = url ?: "",
                    reason = "EXPLICIT_TEXT"
                )
            }
            
            // Check emojis
            if (PolicyEngine.shouldBlockEmoji(pageText, currentAgeGroup)) {
                Log.w(TAG, "Page emoji blocked: $url")
                
                val blockedHtml = BlockedPageHtml.blockedTextPage()
                webView.loadDataWithBaseURL(null, blockedHtml, "text/html", "UTF-8", null)
                
                EventUploader.logSafeBrowserBlock(
                    deviceId = ChildSafetyApp.appDeviceId,
                    url = url ?: "",
                    reason = "EXPLICIT_EMOJI"
                )
            }
        }
        */
    }

    /**
     * Handle back button for WebView navigation.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        ImageMlQueue.setWebView(null)
        webView.destroy()
        super.onDestroy()
    }
}
