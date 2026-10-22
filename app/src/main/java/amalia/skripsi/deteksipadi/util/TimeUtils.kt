package amalia.skripsi.deteksipadi.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val WITA = TimeZone.getTimeZone("Asia/Makassar")
private val ID_LOCALE = Locale.Builder().setLanguage("id").setRegion("ID").build()

private fun parseTimestampMillis(value: String): Long? {
    val candidates = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd HH:mm:ss"
    )

    for (pattern in candidates) {
        runCatching {
            val formatter = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                if (!pattern.contains("X") && !pattern.endsWith("Z")) {
                    timeZone = WITA
                }
            }
            return formatter.parse(value)?.time
        }
    }

    return null
}

fun formatWitaDateTime(value: String?): String {
    if (value.isNullOrBlank()) return "-"
    val millis = parseTimestampMillis(value)
        ?: return value.replace('T', ' ').take(16)

    return SimpleDateFormat("dd MMM yyyy, HH:mm", ID_LOCALE).apply {
        timeZone = WITA
    }.format(Date(millis))
}

fun formatWitaDate(value: String?): String {
    if (value.isNullOrBlank()) return "-"
    val millis = parseTimestampMillis(value)
        ?: return value.replace('T', ' ').take(10)

    return SimpleDateFormat("dd MMM yyyy", ID_LOCALE).apply {
        timeZone = WITA
    }.format(Date(millis))
}

fun formatWitaDateTimeWithWeekday(epochMillis: Long): String =
    SimpleDateFormat("EEEE, dd MMM yyyy • HH:mm", ID_LOCALE).apply {
        timeZone = WITA
    }.format(Date(epochMillis))

fun parseTimestampToEpochMillis(value: String?): Long? =
    value?.takeIf { it.isNotBlank() }?.let(::parseTimestampMillis)