package com.example.eardrum_interpretation

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.min
import kotlin.math.roundToInt

// 耳膜分類模型（Ultralytics YOLO 分類模型，轉成 TFLite float16）。
// 輸入 320x320 RGB，輸出 [Abnormal, Normal] 的機率。
// 前處理比照 Ultralytics classify_transforms：短邊縮放、置中裁切、像素值縮到 0~1（不做 mean/std 正規化）。
class EardrumClassifier(context: Context) {

    private val interpreter: Interpreter = Interpreter(loadModelFile(context, MODEL_FILE))
    private val inputSize: Int
    private val inputIsNhwc: Boolean

    data class Prediction(
        val label: String,
        val confidence: Float,
        val abnormalProb: Float,
        val normalProb: Float
    )

    init {
        val shape = interpreter.getInputTensor(0).shape()
        // NHWC 為 [1, H, W, 3]，NCHW 為 [1, 3, H, W]
        inputIsNhwc = shape.size == 4 && shape[3] == 3
        inputSize = if (inputIsNhwc) shape[1] else shape[2]
    }

    fun classify(bitmap: Bitmap): Prediction {
        val input = preprocess(bitmap)
        val output = Array(1) { FloatArray(LABELS.size) }
        interpreter.run(input, output)
        val probs = output[0]
        val bestIdx = if (probs[0] >= probs[1]) 0 else 1
        return Prediction(
            label = LABELS[bestIdx],
            confidence = probs[bestIdx],
            abnormalProb = probs[0],
            normalProb = probs[1]
        )
    }

    private fun preprocess(src: Bitmap): ByteBuffer {
        val scale = inputSize.toFloat() / min(src.width, src.height)
        val scaledW = (src.width * scale).roundToInt()
        val scaledH = (src.height * scale).roundToInt()
        val scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
        val left = ((scaledW - inputSize) / 2).coerceAtLeast(0)
        val top = ((scaledH - inputSize) / 2).coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(scaled, left, top, inputSize, inputSize)

        val buffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputSize * inputSize)
        cropped.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        if (inputIsNhwc) {
            for (p in pixels) {
                buffer.putFloat(((p shr 16) and 0xFF) / 255f)
                buffer.putFloat(((p shr 8) and 0xFF) / 255f)
                buffer.putFloat((p and 0xFF) / 255f)
            }
        } else {
            // NCHW：依序寫入所有 R、G、B 通道
            for (channel in 0..2) {
                val shift = when (channel) { 0 -> 16; 1 -> 8; else -> 0 }
                for (p in pixels) {
                    buffer.putFloat(((p shr shift) and 0xFF) / 255f)
                }
            }
        }
        buffer.rewind()
        return buffer
    }

    private companion object {
        const val MODEL_FILE = "best_float16.tflite"
        val LABELS = listOf("Abnormal", "Normal")

        fun loadModelFile(context: Context, name: String): MappedByteBuffer {
            val fd = context.assets.openFd(name)
            FileInputStream(fd.fileDescriptor).use { input ->
                return input.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }
    }
}
