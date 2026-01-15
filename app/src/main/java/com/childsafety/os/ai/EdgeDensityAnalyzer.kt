package com.childsafety.os.ai

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Analyzes edge density in an image using Sobel edge detection.
 * 
 * High edge density (> 0.60) indicates cartoon/drawing style,
 * which helps prevent false positives on anime and cartoons.
 * 
 * Real photographs typically have lower edge density than
 * drawings which have bold outlines and sharp color transitions.
 */
object EdgeDensityAnalyzer {

    // Sobel kernels for edge detection
    private val SOBEL_X = arrayOf(
        intArrayOf(-1, 0, 1),
        intArrayOf(-2, 0, 2),
        intArrayOf(-1, 0, 1)
    )
    
    private val SOBEL_Y = arrayOf(
        intArrayOf(-1, -2, -1),
        intArrayOf(0, 0, 0),
        intArrayOf(1, 2, 1)
    )
    
    // Edge threshold - pixels with gradient above this are considered edges
    private const val EDGE_THRESHOLD = 50

    /**
     * Analyze edge density of a bitmap.
     * 
     * @param bitmap The image to analyze
     * @param sampleRate Sample every Nth pixel for performance (default: 3)
     * @return Edge density ratio (0.0 - 1.0)
     */
    fun analyze(bitmap: Bitmap, sampleRate: Int = 3): Float {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width < 3 || height < 3) return 0f
        
        // Convert to grayscale array for faster processing
        val grayscale = Array(height) { IntArray(width) }
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                // Luminance formula: 0.299*R + 0.587*G + 0.114*B
                grayscale[y][x] = (0.299 * Color.red(pixel) + 
                                   0.587 * Color.green(pixel) + 
                                   0.114 * Color.blue(pixel)).toInt()
            }
        }
        
        var edgePixels = 0
        var totalPixels = 0
        
        // Apply Sobel filter (skip borders)
        var y = 1
        while (y < height - 1) {
            var x = 1
            while (x < width - 1) {
                totalPixels++
                
                // Calculate Sobel gradients
                var gx = 0
                var gy = 0
                
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val pixelValue = grayscale[y + ky][x + kx]
                        gx += SOBEL_X[ky + 1][kx + 1] * pixelValue
                        gy += SOBEL_Y[ky + 1][kx + 1] * pixelValue
                    }
                }
                
                // Gradient magnitude
                val magnitude = sqrt((gx * gx + gy * gy).toDouble())
                
                if (magnitude > EDGE_THRESHOLD) {
                    edgePixels++
                }
                
                x += sampleRate
            }
            y += sampleRate
        }
        
        return if (totalPixels > 0) {
            edgePixels.toFloat() / totalPixels.toFloat()
        } else {
            0f
        }
    }
    
    /**
     * Quick check if image appears to be a drawing/cartoon.
     */
    fun isLikelyDrawing(bitmap: Bitmap): Boolean {
        return analyze(bitmap) > 0.60f
    }
    
    /**
     * Quick check for very high edge density (comic/illustration style).
     */
    fun isHighEdgeDensity(bitmap: Bitmap): Boolean {
        return analyze(bitmap) > 0.45f
    }
}
