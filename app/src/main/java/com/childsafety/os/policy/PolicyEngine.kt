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

    // Lazy initialization of the Antigravity Engine
    private var antigravityKernel: AntigravityRiskEngine? = null

    /**
     * UNIFIED DECISION PIPELINE
     * Returns a detailed RiskResult for the Safe Browser.
     * 
     * Pipeline: Network -> WebView/Url -> JS/Text -> AI (Optional)
     */
    suspend fun evaluatePageRisk(
        context: Context,
        url: String,
        pageText: String = "",
        aiScore: Int = 0,
        ageGroup: AgeGroup
    ): RiskResult {
        
        // Initialize Kernel if needed (using Application context to avoid leaks)
        if (antigravityKernel == null) {
            antigravityKernel = AntigravityRiskEngine(context.applicationContext)
        }

        // 1. Network Layer Signal
        val host = android.net.Uri.parse(url).host
        val netScore = DomainPolicy.getDomainRiskScore(host)
        val trustLevel = DomainPolicy.getTrustLevel(host)

        // 2. WebView/URL Layer Signal
        val urlLower = url.lowercase()
        val isSearch = urlLower.contains("search") || urlLower.contains("query=")
        val webViewScore = when {
            urlLower.contains("porn") || urlLower.contains("sex") -> 70
            urlLower.contains("xxx") -> 60
            isSearch -> 10 // Monitor queries
            else -> 0
        }

        // 3. JS/Text Layer Signal
        val textResult = TextRiskClassifier.classify(context, pageText.take(2000), ageGroup)
        val textScore = (textResult.confidence * 100).toInt()
        val emojiScore = if (shouldBlockEmoji(pageText, ageGroup)) 50 else 0
        val finalJsScore = maxOf(textScore, emojiScore).toFloat()

        // 4. Construct Risk Signals
        val signals = RiskSignals(
            networkScore = netScore.toFloat(),
            jsScore = finalJsScore,
            trust = trustLevel
        )

        // 5. Compute Risk via Antigravity Kernel (suspend function)
        val kernelRisk = antigravityKernel!!.computeRisk(null, signals, false, ageGroup).toInt()
        
        // 6. Text Safety Overlay (Consistency Fix)
        // The Kernel weights JS low (15-20%) for hybrid scoring.
        // But if the specialized Text Classifier says it's risky (e.g. erotica), we MUST enforce it.
        val finalRisk = if (textResult.isRisky || textScore > 85) {
             maxOf(kernelRisk, 80) // Force critical risk for text violations
        } else {
             kernelRisk
        }
        
        // 7. Action Interpretation
        val action = determineAction(finalRisk, netScore, ageGroup)
        val reason = "Risk Score: $finalRisk (Net:$netScore JS:${finalJsScore.toInt()} AI:$aiScore)"

        val input = RiskInput(
            networkScore = netScore,
            webViewScore = webViewScore,
            jsScore = finalJsScore.toInt(),
            aiScore = aiScore,
            ageGroup = ageGroup
        )
        
        val result = RiskResult(
            finalScore = finalRisk,
            action = action,
            reason = reason,
            componentScores = "Net:$netScore JS:$finalJsScore AI:$aiScore Trust:${trustLevel.name}"
        )

        // 7. Mandatory Logging
        RiskLogger.logDecision(url, input, result)

        return result
    }

    private fun determineAction(risk: Int, netScore: Int, ageGroup: AgeGroup): RiskAction {
        return when (ageGroup) {
            AgeGroup.CHILD -> {
                if (netScore >= 30) return RiskAction.BLOCK_HARD
                if (risk >= 30) RiskAction.BLOCK_HARD else RiskAction.ALLOW
            }
            AgeGroup.TEEN -> {
                when {
                    risk >= 50 -> RiskAction.BLOCK_HARD
                    risk >= 30 -> RiskAction.WARN
                    else -> RiskAction.ALLOW
                }
            }
            AgeGroup.ADULT -> {
                when {
                    risk >= 80 -> RiskAction.BLOCK_HARD // Illegal only
                    risk >= 65 -> RiskAction.WARN
                    else -> RiskAction.ALLOW
                }
            }
        }
    }

    /**
     * DOMAIN BLOCKING (VPN Wrapper)
     */
    fun shouldBlockDomain(
        host: String?,
        ageGroup: AgeGroup
    ): Boolean {
        if (host == null) return false
        
        // Lightweight check: Get scores and determine action immediately
        // For network-only checks, we can bypass the full async kernel if we just need the Hard Rules
        val netScore = DomainPolicy.getDomainRiskScore(host)
        
        // Hard Rules (Fast Path) - duplicate of determineAction logic for speed/sync
        if (ageGroup == AgeGroup.CHILD && netScore >= 30) return true
        
        // For weighted check, we technically should run the kernel, 
        // but for Domain Blocking, the "Hard Rule" is usually what matters.
        // IF we want full kernel fidelity:
        /*
        val trust = DomainPolicy.getTrustLevel(host)
        val signals = RiskSignals(netScore.toFloat(), 0f, trust)
        val risk = runBlocking { antigravityKernel?.computeRisk(null, signals, false) ?: 0f }
        return determineAction(risk.toInt(), netScore, ageGroup) == RiskAction.BLOCK_HARD
        */
        
        return false // If not caught by Hard Rule, allow domain (content filter will catch text/images)
    }

    /**
     * TEXT BLOCKING (Legacy Wrapper)
     */
    fun shouldBlockText(
        context: Context,
        text: String,
        ageGroup: AgeGroup
    ): Boolean {
        if (text.isBlank()) return false

        // Quick check using new pipeline via runBlocking
        return kotlinx.coroutines.runBlocking {
             val result = evaluatePageRisk(
                context = context,
                url = "http://internal/text-check", 
                pageText = text,
                ageGroup = ageGroup
            )
            result.action == RiskAction.BLOCK_HARD
        }
    }

    /**
     * EMOJI BLOCKING
     * Kept for specific emoji logic
     */
    fun shouldBlockEmoji(
        text: String,
        ageGroup: AgeGroup
    ): Boolean {
        if (text.isBlank()) return false
        val score = EmojiDetector.score(text)
        return when (ageGroup) {
            AgeGroup.CHILD -> score >= 0.4f
            AgeGroup.TEEN  -> score >= 0.7f
            AgeGroup.ADULT -> score >= 0.95f
        }
    }
}

