package app.memories.gallery.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.memories.gallery.data.GalleryItem
import app.memories.gallery.faces.FaceIndex
import app.memories.gallery.ui.AppViewModel
import app.memories.gallery.ui.components.EmptyState
import app.memories.gallery.ui.components.MediaThumb
import app.memories.gallery.ui.components.ScreenHeader
import app.memories.gallery.util.Fmt
import java.time.LocalDate

private data class Resolved(val title: String, val subtitle: String?, val items: List<GalleryItem>)

/**
 * Generic grid for: "favorites", "videos", "people", "groups", "album:<bucketId>", "day:<yyyy-MM-dd>", "memory:<id>".
 */
@Composable
fun CollectionScreen(
    vm: AppViewModel,
    collectionKey: String,
    onBack: () -> Unit,
    onOpenViewer: (List<GalleryItem>, Int) -> Unit,
) {
    val media by vm.media.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val memories by vm.allMemories.collectAsStateWithLifecycle()
    val faceCounts by FaceIndex.counts.collectAsStateWithLifecycle()
    val faceProgress by FaceIndex.progress.collectAsStateWithLifecycle()
    val scanning = faceProgress.running && faceProgress.total > 0
    val scanNote = if (scanning) "Scanning ${faceProgress.done}/${faceProgress.total}…" else null

    val faceKeyed = collectionKey == "people" || collectionKey == "groups"
    val resolved = remember(collectionKey, media, favorites, memories, if (faceKeyed) faceCounts else null) {
        val today = LocalDate.now()
        when {
            collectionKey == "favorites" -> Resolved("Favorites", null, media.filter { it.key in favorites })
            collectionKey == "videos" -> Resolved("Videos", null, media.filter { it.isVideo })
            collectionKey == "people" ->
                Resolved("People & selfies", null, media.filter { (faceCounts[it.key] ?: 0) >= 1 })
            collectionKey == "groups" ->
                Resolved("Group photos", null, media.filter { (faceCounts[it.key] ?: 0) >= 3 })
            collectionKey.startsWith("album:") -> {
                val id = collectionKey.removePrefix("album:")
                val list = media.filter { it.bucketId == id }
                Resolved(list.firstOrNull()?.bucketName?.ifBlank { null } ?: "Album", null, list)
            }
            collectionKey.startsWith("day:") -> {
                val day = runCatching { LocalDate.parse(collectionKey.removePrefix("day:")) }.getOrNull()
                if (day == null) {
                    Resolved("Day", null, emptyList())
                } else {
                    Resolved(day.format(Fmt.full), Fmt.relative(day, today), media.filter { it.date == day })
                }
            }
            collectionKey.startsWith("memory:") -> {
                val m = memories.firstOrNull { it.id == collectionKey.removePrefix("memory:") }
                Resolved(m?.title ?: "Memory", m?.subtitle, m?.items.orEmpty())
            }
            else -> Resolved("Photos", null, emptyList())
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        val sub = listOfNotNull(resolved.subtitle, "${resolved.items.size} items", if (faceKeyed) scanNote else null).joinToString(" · ")
        ScreenHeader(resolved.title, subtitle = sub, onBack = onBack)
        if (resolved.items.isEmpty()) {
            Box(Modifier.fillMaxSize()) {
                EmptyState(Icons.Outlined.PhotoLibrary, "Nothing here yet", "")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(resolved.items, key = { _, it -> it.key }) { index, item ->
                    MediaThumb(item = item, isFavorite = item.key in favorites) {
                        onOpenViewer(resolved.items, index)
                    }
                }
            }
        }
    }
}
