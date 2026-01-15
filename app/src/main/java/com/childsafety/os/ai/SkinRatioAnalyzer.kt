package com.childsafety.os.ai

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

/**
 * Analyzes skin-colored pixels in an image.
 * 
 * Low skin ratio (< 0.15) is a strong indicator of false positive
 * for NSFW detection - cartoons, landscapes, objects won't have skin.
 * 
 * Uses both RGB and HSV color space thresholds for accuracy.
 */
object SkinRatioAnalyzer {

    /**
     * Analyze the ratio of skin-colored pixels in a bitmap.
     * 
     * @param bitmap The image to analyze
     * @param sampleRate Sample every Nth pixel for performance (default: 4)
     * @return Ratio of skin pixels (0.0 - 1.0)
     */
    fun analyze(bitmap: Bitmap, sampleRate: Int = 4): Float {
        val width = bitmap.width
        val height = bitmap.height
        
        var skinPixels = 0
        var totalPixels = 0
        
        // Sample pixels at intervals for performance
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val pixel = bitmap.getPixel(x, y)
                totalPixels++
                
                if (isSkinColor(pixel)) {
                    skinPixels++
                }
                x += sampleRate
            }
            y += sampleRate
        }
        
        return if (totalPixels > 0) {
            skinPixels.toFloat() / totalPixels.toFloat()
        } else {
            0f
        }
    }
    
    /**
     * Check if a pixel color matches skin tone.
     * Uses combination of RGB and HSV thresholds.
     */
    private fun isSkinColor(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        
        // RGB-based skin detection
        // Skin typically has: R > G > B, with R being dominant
        val rgbMatch = r > 95 && g > 40 && b > 20 &&
                       r > g && r > b &&
                       abs(r - g) > 15 &&
                       r - g > 15 &&
                       r - b > 15
        
        if (!rgbMatch) return false
        
        // HSV-based refinement for better accuracy
        val hsv = FloatArray(3)
        Color.RGBToHSV(r, g, b, hsv)
        
        val hue = hsv[0]
        val saturation = hsv[1]
        val value = hsv[2]
        
        // Skin hue is typically in 0-50 range (red-orange-yellow)
        // with moderate saturation
        return hue <= 50f && 
               saturation >= 0.15f && saturation <= 0.75f &&
               value >= 0.20f
    }
    
    /**
     * Quick check if image is likely to have significant skin.
     * Returns false for very low skin ratios.
     */
    fun hasSignificantSkin(bitmap: Bitmap): Boolean {
        return analyze(bitmap) >= 0.15f
    }
}
