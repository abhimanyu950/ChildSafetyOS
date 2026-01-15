package com.childsafety.os.security

import android.content.Context
import android.provider.Settings
import android.util.Log

/**
 * SecurityManager - Handles security-related features.
 * 
 * Features:
 * - PIN attempt tracking with lockout
 * - USB debugging detection
 * - Root detection (basic)
 */
object SecurityManager {
    
    private const val TAG = "SecurityManager"
    private const val MAX_PIN_ATTEMPTS = 5
    private const val LOCKOUT_DURATION_MS = 30_000L // 30 seconds
    
    private var pinAttempts = 0
    private var lockoutUntil: Long = 0
    
    /**
     * Check if PIN entry is currently locked out
     */
    fun isLockedOut(): Boolean {
        if (System.currentTimeMillis() < lockoutUntil) {
            return true
        }
        // Lockout expired, reset attempts
        if (lockoutUntil > 0 && System.currentTimeMillis() >= lockoutUntil) {
            pinAttempts = 0
            lockoutUntil = 0
        }
        return false
    }
    
    /**
     * Get remaining lockout time in seconds
     */
    fun getRemainingLockoutSeconds(): Int {
        val remaining = lockoutUntil - System.currentTimeMillis()
        return if (remaining > 0) (remaining / 1000).toInt() else 0
    }
    
    /**
     * Get remaining PIN attempts
     */
    fun getRemainingAttempts(): Int {
        return MAX_PIN_ATTEMPTS - pinAttempts
    }
    
    /**
     * Record a failed PIN attempt
     * Returns true if now locked out
     */
    fun recordFailedAttempt(): Boolean {
        pinAttempts++
        Log.w(TAG, "Failed PIN attempt $pinAttempts/$MAX_PIN_ATTEMPTS")
        
        if (pinAttempts >= MAX_PIN_ATTEMPTS) {
            lockoutUntil = System.currentTimeMillis() + LOCKOUT_DURATION_MS
            Log.w(TAG, "PIN locked out for 30 seconds")
            
            // Log security event
            try {
                com.childsafety.os.cloud.FirebaseManager.logSecurityAlert("PIN_LOCKOUT", "5 failed PIN attempts")
            } catch (e: Exception) { }
            
            return true
        }
        return false
    }
    
    /**
     * Reset PIN attempts on successful entry
     */
    fun resetAttempts() {
        pinAttempts = 0
        lockoutUntil = 0
    }
    
    /**
     * Check if USB debugging is enabled (security risk)
     */
    fun isUsbDebuggingEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if device might be rooted (basic detection)
     */
    fun isDevicePotentiallyRooted(): Boolean {
        val rootPaths = listOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        
        return rootPaths.any { java.io.File(it).exists() }
    }
    
    /**
     * Get security status summary
     */
    fun getSecurityStatus(context: Context): SecurityStatus {
        return SecurityStatus(
            usbDebuggingEnabled = isUsbDebuggingEnabled(context),
            potentiallyRooted = isDevicePotentiallyRooted(),
            pinLocked = isLockedOut(),
            remainingAttempts = getRemainingAttempts()
        )
    }
}

data class SecurityStatus(
    val usbDebuggingEnabled: Boolean,
    val potentiallyRooted: Boolean,
    val pinLocked: Boolean,
    val remainingAttempts: Int
)
