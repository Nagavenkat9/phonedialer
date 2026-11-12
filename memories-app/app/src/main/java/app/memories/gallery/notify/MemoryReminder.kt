package app.memories.gallery.notify

import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Intent extra carrying the id of the memory to open when a reminder is tapped. */
const val OPEN_MEMORY_EXTRA = "open_memory_id"

/** The memory id a reminder notification asked to open, if any. */
fun Intent.openMemoryIdExtra(): String? = getStringExtra(OPEN_MEMORY_EXTRA)

object MemoryReminder {
    const val CHANNEL_ID = "memories"
    const val NOTIFICATION_ID = 4201
    private const val WORK_NAME = "daily_memory_reminder"
    private val TARGET_TIME: LocalTime = LocalTime.of(9, 0)

    /** Schedules the once-a-day reminder (no-op if it is already scheduled). */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<MemoryReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntilNextTarget().toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun delayUntilNextTarget(): Duration {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(TARGET_TIME)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }
}
