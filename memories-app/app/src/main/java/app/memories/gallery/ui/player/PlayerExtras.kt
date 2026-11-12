package app.memories.gallery.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import java.util.Locale

/** One selectable audio or text track. */
internal data class TrackOption(
    val group: Tracks.Group,
    val index: Int,
    val label: String,
    val selected: Boolean,
)

internal fun listTracks(tracks: Tracks, type: Int): List<TrackOption> {
    val out = mutableListOf<TrackOption>()
    tracks.groups.filter { it.type == type }.forEach { group ->
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            val f = group.getTrackFormat(i)
            out += TrackOption(group, i, trackLabel(f, out.size + 1, type), group.isTrackSelected(i))
        }
    }
    return out
}

private fun trackLabel(f: Format, n: Int, type: Int): String {
    val parts = mutableListOf<String>()
    f.label?.takeIf { it.isNotBlank() }?.let { parts += it }
    f.language?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED }?.let { lang ->
        val name = Locale.forLanguageTag(lang).displayLanguage
        if (name.isNotBlank() && parts.none { it.equals(name, ignoreCase = true) }) parts += name
    }
    if (type == C.TRACK_TYPE_AUDIO && f.channelCount != Format.NO_VALUE) {
        parts += when (f.channelCount) {
            1 -> "Mono"
            2 -> "Stereo"
            6 -> "5.1"
            8 -> "7.1"
            else -> "${f.channelCount}ch"
        }
    }
    return if (parts.isEmpty()) "Track $n" else "Track $n · " + parts.joinToString(" · ")
}

internal fun selectTrack(player: Player, option: TrackOption, type: Int) {
    player.trackSelectionParameters = player.trackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(type, false)
        .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.index))
        .build()
}

internal fun disableTrackType(player: Player, type: Int) {
    player.trackSelectionParameters = player.trackSelectionParameters
        .buildUpon()
        .clearOverridesOfType(type)
        .setTrackTypeDisabled(type, true)
        .build()
}

internal fun supportsPip(activity: Activity): Boolean =
    activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

/** Enters picture-in-picture with the video's aspect ratio clamped to the platform limits. */
internal fun enterPip(activity: Activity, videoW: Int, videoH: Int): Boolean {
    if (!supportsPip(activity)) return false
    val ratio = if (videoW > 0 && videoH > 0) {
        val r = videoW.toFloat() / videoH
        when {
            r > 2.39f -> Rational(239, 100)
            r < 1f / 2.39f -> Rational(100, 239)
            else -> Rational(videoW, videoH)
        }
    } else {
        Rational(16, 9)
    }
    return runCatching {
        activity.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(ratio).build())
    }.getOrDefault(false)
}

/** Observes whether [activity] is currently in picture-in-picture mode. */
@Composable
internal fun rememberInPipMode(activity: Activity?): Boolean {
    var inPip by remember(activity) { mutableStateOf(activity?.isInPictureInPictureMode == true) }
    DisposableEffect(activity) {
        val component = activity as? ComponentActivity
        val listener = Consumer<PictureInPictureModeChangedInfo> { info -> inPip = info.isInPictureInPictureMode }
        component?.addOnPictureInPictureModeChangedListener(listener)
        onDispose { component?.removeOnPictureInPictureModeChangedListener(listener) }
    }
    return inPip
}

internal enum class SleepMode(val label: String, val minutes: Int) {
    Off("Off", 0),
    M15("15 minutes", 15),
    M30("30 minutes", 30),
    M60("60 minutes", 60),
    EndOfVideo("End of video", -1),
}

private enum class MenuPage { Main, Audio, Subtitles, Sleep }

/** The top-bar "more" button and its (paged) dropdown. */
@Composable
internal fun PlayerMoreMenu(
    tracks: Tracks,
    loop: Boolean,
    abLabel: String,
    sleep: SleepMode,
    pipAvailable: Boolean,
    onOpen: () -> Unit,
    onToggleLoop: () -> Unit,
    onAbRepeat: () -> Unit,
    onSelectTrack: (TrackOption, Int) -> Unit,
    onSubtitlesOff: () -> Unit,
    onSleep: (SleepMode) -> Unit,
    onPip: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(MenuPage.Main) }
    val close = { expanded = false; page = MenuPage.Main }
    Box {
        IconButton(onClick = { expanded = true; page = MenuPage.Main; onOpen() }) {
            Icon(Icons.Filled.MoreVert, "More options", tint = Color.White)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = close) {
            when (page) {
                MenuPage.Main -> {
                    DropdownMenuItem(
                        text = { Text(if (loop) "Loop: on" else "Loop: off") },
                        leadingIcon = { Icon(if (loop) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat, null) },
                        onClick = { onToggleLoop(); close() },
                    )
                    DropdownMenuItem(
                        text = { Text(abLabel) },
                        leadingIcon = { Icon(Icons.Outlined.Repeat, null) },
                        onClick = { onAbRepeat(); close() },
                    )
                    DropdownMenuItem(
                        text = { Text("Audio track") },
                        leadingIcon = { Icon(Icons.Outlined.Audiotrack, null) },
                        onClick = { page = MenuPage.Audio },
                    )
                    DropdownMenuItem(
                        text = { Text("Subtitles") },
                        leadingIcon = { Icon(Icons.Outlined.Subtitles, null) },
                        onClick = { page = MenuPage.Subtitles },
                    )
                    DropdownMenuItem(
                        text = { Text(if (sleep == SleepMode.Off) "Sleep timer" else "Sleep timer: ${sleep.label}") },
                        leadingIcon = { Icon(Icons.Outlined.Bedtime, null) },
                        onClick = { page = MenuPage.Sleep },
                    )
                    if (pipAvailable) {
                        DropdownMenuItem(
                            text = { Text("Picture-in-picture") },
                            leadingIcon = { Icon(Icons.Outlined.PictureInPictureAlt, null) },
                            onClick = { close(); onPip() },
                        )
                    }
                }
                MenuPage.Audio -> {
                    BackItem("Audio track") { page = MenuPage.Main }
                    val audio = listTracks(tracks, C.TRACK_TYPE_AUDIO)
                    if (audio.isEmpty()) {
                        DropdownMenuItem(text = { Text("No audio tracks") }, onClick = close, enabled = false)
                    }
                    audio.forEach { t ->
                        CheckItem(t.label, t.selected) { onSelectTrack(t, C.TRACK_TYPE_AUDIO); close() }
                    }
                }
                MenuPage.Subtitles -> {
                    BackItem("Subtitles") { page = MenuPage.Main }
                    val text = listTracks(tracks, C.TRACK_TYPE_TEXT)
                    CheckItem("Off", text.none { it.selected }) { onSubtitlesOff(); close() }
                    text.forEach { t ->
                        CheckItem(t.label, t.selected) { onSelectTrack(t, C.TRACK_TYPE_TEXT); close() }
                    }
                }
                MenuPage.Sleep -> {
                    BackItem("Sleep timer") { page = MenuPage.Main }
                    SleepMode.entries.forEach { m ->
                        CheckItem(m.label, m == sleep) { onSleep(m); close() }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackItem(title: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(title, fontWeight = FontWeight.Bold) },
        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") },
        onClick = onClick,
    )
}

@Composable
private fun CheckItem(label: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal) },
        trailingIcon = { if (checked) Icon(Icons.Filled.Check, null) },
        onClick = onClick,
    )
}
