package com.childsafety.os.cloud

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

/**
 * Manages Firebase initialization and configuration.
 * Called once during Application.onCreate().
 */
object FirebaseManager {

    private const val TAG = "FirebaseManager"
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        
        try {
            // Configure Firestore for offline support
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build()
            
            FirebaseFirestore.getInstance().firestoreSettings = settings
            
            initialized = true
            Log.i(TAG, "Firebase initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization failed (app will continue)", e)
        }
    }
}
