package app.memories.gallery.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.memories.gallery.ui.library.CollectionScreen
import app.memories.gallery.ui.library.HiddenScreen
import app.memories.gallery.ui.memories.MemoryPlayerScreen
import app.memories.gallery.ui.player.VideoPlayerScreen
import app.memories.gallery.ui.settings.SettingsScreen
import app.memories.gallery.ui.viewer.MediaViewerScreen

/** Navigation route for the settings screen. */
const val ROUTE_SETTINGS = "settings"

private fun mediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun hasMediaAccess(context: Context): Boolean =
    mediaPermissions().any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

private fun NavController.back() {
    if (previousBackStackEntry != null) popBackStack()
}

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasMediaAccess(context)) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asked = true
        granted = hasMediaAccess(context)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = hasMediaAccess(context) }
    LaunchedEffect(granted) { if (granted) vm.refresh() }

    if (!granted) {
        PermissionScreen(
            askedBefore = asked,
            onGrant = { launcher.launch(mediaPermissions()) },
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            },
        )
        return
    }

    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                vm = vm,
                onOpenViewer = { items, index ->
                    vm.openViewer(items, index)
                    nav.navigate("viewer")
                },
                onOpenMemory = { nav.navigate("memory/${Uri.encode(it)}") },
                onOpenCollection = { nav.navigate("collection/${Uri.encode(it)}") },
                onOpenHidden = { nav.navigate("hidden") },
            )
        }
        composable("viewer") {
            MediaViewerScreen(
                vm = vm,
                onBack = { nav.back() },
                onPlayVideo = { nav.navigate("player/${Uri.encode(it)}") },
            )
        }
        composable("memory/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            MemoryPlayerScreen(
                vm = vm,
                memoryId = id,
                onClose = { nav.back() },
                onPlayVideo = { nav.navigate("player/${Uri.encode(it)}") },
                onOpenCollection = { nav.navigate("collection/${Uri.encode(it)}") },
            )
        }
        composable("player/{key}") { entry ->
            val key = entry.arguments?.getString("key").orEmpty()
            val media by vm.media.collectAsStateWithLifecycle()
            val item = remember(media, key) { media.firstOrNull { it.key == key } }
            if (item != null) {
                VideoPlayerScreen(item = item, prefs = vm.prefs, onBack = { nav.back() })
            } else {
                Box(Modifier.fillMaxSize())
            }
        }
        composable("collection/{key}") { entry ->
            val key = entry.arguments?.getString("key").orEmpty()
            CollectionScreen(
                vm = vm,
                collectionKey = key,
                onBack = { nav.back() },
                onOpenViewer = { items, index ->
                    vm.openViewer(items, index)
                    nav.navigate("viewer")
                },
            )
        }
        composable("hidden") {
            HiddenScreen(vm = vm, onBack = { nav.back() })
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                onBack = { nav.back() },
                onOpenHidden = { nav.navigate("hidden") },
            )
        }
    }
}

@Composable
private fun PermissionScreen(askedBefore: Boolean, onGrant: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(112.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Relive your best moments",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Memories turns your photos and videos into stories — 1 year ago, 2 years ago, your favorites and more. " +
                "Everything stays on your phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) {
            Text("Allow access to photos & videos")
        }
        if (askedBefore) {
            TextButton(onClick = onOpenSettings) { Text("Open settings") }
        }
    }
}
