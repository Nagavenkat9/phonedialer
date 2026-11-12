package app.memories.gallery.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface

/**
 * Reads GPS coordinates from photo EXIF data on Android 10+ (where MediaStore no longer
 * exposes them). Results, including "no location", are cached in SharedPreferences,
 * and only a limited number of new files are opened per load so loading stays fast.
 */
class PhotoLocationReader(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @RequiresApi(Build.VERSION_CODES.Q)
    fun applyLocations(photos: List<GalleryItem>): List<GalleryItem> {
        if (photos.isEmpty()) return photos
        val canRead = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        val cached: Map<String, *> = try {
            prefs.all
        } catch (t: Throwable) {
            emptyMap<String, Any?>()
        }
        val editor = prefs.edit()
        var newReads = 0
        var dirty = false

        // Newest first so recent photos get located first.
        val order = photos.indices.sortedByDescending { photos[it].dateTaken }
        val out = photos.toMutableList()
        for (i in order) {
            val item = out[i]
            if (item.isVideo) continue
            val key = cacheKey(item)
            val hit = cached[key] as? String
            if (hit != null) {
                val parsed = parse(hit)
                if (parsed != null) {
                    out[i] = item.copy(latitude = parsed.first, longitude = parsed.second)
                }
                continue
            }
            if (!canRead || newReads >= MAX_NEW_READS_PER_LOAD) continue
            newReads++
            val result = readExif(item) ?: continue // error: retry on a later load
            dirty = true
            if (result.size >= 2 && isValid(result[0], result[1])) {
                editor.putString(key, "${result[0]},${result[1]}")
                out[i] = item.copy(latitude = result[0], longitude = result[1])
            } else {
                editor.putString(key, NONE)
            }
        }
        if (dirty) editor.apply()
        return out
    }

    /** Lat/long, an empty array when the photo has no GPS tag, or null on errors. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun readExif(item: GalleryItem): DoubleArray? {
        return try {
            val original = MediaStore.setRequireOriginal(item.uri)
            context.contentResolver.openInputStream(original)?.use { stream ->
                ExifInterface(stream).latLong ?: DoubleArray(0)
            }
        } catch (t: Throwable) {
            // Missing ACCESS_MEDIA_LOCATION, file gone, unsupported format: skip silently.
            null
        }
    }

    companion object {
        private const val PREFS = "photo_locations_v1"
        private const val NONE = "-"
        private const val MAX_NEW_READS_PER_LOAD = 400

        fun isValid(lat: Double, lon: Double): Boolean =
            !lat.isNaN() && !lon.isNaN() &&
                lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0 &&
                !(lat == 0.0 && lon == 0.0)

        private fun cacheKey(item: GalleryItem): String =
            "${item.key}:${item.dateTaken}:${item.sizeBytes}"

        private fun parse(value: String): Pair<Double, Double>? {
            if (value == NONE) return null
            val parts = value.split(',')
            if (parts.size != 2) return null
            val lat = parts[0].toDoubleOrNull() ?: return null
            val lon = parts[1].toDoubleOrNull() ?: return null
            return if (isValid(lat, lon)) lat to lon else null
        }
    }
}
