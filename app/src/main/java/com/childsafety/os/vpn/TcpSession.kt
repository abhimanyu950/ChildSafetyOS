package com.childsafety.os.vpn

import java.io.ByteArrayOutputStream

/**
 * Represents a single TCP flow.
 * Accumulates TCP payload for HTTP inspection.
 */
class TcpSession(
    val srcIp: String,
    val srcPort: Int,
    val dstIp: String,
    val dstPort: Int
) {

    private val streamBuffer = ByteArrayOutputStream()

    /**
     * Append raw TCP payload.
     * PacketRouter guarantees only TCP packets reach here.
     */
    fun handlePacket(packet: ByteArray, length: Int) {
        // Skip IP header (minimum 20 bytes)
        if (length <= 20) return

        val payloadOffset = 20
        val payloadLength = length - payloadOffset
        if (payloadLength <= 0) return

        streamBuffer.write(packet, payloadOffset, payloadLength)
    }

    /**
     * Returns accumulated stream data.
     * HTTP parser / image extractor plugs in here.
     */
    fun getStreamBytes(): ByteArray {
        return streamBuffer.toByteArray()
    }

    fun reset() {
        streamBuffer.reset()
    }
}
