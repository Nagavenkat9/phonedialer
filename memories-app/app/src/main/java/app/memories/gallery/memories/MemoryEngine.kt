package app.memories.gallery.memories

import app.memories.gallery.data.GalleryItem
import app.memories.gallery.util.Fmt
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.IsoFields
import kotlin.random.Random

/**
 * Turns the raw gallery into "memories".
 *
 * Rules:
 *  - "N years ago" for the exact same calendar day (falls back to the same week).
 *  - Favorites are surfaced often: a dedicated "Your favorites" memory, a daily
 *    rotating "Remember this day?" built around one favorite, "Favorites from <year>",
 *    favorites are always kept when a memory is trimmed, they are preferred as covers,
 *    and memories that contain favorites rank higher.
 *  - Items the user hid from memories are never used (they stay in the gallery).
 *  - Selection rotates daily (seeded by the date) but is stable within a day.
 */
object MemoryEngine {
    private const val MAX_ITEMS = 40

    fun generate(
        all: List<GalleryItem>,
        favorites: Set<String>,
        hiddenItems: Set<String>,
        today: LocalDate = LocalDate.now(),
    ): List<Memory> {
        val pool = all.filter { it.key !in hiddenItems && !it.date.isAfter(today) }
        if (pool.isEmpty()) return emptyList()

        val rnd = Random(today.toEpochDay())
        val isFav: (GalleryItem) -> Boolean = { it.key in favorites }
        val byDay: Map<LocalDate, List<GalleryItem>> = pool.groupBy { it.date }

        val onThisDay = mutableListOf<Memory>()
        val ranked = mutableListOf<Pair<Memory, Double>>()

        fun rank(memory: Memory, base: Double) {
            ranked += memory to (base + memory.favoriteCount * 25.0 + rnd.nextDouble() * 40.0)
        }

        // 1) On this day — 1 year ago, 2 years ago, …
        val oldestYear = pool.minOf { it.date.year }
        for (yearsAgo in 1..(today.year - oldestYear)) {
            val target = today.minusYears(yearsAgo.toLong())
            val exact = byDay[target].orEmpty()
            if (exact.isNotEmpty()) {
                onThisDay += build(
                    id = "otd-$target",
                    kind = MemoryKind.ON_THIS_DAY,
                    title = Fmt.yearsAgo(yearsAgo),
                    subtitle = target.format(Fmt.full),
                    items = exact,
                    isFav = isFav,
                    rnd = rnd,
                    day = target,
                )
            } else {
                val week = (-3L..3L).flatMap { byDay[target.plusDays(it)].orEmpty() }
                if (week.size >= 3) {
                    val wk = target.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                    onThisDay += build(
                        id = "otw-${target.year}-$wk",
                        kind = MemoryKind.THIS_WEEK,
                        title = "This week, ${Fmt.yearsAgo(yearsAgo)}",
                        subtitle = "${target.minusDays(3).format(Fmt.dayMonth)} – ${target.plusDays(3).format(Fmt.short)}",
                        items = week,
                        isFav = isFav,
                        rnd = rnd,
                        day = null,
                    )
                }
            }
        }

        // 2) Favorites — shown often.
        val favItems = pool.filter(isFav)
        if (favItems.isNotEmpty()) {
            ranked += build(
                id = "fav-all",
                kind = MemoryKind.FAVORITES,
                title = "Your favorites",
                subtitle = if (favItems.size == 1) "1 loved moment" else "${favItems.size} loved moments",
                items = favItems,
                isFav = isFav,
                rnd = rnd,
                day = null,
                chronological = false,
            ) to 10_000.0

            // A different favorite gets the spotlight every day.
            val spot = favItems[rnd.nextInt(favItems.size)]
            if (spot.date != today) {
                ranked += build(
                    id = "fotd-${spot.date}",
                    kind = MemoryKind.FAVORITE_SPOTLIGHT,
                    title = "Remember this day?",
                    subtitle = "${spot.date.format(Fmt.short)} · ${Fmt.relative(spot.date, today)}",
                    items = byDay[spot.date].orEmpty(),
                    isFav = isFav,
                    rnd = rnd,
                    day = spot.date,
                    coverOverride = spot,
                ) to 9_000.0
            }

            favItems.groupBy { it.date.year }
                .filter { (year, list) -> list.size >= 3 && year < today.year }
                .forEach { (year, list) ->
                    rank(
                        build(
                            id = "fav-$year",
                            kind = MemoryKind.FAVORITES_YEAR,
                            title = "Favorites from $year",
                            subtitle = Fmt.yearsAgo(today.year - year),
                            items = list,
                            isFav = isFav,
                            rnd = rnd,
                            day = null,
                        ),
                        base = 600.0,
                    )
                }
        }

        // 3) Big days worth remembering (lots of photos = something happened).
        val bigDays = byDay.entries.filter { it.value.size >= 8 && it.key.isBefore(today.minusDays(30)) }
        weightedSample(bigDays, 4, rnd) { e -> e.value.size + 10.0 * e.value.count(isFav) }
            .forEach { (day, list) ->
                rank(
                    build(
                        id = "day-$day",
                        kind = MemoryKind.DAY_TO_REMEMBER,
                        title = day.format(Fmt.headerOtherYear),
                        subtitle = Fmt.relative(day, today),
                        items = list,
                        isFav = isFav,
                        rnd = rnd,
                        day = day,
                    ),
                    base = 300.0,
                )
            }

        // 4) Recent highlights (last 30 days).
        val recent = pool.filter { !it.date.isBefore(today.minusDays(30)) }
        if (recent.size >= 6) {
            rank(
                build(
                    id = "recent-${YearMonth.from(today)}",
                    kind = MemoryKind.RECENT,
                    title = "Recent highlights",
                    subtitle = "Last 30 days",
                    items = recent,
                    isFav = isFav,
                    rnd = rnd,
                    day = null,
                ),
                base = 350.0,
            )
        }

        // 5) Best of a month.
        val thisMonth = YearMonth.from(today)
        val months = pool.groupBy { YearMonth.from(it.date) }.entries
            .filter { it.value.size >= 12 && it.key.isBefore(thisMonth) }
        weightedSample(months, 3, rnd) { e -> e.value.size + 10.0 * e.value.count(isFav) }
            .forEach { (month, list) ->
                rank(
                    build(
                        id = "month-$month",
                        kind = MemoryKind.MONTH_HIGHLIGHTS,
                        title = "Best of ${month.format(Fmt.monthYear)}",
                        subtitle = Fmt.relative(month.atDay(1), today),
                        items = list,
                        isFav = isFav,
                        rnd = rnd,
                        day = null,
                    ),
                    base = 200.0,
                )
            }

        // 6) Videos from a year.
        val videoYears = pool.filter { it.isVideo }.groupBy { it.date.year }.entries.filter { it.value.size >= 3 }
        weightedSample(videoYears, 2, rnd) { e -> e.value.size.toDouble() }
            .forEach { (year, list) ->
                rank(
                    build(
                        id = "videos-$year",
                        kind = MemoryKind.VIDEOS_YEAR,
                        title = "Videos from $year",
                        subtitle = if (list.size == 1) "1 video" else "${list.size} videos",
                        items = list,
                        isFav = isFav,
                        rnd = rnd,
                        day = null,
                    ),
                    base = 250.0,
                )
            }

        // 7) Throwback to an older year.
        val oldYears = pool.groupBy { it.date.year }.entries.filter { it.key < today.year - 1 && it.value.size >= 10 }
        if (oldYears.isNotEmpty()) {
            val (year, list) = oldYears[rnd.nextInt(oldYears.size)]
            rank(
                build(
                    id = "throwback-$year",
                    kind = MemoryKind.THROWBACK,
                    title = "Throwback to $year",
                    subtitle = Fmt.yearsAgo(today.year - year),
                    items = list,
                    isFav = isFav,
                    rnd = rnd,
                    day = null,
                ),
                base = 150.0,
            )
        }

        return onThisDay + ranked.sortedByDescending { it.second }.map { it.first }
    }

