package com.childsafety.os.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class ChildSafetyAdminReceiver : DeviceAdminReceiver() {
    
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        // Sync to Cloud
        com.childsafety.os.cloud.FirebaseManager.updateDeviceStatus(adminEnabled = true)
        Toast.makeText(context, "Child Safety: Uninstall protection enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        // Sync to Cloud
        com.childsafety.os.cloud.FirebaseManager.updateDeviceStatus(adminEnabled = false)
        Toast.makeText(context, "Child Safety: App is no longer protected", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // 1. Log CRITICAL Alert to Firebase
        com.childsafety.os.cloud.FirebaseManager.logCriticalAlert(
            title = "Admin Privileges Revoked",
            message = "User is attempting to disable protection (likely for uninstall). Action detected in System Settings."
        )
        
        // 2. Return a warning message to the system dialog
        return "WARNING: Disabling this will remove all safety protections. This action has been logged."
    }
}
