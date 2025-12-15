package amalia.skripsi.deteksipadi.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.ImageProxy
import androidx.exifinterface.media.ExifInterface
import java.nio.ByteBuffer
import androidx.core.graphics.createBitmap
import java.io.File
import java.io.FileOutputStream

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

            // Fix rotasi galeri
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

    fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        val planeProxy = image.planes[0]
        val buffer: ByteBuffer = planeProxy.buffer
        buffer.rewind()

        if (image.format == ImageFormat.JPEG) {
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }

        if (image.format == ImageFormat.YUV_420_888 || image.format == 0x22) {
            val width = image.width
            val height = image.height
            val pixelStride = planeProxy.pixelStride
            val rowStride = planeProxy.rowStride
            val rowPadding = rowStride - pixelStride * width

            val bitmap = createBitmap(width + rowPadding / pixelStride, height)
            bitmap.copyPixelsFromBuffer(buffer)

            return if (rowPadding == 0) {
                bitmap
            } else {
                Bitmap.createBitmap(bitmap, 0, 0, width, height)
            }
        }
        return null
    }

    fun getGeoLocation(context: Context, uri: Uri): Pair<Double, Double>? {
        return try {
            // Coba dapatkan URI asli (Un-redacted)
            val photoUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    MediaStore.setRequireOriginal(uri)
                } catch (e: Exception) {
                    uri // Fallback jika bukan dari MediaStore
                }
            } else {
                uri
            }

            // SALIN KE TEMP FILE (Kunci agar terbaca di semua HP)
            // Stream EXIF butuh akses file penuh, kadang InputStream saja gagal
            val inputStream = context.contentResolver.openInputStream(photoUri) ?: return null
            val tempFile = File(context.cacheDir, "temp_gps_check.jpg")
            val outputStream = FileOutputStream(tempFile)

            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()

            //Baca EXIF dari File Temp
            val exif = ExifInterface(tempFile.absolutePath)
            val latLong = FloatArray(2)

            val hasLatLong = exif.getLatLong(latLong)

            // Hapus file temp biar bersih
            tempFile.delete()

            if (hasLatLong) {
                return Pair(latLong[0].toDouble(), latLong[1].toDouble())
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}