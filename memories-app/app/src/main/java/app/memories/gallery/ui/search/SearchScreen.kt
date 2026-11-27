package app.memories.gallery.ui.search

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.memories.gallery.data.GalleryItem
import app.memories.gallery.ui.AppViewModel
import app.memories.gallery.ui.components.EmptyState
import app.memories.gallery.ui.components.MediaThumb
import java.time.LocalDate

@Composable
fun SearchScreen(
    vm: AppViewModel,
    padding: PaddingValues,
    onOpenViewer: (List<GalleryItem>, Int) -> Unit,
) {
    val media by vm.media.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    val results = remember(media, favorites, query) {
        SearchQuery.filter(media, favorites, query, LocalDate.now())
    }

    Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            placeholder = { Text("Search your photos") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        )

        if (query.isBlank()) {
            Suggestions(
                media = media,
                bottomPadding = padding.calculateBottomPadding(),
                onPick = { query = it },
            )
        } else if (results.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = "No results",
                body = "Try a year, a month, an album name or \"videos\".",
                modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 16.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = if (results.size == 1) "1 result" else "${results.size} results",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                itemsIndexed(results, key = { _, item -> item.key }) { index, item ->
                    MediaThumb(
                        item = item,
                        isFavorite = item.key in favorites,
                        onClick = {
                            keyboard?.hide()
                            onOpenViewer(results, index)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Suggestions(
    media: List<GalleryItem>,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onPick: (String) -> Unit,
) {
    val years = remember(media) { media.map { it.date.year }.distinct().sortedDescending() }
    val albums = remember(media) {
        media.filter { it.bucketName.isNotBlank() }
            .groupingBy { it.bucketName }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(6)
            .map { it.key }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = bottomPadding + 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ChipSection("Suggestions", listOf("Videos", "Favorites", "Screenshots", "This month", "Last year"), onPick)
        if (years.isNotEmpty()) ChipSection("Years", years.map { it.toString() }, onPick)
        if (albums.isNotEmpty()) ChipSection("Albums", albums, onPick)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipSection(title: String, chips: List<String>, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { label ->
                SuggestionChip(onClick = { onPick(label) }, label = { Text(label) })
            }
        }
    }
}
