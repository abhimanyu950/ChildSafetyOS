package com.childsafety.os.policy

/**
 * Comprehensive domain blocking policy with category-based filtering.
 * Commercial-grade parental control solution.
 * 
 * Total blocked domains: 200+
 * Categories: Adult, Gambling, Drugs, Violence, Proxy/VPN, Social Media, Dating, URL Shorteners
 */
object DomainPolicy {

    // ========== CATEGORY: ADULT/PORNOGRAPHY (100+ domains) ==========
    private val adultDomains = setOf(
        // Major tube sites
        "pornhub.com", "xvideos.com", "xnxx.com", "redtube.com", "youporn.com",
        "xhamster.com", "spankbang.com", "beeg.com", "tube8.com", "xtube.com",
        "porn.com", "sex.com", "xxxvideos.com", "porntrex.com", "eporner.com",
        "motherless.com", "thumbzilla.com", "pornone.com", "tnaflix.com",
        "drtuber.com", "sunporno.com", "txxx.com", "voyeurhit.com", "hclips.com",
        "porn300.com", "fuq.com", "4tube.com", "fapvid.com", "porndig.com",
        
        // Additional major sites
        "xnxx.tv", "pornhubpremium.com", "modelhub.com", "pornhublive.com",
        "pornmd.com", "tubegalore.com", "nudevista.com", "ixxx.com",
        "pornpics.com", "imagefap.com", "zishy.com", "femjoy.com",
        "metart.com", "hegre.com", "playboy.com", "penthouse.com",
        "brazzers.com", "realitykings.com", "bangbros.com", "naughtyamerica.com",
        "teamskeet.com", "vixen.com", "blacked.com", "tushy.com", "deeper.com",
        "mofos.com", "digitalplayground.com", "babes.com", "twistys.com",
        
        // Amateur/User-generated
        "xfree.com", "amateurporn.net", "homemoviestube.com",
        
        // Hentai/Anime adult
        "hentaihaven.xxx", "hanime.tv", "nhentai.net", "hentai.tv", "fakku.net",
        "rule34.xxx", "rule34.paheal.net", "e621.net", "gelbooru.com",
        "danbooru.donmai.us", "sankakucomplex.com", "hitomi.la",
        "tsumino.com", "hentai2read.com", "hentainexus.com",
        
        // OnlyFans type platforms
        "onlyfans.com", "fansly.com", "justforfans.com", "fancentro.com",
        "manyvids.com", "clips4sale.com", "iwantclips.com", "loyalfans.com",
        
        // Adult dating/escort
        "adultfriendfinder.com", "ashleymadison.com", "fling.com",
        "bdsm.com", "fetlife.com", "collarspace.com",
        "seekingarrangement.com", "sugardaddymeet.com",
        
        // Cam sites
        "chaturbate.com", "livejasmin.com", "stripchat.com", "bongacams.com",
        "myfreecams.com", "camsoda.com", "cam4.com", "flirt4free.com",
        "streamate.com", "imlive.com", "camcontacts.com", "xlovecam.com",
        
        // Nude/Leak sites
        "thefappeningblog.com", "fapello.com", "leakedbb.com",
        "celebjihad.com", "drunkenstepfather.com"
    )

    // ========== CATEGORY: GAMBLING (40+ domains) ==========
    private val gamblingDomains = setOf(
        // Major betting sites
        "bet365.com", "betway.com", "888casino.com", "pokerstars.com",
        "draftkings.com", "fanduel.com", "bovada.lv", "betfair.com",
        "williamhill.com", "ladbrokes.com", "paddypower.com", "unibet.com",
        "bwin.com", "betsson.com", "1xbet.com", "betonline.ag", "stake.com",
        
        // Additional gambling
        "betmgm.com", "caesars.com", "mgmresorts.com", "partypoker.com",
        "888poker.com", "ggpoker.com", "wsop.com", "fulltiltpoker.com",
        "sportsbetting.ag", "mybookie.ag", "betrivers.com", "pointsbet.com",
        
        // Casino sites
        "casinoluck.com", "leovegas.com", "casumo.com", "mrgreen.com",
        "rizk.com", "videoslots.com", "slotsmillion.com",
        
        // Crypto gambling
        "stake.com", "roobet.com", "bc.game", "bustabit.com",
        "primedice.com", "luckydice.com"
    )

