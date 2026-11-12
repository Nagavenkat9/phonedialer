package app.memories.gallery.notify

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * On Android 13+, asks once for POST_NOTIFICATIONS so the daily memory reminder can be shown.
 * Remembers that it asked, so the user is never prompted again by this composable.
 */
@Composable
fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* the worker re-checks the permission before posting */ }

    LaunchedEffect(Unit) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted || sp.getBoolean(KEY_ASKED, false)) return@LaunchedEffect
        sp.edit().putBoolean(KEY_ASKED, true).apply()
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private const val PREFS = "notification_permission"
private const val KEY_ASKED = "asked_post_notifications"
