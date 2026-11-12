package app.memories.gallery.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.memories.gallery.MainActivity
import app.memories.gallery.R
import app.memories.gallery.data.GalleryItem
import app.memories.gallery.data.MediaRepository
import app.memories.gallery.data.UserPrefs
import app.memories.gallery.memories.Memory
import app.memories.gallery.memories.MemoryEngine
import app.memories.gallery.memories.MemoryKind
import app.memories.gallery.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Runs once a day and posts an "On this day" reminder when there is something to relive. */
class MemoryReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        return try {
            val today = LocalDate.now()
            val state = ctx.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
            if (state.getString(KEY_LAST_DATE, null) == today.toString()) return Result.success()
            if (!hasMediaAccess(ctx) || !canPostNotifications(ctx)) return Result.success()

            val all = MediaRepository(ctx).loadAll()
            if (all.isEmpty()) return Result.success()

            val prefs = UserPrefs(ctx)
            val hiddenMemories = prefs.hiddenMemories.value
            val memories = MemoryEngine.generate(
                all = all,
                favorites = prefs.favorites.value,
                hiddenItems = prefs.hiddenItems.value,
                today = today,
            ).filter { it.id !in hiddenMemories && it.items.isNotEmpty() }

            val onThisDay = memories
                .filter { it.kind.isOnThisDay }
                .maxWithOrNull(compareBy<Memory>({ it.kind == MemoryKind.ON_THIS_DAY }, { it.items.size }))
            val memory = onThisDay
                ?: memories.firstOrNull { it.id.startsWith("fotd-") }
                ?: return Result.success()

            val (title, body) = texts(memory, today)
            val cover = withContext(Dispatchers.IO) { loadCover(ctx, memory.cover) }
            if (post(ctx, memory, title, body, cover)) {
                state.edit().putString(KEY_LAST_DATE, today.toString()).apply()
            }
            Result.success()
        } catch (t: Throwable) {
            Result.success() // never retry-loop on a reminder
        }
    }

    private fun texts(memory: Memory, today: LocalDate): Pair<String, String> {
        val day = memory.day ?: memory.cover.date
        val yearsAgo = (today.year - day.year).coerceAtLeast(1)
        val title = when (memory.kind) {
            MemoryKind.ON_THIS_DAY -> "${Fmt.yearsAgo(yearsAgo)} today"
            MemoryKind.THIS_WEEK -> "This week, ${Fmt.yearsAgo(yearsAgo)}"
            else -> memory.title
        }
        val what = countLabel(memory.photoCount, memory.videoCount)
        val body = when (memory.kind) {
            MemoryKind.THIS_WEEK -> "$what from ${memory.subtitle} — tap to relive them"
            else -> "$what from ${day.format(BODY_DATE)} — tap to relive them"
        }
        return title to body
    }

    private fun countLabel(photos: Int, videos: Int): String {
        fun plural(n: Int, one: String, many: String) = if (n == 1) "1 $one" else "$n $many"
        return when {
            photos > 0 && videos > 0 ->
                "${plural(photos, "photo", "photos")} and ${plural(videos, "video", "videos")}"
            videos > 0 -> plural(videos, "video", "videos")
            else -> plural(photos, "photo", "photos")
        }
    }

    /** Returns true when the notification was handed to the system. */
    private fun post(ctx: Context, memory: Memory, title: String, body: String, cover: Bitmap?): Boolean {
        ensureChannel(ctx)
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(OPEN_MEMORY_EXTRA, memory.id)
        }
        val pending = PendingIntent.getActivity(
            ctx,
            memory.id.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(ctx, MemoryReminder.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        if (cover != null) {
            builder.setLargeIcon(cover)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(cover)
                    .bigLargeIcon(null as Bitmap?)
                    .setSummaryText(body),
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }
        if (!canPostNotifications(ctx)) return false
        return try {
            NotificationManagerCompat.from(ctx).notify(MemoryReminder.NOTIFICATION_ID, builder.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(MemoryReminder.CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                MemoryReminder.CHANNEL_ID,
                "Memories",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Daily \"On this day\" reminders" },
        )
    }

    @Suppress("DEPRECATION")
    private fun loadCover(ctx: Context, item: GalleryItem): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 29) {
            ctx.contentResolver.loadThumbnail(item.uri, Size(THUMB_PX, THUMB_PX), null)
        } else if (item.isVideo) {
            MediaStore.Video.Thumbnails.getThumbnail(
                ctx.contentResolver, item.id, MediaStore.Video.Thumbnails.MINI_KIND, null,
            )
        } else {
            MediaStore.Images.Thumbnails.getThumbnail(
                ctx.contentResolver, item.id, MediaStore.Images.Thumbnails.MINI_KIND, null,
            ) ?: decodeSampled(ctx, item)
        }
    } catch (_: Throwable) {
        null
    }

    private fun decodeSampled(ctx: Context, item: GalleryItem): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= THUMB_PX && bounds.outHeight / (sample * 2) >= THUMB_PX) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        ctx.contentResolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    } catch (_: Throwable) {
        null
    }

    companion object {
        private const val STATE_PREFS = "memory_reminder"
        private const val KEY_LAST_DATE = "last_notified_date"
        private const val THUMB_PX = 720
        private val BODY_DATE: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", Locale.getDefault())

        fun hasMediaAccess(ctx: Context): Boolean {
            val perms = when {
                Build.VERSION.SDK_INT >= 34 -> listOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                )
                Build.VERSION.SDK_INT >= 33 -> listOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                )
                else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            return perms.any { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
        }

        fun canPostNotifications(ctx: Context): Boolean {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        }
    }
}
