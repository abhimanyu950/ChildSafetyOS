package com.childsafety.os.cloud.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

/**
 * Device status and configuration.
 * Maps to Firestore: devices/{deviceId}
 */
data class DeviceStatus(
    val deviceId: String = "",
    val deviceName: String = "",
    val childName: String = "",
    @ServerTimestamp
    val lastSeen: Timestamp? = null,
    val vpnEnabled: Boolean = false,
    val adminProtectionEnabled: Boolean = false,
    val settingsLockEnabled: Boolean = false,
    val ageGroup: String = "CHILD",
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
) {
    constructor() : this(deviceId = "")
}
