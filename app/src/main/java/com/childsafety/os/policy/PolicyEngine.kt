package com.childsafety.os.policy

import android.content.Context
import com.childsafety.os.ai.TextRiskClassifier
import com.childsafety.os.ai.EmojiDetector

object PolicyEngine {

    /**
     * DOMAIN BLOCKING
     * Unified policy decision (VPN / Chrome / Safe Browser)
     */
    fun shouldBlockDomain(
        host: String?,
        ageGroup: AgeGroup
    ): Boolean {

        // Adults are never domain-blocked
        if (ageGroup == AgeGroup.ADULT) return false

        val decision = DomainPolicy.evaluate(host)
        return decision.blocked
    }

    /**
     * TEXT BLOCKING
     * Rule-based + ML (Safe Browser only)
     */
    fun shouldBlockText(
        context: Context,
        text: String,
        ageGroup: AgeGroup
    ): Boolean {

        if (ageGroup == AgeGroup.ADULT) return false
        if (text.isBlank() || text.length < 3) return false

        return TextRiskClassifier
            .classify(context, text, ageGroup)
            .isRisky
    }

    /**
     * EMOJI BLOCKING
     * Deterministic scoring
     */
    fun shouldBlockEmoji(
        text: String,
        ageGroup: AgeGroup
    ): Boolean {

        if (text.isBlank()) return false

        val score = EmojiDetector.score(text)

        return when (ageGroup) {
            AgeGroup.CHILD -> score >= 0.5f
            AgeGroup.TEEN  -> score >= 0.8f
            AgeGroup.ADULT -> false
        }
    }
}
