package amalia.skripsi.deteksipadi.ui.screens.petani.detection

import amalia.skripsi.deteksipadi.R
import amalia.skripsi.deteksipadi.ui.screens.petani.home.HomeViewModel
import amalia.skripsi.deteksipadi.util.ImageUtils
import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import java.util.concurrent.ExecutorService

@Composable
fun ScannerFab(
    showCapturedImage: MutableState<Boolean>,
    showScanFab: MutableState<Boolean>,
    imageCapture: ImageCapture,
    isGalleryImageShown: MutableState<Boolean>,
    cameraExecutor: ExecutorService,
    capturedBitmap: MutableState<Bitmap?>,
    selectedGalleryBitmap: MutableState<Bitmap?>,
    navController: NavController,
    context: Context,
    homeViewModel: HomeViewModel,
    // Param tambahan (default value agar tidak error di preview)
    onUploadTriggered: () -> Unit = {},
    isUploading: Boolean = false
) {
    // Logic: Gambar ada jika captured TRUE atau gallery TRUE
    val isImageReady = showCapturedImage.value || isGalleryImageShown.value

    FloatingActionButton(
        onClick = {
            if (isImageReady) {
                // MODE UPLOAD
                onUploadTriggered()
            } else {
                // MODE KAMERA (Ambil Foto)
                imageCapture.takePicture(
                    cameraExecutor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(imageProxy: ImageProxy) {
                            // Gunakan ImageUtils
                            val rawBitmap = ImageUtils.imageProxyToBitmap(imageProxy)
                            if (rawBitmap != null) {
                                val rotation = imageProxy.imageInfo.rotationDegrees.toFloat()
                                val fixedBitmap = ImageUtils.rotateBitmap(rawBitmap, rotation)

                                capturedBitmap.value = fixedBitmap
                                showCapturedImage.value = true
                                showScanFab.value = true // Trigger UI change
                            }
                            imageProxy.close()
                        }
                        override fun onError(exception: ImageCaptureException) {
                            exception.printStackTrace()
                        }
                    }
                )
            }
        },
        // Warna berubah: Hijau (Kamera) -> Biru (Ready)
        containerColor = if (isImageReady) Color(0xFF21F3DB) else Color(0xFF4CB64E),
        shape = CircleShape,
        modifier = Modifier
            .size(56.dp)
            .offset(y = (-15).dp)
    ) {
        if (isUploading) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
        } else if (isImageReady) {
            Icon(Icons.Default.Send, "Kirim Laporan", tint = Color.White)
        } else {
            Icon(
                painterResource(id = R.drawable.baseline_camera_alt_24),
                contentDescription = "Camera",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}