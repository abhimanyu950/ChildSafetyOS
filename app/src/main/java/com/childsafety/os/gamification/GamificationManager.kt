package com.childsafety.os.gamification

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages the "Cyber Ninja" gamification logic.
 * Tracks safe streaks, points, and badges to positively reinforce safety.
 */
object GamificationManager {

    private const val TAG = "GamificationManager"
    private const val PREFS_NAME = "child_safety_gamification"
    
    // keys
    private const val KEY_STREAK_DAYS = "streak_days"
    private const val KEY_TOTAL_POINTS = "total_points"
    private const val KEY_LAST_CHECK_DATE = "last_check_date"
    private const val KEY_IS_TODAY_SAFE = "is_today_safe"
    private const val KEY_BADGES = "unlocked_badges"

    private lateinit var prefs: SharedPreferences
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        initialized = true
        
        // Initial check on app launch
        checkDailyProgress()
    }

    /**
     * Called whenever a BLOCK event occurs (Image, Text, App Lock).
     * This marks the current day as "Unsafe" and breaks the potential streak increase for today.
     * note: It doesn't reset the *existing* streak, just prevents it from growing tomorrow.
     */
    fun onSafetyViolation() {
        if (!initialized) return
        
        // If today was previously marked safe, mark it unsafe
        if (prefs.getBoolean(KEY_IS_TODAY_SAFE, true)) {
            Log.i(TAG, "🚫 Safety violation detected! Today is no longer a 'Safe Day'.")
            prefs.edit().putBoolean(KEY_IS_TODAY_SAFE, false).apply()
        }
    }

    /**
     * Check if we moved to a new day and update stats.
     * Should be called on App Start.
     */
    fun checkDailyProgress() {
        if (!initialized) return

        val today = getTodayString()
        val lastCheck = prefs.getString(KEY_LAST_CHECK_DATE, "")

        if (today != lastCheck) {
            // It's a new day! Let's conclude yesterday.
            if (!lastCheck.isNullOrEmpty()) {
                // Was yesterday clean?
                val wasYesterdaySafe = prefs.getBoolean(KEY_IS_TODAY_SAFE, true)
                
                if (wasYesterdaySafe) {
                    // 🎉 STREAK INCREASE!
                    incrementStreak()
                    Log.i(TAG, "🎉 Yesterday was safe! Streak increased.")
                } else {
                    // 😢 STREAK RESET
                    resetStreak()
                    Log.i(TAG, "😢 Yesterday had violations. Streak reset.")
                }
            }
            
            // Setup for Today (Default to SAFE until proven otherwise)
            prefs.edit()
                .putString(KEY_LAST_CHECK_DATE, today)
                .putBoolean(KEY_IS_TODAY_SAFE, true)
                .apply()
        }
    }

    private fun incrementStreak() {
        val current = prefs.getInt(KEY_STREAK_DAYS, 0)
        val newStreak = current + 1
        val pointsEarned = 100 + (newStreak * 10) // Bonus for longer streaks
        
        addPoints(pointsEarned)
        
        prefs.edit().putInt(KEY_STREAK_DAYS, newStreak).apply()
        
        // Check for Badges
        checkBadges(newStreak)
    }

    private fun resetStreak() {
        prefs.edit().putInt(KEY_STREAK_DAYS, 0).apply()
    }
    
    fun addPoints(amount: Int) {
        val current = prefs.getInt(KEY_TOTAL_POINTS, 0)
        prefs.edit().putInt(KEY_TOTAL_POINTS, current + amount).apply()
    }

    /**
     * Unlock badges based on milestones
     */
    private fun checkBadges(streak: Int) {
        val currentBadges = getBadges().toMutableSet()
        
        if (streak >= 3) currentBadges.add("badge_scout") // 3 Days
        if (streak >= 7) currentBadges.add("badge_ninja") // 7 Days
        if (streak >= 30) currentBadges.add("badge_master") // 30 Days
        
        prefs.edit().putStringSet(KEY_BADGES, currentBadges).apply()
    }

    // --- Getters for UI ---
    
    fun getStreak(): Int = prefs.getInt(KEY_STREAK_DAYS, 0)
    
    fun getPoints(): Int = prefs.getInt(KEY_TOTAL_POINTS, 0)
    
    fun getBadges(): Set<String> = prefs.getStringSet(KEY_BADGES, emptySet()) ?: emptySet()
    
    fun isTodayStillSafe(): Boolean = prefs.getBoolean(KEY_IS_TODAY_SAFE, true)

    private fun getTodayString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
}
