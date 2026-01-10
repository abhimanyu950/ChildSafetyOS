package com.childsafety.os

import android.app.Application
import android.os.Build
import android.util.Log
import com.childsafety.os.cloud.EventUploader
import com.childsafety.os.cloud.FirebaseManager
import com.childsafety.os.util.DeviceUtils
import com.google.firebase.FirebaseApp

class ChildSafetyApp : Application() {

    companion object {
        private const val TAG = "ChildSafetyApp"

        // Cached device ID for use throughout the app
        @Volatile
        var appDeviceId: String = "unknown_device"
            private set

        internal fun setDeviceId(id: String) {
            appDeviceId = id
        }
    }

    override fun onCreate() {
        super.onCreate()

        try {
            // Get unique device ID
            val id = DeviceUtils.getDeviceId(this)
            setDeviceId(id)

            // Initialize Firebase
            FirebaseApp.initializeApp(this)
            FirebaseManager.init(this)

            // Log app start event - using local variables to avoid any type confusion
            // Log app start event - using local variables to avoid any type confusion
            val api: Int = Build.VERSION.SDK_INT
            val device: String = appDeviceId
            EventUploader.logAppStart(api.toString(), device)

            Log.i(TAG, "App started | Firebase OK | Device: $device")
        } catch (e: Exception) {
            Log.e(TAG, "Startup failed, running degraded", e)
        }
    }
}


