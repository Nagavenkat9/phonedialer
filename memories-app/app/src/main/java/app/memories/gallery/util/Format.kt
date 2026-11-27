package app.memories.gallery.util

import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object Fmt {
    val dayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
    val short: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
    val headerThisYear: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")
    val headerOtherYear: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
    val full: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")
    val weekday: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE")
    val dayMonthYearLong: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
    val monthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    val time: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    fun header(date: LocalDate, today: LocalDate = LocalDate.now()): String = when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        date.year == today.year -> date.format(headerThisYear)
        else -> date.format(headerOtherYear)
    }

    fun yearsAgo(n: Int): String = if (n == 1) "1 year ago" else "$n years ago"

    /** "Today", "3 days ago", "2 months ago", "4 years ago"… */
    fun relative(date: LocalDate, today: LocalDate = LocalDate.now()): String {
        val days = ChronoUnit.DAYS.between(date, today)
        if (days < 0) return date.format(short)
        val period = Period.between(date, today)
        return when {
            period.years >= 1 -> yearsAgo(period.years)
            period.months >= 1 -> if (period.months == 1) "1 month ago" else "${period.months} months ago"
            days == 0L -> "Today"
            days == 1L -> "Yesterday"
            days < 7 -> "$days days ago"
            days < 14 -> "1 week ago"
            else -> "${days / 7} weeks ago"
        }
    }

    fun duration(ms: Long): String {
        val total = (ms.coerceAtLeast(0) / 1000)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun count(photos: Int, videos: Int): String {
        val parts = mutableListOf<String>()
        if (photos > 0) parts += if (photos == 1) "1 photo" else "$photos photos"
        if (videos > 0) parts += if (videos == 1) "1 video" else "$videos videos"
        return parts.joinToString(" · ")
    }

    fun bytes(b: Long): String = when {
        b <= 0 -> "—"
        b < 1024 * 1024 -> "%.0f KB".format(b / 1024.0)
        b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / (1024.0 * 1024))
        else -> "%.2f GB".format(b / (1024.0 * 1024 * 1024))
    }
}
