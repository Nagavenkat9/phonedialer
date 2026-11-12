package app.memories.gallery.ui.memories

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.memories.gallery.memories.Memory
import app.memories.gallery.ui.AppViewModel
import app.memories.gallery.ui.components.EmptyState
import app.memories.gallery.ui.components.MemoryHeroCard
import app.memories.gallery.ui.components.MemoryStoryCard
import app.memories.gallery.ui.components.ScreenHeader

@Composable
fun MemoriesScreen(
    vm: AppViewModel,
    padding: PaddingValues,
    onOpenMemory: (String) -> Unit,
    onShowDay: (Memory) -> Unit,
    onOpenHidden: () -> Unit,
) {
    val memories by vm.memories.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
        ScreenHeader("Memories") {
            IconButton(onClick = onOpenHidden) {
                Icon(Icons.Outlined.VisibilityOff, contentDescription = "Hidden from memories")
            }
        }
        if (memories.isEmpty()) {
            Box(Modifier.fillMaxSize()) {
                if (loading) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else {
                    EmptyState(
                        Icons.Outlined.AutoAwesome,
                        "No memories yet",
                        "As your gallery grows you'll see moments from 1 year ago, 2 years ago, your favorites and more.",
                    )
                }
            }
            return@Column
        }

        val hero = memories.first()
        val rest = memories.drop(1)
        val onThisDay = rest.filter { it.kind.isOnThisDay }
        val favorites = rest.filter { it.kind.isFavorite }
        val rediscover = rest.filter { !it.kind.isOnThisDay && !it.kind.isFavorite }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 16.dp),
        ) {
            item(key = "hero") {
                MemoryHeroCard(
                    memory = hero,
                    onClick = { onOpenMemory(hero.id) },
                    onLongClick = { onShowDay(hero) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            item(key = "tip") {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.TouchApp,
                            null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Long-press any memory to see its day. Tap to play.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
            memorySection("On this day", onThisDay, onOpenMemory, onShowDay)
            memorySection("Your favorites", favorites, onOpenMemory, onShowDay)
            memorySection("Rediscover", rediscover, onOpenMemory, onShowDay)
        }
    }
}

private fun LazyListScope.memorySection(
    title: String,
    list: List<Memory>,
    onOpenMemory: (String) -> Unit,
    onShowDay: (Memory) -> Unit,
) {
    if (list.isEmpty()) return
    item(key = "title-$title") {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
        )
    }
    item(key = "row-$title") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(list, key = { it.id }) { memory ->
                MemoryStoryCard(
                    memory = memory,
                    width = 160.dp,
                    height = 236.dp,
                    onClick = { onOpenMemory(memory.id) },
                    onLongClick = { onShowDay(memory) },
                )
            }
        }
    }
}
