package app.memories.gallery.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads every photo and video on the device through MediaStore. */
class MediaRepository(private val context: Context) {

    private val locations by lazy { PhotoLocationReader(context) }

    suspend fun loadAll(): List<GalleryItem> = withContext(Dispatchers.IO) {
        val images = query(
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            isVideo = false,
        )
        val videos = query(
            collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            isVideo = true,
        )
        val located = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                locations.applyLocations(images)
            } catch (t: Throwable) {
                images
            }
        } else {
            images
        }
        (located + videos).sortedByDescending { it.dateTaken }
    }

    @Suppress("DEPRECATION")
    private fun query(collection: Uri, isVideo: Boolean): List<GalleryItem> {
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.Images.ImageColumns.DATE_TAKEN)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.Images.ImageColumns.BUCKET_ID)
            add(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
            if (isVideo) add(MediaStore.Video.VideoColumns.DURATION)
            if (!isVideo && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                add(MediaStore.Images.ImageColumns.LATITUDE)
                add(MediaStore.Images.ImageColumns.LONGITUDE)
            }
        }.toTypedArray()

        val out = ArrayList<GalleryItem>()
        context.contentResolver.query(collection, projection, null, null, null)?.use { c ->
            val idCol = c.getColumnIndex(MediaStore.MediaColumns._ID)
            val takenCol = c.getColumnIndex(MediaStore.Images.ImageColumns.DATE_TAKEN)
            val addedCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
            val modCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val nameCol = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val wCol = c.getColumnIndex(MediaStore.MediaColumns.WIDTH)
            val hCol = c.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
            val sizeCol = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val bIdCol = c.getColumnIndex(MediaStore.Images.ImageColumns.BUCKET_ID)
            val bNameCol = c.getColumnIndex(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
            val durCol = if (isVideo) c.getColumnIndex(MediaStore.Video.VideoColumns.DURATION) else -1
            val legacyLocation = !isVideo && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
            val latCol = if (legacyLocation) c.getColumnIndex(MediaStore.Images.ImageColumns.LATITUDE) else -1
            val lonCol = if (legacyLocation) c.getColumnIndex(MediaStore.Images.ImageColumns.LONGITUDE) else -1

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val taken = if (takenCol >= 0) c.getLong(takenCol) else 0L
                val modified = if (modCol >= 0) c.getLong(modCol) * 1000 else 0L
                val added = if (addedCol >= 0) c.getLong(addedCol) * 1000 else 0L
                val date = when {
                    taken > 0 -> taken
                    modified > 0 -> modified
                    else -> added
                }
                var lat: Double? = null
                var lon: Double? = null
                if (latCol >= 0 && lonCol >= 0 && !c.isNull(latCol) && !c.isNull(lonCol)) {
                    val la = c.getDouble(latCol)
                    val lo = c.getDouble(lonCol)
                    if (PhotoLocationReader.isValid(la, lo)) {
                        lat = la
                        lon = lo
                    }
                }
                out += GalleryItem(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    isVideo = isVideo,
                    dateTaken = date,
                    displayName = if (nameCol >= 0) c.getString(nameCol).orEmpty() else "",
                    mimeType = if (mimeCol >= 0) c.getString(mimeCol).orEmpty() else "",
                    width = if (wCol >= 0) c.getInt(wCol) else 0,
                    height = if (hCol >= 0) c.getInt(hCol) else 0,
                    durationMs = if (durCol >= 0) c.getLong(durCol) else 0L,
                    bucketId = if (bIdCol >= 0) c.getString(bIdCol).orEmpty() else "",
                    bucketName = if (bNameCol >= 0) c.getString(bNameCol).orEmpty() else "",
                    sizeBytes = if (sizeCol >= 0) c.getLong(sizeCol) else 0L,
                    latitude = lat,
                    longitude = lon,
                )
            }
        }
        return out
    }
}
