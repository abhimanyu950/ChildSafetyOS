package com.childsafety.os.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.childsafety.os.cloud.EventUploader
import com.childsafety.os.ChildSafetyApp
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * VPN Service for network-level content filtering.
 * 
 * Architecture:
 * 1. Creates local VPN interface to intercept all traffic
 * 2. Routes packets through PacketRouter for analysis
 * 3. Uses Cloudflare Family DNS (1.1.1.3) for additional filtering
 * 4. Domain blocking via DomainBlocker/DomainPolicy
 * 
 * Design:
 * - Runs as foreground service with persistent notification
 * - Fail-safe: if VPN crashes, traffic flows normally
 * - Logs blocked domains to Firebase
 */
class SafeVpnService : VpnService() {

    companion object {
        private const val TAG = "SafeVpnService"
        const val ACTION_START = "com.childsafety.os.vpn.START"
        const val ACTION_STOP = "com.childsafety.os.vpn.STOP"
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val isRunning = AtomicBoolean(false)
    private var vpnThread: Thread? = null
    private val packetRouter = PacketRouter()

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "VPN Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                START_NOT_STICKY
            }
            ACTION_START, null -> {
                startVpn()
                START_STICKY
            }
            else -> START_NOT_STICKY
        }
    }

    private fun startVpn() {
        if (isRunning.get()) {
            Log.w(TAG, "VPN already running")
            return
        }

        try {
            // Start as foreground service with notification
            val notification = VpnNotification.build(this)
            startForeground(VpnConstants.NOTIFICATION_ID, notification)

            // Build VPN interface
            vpnInterface = Builder()
                .setSession(VpnConstants.VPN_SESSION_NAME)
                .addAddress(VpnConstants.VPN_ADDRESS, VpnConstants.VPN_PREFIX)
               // .addRoute(VpnConstants.ROUTE_ALL, 0)
                .addDnsServer(VpnConstants.DNS_FAMILY)
                .setBlocking(true)
                .establish()

            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface")
                stopSelf()
                return
            }

            isRunning.set(true)
            
            // Start packet routing thread
            vpnThread = Thread(::runVpnLoop, "VPN-Thread").apply { start() }

            // Log VPN start event
            EventUploader.logAppStart(
                apiLevel = "VPN_START",
                deviceId = ChildSafetyApp.appDeviceId
            )

            Log.i(TAG, "VPN started successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            stopVpn()
        }
    }

    /**
     * Main VPN packet routing loop.
     * Reads packets from TUN interface, processes them, writes back.
     */
    private fun runVpnLoop() {
        val vpnFd = vpnInterface ?: return
        
        val inputStream = FileInputStream(vpnFd.fileDescriptor)
        val outputStream = FileOutputStream(vpnFd.fileDescriptor)
        
        val packetBuffer = ByteBuffer.allocate(32767)
        val packet = ByteArray(32767)

        Log.d(TAG, "VPN loop started")

        while (isRunning.get()) {
            try {
                // Read packet from TUN
                packetBuffer.clear()
                val length = inputStream.read(packet)

                if (length > 0) {
                    // Route packet for analysis
                    packetRouter.route(packet, length)

                    // Write packet back (we don't block at packet level)
                    // Domain blocking is done via DNS and higher-level checks
                    outputStream.write(packet, 0, length)
                }

            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.e(TAG, "VPN loop error", e)
                }
                break
            }
        }

        Log.d(TAG, "VPN loop ended")
    }

    private fun stopVpn() {
        Log.i(TAG, "Stopping VPN...")

        isRunning.set(false)

        // Stop thread
        vpnThread?.interrupt()
        vpnThread = null

        // Close interface
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        vpnInterface = null

        // Clear packet router
        packetRouter.clear()

        // Stop foreground
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        Log.i(TAG, "VPN stopped")
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    override fun onRevoke() {
        Log.i(TAG, "VPN permission revoked")
        stopVpn()
        super.onRevoke()
    }
}
