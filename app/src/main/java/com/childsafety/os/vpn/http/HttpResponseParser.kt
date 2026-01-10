package com.childsafety.os.vpn.http

/**
 * Minimal HTTP response parser for VPN stream.
 * Detects image responses and extracts raw image bytes.
 */
object HttpResponseParser {

    fun isHttpImage(stream: ByteArray): Boolean {
        val text = stream.toString(Charsets.ISO_8859_1)
        return text.startsWith("HTTP/")
                && text.contains("Content-Type: image", ignoreCase = true)
    }

    fun extractImageBytes(stream: ByteArray): ByteArray? {
        val text = stream.toString(Charsets.ISO_8859_1)
        val headerEnd = text.indexOf("\r\n\r\n")
        if (headerEnd == -1) return null

        return stream.copyOfRange(headerEnd + 4, stream.size)
    }
}
