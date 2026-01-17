package com.childsafety.os.vpn

import com.childsafety.os.policy.DomainPolicy as UnifiedDomainPolicy

/**
 * VPN-specific domain policy wrapper.
 * Delegates to the unified DomainPolicy for consistency.
 */
object DomainPolicy {

    /**
     * Returns true if the domain should be blocked.
     * Uses the same policy as Safe Browser for consistency.
     * 
     * @param rawDomain The domain to check
     * @param ageGroup The current age group (defaults to CHILD for max safety at VPN level)
     */
    fun shouldBlockDomain(
        rawDomain: String, 
        ageGroup: com.childsafety.os.policy.AgeGroup = com.childsafety.os.policy.AgeGroup.CHILD
    ): Boolean {
        val decision = UnifiedDomainPolicy.evaluate(rawDomain, ageGroup)
        return decision.blocked
    }
}
