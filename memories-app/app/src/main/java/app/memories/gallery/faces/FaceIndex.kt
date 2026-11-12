package app.memories.gallery.faces

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Size
import app.memories.gallery.data.GalleryItem
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Progress of the current face scan. [total] is the number of photos queued in this run. */
data class FaceScanProgress(val running: Boolean = false, val done: Int = 0, val total: Int = 0)

/**
 * On-device face index (ML Kit bundled face detection, no network).
 * Maps a [GalleryItem.key] to the number of faces found in that photo.
 * Results are cached in SharedPreferences as a JSON object.
 */
object FaceIndex {
    private const val PREFS = "face_index"
    private const val KEY_JSON = "counts"
    private const val TARGET_PX = 640
    private const val SAVE_EVERY = 50

    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

    private val _progress = MutableStateFlow(FaceScanProgress())
    val progress: StateFlow<FaceScanProgress> = _progress.asStateFlow()

    /** Process-wide scope so scanning continues while the user navigates away from Library. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val scanMutex = Mutex()
    private var loaded = false
    private var job: Job? = null

    /** Starts (or restarts with a newer item list) a background scan in the process-wide scope. */
    @Synchronized
    fun startScan(context: Context, items: List<GalleryItem>) {
        val app = context.applicationContext
        job?.cancel()
        job = scope.launch { scan(app, items) }
    }

    /** Scans un-scanned photos (newest first). Safe to call repeatedly; runs one scan at a time. */
    suspend fun scan(context: Context, items: List<GalleryItem>) {
        val app = context.applicationContext
        scanMutex.withLock {
            withContext(Dispatchers.IO) { ensureLoaded(app) }
            val known = _counts.value
            val todo = items.asSequence()
                .filter { !it.isVideo && it.key !in known }
                .sortedByDescending { it.dateTaken }
                .toList()
            if (todo.isEmpty()) {
                _progress.value = FaceScanProgress(running = false, done = 0, total = 0)
                return
            }
            _progress.value = FaceScanProgress(running = true, done = 0, total = todo.size)

            val detector = FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                    .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                    .setMinFaceSize(0.1f)
                    .build()
            )
            val pending = HashMap<String, Int>()
            try {
                todo.forEachIndexed { index, item ->
                    currentCoroutineContext().ensureActive()
                    val faces = withContext(Dispatchers.IO) { detectFaces(app, detector, item) }
                    pending[item.key] = faces
                    if (pending.size >= SAVE_EVERY) {
                        flush(app, pending)
                    }
                    _progress.value = FaceScanProgress(running = true, done = index + 1, total = todo.size)
                }
            } finally {
                withContext(NonCancellable) {
                    flush(app, pending)
                    runCatching { detector.close() }
                    _progress.value = _progress.value.copy(running = false)
                }
            }
        }
    }

    private suspend fun flush(context: Context, pending: HashMap<String, Int>) {
        if (pending.isEmpty()) return
        val merged = _counts.value + pending
        pending.clear()
        _counts.value = merged
        withContext(Dispatchers.IO) { save(context, merged) }
    }

    /** Returns the face count, or 0 if the image can't be decoded / analysed. */
    private suspend fun detectFaces(context: Context, detector: FaceDetector, item: GalleryItem): Int {
        val bitmap = try {
            decodeSmall(context, item)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            null
        } ?: return 0
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            detector.process(image).await().size
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            0
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeSmall(context: Context, item: GalleryItem): Bitmap? {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return resolver.loadThumbnail(item.uri, Size(TARGET_PX, TARGET_PX), null)
        }
        // Pre-Q: read bounds, then decode with inSampleSize.
        var w = item.width
        var h = item.height
        if (w <= 0 || h <= 0) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            w = bounds.outWidth
            h = bounds.outHeight
        }
        if (w <= 0 || h <= 0) return null
        var sample = 1
        while (w / (sample * 2) >= TARGET_PX && h / (sample * 2) >= TARGET_PX) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_JSON, null) ?: return
        val map = HashMap<String, Int>()
        runCatching {
            val obj = JSONObject(json)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.optInt(k, 0)
            }
        }
        _counts.value = map + _counts.value
    }

    private fun save(context: Context, map: Map<String, Int>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_JSON, obj.toString())
            .apply()
    }
}

/** Awaits a Play Services Task without kotlinx-coroutines-play-services. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result -> if (cont.isActive) cont.resume(result) }
    addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
    addOnCanceledListener { if (cont.isActive) cont.cancel() }
}
