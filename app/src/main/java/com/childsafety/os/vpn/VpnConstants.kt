package com.childsafety.os.vpn

object VpnConstants {

    const val CHANNEL_ID = "child_safety_vpn"
    const val NOTIFICATION_ID = 1001

    const val VPN_SESSION_NAME = "ChildSafetyOS"

    const val VPN_ADDRESS = "10.0.0.2"
    const val VPN_PREFIX = 32

    const val ROUTE_ALL = "0.0.0.0"

    // Cloudflare Family DNS (Malware + Porn)
    const val DNS_FAMILY = "1.1.1.3"
    
    // Cloudflare Security DNS (Malware only)
    const val DNS_SECURITY = "1.1.1.2"
    
    // Cloudflare Standard DNS (Privacy only)
    const val DNS_STANDARD = "1.1.1.1"
}
