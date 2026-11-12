package app.memories.gallery.ui.memories

import android.os.Build
import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.MusicOff
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.memories.gallery.R
import app.memories.gallery.data.GalleryItem
import app.memories.gallery.memories.Memory
import app.memories.gallery.music.MusicChoice
import app.memories.gallery.music.MusicLibrary
import app.memories.gallery.ui.AppViewModel
import app.memories.gallery.ui.components.DarkSystemBars
import app.memories.gallery.ui.components.DaySheet
import app.memories.gallery.util.Fmt
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val PHOTO_DURATION_MS = 5_000

@Composable
fun MemoryPlayerScreen(
    vm: AppViewModel,
    memoryId: String,
    onClose: () -> Unit,
    onPlayVideo: (String) -> Unit,
    onOpenCollection: (String) -> Unit,
) {
    val all by vm.allMemories.collectAsStateWithLifecycle()
    val media by vm.media.collectAsStateWithLifecycle()
    // Snapshot the memory when it opens so it doesn't reshuffle while you watch.
    var snapshot by remember { mutableStateOf<Memory?>(null) }
    val found = remember(all, memoryId) { all.firstOrNull { it.id == memoryId } }
    LaunchedEffect(found) { if (snapshot == null && found != null) snapshot = found }
    val memory = snapshot ?: found
    if (memory == null) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        LaunchedEffect(all, media) { if (media.isNotEmpty() && all.isNotEmpty()) onClose() }
        return
    }
    MemoryPlayer(memory, media, vm, onClose, onPlayVideo, onOpenCollection)
}

