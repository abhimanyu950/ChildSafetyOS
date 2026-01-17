package com.childsafety.os.ai

/**
 * Comprehensive Keyword Repository for Content Filtering
 * 
 * Categories:
 * - Adult/Explicit content
 * - Violence and weapons
 * - Drugs and substances
 * - Self-harm and suicide
 * - Gambling
 * - Hate speech indicators
 * 
 * Note: These keywords are used for PROTECTION, not censorship.
 * Context-aware analysis in TextRiskClassifier handles false positives.
 */
object KeywordRepository {

    // ========== HIGH-RISK: ADULT/EXPLICIT CONTENT ==========
    private val adultKeywords = listOf(
        // Core explicit terms
        "porn", "porno", "pornography", "pornographic",
        "xxx", "xxxx", "xxxvideos", "xvideos", "xnxx", "xhamster",
        "sex", "sexual", "sexually", "sexting",
        "nude", "nudes", "nudity", "naked", "nakedness",
        "hentai", "ecchi", "yaoi", "yuri", "futanari",
        
        // Body parts (explicit context)
        "boobs", "boobies", "tits", "titties", "breasts",
        "pussy", "vagina", "penis", "dick", "cock",
        "ass", "butt", "buttocks", "anus", "anal",
        "genitals", "genital", "clit", "clitoris",
        
        // Sexual acts
        "intercourse", "blowjob", "handjob", "masturbat",
        "orgasm", "ejaculat", "cumshot", "creampie",
        "threesome", "gangbang", "orgy", "fetish",
        "bdsm", "bondage", "domination", "submission",
        "milf", "dilf", "gilf", "teen porn",
        
        // Explicit sites/platforms
        "pornhub", "xvideos", "redtube", "youporn",
        "brazzers", "bangbros", "onlyfans", "fansly",
        "chaturbate", "livejasmin", "stripchat",
        "rule34", "e621", "gelbooru", "danbooru",
        
        // Slang and variations
        "fap", "fapping", "wank", "wanking", "jerk off",
        "horny", "slutty", "whore", "hooker", "escort",
        "stripper", "strip club", "lap dance",
        "hot girl pic", "hotgirlpic", "sexy pic", "naughty",
        "erotic", "erotica", "sensual", "seduction"
    )

    // ========== HIGH-RISK: VIOLENCE ==========
    private val violenceKeywords = listOf(
        // Direct violence
        "kill", "killing", "murder", "murderer", "homicide",
        "assassinate", "assassination", "execute", "execution",
        "slaughter", "massacre", "genocide", "bloodbath",
        
        // Weapons
        "stab", "stabbing", "shoot", "shooting", "gunshot",
        "decapitate", "behead", "dismember", "mutilate",
        "strangle", "choke", "suffocate", "drown",
        
        // Terrorism
        "terror", "terrorist", "terrorism", "bomb", "bombing",
        "explode", "explosion", "detonate", "ied",
        "mass shooting", "school shooting", "jihad",
        
        // Gore
        "gore", "gory", "gruesome", "mutilation",
        "torture", "torment", "brutality", "brutal"
    )

    // ========== HIGH-RISK: SELF-HARM/SUICIDE ==========
    private val selfHarmKeywords = listOf(
        "suicide", "suicidal", "kill myself", "end my life",
        "hang myself", "slit wrists", "overdose",
        "self harm", "self-harm", "selfharm", "cutting",
        "cut myself", "hurt myself", "i want to die",
        "best way to die", "painless death", "how to commit",
        "suicide method", "suicide note", "goodbye letter",
        "anorexia", "bulimia", "pro-ana", "pro-mia", "thinspo"
    )

