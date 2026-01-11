package com.childsafety.os.policy

/**
 * Trusted image domains that bypass ML analysis.
 * 
 * CRITICAL: Only include domains that host CURATED, SAFE content.
 * DO NOT include:
 * - CDNs that serve arbitrary content (gstatic, cloudfront, etc.)
 * - Search engines (Google Images serves explicit content!)
 * - Social media (user-generated content)
 * - Image hosting services
 */
object TrustedImageDomains {

    private val trusted = listOf(
        // Educational institutions - SAFE, curated content only
        "wikimedia.org",          // Wikipedia images (curated)
        "wikipedia.org",          // Wikipedia 
        "britannica.com",         // Encyclopedia Britannica
        "khanacademy.org",        // Khan Academy educational content
        "coursera.org",           // Coursera educational platform
        "edx.org",                // EdX educational platform
        "mit.edu",                // MIT educational content
        "stanford.edu",           // Stanford educational content
        "harvard.edu",            // Harvard
        "yale.edu",               // Yale
        "nasa.gov",               // NASA images (safe government content)
        "nationalgeographic.com", // Nat Geo (professional photography)
        
        // News organizations - Professional journalism, safe images
        "bbc.co.uk",              // BBC News
        "bbc.com",                // BBC International
        "reuters.com",            // Reuters news wire
        "apnews.com"              // Associated Press
    )

    /**
     * Check if URL is from a trusted domain.
     * Uses STRICT ending match to prevent bypasses.
     */
    fun isTrusted(url: String): Boolean {
        if (url.isBlank()) return false
        
        try {
            val host = java.net.URL(url).host.lowercase()
            // STRICT match: host must END with trusted domain
            return trusted.any { domain ->
                host == domain || host.endsWith(".$domain")
            }
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Check if host is explicitly trusted.
     */
    fun isHostTrusted(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val lowerHost = host.lowercase()
        
        // STRICT match: must END with trusted domain
        return trusted.any { domain ->
            lowerHost == domain || lowerHost.endsWith(".$domain")
        }
    }
}
