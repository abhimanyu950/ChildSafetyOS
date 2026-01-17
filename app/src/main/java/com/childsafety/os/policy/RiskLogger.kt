package com.childsafety.os.policy

import android.util.Log
import com.childsafety.os.cloud.EventUploader
import com.childsafety.os.ChildSafetyApp
import org.json.JSONObject

object RiskLogger {

    private const val TAG = "RiskEngine"

    fun logDecision(
        url: String,
        input: RiskInput,
        result: RiskResult
    ) {
        val json = JSONObject()
        json.put("finalRisk", result.finalScore)
        json.put("action", result.action.name)
        json.put("reason", result.reason)
        json.put("userMode", input.ageGroup.name)
        
        val components = JSONObject()
        components.put("aiScore", input.aiScore)
        components.put("networkScore", input.networkScore)
        components.put("jsScore", input.jsScore)
        components.put("webViewScore", input.webViewScore)
        json.put("components", components)
        
        json.put("timestamp", System.currentTimeMillis())
        json.put("url", url)

        // Log to Android Logcat
        Log.i(TAG, "DECISION [${result.action}] R=${result.finalScore} | $url | ${result.componentScores}")
        
        // Log to Cloud/Analytics
        if (result.finalScore >= 30 || result.action != RiskAction.ALLOW) {
             EventUploader.logRiskEvent(json.toString())
        }
    }
}
