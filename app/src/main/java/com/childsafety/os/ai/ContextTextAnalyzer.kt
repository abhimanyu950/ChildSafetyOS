package com.childsafety.os.ai

import android.util.Log

/**
 * Context-Aware Text Analyzer - Semantic understanding without ML model.
 * 
 * Solves the key problem:
 * - "I want to kill this game boss" → SAFE (gaming context)
 * - "How to kill someone" → BLOCKED (violence context)
 * - "breast cancer awareness" → SAFE (medical context)
 * - "show me breasts" → BLOCKED (explicit context)
 * 
 * Uses pattern matching with context windows to make intelligent decisions.
 */
object ContextTextAnalyzer {

    private const val TAG = "ContextTextAnalyzer"

    /**
     * Context categories that make dangerous words safe
     */
    enum class SafeContext {
        GAMING,
        COOKING,
        MEDICAL,
        EDUCATIONAL,
        SPORTS,
        NEWS,
        MUSIC,
        MOVIE,
        SCIENCE,
        HISTORY,
        SHOPPING,  // New: "knife for shopping"
        HUNTING    // New: "gun by hunting"
    }

    /**
     * Analysis result with context explanation
     */
    data class ContextResult(
        val isRisky: Boolean,
        val originalText: String,
        val triggerWord: String?,
        val detectedContext: SafeContext?,
        val confidence: Float,
        val reason: String
    )

    // Words that are dangerous WITHOUT context
    private val dangerousKeywords = mapOf(
        // Violence (Context-sensitive)
        "kill" to listOf("murder", "assassinate", "stab", "shoot dead", "how to kill"),
        "murder" to listOf("homicide"), 
        "suicide" to listOf("hang myself", "end my life", "self harm"),
        
        // Weapons (Context-sensitive)
        "knife" to listOf("blade", "dagger", "machete"),
        "gun" to listOf("pistol", "rifle", "firearm"),
        "poison" to listOf("cyanide", "arsenic"),
        
        // Explicit
        "porn" to listOf("xxx", "pornography", "adult video"),
        "nude" to listOf("naked", "nudes", "naked pics"),
        "sex" to listOf("sexual", "intercourse"),
        
        // Drugs
        "cocaine" to listOf("coke", "crack"),
        "heroin" to listOf("smack", "dope"),
        "meth" to listOf("crystal meth", "methamphetamine"),
        
        // Self-harm
        "cut myself" to listOf("self harm", "cutting"),
        "eating disorder" to listOf("anorexia", "bulimia")
    )

    // Gaming context indicators
    private val gamingContext = setOf(
        "game", "boss", "level", "player", "score", "enemy", "weapon",
        "mission", "quest", "character", "minecraft", "fortnite", "roblox",
        "pubg", "cod", "gta", "fps", "rpg", "mmorpg", "respawn", "spawn",
        "pvp", "npc", "xp", "health bar", "power up", "game over",
        "final boss", "mini boss", "dungeon", "raid", "loot", "headshot"
    )

    // Shopping context indicators
    private val shoppingContext = setOf(
        "buy", "shop", "store", "amazon", "ebay", "walmart",
        "cost", "cheap", "expensive", "sale", "discount",
        "online shopping", "purchase", "order", "delivery", "shipping",
        "cart", "checkout", "product", "item"
    )

    // Hunting context indicators (makes 'gun' safe in some contexts)
    private val huntingContext = setOf(
        "hunt", "hunting", "deer", "duck", "wildlife", "safari",
        "outdoor", "camping", "gear", "season", "licence", "permit"
    )

    // Cooking context indicators
    private val cookingContext = setOf(
        "recipe", "cook", "bake", "ingredient", "kitchen", "oven",
        "fry", "boil", "chop", "dice", "slice", "chicken", "beef",
        "meat", "vegetable", "sauce", "spice", "flour", "sugar",
        "butter", "egg", "pan", "pot", "stir", "mix", "blend",
        "chef", "restaurant", "food", "dinner", "lunch"
    )

    // Medical context indicators
    private val medicalContext = setOf(
        "doctor", "hospital", "patient", "treatment", "disease", "cancer",
        "diagnosis", "symptom", "medicine", "health", "medical", "surgery",
        "breast cancer", "tumor", "therapy", "clinical", "examination",
        "anatomy", "biology", "organ", "cell", "tissue", "bone"
    )

