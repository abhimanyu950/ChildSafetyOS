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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        
        // Hide the grey action bar for fullscreen browser experience
        supportActionBar?.hide()
        
        // Enable edge-to-edge display with proper insets handling
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        // Get age group from intent
        val ageGroupName = intent.getStringExtra(EXTRA_AGE_GROUP) ?: "CHILD"
        currentAgeGroup = try {
            AgeGroup.valueOf(ageGroupName)
        } catch (e: Exception) {
            AgeGroup.CHILD
        }

        // Get start URL
        val startUrl = intent.getStringExtra(EXTRA_START_URL) ?: DEFAULT_URL

        // Create layout with proper system bar padding
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Handle system bar insets
            fitsSystemWindows = true
            setBackgroundColor(android.graphics.Color.parseColor("#1A1B2E"))
        }

        // Navigation Bar REMOVED as per requirements
        // The browser is now single-purpose / kiosk-like for safety


        // Create and configure WebView
        webView = createSafeWebView()
        layout.addView(webView)

        setContentView(layout)

        // Initialize ImageMlQueue with WebView reference and listener
        ImageMlQueue.setWebView(webView, object : ImageMlQueue.BlockListener {
            override fun onImageBlocked(count: Int) {
                // AGGRESSIVE PAGE BLOCKING: If multiple explicit images detected, block entire page
                // Common scenario: Google Images search for explicit content
                // Threshold: 2+ blocked images = block page immediately
                if (count >= 2) {
                    runOnUiThread {
                        Log.w(TAG, "⚠️ EXPLICIT CONTENT PAGE DETECTED: $count blocked images. Blocking page.")
                        val currentUrl = webView.url ?: ""
                        
                        // Log page-level block to Firebase
                        com.childsafety.os.cloud.FirebaseManager.logPageBlock(
                            url = currentUrl,
                            domain = getDomain(currentUrl),
                            reason = "Multiple explicit images detected ($count images)",
                            blockedImageCount = count
                        )
                        
                        blockNavigation(webView, currentUrl, "⛔ Explicit Content Blocked", DomainPolicy.BlockCategory.EXPLICIT_IMAGE)
                    }
                }
            }
        })
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
     * Helper to show blocked page and log event.
     */
    private fun blockNavigation(
        view: WebView?, 
        url: String, 
        host: String, 
        category: DomainPolicy.BlockCategory?
    ) {
        if (isDestroyed || isFinishing) return
        
        Log.w(TAG, "Blocking content: $host ($category)")

        // Show blocked page
        val blockedHtml = BlockedPageHtml.forCategory(category, host)
        view?.loadDataWithBaseURL(
            null,
            blockedHtml,
            "text/html",
            "UTF-8",
            null
        )

        // Log to Firebase with detailed analytics
        val reason = category?.name ?: "Unknown"
        com.childsafety.os.cloud.FirebaseManager.logUrlBlock(
            url = url,
            domain = getDomain(url),
            reason = "Blocked: $reason",
            blockType = com.childsafety.os.cloud.models.BlockType.KEYWORD,
            browserType = com.childsafety.os.cloud.models.BrowserType.SAFE_BROWSER
        )
    }
    
    private fun getDomain(url: String): String {
        return try {
            java.net.URL(url).host
        } catch (e: Exception) {
            url
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
                val urlLower = url.lowercase()

                // 1. Check domain policy
                val domainDecision = DomainPolicy.evaluate(host)
                if (domainDecision.blocked) {
                    blockNavigation(view, url, host, domainDecision.category)
                    return true
                }

                // 2. Check search queries for specific explicit keywords
                // This handles cases where Image ML is disabled (Google/Bing) but we need to block bad searches
                // "pornhub", "sex", "xxx", etc. in the URL query string
                val isSearchEngine = host.contains("google.") || 
                                     host.contains("bing.") || 
                                     host.contains("yahoo.") || 
                                     host.contains("duckduckgo.") ||
                                     host.contains("search.")

                if (isSearchEngine) {
                    // Extract search query from URL
                    // Common params: q= (Google/Bing), p= (Yahoo), query=, text=
                    val queryParams = listOf("q", "p", "query", "text", "search_query")
                    var searchQuery = ""
                    
                    // Parse URL params manually or via Uri
                    try {
                        val uri = android.net.Uri.parse(url)
                        for (param in queryParams) {
                            val value = uri.getQueryParameter(param)
                            if (!value.isNullOrBlank()) {
                                searchQuery = value
                                break
                            }
                        }
                    } catch (e: Exception) {
                        // Fallback regex if URI parsing fails
                    }

                    if (searchQuery.isNotBlank()) {
                        // Use advanced Context-Aware Text Classifier ASYNC
                        val decodingQuery = java.net.URLDecoder.decode(searchQuery, "UTF-8")
                        
                        // Launch in background to prevent UI freeze
                        lifecycleScope.launch(Dispatchers.Default) {
                            val classification = com.childsafety.os.ai.TextRiskClassifier.classify(
                                this@SafeBrowserActivity, 
                                decodingQuery, 
                                currentAgeGroup
                            )

                            withContext(Dispatchers.Main) {
                                if (classification.isRisky) {
                                    Log.w(TAG, "Search query blocked: '$decodingQuery' (Confidence: ${classification.confidence})")
                                    
                                    // Log to Firebase with search analytics
                                    com.childsafety.os.cloud.FirebaseManager.logSearchBlock(
                                        searchQuery = decodingQuery,
                                        url = url,
                                        domain = host,
                                        reason = classification.contextOverride ?: "Explicit content detected"
                                    )
                                    
                                    blockNavigation(view, url, host, DomainPolicy.BlockCategory.EXPLICIT_TEXT)
                                } else {
                                    Log.i(TAG, "Search query allowed: '$decodingQuery' (Context: ${classification.contextOverride ?: "None"})")
                                }
                            }
                        }
                    }
                }

                // 3. Enforce SafeSearch parameters (Strict Mode)
                // This forces the search engine to filter explicit content at the source
                val safeUrl = enforceSafeSearch(url, host)
                if (safeUrl != url) {
                    Log.i(TAG, "Enforcing SafeSearch: $safeUrl")
                    view?.loadUrl(safeUrl)
                    return true
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error in shouldOverrideUrlLoading", e)
            }

            return false // Allow navigation
        }

        /**
         * Appends SafeSearch parameters to search engine URLs if missing.
         */
        private fun enforceSafeSearch(url: String, host: String): String {
            var newUrl = url
            
            if (host.contains("google.")) {
                if (!url.contains("safe=active")) {
                    newUrl += if (url.contains("?")) "&safe=active" else "?safe=active"
                }
            } else if (host.contains("bing.")) {
                if (!url.contains("adlt=strict")) {
                    newUrl += if (url.contains("?")) "&adlt=strict" else "?adlt=strict"
                }
            } else if (host.contains("duckduckgo.")) {
                if (!url.contains("kp=1")) {
                    newUrl += if (url.contains("?")) "&kp=1" else "?kp=1"
                }
            } else if (host.contains("yahoo.")) {
                if (!url.contains("vm=r")) {
                    newUrl += if (url.contains("?")) "&vm=r" else "?vm=r"
                }
            }
            
            return newUrl
        }



        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            super.onPageStarted(view, url, favicon)
            VideoFrameAnalyzer.reset()
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
    }

    /**
     * Inject JavaScript to scan and track images.
     */
    private fun injectImageScanner(webView: WebView?) {
        // We now enable scanning EVERYWHERE, including search engines.
        // Rate limiting is handled in ImageMlQueue to prevent bot detection.
        
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

        // Text/emoji analysis enabled safely on background thread
        
        val js = """
            (function() {
                var text = document.body.innerText || '';
                return text.substring(0, 5000); // Limit text length
            })();
        """.trimIndent()

        webView?.evaluateJavascript(js) { result ->
            val pageText = result?.removeSurrounding("\"") ?: return@evaluateJavascript
            
            lifecycleScope.launch(Dispatchers.Default) {
                // Check text for explicit content
                val isTextRisky = com.childsafety.os.ai.TextRiskClassifier.isRisky(
                    this@SafeBrowserActivity, 
                    pageText, 
                    currentAgeGroup
                )
                
                if (isTextRisky) {
                   withContext(Dispatchers.Main) {
                       Log.w(TAG, "Page text blocked: $url")
                       val blockedHtml = BlockedPageHtml.blockedTextPage()
                       webView.loadDataWithBaseURL(null, blockedHtml, "text/html", "UTF-8", null)
                       
                       com.childsafety.os.cloud.FirebaseManager.logUrlBlock(
                           url = url ?: "",
                           domain = getDomain(url ?: ""),
                           reason = "EXPLICIT_TEXT",
                           blockType = com.childsafety.os.cloud.models.BlockType.KEYWORD,
                           browserType = com.childsafety.os.cloud.models.BrowserType.SAFE_BROWSER
                       )
                   }
                   return@launch
                }
                
                // Check emojis
                // Using simple heuristic - if text wasn't risky, check emoji specific
                val isEmojiRisky = com.childsafety.os.ai.EmojiDetector.containsRiskyEmoji(pageText)
                if (isEmojiRisky) {
                    withContext(Dispatchers.Main) {
                        Log.w(TAG, "Page emoji blocked: $url")
                        
                        val blockedHtml = BlockedPageHtml.blockedTextPage()
                        webView.loadDataWithBaseURL(null, blockedHtml, "text/html", "UTF-8", null)
                        
                        com.childsafety.os.cloud.FirebaseManager.logUrlBlock(
                           url = url ?: "",
                           domain = getDomain(url ?: ""),
                           reason = "EXPLICIT_EMOJI",
                           blockType = com.childsafety.os.cloud.models.BlockType.KEYWORD,
                           browserType = com.childsafety.os.cloud.models.BrowserType.SAFE_BROWSER
                       )
                    }
                }
            }
        }
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

    override fun onResume() {
        super.onResume()
        // Resume video analysis if page loaded
        if (::webView.isInitialized && webView.url != null) {
            VideoFrameAnalyzer.startAnalysis(this, webView, currentAgeGroup)
        }
    }

    override fun onPause() {
        super.onPause()
        VideoFrameAnalyzer.stopAnalysis()
    }

    override fun onDestroy() {
        ImageMlQueue.setWebView(null)
        VideoFrameAnalyzer.stopAnalysis()
        webView.destroy()
        super.onDestroy()
    }
}
