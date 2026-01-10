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
     */
    fun shouldBlockDomain(rawDomain: String): Boolean {
        val decision = UnifiedDomainPolicy.evaluate(rawDomain)
        return decision.blocked
    }
}
