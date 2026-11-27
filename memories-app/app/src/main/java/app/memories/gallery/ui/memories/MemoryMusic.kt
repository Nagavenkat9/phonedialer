package app.memories.gallery.ui.memories

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.memories.gallery.music.MusicChoice
import app.memories.gallery.music.MusicLibrary
import app.memories.gallery.music.Song
import kotlinx.coroutines.flow.collectLatest

private const val DUCKED_VOLUME = 0.15f
private const val FADE_IN_MS = 2_000
private const val DUCK_MS = 400

/**
 * Background music for a memory: a second, looping ExoPlayer that starts ~30% into the song,
 * fades in, ducks under unmuted videos and follows the story's pause state.
 */
@Composable
fun MemoryMusic(choice: MusicChoice, paused: Boolean, duck: Boolean) {
    val context = LocalContext.current
    val musicPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
        }
    }
    DisposableEffect(Unit) { onDispose { musicPlayer.release() } }

    var song by remember { mutableStateOf<Song?>(null) }
    LaunchedEffect(choice) { song = MusicLibrary.resolve(context, choice) }

    val volume = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        snapshotFlow { volume.value }.collect { musicPlayer.volume = it }
    }

    val target by rememberUpdatedState(if (duck) DUCKED_VOLUME else 1f)
    val s = song
    LaunchedEffect(s?.id) {
        if (s == null) {
            volume.snapTo(0f)
            musicPlayer.stop()
            musicPlayer.clearMediaItems()
            return@LaunchedEffect
        }
        volume.snapTo(0f)
        val startMs = (s.durationMs * 0.3).toLong().coerceAtLeast(0L)
        musicPlayer.setMediaItem(MediaItem.fromUri(s.uri), startMs)
        musicPlayer.prepare()
        var first = true
        snapshotFlow { target }.collectLatest { t ->
            volume.animateTo(t, tween(if (first) FADE_IN_MS else DUCK_MS))
            first = false
        }
    }

    LaunchedEffect(paused, s?.id) {
        musicPlayer.playWhenReady = s != null && !paused
    }
}