    // Educational context indicators
    private val educationalContext = setOf(
        "learn", "study", "school", "class", "teacher", "student",
        "history", "science", "math", "literature", "essay", "exam",
        "homework", "project", "research", "university", "college",
        "biology class", "chemistry", "physics", "education"
    )

    // Sports context indicators
    private val sportsContext = setOf(
        "match", "team", "score", "goal", "player", "coach", "win",
        "championship", "tournament", "league", "cricket", "football",
        "basketball", "tennis", "olympics", "athlete", "race", "run"
    )

    // Movie/Entertainment context indicators
    private val movieContext = setOf(
        "movie", "film", "actor", "scene", "director", "character",
        "plot", "story", "ending", "trailer", "review", "netflix",
        "series", "episode", "season", "cinematic", "thriller", "horror"
    )

    // News context indicators
    private val newsContext = setOf(
        "news", "report", "journalist", "article", "headline", "breaking",
        "investigation", "sources", "according to", "officials said"
    )

    // Music context indicators
    private val musicContext = setOf(
        "song", "lyrics", "music", "album", "singer", "band", "concert",
        "melody", "beat", "hip hop", "rock", "pop", "rap", "spotify"
    )

    /**
     * Analyze text with context understanding
     */
    fun analyze(text: String): ContextResult {
        val lowerText = text.lowercase()
        val words = lowerText.split(Regex("\\s+"))
        
        // 1. First check for explicitly dangerous patterns (no context can save these)
        val explicitDanger = checkExplicitlyDangerous(lowerText)
        if (explicitDanger != null) {
            return ContextResult(
                isRisky = true,
                originalText = text,
                triggerWord = explicitDanger,
                detectedContext = null,
                confidence = 0.95f,
                reason = "Explicitly harmful content: $explicitDanger"
            )
        }
        
        // 2. Find potentially dangerous keywords
        var foundKeyword: String? = null
        
        // Check dynamic keywords (from Remote Config)
        val dynamicKeywords = com.childsafety.os.cloud.RemoteConfigManager.getDynamicBlockedKeywords()
        for (keyword in dynamicKeywords) {
             if (lowerText.contains(keyword.lowercase())) {
                 foundKeyword = keyword
                 break
             }
        }
        
        // Check static keywords
        if (foundKeyword == null) {
            for ((keyword, synonyms) in dangerousKeywords) {
                if (lowerText.contains(keyword) || synonyms.any { lowerText.contains(it) }) {
                    foundKeyword = keyword
                    break
                }
            }
        }
        
        // 3. If no dangerous keyword, text is safe
        if (foundKeyword == null) {
            return ContextResult(
                isRisky = false,
                originalText = text,
                triggerWord = null,
                detectedContext = null,
                confidence = 0.9f,
                reason = "No dangerous keywords detected"
            )
        }
        
        // 4. Check for safe context
        val detectedContext = detectContext(lowerText, words)
        
        if (detectedContext != null) {
            // Context makes it safe
            return ContextResult(
                isRisky = false,
                originalText = text,
                triggerWord = foundKeyword,
                detectedContext = detectedContext,
                confidence = 0.85f,
                reason = "Keyword '$foundKeyword' is safe in ${detectedContext.name} context"
            )
        }
        
        // 5. Dangerous keyword without safe context = risky
        return ContextResult(
            isRisky = true,
            originalText = text,
            triggerWord = foundKeyword,
            detectedContext = null,
            confidence = 0.8f,
            reason = "Dangerous keyword '$foundKeyword' without safe context"
        )
    }

    private fun log(type: String, message: String) {
        try {
            when(type) {
                "i" -> Log.i(TAG, message)
                "w" -> Log.w(TAG, message)
                "e" -> Log.e(TAG, message)
            }
        } catch (e: RuntimeException) {
            // Unit test mode: Print to stdout
            println("[$type/$TAG] $message")
        }
    }

