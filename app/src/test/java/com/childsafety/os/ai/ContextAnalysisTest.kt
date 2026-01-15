package com.childsafety.os.ai

import org.junit.Test
import org.junit.Assert.*

class ContextAnalysisTest {

    @Test
    fun testContextAnalysis() {
        val testCases = mapOf(
            // Safe: Gaming
            "kill the game boss" to false,
            "headshot enemy in fortnite" to false,
            
            // Safe: Shopping
            "buy amazon knife" to false,
            "cheap kitchen knife price" to false,
            "hunting gear store" to false,
            
            // Safe: Medical
            "breast cancer awareness" to false,
            "human anatomy body parts" to false,
            
            // Safe: Cooking
            "how to cook chicken breast" to false,
            "chop meat with knife" to false,
            
            // Safe: Hunting
            "duck hunting season gun" to false,

            // Risky: Violence
            "how to kill someone" to true,
            "murder plan" to true,
            "best way to commit suicide" to true,
            
            // Risky: Explicit
            "show me nudes" to true,
            "naked hot girl" to true,
            "porn video" to true
        )
        
        println("Running Context Analysis Tests...")
        var passed = 0
        
        val failures = mutableListOf<String>()
        
        testCases.forEach { (text, shouldBeRisky) ->
            val result = ContextTextAnalyzer.analyze(text)
            val correct = result.isRisky == shouldBeRisky
            
            if (!correct) {
                failures.add("FAIL: \"$text\" -> Got Risky=${result.isRisky} (Expected $shouldBeRisky) | Context=${result.detectedContext}")
            }
        }
        
        if (failures.isNotEmpty()) {
            fail("Context Analysis Failed:\n" + failures.joinToString("\n"))
        }
    }
}
