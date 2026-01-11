package com.childsafety.os.browser

/**
 * JavaScript scripts for WebView content analysis.
 * 
 * These scripts are injected into pages to:
 * - Detect and report images for ML analysis
 * - Blur images immediately (preemptive safety)
 * - Scan text for explicit content patterns
 * - Detect risky emoji combinations
 */
object JsScripts {

    /**
     * Detects images and sends them to native code for ML analysis.
     * SECURITY: Blurs all images by default until ML confirms they're safe.
     * Called by WebViewInterceptor.onPageFinished()
     */
    const val IMAGE_DETECTOR = """
        (function() {
            // Generate unique ID for images
            function generateId() {
                return 'cs_' + Math.random().toString(36).substr(2, 9);
            }
            
            // Process an image element
            function processImage(img) {
                if (img.dataset.csid) return; // Already processed
                if (!img.src || img.src.startsWith('data:')) return; // Skip data URIs
                if (img.naturalWidth < 50 || img.naturalHeight < 50) return; // Skip tiny images
                
                var imageId = generateId();
                img.dataset.csid = imageId;
                img.dataset.safetyStatus = 'pending';
                
                // PREEMPTIVE BLUR - Apply immediately for safety
                img.style.filter = 'blur(20px)';
                img.style.transition = 'filter 0.3s ease-in-out';
                img.style.opacity = '0.7';
                
                // Add checking overlay
                var wrapper = img.parentElement;
                if (wrapper && wrapper.style.position !== 'relative') {
                    wrapper.style.position = 'relative';
                }
                
                var overlay = document.createElement('div');
                overlay.className = 'cs-checking-overlay';
                overlay.dataset.csOverlay = imageId;
                overlay.style.cssText = 'position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);background:rgba(0,0,0,0.6);color:white;padding:6px 12px;border-radius:6px;font-size:11px;font-weight:600;pointer-events:none;z-index:1000;white-space:nowrap;box-shadow:0 2px 8px rgba(0,0,0,0.3);';
                overlay.textContent = '🔒 Checking...';
                
                if (wrapper) {
                    wrapper.appendChild(overlay);
                }
                
                // Check if image is in viewport (for priority processing)
                var rect = img.getBoundingClientRect();
                var isInViewport = (
                    rect.top >= 0 &&
                    rect.left >= 0 &&
                    rect.bottom <= (window.innerHeight || document.documentElement.clientHeight) &&
                    rect.right <= (window.innerWidth || document.documentElement.clientWidth)
                );
                
                // Calculate image area for priority
                var area = img.naturalWidth * img.naturalHeight;
                
                // Send to native for ML analysis with priority info
                if (window.ChildSafety && window.ChildSafety.onImageFoundPriority) {
                    // Priority: in viewport + large images = high priority
                    var priority = isInViewport ? 100 : 0;
                    priority += Math.min(Math.floor(area / 10000), 50); // Area bonus
                    
                    window.ChildSafety.onImageFoundPriority(imageId, img.src, priority, isInViewport);
                } else if (window.ChildSafety && window.ChildSafety.onImageFound) {
                    // Fallback for old interface
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
    """

    /**
     * Scans page text and emojis for explicit content.
     * Called by WebViewContentEnforcer.inject()
     */
    const val TEXT_EMOJI_SCANNER = """
        (function() {
            // Get page text content
            var pageText = document.body.innerText || '';
            
            // Send to native for analysis if content bridge exists
            if (window.ChildSafetyContent && window.ChildSafetyContent.onTextFound) {
                // Limit text to prevent performance issues
                var limitedText = pageText.substring(0, 5000);
                window.ChildSafetyContent.onTextFound(limitedText);
            }
            
            // Emoji pattern detection (suggestive emoji combinations)
            var emojiPattern = /[\u{1F346}\u{1F351}\u{1F353}\u{1F34C}\u{1F4A6}\u{1F60D}\u{1F618}\u{1F48B}]/gu;
            var emojis = pageText.match(emojiPattern) || [];
            
            if (emojis.length > 3) {
                if (window.ChildSafetyContent && window.ChildSafetyContent.onEmojiRisk) {
                    window.ChildSafetyContent.onEmojiRisk(emojis.join(''));
                }
            }
        })();
    """

    /**
     * Blocks a specific image by replacing it with a placeholder.
     */
    fun blockedImageScript(imageId: String): String = """
        (function() {
            var img = document.querySelector('[data-csid="$imageId"]');
            if (!img) return;
            
            img.dataset.safetyStatus = 'blocked';
            
            // Keep heavily blurred
            img.style.filter = 'blur(30px)';
            img.style.opacity = '0.3';
            
            // Update overlay to blocked state
            var overlay = document.querySelector('[data-cs-overlay="$imageId"]');
            if (overlay) {
                overlay.style.background = 'rgba(220, 38, 38, 0.85)';
                overlay.innerHTML = '🚫 Blocked';
            }
        })();
    """.trimIndent()
    
    /**
     * Unblurs a safe image after ML analysis confirms it's appropriate.
     */
    fun unblurSafeImageScript(imageId: String): String = """
        (function() {
            var img = document.querySelector('[data-csid="$imageId"]');
            if (!img) return;
            
            img.dataset.safetyStatus = 'safe';
            
            // Remove blur with smooth transition
            img.style.filter = 'none';
            img.style.opacity = '1';
            
            // Remove checking overlay
            var overlay = document.querySelector('[data-cs-overlay="$imageId"]');
            if (overlay) {
                overlay.style.opacity = '0';
                setTimeout(function() {
                    if (overlay && overlay.parentNode) {
                        overlay.parentNode.removeChild(overlay);
                    }
                }, 300);
            }
        })();
    """.trimIndent()
}
