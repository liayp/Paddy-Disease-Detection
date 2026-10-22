package amalia.skripsi.deteksipadi.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class PendingLaporanUpdate(
    val id: Long,
    val laporanId: String,
    val userId: String,
    val imagePath: String,
    val labelAi: String,
    val confidence: Float,
    val catatan: String,
    val latitude: Double,
    val longitude: Double,
    val gpsAccuracyMeters: Float,
    val capturedAt: String
)

class PendingLaporanUpdateQueue(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    "pending-laporan-updates.db",
    null,
    1
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE pending_laporan_updates (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                laporan_id TEXT NOT NULL,
                user_id TEXT NOT NULL,
                image_path TEXT NOT NULL,
                label_ai TEXT NOT NULL,
                confidence REAL NOT NULL,
                catatan TEXT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                gps_accuracy_m REAL NOT NULL,
                captured_at TEXT NOT NULL
            )""".trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun enqueue(
        laporanId: String, userId: String, imagePath: String, labelAi: String,
        confidence: Float, catatan: String, latitude: Double, longitude: Double,
        gpsAccuracyMeters: Float, capturedAt: String
    ): Long {
        val values = ContentValues().apply {
            put("laporan_id", laporanId)
            put("user_id", userId)
            put("image_path", imagePath)
            put("label_ai", labelAi)
            put("confidence", confidence)
            put("catatan", catatan)
            put("latitude", latitude)
            put("longitude", longitude)
            put("gps_accuracy_m", gpsAccuracyMeters)
            put("captured_at", capturedAt)
        }
        return writableDatabase.insertOrThrow("pending_laporan_updates", null, values)
    }

    fun getAll(): List<PendingLaporanUpdate> {
        val result = mutableListOf<PendingLaporanUpdate>()
        readableDatabase.query(
            "pending_laporan_updates", null, null, null, null, null, "id ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += PendingLaporanUpdate(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    laporanId = cursor.getString(cursor.getColumnIndexOrThrow("laporan_id")),
                    userId = cursor.getString(cursor.getColumnIndexOrThrow("user_id")),
                    imagePath = cursor.getString(cursor.getColumnIndexOrThrow("image_path")),
                    labelAi = cursor.getString(cursor.getColumnIndexOrThrow("label_ai")),
                    confidence = cursor.getFloat(cursor.getColumnIndexOrThrow("confidence")),
                    catatan = cursor.getString(cursor.getColumnIndexOrThrow("catatan")),
                    latitude = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude")),
                    longitude = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude")),
                    gpsAccuracyMeters = cursor.getFloat(cursor.getColumnIndexOrThrow("gps_accuracy_m")),
                    capturedAt = cursor.getString(cursor.getColumnIndexOrThrow("captured_at"))
                )
            }
        }
        return result
    }

    fun delete(id: Long) {
        writableDatabase.delete("pending_laporan_updates", "id = ?", arrayOf(id.toString()))
    }
}
