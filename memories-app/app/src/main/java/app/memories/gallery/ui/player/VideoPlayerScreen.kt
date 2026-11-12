package app.memories.gallery.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Settings
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.memories.gallery.R
import app.memories.gallery.data.GalleryItem
import app.memories.gallery.data.UserPrefs
import app.memories.gallery.util.Fmt
import app.memories.gallery.util.findActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private sealed interface Hud {
    data class Seek(val target: Long, val delta: Long) : Hud
    data class Brightness(val level: Float) : Hud
    data class Volume(val level: Float) : Hud
    data class Message(val text: String) : Hud
}

private data class Skip(val dir: Int, val seconds: Int, val at: Long)

private data class ResizeOption(val label: String, val mode: Int)

@OptIn(UnstableApi::class)
private val resizeOptions = listOf(
    ResizeOption("Fit to screen", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    ResizeOption("Crop to fill", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    ResizeOption("Stretch", AspectRatioFrameLayout.RESIZE_MODE_FILL),
)

private val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

private const val SKIP_MS = 10_000L
private const val CHAIN_WINDOW_MS = 800L

private fun currentBrightness(activity: Activity): Float {
    val b = activity.window.attributes.screenBrightness
    if (b >= 0f) return b
    return runCatching {
        Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    }.getOrDefault(0.5f)
}

private fun speedLabel(s: Float): String =
    if (s == s.toInt().toFloat()) "${s.toInt()}x" else "${s}x"

/**
 * Full-screen video player with MX-Player style controls:
 *  - drag left/right anywhere to seek (live preview)
 *  - double-tap left/right to skip 10s back/forward (keep tapping to add +10s)
 *  - double-tap centre to play/pause
 *  - swipe up/down on the left for brightness, right for volume
 *  - pinch to zoom, aspect modes (fit / crop / stretch)
 *  - press & hold for 2x speed, playback speed menu 0.25x–3x
 *  - lock controls, rotate, auto-rotate to the video's orientation
 *  - remembers where you stopped and resumes
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(item: GalleryItem, prefs: UserPrefs, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    val resumeFrom = remember(item.key) {
        val saved = prefs.resumePosition(item.key)
        if (saved > 3_000 && (item.durationMs <= 0 || saved < item.durationMs - 3_000)) saved else 0L
    }
    val player = remember(item.key) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(item.uri))
            if (resumeFrom > 0) seekTo(resumeFrom)
            prepare()
            playWhenReady = true
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var ended by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(resumeFrom) }
    var duration by remember { mutableLongStateOf(item.durationMs) }
    var controls by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var speedMenu by remember { mutableStateOf(false) }
    var resizeIndex by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var hud by remember { mutableStateOf<Hud?>(null) }
    var gestureActive by remember { mutableStateOf(false) }
    var skip by remember { mutableStateOf<Skip?>(null) }
    var holdFast by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    var manualOrientation by remember { mutableStateOf(false) }
    var brightness by remember { mutableFloatStateOf(activity?.let { currentBrightness(it) } ?: 0.5f) }
    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume) }
    var seekStart by remember { mutableLongStateOf(0L) }
    var seekTarget by remember { mutableLongStateOf(0L) }
    var wasPlaying by remember { mutableStateOf(false) }
    var loop by remember { mutableStateOf(false) }
    var abA by remember { mutableLongStateOf(-1L) }
    var abB by remember { mutableLongStateOf(-1L) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var sleep by remember { mutableStateOf(SleepMode.Off) }
    var pipRequested by remember { mutableStateOf(false) }
    val inPip = rememberInPipMode(activity)
    val pipAvailable = remember(activity) { activity?.let { supportsPip(it) } == true }

    // Player events.
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (player.duration > 0) duration = player.duration
                ended = state == Player.STATE_ENDED
                if (ended) controls = true
            }

            override fun onTracksChanged(t: Tracks) {
                tracks = t
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (manualOrientation || activity?.isInPictureInPictureMode == true || videoSize.width <= 0 || videoSize.height <= 0) return
                var w = videoSize.width * videoSize.pixelWidthHeightRatio
                var h = videoSize.height.toFloat()
                if (videoSize.unappliedRotationDegrees == 90 || videoSize.unappliedRotationDegrees == 270) {
                    val t = w; w = h; h = t
                }
                activity?.requestedOrientation =
                    if (w > h) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
        }
        player.addListener(listener)
        onDispose {
            val pos = if (player.playbackState == Player.STATE_ENDED) 0L else player.currentPosition
            prefs.saveResumePosition(item.key, pos)
            player.removeListener(listener)
            player.release()
        }
    }

    // Immersive full screen, keep screen on, restore everything on exit.
    DisposableEffect(Unit) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val attrs = window.attributes
                attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = attrs
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Keep playing while shrinking into PiP; pause when actually backgrounded / PiP closed.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        if (!pipRequested && activity?.isInPictureInPictureMode != true) player.pause()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }
    LaunchedEffect(inPip) {
        if (inPip) {
            controls = false
            hud = null
            speedMenu = false
        } else {
            pipRequested = false
        }
    }

    // A-B repeat: jump back to A whenever playback passes B.
    LaunchedEffect(abA, abB) {
        if (abA < 0 || abB <= abA) return@LaunchedEffect
        while (true) {
            if (!scrubbing && player.currentPosition >= abB) {
                player.seekTo(abA)
                position = abA
            }
            delay(50)
        }
    }

    // Sleep timer.
    LaunchedEffect(sleep) {
        when {
            sleep.minutes > 0 -> {
                delay(sleep.minutes * 60_000L)
                player.pause()
                sleep = SleepMode.Off
                hud = Hud.Message("Sleep timer: paused")
            }
            sleep == SleepMode.EndOfVideo -> {
                // Stop at the end instead of looping or repeating A-B.
                player.repeatMode = Player.REPEAT_MODE_OFF
                loop = false
                abA = -1L
                abB = -1L
                while (true) {
                    val d = player.duration
                    if (player.playbackState == Player.STATE_ENDED || (d > 0 && player.currentPosition >= d - 250)) {
                        player.pause()
                        sleep = SleepMode.Off
                        hud = Hud.Message("Sleep timer: paused")
                        break
                    }
                    delay(200)
                }
            }
        }
    }

    LaunchedEffect(player) {
        if (resumeFrom > 0) hud = Hud.Message("Resumed at ${Fmt.duration(resumeFrom)}")
        while (true) {
            if (!scrubbing) position = player.currentPosition
            if (player.duration > 0) duration = player.duration
            delay(200)
        }
    }
    LaunchedEffect(controls, isPlaying, interaction, locked) {
        if (controls && (isPlaying || locked)) {
            delay(3_500)
            controls = false
        }
    }
    LaunchedEffect(hud, gestureActive) {
        if (hud != null && !gestureActive) {
            delay(800)
            hud = null
        }
    }
    LaunchedEffect(skip) {
        if (skip != null) {
            delay(CHAIN_WINDOW_MS)
            skip = null
        }
    }

    BackHandler(enabled = locked) {
        controls = true
        hud = Hud.Message("Screen is locked — tap the lock to unlock")
    }

    fun seekTo(ms: Long) {
        val d = duration
        val target = if (d > 0) ms.coerceIn(0L, d) else ms.coerceAtLeast(0L)
        player.seekTo(target)
        position = target
    }

    fun togglePlay() {
        when {
            ended -> {
                player.seekTo(0)
                player.play()
            }
            player.isPlaying -> player.pause()
            else -> player.play()
        }
        interaction++
    }

    fun skipBy(dir: Int) {
        val now = SystemClock.uptimeMillis()
        val cur = skip
        val seconds = if (cur != null && cur.dir == dir && now - cur.at < CHAIN_WINDOW_MS) cur.seconds + 10 else 10
        skip = Skip(dir, seconds, now)
        seekTo(player.currentPosition + dir * SKIP_MS)
    }

    fun toggleLoop() {
        loop = !loop
        player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        if (loop && sleep == SleepMode.EndOfVideo) sleep = SleepMode.Off
        hud = Hud.Message(if (loop) "Loop on" else "Loop off")
    }

    fun abRepeat() {
        val pos = player.currentPosition
        when {
            abA < 0 -> {
                abA = pos
                hud = Hud.Message("A set at ${Fmt.duration(pos)}")
            }
            abB < 0 -> {
                if (pos <= abA + 500) {
                    hud = Hud.Message("B must be after A")
                } else {
                    abB = pos
                    if (sleep == SleepMode.EndOfVideo) sleep = SleepMode.Off
                    player.seekTo(abA)
                    position = abA
                    hud = Hud.Message("Repeating A-B")
                }
            }
            else -> {
                abA = -1L
                abB = -1L
                hud = Hud.Message("A-B repeat off")
            }
        }
        interaction++
    }

    fun startPip() {
        val act = activity ?: return
        val vs = player.videoSize
        val w = (vs.width * vs.pixelWidthHeightRatio).roundToInt()
        val h = vs.height
        val (pw, ph) = if (vs.unappliedRotationDegrees == 90 || vs.unappliedRotationDegrees == 270) h to w else w to h
        pipRequested = true
        if (!enterPip(act, pw, ph)) {
            pipRequested = false
            hud = Hud.Message("Picture-in-picture unavailable")
        }
    }

    fun rotate() {
        manualOrientation = true
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        activity?.requestedOrientation =
            if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        interaction++
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(locked) {
                if (locked) {
                    detectTapGestures(onTap = { controls = !controls })
                } else {
                    detectTapGestures(
                        onTap = { pos ->
                            val dir = when {
                                pos.x < size.width * 0.4f -> -1
                                pos.x > size.width * 0.6f -> 1
                                else -> 0
                            }
                            val cur = skip
                            if (cur != null && dir != 0 && dir == cur.dir &&
                                SystemClock.uptimeMillis() - cur.at < CHAIN_WINDOW_MS
                            ) {
                                skipBy(dir) // keep tapping to keep skipping, like MX Player
                            } else {
                                controls = !controls
                                interaction++
                            }
                        },
                        onDoubleTap = { pos ->
                            when {
                                pos.x < size.width * 0.4f -> skipBy(-1)
                                pos.x > size.width * 0.6f -> skipBy(1)
                                else -> togglePlay()
                            }
                        },
                        onPress = {
                            // Press & hold → temporary 2x speed.
                            var held = false
                            val job = scope.launch {
                                delay(600)
                                if (!gestureActive) {
                                    held = true
                                    holdFast = true
                                    player.setPlaybackSpeed(2f)
                                }
                            }
                            tryAwaitRelease()
                            job.cancel()
                            if (held) {
                                holdFast = false
                                player.setPlaybackSpeed(speed)
                            }
                        },
                    )
                }
            }
            .pointerInput(locked) {
                if (locked) return@pointerInput
                detectPlayerDragGestures(
                    onSeekStart = {
                        gestureActive = true
                        scrubbing = true
                        seekStart = player.currentPosition
                        seekTarget = seekStart
                        wasPlaying = player.isPlaying
                        player.pause()
                        player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                    },
                    onSeek = { dx ->
                        val d = duration
                        val range = if (d > 0) d.coerceAtMost(240_000L) else 120_000L
                        val msPerPx = range.toFloat() / size.width.coerceAtLeast(1)
                        val next = seekTarget + (dx * msPerPx).toLong()
                        seekTarget = if (d > 0) next.coerceIn(0L, d) else next.coerceAtLeast(0L)
                        position = seekTarget
                        player.seekTo(seekTarget)
                        hud = Hud.Seek(seekTarget, seekTarget - seekStart)
                    },
                    onSeekEnd = {
                        player.setSeekParameters(SeekParameters.EXACT)
                        player.seekTo(seekTarget)
                        if (wasPlaying) player.play()
                        scrubbing = false
                        gestureActive = false
                    },
                    onVerticalStart = { left ->
                        gestureActive = true
                        if (left) {
                            activity?.let { brightness = currentBrightness(it) }
                        } else {
                            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
                        }
                    },
                    onVertical = { left, dy ->
                        val delta = -dy / (size.height * 0.75f)
                        if (left) {
                            brightness = (brightness + delta).coerceIn(0.01f, 1f)
                            activity?.window?.let { w ->
                                val attrs = w.attributes
                                attrs.screenBrightness = brightness
                                w.attributes = attrs
                            }
                            hud = Hud.Brightness(brightness)
                        } else {
                            volume = (volume + delta).coerceIn(0f, 1f)
                            runCatching {
                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * maxVolume).roundToInt(), 0)
                            }
                            hud = Hud.Volume(volume)
                        }
                    },
                    onVerticalEnd = { gestureActive = false },
                    onZoom = { factor ->
                        gestureActive = true
                        zoom = (zoom * factor).coerceIn(0.5f, 4f)
                        hud = Hud.Message("Zoom ${(zoom * 100).roundToInt()}%")
                    },
                    onZoomEnd = {
                        if (zoom in 0.95f..1.05f) zoom = 1f
                        gestureActive = false
                    },
                )
            },
    ) {
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.texture_player_view, null) as PlayerView).apply {
                    this.player = player
                }
            },
            update = { it.resizeMode = resizeOptions[resizeIndex].mode },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                },
        )

        if (!inPip) skip?.let { s ->
            SkipBubble(s, Modifier.align(if (s.dir < 0) Alignment.CenterStart else Alignment.CenterEnd))
        }

        if (!inPip) hud?.let { HudView(it, duration, Modifier.align(Alignment.Center)) }

        if (holdFast && !inPip) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("2x", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.FastForward, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Locked: only the unlock button.
        AnimatedVisibility(
            visible = controls && locked && !inPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp),
        ) {
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f)) {
                IconButton(onClick = { locked = false; controls = true; interaction++ }) {
                    Icon(Icons.Outlined.Lock, "Unlock", tint = Color.White)
                }
            }
        }

        AnimatedVisibility(
            visible = controls && !locked && !inPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
                // Top bar.
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.displayName,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            item.date.format(Fmt.headerOtherYear),
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Box {
                        TextButton(onClick = { speedMenu = true; interaction++ }) {
                            Text(speedLabel(speed), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                            speeds.forEach { s ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            speedLabel(s),
                                            fontWeight = if (s == speed) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    },
                                    onClick = {
                                        speed = s
                                        player.setPlaybackSpeed(s)
                                        speedMenu = false
                                        hud = Hud.Message("Speed ${speedLabel(s)}")
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        resizeIndex = (resizeIndex + 1) % resizeOptions.size
                        zoom = 1f
                        hud = Hud.Message(resizeOptions[resizeIndex].label)
                        interaction++
                    }) { Icon(Icons.Outlined.AspectRatio, "Aspect ratio", tint = Color.White) }
                    IconButton(onClick = { rotate() }) {
                        Icon(Icons.Outlined.ScreenRotation, "Rotate", tint = Color.White)
                    }
                    IconButton(onClick = {
                        locked = true
                        hud = Hud.Message("Screen locked")
                    }) { Icon(Icons.Outlined.LockOpen, "Lock", tint = Color.White) }
                    PlayerMoreMenu(
                        tracks = tracks,
                        loop = loop,
                        abLabel = when {
                            abA < 0 -> "A-B repeat: set A"
                            abB < 0 -> "A-B repeat: set B"
                            else -> "A-B repeat: clear"
                        },
                        sleep = sleep,
                        pipAvailable = pipAvailable,
                        onOpen = { interaction++ },
                        onToggleLoop = { toggleLoop() },
                        onAbRepeat = { abRepeat() },
                        onSelectTrack = { t, type ->
                            selectTrack(player, t, type)
                            hud = Hud.Message(t.label)
                        },
                        onSubtitlesOff = {
                            disableTrackType(player, C.TRACK_TYPE_TEXT)
                            hud = Hud.Message("Subtitles off")
                        },
                        onSleep = { m ->
                            sleep = m
                            hud = Hud.Message(if (m == SleepMode.Off) "Sleep timer off" else "Sleep: ${m.label}")
                        },
                        onPip = { startPip() },
                    )
                }

                // Centre controls.
                Row(
                    Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    RoundControl(Icons.Filled.Replay10, "Back 10 seconds", 52) { skipBy(-1); interaction++ }
                    RoundControl(
                        when {
                            ended -> Icons.Filled.Replay
                            isPlaying -> Icons.Filled.Pause
                            else -> Icons.Filled.PlayArrow
                        },
                        "Play / pause",
                        72,
                    ) { togglePlay() }
                    RoundControl(Icons.Filled.Forward10, "Forward 10 seconds", 52) { skipBy(1); interaction++ }
                }

                // Bottom seek bar.
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(Fmt.duration(position), color = Color.White, style = MaterialTheme.typography.labelLarge)
                        val badges = buildList {
                            if (abA >= 0) {
                                add("A " + Fmt.duration(abA) + if (abB >= 0) "  ·  B " + Fmt.duration(abB) else "  ·  B –")
                            }
                            if (loop) add("Loop")
                            if (sleep != SleepMode.Off) add("Sleep: " + sleep.label)
                        }
                        if (badges.isNotEmpty()) {
                            Spacer(Modifier.width(12.dp))
                            Text(
                                badges.joinToString("   "),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "-" + Fmt.duration((duration - position).coerceAtLeast(0)) + "  /  " + Fmt.duration(duration),
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Slider(
                        value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                        onValueChange = { v ->
                            scrubbing = true
                            position = (v * duration).toLong()
                            interaction++
                        },
                        onValueChangeFinished = {
                            seekTo(position)
                            scrubbing = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        ),
                    )
                    Text(
                        "Double-tap sides ±10s · drag to seek · swipe left/right edge for brightness/volume · hold for 2x",
                        color = Color.White.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun RoundControl(icon: ImageVector, label: String, sizeDp: Int, onClick: () -> Unit) {
    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.4f), modifier = Modifier.size(sizeDp.dp)) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size((sizeDp * 0.6f).dp))
        }
    }
}

@Composable
private fun SkipBubble(skip: Skip, modifier: Modifier) {
    val shape = if (skip.dir < 0) {
        RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
    } else {
        RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
    }
    Box(
        modifier
            .fillMaxHeight()
            .fillMaxWidth(0.35f)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (skip.dir < 0) Icons.Filled.FastRewind else Icons.Filled.FastForward,
                null,
                tint = Color.White,
                modifier = Modifier.size(40.dp),
            )
            Text("${skip.seconds} seconds", color = Color.White, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun HudView(hud: Hud, duration: Long, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color.Black.copy(alpha = 0.65f), modifier = modifier) {
        Column(
            Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (hud) {
                is Hud.Seek -> {
                    val sign = if (hud.delta >= 0) "+" else "-"
                    Text(
                        "$sign${Fmt.duration(abs(hud.delta))}",
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${Fmt.duration(hud.target)} / ${Fmt.duration(duration)}",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                is Hud.Brightness -> LevelHud(Icons.Filled.BrightnessMedium, "Brightness", hud.level)
                is Hud.Volume -> LevelHud(
                    if (hud.level <= 0f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    "Volume",
                    hud.level,
                )
                is Hud.Message -> Text(hud.text, color = Color.White, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun LevelHud(icon: ImageVector, label: String, level: Float) {
    Icon(icon, label, tint = Color.White, modifier = Modifier.size(32.dp))
    Spacer(Modifier.height(6.dp))
    Text("${(level * 100).roundToInt()}%", color = Color.White, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    LinearProgressIndicator(
        progress = { level },
        modifier = Modifier.width(150.dp),
        color = Color.White,
        trackColor = Color.White.copy(alpha = 0.3f),
    )
}
