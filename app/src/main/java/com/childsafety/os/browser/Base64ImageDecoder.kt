package com.childsafety.os.browser

import android.util.Base64
import android.util.Log

object Base64ImageDecoder {

    fun decode(dataUrl: String): ByteArray? {
        return try {
            if (!dataUrl.startsWith("data:image")) return null

            val base64Part = dataUrl.substringAfter(",")
            Base64.decode(base64Part, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.w("Base64Decoder", "Failed to decode base64 image", e)
            null
        }
    }
}
