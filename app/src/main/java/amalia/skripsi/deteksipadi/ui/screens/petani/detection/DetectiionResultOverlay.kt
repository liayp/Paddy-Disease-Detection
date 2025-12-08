package amalia.skripsi.deteksipadi.ui.screens.petani.detection

import android.graphics.RectF
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import amalia.skripsi.deteksipadi.ml.DetectionResult
import android.graphics.Color
import android.graphics.Paint

@Composable
fun DetectionOverlay(
    results: List<DetectionResult>,
    imageWidth: Int,
    imageHeight: Int,
    modifier: Modifier = Modifier
) {
    Log.e("OVERLAY", "DRAWING ${results.size} RESULTS")

    Canvas(modifier = modifier.fillMaxSize()) {

        val cw = size.width
        val ch = size.height

        val boxPaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }

        val textPaint = Paint().apply {
            color = Color.RED
            textSize = 40f
        }

        results.forEach { det ->
            val left = det.box.left * cw
            val top = det.box.top * ch
            val right = det.box.right * cw
            val bottom = det.box.bottom * ch

            Log.e("OVERLAY-BOX", "$left,$top,$right,$bottom")

            drawContext.canvas.nativeCanvas.drawRect(left, top, right, bottom, boxPaint)
            drawContext.canvas.nativeCanvas.drawText(det.label, left, top - 10f, textPaint)
        }
    }
}