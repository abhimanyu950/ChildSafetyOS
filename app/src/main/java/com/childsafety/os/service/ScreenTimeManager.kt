package com.childsafety.os.service

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import com.childsafety.os.cloud.FirebaseManager
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Screen Time Manager - Tracks app usage and enforces time limits.
 * 
 * Features:
 * - Per-app time limits
 * - Category-based limits (Games, Social, etc.)
 * - Daily reset at midnight
 * - "Time's Up" overlay when limit exceeded
 */
object ScreenTimeManager {

    private const val TAG = "ScreenTimeManager"
    private const val PREFS_NAME = "screen_time_prefs"
    private const val KEY_DAILY_LIMITS = "daily_limits"
    private const val KEY_LAST_RESET = "last_reset_date"

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // App usage today (in minutes)
    private val appUsageToday = ConcurrentHashMap<String, Long>()
    
    // Time limits per app category (in minutes)
    private val categoryLimits = mutableMapOf<AppCategory, Int>()
    
    // Custom per-app limits (in minutes)
    private val appLimits = mutableMapOf<String, Int>()
    
    // Apps that have exceeded their limit today
    private val exceededApps = mutableSetOf<String>()

    /**
     * App categories for time limits
     */
    enum class AppCategory {
        GAMES,
        SOCIAL_MEDIA,
        VIDEO,
        BROWSER,
        OTHER
    }

    /**
     * Common gaming apps
     */
    private val gamingApps = setOf(
        "com.supercell.clashofclans", "com.supercell.clashroyale",
        "com.mojang.minecraftpe", "com.kiloo.subwaysurf",
        "com.imangi.templerun2", "com.pubg.krmobile",
        "com.activision.callofduty.shooter", "com.dts.freefireth",
        "com.mobile.legends", "com.riotgames.league.wildrift"
    )

    /**
     * Common social media apps
     */
    private val socialApps = setOf(
        "com.instagram.android", "com.zhiliaoapp.musically",
        "com.snapchat.android", "com.twitter.android",
        "com.facebook.katana", "com.whatsapp"
    )

    /**
     * Common video apps
     */
    private val videoApps = setOf(
        "com.google.android.youtube", "com.netflix.mediaclient",
        "com.amazon.avod.thirdpartyclient", "in.startv.hotstar",
        "com.jio.media.jiobeats"
    )

    /**
     * Initialize with saved preferences
     */
    fun init(context: Context) {
        loadPreferences(context)
        checkDailyReset(context)
        
        // Set default limits
        if (categoryLimits.isEmpty()) {
            categoryLimits[AppCategory.GAMES] = 60       // 1 hour
            categoryLimits[AppCategory.SOCIAL_MEDIA] = 60 // 1 hour
            categoryLimits[AppCategory.VIDEO] = 120      // 2 hours
            categoryLimits[AppCategory.BROWSER] = 120    // 2 hours
            categoryLimits[AppCategory.OTHER] = 180      // 3 hours
        }
        
        Log.i(TAG, "ScreenTimeManager initialized")
    }

    /**
     * Check if usage stats permission is granted
     */
    fun hasPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Open usage stats permission settings
     */
    fun requestPermission(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Get today's usage for an app (in minutes)
     */
    fun getAppUsageToday(context: Context, packageName: String): Long {
        if (!hasPermission(context)) return 0
        
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfDay = calendar.timeInMillis
        val now = System.currentTimeMillis()
        
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startOfDay,
            now
        )
        
        val appStats = stats.find { it.packageName == packageName }
        return if (appStats != null) {
            appStats.totalTimeInForeground / 60000 // Convert ms to minutes
        } else {
            0
        }
    }

    /**
     * Get category usage today (aggregates all apps in category)
     */
    fun getCategoryUsageToday(context: Context, category: AppCategory): Long {
        val apps = when (category) {
            AppCategory.GAMES -> gamingApps
            AppCategory.SOCIAL_MEDIA -> socialApps
            AppCategory.VIDEO -> videoApps
            else -> emptySet()
        }
        
        return apps.sumOf { getAppUsageToday(context, it) }
    }

