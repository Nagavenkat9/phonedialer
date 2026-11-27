package app.memories.gallery.music

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val durationMs: Long,
) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
}

/** What the user picked for memory background music. */
sealed interface MusicChoice {
    data object None : MusicChoice
    data object Shuffle : MusicChoice
    data class Track(val songId: Long) : MusicChoice
}

object MusicLibrary {
    private const val PREFS = "memory_music"
    private const val KEY_CHOICE = "choice"
    private const val MIN_DURATION_MS = 30_000L

    val permission: String
        get() = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun loadChoice(context: Context): MusicChoice {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CHOICE, null)
        return when {
            raw == null || raw == "none" -> MusicChoice.None
            raw == "shuffle" -> MusicChoice.Shuffle
            raw.startsWith("song:") -> raw.removePrefix("song:").toLongOrNull()
                ?.let { MusicChoice.Track(it) } ?: MusicChoice.None
            else -> MusicChoice.None
        }
    }

    fun saveChoice(context: Context, choice: MusicChoice) {
        val raw = when (choice) {
            MusicChoice.None -> "none"
            MusicChoice.Shuffle -> "shuffle"
            is MusicChoice.Track -> "song:${choice.songId}"
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_CHOICE, raw).apply()
    }

    /** Songs on the device (music only, longer than 30s). Empty if permission is missing. */
    suspend fun querySongs(context: Context): List<Song> = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext emptyList()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > ?"
        val args = arrayOf(MIN_DURATION_MS.toString())
        val songs = ArrayList<Song>()
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (c.moveToNext()) {
                    val artist = c.getString(artistCol)
                    songs += Song(
                        id = c.getLong(idCol),
                        title = c.getString(titleCol) ?: "Unknown",
                        artist = if (artist.isNullOrBlank() || artist == MediaStore.UNKNOWN_STRING) "Unknown artist" else artist,
                        durationMs = c.getLong(durCol),
                    )
                }
            }
        } catch (_: SecurityException) {
            return@withContext emptyList()
        } catch (_: IllegalArgumentException) {
            return@withContext emptyList()
        }
        songs
    }

    /** Resolves a choice to the concrete song to play, or null for no music. */
    suspend fun resolve(context: Context, choice: MusicChoice): Song? = when (choice) {
        MusicChoice.None -> null
        MusicChoice.Shuffle -> querySongs(context).randomOrNull()
        is MusicChoice.Track -> querySongs(context).firstOrNull { it.id == choice.songId }
    }
}
