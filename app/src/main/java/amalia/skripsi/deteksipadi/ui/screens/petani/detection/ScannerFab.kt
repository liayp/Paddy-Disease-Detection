package amalia.skripsi.deteksipadi.ui.screens.petani.detection

import amalia.skripsi.deteksipadi.R
import amalia.skripsi.deteksipadi.util.ImageUtils
import android.graphics.Bitmap
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import java.util.concurrent.ExecutorService

@Composable
fun ScannerFab(
    imageCapture: ImageCapture,
    cameraExecutor: ExecutorService,
    onPhotoCaptured: (Bitmap) -> Unit
) {
    FloatingActionButton(
        onClick = {
            imageCapture.takePicture(
                cameraExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(imageProxy: ImageProxy) {
                        val rawBitmap = ImageUtils.imageProxyToBitmap(imageProxy)
                        if (rawBitmap != null) {
                            val rotation = imageProxy.imageInfo.rotationDegrees.toFloat()
                            val fixedBitmap = ImageUtils.rotateBitmap(rawBitmap, rotation)
                            onPhotoCaptured(fixedBitmap)
                        }
                        imageProxy.close()
                    }
                    override fun onError(exception: ImageCaptureException) {
                        exception.printStackTrace()
                    }
                }
            )
        },
        containerColor = Color(0xFF4CB64E),
        shape = CircleShape,
        modifier = Modifier.size(72.dp).offset(y = (-20).dp)
    ) {
        Icon(
            painterResource(id = R.drawable.baseline_camera_alt_24),
            contentDescription = "Scan",
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
    }
}