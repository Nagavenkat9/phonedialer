package app.memories.gallery.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import app.memories.gallery.data.GalleryItem

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun Context.shareMedia(item: GalleryItem) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = item.mimeType.ifBlank { if (item.isVideo) "video/*" else "image/*" }
        putExtra(Intent.EXTRA_STREAM, item.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(send, "Share"))
}
