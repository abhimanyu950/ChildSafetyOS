package com.childsafety.os.policy

/**
 * Comprehensive domain blocking policy with category-based filtering.
 * Competes with commercial parental control solutions.
 */
object DomainPolicy {

    // ========== CATEGORY: ADULT/PORNOGRAPHY ==========
    private val adultDomains = setOf(
        // Major porn sites
        "pornhub.com", "xvideos.com", "xnxx.com", "redtube.com", "youporn.com",
        "xhamster.com", "spankbang.com", "beeg.com", "tube8.com", "xtube.com",
        "porn.com", "sex.com", "xxxvideos.com", "porntrex.com", "eporner.com",
        "motherless.com", "thumbzilla.com", "pornone.com", "tnaflix.com",
        "drtuber.com", "sunporno.com", "txxx.com", "voyeurhit.com", "hclips.com",
        "porn300.com", "fuq.com", "4tube.com", "fapvid.com", "porndig.com",
        // Hentai/Anime adult
        "hentaihaven.xxx", "hanime.tv", "nhentai.net", "hentai.tv", "fakku.net",
        // OnlyFans type
        "onlyfans.com", "fansly.com", "justforfans.com", "fancentro.com",
        // Adult dating
        "adultfriendfinder.com", "ashleymadison.com", "fling.com",
        // Cam sites
        "chaturbate.com", "livejasmin.com", "stripchat.com", "bongacams.com",
        "myfreecams.com", "camsoda.com", "cam4.com", "flirt4free.com"
    )

    // ========== CATEGORY: GAMBLING ==========
    private val gamblingDomains = setOf(
        "bet365.com", "betway.com", "888casino.com", "pokerstars.com",
        "draftkings.com", "fanduel.com", "bovada.lv", "betfair.com",
        "williamhill.com", "ladbrokes.com", "paddypower.com", "unibet.com",
        "bwin.com", "betsson.com", "1xbet.com", "betonline.ag", "stake.com"
    )

    // ========== CATEGORY: DRUGS ==========
    private val drugDomains = setOf(
        "erowid.org", "shroomery.org", "420magazine.com", "hightimes.com",
        "leafly.com", "weedmaps.com", "grasscity.com"
    )

    // ========== CATEGORY: VIOLENCE/GORE ==========
    private val violenceDomains = setOf(
        "liveleak.com", "bestgore.com", "documentingreality.com",
        "theync.com", "kaotic.com", "crazyshit.com", "efukt.com"
    )

    // ========== CATEGORY: PROXY/VPN (to prevent bypass) ==========
    private val proxyDomains = setOf(
        "hide.me", "proxysite.com", "hidemyass.com", "kproxy.com",
        "croxyproxy.com", "proxysite.one", "unblocksite.org", "unblockit.tv",
        "filterbypass.me", "vpnbook.com", "4everproxy.com", "webproxy.to"
    )

    // ========== CATEGORY: SOCIAL MEDIA (optional - can be toggled) ==========
    private val socialMediaDomains = setOf(
        "tiktok.com", "snapchat.com", "twitter.com", "x.com",
        "reddit.com", "4chan.org", "8kun.top"
    )

    // ========== CATEGORY: DATING APPS ==========
    private val datingDomains = setOf(
        "tinder.com", "bumble.com", "hinge.co", "okcupid.com",
        "match.com", "pof.com", "zoosk.com", "badoo.com"
    )

    // Combined blocklist
    private val allBlockedDomains = adultDomains + gamblingDomains + 
        drugDomains + violenceDomains + proxyDomains + socialMediaDomains + datingDomains

    /**
     * Evaluates if a domain should be blocked
     */
    fun evaluate(host: String?): PolicyDecision {
        if (host.isNullOrBlank()) {
            return PolicyDecision.allow()
        }

        val h = host.lowercase()
        
        // Check each category for specific blocking reason
        val category = when {
            adultDomains.any { h == it || h.endsWith(".$it") } -> BlockCategory.ADULT
            gamblingDomains.any { h == it || h.endsWith(".$it") } -> BlockCategory.GAMBLING
            drugDomains.any { h == it || h.endsWith(".$it") } -> BlockCategory.DRUGS
            violenceDomains.any { h == it || h.endsWith(".$it") } -> BlockCategory.VIOLENCE
            proxyDomains.any { h == it || h.endsWith(".$it") } -> BlockCategory.PROXY
            socialMediaDomains.any { h == it || h.endsWith(".$it") } -> BlockCategory.SOCIAL_MEDIA
            datingDomains.any { h == it || h.endsWith(".$it") } -> BlockCategory.DATING
            else -> null
        }

        return if (category != null) {
            PolicyDecision.block(PolicyReason.DOMAIN, category)
        } else {
            PolicyDecision.allow()
        }
    }

    /**
     * Categories of blocked content for better UX messaging
     */
    enum class BlockCategory {
        ADULT,
        GAMBLING,
        DRUGS,
        VIOLENCE,
        PROXY,
        SOCIAL_MEDIA,
        DATING,
        EXPLICIT_TEXT,
        EXPLICIT_IMAGE
    }
}

