package app.memories.gallery.ui.search

import app.memories.gallery.data.GalleryItem
import java.time.LocalDate
import java.util.Locale

/**
 * Local, case-insensitive query parser for the Search tab.
 *
 * The query is split into terms; every term becomes a filter and all filters are AND-ed.
 * Supported terms: years ("2021"), month names ("august", "aug"), relative dates
 * ("today", "yesterday", "last week", "this month", "last month", "this year", "last year"),
 * media kinds ("video(s)", "photo(s)"), "favorite(s)", "screenshot(s)", and free text
 * which matches the album/bucket name or the file name.
 */
object SearchQuery {

    private val months: Map<String, Int> = buildMap {
        val names = listOf(
            "january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december",
        )
        names.forEachIndexed { i, name ->
            put(name, i + 1)
            put(name.take(3), i + 1)
        }
        put("sept", 9)
    }

    private val stopWords = setOf("in", "from", "of", "and", "my", "the", "on", "at", "with", "all")

    private val phraseRegexes: List<Pair<String, Regex>> = listOf(
        "last week" to Regex("\\blast\\s+week\\b"),
        "this month" to Regex("\\bthis\\s+month\\b"),
        "last month" to Regex("\\blast\\s+month\\b"),
        "this year" to Regex("\\bthis\\s+year\\b"),
        "last year" to Regex("\\blast\\s+year\\b"),
    )

    private fun pred(f: (GalleryItem) -> Boolean): (GalleryItem) -> Boolean = f

    private val splitter = Regex("[\\s,]+")

    fun isScreenshot(item: GalleryItem): Boolean =
        item.bucketName.contains("screenshot", ignoreCase = true) ||
            item.displayName.contains("screenshot", ignoreCase = true)

    /** Returns the items matching [query], newest first. A blank query matches nothing. */
    fun filter(
        items: List<GalleryItem>,
        favorites: Set<String>,
        query: String,
        today: LocalDate = LocalDate.now(),
    ): List<GalleryItem> {
        val filters = parse(query, favorites, today)
        if (filters.isEmpty()) return emptyList()
        return items.filter { item -> filters.all { it(item) } }.sortedByDescending { it.dateTaken }
    }

    private fun phraseFilter(phrase: String, today: LocalDate): (GalleryItem) -> Boolean = when (phrase) {
        "last week" -> {
            val from = today.minusDays(7)
            pred { item -> !item.date.isBefore(from) && !item.date.isAfter(today) }
        }
        "this month" -> pred { item -> item.date.year == today.year && item.date.monthValue == today.monthValue }
        "last month" -> {
            val m = today.minusMonths(1)
            pred { item -> item.date.year == m.year && item.date.monthValue == m.monthValue }
        }
        "this year" -> pred { item -> item.date.year == today.year }
        else -> pred { item -> item.date.year == today.year - 1 } // "last year"
    }

    private fun wordFilter(word: String, favorites: Set<String>, today: LocalDate): (GalleryItem) -> Boolean {
        val year = word.toIntOrNull()
        val month = months[word]
        return when {
            year != null && word.length == 4 && year in 1900..2100 -> pred { item -> item.date.year == year }
            month != null -> pred { item -> item.date.monthValue == month }
            word == "today" -> pred { item -> item.date == today }
            word == "yesterday" -> {
                val y = today.minusDays(1)
                pred { item -> item.date == y }
            }
            word == "video" || word == "videos" -> pred { item -> item.isVideo }
            word == "photo" || word == "photos" || word == "picture" || word == "pictures" ->
                pred { item -> !item.isVideo }
            word == "favorite" || word == "favorites" || word == "favourite" || word == "favourites" ->
                pred { item -> item.key in favorites }
            word == "screenshot" || word == "screenshots" -> pred { item -> isScreenshot(item) }
            else -> pred { item ->
                item.bucketName.contains(word, ignoreCase = true) ||
                    item.displayName.contains(word, ignoreCase = true)
            }
        }
    }

    fun parse(query: String, favorites: Set<String>, today: LocalDate): List<(GalleryItem) -> Boolean> {
        var text = query.trim().lowercase(Locale.getDefault())
        if (text.isEmpty()) return emptyList()
        val filters = mutableListOf<(GalleryItem) -> Boolean>()

        for ((phrase, regex) in phraseRegexes) {
            if (regex.containsMatchIn(text)) {
                filters.add(phraseFilter(phrase, today))
                text = regex.replace(text, " ")
            }
        }

        for (word in text.split(splitter)) {
            if (word.isBlank() || word in stopWords) continue
            filters.add(wordFilter(word, favorites, today))
        }
        return filters
    }
}