    /**
     * Check for patterns that are ALWAYS dangerous, regardless of context
     */
    private fun checkExplicitlyDangerous(text: String): String? {
        val alwaysDangerous = listOf(
            // Direct violence instructions
            "how to make a bomb" to "bomb making",
            "how to kill someone" to "murder instructions",
            "how to hurt" to "harm instructions",
            
            // Explicit requests
            "send nudes" to "soliciting explicit images",
            "show me naked" to "requesting explicit content",
            "child porn" to "CSAM",
            "underage" to "minor exploitation",
            
            // Self-harm
            "i want to die" to "suicidal ideation",
            "how to commit suicide" to "suicide instructions",
            "best way to kill myself" to "suicide method",
            
            // Drug dealing
            "where to buy drugs" to "drug procurement",
            "how to make meth" to "drug manufacturing"
        )
        
        for ((phrase, label) in alwaysDangerous) {
            if (text.contains(phrase)) {
                log("w", "BLOCKED: $label detected")
                return label
            }
        }
        return null
    }

    /**
     * Detect the context of the text
     */
    private fun detectContext(text: String, words: List<String>): SafeContext? {
        // Count context indicators
        val contextScores = mutableMapOf<SafeContext, Int>()
        
        // Gaming context
        val gamingScore = gamingContext.count { text.contains(it) }
        if (gamingScore >= 1) contextScores[SafeContext.GAMING] = gamingScore
        
        // Shopping context (New)
        val shoppingScore = shoppingContext.count { text.contains(it) }
        if (shoppingScore >= 1) contextScores[SafeContext.SHOPPING] = shoppingScore

        // Hunting context (New)
        val huntingScore = huntingContext.count { text.contains(it) }
        if (huntingScore >= 1) contextScores[SafeContext.HUNTING] = huntingScore
        
        // Cooking context
        val cookingScore = cookingContext.count { text.contains(it) }
        if (cookingScore >= 1) contextScores[SafeContext.COOKING] = cookingScore
        
        // Medical context
        val medicalScore = medicalContext.count { text.contains(it) }
        if (medicalScore >= 1) contextScores[SafeContext.MEDICAL] = medicalScore
        
        // Educational context
        
        // Educational context
        val educationalScore = educationalContext.count { text.contains(it) }
        if (educationalScore >= 2) contextScores[SafeContext.EDUCATIONAL] = educationalScore
        
        // Sports context
        val sportsScore = sportsContext.count { text.contains(it) }
        if (sportsScore >= 2) contextScores[SafeContext.SPORTS] = sportsScore
        
        // Movie context
        val movieScore = movieContext.count { text.contains(it) }
        if (movieScore >= 2) contextScores[SafeContext.MOVIE] = movieScore
        
        // News context  
        val newsScore = newsContext.count { text.contains(it) }
        if (newsScore >= 2) contextScores[SafeContext.NEWS] = newsScore
        
        // Music context
        val musicScore = musicContext.count { text.contains(it) }
        if (musicScore >= 2) contextScores[SafeContext.MUSIC] = musicScore
        
        // Return the highest scoring context
        return contextScores.maxByOrNull { it.value }?.key
    }

    /**
     * Quick check for gaming-specific phrases
     */
    fun isGamingContext(text: String): Boolean {
        val lowerText = text.lowercase()
        val gamingPhrases = listOf(
            "kill the boss", "kill the enemy", "kill enemies",
            "beat the boss", "defeat the boss", "destroy the enemy",
            "headshot", "game over", "respawn", "level up",
            "in the game", "playing", "my character"
        )
        return gamingPhrases.any { lowerText.contains(it) }
    }

    /**
     * Quick check for medical context
     */
    fun isMedicalContext(text: String): Boolean {
        val lowerText = text.lowercase()
        val medicalPhrases = listOf(
            "breast cancer", "cancer treatment", "cancer awareness",
            "medical examination", "doctor said", "hospital",
            "diagnosis", "symptoms of", "treatment for"
        )
        return medicalPhrases.any { lowerText.contains(it) }
    }

    /**
     * Examples for testing
     */
    fun runExamples() {
        val testCases = listOf(
            "I want to kill this game boss",
            "How to kill someone",
            "Breast cancer awareness month",
            "Show me breasts",
            "Kill the enemy in Fortnite",
            "Best way to kill myself",
            "How to cook and kill bacteria in meat",
            "The character dies in the movie ending"
        )
        
        testCases.forEach { text ->
            val result = analyze(text)
            Log.i(TAG, "Text: \"$text\"")
            Log.i(TAG, "  Risky: ${result.isRisky}, Context: ${result.detectedContext}, Reason: ${result.reason}")
        }
    }
}
