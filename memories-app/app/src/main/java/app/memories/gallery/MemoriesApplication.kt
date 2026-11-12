package app.memories.gallery

import android.app.Application
import app.memories.gallery.notify.MemoryReminder
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder

class MemoriesApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        try {
            MemoryReminder.schedule(this)
        } catch (_: Throwable) {
            // Scheduling the reminder must never break app start-up.
        }
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}
