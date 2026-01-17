package com.childsafety.os.manager

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.app.AppOpsManager
import android.os.Process
import java.util.Calendar

object UsageManager {

    /**
     * Check if Usage Access permission is granted
     */
    fun hasPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Prompt user to grant permission
     */
    fun requestPermission(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Get today's app usage in minutes.
     * Returns Map<PackageName, Minutes>
     */
    fun getTodayUsage(context: Context): Map<String, Long> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        val usageMap = mutableMapOf<String, Long>()

        if (usageStatsList != null) {
            for (usageStats in usageStatsList) {
                // Total time in foreground
                val totalTimeMs = usageStats.totalTimeInForeground
                if (totalTimeMs > 0) {
                    // Convert to minutes
                    val minutes = totalTimeMs / 1000 / 60
                    if (minutes > 0) {
                        usageMap[usageStats.packageName] = minutes
                    }
                }
            }
        }
        
        // Filter out system apps with 0 or very low usage if needed, 
        // strictly returning apps with meaningful usage (> 1 min)
        return usageMap.filterValues { it >= 1 }
    }
}
