package app.memories.gallery.ui

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.memories.gallery.data.GalleryItem
import app.memories.gallery.data.MediaRepository
import app.memories.gallery.data.UserPrefs
import app.memories.gallery.memories.Memory
import app.memories.gallery.memories.MemoryEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MediaRepository(app)
    val prefs = UserPrefs(app)

    private val _media = MutableStateFlow<List<GalleryItem>>(emptyList())
    val media: StateFlow<List<GalleryItem>> = _media.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    val favorites: StateFlow<Set<String>> = prefs.favorites
    val hiddenItems: StateFlow<Set<String>> = prefs.hiddenItems
    val hiddenMemories: StateFlow<Set<String>> = prefs.hiddenMemories

    /** Every generated memory, including ones the user dismissed. */
    val allMemories: StateFlow<List<Memory>> =
        combine(_media, favorites, hiddenItems) { media, favs, hidden ->
            MemoryEngine.generate(media, favs, hidden, LocalDate.now())
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Memories that should be shown. */
    val memories: StateFlow<List<Memory>> =
        combine(allMemories, hiddenMemories) { list, hidden -> list.filter { it.id !in hidden } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Hand-off for the full-screen viewer.
    var viewerItems by mutableStateOf<List<GalleryItem>>(emptyList())
        private set
    var viewerIndex by mutableIntStateOf(0)
        private set

    fun openViewer(items: List<GalleryItem>, index: Int) {
        viewerItems = items
        viewerIndex = index
    }

    private var loadJob: Job? = null
    private var observing = false
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = reload(debounceMs = 800)
    }

    fun refresh() {
        startObserving()
        reload(debounceMs = 0)
    }

    private fun reload(debounceMs: Long) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            if (_media.value.isEmpty()) _loading.value = true
            _media.value = runCatching { repo.loadAll() }.getOrElse { _media.value }
            _loading.value = false
        }
    }

    private fun startObserving() {
        if (observing) return
        observing = true
        val resolver = getApplication<Application>().contentResolver
        resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
    }

    override fun onCleared() {
        if (observing) getApplication<Application>().contentResolver.unregisterContentObserver(observer)
        super.onCleared()
    }

    fun toggleFavorite(key: String) = prefs.toggleFavorite(key)
    fun hideFromMemories(key: String) = prefs.setHiddenFromMemories(key, true)
    fun showInMemories(key: String) = prefs.setHiddenFromMemories(key, false)
    fun showAllInMemories() = prefs.clearHiddenItems()
    fun hideMemory(id: String) = prefs.setMemoryHidden(id, true)
    fun showMemory(id: String) = prefs.setMemoryHidden(id, false)
}
