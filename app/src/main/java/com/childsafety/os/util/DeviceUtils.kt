package com.childsafety.os.util

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

object DeviceUtils {

    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        return try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown_${System.currentTimeMillis()}"
        } catch (e: Exception) {
            "fallback_${System.currentTimeMillis()}"
        }
    }
}

