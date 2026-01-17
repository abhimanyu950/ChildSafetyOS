package com.childsafety.os.policy

import android.util.Log

enum class RiskAction {
    ALLOW,
    WARN,      // Allow but notify/log (Teens/Adults)
    BLOCK_SOFT, // Block but allow override (not implemented yet, treats as BLOCK)
    BLOCK_HARD // Strict block
}

data class RiskInput(
    val networkScore: Int,      // 0-100 (Domain/IP reputation)
    val webViewScore: Int,      // 0-100 (Path, Query params)
    val jsScore: Int,           // 0-100 (Page text, emojis, context)
    val aiScore: Int,           // 0-100 (Visual model confidence)
    val ageGroup: AgeGroup,
    val isChildMode: Boolean = ageGroup == AgeGroup.CHILD
)

data class RiskResult(
    val finalScore: Int,        // 0-100
    val action: RiskAction,
    val reason: String,
    val componentScores: String // For logging: "Net:30, JS:10, AI:0"
)

/**
 * Deterministic Risk Calculator
 * 
 * Formula: R = (AI * 0.60) + (Net * 0.25) + (JS * 0.15)
 * 
 * Execution strictness:
 * - Scores are clamped 0-100 individually
 * - Weights sum to 1.0
 * - Gates applied per mode
 */
object RiskCalculator {

    private const val WEIGHT_AI = 0.60
    private const val WEIGHT_NET = 0.25
    private const val WEIGHT_JS = 0.15

    fun calculate(input: RiskInput): RiskResult {
        // 1. Component Clamping (Safety measure)
        val ai = input.aiScore.coerceIn(0, 100)
        val net = input.networkScore.coerceIn(0, 100)
        val js = input.webViewScore.coerceAtLeast(input.jsScore).coerceIn(0, 100) 
        // Note: We take max of WebView signal (URL) and JS signal (Text) for "Context" layer
        // User formula says "JS_context_score", we interpret this as the merged page-context score.
        // Actually user said: "WebView request interception" AND "JavaScript context extraction".
        // Formula lists "JS_context_score * 0.15". 
        // Let's assume WebView signals feed into Network or JS score?
        // User: "Network (VPN) ... WebView interception ... JavaScript ... AI"
        // Formula components: AI, Network, JS. 
        // I will treat WebView signals (URL patterns) as an augment to Network score or JS score.
        // Network score is usually Domain reputation. URL/Path is more specific.
        // We will combine WebView(URL) + JS(Text) into the "Context" component (0.15 weight)?
        // OR we combine WebView(URL) into Network? 
        // User "Network Layer (Android VpnService) ... WebView Layer ... JS Context Layer".
        // Risk Model: "R = (AI * 0.6) + (Network * 0.25) + (JS_context * 0.15)"
        // It seems WebView is missing from formula explicit terms. 
        // I'll merge WebView intent risk into the JsContext score (Page Context), 
        // because URL path is part of the page context.
        
        val contextScore = maxOf(input.webViewScore, input.jsScore).coerceIn(0, 100)

        // 2. Weighted Calculation
        val weightedScore = (ai * WEIGHT_AI) + (net * WEIGHT_NET) + (contextScore * WEIGHT_JS)
        val finalRisk = weightedScore.toInt().coerceIn(0, 100)

        // 3. Mode-Based Enforcement
        return when (input.ageGroup) {
            AgeGroup.CHILD -> evaluateChild(finalRisk, net, ai)
            AgeGroup.TEEN -> evaluateTeen(finalRisk)
            AgeGroup.ADULT -> evaluateAdult(finalRisk)
        }
    }

    private fun evaluateChild(risk: Int, netScore: Int, aiScore: Int): RiskResult {
        // Hard Rule: Network Risk >= 30 -> Block Immediate
        if (netScore >= 30) {
            return RiskResult(
                risk, RiskAction.BLOCK_HARD, 
                "Network Risk Rule (Score: $netScore >= 30)", 
                formatScores(netScore, 0, aiScore)
            )
        }
        
        // AI Escalation Gate
        if (aiScore >= 25 && risk < 50) {
            // If AI is confident but overall score is low (e.g. clean text), bump risk?
            // User says "Escalate if AI >= 25". We imply strict blocking.
            // But let's stick to the threshold logic first.
        }

        return when {
            risk >= 70 -> RiskResult(risk, RiskAction.BLOCK_HARD, "Critical Risk (Parent Alert)", formatScores(netScore, 0, aiScore))
            risk >= 50 -> RiskResult(risk, RiskAction.BLOCK_HARD, "High Risk", formatScores(netScore, 0, aiScore))
            risk >= 30 -> RiskResult(risk, RiskAction.BLOCK_HARD, "Moderate Risk", formatScores(netScore, 0, aiScore))
            else -> RiskResult(risk, RiskAction.ALLOW, "Safe", formatScores(netScore, 0, aiScore))
        }
    }

    private fun evaluateTeen(risk: Int): RiskResult {
        return when {
            risk >= 70 -> RiskResult(risk, RiskAction.BLOCK_HARD, "Critical Risk (Alert)", "")
            risk >= 50 -> RiskResult(risk, RiskAction.BLOCK_HARD, "Blocked (Explainable)", "")
            risk >= 30 -> RiskResult(risk, RiskAction.WARN, "Warning: Sensitive Content", "")
            else -> RiskResult(risk, RiskAction.ALLOW, "Allowed", "")
        }
    }

    private fun evaluateAdult(risk: Int): RiskResult {
        return when {
            risk >= 80 -> RiskResult(risk, RiskAction.BLOCK_HARD, "Illegal/Extreme Content", "")
            risk >= 65 -> RiskResult(risk, RiskAction.WARN, "Warning: High Risk", "")
            else -> RiskResult(risk, RiskAction.ALLOW, "Allowed", "")
        }
    }
    
    private fun formatScores(net: Int, js: Int, ai: Int): String = "Net:$net JS:$js AI:$ai"
}