    // ========== CATEGORY: DRUGS (20+ domains) ==========
    private val drugDomains = setOf(
        "erowid.org", "shroomery.org", "420magazine.com", "hightimes.com",
        "leafly.com", "weedmaps.com", "grasscity.com", "rollitup.org",
        "icmag.com", "thcfarmer.com", "cannabis.com", "marijuana.com",
        "bluelight.org", "drugs-forum.com", "drugsdata.org",
        "psychonautwiki.org", "dmt-nexus.me", "tripsit.me"
    )

    // ========== CATEGORY: VIOLENCE/GORE (15+ domains) ==========
    private val violenceDomains = setOf(
        "liveleak.com", "bestgore.com", "documentingreality.com",
        "theync.com", "kaotic.com", "crazyshit.com", "efukt.com",
        "goregrish.com", "horriblevideos.com", "seegore.com",
        "deathdate.info", "deaths.news", "runthegauntlet.org",
        "shockgore.com", "deadhouse.org"
    )

    // ========== CATEGORY: PROXY/VPN/BYPASS (50+ domains) ==========
    private val proxyDomains = setOf(
        // Web proxies
        "hide.me", "proxysite.com", "hidemyass.com", "kproxy.com",
        "croxyproxy.com", "proxysite.one", "unblocksite.org", "unblockit.tv",
        "filterbypass.me", "vpnbook.com", "4everproxy.com", "webproxy.to",
        "proxfree.com", "proxysite.cloud", "unblock-proxy.com", "freeproxy.win",
        "hidester.com", "anonymouse.org", "anonymizer.com", "megaproxy.com",
        
        // VPN provider sites
        "nordvpn.com", "expressvpn.com", "surfshark.com", "cyberghostvpn.com",
        "privateinternetaccess.com", "protonvpn.com", "mullvad.net",
        "windscribe.com", "tunnelbear.com", "hotspotshield.com",
        "ipvanish.com", "purevpn.com", "vyprvpn.com", "strongvpn.com",
        "privatevpn.com", "torguard.net", "airvpn.org", "ivacy.com",
        
        // Tor-related
        "torproject.org", "torbrowser.com", "onion.link", "tor2web.org",
        "onion.to", "onion.ws", "onion.pet", "darkweb.link",
        
        // DNS bypass
        "dns.google", "cloudflare-dns.com", "1.1.1.1", "1.0.0.1",
        "doh.dns.sb", "dns.quad9.net",
        
        // Mirror/unblock sites
        "piratebayproxylist.com", "unblockit.click", "unblockit.li",
        "proxysite.video", "freeproxyserver.net"
    )

    // ========== CATEGORY: SOCIAL MEDIA (risky for children) ==========
    private val socialMediaDomains = setOf(
        "tiktok.com", "snapchat.com", "twitter.com", "x.com",
        "reddit.com", "4chan.org", "8kun.top", "8ch.net",
        "tumblr.com", "flickr.com", "imgur.com",
        "kik.com", "whisper.sh", "yikyak.com", "omegle.com",
        "chatroulette.com", "monkey.cool", "amino.app",
        "discord.com", "twitch.tv" // Can be toggled by parents
    )

    // ========== CATEGORY: DATING APPS ==========
    private val datingDomains = setOf(
        "tinder.com", "bumble.com", "hinge.co", "okcupid.com",
        "match.com", "pof.com", "zoosk.com", "badoo.com",
        "grindr.com", "scruff.com", "her.app", "taimi.com",
        "coffee.meetsbagel.com", "happn.com", "hily.com",
        "skout.com", "meetme.com", "tagged.com"
    )

