package com.childsafety.os.cloud

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.childsafety.os.ChildSafetyApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

/**
 * Dynamic Cloud Policy Sync - Fetches blocklists from Firestore in real-time.
 * 
 * Features:
 * - Real-time listener for instant updates
 * - Local caching for offline support
 * - Merge with hardcoded domains
 * - Parent can add domains via dashboard
 */
object CloudPolicySync {

    private const val TAG = "CloudPolicySync"
    private const val PREFS_NAME = "cloud_policy_cache"
    private const val KEY_BLOCKED_DOMAINS = "blocked_domains"
    private const val KEY_LAST_SYNC = "last_sync_time"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Dynamic domains from Firestore
    private val cloudBlockedDomains = mutableSetOf<String>()
    private val cloudAllowedDomains = mutableSetOf<String>() // Whitelist
    
    // Firestore listener
    private var policyListener: ListenerRegistration? = null

    /**
     * Initialize with local cache
     */
    fun init(context: Context) {
        loadFromCache(context)
        Log.i(TAG, "CloudPolicySync initialized with ${cloudBlockedDomains.size} cached domains")
    }

    /**
     * Start real-time sync with Firestore
     */
    fun startSync(context: Context) {
        val deviceId = ChildSafetyApp.appDeviceId
        if (deviceId.isEmpty()) {
            Log.w(TAG, "Device ID not set, cannot sync")
            return
        }

        val db = FirebaseFirestore.getInstance()

        // Listen to device-specific policy
        policyListener = db.collection("devices")
            .document(deviceId)
            .collection("settings")
            .document("policy")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Firestore listen failed", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val blockedList = snapshot.get("blocked_domains") as? List<String> ?: emptyList()
                    @Suppress("UNCHECKED_CAST")
                    val allowedList = snapshot.get("allowed_domains") as? List<String> ?: emptyList()

                    cloudBlockedDomains.clear()
                    cloudBlockedDomains.addAll(blockedList)
                    
                    cloudAllowedDomains.clear()
                    cloudAllowedDomains.addAll(allowedList)

                    // Save to cache
                    saveToCache(context)
                    
                    Log.i(TAG, "Policy synced: ${blockedList.size} blocked, ${allowedList.size} allowed")
                }
            }

        // Also fetch global blocklist
        fetchGlobalBlocklist(context)
    }

    /**
     * Fetch global blocklist (shared across all devices)
     */
    private fun fetchGlobalBlocklist(context: Context) {
        scope.launch {
            try {
                val db = FirebaseFirestore.getInstance()
                val doc = db.collection("global")
                    .document("blocklist")
                    .get()
                    .await()

                if (doc.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val domains = doc.get("domains") as? List<String> ?: emptyList()
                    cloudBlockedDomains.addAll(domains)
                    saveToCache(context)
                    Log.i(TAG, "Global blocklist fetched: ${domains.size} domains")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch global blocklist", e)
            }
        }
    }

    /**
     * Stop syncing
     */
    fun stopSync() {
        policyListener?.remove()
        policyListener = null
    }

    /**
     * Check if a domain is blocked by cloud policy
     */
    fun isBlockedByCloud(host: String): Boolean {
        val h = host.lowercase()
        
        // Check whitelist first
        if (cloudAllowedDomains.any { h == it || h.endsWith(".$it") }) {
            return false
        }
        
        // Check blocklist
        return cloudBlockedDomains.any { h == it || h.endsWith(".$it") }
    }

    /**
     * Check if a domain is whitelisted
     */
    fun isAllowedByCloud(host: String): Boolean {
        val h = host.lowercase()
        return cloudAllowedDomains.any { h == it || h.endsWith(".$it") }
    }

    /**
     * Get all cloud-blocked domains
     */
    fun getBlockedDomains(): Set<String> = cloudBlockedDomains.toSet()

    /**
     * Get all cloud-allowed domains
     */
    fun getAllowedDomains(): Set<String> = cloudAllowedDomains.toSet()

    /**
     * Add a domain to blocklist via Firestore
     */
    suspend fun addBlockedDomain(domain: String): Boolean {
        return try {
            val deviceId = ChildSafetyApp.appDeviceId
            val db = FirebaseFirestore.getInstance()
            
            val currentList = cloudBlockedDomains.toMutableList()
            if (!currentList.contains(domain.lowercase())) {
                currentList.add(domain.lowercase())
            }
            
            db.collection("devices")
                .document(deviceId)
                .collection("settings")
                .document("policy")
                .update("blocked_domains", currentList)
                .await()
            
            cloudBlockedDomains.add(domain.lowercase())
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add blocked domain", e)
            false
        }
    }

    /**
     * Remove a domain from blocklist
     */
    suspend fun removeBlockedDomain(domain: String): Boolean {
        return try {
            val deviceId = ChildSafetyApp.appDeviceId
            val db = FirebaseFirestore.getInstance()
            
            val currentList = cloudBlockedDomains.toMutableList()
            currentList.remove(domain.lowercase())
            
            db.collection("devices")
                .document(deviceId)
                .collection("settings")
                .document("policy")
                .update("blocked_domains", currentList)
                .await()
            
            cloudBlockedDomains.remove(domain.lowercase())
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove blocked domain", e)
            false
        }
    }

    private fun saveToCache(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val blockedJson = JSONArray(cloudBlockedDomains.toList()).toString()
            val allowedJson = JSONArray(cloudAllowedDomains.toList()).toString()
            
            prefs.edit()
                .putString(KEY_BLOCKED_DOMAINS, blockedJson)
                .putString("allowed_domains", allowedJson)
                .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save cache", e)
        }
    }

    private fun loadFromCache(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            
            val blockedJson = prefs.getString(KEY_BLOCKED_DOMAINS, "[]") ?: "[]"
            val allowedJson = prefs.getString("allowed_domains", "[]") ?: "[]"
            
            val blockedArray = JSONArray(blockedJson)
            for (i in 0 until blockedArray.length()) {
                cloudBlockedDomains.add(blockedArray.getString(i))
            }
            
            val allowedArray = JSONArray(allowedJson)
            for (i in 0 until allowedArray.length()) {
                cloudAllowedDomains.add(allowedArray.getString(i))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cache", e)
        }
    }
}
