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

        // FIX: Don't show search engine domains as the 'blocked source'
        // This prevents confusion (e.g. "www.google.com" appearing in red when searching for blocked terms)
        val displayDomain = if (isSearchEngineDomain(host)) {
             "Unsafe Search Content"
        } else {
             host
        }

        // Show blocked page
        val blockedHtml = BlockedPageHtml.forCategory(category, displayDomain)
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

    private fun isSearchEngineDomain(host: String): Boolean {
        val lowerHost = host.lowercase()
        return lowerHost.contains("google.") || 
               lowerHost.contains("bing.") || 
               lowerHost.contains("yahoo.") || 
               lowerHost.contains("duckduckgo.") ||
               lowerHost.contains("search.")
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

                // 2. Check search queries for explicit keywords (MOVED UP)
                val isSearchEngine = host.contains("google.") || 
                                     host.contains("bing.") || 
                                     host.contains("yahoo.") || 
                                     host.contains("duckduckgo.") ||
                                     host.contains("search.")

                if (isSearchEngine) {
                    // IMPORTANT: Never block the homepage (empty query / back button navigation)
                    val isHomepage = listOf(
                        "www.google.com", "google.com", "www.google.co.in", "google.co.in",
                        "www.bing.com", "bing.com",
                        "www.duckduckgo.com", "duckduckgo.com",
                        "www.yahoo.com", "yahoo.com"
                    ).any { url.startsWith("https://$it/") && !url.contains("?q=") && !url.contains("&q=") }
                    
                    if (isHomepage) {
                        Log.d(TAG, "Allowing search engine homepage: $url")
                        return false
                    }
                    
                    val queryParams = listOf("q", "p", "query", "text", "search_query")
                    var searchQuery = ""
                    
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
                        // Fallback if URI parsing fails
                    }

                    if (searchQuery.isNotBlank()) {
                        val decodedQuery = try {
                            java.net.URLDecoder.decode(searchQuery, "UTF-8")
                        } catch (e: Exception) {
                            searchQuery
                        }
                        
                        // === SYNCHRONOUS PRE-CHECK: Block CSAM and explicit terms IMMEDIATELY ===
                        // This prevents any content from loading for the most dangerous terms
                        if (com.childsafety.os.ai.KeywordRepository.containsAlwaysBlock(decodedQuery)) {
                            Log.w(TAG, "BLOCKED IMMEDIATELY: Always-block keyword in query")
                            blockNavigation(view, url, host, DomainPolicy.BlockCategory.EXPLICIT_TEXT)
                            return true
                        }
                        
                        // Synchronous high-risk keyword check (fast, rule-based)
                        val quickRiskScore = com.childsafety.os.ai.KeywordRepository.getRiskScore(decodedQuery)
                        if (quickRiskScore >= 0.6f && currentAgeGroup == AgeGroup.CHILD) {
                            Log.w(TAG, "BLOCKED (sync): High-risk keywords in query (score=$quickRiskScore)")
                            blockNavigation(view, url, host, DomainPolicy.BlockCategory.EXPLICIT_TEXT)
                            
                            // Log to Firebase
                            com.childsafety.os.cloud.FirebaseManager.logSearchBlock(
                                searchQuery = decodedQuery,
                                url = url,
                                domain = host,
                                reason = "High-risk keywords detected (quick check)"
                            )
                            return true
                        }
                        
                        // === ASYNC ML ANALYSIS for nuanced detection ===
                        // This catches context-dependent cases the quick check might miss
                        val originalUrl = url // Capture for comparison after async
                        lifecycleScope.launch(Dispatchers.Default) {
                            val classification = com.childsafety.os.ai.TextRiskClassifier.classify(
                                this@SafeBrowserActivity, 
                                decodedQuery, 
                                currentAgeGroup
                            )

                            withContext(Dispatchers.Main) {
                                // SAFETY CHECK: Only block if we're still on the same page
                                // This prevents blocking after user pressed back button
                                val currentWebUrl = webView.url ?: ""
                                if (!currentWebUrl.contains(decodedQuery.take(10))) {
                                    Log.d(TAG, "Skipping async block - page changed from $originalUrl to $currentWebUrl")
                                    return@withContext
                                }
                                
                                if (classification.isRisky) {
                                    Log.w(TAG, "Search query blocked (async): '$decodedQuery' (Confidence: ${classification.confidence})")
                                    
                                    com.childsafety.os.cloud.FirebaseManager.logSearchBlock(
                                        searchQuery = decodedQuery,
                                        url = url,
                                        domain = host,
                                        reason = classification.contextOverride ?: "Explicit content detected"
                                    )
                                    
                                    blockNavigation(view, url, host, DomainPolicy.BlockCategory.EXPLICIT_TEXT)
                                } else {
                                    Log.i(TAG, "Search query allowed: '$decodedQuery' (Context: ${classification.contextOverride ?: "None"})")
                                }
                            }
                        }
                    }
                }

                // 3. NEW: Check URL path for explicit content (Reddit NSFW, etc.)
                // MOVED AFTER SEARCH LOGIC to prevent flagging search URLs as generic path blocks
                val pathAnalysis = com.childsafety.os.policy.UrlPathAnalyzer.analyze(url)
                if (pathAnalysis.isExplicit) {
                    Log.w(TAG, "URL path blocked: ${pathAnalysis.matchedPattern}")
                    blockNavigation(view, url, host, DomainPolicy.BlockCategory.ADULT)
                    return true
                }

                // 4. Enforce SafeSearch parameters (Strict Mode)
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
     * CRITICAL: Images are blurred immediately until ML confirms they're safe.
     */
    private fun injectImageScanner(webView: WebView?) {
        // Check if we're on Google Images with a potentially risky query
        val currentUrl = webView?.url ?: ""
        
        // Block image tab for suggestive/risky queries immediately
        if (currentUrl.contains("tbm=isch") || currentUrl.contains("/images?")) {
            val uri = android.net.Uri.parse(currentUrl)
            val query = uri.getQueryParameter("q")?.lowercase() ?: ""
            
            // Suggestive queries that should block image tab completely for CHILD mode
            val riskyImageQueries = listOf(
                "hot", "hot girls", "hot girl", "sexy", "sexy girl",
                "bikini", "bikini girl", "swimsuit model",
                "bra", "bras", "lingerie", "underwear model",
                "actress hot", "celebrity hot", "model pic",
                "strip", "topless", "shirtless girl"
            )
            
            if (currentAgeGroup == AgeGroup.CHILD && riskyImageQueries.any { query.contains(it) }) {
                Log.w(TAG, "🚫 BLOCKING risky image search in CHILD mode: $query")
                val blockedHtml = BlockedPageHtml.forCategory(DomainPolicy.BlockCategory.EXPLICIT_IMAGE, "images.google.com")
                webView?.loadDataWithBaseURL(null, blockedHtml, "text/html", "UTF-8", null) ?: return
                
                com.childsafety.os.cloud.FirebaseManager.logSearchBlock(
                    searchQuery = query,
                    url = currentUrl,
                    domain = "images.google.com",
                    reason = "Risky image search blocked for child safety"
                )
                return
            }
        }
        
        // Inject enhanced image scanner with preemptive blur
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
                    img.dataset.safetyStatus = 'pending';
                    
                    // *** PREEMPTIVE BLUR - Applied immediately for safety ***
                    img.style.filter = 'blur(20px)';
                    img.style.transition = 'filter 0.3s ease-in-out';
                    img.style.opacity = '0.7';
                    
                    // Create checking overlay
                    var wrapper = img.parentElement;
                    if (wrapper && wrapper.style.position !== 'relative') {
                        wrapper.style.position = 'relative';
                    }
                    
                    var overlay = document.createElement('div');
                    overlay.className = 'cs-checking-overlay';
                    overlay.dataset.csOverlay = imageId;
                    overlay.style.cssText = 'position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);background:rgba(0,0,0,0.7);color:white;padding:6px 12px;border-radius:6px;font-size:11px;font-weight:600;pointer-events:none;z-index:1000;white-space:nowrap;';
                    overlay.textContent = '🔒 Checking...';
                    
                    if (wrapper) {
                        wrapper.appendChild(overlay);
                    }
                    
                    // Send to native for ML analysis
                    if (window.ChildSafety && window.ChildSafety.onImageFound) {
                        window.ChildSafety.onImageFound(imageId, img.src);
                    }
                }
                
                // Process all existing images
                document.querySelectorAll('img').forEach(processImage);
                
                // Observe new images (for lazy loading and infinite scroll)
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
                
                // Re-scan periodically for images loaded via JS (Google Images uses this)
                setInterval(function() {
                    document.querySelectorAll('img:not([data-csid])').forEach(processImage);
                }, 1000);
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
