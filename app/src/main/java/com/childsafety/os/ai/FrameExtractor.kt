package com.childsafety.os.ai

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log

/**
 * Extracts representative frames from a video for ML inference.
 *
 * Strategy:
 * - Sample frames at fixed intervals
 * - Limit total frames to avoid CPU / memory abuse
 * - Downscale frames to model input size
 */
object FrameExtractor {

    private const val TAG = "FrameExtractor"

    // Target size should match ImagePreprocessor expectations
    private const val TARGET_WIDTH = 224
    private const val TARGET_HEIGHT = 224

    /**
     * Extracts frames from a video.
     *
     * @param intervalMs interval between frames (default: 1s)
     * @param maxFrames maximum frames to extract
     */
    fun extractFrames(
        context: Context,
        videoUri: Uri,
        intervalMs: Long = 1_000L,
        maxFrames: Int = 10
    ): List<Bitmap> {

        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<Bitmap>()

        try {
            retriever.setDataSource(context, videoUri)

            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: return emptyList()

            if (durationMs <= 0) return emptyList()

            var timestampMs = 0L

            while (timestampMs < durationMs && frames.size < maxFrames) {

                // MediaMetadataRetriever expects time in MICROSECONDS
                val frame = retriever.getFrameAtTime(
                    timestampMs * 1_000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )

                if (frame != null) {
                    // Downscale immediately to control memory usage
                    val scaled = Bitmap.createScaledBitmap(
                        frame,
                        TARGET_WIDTH,
                        TARGET_HEIGHT,
                        true
                    )
                    frames.add(scaled)

                    // Free the original frame ASAP
                    if (scaled != frame) {
                        frame.recycle()
                    }
                }

                timestampMs += intervalMs
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract frames", e)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        Log.d(TAG, "Extracted ${frames.size} frames from video")
        return frames
    }
}
