package com.childsafety.os.browser

import com.childsafety.os.policy.DomainPolicy.BlockCategory

/**
 * HTML templates for blocked content messages.
 * Category-specific with child-friendly UI.
 */
object BlockedPageHtml {

    /**
     * Get blocked page HTML based on category
     */
    fun forCategory(category: BlockCategory?, domain: String = ""): String {
        return when (category) {
            BlockCategory.ADULT -> blockedPage(
                emoji = "🔞",
                title = "Adult Content Blocked",
                message = "This website contains adult content that isn't appropriate for you.",
                domain = domain,
                gradient = "#e53e3e, #c53030"
            )
            BlockCategory.GAMBLING -> blockedPage(
                emoji = "🎰",
                title = "Gambling Site Blocked",
                message = "Gambling websites are not allowed. These sites can be harmful and addictive.",
                domain = domain,
                gradient = "#d69e2e, #b7791f"
            )
            BlockCategory.DRUGS -> blockedPage(
                emoji = "💊",
                title = "Drug Content Blocked",
                message = "This website contains drug-related content that isn't safe for you.",
                domain = domain,
                gradient = "#38a169, #276749"
            )
            BlockCategory.VIOLENCE -> blockedPage(
                emoji = "⚠️",
                title = "Violent Content Blocked",
                message = "This website contains violent or disturbing content.",
                domain = domain,
                gradient = "#2d3748, #1a202c"
            )
            BlockCategory.PROXY -> blockedPage(
                emoji = "🚫",
                title = "Proxy Site Blocked",
                message = "Proxy and VPN bypass sites are blocked to keep you safe.",
                domain = domain,
                gradient = "#805ad5, #6b46c1"
            )
            BlockCategory.SOCIAL_MEDIA -> blockedPage(
                emoji = "📱",
                title = "Social Media Blocked",
                message = "This social media site has been blocked by your parent.",
                domain = domain,
                gradient = "#3182ce, #2b6cb0"
            )
            BlockCategory.DATING -> blockedPage(
                emoji = "💔",
                title = "Dating Site Blocked",
                message = "Dating websites are not appropriate for your age.",
                domain = domain,
                gradient = "#ed64a6, #d53f8c"
            )
            BlockCategory.EXPLICIT_TEXT -> blockedTextPage()
            BlockCategory.EXPLICIT_IMAGE -> blockedPage(
                emoji = "🖼️",
                title = "Image Blocked",
                message = "An inappropriate image was detected and blocked.",
                domain = "",
                gradient = "#667eea, #764ba2"
            )
            else -> blockedPage(
                emoji = "🛡️",
                title = "Website Blocked",
                message = "This website has been blocked by Parental Controls.",
                domain = domain,
                gradient = "#667eea, #764ba2"
            )
        }
    }

