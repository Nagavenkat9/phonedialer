package app.memories.gallery.ui.photos

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current

    // Multi-select state: set of GalleryItem.key. Non-empty = selection mode.
    var selected by remember { mutableStateOf(emptySet<String>()) }
    val selecting = selected.isNotEmpty()
    LaunchedEffect(media) {
        if (selected.isNotEmpty()) {
            val keys = media.mapTo(HashSet()) { it.key }
            selected = selected.filterTo(HashSet()) { it in keys }
        }
    }
    BackHandler(enabled = selecting) { selected = emptySet() }
    fun toggle(key: String) {
        selected = if (key in selected) selected - key else selected + key
    }
    fun toggleDay(section: DaySection) {
        val keys = section.items.map { it.key }
        selected = if (selected.containsAll(keys)) selected - keys.toSet() else selected + keys
    }

    Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
    AnimatedVisibility(
        visible = selecting,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
    ) {
        SelectionTopBar(count = selected.size, onClose = { selected = emptySet() })
    }
    Box(Modifier.fillMaxWidth().weight(1f)) {
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
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + if (selecting) 88.dp else 8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (!selecting) item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
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
                        val daySelected = selecting && section.items.all { it.key in selected }
                        Row(
                            Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (selecting) {
                                Box(
                                    Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .clickable { toggleDay(section) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        if (daySelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                                        contentDescription = if (daySelected) "Deselect day" else "Select day",
                                        tint = if (daySelected) SelectBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                            } else {
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                Fmt.header(section.date, today),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = if (selecting) Modifier.clickable { toggleDay(section) } else Modifier,
                            )
                        }
                    }
                    itemsIndexed(section.items, key = { _, it -> it.key }) { i, item ->
                        val index = section.offset + i
                        MediaThumb(
                            item = item,
                            isFavorite = item.key in favorites,
                            selected = if (selecting) item.key in selected else null,
                            onLongClick = { toggle(item.key) },
                        ) {
                            if (selecting) toggle(item.key) else onOpenViewer(media, index)
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = selecting,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = padding.calculateBottomPadding() + 12.dp),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            val allFavorite = selected.isNotEmpty() && selected.all { it in favorites }
            SelectionActionBar(
                allFavorite = allFavorite,
                onShare = {
                    context.shareMultiple(media.filter { it.key in selected })
                },
                onFavorite = {
                    if (allFavorite) {
                        selected.forEach { vm.toggleFavorite(it) }
                    } else {
                        selected.filter { it !in favorites }.forEach { vm.toggleFavorite(it) }
                    }
                    selected = emptySet()
                },
                onRemoveFromMemories = {
                    selected.forEach { vm.hideFromMemories(it) }
                    selected = emptySet()
                },
            )
        }
    }
    }
}

private val SelectBlue = Color(0xFF1A73E8)

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
