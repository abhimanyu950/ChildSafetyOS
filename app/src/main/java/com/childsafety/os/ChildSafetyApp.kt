package com.childsafety.os

import android.app.Application
import android.os.Build
import android.util.Log

import com.childsafety.os.cloud.FirebaseManager
import com.childsafety.os.util.DeviceUtils
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
            
            // Initialize App Check for enhanced security (device attestation)
            try {
                val appCheck = com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
                appCheck.installAppCheckProviderFactory(
                    com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory.getInstance()
                )
                Log.i(TAG, "App Check initialized with Play Integrity")
            } catch (e: Exception) {
                Log.w(TAG, "App Check init failed (debug mode or no Play Services)", e)
            }
            
            FirebaseManager.init(this)
            com.childsafety.os.cloud.RemoteConfigManager.init()
            com.childsafety.os.gamification.GamificationManager.init(this)
            com.childsafety.os.policy.UserWhitelistManager.init(this)
            
            // Initialize Anonymous Authentication (runs in background)
            GlobalScope.launch(Dispatchers.IO) {
                val authSuccess = com.childsafety.os.cloud.AuthManager.initAuth()
                if (authSuccess) {
                    Log.i(TAG, "Auth initialized. UID: ${com.childsafety.os.cloud.AuthManager.userId}")
                } else {
                    Log.w(TAG, "Auth initialization failed - running without auth")
                }
            }

            // Log app start event
            val api: Int = Build.VERSION.SDK_INT
            val device: String = appDeviceId
            FirebaseManager.logAppStart(api.toString(), device)

            Log.i(TAG, "App started | Firebase OK | Device: $device")
        } catch (e: Exception) {
            Log.e(TAG, "Startup failed, running degraded", e)
        }
    }
}
