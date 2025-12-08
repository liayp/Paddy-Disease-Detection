package amalia.skripsi.deteksipadi.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

data class DetectionResult(
    val box: RectF,
    val score: Float,
    val label: String,
    val labelIndex: Int
)

class YoloDetector(
    context: Context,
    modelName: String = "best.tflite",
    // Kembalikan threshold ke 0.50f atau 0.40f agar deteksi sampah hilang
    private val confThreshold: Float = 0.40f,
    private val iouThreshold: Float = 0.45f
) {
    private var interpreter: Interpreter? = null
    private val inputSize = 640

    @Volatile
    private var isClosed = false

    private val labels = listOf("Blast", "Hama Putih Palsu", "Hawar Daun Bakteri", "Stem Borer")

    init {
        try {
            val model = FileUtil.loadMappedFile(context, modelName)
            val options = Interpreter.Options().apply { setNumThreads(4) }
            interpreter = Interpreter(model, options)
            Log.i("YoloDetector", "Model loaded successfully")
        } catch (e: Exception) {
            Log.e("YoloDetector", "Error loading model", e)
        }
    }

    fun detect(bitmap: Bitmap): List<DetectionResult> {
        synchronized(this) {
            if (isClosed || interpreter == null) return emptyList()
        }

        return try {
            val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
            val input = bitmapToByteBuffer(resized)

            // Output shape YOLOv8 [1, 8, 8400]
            val output = Array(1) { Array(8) { FloatArray(8400) } }

            synchronized(this) {
                if (isClosed || interpreter == null) return emptyList()
                interpreter?.run(input, output)
            }

            val results = parseOutputYoloV8(output[0])
            return applyNMS(results)
        } catch (e: Exception) {
            Log.e("YoloDetector", "Error detect", e)
            emptyList()
        }
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        buffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(inputSize * inputSize)
        bitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixelValue in intValues) {
            // Normalisasi 0-255 -> 0.0-1.0
            buffer.putFloat(((pixelValue shr 16 and 0xFF) / 255.0f))
            buffer.putFloat(((pixelValue shr 8 and 0xFF) / 255.0f))
            buffer.putFloat(((pixelValue and 0xFF) / 255.0f))
        }
        return buffer
    }

    private fun parseOutputYoloV8(output: Array<FloatArray>): List<DetectionResult> {
        val detections = ArrayList<DetectionResult>()
        val numAnchors = 8400
        val numClasses = 4

        for (i in 0 until numAnchors) {
            var maxScore = 0f
            var bestClassIdx = -1

            // Cari kelas terbaik
            for (c in 0 until numClasses) {
                val score = output[4 + c][i]
                if (score > maxScore) {
                    maxScore = score
                    bestClassIdx = c
                }
            }

            if (maxScore < confThreshold) continue

            // --- PERBAIKAN DI SINI ---
            // Output model Anda ternyata SUDAH 0.0 - 1.0 (Relatif)
            // JADI JANGAN DIBAGI DENGAN inputSize LAGI!

            val cx = output[0][i] // Contoh: 0.5 (Tengah)
            val cy = output[1][i]
            val w = output[2][i]
            val h = output[3][i]

            val left = (cx - w / 2).coerceIn(0f, 1f)
            val top = (cy - h / 2).coerceIn(0f, 1f)
            val right = (cx + w / 2).coerceIn(0f, 1f)
            val bottom = (cy + h / 2).coerceIn(0f, 1f)

            detections.add(
                DetectionResult(
                    box = RectF(left, top, right, bottom),
                    score = maxScore,
                    label = labels.getOrElse(bestClassIdx) { "Unknown" },
                    labelIndex = bestClassIdx
                )
            )
        }
        return detections
    }

    private fun applyNMS(list: List<DetectionResult>): List<DetectionResult> {
        if (list.isEmpty()) return emptyList()

        val sorted = list.sortedByDescending { it.score }.toMutableList()
        val nmsList = ArrayList<DetectionResult>()

        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            nmsList.add(best)

            val iterator = sorted.iterator()
            while (iterator.hasNext()) {
                val other = iterator.next()
                if (calculateIoU(best.box, other.box) > iouThreshold) {
                    iterator.remove()
                }
            }
        }
        return nmsList
    }

    private fun calculateIoU(a: RectF, b: RectF): Float {
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        val intersectionLeft = maxOf(a.left, b.left)
        val intersectionTop = maxOf(a.top, b.top)
        val intersectionRight = minOf(a.right, b.right)
        val intersectionBottom = minOf(a.bottom, b.bottom)
        val intersectionArea = maxOf(0f, intersectionRight - intersectionLeft) * maxOf(0f, intersectionBottom - intersectionTop)
        val unionArea = areaA + areaB - intersectionArea
        return if (unionArea <= 0) 0f else intersectionArea / unionArea
    }

    fun close() {
        synchronized(this) {
            if (!isClosed) {
                isClosed = true
                interpreter?.close()
                interpreter = null
            }
        }
    }
}