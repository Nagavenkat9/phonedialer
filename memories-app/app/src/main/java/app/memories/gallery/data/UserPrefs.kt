package app.memories.gallery.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Small persistent store for the user's choices:
 *  - favorites
 *  - items hidden from memories (they stay in the gallery)
 *  - whole memories the user dismissed
 *  - video resume positions
 */
class UserPrefs(context: Context) {
    private val sp = context.getSharedPreferences("memories_prefs", Context.MODE_PRIVATE)

    private val _favorites = MutableStateFlow(read(KEY_FAVORITES))
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _hiddenItems = MutableStateFlow(read(KEY_HIDDEN_ITEMS))
    val hiddenItems: StateFlow<Set<String>> = _hiddenItems.asStateFlow()

    private val _hiddenMemories = MutableStateFlow(read(KEY_HIDDEN_MEMORIES))
    val hiddenMemories: StateFlow<Set<String>> = _hiddenMemories.asStateFlow()

    fun toggleFavorite(key: String) =
        update(_favorites, KEY_FAVORITES) { if (key in it) it - key else it + key }

    fun setHiddenFromMemories(key: String, hidden: Boolean) =
        update(_hiddenItems, KEY_HIDDEN_ITEMS) { if (hidden) it + key else it - key }

    fun setMemoryHidden(id: String, hidden: Boolean) =
        update(_hiddenMemories, KEY_HIDDEN_MEMORIES) { if (hidden) it + id else it - id }

    fun clearHiddenItems() = update(_hiddenItems, KEY_HIDDEN_ITEMS) { emptySet() }

    fun resumePosition(key: String): Long = sp.getLong("pos_$key", 0L)

    fun saveResumePosition(key: String, positionMs: Long) {
        sp.edit().putLong("pos_$key", positionMs).apply()
    }

    private fun read(key: String): Set<String> = sp.getStringSet(key, emptySet())?.toSet() ?: emptySet()

    private fun update(
        flow: MutableStateFlow<Set<String>>,
        key: String,
        transform: (Set<String>) -> Set<String>,
    ) {
        val next = transform(flow.value)
        flow.value = next
        sp.edit().putStringSet(key, HashSet(next)).apply()
    }

    private companion object {
        const val KEY_FAVORITES = "favorites"
        const val KEY_HIDDEN_ITEMS = "hidden_items"
        const val KEY_HIDDEN_MEMORIES = "hidden_memories"
    }
}