    /**
     * Generic blocked page with customizable styling
     */
    private fun blockedPage(
        emoji: String,
        title: String,
        message: String,
        domain: String,
        gradient: String
    ): String = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    background: linear-gradient(135deg, $gradient);
                    min-height: 100vh;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    padding: 20px;
                }
                .container {
                    background: white;
                    border-radius: 24px;
                    padding: 40px 30px;
                    text-align: center;
                    max-width: 400px;
                    box-shadow: 0 20px 60px rgba(0,0,0,0.3);
                    animation: slideUp 0.5s ease-out;
                }
                @keyframes slideUp {
                    from { opacity: 0; transform: translateY(30px); }
                    to { opacity: 1; transform: translateY(0); }
                }
                .emoji {
                    font-size: 72px;
                    margin-bottom: 20px;
                    animation: pulse 2s infinite;
                }
                @keyframes pulse {
                    0%, 100% { transform: scale(1); }
                    50% { transform: scale(1.1); }
                }
                h1 {
                    color: #2d3748;
                    font-size: 22px;
                    margin-bottom: 16px;
                    font-weight: 700;
                }
                p {
                    color: #718096;
                    font-size: 15px;
                    line-height: 1.6;
                    margin-bottom: 16px;
                }
                .domain {
                    background: #f7fafc;
                    color: #e53e3e;
                    padding: 8px 16px;
                    border-radius: 8px;
                    font-family: monospace;
                    font-size: 13px;
                    margin-bottom: 24px;
                    display: inline-block;
                    word-break: break-all;
                }
                .search-btn {
                    background: linear-gradient(135deg, $gradient);
                    color: white;
                    border: none;
                    padding: 14px 28px;
                    border-radius: 12px;
                    font-size: 15px;
                    font-weight: 600;
                    cursor: pointer;
                    text-decoration: none;
                    display: inline-block;
                    transition: transform 0.2s, box-shadow 0.2s;
                }
                .search-btn:hover {
                    transform: translateY(-2px);
                    box-shadow: 0 4px 12px rgba(0,0,0,0.2);
                }
                .footer {
                    margin-top: 24px;
                    color: #a0aec0;
                    font-size: 11px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    gap: 6px;
                }
                .shield { font-size: 14px; }
                .help-text {
                    background: #edf2f7;
                    border-radius: 8px;
                    padding: 12px;
                    margin-top: 16px;
                    font-size: 12px;
                    color: #4a5568;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="emoji">$emoji</div>
                <h1>$title</h1>
                <p>$message</p>
                ${if (domain.isNotEmpty()) "<div class=\"domain\">$domain</div>" else ""}
                <a href="https://www.google.com" class="search-btn">🔍 Search Something Else</a>
                <div class="help-text">
                    💬 If you think this is a mistake, please talk to your parent.
                </div>
                <div class="footer">
                    <span class="shield">🛡️</span>
                    Protected by ChildSafetyOS
                </div>
            </div>
        </body>
        </html>
    """.trimIndent()

    /**
     * Page shown when explicit text/emoji is detected
     */
    fun blockedTextPage(): String = blockedPage(
        emoji = "📝",
        title = "Content Blocked",
        message = "This page contains words or content that have been blocked for your safety.",
        domain = "",
        gradient = "#f093fb, #f5576c"
    )

    /**
     * Page shown when explicit video content is detected
     */
    fun blockedVideoPage(reason: String, domain: String = ""): String = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
                    min-height: 100vh;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    padding: 20px;
                }
                .container {
                    background: rgba(255,255,255,0.95);
                    border-radius: 24px;
                    padding: 40px 30px;
                    text-align: center;
                    max-width: 400px;
                    box-shadow: 0 20px 60px rgba(0,0,0,0.4);
                    animation: slideUp 0.4s ease-out;
                }
                @keyframes slideUp {
                    from { opacity: 0; transform: translateY(30px); }
                    to { opacity: 1; transform: translateY(0); }
                }
                .emoji {
                    font-size: 80px;
                    margin-bottom: 20px;
                }
                h1 {
                    color: #e53e3e;
                    font-size: 24px;
                    margin-bottom: 16px;
                    font-weight: 700;
                }
                .reason {
                    background: #fff5f5;
                    color: #c53030;
                    padding: 12px 16px;
                    border-radius: 12px;
                    font-size: 14px;
                    margin-bottom: 20px;
                    border: 1px solid #feb2b2;
                }
                p {
                    color: #4a5568;
                    font-size: 15px;
                    line-height: 1.6;
                    margin-bottom: 24px;
                }
                .btn {
                    background: linear-gradient(135deg, #667eea, #764ba2);
                    color: white;
                    border: none;
                    padding: 14px 32px;
                    border-radius: 12px;
                    font-size: 16px;
                    font-weight: 600;
                    cursor: pointer;
                    text-decoration: none;
                    display: inline-block;
                    margin: 8px;
                }
                .btn-danger {
                    background: linear-gradient(135deg, #e53e3e, #c53030);
                }
                .footer {
                    margin-top: 24px;
                    color: #a0aec0;
                    font-size: 12px;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="emoji">🛡️</div>
                <h1>Video Blocked</h1>
                <div class="reason">$reason</div>
                <p>This video was automatically blocked because our AI detected content that isn't safe for you.</p>
                <button class="btn btn-danger" onclick="history.back()">← Go Back</button>
                <a href="https://www.google.com" class="btn">🔍 Search</a>
                <div class="footer">
                    🛡️ Protected by ChildSafetyOS<br>
                    ${if (domain.isNotEmpty()) "<small>$domain</small>" else ""}
                </div>
            </div>
        </body>
        </html>
    """.trimIndent()

    /**
     * Legacy method for domain blocking
     */
    fun blockedDomainPage(domain: String): String = forCategory(null, domain)

    /**
     * JavaScript to replace a blocked image with placeholder
     */
    fun blockedImageJs(imageId: String): String = """
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

