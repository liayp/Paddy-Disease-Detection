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

    // State ukuran gambar asli untuk Overlay
    var sourceWidth by remember { mutableIntStateOf(1) }
    var sourceHeight by remember { mutableIntStateOf(1) }

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

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
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

                        // 1. KIRIM GAMBAR UTUH (Sama seperti Python)
                        // Detector akan me-resize (gepengkan) sendiri secara internal
                        val results = detector.detect(rotated)

                        mainHandler.post {
                            // Update ukuran asli untuk perhitungan Overlay
                            sourceWidth = rotated.width
                            sourceHeight = rotated.height
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

    Column(
        modifier = modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (hasCameraPermission.value) {

            // UI KOTAK 3:4
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3/4f)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (!showCapturedImage.value && !isGalleryImageShown.value) {
                    // Preview Live
                    AndroidView(
                        factory = {
                            previewView.apply {
                                scaleType = PreviewView.ScaleType.FIT_CENTER
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    DetectionOverlay(
                        results = detectionResults,
                        sourceWidth = sourceWidth,
                        sourceHeight = sourceHeight
                    )

                } else {
                    // Hasil Foto
                    val bmp = capturedBitmap.value ?: selectedGalleryBitmap.value
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Captured",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit // Fit juga biar konsisten
                        )
                        DetectionOverlay(
                            results = detectionResults,
                            sourceWidth = bmp.width,
                            sourceHeight = bmp.height
                        )
                    }
                }

                // Close Button
                if (showCapturedImage.value || isGalleryImageShown.value) {
                    IconButton(onClick = onClearImage, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(0.5f), MaterialTheme.shapes.small)) {
                        Icon(Icons.Default.Close, "Close", tint = Color.White)
                    }
                }
            }

        } else {
            Text("Izin Kamera Diperlukan")
        }
    }
}