    /**
     * Check if an app has exceeded its time limit
     */
    fun isLimitExceeded(context: Context, packageName: String): Boolean {
        // Check custom app limit first
        if (appLimits.containsKey(packageName)) {
            val limit = appLimits[packageName] ?: Int.MAX_VALUE
            val usage = getAppUsageToday(context, packageName)
            return usage >= limit
        }
        
        // Check category limit
        val category = getAppCategory(packageName)
        val limit = categoryLimits[category] ?: Int.MAX_VALUE
        val usage = getCategoryUsageToday(context, category)
        
        return usage >= limit
    }

    /**
     * Get remaining time for an app (in minutes)
     */
    fun getRemainingTime(context: Context, packageName: String): Long {
        val category = getAppCategory(packageName)
        val limit = appLimits[packageName] ?: categoryLimits[category] ?: Int.MAX_VALUE
        val usage = if (appLimits.containsKey(packageName)) {
            getAppUsageToday(context, packageName)
        } else {
            getCategoryUsageToday(context, category)
        }
        
        return maxOf(0, limit - usage)
    }

    /**
     * Set a time limit for an app (in minutes)
     */
    fun setAppLimit(context: Context, packageName: String, limitMinutes: Int) {
        appLimits[packageName] = limitMinutes
        savePreferences(context)
        Log.i(TAG, "Set limit for $packageName: $limitMinutes minutes")
    }

    /**
     * Set a time limit for a category (in minutes)
     */
    fun setCategoryLimit(context: Context, category: AppCategory, limitMinutes: Int) {
        categoryLimits[category] = limitMinutes
        savePreferences(context)
        Log.i(TAG, "Set limit for $category: $limitMinutes minutes")
    }

    /**
     * Get the category of an app
     */
    fun getAppCategory(packageName: String): AppCategory {
        return when {
            gamingApps.contains(packageName) -> AppCategory.GAMES
            socialApps.contains(packageName) -> AppCategory.SOCIAL_MEDIA
            videoApps.contains(packageName) -> AppCategory.VIDEO
            packageName.contains("browser") || packageName.contains("chrome") -> AppCategory.BROWSER
            else -> AppCategory.OTHER
        }
    }

    /**
     * Get all usage stats for today
     */
    fun getAllUsageToday(context: Context): Map<String, Long> {
        if (!hasPermission(context)) return emptyMap()
        
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfDay = calendar.timeInMillis
        val now = System.currentTimeMillis()
        
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startOfDay,
            now
        )
        
        return stats
            .filter { it.totalTimeInForeground > 0 }
            .associate { it.packageName to (it.totalTimeInForeground / 60000) }
            .toList()
            .sortedByDescending { it.second }
            .toMap()
    }

    /**
     * Log time limit exceeded event
     */
    fun logLimitExceeded(context: Context, packageName: String) {
        if (exceededApps.contains(packageName)) return // Already logged today
        
        exceededApps.add(packageName)
        val category = getAppCategory(packageName)
        val usage = getAppUsageToday(context, packageName)
        val limit = appLimits[packageName] ?: categoryLimits[category] ?: 0
        
        // Log to Firebase using existing method
        scope.launch {
            FirebaseManager.logAppLock(
                appPackage = packageName,
                appName = "$packageName (${category.name})",
                reason = "Screen time exceeded: $usage/$limit minutes"
            )
        }
        
        Log.w(TAG, "Time limit exceeded: $packageName ($usage minutes)")
    }

    private fun checkDailyReset(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastReset = prefs.getString(KEY_LAST_RESET, "") ?: ""
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        
        if (lastReset != today) {
            // Reset daily tracking
            appUsageToday.clear()
            exceededApps.clear()
            prefs.edit().putString(KEY_LAST_RESET, today).apply()
            Log.i(TAG, "Daily reset performed")
        }
    }

    private fun savePreferences(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Save category limits as JSON
        val limitsJson = categoryLimits.entries.joinToString(",") { "${it.key.name}:${it.value}" }
        prefs.edit().putString(KEY_DAILY_LIMITS, limitsJson).apply()
    }

    private fun loadPreferences(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val limitsJson = prefs.getString(KEY_DAILY_LIMITS, "") ?: ""
        
        if (limitsJson.isNotEmpty()) {
            limitsJson.split(",").forEach { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) {
                    try {
                        val category = AppCategory.valueOf(parts[0])
                        val limit = parts[1].toInt()
                        categoryLimits[category] = limit
                    } catch (e: Exception) {
                        // Ignore invalid entries
                    }
                }
            }
        }
    }
}
