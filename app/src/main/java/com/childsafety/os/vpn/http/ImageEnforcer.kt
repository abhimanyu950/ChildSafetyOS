package com.childsafety.os.vpn.http

import com.childsafety.os.policy.AgeGroup

/**
 * Pure decision engine.
 *
 * - NO Firebase
 * - NO logging
 * - NO Android Context
 *
 * Returns a deterministic block / allow decision.
 */
object ImageEnforcer {

    fun shouldBlock(
        scores: Map<String, Float>,
        ageGroup: AgeGroup
    ): Boolean {

        // Hackathon-safe static thresholds
        val threshold = when (ageGroup) {
            AgeGroup.CHILD -> 0.65f
            AgeGroup.TEEN  -> 0.80f
            AgeGroup.ADULT -> 0.95f
        }

        val porn = scores["porn"] ?: 0f
        val sexy = scores["sexy"] ?: 0f
        val hentai = scores["hentai"] ?: 0f

        // Conservative policy:
        // Block if ANY high-risk signal crosses threshold
        return porn >= threshold ||
                sexy >= threshold ||
                hentai >= threshold
    }
}
