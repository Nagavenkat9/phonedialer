package app.memories.gallery.memories

import app.memories.gallery.data.GalleryItem
import java.time.LocalDate

enum class MemoryKind {
    ON_THIS_DAY,
    THIS_WEEK,
    FAVORITES,
    FAVORITE_SPOTLIGHT,
    FAVORITES_YEAR,
    DAY_TO_REMEMBER,
    RECENT,
    MONTH_HIGHLIGHTS,
    VIDEOS_YEAR,
    THROWBACK;

    val isOnThisDay get() = this == ON_THIS_DAY || this == THIS_WEEK
    val isFavorite get() = this == FAVORITES || this == FAVORITE_SPOTLIGHT || this == FAVORITES_YEAR
}

data class Memory(
    /** Stable id — used to remember memories the user dismissed. */
    val id: String,
    val kind: MemoryKind,
    val title: String,
    val subtitle: String,
    /** Items in playback order. */
    val items: List<GalleryItem>,
    val cover: GalleryItem,
    /** The single day this memory is about, if it is about one day. */
    val day: LocalDate?,
    val favoriteCount: Int,
) {
    val photoCount: Int get() = items.count { !it.isVideo }
    val videoCount: Int get() = items.count { it.isVideo }

    /** Distinct days covered, oldest first. */
    val days: List<LocalDate> get() = items.map { it.date }.distinct().sorted()
}
