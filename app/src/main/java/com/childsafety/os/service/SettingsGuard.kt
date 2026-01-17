package com.childsafety.os.service

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

/**
 * Intelligent guard for System Settings.
 * Uses hierarchy inspection to block only specific dangerous pages
 * while allowing safe usage of Settings (e.g., Wifi, Display).
 */
object SettingsGuard {
    private const val TAG = "SettingsGuard"
    private const val SETTINGS_PKG = "com.android.settings"

    // List of forbidden page titles (lowercase)
    private val FORBIDDEN_TITLES = listOf(
        "device admin",
        "device admin apps",
        "device administrators",
        "date & time",
        "date and time",
        "developer options",
        "users & accounts",
        "multiple users",
        "factory reset",
        "erase all data"
    )

    // Our App Name to protect its App Info page
    private const val PROTECTED_APP_NAME = "ChildSafetyOS"

    /**
     * structural scan of the screen to determine if it should be blocked.
     * Returns a pair of (ShouldBlock, Reason)
     */
    fun shouldBlock(event: AccessibilityEvent, rootNode: AccessibilityNodeInfo?): Pair<Boolean, String> {
        val packageName = event.packageName?.toString() ?: return false to ""
        
        // Only inspect System Settings
        if (packageName != SETTINGS_PKG && !packageName.contains("settings")) {
            return false to ""
        }

        if (rootNode == null) return false to ""

        // 1. Check Window Title (often available in event text or root description)
        val eventText = event.text.joinToString(" ").lowercase()
        
        // Quick check for highly dangerous keywords in the event text itself (top level)
        if (isValidDangerousContext(eventText)) {
            return true to "Restricted Settings Menu"
        }

        // 2. Deep Inspection of View Hierarchy
        // We look for specific headers or combinations of text
        val restriction = scanHierarchy(rootNode)
        if (restriction != null) {
            return true to restriction
        }

        return false to ""
    }

    private fun isValidDangerousContext(text: String): Boolean {
        // Simple text matching for obvious blocking
        return FORBIDDEN_TITLES.any { text.contains(it) }
    }

    private fun scanHierarchy(node: AccessibilityNodeInfo): String? {
        // Recursive scan to find dangerous combinations
        // NOTE: In production, we'd limit recursion depth, but Settings hierarchies are usually shallow enough.
        
        val nodeText = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val text = (nodeText + " " + contentDesc).trim()

        // CHECK 1: App Info Page Protection
        // If we see "App info" AND "ChildSafetyOS" on the screen, block it.
        // This is a heuristic: usually the App Info page has the app name clearly visible.
        if (text.equals(PROTECTED_APP_NAME, ignoreCase = true)) {
            // Found our app name. Now check if we are in an "App info" context.
            // This is harder to be 100% sure of without false positives, 
            // but if we are in Settings and see "ChildSafetyOS", it's 99% likely the App Info page 
            // or the Special App Access page for it. Safe to block to be sure.
            return "Protected App Settings"
        }

        // CHECK 2: Users / Profiles
        if (text.equals("Remove user", ignoreCase = true) || 
            text.equals("Add user", ignoreCase = true)) {
            return "User Management Restricted"
        }

        // Recursively check children
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val result = scanHierarchy(child)
                child.recycle() // Important!
                if (result != null) return result
            }
        }

        return null
    }
}
