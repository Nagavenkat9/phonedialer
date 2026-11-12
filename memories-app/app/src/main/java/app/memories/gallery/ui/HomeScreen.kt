package app.memories.gallery.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import app.memories.gallery.data.GalleryItem
import app.memories.gallery.memories.Memory
import app.memories.gallery.ui.components.DaySheet
import app.memories.gallery.ui.library.LibraryScreen
import app.memories.gallery.ui.memories.MemoriesScreen
import app.memories.gallery.ui.photos.PhotosScreen

private data class Tab(val label: String, val selected: ImageVector, val unselected: ImageVector)

private val tabs = listOf(
    Tab("Photos", Icons.Filled.Photo, Icons.Outlined.Photo),
    Tab("Memories", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    Tab("Library", Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary),
)

@Composable
fun HomeScreen(
    vm: AppViewModel,
    onOpenViewer: (List<GalleryItem>, Int) -> Unit,
    onOpenMemory: (String) -> Unit,
    onOpenCollection: (String) -> Unit,
    onOpenHidden: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var dayMemory by remember { mutableStateOf<Memory?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(if (tab == i) t.selected else t.unselected, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        when (tab) {
            0 -> PhotosScreen(
                vm = vm,
                padding = padding,
                onOpenViewer = onOpenViewer,
                onOpenMemory = onOpenMemory,
                onShowDay = { dayMemory = it },
            )
            1 -> MemoriesScreen(
                vm = vm,
                padding = padding,
                onOpenMemory = onOpenMemory,
                onShowDay = { dayMemory = it },
                onOpenHidden = onOpenHidden,
            )
            else -> LibraryScreen(
                vm = vm,
                padding = padding,
                onOpenCollection = onOpenCollection,
                onOpenHidden = onOpenHidden,
            )
        }
    }

    dayMemory?.let { memory ->
        DaySheet(
            memory = memory,
            onDismiss = { dayMemory = null },
            onPlay = {
                dayMemory = null
                onOpenMemory(memory.id)
            },
            onOpenDay = { day ->
                dayMemory = null
                onOpenCollection("day:$day")
            },
            onHide = {
                vm.hideMemory(memory.id)
                dayMemory = null
            },
        )
    }
}