    private fun build(
        id: String,
        kind: MemoryKind,
        title: String,
        subtitle: String,
        items: List<GalleryItem>,
        isFav: (GalleryItem) -> Boolean,
        rnd: Random,
        day: LocalDate?,
        chronological: Boolean = true,
        coverOverride: GalleryItem? = null,
    ): Memory {
        val favs = items.filter(isFav)
        val chosen: List<GalleryItem> = if (items.size <= MAX_ITEMS) {
            items
        } else {
            // Always keep favorites, then spread the remaining slots evenly over time.
            val favPart = if (favs.size > MAX_ITEMS) favs.shuffled(rnd).take(MAX_ITEMS) else favs
            val slots = MAX_ITEMS - favPart.size
            val rest = items.filterNot(isFav).sortedBy { it.dateTaken }
            val sampled = if (slots <= 0 || rest.isEmpty()) {
                emptyList()
            } else {
                val step = rest.size.toDouble() / slots
                (0 until slots).map { rest[(it * step).toInt().coerceAtMost(rest.lastIndex)] }.distinct()
            }
            favPart + sampled
        }
        val ordered = if (chronological) chosen.sortedBy { it.dateTaken } else chosen.shuffled(rnd)
        val orderedFavs = ordered.filter(isFav)
        val cover = coverOverride?.takeIf { c -> ordered.any { it.key == c.key } }
            ?: orderedFavs.filterNot { it.isVideo }.randomOrNull(rnd)
            ?: orderedFavs.randomOrNull(rnd)
            ?: ordered.filterNot { it.isVideo }.let { photos -> if (photos.isEmpty()) null else photos[photos.size / 2] }
            ?: ordered.first()
        return Memory(
            id = id,
            kind = kind,
            title = title,
            subtitle = subtitle,
            items = ordered,
            cover = cover,
            day = day,
            favoriteCount = orderedFavs.size,
        )
    }

    private fun <T> weightedSample(items: List<T>, n: Int, rnd: Random, weight: (T) -> Double): List<T> {
        val pool = items.toMutableList()
        val out = mutableListOf<T>()
        repeat(minOf(n, pool.size)) {
            val total = pool.sumOf { weight(it).coerceAtLeast(0.01) }
            var r = rnd.nextDouble() * total
            var chosen = pool.last()
            for (candidate in pool) {
                r -= weight(candidate).coerceAtLeast(0.01)
                if (r <= 0) {
                    chosen = candidate
                    break
                }
            }
            pool.remove(chosen)
            out += chosen
        }
        return out
    }
}
