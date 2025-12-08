package amalia.skripsi.deteksipadi.ui.screens.petani.detection

import amalia.skripsi.deteksipadi.ml.DetectionResult
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Paint

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

        // --- LOGIKA "FIT_CENTER" (Muat Semua) ---
        // Kita cari skala TERKECIL agar gambar muat sepenuhnya
        val scaleX = screenW / sourceWidth.toFloat()
        val scaleY = screenH / sourceHeight.toFloat()

        val scale = minOf(scaleX, scaleY) // Gunakan minOf untuk FIT

        // Hitung Margin/Offset (Area hitam)
        val offsetX = (screenW - (sourceWidth * scale)) / 2
        val offsetY = (screenH - (sourceHeight * scale)) / 2

        val paintText = Paint().apply {
            color = android.graphics.Color.YELLOW
            textSize = 40f
            style = Paint.Style.FILL
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        results.forEach { det ->
            val box = det.box // 0.0 - 1.0

            // Rumus: (Relatif * Asli * Zoom) + Margin
            val left = (box.left * sourceWidth * scale) + offsetX
            val top = (box.top * sourceHeight * scale) + offsetY
            val right = (box.right * sourceWidth * scale) + offsetX
            val bottom = (box.bottom * sourceHeight * scale) + offsetY

            val w = right - left
            val h = bottom - top

            drawRect(
                color = Color.Red,
                topLeft = Offset(left, top),
                size = Size(w, h),
                style = Stroke(width = 6f)
            )

            drawContext.canvas.nativeCanvas.drawText(
                "${det.label} ${(det.score * 100).toInt()}%",
                left,
                top - 10f,
                paintText
            )
        }
    }
}