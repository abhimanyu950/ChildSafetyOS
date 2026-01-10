package com.childsafety.os.policy

/**
 * Trusted image domains that bypass ML analysis.
 * 
 * These are known-safe sources where we can skip NSFW scanning
 * to improve performance and prevent false positives.
 */
object TrustedImageDomains {

    private val trusted = listOf(
        // Google services
        "googleusercontent.com",
        "gstatic.com",
        "google.com",
        "googleapis.com",
        "ggpht.com",
        "ytimg.com",
        "googlevideo.com",
        
        // Microsoft services
        "bing.com",
        "bing.net",
        "msn.com",
        "microsoft.com",
        "azure.com",
        "live.com",
        
        // Amazon services
        "amazon.com",
        "amazonaws.com",
        "cloudfront.net",
        
        // Apple services
        "apple.com",
        "mzstatic.com",
        "icloud.com",
        
        // Educational sources
        "wikimedia.org",
        "wikipedia.org",
        "britannica.com",
        "nationalgeographic.com",
        "khanacademy.org",
        "coursera.org",
        "edx.org",
        "education.com",
        
        // News sources
        "bbc.co.uk",
        "bbc.com",
        "cnn.com",
        "reuters.com",
        "nytimes.com",
        
        // Social media CDNs (images from these are typically safe)
        "fbcdn.net",
        "twimg.com",
        "pinimg.com",
        "cdninstagram.com",
        
        // Other safe sources
        "cloudinary.com",
        "imgix.net",
        "fastly.net",
        "akamaized.net",
        "github.com",
        "githubusercontent.com"
    )

    /**
     * Check if URL is from a trusted domain.
     * Uses substring matching for performance.
     */
    fun isTrusted(url: String): Boolean {
        if (url.isBlank()) return false
        val lowerUrl = url.lowercase()
        return trusted.any { lowerUrl.contains(it) }
    }

    /**
     * Check if host is explicitly trusted.
     */
    fun isHostTrusted(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val lowerHost = host.lowercase()
        return trusted.any { lowerHost.contains(it) || lowerHost.endsWith(".$it") }
    }
}
