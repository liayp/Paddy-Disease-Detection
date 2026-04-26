package amalia.skripsi.deteksipadi.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween

@Composable
fun ZoomableImageDialog(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {

            val scope = rememberCoroutineScope()

            val scale = remember { Animatable(1f) }
            val offsetX = remember { Animatable(0f) }
            val offsetY = remember { Animatable(0f) }

            val minScale = 1f
            val maxScale = 4f

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {

                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Zoomable Image",
                    modifier = Modifier
                        .fillMaxSize()

                        // 👉 DOUBLE TAP (dipisah biar tidak conflict)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { tapOffset ->
                                    scope.launch {
                                        if (scale.value > 1f) {
                                            scale.animateTo(1f, tween(300))
                                            offsetX.animateTo(0f, tween(300))
                                            offsetY.animateTo(0f, tween(300))
                                        } else {
                                            scale.animateTo(2f, tween(300))
                                        }
                                    }
                                }
                            )
                        }

                        // 👉 PINCH + DRAG
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->

                                val newScale = (scale.value * zoom)
                                    .coerceIn(minScale, maxScale)

                                val maxX = (size.width * (newScale - 1)) / 2
                                val maxY = (size.height * (newScale - 1)) / 2

                                val newOffsetX = (offsetX.value + pan.x)
                                    .coerceIn(-maxX, maxX)

                                val newOffsetY = (offsetY.value + pan.y)
                                    .coerceIn(-maxY, maxY)

                                scope.launch {
                                    scale.snapTo(newScale)
                                    offsetX.snapTo(newOffsetX)
                                    offsetY.snapTo(newOffsetY)
                                }
                            }
                        }

                        .graphicsLayer(
                            scaleX = scale.value,
                            scaleY = scale.value,
                            translationX = offsetX.value,
                            translationY = offsetY.value
                        ),
                    contentScale = ContentScale.Fit
                )

                // Tombol close
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 40.dp, end = 16.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = Color.White
                    )
                }
            }
        }
    }
}