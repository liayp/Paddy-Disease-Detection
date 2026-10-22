package amalia.skripsi.deteksipadi.ui.screens.petani.detection

import amalia.skripsi.deteksipadi.ml.DetectionResult
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas

@Composable
fun DetectionOverlay(
    results: List<DetectionResult>,
    sourceWidth: Int,
    sourceHeight: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val screenW = size.width
        val screenH = size.height

        val scaleX = screenW / sourceWidth.toFloat()
        val scaleY = screenH / sourceHeight.toFloat()
        val scale = minOf(scaleX, scaleY)

        val offsetX = (screenW - (sourceWidth * scale)) / 2
        val offsetY = (screenH - (sourceHeight * scale)) / 2

        val paintText = Paint().apply {
            color = android.graphics.Color.YELLOW
            textSize = 38f
            style = Paint.Style.FILL
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val centerX = screenW / 2f
        val centerY = screenH / 2f

        results.forEach { det ->
            val box = det.box
            val left = (box.left * sourceWidth * scale) + offsetX
            val top = (box.top * sourceHeight * scale) + offsetY
            val right = (box.right * sourceWidth * scale) + offsetX
            val bottom = (box.bottom * sourceHeight * scale) + offsetY

            val w = right - left
            val h = bottom - top
            val boxCenterX = left + (w / 2f)
            val boxCenterY = top + (h / 2f)

            // Garis penanda visual tambahan langsung mengarah ke titik pusat objek hama
            drawLine(
                color = Color.Yellow.copy(alpha = 0.7f),
                start = Offset(centerX, centerY),
                end = Offset(boxCenterX, boxCenterY),
                strokeWidth = 3f
            )

            // Kotak Bounding Box utama
            drawRect(
                color = Color.Red,
                topLeft = Offset(left, top),
                size = Size(w, h),
                style = Stroke(width = 6f)
            )

            // Label Teks Akurasi
            drawContext.canvas.nativeCanvas.drawText(
                "${det.label} ${(det.score * 100).toInt()}%",
                left,
                top - 12f,
                paintText
            )
        }
    }
}