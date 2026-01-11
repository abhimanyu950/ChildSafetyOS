package com.childsafety.os.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.childsafety.os.ChildSafetyApp
import java.util.concurrent.ConcurrentHashMap

object ImageHashCache {
    private const val TAG = "ImageHashCache"
    private const val PREFS_NAME = "image_hash_cache"
    private const val MAX_CACHE_SIZE = 10000 // Limit cache growth
    
    // In-memory cache: hash -> isBlocked
    private val memoryCache = ConcurrentHashMap<String, Boolean>()
    
    // SharedPreferences (initialized on first use)
    private var prefs: android.content.SharedPreferences? = null
    private var initialized = false
    
    /**
     * Initialize with application context (called from MainActivity or ChildSafetyApp).
     */
    fun init(context: Context) {
        if (initialized) return
        
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        initialized = true
        loadFromDisk()
    }
    
    /**
     * Ensure initialization (call this before any operation).
     */
    private fun ensureInitialized(context: Context) {
        if (!initialized) {
            init(context)
        }
    }
    
    /**
     * Get cached decision for an image hash.
     * @return true if blocked, false if safe, null if not in cache
     */
    fun getDecision(hash: String): Boolean? {
        return memoryCache[hash]
    }
    
    /**
     * Cache a decision for an image hash.
     */
    fun cacheDecision(hash: String, isBlocked: Boolean) {
        // Update memory cache
        memoryCache[hash] = isBlocked
        
        // Persist to disk (async) if initialized
        try {
            prefs?.edit()?.putBoolean(hash, isBlocked)?.apply()
            Log.d(TAG, "Cached decision: hash=${hash.take(8)}... blocked=$isBlocked")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist cache", e)
        }
        
        // Prevent unbounded growth
        if (memoryCache.size > MAX_CACHE_SIZE) {
            pruneOldestEntries()
        }
    }
    
    /**
     * Calculate perceptual hash of a bitmap.
     * Uses DCT-based pHash algorithm (resistant to minor changes).
     */
    fun calculateHash(bitmap: Bitmap): String {
        try {
            // 1. Resize to 8x8 (perceptual hash standard)
            val resized = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
            
            // 2. Convert to grayscale and get pixel values
            val pixels = FloatArray(64)
            for (y in 0 until 8) {
                for (x in 0 until 8) {
                    val pixel = resized.getPixel(x, y)
                    // Grayscale conversion
                    val gray = (Color.red(pixel) * 0.299 + 
                               Color.green(pixel) * 0.587 + 
                               Color.blue(pixel) * 0.114).toFloat()
                    pixels[y * 8 + x] = gray
                }
            }
            
            // 3. Calculate average
            val avg = pixels.average().toFloat()
            
            // 4. Generate hash: 1 if above average, 0 if below
            val hash = StringBuilder()
            for (value in pixels) {
                hash.append(if (value >= avg) '1' else '0')
            }
            
            resized.recycle()
            return hash.toString()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating hash", e)
            // Return a random hash so it won't match anything
            return System.currentTimeMillis().toString()
        }
    }
    
    /**
     * Load cache from persistent storage on init.
     */
    private fun loadFromDisk() {
        try {
            val allEntries = prefs?.all ?: emptyMap<String, Any?>()
            for ((key, value) in allEntries) {
                if (value is Boolean) {
                    memoryCache[key] = value
                }
            }
            Log.i(TAG, "Loaded ${memoryCache.size} cached decisions from disk")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cache from disk", e)
        }
    }
    
    /**
     * Remove oldest 20% of entries when cache is full.
     */
    private fun pruneOldestEntries() {
        val entriesToRemove = memoryCache.size / 5 // Remove 20%
        val iterator = memoryCache.keys.iterator()
        
        var removed = 0
        val editor = prefs?.edit()
        
        while (iterator.hasNext() && removed < entriesToRemove) {
            val key = iterator.next()
            iterator.remove()
            editor?.remove(key)
            removed++
        }
        
        editor?.apply()
        Log.i(TAG, "Pruned $removed oldest cache entries")
    }
    
    /**
     * Clear all cached data (for debugging/testing).
     */
    fun clear() {
        memoryCache.clear()
        prefs?.edit()?.clear()?.apply()
        Log.i(TAG, "Cache cleared")
    }
    
    /**
     * Get cache statistics.
     */
    fun getStats(): CacheStats {
        return CacheStats(
            totalEntries = memoryCache.size,
            blockedCount = memoryCache.count { it.value },
            safeCount = memoryCache.count { !it.value }
        )
    }
    
    data class CacheStats(
        val totalEntries: Int,
        val blockedCount: Int,
        val safeCount: Int
    )
}
