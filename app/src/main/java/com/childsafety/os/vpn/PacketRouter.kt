package com.childsafety.os.vpn

import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * Routes IPv4 TCP packets into TCP sessions.
 * HTTP / image enforcement plugs in AFTER reassembly.
 */
class PacketRouter {

    private val tcpSessions = ConcurrentHashMap<String, TcpSession>()

    fun route(packet: ByteArray, length: Int) {

        if (length < 20) return

        val buffer = ByteBuffer.wrap(packet, 0, length)

        // IPv4 check
        val version = (buffer.get(0).toInt() shr 4) and 0xF
        if (version != 4) return

        // Protocol check (TCP = 6)
        val protocol = buffer.get(9).toInt() and 0xFF
        if (protocol != 6) return

        // Minimal session key (single-flow safe baseline)
        val sessionKey = "tcp-session"

        val session = tcpSessions.getOrPut(sessionKey) {
            TcpSession(
                srcIp = "0.0.0.0",
                srcPort = 0,
                dstIp = "0.0.0.0",
                dstPort = 0
            )
        }

        session.handlePacket(packet, length)
    }

    fun clear() {
        tcpSessions.clear()
    }
}
