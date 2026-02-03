package amalia.skripsi.deteksipadi.ui.screens.petani.detection

import amalia.skripsi.deteksipadi.ml.DetectionResult
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.ImageProxy
import androidx.exifinterface.media.ExifInterface
import java.nio.ByteBuffer
import androidx.core.graphics.createBitmap
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

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


    fun drawDetectionOnBitmap(originalBitmap: Bitmap, results: List<DetectionResult>): Bitmap {
        // 1. Copy bitmap agar bisa diedit
        val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(mutableBitmap)

        // AMBIL UKURAN GAMBAR ASLI
        val imgWidth = mutableBitmap.width.toFloat()
        val imgHeight = mutableBitmap.height.toFloat()

        val boxPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.RED
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 8f
        }

        val textBgPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.RED
            style = android.graphics.Paint.Style.FILL
        }

        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 40f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        for (result in results) {
            val box = result.box // Ini masih 0.0 - 1.0

            // --- PERBAIKAN UTAMA: KONVERSI KE PIKSEL ---
            val left = box.left * imgWidth
            val top = box.top * imgHeight
            val right = box.right * imgWidth
            val bottom = box.bottom * imgHeight

            val pixelRect = android.graphics.RectF(left, top, right, bottom)

            // Gambar Kotak menggunakan koordinat PIKSEL
            canvas.drawRect(pixelRect, boxPaint)

            // Label
            val labelText = "${result.label} ${(result.score * 100).toInt()}%"
            val textWidth = textPaint.measureText(labelText)
            val textHeight = textPaint.textSize

            // Gambar Background Teks
            canvas.drawRect(
                left,
                top - textHeight - 10f,
                left + textWidth + 20f,
                top,
                textBgPaint
            )

            // Gambar Teks
            canvas.drawText(labelText, left + 10f, top - 10f, textPaint)
        }

        return mutableBitmap
    }

    fun getAddressName(context: Context, lat: Double, lon: Double): Triple<String, String, String> {
        var kecamatan = "Tidak Diketahui"
        var kelurahan = "Tidak Diketahui"
        var fullAddress = "Tidak Diketahui"

        try {
            val geocoder = Geocoder(context, Locale("id", "ID"))
            val addresses = geocoder.getFromLocation(lat, lon, 1)

            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                kecamatan = address.locality ?: address.subAdminArea ?: "-"
                kelurahan = address.subLocality ?: "-"
                fullAddress = address.getAddressLine(0) ?: "-"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Triple(kecamatan, kelurahan, fullAddress)
    }

    fun getGeoLocation(context: Context, uri: Uri): Pair<Double, Double>? {
        return try {
            // 1. Coba dapatkan URI asli (Un-redacted)
            val photoUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    MediaStore.setRequireOriginal(uri)
                } catch (_: Exception) {
                    uri // Fallback jika bukan dari MediaStore
                }
            } else {
                uri
            }

            // 2. SALIN KE TEMP FILE (Kunci agar terbaca di semua HP)
            // Stream EXIF butuh akses file penuh, kadang InputStream saja gagal
            val inputStream = context.contentResolver.openInputStream(photoUri) ?: return null
            val tempFile = File(context.cacheDir, "temp_gps_check.jpg")
            val outputStream = FileOutputStream(tempFile)

            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()

            // 3. Baca EXIF dari File Temp
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