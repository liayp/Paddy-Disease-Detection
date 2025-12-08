package amalia.skripsi.deteksipadi.ui.screens.petani.detection

import amalia.skripsi.deteksipadi.ml.DetectionResult
import amalia.skripsi.deteksipadi.ml.YoloDetector
import amalia.skripsi.deteksipadi.util.ImageUtils
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.concurrent.Executors
import kotlin.math.min

@Composable
fun ScannerContent(
    hasCameraPermission: State<Boolean>,
    showCapturedImage: MutableState<Boolean>,
    capturedBitmap: MutableState<Bitmap?>,
    selectedGalleryBitmap: MutableState<Bitmap?>,
    isGalleryImageShown: MutableState<Boolean>,
    cameraProviderFuture: com.google.common.util.concurrent.ListenableFuture<ProcessCameraProvider>,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    imageCapture: ImageCapture,
    detectionResults: List<DetectionResult>,
    context: Context,
    detector: YoloDetector?,
    onRealtimeDetection: (List<DetectionResult>) -> Unit,
    onClearImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val previewView = remember { PreviewView(context) }
    var lastInference by remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) { onDispose { analysisExecutor.shutdown() } }

    LaunchedEffect(hasCameraPermission.value, showCapturedImage.value, isGalleryImageShown.value) {
        val shouldRunCamera = hasCameraPermission.value && !showCapturedImage.value && !isGalleryImageShown.value
        val cameraProvider = cameraProviderFuture.get()
        cameraProvider.unbindAll()

        if (shouldRunCamera) {
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // ANALYZER: Gunakan resolusi KOTAK (640x640) agar sesuai model
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setTargetResolution(Size(640, 640))
                .build()

            imageAnalyzer.setAnalyzer(analysisExecutor) { imageProxy ->
                val now = System.currentTimeMillis()
                if (now - lastInference < 100) { imageProxy.close(); return@setAnalyzer }
                lastInference = now
                try {
                    val bmp = ImageUtils.imageProxyToBitmap(imageProxy)
                    if (bmp != null && detector != null) {
                        val rotation = imageProxy.imageInfo.rotationDegrees.toFloat()
                        val rotated = ImageUtils.rotateBitmap(bmp, rotation)

                        // ----------------------------------------------------
                        // CROP KOTAK: Samakan input AI dengan tampilan UI Square
                        // ----------------------------------------------------
                        val dimension = min(rotated.width, rotated.height)
                        val x = (rotated.width - dimension) / 2
                        val y = (rotated.height - dimension) / 2
                        val squareBitmap = Bitmap.createBitmap(rotated, x, y, dimension, dimension)

                        val results = detector.detect(squareBitmap)
                        mainHandler.post {
                            onRealtimeDetection(results)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ScannerContent", "Analyzer error", e)
                } finally {
                    imageProxy.close()
                }
            }

            try {
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, imageAnalyzer)
            } catch (e: Exception) {
                Log.e("ScannerContent", "bind failed", e)
            }
        }
    }

    // Layout Utama: Putih Bersih
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (hasCameraPermission.value) {

            // === WADAH KOTAK 1:1 (SQUARE) ===
            // Box ini akan memaksa isinya menjadi kotak
            Box(
                modifier = Modifier
                    .fillMaxWidth()        // Lebar penuh
                    .aspectRatio(1f)       // Rasio 1:1 -> Jadi Kotak
                    .padding(16.dp)        // Padding agar cantik (tidak nempel pinggir)
                    .clip(RoundedCornerShape(16.dp)) // Sudut membulat
                    .background(Color.Black), // Background hitam untuk area kosong
                contentAlignment = Alignment.Center
            ) {
                if (!showCapturedImage.value && !isGalleryImageShown.value) {
                    // 1. Preview Kamera
                    AndroidView(
                        factory = {
                            previewView.apply {
                                // SCALE TYPE PENTING: FILL_CENTER
                                // Ini akan memotong (crop) bagian atas-bawah gambar kamera
                                // agar pas masuk ke kotak 1:1 ini.
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // 2. Overlay Kotak Merah
                    DetectionOverlay(results = detectionResults)

                } else {
                    // 3. Hasil Foto / Galeri
                    val bmp = capturedBitmap.value ?: selectedGalleryBitmap.value
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Captured",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop // Crop juga biar konsisten
                        )
                        DetectionOverlay(results = detectionResults)
                    }
                }

                // Tombol Close (X)
                if (showCapturedImage.value || isGalleryImageShown.value) {
                    IconButton(
                        onClick = onClearImage,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(0.5f), MaterialTheme.shapes.small)
                    ) {
                        Icon(Icons.Default.Close, "Close", tint = Color.White)
                    }
                }
            }

            // Teks Petunjuk di bawah kotak
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (detectionResults.isNotEmpty())
                    "Terdeteksi: ${detectionResults[0].label} (${(detectionResults[0].score * 100).toInt()}%)"
                else "Arahkan kamera ke tanaman padi",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Black
            )

        } else {
            Text("Izin Kamera Diperlukan")
        }
    }
}