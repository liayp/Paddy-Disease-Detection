package amalia.skripsi.deteksipadi.benchmark

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

data class BenchmarkDetection(
    val score: Float,
    val label: String,
    val labelIndex: Int
)

data class BenchmarkInferenceResult(
    val detections: List<BenchmarkDetection>,
    val preprocessingMs: Double,
    val inferenceMs: Double,
    val postprocessingMs: Double,
    val totalMs: Double
)

class BenchmarkYoloDetector(
    private val context: Context,
    private val modelName: String,
    private val confThreshold: Float = 0.40f,
    private val numThreads: Int = 4
) : AutoCloseable {

    companion object {
        private const val TAG = "MODEL_BENCHMARK"
        private const val INPUT_SIZE = 640
        private const val NUM_CLASSES = 4
        private const val NUM_FEATURES = 4 + NUM_CLASSES

        private val LABELS = listOf(
            "Blast",
            "Hama Putih Palsu",
            "Hawar Daun Bakteri",
            "Stem Borer"
        )
    }

    private val interpreter: Interpreter
    private lateinit var inputTensor: org.tensorflow.lite.Tensor
    private lateinit var outputTensor: org.tensorflow.lite.Tensor

    private val inputType: DataType
    private val outputType: DataType
    private val inputScale: Float
    private val inputZeroPoint: Int
    private val outputScale: Float
    private val outputZeroPoint: Int
    private val inputShape: IntArray
    private val outputShape: IntArray
    private val isNchw: Boolean

    init {
        val model = FileUtil.loadMappedFile(context, modelName)

        interpreter = Interpreter(model, Interpreter.Options().apply {
            setNumThreads(numThreads)
        })

        inputTensor = interpreter.getInputTensor(0)
        outputTensor = interpreter.getOutputTensor(0)

        inputType = inputTensor.dataType()
        outputType = outputTensor.dataType()
        inputScale = inputTensor.quantizationParams().scale
        inputZeroPoint = inputTensor.quantizationParams().zeroPoint
        outputScale = outputTensor.quantizationParams().scale
        outputZeroPoint = outputTensor.quantizationParams().zeroPoint
        inputShape = inputTensor.shape()
        outputShape = outputTensor.shape()
        isNchw = detectNchwLayout(inputShape)

        validateModel()
        logModelInfo()
    }

    private fun detectNchwLayout(shape: IntArray): Boolean {
        require(shape.size == 4) {
            "Input tensor harus 4D: ${shape.contentToString()}"
        }

        return when {
            shape.contentEquals(intArrayOf(1, 3, INPUT_SIZE, INPUT_SIZE)) -> true
            shape.contentEquals(intArrayOf(1, INPUT_SIZE, INPUT_SIZE, 3)) -> false
            else -> error("Input shape tidak didukung: ${shape.contentToString()}")
        }
    }

    private fun validateModel() {
        require(outputShape.size == 3) {
            "Output tensor harus 3D: ${outputShape.contentToString()}"
        }

        val validOutput = (outputShape[1] == NUM_FEATURES && outputShape[2] == 8400) ||
                (outputShape[2] == NUM_FEATURES && outputShape[1] == 8400)

        require(validOutput) {
            "Output tidak cocok dengan YOLO 4 kelas: ${outputShape.contentToString()}"
        }

        if (inputType == DataType.INT8) {
            require(inputScale > 0f) { "Input INT8 memiliki scale tidak valid: $inputScale" }
        }

        if (outputType == DataType.INT8) {
            require(outputScale > 0f) { "Output INT8 memiliki scale tidak valid: $outputScale" }
        }
    }

    private fun logModelInfo() {
        Log.i(TAG, "==============================================")
        Log.i(TAG, "MODEL: $modelName")
        Log.i(TAG, "Input type: $inputType")
        Log.i(TAG, "Input shape: ${inputShape.contentToString()}")
        Log.i(TAG, "Input layout: ${if (isNchw) "NCHW" else "NHWC"}")
        Log.i(TAG, "Input bytes: ${inputTensor.numBytes()}")
        Log.i(TAG, "Input scale: $inputScale")
        Log.i(TAG, "Input zero point: $inputZeroPoint")
        Log.i(TAG, "Output type: $outputType")
        Log.i(TAG, "Output shape: ${outputShape.contentToString()}")
        Log.i(TAG, "Output bytes: ${outputTensor.numBytes()}")
        Log.i(TAG, "Output scale: $outputScale")
        Log.i(TAG, "Output zero point: $outputZeroPoint")
        Log.i(TAG, "Confidence threshold: $confThreshold")
        Log.i(TAG, "Threads: $numThreads")
        Log.i(TAG, "==============================================")
    }

    fun detect(bitmap: Bitmap): BenchmarkInferenceResult {
        val totalStart = System.nanoTime()

        val preprocessingStart = System.nanoTime()
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val inputBuffer = createInputBuffer(resized)
        val preprocessingMs = elapsedMs(preprocessingStart)

        val outputBuffer = ByteBuffer.allocateDirect(outputTensor.numBytes())
            .order(ByteOrder.nativeOrder())

        val inferenceStart = System.nanoTime()
        interpreter.run(inputBuffer, outputBuffer)
        val inferenceMs = elapsedMs(inferenceStart)

        val postprocessingStart = System.nanoTime()
        outputBuffer.rewind()

        val outputValues = readOutput(outputBuffer)
        logOutputStatistics(outputValues)

        val detections = parseOutput(outputValues)
        val postprocessingMs = elapsedMs(postprocessingStart)
        val totalMs = elapsedMs(totalStart)

        if (resized !== bitmap) resized.recycle()

        return BenchmarkInferenceResult(
            detections = detections,
            preprocessingMs = preprocessingMs,
            inferenceMs = inferenceMs,
            postprocessingMs = postprocessingMs,
            totalMs = totalMs
        )
    }

    private fun createInputBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(inputTensor.numBytes())
            .order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)

        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        if (isNchw) {
            for (pixel in pixels) putInputValue(buffer, ((pixel shr 16) and 0xFF) / 255f)
            for (pixel in pixels) putInputValue(buffer, ((pixel shr 8) and 0xFF) / 255f)
            for (pixel in pixels) putInputValue(buffer, (pixel and 0xFF) / 255f)
        } else {
            for (pixel in pixels) {
                putInputValue(buffer, ((pixel shr 16) and 0xFF) / 255f)
                putInputValue(buffer, ((pixel shr 8) and 0xFF) / 255f)
                putInputValue(buffer, (pixel and 0xFF) / 255f)
            }
        }

        buffer.rewind()
        return buffer
    }

    private fun putInputValue(buffer: ByteBuffer, realValue: Float) {
        when (inputType) {
            DataType.FLOAT32 -> buffer.putFloat(realValue)

            DataType.INT8 -> {
                val value = (realValue / inputScale + inputZeroPoint)
                    .roundToInt()
                    .coerceIn(-128, 127)
                buffer.put(value.toByte())
            }

            DataType.UINT8 -> {
                val value = (realValue / inputScale + inputZeroPoint)
                    .roundToInt()
                    .coerceIn(0, 255)
                buffer.put(value.toByte())
            }

            else -> error("Input datatype tidak didukung: $inputType")
        }
    }

    private fun readOutput(buffer: ByteBuffer): FloatArray {
        val count = outputTensor.numElements()

        return when (outputType) {
            DataType.FLOAT32 -> {
                val floatBuffer = buffer.asFloatBuffer()
                FloatArray(count) { floatBuffer.get() }
            }

            DataType.INT8 -> {
                FloatArray(count) {
                    val value = buffer.get().toInt()
                    (value - outputZeroPoint) * outputScale
                }
            }

            DataType.UINT8 -> {
                FloatArray(count) {
                    val value = buffer.get().toInt() and 0xFF
                    (value - outputZeroPoint) * outputScale
                }
            }

            else -> error("Output datatype tidak didukung: $outputType")
        }
    }

    private fun parseOutput(output: FloatArray): List<BenchmarkDetection> {
        val channelsFirst = outputShape[1] == NUM_FEATURES
        val anchors = if (channelsFirst) outputShape[2] else outputShape[1]
        val detections = ArrayList<BenchmarkDetection>()

        for (anchorIndex in 0 until anchors) {
            var maxScore = 0f
            var bestClassIndex = -1

            for (classIndex in 0 until NUM_CLASSES) {
                val score = getOutputValue(
                    output = output,
                    featureIndex = 4 + classIndex,
                    anchorIndex = anchorIndex,
                    channelsFirst = channelsFirst
                )

                if (score > maxScore) {
                    maxScore = score
                    bestClassIndex = classIndex
                }
            }

            if (bestClassIndex < 0 || maxScore < confThreshold) continue

            detections.add(
                BenchmarkDetection(
                    score = maxScore,
                    label = LABELS[bestClassIndex],
                    labelIndex = bestClassIndex
                )
            )
        }

        return detections.sortedByDescending { it.score }.take(20)
    }

    private fun getOutputValue(
        output: FloatArray,
        featureIndex: Int,
        anchorIndex: Int,
        channelsFirst: Boolean
    ): Float {
        return if (channelsFirst) {
            output[featureIndex * outputShape[2] + anchorIndex]
        } else {
            output[anchorIndex * outputShape[2] + featureIndex]
        }
    }

    private fun logOutputStatistics(output: FloatArray) {
        if (output.isEmpty()) return

        var min = Float.POSITIVE_INFINITY
        var max = Float.NEGATIVE_INFINITY
        var sum = 0.0

        for (value in output) {
            if (value < min) min = value
            if (value > max) max = value
            sum += value
        }

        Log.i(
            TAG,
            "OUTPUT RANGE | min=$min | max=$max | avg=${sum / output.size}"
        )

        val preview = output.take(20).joinToString(", ") { "%.7f".format(it) }
        Log.i(TAG, "OUTPUT FIRST 20 | $preview")
    }

    fun topConfidence(result: BenchmarkInferenceResult): Float? {
        return result.detections.maxByOrNull { it.score }?.score
    }

    override fun close() {
        interpreter.close()
    }

    private fun elapsedMs(startNs: Long): Double {
        return (System.nanoTime() - startNs) / 1_000_000.0
    }
}