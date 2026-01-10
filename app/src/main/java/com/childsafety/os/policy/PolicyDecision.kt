package com.childsafety.os.policy

data class PolicyDecision(
    val blocked: Boolean,
    val reason: PolicyReason? = null,
    val category: DomainPolicy.BlockCategory? = null
) {
    companion object {
        fun allow() = PolicyDecision(blocked = false)
        fun block(reason: PolicyReason) =
            PolicyDecision(blocked = true, reason = reason)
        fun block(reason: PolicyReason, category: DomainPolicy.BlockCategory) =
            PolicyDecision(blocked = true, reason = reason, category = category)
    }
}

