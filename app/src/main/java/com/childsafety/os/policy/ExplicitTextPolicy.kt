package com.childsafety.os.policy

/**
 * Comprehensive Explicit Text Policy with Multi-Language Support.
 * 
 * Handles:
 * - English explicit words
 * - Hindi/Hinglish slang (transliterated)
 * - Internet abbreviations/slang
 * - L33t speak variations
 * - Dangerous emojis
 * - Phonetic variations
 */
object ExplicitTextPolicy {

    // ==========================================
    // ENGLISH EXPLICIT WORDS
    // ==========================================
    private val englishExplicitWords = setOf(
        "sex", "porn", "nude", "xxx", "fuck", "boobs", "pussy", "dick",
        "naked", "strip", "horny", "orgasm", "blowjob", "handjob",
        "masturbate", "erotic", "slutty", "whore", "bitch", "cunt",
        "milf", "dildo", "vibrator", "fetish", "bondage", "hentai",
        "onlyfans", "pornhub", "xvideos", "xnxx", "redtube"
    )

    // ==========================================
    // HINDI / HINGLISH SLANG (TRANSLITERATED)
    // Common explicit terms in Hindi romanized script
    // ==========================================
    private val hindiSlang = setOf(
        // Explicit words (romanized Hindi)
        "chut", "lund", "gaand", "chudai", "randi", "madarchod", "bhenchod",
        "chutiya", "loda", "bhosdike", "bhadwa", "lavda", "tatte",
        "maal", "pataka", "item", "chikni", "ladkibaji",
        // Common slang abbreviations
        "mc", "bc", "bsdk", "bkl", "tmkc", "lmkc",
        // Variations
        "ch00t", "ch*t", "l*nd", "g@@nd", "bh0sdi"
    )

    // ==========================================
    // INTERNET SLANG / ABBREVIATIONS
    // Common texting and social media abbreviations
    // ==========================================
    private val internetSlang = setOf(
        // Explicit abbreviations
        "nsfw", "18+", "xxx", "milf", "pawg", "bbc", "bwc",
        "dtf", "fwb", "nudes", "sext", "hookup", "smash",
        // Dating/hookup slang
        "sugar daddy", "sugar baby", "escort", "call girl",
        // Drug-related
        "weed", "420", "maal", "charas", "ganja", "drugs",
        // Violence
        "k!ll", "death", "suicide", "attack"
    )

    // ==========================================
    // L33T SPEAK MAPPING
    // Number/symbol substitutions for letters
    // ==========================================
    private val l33tMap = mapOf(
        '0' to 'o', '1' to 'i', '2' to 'z', '3' to 'e',
        '4' to 'a', '5' to 's', '6' to 'g', '7' to 't',
        '8' to 'b', '9' to 'g', '@' to 'a', '$' to 's',
        '!' to 'i', '*' to 'a'
    )

    // ==========================================
    // DANGEROUS / SUGGESTIVE EMOJIS
    // ==========================================
    private val dangerousEmojis = setOf(
        // Explicit/suggestive
        "🍑", "🍆", "💦", "👅", "😈", "🔥", "😏", "🥵", "😜", "💋",
        "🍒", "🍌", "🥒", "🌶️", "💧", "🤤", "😍", "❤️‍🔥",
        // Violence
        "🔪", "💀", "☠️", "🔫", "💣", "🗡️",
        // Drug-related  
        "💉", "💊", "🚬", "🍁"
    )

    // ==========================================
    // PHONETIC VARIATIONS (sounds like explicit words)
    // ==========================================
    private val phoneticVariations = setOf(
        // Sounds like explicit words
        "phuck", "fuk", "fck", "phuk", "fook",
        "secks", "sexx", "s3x",
        "pr0n", "pron", "p0rn",
        "b00bs", "bewbs", "boobz",
        "dikk", "d1ck", "d!ck",
        "pussi", "pussie", "pu\$\$y",
        // Hindi phonetic
        "bhosdi", "bhosad", "bhosd"
    )

    // ==========================================
    // SPACING EVASION PATTERNS
    // Words with spaces/dots to evade filters
    // ==========================================
    private val spacingPatterns = listOf(
        "s.e.x", "p.o.r.n", "n.u.d.e",
        "s e x", "p o r n", "n u d e",
        "f u c k", "b o o b s"
    )

    /**
     * Main check function - comprehensive explicit content detection
     */
    fun containsExplicitContent(text: String): Boolean {
        val normalized = normalizeText(text)
        
        // Check all word lists
        if (checkWordList(normalized, englishExplicitWords)) return true
        if (checkWordList(normalized, hindiSlang)) return true
        if (checkWordList(normalized, internetSlang)) return true
        if (checkWordList(normalized, phoneticVariations)) return true
        
        // Check emojis (on original text, not normalized)
        if (dangerousEmojis.any { text.contains(it) }) return true
        
        // Check spacing evasion patterns
        if (spacingPatterns.any { normalized.contains(it.replace(".", "").replace(" ", "")) }) return true
        
        return false
    }
    
    /**
     * Get all matched explicit terms (for logging/debugging)
     */
    fun getMatchedTerms(text: String): List<String> {
        val normalized = normalizeText(text)
        val matches = mutableListOf<String>()
        
        matches.addAll(englishExplicitWords.filter { normalized.contains(it) })
        matches.addAll(hindiSlang.filter { normalized.contains(it) })
        matches.addAll(internetSlang.filter { normalized.contains(it) })
        matches.addAll(phoneticVariations.filter { normalized.contains(it) })
        matches.addAll(dangerousEmojis.filter { text.contains(it) })
        
        return matches
    }

    /**
     * Normalize text to catch l33t speak and obfuscation
     */
    private fun normalizeText(text: String): String {
        var normalized = text.lowercase()
        
        // Remove common separators used for evasion
        normalized = normalized.replace(Regex("[.\\-_*]+"), "")
        
        // Apply l33t speak normalization
        val sb = StringBuilder()
        for (char in normalized) {
            sb.append(l33tMap[char] ?: char)
        }
        
        return sb.toString()
    }

    /**
     * Check if normalized text contains any word from the list
     */
    private fun checkWordList(normalizedText: String, wordList: Set<String>): Boolean {
        return wordList.any { word -> 
            normalizedText.contains(word.lowercase())
        }
    }
    
    /**
     * Check if text is in a specific category
     */
    fun getCategory(text: String): String? {
        val normalized = normalizeText(text)
        
        if (englishExplicitWords.any { normalized.contains(it) }) return "EXPLICIT_ENGLISH"
        if (hindiSlang.any { normalized.contains(it) }) return "EXPLICIT_HINDI"
        if (internetSlang.any { normalized.contains(it) }) return "INTERNET_SLANG"
        if (phoneticVariations.any { normalized.contains(it) }) return "PHONETIC_EVASION"
        if (dangerousEmojis.any { text.contains(it) }) return "DANGEROUS_EMOJI"
        
        return null
    }
}
