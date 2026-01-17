package com.childsafety.os.policy

import android.net.Uri
import android.util.Log

/**
 * URL Path Analyzer - Detects explicit content in URL paths and query parameters.
 * 
 * Use case: Block explicit content on otherwise safe domains
 * Example: reddit.com/r/nsfw, imgur.com/a/explicit-album
 * 
 * This adds an extra layer of protection beyond domain-level blocking.
 */
object UrlPathAnalyzer {

    private const val TAG = "UrlPathAnalyzer"

    /**
     * Result of URL path analysis
     */
    data class PathAnalysisResult(
        val isExplicit: Boolean,
        val matchedPattern: String? = null,
        val category: ExplicitCategory? = null,
        val confidence: Float = 0f
    )

    enum class ExplicitCategory {
        ADULT_SUBREDDIT,
        EXPLICIT_PATH,
        EXPLICIT_QUERY,
        EXPLICIT_FILENAME,
        NSFW_TAG
    }

    // ========== REDDIT NSFW SUBREDDITS ==========
    private val nsfwSubreddits = setOf(
        "nsfw", "gonewild", "porn", "sex", "nudes", "nsfw_gifs",
        "realgirls", "amateur", "ass", "boobs", "tits", "milf",
        "petitegonewild", "asiansgonewild", "indiansgonewild",
        "onoff", "curvy", "thickthighs", "biggerthanyouthought",
        "bustypetite", "collegesluts", "hotwife", "cuckold",
        "bdsm", "bondage", "femdom", "hentai", "rule34",
        "nsfw_snapchat", "dirtysnapchat", "sextingfriendfinder"
    )

    // ========== EXPLICIT PATH PATTERNS ==========
    private val explicitPathPatterns = listOf(
        "/xxx/", "/porn/", "/adult/", "/nsfw/", "/nude/", "/naked/",
        "/sex/", "/erotic/", "/fetish/", "/hentai/", "/r18/",
        "/18+/", "/mature/", "/explicit/", "/uncensored/"
    )

    // ========== EXPLICIT QUERY KEYWORDS ==========
    private val explicitQueryKeywords = listOf(
        "porn", "sex", "xxx", "nude", "naked", "nsfw",
        "hentai", "adult", "erotic", "fetish", "explicit"
    )

    // ========== EXPLICIT FILENAME PATTERNS ==========
    private val explicitFilenamePatterns = listOf(
        Regex(".*porn.*\\.(jpg|jpeg|png|gif|webp|mp4|webm)", RegexOption.IGNORE_CASE),
        Regex(".*nude.*\\.(jpg|jpeg|png|gif|webp|mp4|webm)", RegexOption.IGNORE_CASE),
        Regex(".*xxx.*\\.(jpg|jpeg|png|gif|webp|mp4|webm)", RegexOption.IGNORE_CASE),
        Regex(".*nsfw.*\\.(jpg|jpeg|png|gif|webp|mp4|webm)", RegexOption.IGNORE_CASE),
        Regex(".*sex.*\\.(jpg|jpeg|png|gif|webp|mp4|webm)", RegexOption.IGNORE_CASE)
    )

    /**
     * Analyze a URL for explicit content in path/query
     */
    fun analyze(url: String): PathAnalysisResult {
        if (url.isBlank()) {
            return PathAnalysisResult(isExplicit = false)
        }

        try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase() ?: ""
            val path = uri.path?.lowercase() ?: ""
            val query = uri.query?.lowercase() ?: ""
            val fullUrl = url.lowercase()

            // 1. Check Reddit NSFW subreddits
            if (host.contains("reddit.com") || host.contains("redd.it")) {
                val subredditMatch = Regex("/r/([^/]+)").find(path)
                if (subredditMatch != null) {
                    val subreddit = subredditMatch.groupValues[1]
                    if (nsfwSubreddits.contains(subreddit)) {
                        Log.w(TAG, "NSFW subreddit detected: r/$subreddit")
                        return PathAnalysisResult(
                            isExplicit = true,
                            matchedPattern = "r/$subreddit",
                            category = ExplicitCategory.ADULT_SUBREDDIT,
                            confidence = 0.95f
                        )
                    }
                }
            }

            // 2. Check explicit path patterns
            for (pattern in explicitPathPatterns) {
                if (path.contains(pattern)) {
                    Log.w(TAG, "Explicit path pattern detected: $pattern")
                    return PathAnalysisResult(
                        isExplicit = true,
                        matchedPattern = pattern,
                        category = ExplicitCategory.EXPLICIT_PATH,
                        confidence = 0.90f
                    )
                }
            }

            // 3. Check query parameters for explicit keywords
            for (keyword in explicitQueryKeywords) {
                if (query.contains(keyword)) {
                    Log.w(TAG, "Explicit query keyword detected: $keyword")
                    return PathAnalysisResult(
                        isExplicit = true,
                        matchedPattern = keyword,
                        category = ExplicitCategory.EXPLICIT_QUERY,
                        confidence = 0.85f
                    )
                }
            }

            // 4. Check filename patterns in URL
            for (pattern in explicitFilenamePatterns) {
                if (pattern.matches(fullUrl)) {
                    Log.w(TAG, "Explicit filename pattern detected: ${pattern.pattern}")
                    return PathAnalysisResult(
                        isExplicit = true,
                        matchedPattern = pattern.pattern,
                        category = ExplicitCategory.EXPLICIT_FILENAME,
                        confidence = 0.80f
                    )
                }
            }

            // 5. Check for NSFW tags in Imgur, Tumblr, etc.
            if (host.contains("imgur.com") || host.contains("tumblr.com")) {
                if (path.contains("/nsfw") || query.contains("nsfw=1") || query.contains("mature=1")) {
                    Log.w(TAG, "NSFW tag detected on ${uri.host}")
                    return PathAnalysisResult(
                        isExplicit = true,
                        matchedPattern = "nsfw_tag",
                        category = ExplicitCategory.NSFW_TAG,
                        confidence = 0.85f
                    )
                }
            }

            // 6. Check for common adult URL patterns
            val adultUrlIndicators = listOf(
                "onlyfans.com/", "fansly.com/", "patreon.com/nsfw",
                "pornhub.com", "xvideos.com", "xhamster.com"
            )
            for (indicator in adultUrlIndicators) {
                if (fullUrl.contains(indicator)) {
                    return PathAnalysisResult(
                        isExplicit = true,
                        matchedPattern = indicator,
                        category = ExplicitCategory.EXPLICIT_PATH,
                        confidence = 0.95f
                    )
                }
            }

            return PathAnalysisResult(isExplicit = false)

        } catch (e: Exception) {
            Log.e(TAG, "URL analysis error: $url", e)
            return PathAnalysisResult(isExplicit = false)
        }
    }

    /**
     * Quick check if URL path contains explicit content
     */
    fun isExplicit(url: String): Boolean {
        return analyze(url).isExplicit
    }

    /**
     * Get human-readable reason for blocking
     */
    fun getBlockReason(result: PathAnalysisResult): String {
        return when (result.category) {
            ExplicitCategory.ADULT_SUBREDDIT -> "NSFW subreddit: ${result.matchedPattern}"
            ExplicitCategory.EXPLICIT_PATH -> "Explicit content path detected"
            ExplicitCategory.EXPLICIT_QUERY -> "Explicit search query detected"
            ExplicitCategory.EXPLICIT_FILENAME -> "Explicit media file detected"
            ExplicitCategory.NSFW_TAG -> "Content marked as NSFW"
            null -> "Unknown explicit content"
        }
    }
}
