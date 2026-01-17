package com.childsafety.os.policy

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentSkipListSet

/**
 * Manages user-specific whitelisted domains.
 * Allows parents/adult users to override blocks for specific sites.
 */
object UserWhitelistManager {

    private const val PREF_NAME = "user_whitelist_prefs"
    private const val KEY_WHITELIST = "whitelist_domains"

    private lateinit var prefs: SharedPreferences
    private val whitelistCache = ConcurrentSkipListSet<String>()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        loadWhitelist()
    }

    private fun loadWhitelist() {
        val saved = prefs.getStringSet(KEY_WHITELIST, emptySet()) ?: emptySet()
        whitelistCache.clear()
        whitelistCache.addAll(saved)
    }

    fun isWhitelisted(domain: String): Boolean {
        if (domain.isBlank()) return false
        val lowerDomain = domain.lowercase()
        // Check if exact match or subdomain
        return whitelistCache.any { trusted ->
             lowerDomain == trusted || lowerDomain.endsWith(".$trusted")
        }
    }

    fun addToWhitelist(domain: String) {
        val lowerDomain = domain.lowercase()
        whitelistCache.add(lowerDomain)
        saveWhitelist()
    }

    fun removeFromWhitelist(domain: String) {
        val lowerDomain = domain.lowercase()
        whitelistCache.remove(lowerDomain)
        saveWhitelist()
    }
    
    fun getWhitelist(): Set<String> {
        return whitelistCache.toSet()
    }
    
    fun clear() {
        whitelistCache.clear()
        saveWhitelist()
    }

    private fun saveWhitelist() {
        prefs.edit().putStringSet(KEY_WHITELIST, whitelistCache).apply()
    }
}
