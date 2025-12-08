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
    val label: String
)

class YoloDetector(
    context: Context,
    modelName: String = "best.tflite",
    private val confThreshold: Float = 0.5f,
    private val iouThreshold: Float = 0.45f
) {
    private var interpreter: Interpreter
    private val inputSize = 640

    private val labels = listOf(
        "Blast",
        "Hama Putih Palsu",
        "Hawar Daun Bakteri",
        "Stem Borer"
    )

    init {
        val model = FileUtil.loadMappedFile(context, modelName)
        interpreter = Interpreter(model, Interpreter.Options().apply { setNumThreads(4) })
    }

    fun detect(bitmap: Bitmap): List<DetectionResult> {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val input = bitmapToBGRFloat(resized)

        val output = Array(1) { Array(8) { FloatArray(8400) } }

        interpreter.run(input, output)

        val results = parseOutput(output[0])

        Log.e("YOLO-DETECT", "TOTAL: ${results.size}")

        return applyNMS(results)
    }

    private fun bitmapToBGRFloat(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (px in pixels) {
            val r = (px shr 16 and 0xFF)
            val g = (px shr 8 and 0xFF)
            val b = (px and 0xFF)

            buffer.putFloat(b / 255f)
            buffer.putFloat(g / 255f)
            buffer.putFloat(r / 255f)
        }
        return buffer
    }

    private fun parseOutput(out: Array<FloatArray>): List<DetectionResult> {
        val list = ArrayList<DetectionResult>()

        for (i in 0 until 8400) {
            val obj = out[4][i]
            if (obj < confThreshold) continue

            // ❗ MODEL KAMU HANYA PUNYA 3 CLASS SCORE
            val clsScores = floatArrayOf(out[5][i], out[6][i], out[7][i])
            var best = 0
            var bestScore = 0f

            for (c in clsScores.indices) {
                val s = clsScores[c] * obj
                if (s > bestScore) {
                    bestScore = s
                    best = c
                }
            }

            if (bestScore < confThreshold) continue

            val cx = out[0][i]
            val cy = out[1][i]
            val w = out[2][i]
            val h = out[3][i]

            val x1 = (cx - w / 2).coerceIn(0f, inputSize.toFloat())
            val y1 = (cy - h / 2).coerceIn(0f, inputSize.toFloat())
            val x2 = (cx + w / 2).coerceIn(0f, inputSize.toFloat())
            val y2 = (cy + h / 2).coerceIn(0f, inputSize.toFloat())

            val box = RectF(
                x1 / inputSize,
                y1 / inputSize,
                x2 / inputSize,
                y2 / inputSize
            )

            Log.e("YOLO-BOX", "($bestScore) → ${labels[best]}  BOX=$box")

            list.add(
                DetectionResult(
                    box = box,
                    score = bestScore,
                    label = labels[best]
                )
            )
        }

        return list
    }

    private fun applyNMS(dets: List<DetectionResult>): List<DetectionResult> {
        if (dets.isEmpty()) return emptyList()

        val sorted = dets.sortedByDescending { it.score }.toMutableList()
        val final = mutableListOf<DetectionResult>()

        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            final.add(best)

            val iterator = sorted.iterator()
            while (iterator.hasNext()) {
                val other = iterator.next()
                if (iou(best.box, other.box) > iouThreshold) iterator.remove()
            }
        }
        return final
    }

    private fun iou(a: RectF, b: RectF): Float {
        val xA = max(a.left, b.left)
        val yA = max(a.top, b.top)
        val xB = min(a.right, b.right)
        val yB = min(a.bottom, b.bottom)

        val inter = max(0f, xB - xA) * max(0f, yB - yA)
        val union = a.width() * a.height() + b.width() * b.height() - inter

        return if (union <= 0) 0f else inter / union
    }

    fun close() = interpreter.close()
}