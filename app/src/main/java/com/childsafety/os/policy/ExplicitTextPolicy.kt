package com.childsafety.os.policy

object ExplicitTextPolicy {

    private val bannedWords = setOf(
        "sex", "porn", "nude", "xxx", "fuck", "boobs", "pussy", "dick"
    )

    private val bannedEmojis = setOf(
        "🍑", "🍆", "💦", "👅", "😈", "🔥"
    )

    fun containsExplicitContent(text: String): Boolean {
        val normalized = text.lowercase()

        if (bannedWords.any { normalized.contains(it) }) {
            return true
        }

        if (bannedEmojis.any { text.contains(it) }) {
            return true
        }

        return false
    }
}
