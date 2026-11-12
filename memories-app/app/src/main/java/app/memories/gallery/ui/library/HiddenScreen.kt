package app.memories.gallery.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.memories.gallery.data.GalleryItem
import app.memories.gallery.ui.AppViewModel
import app.memories.gallery.ui.components.EmptyState
import app.memories.gallery.ui.components.MediaThumb
import app.memories.gallery.ui.components.ScreenHeader
import coil.compose.AsyncImage

/** Manage what's been removed from memories. Nothing here is deleted from the gallery. */
@Composable
fun HiddenScreen(vm: AppViewModel, onBack: () -> Unit) {
    val media by vm.media.collectAsStateWithLifecycle()
    val hiddenKeys by vm.hiddenItems.collectAsStateWithLifecycle()
    val hiddenMemoryIds by vm.hiddenMemories.collectAsStateWithLifecycle()
    val allMemories by vm.allMemories.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    var confirmAll by remember { mutableStateOf(false) }

    val hiddenItems = remember(media, hiddenKeys) { media.filter { it.key in hiddenKeys } }
    val hiddenMemories = remember(allMemories, hiddenMemoryIds) {
        hiddenMemoryIds.map { id -> id to allMemories.firstOrNull { it.id == id } }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader("Hidden from memories", onBack = onBack) {
            if (hiddenItems.isNotEmpty()) {
                TextButton(onClick = { confirmAll = true }) { Text("Restore all") }
            }
        }
        if (hiddenItems.isEmpty() && hiddenMemories.isEmpty()) {
            EmptyState(
                Icons.Outlined.VisibilityOff,
                "Nothing hidden",
                "When you remove a photo or video from memories it shows up here. It always stays in your gallery.",
            )
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "These stay in your gallery — they just won't appear in memories. Tap one to bring it back.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (hiddenMemories.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Memories", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 8.dp))
                }
                items(hiddenMemories, key = { "m-" + it.first }, span = { GridItemSpan(maxLineSpan) }) { (id, memory) ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (memory != null) {
                            AsyncImage(
                                model = memory.cover.uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                            )
                        } else {
                            Box(
                                Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Outlined.AutoAwesome, null) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(memory?.title ?: "Past memory", style = MaterialTheme.typography.titleSmall)
                            Text(
                                memory?.subtitle ?: id,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { vm.showMemory(id) }) { Text("Show") }
                    }
                }
            }
            if (hiddenItems.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Photos & videos (${hiddenItems.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp, 8.dp),
                    )
                }
                items(hiddenItems, key = { it.key }) { item ->
                    HiddenThumb(item, item.key in favorites) { vm.showInMemories(item.key) }
                }
            }
        }
    }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("Bring everything back?") },
            text = { Text("All ${hiddenItems.size} photos and videos can appear in memories again.") },
            confirmButton = {
                TextButton(onClick = { vm.showAllInMemories(); confirmAll = false }) { Text("Restore all") }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HiddenThumb(item: GalleryItem, isFavorite: Boolean, onRestore: () -> Unit) {
    MediaThumb(
        item = item,
        isFavorite = isFavorite,
        overlay = {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.5f)) {
                    Icon(
                        Icons.Outlined.Visibility,
                        "Show in memories",
                        tint = Color.White,
                        modifier = Modifier.padding(8.dp).size(20.dp),
                    )
                }
            }
        },
        onClick = onRestore,
    )
}
