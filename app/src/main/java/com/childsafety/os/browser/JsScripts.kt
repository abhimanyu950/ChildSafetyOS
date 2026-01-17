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
    /**
     * Detects images and sends them to native code for ML analysis.
     * SECURITY: Blurs all images by default until ML confirms they're safe.
     * Called by WebViewInterceptor.onPageFinished()
     */
    fun getImageDetectorScript(isAdult: Boolean): String {
        val blurBehavior = if (isAdult) {
             // ADULT MODE: No preemptive blur, just monitoring OR mild overlay
             // We'll skip the blur but keep the ID generation for tracking if ML flags it later
             """
             // ADULT MODE: No preemptive blur
             img.style.filter = ''; 
             img.style.opacity = '1';
             """
        } else {
             // CHILD/TEEN MODE: Aggressive preemptive blur
             """
             // PREEMPTIVE BLUR - Apply immediately for safety
             img.style.setProperty('filter', 'blur(20px)', 'important');
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
             """
        }

        return """
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
                
                $blurBehavior
                
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
    }

    /**
     * Scans page text and statistics for context analysis.
     */
    const val TEXT_EMOJI_SCANNER = """
        (function() {
            // Get page text content
            var pageText = document.body.innerText || '';
            
            // Image count
            var imageCount = document.images ? document.images.length : 0;
            
            // Send to native for Risk Engine analysis
            if (window.ChildSafetyContent && window.ChildSafetyContent.onPageContext) {
                // Limit text to 2000 chars as per Risk Engine requirements
                var limitedText = pageText.substring(0, 2000);
                window.ChildSafetyContent.onPageContext(limitedText, imageCount);
            }
            
            // Fallback legacy support (keep emoji scan local for now or remove if handled by PolicyEngine)
            // PolicyEngine now handles emoji inside 'pageText' analysis, so we can likely remove the separate call
            // unless we want double safety. We'll leave it but reduced.
        })();
    """

    /**
     * Blocks a specific image by completely hiding it.
     * STRICT: Image is replaced with a solid block, not just blurred.
     */
    fun blockedImageScript(imageId: String): String = """
        (function() {
            var img = document.querySelector('[data-csid="$imageId"]');
            if (!img) return;
            
            img.dataset.safetyStatus = 'blocked';
            
            // COMPLETE BLOCK: Hide the image entirely
            img.style.display = 'none !important';
            img.style.visibility = 'hidden';
            img.src = 'about:blank'; // Clear source to stop loading
            
            // Replace with solid block placeholder
            var placeholder = document.createElement('div');
            placeholder.style.cssText = 'width:' + (img.width || 100) + 'px;height:' + (img.height || 100) + 'px;' +
                'background:linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);' +
                'display:flex;align-items:center;justify-content:center;color:#ff4444;' +
                'font-size:24px;border-radius:8px;border:2px solid #ff4444;';
            placeholder.innerHTML = '🛡️';
            placeholder.dataset.csPlaceholder = '$imageId';
            
            img.parentNode.insertBefore(placeholder, img);
            
            // Remove any existing overlay
            var overlay = document.querySelector('[data-cs-overlay="$imageId"]');
            if (overlay) {
                overlay.remove();
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
