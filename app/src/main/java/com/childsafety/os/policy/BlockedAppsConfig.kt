package com.childsafety.os.policy

import android.content.Context
import android.content.SharedPreferences
import com.childsafety.os.cloud.FirebaseManager
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Configurable blocked apps list with age-based restrictions.
 * 
 * Apps can be blocked by:
 * 1. Default hardcoded list (system apps, always blocked)
 * 2. Age-based restrictions (social media for children)
 * 3. Custom parent additions via Firestore
 */
object BlockedAppsConfig {

    private const val PREFS_NAME = "blocked_apps_prefs"
    private const val KEY_CUSTOM_APPS = "custom_blocked_apps"

    /**
     * System apps - ALWAYS blocked regardless of age
     * These are critical for preventing app tampering
     */
    private val systemApps = setOf(
        // "com.android.settings",           // Settings - REMOVED: Now handled smartly by AppLockService
        "com.android.vending",            // Play Store
        "com.google.android.packageinstaller", // Package installer
        "com.android.packageinstaller",   // Package installer (alternate)
        
        // === BROWSERS - Block to prevent filter bypass ===
        "com.android.chrome",             // Chrome
        "org.mozilla.firefox",            // Firefox
        "org.mozilla.firefox_beta",       // Firefox Beta
        "org.mozilla.fenix",              // Firefox Nightly
        "com.opera.browser",              // Opera
        "com.opera.mini.native",          // Opera Mini
        "com.brave.browser",              // Brave
        "com.microsoft.emmx",             // Edge
        "com.UCMobile.intl",              // UC Browser
        "com.duckduckgo.mobile.android",  // DuckDuckGo
        "com.kiwibrowser.browser",        // Kiwi Browser
        "org.chromium.chrome",            // Chromium
        "com.yandex.browser",             // Yandex Browser
        "com.sec.android.app.sbrowser",   // Samsung Internet
        "com.mi.globalbrowser",           // Mi Browser
        "com.huawei.browser",             // Huawei Browser
        "mark.via",                       // Via Browser
        "acr.browser.lightning",          // Lightning Browser
        
        // === VPN APPS - Block to prevent bypass ===
        "com.nordvpn.android",            // NordVPN
        "com.expressvpn.vpn",             // ExpressVPN
        "com.surfshark.vpnclient.android", // Surfshark
        "com.cyberghostvpn.android",      // CyberGhost
        "com.privateinternetaccess.android", // PIA
        "ch.protonvpn.android",           // ProtonVPN
        "net.mullvad.mullvadvpn",         // Mullvad
        "com.windscribe.vpn",             // Windscribe
        "com.tunnelbear.android",         // TunnelBear
        "hotspotshield.android.vpn",      // Hotspot Shield
        "com.ipvanish.android",           // IPVanish
        "com.purevpn.androidvpn",         // PureVPN
        "com.vyprvpn.android",            // VyprVPN
        "com.freevpn.unblock.proxy",      // Free VPN apps
        "com.fast.free.unblock.vpn.proxy", // Fast VPN
        "free.vpn.unblock.proxy.turbovpn", // Turbo VPN
        "com.speedvpn.free.vpn",          // Speed VPN
        "com.secure.vpn.free.proxy.master", // VPN Master
        "com.supervpn.android",           // SuperVPN
        "com.thunder.vpn.android",        // Thunder VPN
        
        // === TOR & ONION BROWSERS ===
        "org.torproject.torbrowser",      // Tor Browser
        "org.torproject.android",         // Orbot (Tor proxy)
        "com.nicevpn.topfreevpn",         // Onion Browser
        "info.nicevpn.nicevpn",           // Free Tor VPN
        "browser.nicevpn.nicevpn",        // Tor VPN Browser
        
        // === PROXY APPS ===
        "ca.psiphon.ploggy",              // Psiphon
        "org.nicevpn.nicevpn",            // Lantern
        "cloudveil.dns.doh.proxy",        // DNS proxy apps
        "com.appspot.nicevpn"             // Other proxy apps
    )

    /**
     * Social Media apps - blocked for CHILD, optional for TEEN
     */
    private val socialMediaApps = mapOf(
        "com.instagram.android" to "Instagram",
        "com.instagram.lite" to "Instagram Lite",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.ss.android.ugc.trill" to "TikTok (Lite)",
        "com.snapchat.android" to "Snapchat",
        "com.twitter.android" to "Twitter/X",
        "com.twitter.android.lite" to "Twitter Lite",
        "com.facebook.katana" to "Facebook",
        "com.facebook.lite" to "Facebook Lite",
        "com.facebook.orca" to "Messenger",
        "com.facebook.mlite" to "Messenger Lite",
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "com.discord" to "Discord",
        "tv.twitch.android.app" to "Twitch",
        "com.pinterest" to "Pinterest",
        "com.tumblr" to "Tumblr"
    )

    /**
     * Gaming apps - blocked for CHILD if time limit exceeded
     */
    private val gamingApps = mapOf(
        "com.supercell.clashofclans" to "Clash of Clans",
        "com.supercell.clashroyale" to "Clash Royale",
        "com.supercell.brawlstars" to "Brawl Stars",
        "com.mojang.minecraftpe" to "Minecraft",
        "com.kiloo.subwaysurf" to "Subway Surfers",
        "com.imangi.templerun2" to "Temple Run 2",
        "com.king.candycrushsaga" to "Candy Crush",
        "com.pubg.imobile" to "PUBG Mobile",
        "com.activision.callofduty.shooter" to "Call of Duty Mobile",
        "com.garena.game.freefire" to "Free Fire",
        "com.dts.freefireth" to "Free Fire (TH)",
        "com.epicgames.fortnite" to "Fortnite",
        "com.roblox.client" to "Roblox"
    )

