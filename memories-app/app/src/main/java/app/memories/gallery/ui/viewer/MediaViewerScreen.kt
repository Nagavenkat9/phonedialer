package app.memories.gallery.ui.viewer

import android.app.Activity
import android.content.IntentSender
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.memories.gallery.data.GalleryItem
import app.memories.gallery.ui.AppViewModel
import app.memories.gallery.ui.components.DarkSystemBars
import app.memories.gallery.util.Fmt
import app.memories.gallery.util.Trash
import app.memories.gallery.util.shareMedia
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.time.LocalDate

@Composable
fun MediaViewerScreen(vm: AppViewModel, onBack: () -> Unit, onPlayVideo: (String) -> Unit) {
    // Local copy so deletions take effect immediately, before the VM's MediaStore reload lands.
    var items by remember { mutableStateOf(vm.viewerItems) }
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    DarkSystemBars()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val hidden by vm.hiddenItems.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(initialPage = vm.viewerIndex.coerceIn(0, items.lastIndex)) { items.size }
    var chrome by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf<GalleryItem?>(null) }
    var confirmDelete by remember { mutableStateOf<GalleryItem?>(null) }
    var pendingDelete by remember { mutableStateOf<GalleryItem?>(null) }
    val current = items[pagerState.currentPage.coerceIn(0, items.lastIndex)]

    fun removeItem(item: GalleryItem, message: String) {
        val idx = items.indexOfFirst { it.key == item.key }
        if (idx < 0) return
        val newList = items.toMutableList().apply { removeAt(idx) }
        items = newList
        if (newList.isEmpty()) {
            vm.openViewer(emptyList(), 0)
            onBack()
            return
        }
        // Stay on the same position: shows the next item, or the previous one if we deleted the last.
        val newIndex = idx.coerceAtMost(newList.lastIndex)
        vm.openViewer(newList, newIndex)
        scope.launch { pagerState.scrollToPage(newIndex) }
        scope.launch { snackbar.showSnackbar(message) }
    }

    fun showError() {
        scope.launch { snackbar.showSnackbar("Couldn't delete this item") }
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val item = pendingDelete
        pendingDelete = null
        if (item == null || result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        if (Trash.supportsTrash) {
            removeItem(item, "Moved to trash")
        } else {
            // API 29: consent granted via RecoverableSecurityException; retry the delete.
            when (Trash.deletePermanently(context.contentResolver, item.uri)) {
                is Trash.DeleteResult.Deleted -> removeItem(item, "Deleted")
                else -> showError()
            }
        }
    }

    fun launchSender(item: GalleryItem, sender: IntentSender) {
        pendingDelete = item
        deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
    }

    fun deleteLegacy(item: GalleryItem) {
        when (val r = Trash.deletePermanently(context.contentResolver, item.uri)) {
            is Trash.DeleteResult.Deleted -> removeItem(item, "Deleted")
            is Trash.DeleteResult.NeedsConsent -> launchSender(item, r.intentSender)
            is Trash.DeleteResult.Failed -> showError()
        }
    }

    fun requestDelete(item: GalleryItem) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val sender = try {
                Trash.trashRequest(context.contentResolver, listOf(item.uri))
            } catch (e: Exception) {
                null
            }
            if (sender != null) launchSender(item, sender) else showError()
        } else {
            confirmDelete = item
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            key = { items[it].key },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items[page]
            if (item.isVideo) {
                VideoPage(item, onPlay = { onPlayVideo(item.key) })
            } else {
                ZoomableImage(item, onTap = { chrome = !chrome })
            }
        }

        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(
                        current.date.format(Fmt.headerOtherYear),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "${current.dateTime.format(Fmt.time)} · ${Fmt.relative(current.date, LocalDate.now())}",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val fav = current.key in favorites
            val isHidden = current.key in hidden
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                    .navigationBarsPadding()
                    .padding(top = 24.dp),
            ) {
            // Nothing-style filmstrip: drag it to skim through everything quickly.
            if (items.size > 1) Filmstrip(items = items, pagerState = pagerState)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                BarAction(Icons.Outlined.Share, "Share") { context.shareMedia(current) }
                BarAction(
                    if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    if (fav) "Favorited" else "Favorite",
                    tint = if (fav) Color(0xFFFF5C7A) else Color.White,
                ) { vm.toggleFavorite(current.key) }
                BarAction(
                    if (isHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    if (isHidden) "Show in memories" else "Hide from memories",
                ) { if (isHidden) vm.showInMemories(current.key) else vm.hideFromMemories(current.key) }
                BarAction(Icons.Outlined.Info, "Info") { info = current }
                BarAction(Icons.Outlined.Delete, "Delete") { requestDelete(current) }
            }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 160.dp),
        )
    }

    info?.let { InfoSheet(it, inMemories = it.key !in hidden, onDismiss = { info = null }) }

    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete permanently?") },
            text = { Text("This can't be undone on this Android version.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    deleteLegacy(item)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun RowScope.BarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
        IconButton(onClick = onClick) { Icon(icon, label, tint = tint) }
        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun VideoPage(item: GalleryItem, onPlay: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onPlay() }) },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f), modifier = Modifier.size(84.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(52.dp))
            }
        }
        Text(
            Fmt.duration(item.durationMs),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 120.dp),
        )
    }
}

