package app.memories.gallery.ui.memories

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.MusicOff
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import app.memories.gallery.music.MusicChoice
import app.memories.gallery.music.MusicLibrary
import app.memories.gallery.music.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPickerSheet(
    current: MusicChoice,
    onChoose: (MusicChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var granted by remember { mutableStateOf(MusicLibrary.hasPermission(context)) }
    var asked by remember { mutableStateOf(false) }
    var songs by remember { mutableStateOf<List<Song>?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        asked = true
    }
    // Ask for audio access only once the user actually opens the picker.
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(MusicLibrary.permission)
    }
    LaunchedEffect(granted) {
        songs = if (granted) MusicLibrary.querySongs(context) else null
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                "Memory music",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            PickerRow(
                icon = { Icon(Icons.Outlined.MusicOff, null) },
                title = "No music",
                subtitle = null,
                selected = current == MusicChoice.None,
                onClick = { onChoose(MusicChoice.None) },
            )
            PickerRow(
                icon = { Icon(Icons.Outlined.Shuffle, null) },
                title = "Shuffle my songs",
                subtitle = "A random song from your device each time",
                selected = current == MusicChoice.Shuffle,
                enabled = granted,
                onClick = { onChoose(MusicChoice.Shuffle) },
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            val list = songs
            when {
                !granted -> Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        if (asked) "Allow access to audio to pick songs from your device." else "Requesting access to your music…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (asked) {
                        TextButton(onClick = { launcher.launch(MusicLibrary.permission) }) { Text("Allow access") }
                    }
                }
                list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                list.isEmpty() -> Text(
                    "No songs found on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(24.dp),
                )
                else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(list, key = { it.id }) { song ->
                        PickerRow(
                            icon = { Icon(Icons.Outlined.MusicNote, null) },
                            title = song.title,
                            subtitle = "${song.artist} · ${formatDuration(song.durationMs)}",
                            selected = (current as? MusicChoice.Track)?.songId == song.id,
                            onClick = { onChoose(MusicChoice.Track(song.id)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ListItem(
        leadingContent = icon,
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (subtitle != null) {
            @Composable { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        trailingContent = if (selected) {
            @Composable { Icon(Icons.Filled.Check, "Selected") }
        } else {
            null
        },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
