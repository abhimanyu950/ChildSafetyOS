package com.childsafety.os.browser

/**
 * JavaScript scripts for WebView content analysis.
 * 
 * These scripts are injected into pages to:
 * - Detect and report images for ML analysis
 * - Scan text for explicit content patterns
 * - Detect risky emoji combinations
 */
object JsScripts {

    /**
     * Detects images and sends them to native code for ML analysis.
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
            
            var placeholder = document.createElement('div');
            placeholder.style.cssText = 'display:flex;flex-direction:column;align-items:center;justify-content:center;background:linear-gradient(135deg,#e2e8f0 0%,#edf2f7 100%);border-radius:12px;padding:24px;min-height:140px;border:2px solid #cbd5e0;margin:8px 0;box-shadow:0 2px 8px rgba(0,0,0,0.1);';
            placeholder.innerHTML = '<div style="font-size:36px;margin-bottom:10px;">🛡️</div><div style="color:#4a5568;font-size:13px;font-weight:600;text-align:center;">Image blocked<br><span style="font-size:11px;color:#718096;font-weight:400;">Parental Controls</span></div>';
            
            img.parentNode.replaceChild(placeholder, img);
        })();
    """.trimIndent()
}