@OptIn(UnstableApi::class)
@Composable
private fun MemoryPlayer(
    memory: Memory,
    media: List<GalleryItem>,
    vm: AppViewModel,
    onClose: () -> Unit,
    onPlayVideo: (String) -> Unit,
    onOpenCollection: (String) -> Unit,
) {
    DarkSystemBars()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val close by rememberUpdatedState(onClose)

    val byKey = remember(media) { media.associateBy { it.key } }
    var keys by rememberSaveable(memory.id) { mutableStateOf(memory.items.map { it.key }) }
    val items: List<GalleryItem> = remember(keys, byKey) { keys.mapNotNull { byKey[it] } }
    var index by rememberSaveable(memory.id) { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var muted by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showDay by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val snackbar = remember { SnackbarHostState() }
    val preparedKey = remember { arrayOfNulls<String>(1) }
    var restartTick by remember { mutableIntStateOf(0) }
    var musicChoice by remember { mutableStateOf(MusicLibrary.loadChoice(context)) }
    var showMusic by remember { mutableStateOf(false) }

    val player = remember {
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_OFF }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { paused = true }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }

    if (items.isEmpty()) {
        LaunchedEffect(Unit) { close() }
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    val current = items[index.coerceIn(0, items.lastIndex)]

    // Background music: duck under a playing (unmuted) video, full volume for photos.
    MemoryMusic(choice = musicChoice, paused = paused, duck = current.isVideo && !muted)

    fun next() {
        if (index < items.lastIndex) index++ else close()
    }
    fun prev() {
        if (index > 0) {
            index--
        } else {
            preparedKey[0] = null
            restartTick++
        }
    }

    // Drives timing: photos advance after a few seconds, videos when they finish.
    LaunchedEffect(current.key, paused, restartTick) {
        if (preparedKey[0] != current.key) {
            progress.snapTo(0f)
            if (current.isVideo) {
                player.setMediaItem(MediaItem.fromUri(current.uri))
                player.prepare()
            } else {
                player.stop()
                player.clearMediaItems()
            }
            preparedKey[0] = current.key
        }
        if (paused) {
            player.pause()
            return@LaunchedEffect
        }
        if (!current.isVideo) {
            val remaining = ((1f - progress.value) * PHOTO_DURATION_MS).toInt().coerceAtLeast(1)
            progress.animateTo(1f, tween(remaining, easing = LinearEasing))
            next()
        } else {
            player.play()
            while (true) {
                val d = player.duration
                if (d > 0) progress.snapTo((player.currentPosition.toFloat() / d).coerceIn(0f, 1f))
                if (player.playbackState == Player.STATE_ENDED) {
                    next()
                    break
                }
                delay(50)
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        // Press and hold pauses, like stories.
                        var held = false
                        val hold = scope.launch {
                            delay(200)
                            held = true
                            paused = true
                        }
                        tryAwaitRelease()
                        hold.cancel()
                        if (held) paused = false
                    },
                    onTap = { pos ->
                        if (pos.x < size.width * 0.3f) prev() else next()
                    },
                )
            }
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total > 220f) close() },
                    onVerticalDrag = { change, dy ->
                        total += dy
                        change.consume()
                    },
                )
            },
    ) {
        // Blurred backdrop so every photo/video fills the screen beautifully.
        AsyncImage(
            model = current.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(40.dp) else Modifier)
                .graphicsLayer { alpha = 0.55f },
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))

        if (current.isVideo) {
            AndroidView(
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(R.layout.texture_player_view, null) as PlayerView).apply {
                        this.player = player
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Slow Ken Burns zoom while the photo is on screen.
            AsyncImage(
                model = current.uri,
                contentDescription = current.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = 1f + 0.08f * progress.value
                        scaleX = s
                        scaleY = s
                    },
            )
        }

        // Top: progress segments + title + the day of the current item.
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            ProgressSegments(count = items.size, index = index, progress = { progress.value })
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(
                        memory.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${current.date.format(Fmt.headerOtherYear)} · ${Fmt.relative(current.date, LocalDate.now())}",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (paused) Icon(Icons.Filled.Pause, "Paused", tint = Color.White)
                IconButton(onClick = { showMusic = true; paused = true }) {
                    Icon(
                        if (musicChoice == MusicChoice.None) Icons.Outlined.MusicOff else Icons.Outlined.MusicNote,
                        "Music",
                        tint = Color.White,
                    )
                }
                IconButton(onClick = { showDay = true; paused = true }) {
                    Icon(Icons.Outlined.Info, "About this day", tint = Color.White)
                }
                Box {
                    IconButton(onClick = { showMenu = true; paused = true }) {
                        Icon(Icons.Filled.MoreVert, "More", tint = Color.White)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false; paused = false }) {
                        DropdownMenuItem(
                            text = { Text("See everything from this day") },
                            leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null) },
                            onClick = {
                                showMenu = false
                                onOpenCollection("day:${current.date}")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Don't show this memory") },
                            leadingIcon = { Icon(Icons.Outlined.VisibilityOff, null) },
                            onClick = {
                                showMenu = false
                                vm.hideMemory(memory.id)
                                close()
                            },
                        )
                    }
                }
                IconButton(onClick = { close() }) {
                    Icon(Icons.Filled.Close, "Close", tint = Color.White)
                }
            }
        }

        // Bottom actions.
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val fav = current.key in favorites
            ActionButton(
                icon = { Icon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null, tint = if (fav) Color(0xFFFF5C7A) else Color.White) },
                label = if (fav) "Favorited" else "Favorite",
                onClick = { vm.toggleFavorite(current.key) },
            )
            ActionButton(
                icon = { Icon(Icons.Outlined.VisibilityOff, null, tint = Color.White) },
                label = "Remove",
                onClick = {
                    val removedKey = current.key
                    val removedAt = index
                    vm.hideFromMemories(removedKey)
                    keys = keys - removedKey
                    if (keys.isEmpty()) {
                        close()
                    } else if (index > keys.lastIndex) {
                        index = keys.lastIndex
                    }
                    scope.launch {
                        val result = snackbar.showSnackbar(
                            message = "Removed from memories. Still in your gallery.",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            vm.showInMemories(removedKey)
                            keys = keys.toMutableList().apply { add(removedAt.coerceAtMost(size), removedKey) }
                            index = removedAt.coerceAtMost(keys.lastIndex)
                        }
                    }
                },
            )
            if (current.isVideo) {
                ActionButton(
                    icon = {
                        Icon(
                            if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            null,
                            tint = Color.White,
                        )
                    },
                    label = if (muted) "Unmute" else "Mute",
                    onClick = { muted = !muted },
                )
                ActionButton(
                    icon = { Icon(Icons.Outlined.OndemandVideo, null, tint = Color.White) },
                    label = "Full video",
                    onClick = { onPlayVideo(current.key) },
                )
            }
        }

        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 80.dp),
        )
    }

    if (showMusic) {
        MusicPickerSheet(
            current = musicChoice,
            onChoose = { choice ->
                musicChoice = choice
                MusicLibrary.saveChoice(context, choice)
                showMusic = false
                paused = false
            },
            onDismiss = { showMusic = false; paused = false },
        )
    }

    if (showDay) {
        DaySheet(
            memory = memory,
            onDismiss = { showDay = false; paused = false },
            onPlay = { showDay = false; paused = false },
            onOpenDay = { day ->
                showDay = false
                onOpenCollection("day:$day")
            },
            onHide = {
                showDay = false
                vm.hideMemory(memory.id)
                close()
            },
        )
    }
}

@Composable
private fun ProgressSegments(count: Int, index: Int, progress: () -> Float) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.35f))
                    .drawBehind {
                        val fraction = when {
                            i < index -> 1f
                            i == index -> progress()
                            else -> 0f
                        }
                        drawRect(Color.White, size = Size(size.width * fraction, size.height))
                    },
            )
        }
    }
}

@Composable
private fun ActionButton(icon: @Composable () -> Unit, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) { icon() }
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}
