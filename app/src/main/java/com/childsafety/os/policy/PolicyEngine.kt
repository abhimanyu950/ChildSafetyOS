package com.childsafety.os.policy

import android.content.Context
import com.childsafety.os.ai.TextRiskClassifier
import com.childsafety.os.ai.EmojiDetector

/**
 * Unified policy engine for all content filtering decisions.
 * 
 * PHILOSOPHY: Even adults benefit from protection against:
 * - Highly explicit pornographic content (addictive, triggering)
 * - Illegal content (extreme violence, CSAM)
 * - Scam/phishing sites
 * 
 * We apply progressively relaxed thresholds, not zero filtering.
 */
object PolicyEngine {

    /**
     * DOMAIN BLOCKING
     * Unified policy decision (VPN / Chrome / Safe Browser)
     * 
     * Adults: Only block ADULT category (porn sites, extreme content)
     * Teens: Block ADULT + GAMBLING + DRUGS
     * Children: Block all categories
     */
    fun shouldBlockDomain(
        host: String?,
        ageGroup: AgeGroup
    ): Boolean {

        val decision = DomainPolicy.evaluate(host)
        if (!decision.blocked) return false
        
        // Apply age-appropriate domain blocking
        return when (ageGroup) {
            AgeGroup.CHILD -> true // Block all categories
            
            AgeGroup.TEEN -> {
                // Block most categories except social media (with supervision)
                decision.category != DomainPolicy.BlockCategory.SOCIAL_MEDIA
            }
            
            AgeGroup.ADULT -> {
                // Adults: Only block explicit adult content and violence
                // Rationale: These sites are harmful regardless of age
                decision.category == DomainPolicy.BlockCategory.ADULT ||
                decision.category == DomainPolicy.BlockCategory.VIOLENCE
            }
        }
    }

    /**
     * TEXT BLOCKING
     * Rule-based + ML (Safe Browser only)
     * 
     * Uses age-appropriate thresholds from ThresholdProvider
     */
    fun shouldBlockText(
        context: Context,
        text: String,
        ageGroup: AgeGroup
    ): Boolean {

        if (text.isBlank() || text.length < 3) return false

        // All age groups now use text classification with appropriate thresholds
        return TextRiskClassifier
            .classify(context, text, ageGroup)
            .isRisky
    }

    /**
     * EMOJI BLOCKING
     * Deterministic scoring
     * 
     * Adults have very high threshold (0.95) but still blocked for explicit emoji spam
     */
    fun shouldBlockEmoji(
        text: String,
        ageGroup: AgeGroup
    ): Boolean {

        if (text.isBlank()) return false

        val score = EmojiDetector.score(text)

        return when (ageGroup) {
            AgeGroup.CHILD -> score >= 0.4f  // Very strict
            AgeGroup.TEEN  -> score >= 0.7f  // Moderate
            AgeGroup.ADULT -> score >= 0.95f // Only extreme emoji spam
        }
    }
}

