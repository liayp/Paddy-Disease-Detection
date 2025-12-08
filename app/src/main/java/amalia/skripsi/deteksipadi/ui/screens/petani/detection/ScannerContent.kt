package amalia.skripsi.deteksipadi.ui.screens.petani.detection

import amalia.skripsi.deteksipadi.ml.DetectionResult
import amalia.skripsi.deteksipadi.ml.YoloDetector
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors
import kotlin.system.measureTimeMillis

@Composable
fun ScannerContent(
    hasCameraPermission: State<Boolean>, // Pake State wrapper sesuai kode asli
    showCapturedImage: MutableState<Boolean>,
    capturedBitmap: MutableState<Bitmap?>,
    selectedGalleryBitmap: MutableState<Bitmap?>,
    isGalleryImageShown: MutableState<Boolean>,
    cameraProviderFuture: ListenableFuture<ProcessCameraProvider>,
    lifecycleOwner: LifecycleOwner,
    imageCapture: ImageCapture,
    detectionResults: List<DetectionResult>,
    context: Context,
    detector: YoloDetector?,
    onRealtimeDetection: (List<DetectionResult>) -> Unit,
    // Param tambahan untuk tombol close
    onClearImage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val previewView = remember { PreviewView(context) }
    var realtimeWidth by remember { mutableIntStateOf(1) }
    var realtimeHeight by remember { mutableIntStateOf(1) }
    var lastInference by remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) { onDispose { analysisExecutor.shutdown() } }

    LaunchedEffect(hasCameraPermission.value, showCapturedImage.value, isGalleryImageShown.value) {
        val shouldRunCamera = hasCameraPermission.value && !showCapturedImage.value && !isGalleryImageShown.value
        val cameraProvider = cameraProviderFuture.get()
        cameraProvider.unbindAll()
        if (shouldRunCamera) {
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setTargetResolution(Size(640, 640))
                .build()

            imageAnalyzer.setAnalyzer(analysisExecutor) { imageProxy ->
                val now = System.currentTimeMillis()
                if (now - lastInference < 200) { imageProxy.close(); return@setAnalyzer }
                lastInference = now
                try {
                    // FIX: Gunakan ImageUtils untuk hindari crash warna
                    val bmp = ImageUtils.imageProxyToBitmap(imageProxy)

                    if (bmp != null && detector != null) {
                        val rotation = imageProxy.imageInfo.rotationDegrees.toFloat()
                        val rotated = ImageUtils.rotateBitmap(bmp, rotation)
                        val time = measureTimeMillis {
                            val results = detector.detect(rotated)
                            mainHandler.post {
                                realtimeWidth = rotated.width
                                realtimeHeight = rotated.height
                                onRealtimeDetection(results)
                            }
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
        } else {
            cameraProvider.unbindAll()
        }
    }

    // UI ASLI (Column) -> Menjamin layout tidak putih kosong
    Column(modifier = modifier.fillMaxSize().background(Color.White)) {
        if (hasCameraPermission.value) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (!showCapturedImage.value && !isGalleryImageShown.value) {
                    // Preview Kamera
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                    if (realtimeWidth > 1) DetectionOverlay(detectionResults, realtimeWidth, realtimeHeight) // Sesuaikan DetectionOverlay Anda
                } else {
                    // Hasil Foto / Galeri
                    val bmp = capturedBitmap.value ?: selectedGalleryBitmap.value
                    if (bmp != null) {
                        Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                        // Jika DetectionOverlay butuh parameter lebar/tinggi, sesuaikan disini
                        DetectionOverlay(detectionResults, bmp.width, bmp.height)
                    }
                }

                // Tombol Close
                if (showCapturedImage.value || isGalleryImageShown.value) {
                    IconButton(onClick = {
                        onClearImage() // Panggil callback reset
                    }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(40.dp).background(Color.Black.copy(0.5f), MaterialTheme.shapes.small)) {
                        Icon(Icons.Default.Close, "Clear", tint = Color.White)
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Izin kamera diperlukan") }
        }
    }
}