    /**
     * Dating apps - blocked for CHILD and TEEN
     */
    private val datingApps = mapOf(
        "com.tinder" to "Tinder",
        "com.bumble.app" to "Bumble",
        "com.badoo.mobile" to "Badoo",
        "com.hinge.app" to "Hinge",
        "com.okcupid.okcupid" to "OkCupid",
        "com.match.android.matchmobile" to "Match",
        "com.pof.android" to "Plenty of Fish",
        "com.zoosk.zoosk" to "Zoosk",
        "com.grindr.android" to "Grindr",
        "com.scruff.app" to "Scruff",
        "com.coffee.meetsbagel" to "Coffee Meets Bagel"
    )

    /**
     * Adult/Explicit apps - ALWAYS blocked
     * Includes anonymous chat apps which can be used for predatory behavior
     */
    private val adultApps = mapOf(
        "com.reddit.frontpage" to "Reddit",
        // Anonymous chat apps (high risk for child safety)
        "com.leapfrog.omegle" to "Omegle",
        "com.omegle.videochat" to "Omegle Video",
        "com.chatroulette.android" to "Chatroulette",
        "gg.monkey.app" to "Monkey",
        "com.holla.android" to "Holla",
        "video.chat.yubo" to "Yubo",
        "com.whisper" to "Whisper",
        "com.kik.chat" to "Kik Messenger",
        "com.yikyak.android" to "YikYak"
    )

    /**
     * Custom apps added by parent via Firestore
     */
    private var customBlockedApps = mutableSetOf<String>()

    // Focus Mode Toggle
    @Volatile
    var isFocusModeEnabled: Boolean = false

    /**
     * Get all blocked packages for the given age group
     */
    fun getBlockedPackages(ageGroup: AgeGroup): Set<String> {
        val blocked = mutableSetOf<String>()

        // System apps - always blocked
        blocked.addAll(systemApps)
        
        // FOCUS MODE OVERRIDE:
        // If Focus Mode is ON, we block ALL Time-Wasting Categories + Adult
        if (isFocusModeEnabled) {
            blocked.addAll(adultApps.keys)
            blocked.addAll(datingApps.keys)
            blocked.addAll(socialMediaApps.keys)
            blocked.addAll(gamingApps.keys) // Also block Games in Focus Mode
            
            // Add custom apps and return early
            blocked.addAll(customBlockedApps)
            return blocked
        }

        // Adult apps - always blocked
        blocked.addAll(adultApps.keys)

        // Dating apps - blocked for child and teen
        if (ageGroup == AgeGroup.CHILD || ageGroup == AgeGroup.TEEN) {
            blocked.addAll(datingApps.keys)
        }

        // Social media - blocked for child
        if (ageGroup == AgeGroup.CHILD) {
            blocked.addAll(socialMediaApps.keys)
        }

        // Custom blocked apps from parent
        blocked.addAll(customBlockedApps)

        return blocked
    }

    /**
     * Check if a package is blocked
     */
    fun isBlocked(packageName: String, ageGroup: AgeGroup): Boolean {
        return getBlockedPackages(ageGroup).contains(packageName)
    }

    /**
     * Get the friendly name for a blocked app
     */
    fun getAppName(packageName: String): String {
        return when {
            packageName == "com.android.settings" -> "Settings"
            packageName == "com.android.vending" -> "Play Store"
            socialMediaApps.containsKey(packageName) -> socialMediaApps[packageName]!!
            gamingApps.containsKey(packageName) -> gamingApps[packageName]!!
            datingApps.containsKey(packageName) -> datingApps[packageName]!!
            adultApps.containsKey(packageName) -> adultApps[packageName]!!
            else -> packageName.substringAfterLast(".")
        }
    }

    /**
     * Get the block reason for an app
     */
    fun getBlockReason(packageName: String, ageGroup: AgeGroup): String {
        return when {
            systemApps.contains(packageName) -> "System protection - prevents app tampering"
            adultApps.containsKey(packageName) -> "Adult content platform"
            datingApps.containsKey(packageName) -> "Dating app - age restricted"
            socialMediaApps.containsKey(packageName) -> "Social media - blocked for ${ageGroup.name} mode"
            customBlockedApps.contains(packageName) -> "Blocked by parent"
            else -> "App is restricted"
        }
    }

    /**
     * Add a custom blocked app (called by parent via UI or Firestore)
     */
    fun addCustomBlockedApp(context: Context, packageName: String) {
        customBlockedApps.add(packageName)
        saveCustomApps(context)
    }

    /**
     * Remove a custom blocked app
     */
    fun removeCustomBlockedApp(context: Context, packageName: String) {
        customBlockedApps.remove(packageName)
        saveCustomApps(context)
    }

    /**
     * Initialize from SharedPreferences and optionally Firestore
     */
    fun init(context: Context) {
        loadCustomApps(context)
    }

    /**
     * Sync blocked apps from Firestore
     */
    suspend fun syncFromFirestore(deviceId: String) {
        try {
            val db = FirebaseFirestore.getInstance()
            val doc = db.collection("devices")
                .document(deviceId)
                .collection("settings")
                .document("blocked_apps")
                .get()
                .await()

            if (doc.exists()) {
                @Suppress("UNCHECKED_CAST")
                val apps = doc.get("packages") as? List<String> ?: emptyList()
                customBlockedApps.clear()
                customBlockedApps.addAll(apps)
            }
        } catch (e: Exception) {
            // Fail silently, use local cache
        }
    }

    private fun saveCustomApps(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_CUSTOM_APPS, customBlockedApps).apply()
    }

    private fun loadCustomApps(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        customBlockedApps = prefs.getStringSet(KEY_CUSTOM_APPS, emptySet())?.toMutableSet() ?: mutableSetOf()
    }
}
