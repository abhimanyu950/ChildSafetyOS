package com.childsafety.os.cloud

import android.util.Log
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import org.json.JSONArray

object RemoteConfigManager {

    private const val TAG = "RemoteConfigManager"
    
    // Keys matched in Firebase Console
    private const val KEY_BLOCKED_KEYWORDS = "blocked_keywords"
    private const val KEY_TRUSTED_DOMAINS = "trusted_image_domains"
    
    private var initialized = false

    fun init() {
        if (initialized) return
        
        try {
            val config = Firebase.remoteConfig
            val configSettings = remoteConfigSettings {
                minimumFetchIntervalInSeconds = 3600 // 1 hour cache
            }
            config.setConfigSettingsAsync(configSettings)
            
            // Set in-app defaults (Fail-safe)
            val defaults = mapOf(
                KEY_BLOCKED_KEYWORDS to "[]",
                KEY_TRUSTED_DOMAINS to "[]"
            )
            config.setDefaultsAsync(defaults)
            
            // Fetch and Activate
            config.fetchAndActivate()
                .addOnSuccessListener {
                    Log.d(TAG, "Remote Config fetched and activated")
                }
                .addOnFailureListener {
                    Log.e(TAG, "Remote Config fetch failed", it)
                }
                
            initialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init Remote Config", e)
        }
    }
    
    /**
     * Get dynamic blocked keywords combined with local ones? 
     * Or just the dynamic list to be ADDED to local.
     */
    fun getDynamicBlockedKeywords(): List<String> {
        return try {
            val json = Firebase.remoteConfig.getString(KEY_BLOCKED_KEYWORDS)
            parseJsonStringArray(json)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun getDynamicTrustedDomains(): List<String> {
        return try {
            val json = Firebase.remoteConfig.getString(KEY_TRUSTED_DOMAINS)
            parseJsonStringArray(json)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun parseJsonStringArray(json: String): List<String> {
        if (json.isBlank() || json == "[]") return emptyList()
        
        val list = mutableListOf<String>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON array: $json", e)
        }
        return list
    }
}
