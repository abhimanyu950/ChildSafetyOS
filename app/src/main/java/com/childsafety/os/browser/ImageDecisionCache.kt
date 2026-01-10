package com.childsafety.os.browser

import android.util.LruCache

/**
 * LRU cache for image ML decisions.
 * Prevents repeated network/ML calls for same image URLs.
 * Limited to 500 entries to prevent memory issues.
 */
object ImageDecisionCache {
    
    private const val MAX_ENTRIES = 500
    
    private val cache = object : LruCache<String, Boolean>(MAX_ENTRIES) {
        override fun sizeOf(key: String, value: Boolean): Int = 1
    }
    
    fun get(url: String): Boolean? = cache.get(url)
    
    fun put(url: String, blocked: Boolean) {
        cache.put(url, blocked)
    }
    
    fun clear() {
        cache.evictAll()
    }
}
