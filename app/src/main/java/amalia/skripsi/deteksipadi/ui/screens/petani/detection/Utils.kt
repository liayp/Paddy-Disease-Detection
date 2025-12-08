package amalia.skripsi.deteksipadi.ui.screens.petani.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.camera.core.ImageProxy
import androidx.exifinterface.media.ExifInterface
import java.nio.ByteBuffer

object ImageUtils {

    fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        if (angle == 0f) return source
        val matrix = Matrix().apply { postRotate(angle) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri)
            val originalBmp = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // Cek Orientasi EXIF agar gambar tidak miring saat diambil dari galeri
            val exifStream = contentResolver.openInputStream(uri)
            val exif = exifStream?.let { ExifInterface(it) }
            val rotation = when (exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            exifStream?.close()

            if (originalBmp != null) rotateBitmap(originalBmp, rotation) else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Konversi ImageProxy (CameraX) ke Bitmap dengan aman
    fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        val planeProxy = image.planes[0]
        val buffer: ByteBuffer = planeProxy.buffer
        val pixelStride = planeProxy.pixelStride
        val rowStride = planeProxy.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val width = image.width
        val height = image.height

        // Buat bitmap baru
        val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)

        // Crop padding jika ada (penting untuk beberapa device)
        return if (rowPadding == 0) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, width, height)
        }
    }
}