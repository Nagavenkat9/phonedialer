package app.memories.gallery.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.memories.gallery.data.GalleryItem
import app.memories.gallery.faces.FaceIndex
import app.memories.gallery.ui.AppViewModel
import app.memories.gallery.ui.components.ScreenHeader
import coil.compose.AsyncImage

private class Album(val id: String, val name: String, val cover: GalleryItem, val count: Int)

@Composable
fun LibraryScreen(
    vm: AppViewModel,
    padding: PaddingValues,
    onOpenCollection: (String) -> Unit,
    onOpenHidden: () -> Unit,
) {
    val media by vm.media.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val hidden by vm.hiddenItems.collectAsStateWithLifecycle()
    val hiddenMemories by vm.hiddenMemories.collectAsStateWithLifecycle()

    val albums = remember(media) {
        media.groupBy { it.bucketId }
            .map { (id, list) -> Album(id, list.first().bucketName.ifBlank { "Other" }, list.first(), list.size) }
            .sortedByDescending { it.count }
    }
    val favCount = remember(media, favorites) { media.count { it.key in favorites } }
    val videoCount = remember(media) { media.count { it.isVideo } }

    val context = LocalContext.current
    val faceCounts by FaceIndex.counts.collectAsStateWithLifecycle()
    val faceProgress by FaceIndex.progress.collectAsStateWithLifecycle()
    LaunchedEffect(media) {
        if (media.isNotEmpty()) FaceIndex.startScan(context, media)
    }
    val peopleCount = remember(media, faceCounts) { media.count { (faceCounts[it.key] ?: 0) >= 1 } }
    val groupCount = remember(media, faceCounts) { media.count { (faceCounts[it.key] ?: 0) >= 3 } }
    val scanSuffix = if (faceProgress.running && faceProgress.total > 0) {
        " · Scanning ${faceProgress.done}/${faceProgress.total}…"
    } else {
        ""
    }

    Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
        ScreenHeader("Library")
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LibraryTile(Icons.Outlined.FavoriteBorder, "Favorites", "$favCount", Modifier.weight(1f)) {
                            onOpenCollection("favorites")
                        }
                        LibraryTile(Icons.Outlined.VideoLibrary, "Videos", "$videoCount", Modifier.weight(1f)) {
                            onOpenCollection("videos")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LibraryTile(
                            Icons.Outlined.Face,
                            "People & selfies",
                            "$peopleCount$scanSuffix",
                            Modifier.weight(1f),
                        ) {
                            onOpenCollection("people")
                        }
                        LibraryTile(
                            Icons.Outlined.Groups,
                            "Group photos",
                            "$groupCount$scanSuffix",
                            Modifier.weight(1f),
                        ) {
                            onOpenCollection("groups")
                        }
                    }
                    LibraryTile(
                        Icons.Outlined.VisibilityOff,
                        "Hidden from memories",
                        "${hidden.size} items · ${hiddenMemories.size} memories — still in your gallery",
                        Modifier.fillMaxWidth(),
                        onClick = onOpenHidden,
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Albums",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            items(albums, key = { it.id }) { album ->
                Column(Modifier.clickable { onOpenCollection("album:${album.id}") }) {
                    AsyncImage(
                        model = album.cover.uri,
                        contentDescription = album.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(album.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${album.count} items",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Row(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.size(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}
