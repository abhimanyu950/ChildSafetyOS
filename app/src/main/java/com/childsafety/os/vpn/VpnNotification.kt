package com.childsafety.os.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.childsafety.os.R

object VpnNotification {

    fun build(context: Context): Notification {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                VpnConstants.CHANNEL_ID,
                "Child Safety Protection",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager =
                context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return Notification.Builder(context, VpnConstants.CHANNEL_ID)
            .setContentTitle("Child Safety Protection")
            .setContentText("VPN protection active")
            .setSmallIcon(R.drawable.ic_vpn) // ✅ YOUR drawable
            .setOngoing(true)
            .build()
    }
}