    // ========== CATEGORY: URL SHORTENERS (can hide malicious links) ==========
    private val urlShortenerDomains = setOf(
        "bit.ly", "tinyurl.com", "goo.gl", "ow.ly", "t.co",
        "is.gd", "v.gd", "buff.ly", "clck.ru", "shorturl.at",
        "rb.gy", "cutt.ly", "short.io", "rebrand.ly"
        // Note: These are blocked only in CHILD mode
    )

    // Combined blocklist
    private val allBlockedDomains = adultDomains + gamblingDomains + 
        drugDomains + violenceDomains + proxyDomains + socialMediaDomains + 
        datingDomains + urlShortenerDomains

    /**
     * Evaluates if a domain should be blocked
     * Checks: Cloud whitelist → Cloud blocklist → Local blocklist
     */
    /**
     * Evaluates if a domain should be blocked based on Age Group.
     * 
     * Logic:
     * - CHILD: Strict blocking (All categories)
     * - TEEN: Allow Social Media, but block others
     * - ADULT: Allow Social, Dating, Gambling (log only), Block malicious/illegal
     */
    fun evaluate(host: String?, ageGroup: AgeGroup): PolicyDecision {
        if (host.isNullOrBlank()) {
            return PolicyDecision.allow()
        }

        val h = host.lowercase()
        
        // 1. Check cloud whitelist first (parent-allowed domains override everything)
        if (com.childsafety.os.cloud.CloudPolicySync.isAllowedByCloud(h)) {
            return PolicyDecision.allow()
        }
        
        // 2. Check cloud blocklist (dynamic parent additions override everything)
        if (com.childsafety.os.cloud.CloudPolicySync.isBlockedByCloud(h)) {
            return PolicyDecision.block(PolicyReason.DOMAIN, BlockCategory.CLOUD_BLOCKED)
        }
        
        // 3. Check local categories
        val category = when {
            adultDomains.any { h == it || h.endsWith(".$it") } -> BlockCategory.ADULT
            gamblingDomains.any { h == it || h.endsWith(".$it") } -> BlockCategory.GAMBLING
            drugDomains.any { h == it || h.endsWith(".$it") } -> BlockCategory.DRUGS
            violenceDomains.any { h == it || h.endsWith(".$it") } -> BlockCategory.VIOLENCE
            proxyDomains.any { h == it || h.endsWith(".$it") } -> BlockCategory.PROXY
            socialMediaDomains.any { h == it || h.endsWith(".$it") } -> BlockCategory.SOCIAL_MEDIA
            datingDomains.any { h == it || h.endsWith(".$it") } -> BlockCategory.DATING
            urlShortenerDomains.any { h == it || h.endsWith(".$it") } -> BlockCategory.URL_SHORTENER
            else -> null
        }

        // If no category matched, it's safe
        if (category == null) {
            return PolicyDecision.allow()
        }

        // 4. Age-Specific Policy Application
        return when (ageGroup) {
            AgeGroup.CHILD -> {
                // STRICT: Block everything in our lists
                PolicyDecision.block(PolicyReason.DOMAIN, category)
            }
            AgeGroup.TEEN -> {
                // MODERATE: Allow Social Media, block everything else
                if (category == BlockCategory.SOCIAL_MEDIA) {
                    PolicyDecision.allow()
                } else {
                    PolicyDecision.block(PolicyReason.DOMAIN, category)
                }
            }
            AgeGroup.ADULT -> {
                // LIBERAL/MONITOR: Block only illegal/dangerous, allow "vice"
                when (category) {
                    BlockCategory.ADULT -> PolicyDecision.block(PolicyReason.DOMAIN, category) // Still block hardcore porn by default (can be toggled in app settings theoretically)
                    BlockCategory.DRUGS, BlockCategory.VIOLENCE, BlockCategory.PROXY -> PolicyDecision.block(PolicyReason.DOMAIN, category)
                    // Allow these for adults:
                    BlockCategory.SOCIAL_MEDIA, BlockCategory.DATING, BlockCategory.GAMBLING, BlockCategory.URL_SHORTENER -> PolicyDecision.allow()
                    else -> PolicyDecision.allow()
                }
            }
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
        URL_SHORTENER,
        EXPLICIT_TEXT,
        EXPLICIT_IMAGE,
        CLOUD_BLOCKED  // Dynamically blocked by parent via Firestore
    }

    /**
     * Get numeric risk score for a domain (0-100)
     * Used by the new Risk Engine.
     */
    fun getDomainRiskScore(host: String?): Int {
        if (host.isNullOrBlank()) return 0
        
        val h = host.lowercase()
        
        // 1. Cloud Whitelist -> 0 Risk
        if (com.childsafety.os.cloud.CloudPolicySync.isAllowedByCloud(h)) return 0
        
        // 2. Cloud Blocklist -> High Risk
        if (com.childsafety.os.cloud.CloudPolicySync.isBlockedByCloud(h)) return 100

        // 3. Categorization Risk
        return when {
            adultDomains.any { h == it || h.endsWith(".$it") } -> 40 // Adult
            violenceDomains.any { h == it || h.endsWith(".$it") } -> 30 // Violence
            gamblingDomains.any { h == it || h.endsWith(".$it") } -> 25 // Gambling
            drugDomains.any { h == it || h.endsWith(".$it") } -> 20 // Illegal/Drugs
            proxyDomains.any { h == it || h.endsWith(".$it") } -> 15 // Proxy/Evasion
            datingDomains.any { h == it || h.endsWith(".$it") } -> 15 // Dating
            socialMediaDomains.any { h == it || h.endsWith(".$it") } -> 10 // Social
            urlShortenerDomains.any { h == it || h.endsWith(".$it") } -> 10 // Obfuscation
            else -> 0 // Neutral
        }
    }

    /**
     * Get TrustLevel for Antigravity Engine
     */
    fun getTrustLevel(host: String?): com.childsafety.os.policy.TrustLevel {
        if (host.isNullOrBlank()) return com.childsafety.os.policy.TrustLevel.NEUTRAL
        
        val h = host.lowercase()
        
        // HIGH TRUST: Edu, Gov, and major trusted search/tech
        if (h.endsWith(".edu") || h.endsWith(".gov") || 
            h.contains("wikipedia.org") || h.contains("khanacademy.org") ||
            h.contains("stackoverflow.com") || h.contains("github.com") ||
            h.contains("google.com") || h.contains("microsoft.com")) {
            return com.childsafety.os.policy.TrustLevel.HIGH
        }
        
        // SUSPICIOUS: Proxy, P2P, Shorteners, known bad TLDs
        if (proxyDomains.any { h.contains(it) } || 
            urlShortenerDomains.any { h.contains(it) } ||
            h.endsWith(".xyz") || h.endsWith(".top") || h.endsWith(".info")) {
            return com.childsafety.os.policy.TrustLevel.SUSPICIOUS
        }
        
        return com.childsafety.os.policy.TrustLevel.NEUTRAL
    }

    /**
     * Get user-friendly description for a block category
     */
    fun getCategoryDescription(category: BlockCategory): String {
        return when (category) {
            BlockCategory.ADULT -> "Adult/Explicit Content"
            BlockCategory.GAMBLING -> "Gambling Website"
            BlockCategory.DRUGS -> "Drug-Related Content"
            BlockCategory.VIOLENCE -> "Violent/Gore Content"
            BlockCategory.PROXY -> "Proxy/VPN Bypass Tool"
            BlockCategory.SOCIAL_MEDIA -> "Social Media (Age Restricted)"
            BlockCategory.DATING -> "Dating Website"
            BlockCategory.URL_SHORTENER -> "Potentially Unsafe Link"
            BlockCategory.EXPLICIT_TEXT -> "Explicit Text Content"
            BlockCategory.EXPLICIT_IMAGE -> "Explicit Image Detected"
            BlockCategory.CLOUD_BLOCKED -> "Blocked by Parent"
        }
    }
}
