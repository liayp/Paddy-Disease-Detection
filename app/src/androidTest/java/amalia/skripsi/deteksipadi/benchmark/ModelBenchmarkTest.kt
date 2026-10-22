package amalia.skripsi.deteksipadi.benchmark

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class ModelBenchmarkTest {

    companion object {
        private const val TAG = "MODEL_BENCHMARK"

        private const val FLOAT_MODEL = "best_float32.tflite"
        private const val INT8_MODEL = "best_int8.tflite"

        private const val WARMUP_RUNS = 5
        private const val MEASURED_RUNS = 10

        private const val DATASET_ROOT = "benchmark"

        private val CLASS_FOLDERS = listOf(
            "Blast",
            "Hama_Putih_Palsu",
            "Hawar_Daun_Bakteri",
            "Stem_Borer"
        )
    }

    private val instrumentation =
        InstrumentationRegistry.getInstrumentation()

    // Context aplikasi utama: untuk membaca model di src/main/assets
    private val targetContext =
        instrumentation.targetContext

    // Context instrumentation: untuk membaca dataset di
    // src/androidTest/assets
    private val testContext =
        instrumentation.context

    @Test
    fun benchmarkFloat32() {
        runBenchmark(
            modelName = FLOAT_MODEL,
            modelLabel = "FLOAT32"
        )
    }

    @Test
    fun benchmarkInt8() {
        runBenchmark(
            modelName = INT8_MODEL,
            modelLabel = "INT8"
        )
    }

    private fun runBenchmark(
        modelName: String,
        modelLabel: String
    ) {
        Log.i(TAG, "")
        Log.i(TAG, "================================================")
        Log.i(TAG, "START BENCHMARK $modelLabel")
        Log.i(TAG, "MODEL = $modelName")
        Log.i(TAG, "================================================")

        val dataset = loadDataset()

        require(dataset.size == 40) {
            "Dataset harus berjumlah tepat 40 gambar. " +
                    "Saat ini ditemukan ${dataset.size} gambar."
        }

        Log.i(
            TAG,
            "Jumlah dataset: ${dataset.size} gambar"
        )

        /*
         * ============================================================
         * 1. INIT MODEL
         * ============================================================
         *
         * Init harus dilakukan sebelum inference apa pun.
         */
        val initStart = System.nanoTime()

        val detector = BenchmarkYoloDetector(
            context = targetContext,
            modelName = modelName
        )

        val initTimeMs =
            (System.nanoTime() - initStart) / 1_000_000.0

        Log.i(
            TAG,
            "INIT TIME | model=$modelLabel | " +
                    "${format(initTimeMs)} ms"
        )

        try {
            /*
             * ========================================================
             * 2. PERFORMANCE BENCHMARK
             * ========================================================
             *
             * Harus dijalankan sebelum detection evaluation 40 gambar,
             * supaya First Inference benar-benar first inference.
             */
            runInferenceBenchmark(
                detector = detector,
                dataset = dataset,
                modelLabel = modelLabel
            )

            /*
             * ========================================================
             * 3. DETECTION EVALUATION
             * ========================================================
             *
             * Dilakukan setelah timing benchmark selesai.
             */
            runDetectionEvaluation(
                detector = detector,
                dataset = dataset,
                modelLabel = modelLabel
            )
        } finally {
            detector.close()
        }

        Log.i(TAG, "================================================")
        Log.i(TAG, "END BENCHMARK $modelLabel")
        Log.i(TAG, "================================================")
    }

    private fun runInferenceBenchmark(
        detector: BenchmarkYoloDetector,
        dataset: List<TestImage>,
        modelLabel: String
    ) {
        Log.i(TAG, "")
        Log.i(
            TAG,
            "---------------- INFERENCE BENCHMARK ----------------"
        )

        require(dataset.isNotEmpty()) {
            "Dataset benchmark kosong."
        }

        /*
         * ============================================================
         * FIRST INFERENCE
         * ============================================================
         *
         * Ini adalah inference pertama setelah Interpreter selesai
         * dibuat. Jangan menjalankan detection lain sebelum titik ini.
         */
        val firstImage = decodeBitmap(
            dataset.first().path
        )

        val firstResult: BenchmarkInferenceResult

        try {
            firstResult = detector.detect(firstImage)

            Log.i(
                TAG,
                "FIRST INFERENCE | model=$modelLabel | " +
                        "preprocess=${format(firstResult.preprocessingMs)} ms | " +
                        "inference=${format(firstResult.inferenceMs)} ms | " +
                        "postprocess=${format(firstResult.postprocessingMs)} ms | " +
                        "total=${format(firstResult.totalMs)} ms"
            )
        } finally {
            firstImage.recycle()
        }

        /*
         * ============================================================
         * WARM-UP
         * ============================================================
         */
        val warmupTimes = ArrayList<Double>()

        Log.i(
            TAG,
            "Melakukan $WARMUP_RUNS warm-up runs..."
        )

        repeat(WARMUP_RUNS) { index ->
            val image = decodeBitmap(
                dataset[index % dataset.size].path
            )

            try {
                val result = detector.detect(image)

                warmupTimes.add(
                    result.inferenceMs
                )

                Log.i(
                    TAG,
                    "WARMUP #${index + 1} | " +
                            "inference=${format(result.inferenceMs)} ms"
                )
            } finally {
                image.recycle()
            }
        }

        /*
         * ============================================================
         * MEASURED INFERENCE
         * ============================================================
         */
        val inferenceTimes = ArrayList<Double>()
        val preprocessingTimes = ArrayList<Double>()
        val postprocessingTimes = ArrayList<Double>()
        val totalTimes = ArrayList<Double>()

        Log.i(
            TAG,
            "Melakukan $MEASURED_RUNS measured runs..."
        )

        repeat(MEASURED_RUNS) { index ->
            val image = decodeBitmap(
                dataset[index % dataset.size].path
            )

            try {
                val result = detector.detect(image)

                inferenceTimes.add(
                    result.inferenceMs
                )

                preprocessingTimes.add(
                    result.preprocessingMs
                )

                postprocessingTimes.add(
                    result.postprocessingMs
                )

                totalTimes.add(
                    result.totalMs
                )

                Log.i(
                    TAG,
                    "MEASURED #${index + 1} | " +
                            "preprocess=${format(result.preprocessingMs)} ms | " +
                            "inference=${format(result.inferenceMs)} ms | " +
                            "postprocess=${format(result.postprocessingMs)} ms | " +
                            "total=${format(result.totalMs)} ms"
                )
            } finally {
                image.recycle()
            }
        }

        /*
         * ============================================================
         * SUMMARY PERFORMANCE
         * ============================================================
         */
        Log.i(TAG, "")
        Log.i(
            TAG,
            "================ PERFORMANCE SUMMARY $modelLabel ================"
        )

        Log.i(
            TAG,
            "Init Time       : ${format(0.0)} ms " +
                    "(dicatat pada tahap runBenchmark)"
        )

        Log.i(
            TAG,
            "First Inference : ${format(firstResult.inferenceMs)} ms"
        )

        Log.i(
            TAG,
            "Warm-up Avg     : ${format(warmupTimes.average())} ms"
        )

        Log.i(
            TAG,
            "Preprocess Avg  : ${format(preprocessingTimes.average())} ms"
        )

        Log.i(
            TAG,
            "Inference Avg   : ${format(inferenceTimes.average())} ms"
        )

        Log.i(
            TAG,
            "Postprocess Avg : ${format(postprocessingTimes.average())} ms"
        )

        Log.i(
            TAG,
            "Total Avg       : ${format(totalTimes.average())} ms"
        )

        Log.i(
            TAG,
            "Inference Min   : ${format(inferenceTimes.minOrNull() ?: 0.0)} ms"
        )

        Log.i(
            TAG,
            "Inference Max   : ${format(inferenceTimes.maxOrNull() ?: 0.0)} ms"
        )

        Log.i(
            TAG,
            "===================================================================="
        )
    }

    private fun runDetectionEvaluation(
        detector: BenchmarkYoloDetector,
        dataset: List<TestImage>,
        modelLabel: String
    ) {
        Log.i(TAG, "")
        Log.i(
            TAG,
            "---------------- DETECTION EVALUATION ----------------"
        )

        val records = ArrayList<DetectionRecord>()

        for (item in dataset) {
            val bitmap = decodeBitmap(
                item.path
            )

            try {
                val result = detector.detect(bitmap)

                val topConfidence =
                    detector.topConfidence(result)

                val predictedLabel =
                    result.detections
                        .maxByOrNull { it.score }
                        ?.label

                val classMatch =
                    predictedLabel != null &&
                            predictedLabel.equals(
                                item.expectedClass,
                                ignoreCase = true
                            )

                records.add(
                    DetectionRecord(
                        fileName = item.fileName,
                        expectedClass = item.expectedClass,
                        predictedClass = predictedLabel,
                        confidence = topConfidence,
                        classMatch = classMatch
                    )
                )

                Log.i(
                    TAG,
                    "DETECTION | model=$modelLabel | " +
                            "class=${item.expectedClass} | " +
                            "file=${item.fileName} | " +
                            "prediction=${predictedLabel ?: "NO_DETECTION"} | " +
                            "confidence=${format(topConfidence)}% | " +
                            "classMatch=$classMatch"
                )
            } finally {
                bitmap.recycle()
            }
        }

        printDetectionSummary(
            records = records,
            modelLabel = modelLabel
        )
    }

    private fun loadDataset(): List<TestImage> {
        val result = ArrayList<TestImage>()

        for (classFolder in CLASS_FOLDERS) {
            val path = "$DATASET_ROOT/$classFolder"

            val files = testContext.assets
                .list(path)
                ?.filter {
                    it.endsWith(".jpg", true) ||
                            it.endsWith(".jpeg", true) ||
                            it.endsWith(".png", true)
                }
                ?.sorted()
                ?: emptyList()

            require(files.size == 10) {
                "Folder assets/$path harus berisi tepat 10 gambar, " +
                        "tetapi ditemukan ${files.size}."
            }

            for (fileName in files) {
                result.add(
                    TestImage(
                        expectedClass =
                            classFolderToLabel(classFolder),
                        fileName = fileName,
                        path = "$path/$fileName"
                    )
                )
            }
        }

        return result
    }

    private fun classFolderToLabel(
        folder: String
    ): String {
        return when (folder) {
            "Blast" -> "Blast"
            "Hama_Putih_Palsu" -> "Hama Putih Palsu"
            "Hawar_Daun_Bakteri" -> "Hawar Daun Bakteri"
            "Stem_Borer" -> "Stem Borer"
            else -> error(
                "Folder kelas tidak dikenal: $folder"
            )
        }
    }

    private fun decodeBitmap(
        path: String
    ): Bitmap {
        val inputStream =
            testContext.assets.open(path)

        return inputStream.use {
            BitmapFactory.decodeStream(it)
                ?: error(
                    "Gagal membaca gambar: $path"
                )
        }
    }

    private fun printDetectionSummary(
        records: List<DetectionRecord>,
        modelLabel: String
    ) {
        Log.i(TAG, "")
        Log.i(
            TAG,
            "================ DETECTION SUMMARY ================"
        )

        Log.i(
            TAG,
            "MODEL: $modelLabel"
        )

        val confidenceValues =
            records.mapNotNull {
                it.confidence
            }

        val overallConfidence =
            confidenceValues.averageOrZero()

        val classMatchCount =
            records.count {
                it.classMatch
            }

        Log.i(
            TAG,
            "Overall Confidence Avg : " +
                    "${format(overallConfidence)}%"
        )

        Log.i(
            TAG,
            "Top-Class Match        : " +
                    "$classMatchCount/${records.size}"
        )

        for (
        className in CLASS_FOLDERS.map(
            ::classFolderToLabel
        )
        ) {
            val classRecords =
                records.filter {
                    it.expectedClass.equals(
                        className,
                        ignoreCase = true
                    )
                }

            val classConfidence =
                classRecords
                    .mapNotNull {
                        it.confidence
                    }
                    .averageOrZero()

            val classMatch =
                classRecords.count {
                    it.classMatch
                }

            Log.i(
                TAG,
                "CLASS | $className | " +
                        "n=${classRecords.size} | " +
                        "confidence=${format(classConfidence)}% | " +
                        "topClassMatch=$classMatch/${classRecords.size}"
            )
        }

        Log.i(
            TAG,
            "===================================================="
        )
    }

    private fun List<Float>.averageOrZero(): Double {
        return if (isEmpty()) {
            0.0
        } else {
            average().toDouble()
        }
    }

    private fun format(
        value: Double?
    ): String {
        return String.format(
            Locale.US,
            "%.3f",
            value ?: 0.0
        )
    }

    private fun format(
        value: Float?
    ): String {
        return String.format(
            Locale.US,
            "%.3f",
            value ?: 0f
        )
    }
}

data class TestImage(
    val expectedClass: String,
    val fileName: String,
    val path: String
)

data class DetectionRecord(
    val fileName: String,
    val expectedClass: String,
    val predictedClass: String?,
    val confidence: Float?,
    val classMatch: Boolean
)