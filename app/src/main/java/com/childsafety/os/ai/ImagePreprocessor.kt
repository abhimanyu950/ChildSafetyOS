package com.childsafety.os.ai

import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ImagePreprocessor {

    private const val IMAGE_SIZE = 224
    private const val CHANNELS = 3
    private const val BYTES_PER_FLOAT = 4

    fun preprocess(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(
            bitmap,
            IMAGE_SIZE,
            IMAGE_SIZE,
            true
        )

        val buffer = ByteBuffer.allocateDirect(
            1 * IMAGE_SIZE * IMAGE_SIZE * CHANNELS * BYTES_PER_FLOAT
        )
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE)
        resized.getPixels(
            pixels,
            0,
            IMAGE_SIZE,
            0,
            0,
            IMAGE_SIZE,
            IMAGE_SIZE
        )

        var index = 0
        for (y in 0 until IMAGE_SIZE) {
            for (x in 0 until IMAGE_SIZE) {
                val pixel = pixels[index++]

                buffer.putFloat(((pixel shr 16) and 0xFF) / 255f) // R
                buffer.putFloat(((pixel shr 8) and 0xFF) / 255f)  // G
                buffer.putFloat((pixel and 0xFF) / 255f)          // B
            }
        }

        buffer.rewind()
        return buffer
    }
}