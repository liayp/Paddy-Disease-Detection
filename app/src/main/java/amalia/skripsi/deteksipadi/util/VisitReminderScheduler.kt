package amalia.skripsi.deteksipadi.util

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import amalia.skripsi.deteksipadi.data.JadwalKunjunganDto
import amalia.skripsi.deteksipadi.workers.VisitReminderWorker
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object VisitReminderScheduler {
    private val json = Json { ignoreUnknownKeys = true }
    @RequiresApi(Build.VERSION_CODES.O)
    private val zone = ZoneId.of("Asia/Makassar")

    @RequiresApi(Build.VERSION_CODES.O)
    private fun parseTime(value: String): Long? = runCatching {
        runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .getOrElse { runCatching { Instant.parse(value).toEpochMilli() }.getOrElse {
                LocalDateTime.parse(value.take(19), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
                    .atZone(zone).toInstant().toEpochMilli()
            } }
    }.getOrNull()

    @RequiresApi(Build.VERSION_CODES.O)
    fun sync(context: Context, reportId: String, visits: List<JadwalKunjunganDto>) {
        val manager = WorkManager.getInstance(context.applicationContext)
        val prefs = context.getSharedPreferences("visit_reminder_schedule", Context.MODE_PRIVATE)
        val key = "report_$reportId"
        val previous = prefs.getStringSet(key, emptySet()).orEmpty()
        val current = visits.map { it.id }.filter { it.isNotBlank() }.toSet()
        previous.filterNot { it in current }.forEach { id -> cancelVisit(manager, id) }

        visits.forEach { visit ->
            if (visit.id.isBlank()) return@forEach
            cancelVisit(manager, visit.id)
            if (visit.status.lowercase() in setOf("selesai", "dibatalkan", "ditolak", "terlaksana")) return@forEach
            val target = parseTime(visit.waktu_kunjungan) ?: return@forEach
            scheduleOne(context, visit, VisitReminderWorker.KIND_DAY, target - TimeUnit.DAYS.toMillis(1), "Pengingat kunjungan besok", "Kunjungan terkait laporan akan berlangsung besok.")
            scheduleOne(context, visit, VisitReminderWorker.KIND_HOUR, target - TimeUnit.HOURS.toMillis(1), "Pengingat kunjungan 1 jam lagi", "Kunjungan terkait laporan akan berlangsung sekitar satu jam lagi.")
        }
        prefs.edit()
            .putStringSet(key, current)
            .putString("data_$reportId", json.encodeToString(ListSerializer(JadwalKunjunganDto.serializer()), visits))
            .apply()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun rescheduleStored(context: Context) {
        val prefs = context.getSharedPreferences("visit_reminder_schedule", Context.MODE_PRIVATE)
        val stored = prefs.all.filterKeys { it.startsWith("data_") }
        stored.forEach { (key, value) ->
            val reportId = key.removePrefix("data_")
            val visits = runCatching {
                json.decodeFromString(ListSerializer(JadwalKunjunganDto.serializer()), value as String)
            }.getOrNull() ?: return@forEach
            sync(context, reportId, visits)
        }
    }

    private fun cancelVisit(manager: WorkManager, visitId: String) {
        manager.cancelUniqueWork(uniqueName(visitId, VisitReminderWorker.KIND_DAY))
        manager.cancelUniqueWork(uniqueName(visitId, VisitReminderWorker.KIND_HOUR))
    }

    private fun scheduleOne(context: Context, visit: JadwalKunjunganDto, kind: String, triggerAt: Long, title: String, message: String) {
        val preference = context.getSharedPreferences("visit_reminder_settings", Context.MODE_PRIVATE)
        val enabled = when (kind) {
            VisitReminderWorker.KIND_DAY -> preference.getBoolean("day_before", true)
            VisitReminderWorker.KIND_HOUR -> preference.getBoolean("hour_before", true)
            else -> false
        }
        if (!enabled) return
        val delay = triggerAt - System.currentTimeMillis()
        if (delay <= 0L) return
        val data = Data.Builder()
            .putString(VisitReminderWorker.KEY_KIND, kind)
            .putString(VisitReminderWorker.KEY_VISIT_ID, visit.id)
            .putString(VisitReminderWorker.KEY_TITLE, title)
            .putString(VisitReminderWorker.KEY_MESSAGE, message)
            .build()
        val request = OneTimeWorkRequestBuilder<VisitReminderWorker>()
            .setInputData(data)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            uniqueName(visit.id, kind), ExistingWorkPolicy.REPLACE, request
        )
    }

    private fun uniqueName(visitId: String, kind: String) = "visit_reminder_${visitId}_$kind"
}
