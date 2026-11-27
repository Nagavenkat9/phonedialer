package app.memories.gallery.util

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi

/** Helpers for removing media from the device's MediaStore. */
object Trash {

    /** True when the platform supports a recoverable trash (API 30+). */
    val supportsTrash: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * API 30+: builds a system request that moves [uris] to the trash. Launch the returned
     * [IntentSender]; the system shows its own confirmation and returns RESULT_OK on success.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun trashRequest(resolver: ContentResolver, uris: List<Uri>): IntentSender =
        MediaStore.createTrashRequest(resolver, uris, true).intentSender

    sealed interface DeleteResult {
        data object Deleted : DeleteResult

        /** API 29 scoped storage: the user must grant access first, then retry the delete. */
        data class NeedsConsent(val intentSender: IntentSender) : DeleteResult

        data class Failed(val error: Throwable?) : DeleteResult
    }

    /** API 26–29: permanently deletes [uri]. On API 29 this may require user consent first. */
    fun deletePermanently(resolver: ContentResolver, uri: Uri): DeleteResult =
        try {
            val rows = resolver.delete(uri, null, null)
            if (rows > 0) DeleteResult.Deleted else DeleteResult.Failed(null)
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                DeleteResult.NeedsConsent(e.userAction.actionIntent.intentSender)
            } else {
                DeleteResult.Failed(e)
            }
        } catch (e: Exception) {
            DeleteResult.Failed(e)
        }
}
