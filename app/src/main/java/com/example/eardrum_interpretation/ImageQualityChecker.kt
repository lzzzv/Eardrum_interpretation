package com.example.eardrum_interpretation

import android.graphics.Bitmap
import kotlin.math.max

/**
 * Lightweight, model-free image quality gate: catches obviously unusable photos
 * (too dark / too bright / too blurry) before they reach the classifier.
 * Thresholds are heuristic starting points and will likely need tuning against
 * real eardrum photos.
 */
object ImageQualityChecker {

    private const val ANALYSIS_MAX_DIMENSION = 200
    private const val DARK_THRESHOLD = 60.0
    private const val BRIGHT_THRESHOLD = 200.0
    private const val BLUR_VARIANCE_THRESHOLD = 45.0

    data class Result(
        val isDark: Boolean,
        val isBright: Boolean,
        val isBlurry: Boolean,
        val brightness: Double,
        val sharpness: Double
    ) {
        val passed: Boolean get() = !isDark && !isBright && !isBlurry
    }

    fun check(bitmap: Bitmap): Result {
        val gray = toGrayscale(downscale(bitmap))
        val brightness = gray.pixels.average()
        val sharpness = laplacianVariance(gray)
        return Result(
            isDark = brightness < DARK_THRESHOLD,
            isBright = brightness > BRIGHT_THRESHOLD,
            isBlurry = sharpness < BLUR_VARIANCE_THRESHOLD,
            brightness = brightness,
            sharpness = sharpness
        )
    }

    private fun downscale(bitmap: Bitmap): Bitmap {
        val longSide = max(bitmap.width, bitmap.height)
        if (longSide <= ANALYSIS_MAX_DIMENSION) return bitmap
        val scale = ANALYSIS_MAX_DIMENSION.toFloat() / longSide
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private class GrayImage(val width: Int, val height: Int, val pixels: DoubleArray)

    private fun toGrayscale(bitmap: Bitmap): GrayImage {
        val w = bitmap.width
        val h = bitmap.height
        val argb = IntArray(w * h)
        bitmap.getPixels(argb, 0, w, 0, 0, w, h)
        val gray = DoubleArray(w * h)
        for (i in argb.indices) {
            val p = argb[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = 0.299 * r + 0.587 * g + 0.114 * b
        }
        return GrayImage(w, h, gray)
    }

    /** Variance of the Laplacian response; low variance means few sharp edges, i.e. blur. */
    private fun laplacianVariance(image: GrayImage): Double {
        val w = image.width
        val h = image.height
        if (w < 3 || h < 3) return Double.MAX_VALUE
        val responses = DoubleArray((w - 2) * (h - 2))
        var idx = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val center = image.pixels[y * w + x]
                val up = image.pixels[(y - 1) * w + x]
                val down = image.pixels[(y + 1) * w + x]
                val left = image.pixels[y * w + (x - 1)]
                val right = image.pixels[y * w + (x + 1)]
                responses[idx++] = up + down + left + right - 4 * center
            }
        }
        val mean = responses.average()
        var variance = 0.0
        for (v in responses) variance += (v - mean) * (v - mean)
        return variance / responses.size
    }
}
