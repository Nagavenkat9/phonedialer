package app.memories.gallery.data

import android.net.Uri
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** A single photo or video from the device's MediaStore. */
data class GalleryItem(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    /** When the photo/video was taken, epoch millis. */
    val dateTaken: Long,
    val displayName: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val bucketId: String,
    val bucketName: String,
    val sizeBytes: Long,
) {
    /** Stable key used for favorites / hidden lists. */
    val key: String = (if (isVideo) "v" else "i") + id

    val dateTime: LocalDateTime =
        Instant.ofEpochMilli(dateTaken).atZone(ZoneId.systemDefault()).toLocalDateTime()

    val date: LocalDate = dateTime.toLocalDate()
}
