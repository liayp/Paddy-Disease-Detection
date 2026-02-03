package amalia.skripsi.deteksipadi.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Database
import androidx.room.RoomDatabase

@Entity(tableName = "pending_reports")
data class PendingReport(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val imagePath: String,
    val label: String,
    val confidence: Float,
    val lat: Double,
    val lon: Double,
    val kecamatan: String,
    val kelurahan: String,
    val addressDetail: String,
    val createdAt: Long = System.currentTimeMillis(),
    val userId: String,
)

@Dao
interface PendingReportDao {
    @Insert
    suspend fun insert(report: PendingReport)

    @Query("SELECT * FROM pending_reports")
    suspend fun getAllReports(): List<PendingReport>

    @Query("DELETE FROM pending_reports WHERE id = :id")
    suspend fun deleteReport(id: Int)
}

@Database(entities = [PendingReport::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pendingReportDao(): PendingReportDao
}