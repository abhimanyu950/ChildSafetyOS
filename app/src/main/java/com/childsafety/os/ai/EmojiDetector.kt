package com.childsafety.os.ai

object EmojiDetector {

    /**
     * Emoji → risk weight
     * Weights are conservative and explainable.
     * ML is intentionally NOT used here.
     */
    private val emojiRiskMap = mapOf(
        "🍑" to 0.6f,
        "🍆" to 0.7f,
        "🍌" to 0.6f,
        "🍒" to 0.55f,
        "🍓" to 0.45f,
        "💦" to 0.65f,
        "👅" to 0.55f,
        "👄" to 0.5f,
        "🫦" to 0.65f,
        "😏" to 0.45f,
        "😈" to 0.45f,
        "🥵" to 0.5f,
        "🥴" to 0.4f,
        "🤤" to 0.4f,
        "🔥" to 0.4f,
        "💋" to 0.35f,
        "❤️‍🔥" to 0.45f,
        "💞" to 0.3f,
        "💕" to 0.25f,

        // Explicit / Adult-only
        "🔞" to 0.9f,
        "🚫🔞" to 0.95f,
        "❌🔞" to 0.95f,

        // Fetish / Body-focused (context-heavy)
        "🦶" to 0.5f,
        "👙" to 0.45f,
        "🩲" to 0.45f,
        "🩱" to 0.4f,
        "🧴" to 0.35f,

        // Violence / Gore
        "🔪" to 0.85f,
        "💣" to 0.9f,
        "🩸" to 0.75f,
        "🧨" to 0.85f,
        "⚔️" to 0.7f,
        "🔫" to 0.9f,
        "☠️" to 0.8f,
        "💀" to 0.7f,

        // Drugs / Intoxication
        "🍺" to 0.4f,
        "🍻" to 0.45f,
        "🥂" to 0.35f,
        "🍷" to 0.35f,
        "💊" to 0.5f,
        "🚬" to 0.6f
       
    )


    /**
     * Returns max emoji risk score found in text.
     * 0.0f means no risky emoji detected.
     */
    fun score(text: String): Float {
        if (text.isBlank()) return 0.0f

        return text
            .mapNotNull { emojiRiskMap[it.toString()] }
            .maxOrNull() ?: 0.0f
    }

    /**
     * Returns list of risky emojis found (for explainability / logs).
     */
    fun extractRiskyEmojis(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        return emojiRiskMap.keys.filter { text.contains(it) }
    }

    /**
     * Backward-compatible helper.
     * Keeps existing code working without changes.
     */
    fun containsRiskyEmoji(text: String): Boolean {
        return score(text) >= 0.5f
    }
}
