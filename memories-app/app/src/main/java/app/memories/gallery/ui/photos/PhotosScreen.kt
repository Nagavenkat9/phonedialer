package app.memories.gallery.ui.photos

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.memories.gallery.data.GalleryItem
import app.memories.gallery.memories.Memory
import app.memories.gallery.ui.AppViewModel
import app.memories.gallery.ui.components.EmptyState
import app.memories.gallery.ui.components.MediaThumb
import app.memories.gallery.ui.components.MemoryStoryCard
import app.memories.gallery.util.Fmt
import java.time.LocalDate

private class DaySection(val date: LocalDate, val items: List<GalleryItem>, val offset: Int)

@Composable
fun PhotosScreen(
    vm: AppViewModel,
    padding: PaddingValues,
    onOpenViewer: (List<GalleryItem>, Int) -> Unit,
    onOpenMemory: (String) -> Unit,
    onShowDay: (Memory) -> Unit,
) {
    val media by vm.media.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val memories by vm.memories.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    var columns by rememberSaveable { mutableIntStateOf(4) }

    val sections = remember(media) {
        var offset = 0
        media.groupBy { it.date }.map { (date, items) ->
            DaySection(date, items, offset).also { offset += items.size }
        }
    }
    val today = remember { LocalDate.now() }

    Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
        when {
            media.isEmpty() && loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            media.isEmpty() -> EmptyState(
                Icons.Outlined.PhotoLibrary,
                "No photos or videos yet",
                "Take some photos — they'll show up here and turn into memories.",
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize().pinchToChangeColumns(columns) { columns = it },
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Memories", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${media.size} items",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (memories.isNotEmpty()) {
                    item(key = "memories", span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(memories.take(15), key = { it.id }) { memory ->
                                MemoryStoryCard(
                                    memory = memory,
                                    width = 116.dp,
                                    height = 172.dp,
                                    onClick = { onOpenMemory(memory.id) },
                                    onLongClick = { onShowDay(memory) },
                                )
                            }
                        }
                    }
                }
                sections.forEach { section ->
                    item(key = "d-${section.date}", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            Fmt.header(section.date, today),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                        )
                    }
                    itemsIndexed(section.items, key = { _, it -> it.key }) { i, item ->
                        val index = section.offset + i
                        MediaThumb(item = item, isFavorite = item.key in favorites) {
                            onOpenViewer(media, index)
                        }
                    }
                }
            }
        }
    }
}

/** Google-Photos-style pinch on the grid to change how many columns are shown. */
@Composable
private fun Modifier.pinchToChangeColumns(columns: Int, onChange: (Int) -> Unit): Modifier {
    val current by rememberUpdatedState(columns)
    val change by rememberUpdatedState(onChange)
    return this.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var zoom = 1f
            var changed = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    zoom *= event.calculateZoom()
                    if (!changed && zoom > 1.25f && current > 2) {
                        change(current - 1); changed = true
                    } else if (!changed && zoom < 0.8f && current < 7) {
                        change(current + 1); changed = true
                    }
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }
}