@Composable
private fun ZoomableImage(item: GalleryItem, onTap: () -> Unit) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = boxSize.width * (s - 1f) / 2f
        val maxY = boxSize.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            val target = 2.5f
                            val center = Offset(boxSize.width / 2f, boxSize.height / 2f)
                            scale = target
                            offset = clamp((center - tap) * (target - 1f), target)
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val multiTouch = event.changes.count { it.pressed } > 1
                        if (multiTouch || scale > 1f) {
                            val newScale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            offset = if (newScale == 1f) Offset.Zero else clamp(offset + event.calculatePan(), newScale)
                            scale = newScale
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(item.uri).size(2560).build(),
            contentDescription = item.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoSheet(item: GalleryItem, inMemories: Boolean, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
            Text(item.date.format(Fmt.full), style = MaterialTheme.typography.titleLarge)
            Text(
                "${item.dateTime.format(Fmt.time)} · ${Fmt.relative(item.date, LocalDate.now())}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            InfoRow("Name", item.displayName)
            InfoRow("Album", item.bucketName.ifBlank { "—" })
            if (item.width > 0 && item.height > 0) {
                val mp = item.width.toLong() * item.height / 1_000_000.0
                InfoRow("Resolution", "${item.width} × ${item.height}" + if (!item.isVideo) " (%.1f MP)".format(mp) else "")
            }
            if (item.isVideo) InfoRow("Length", Fmt.duration(item.durationMs))
            InfoRow("Size", Fmt.bytes(item.sizeBytes))
            InfoRow("Type", item.mimeType.ifBlank { "—" })
            InfoRow("Memories", if (inMemories) "Included" else "Hidden (still in your gallery)")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private val StripThumbWidth = 38.dp

/** Index of the filmstrip item closest to the horizontal centre of the strip. */
private fun centerIndex(state: LazyListState): Int {
    val info = state.layoutInfo
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - center) }?.index ?: -1
}

/**
 * Thumbnail scrubber synced with the pager.
 *  - swipe the big photo → the strip follows and centres the current item
 *  - drag/fling the strip → the photo under the centre is shown instantly
 *  - tap a thumbnail → jump to it
 */
@Composable
private fun Filmstrip(items: List<GalleryItem>, pagerState: PagerState) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = pagerState.currentPage)
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var userScrolling by remember { mutableStateOf(false) }

    LaunchedEffect(dragged) { if (dragged) userScrolling = true }
    // When the user's drag + fling settles, snap the current item to the centre.
    LaunchedEffect(dragged, listState.isScrollInProgress) {
        if (userScrolling && !dragged && !listState.isScrollInProgress) {
            userScrolling = false
            listState.animateScrollToItem(pagerState.currentPage)
        }
    }
    // Strip → pager.
    LaunchedEffect(listState, items) {
        snapshotFlow { if (userScrolling) centerIndex(listState) else -1 }
            .distinctUntilChanged()
            .collect { idx ->
                if (idx in items.indices && idx != pagerState.currentPage) pagerState.scrollToPage(idx)
            }
    }
    // Pager → strip.
    LaunchedEffect(pagerState, items) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (!userScrolling && page in items.indices) listState.animateScrollToItem(page)
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth().height(64.dp)) {
        val side = ((maxWidth - StripThumbWidth) / 2).coerceAtLeast(0.dp)
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = side),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(items, key = { _, it -> it.key }) { i, item ->
                val active = i == pagerState.currentPage
                val h by animateDpAsState(if (active) 60.dp else 46.dp, label = "stripHeight")
                val shape = RoundedCornerShape(6.dp)
                Box(
                    Modifier
                        .width(StripThumbWidth)
                        .height(h)
                        .clip(shape)
                        .background(Color.DarkGray)
                        .then(if (active) Modifier.border(2.dp, Color.White, shape) else Modifier)
                        .clickable { scope.launch { pagerState.scrollToPage(i) } },
                ) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (item.isVideo) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            null,
                            tint = Color.White,
                            modifier = Modifier.align(Alignment.Center).size(16.dp),
                        )
                    }
                }
            }
        }
    }
}