    // ========== HIGH-RISK: DRUGS ==========
    private val drugKeywords = listOf(
        // Hard drugs
        "cocaine", "heroin", "methamphetamine", "meth",
        "crack", "fentanyl", "opioid", "opiate",
        "lsd", "acid trip", "shrooms", "psilocybin",
        "ecstasy", "mdma", "molly", "ketamine",
        
        // Drug use
        "drug dealer", "drug deal", "buy drugs",
        "how to get high", "get stoned", "get wasted",
        "inject drugs", "snort cocaine", "smoke crack",
        
        // Drug paraphernalia
        "bong", "pipe", "syringe", "needle exchange"
    )

    // ========== HIGH-RISK: CSAM INDICATORS ==========
    // These are ALWAYS blocked, no context can override
    private val csamKeywords = listOf(
        "child porn", "cp", "pedo", "pedophile", "paedophile",
        "underage", "minor porn", "loli", "lolicon", "shotacon",
        "jailbait", "preteen", "kiddie porn"
    )

    // ========== MEDIUM-RISK: GAMBLING ==========
    private val gamblingKeywords = listOf(
        "gambling", "gamble", "casino", "slot machine",
        "poker", "blackjack", "roulette", "sports betting",
        "bet365", "betway", "draftkings", "fanduel",
        "online betting", "place a bet", "odds", "bookmaker"
    )

    // ========== MEDIUM-RISK: DATING/ROMANCE ==========
    private val datingKeywords = listOf(
        "dating", "hookup", "hook up", "one night stand",
        "friends with benefits", "fwb", "booty call",
        "tinder", "bumble", "grindr", "hinge",
        "sugar daddy", "sugar baby", "seeking arrangement"
    )

    // ========== MEDIUM-RISK: CONTEXT DEPENDENT ==========
    private val contextDependentKeywords = listOf(
        "kiss", "kissing", "romance", "romantic",
        "fight", "fighting", "blood", "bloody",
        "gun", "knife", "weapon", "rifle", "pistol",
        "hate", "racist", "sexist", "bigot"
    )

    // ========== COMBINED LISTS ==========
    
    val highRisk: List<String> = (
        adultKeywords + 
        violenceKeywords + 
        selfHarmKeywords + 
        drugKeywords +
        csamKeywords
    ).map { it.lowercase() }.distinct()

    val mediumRisk: List<String> = (
        gamblingKeywords + 
        datingKeywords + 
        contextDependentKeywords
    ).map { it.lowercase() }.distinct()

    // CSAM keywords are ALWAYS blocked, no exceptions
    val alwaysBlock: List<String> = csamKeywords.map { it.lowercase() }

    /**
     * Converts text into a fixed-length numeric vector
     * suitable for TensorFlow Lite input.
     *
     * Vector format:
     * [ highRiskCount, mediumRiskCount, normalizedLength ]
     *
     * Shape: FloatArray(3)
     */
    fun vectorize(text: String): FloatArray {

        val normalized = text.lowercase()

        val highCount =
            highRisk.count { normalized.contains(it) }.toFloat()

        val mediumCount =
            mediumRisk.count { normalized.contains(it) }.toFloat()

        val lengthNorm =
            (normalized.length.coerceAtMost(500) / 500f)

        return floatArrayOf(
            highCount,
            mediumCount,
            lengthNorm
        )
    }

    /**
     * Check if text contains any always-block keywords (CSAM, etc.)
     * These bypass all context analysis and are ALWAYS blocked.
     */
    fun containsAlwaysBlock(text: String): Boolean {
        val normalized = text.lowercase()
        return alwaysBlock.any { normalized.contains(it) }
    }

    /**
     * Get the risk score for a text (0.0 to 1.0)
     */
    fun getRiskScore(text: String): Float {
        val normalized = text.lowercase()
        
        // CSAM = instant max risk
        if (alwaysBlock.any { normalized.contains(it) }) {
            return 1.0f
        }
        
        val highCount = highRisk.count { normalized.contains(it) }
        val mediumCount = mediumRisk.count { normalized.contains(it) }
        
        // Score: Each high-risk = 0.2, medium = 0.1, capped at 1.0
        return (highCount * 0.2f + mediumCount * 0.1f).coerceIn(0f, 1f)
    }
}
