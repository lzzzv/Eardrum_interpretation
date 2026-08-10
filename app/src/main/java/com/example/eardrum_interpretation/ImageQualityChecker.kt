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

    // Otoscope photos are a bright/colored circle surrounded by a near-black vignette.
    // Calibrated against 9 real otoscope photos (corner 1.4-25.6, center-corner gap 74-161)
    // vs. non-otoscope test images (corner ~158, gap <1).
    private const val CORNER_FRACTION = 0.15
    private const val CENTER_FRACTION = 0.35
    private const val VIGNETTE_CORNER_MAX = 40.0
    private const val VIGNETTE_MIN_CONTRAST = 50.0

    data class Result(
        val isDark: Boolean,
        val isBright: Boolean,
        val isBlurry: Boolean,
        val missingVignette: Boolean,
        val brightness: Double,
        val sharpness: Double
    ) {
        val passed: Boolean get() = !isDark && !isBright && !isBlurry && !missingVignette
    }

    fun check(bitmap: Bitmap): Result {
        val gray = toGrayscale(downscale(bitmap))
        val brightness = gray.pixels.average()
        val sharpness = laplacianVariance(gray)
        val isDark = brightness < DARK_THRESHOLD
        val isBright = brightness > BRIGHT_THRESHOLD
        return Result(
            isDark = isDark,
            isBright = isBright,
            isBlurry = sharpness < BLUR_VARIANCE_THRESHOLD,
            // Vignette shape is meaningless once the whole frame is already blown out dark/bright.
            missingVignette = !isDark && !isBright && !hasOtoscopeVignette(gray),
            brightness = brightness,
            sharpness = sharpness
        )
    }

    /** True if the frame looks like a typical otoscope capture: dark corners, bright center. */
    private fun hasOtoscopeVignette(gray: GrayImage): Boolean {
        val w = gray.width
        val h = gray.height
        val cw = (w * CORNER_FRACTION).toInt().coerceAtLeast(1)
        val ch = (h * CORNER_FRACTION).toInt().coerceAtLeast(1)

        fun regionAvg(x0: Int, y0: Int, x1: Int, y1: Int): Double {
            var sum = 0.0
            var count = 0
            for (y in y0 until y1) {
                for (x in x0 until x1) {
                    sum += gray.pixels[y * w + x]
                    count++
                }
            }
            return if (count > 0) sum / count else 0.0
        }

        val cornerAvg = listOf(
            regionAvg(0, 0, cw, ch),
            regionAvg(w - cw, 0, w, ch),
            regionAvg(0, h - ch, cw, h),
            regionAvg(w - cw, h - ch, w, h)
        ).average()

        val cx0 = ((w - w * CENTER_FRACTION) / 2).toInt()
        val cy0 = ((h - h * CENTER_FRACTION) / 2).toInt()
        val centerAvg = regionAvg(cx0, cy0, w - cx0, h - cy0)

        return cornerAvg < VIGNETTE_CORNER_MAX && (centerAvg - cornerAvg) > VIGNETTE_MIN_CONTRAST
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
