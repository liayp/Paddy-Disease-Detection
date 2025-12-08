package amalia.skripsi.deteksipadi.ui.screens.petani.detection

import amalia.skripsi.deteksipadi.ml.DetectionResult
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
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val paintBox = Paint().apply {
            color = android.graphics.Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }
        val paintText = Paint().apply {
            color = android.graphics.Color.YELLOW
            textSize = 40f
            style = Paint.Style.FILL
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        results.forEach { det ->
            // Karena Layar = Kotak dan Input AI = Kotak
            // Kita tinggal kali rata. Tidak perlu matriks rumit.
            val left = det.box.left * w
            val top = det.box.top * h
            val right = det.box.right * w
            val bottom = det.box.bottom * h

            drawRect(
                color = Color.Red,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
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