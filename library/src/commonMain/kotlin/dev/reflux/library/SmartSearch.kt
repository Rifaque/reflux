package dev.reflux.library

import dev.reflux.core.model.Episode
import dev.reflux.core.model.MediaItem
import dev.reflux.core.model.MediaKind
import dev.reflux.core.model.Movie
import dev.reflux.core.model.Show
import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.VideoStream
import dev.reflux.core.search.SearchDocument
import dev.reflux.core.search.SearchMatcher
import dev.reflux.core.search.SearchVocabulary
import dev.reflux.core.search.SmartDocument
import dev.reflux.core.search.SmartQuery
import dev.reflux.core.search.SmartQueryParser
import dev.reflux.core.search.matches
import dev.reflux.library.db.RefluxDatabase

/** Results of a smart search together with how the query was understood (for UI chips). */
data class SmartSearchResult(val query: SmartQuery, val results: List<LibraryEntry>)

/** Evaluates [SmartQuery]s against the library's movies and shows (and episodes when asked for). */
internal class SmartSearch(database: RefluxDatabase) {
    private val queries = database.libraryQueries

    fun vocabulary(): SearchVocabulary = SearchVocabulary(
        genres = queries.metadataFacts().executeAsList().flatMap { it.genres.lines() }.filter { it.isNotBlank() }.toSet(),
        people = queries.creditNames().executeAsList().map { it.name }.toSet(),
    )

    /** Filters [candidates] (already display-ready entries) by [query]; ranks by title text when present. */
    fun evaluate(query: SmartQuery, candidates: List<LibraryEntry>, limit: Int): List<LibraryEntry> {
        val facts = facts()
        val filtered = candidates.filter { entry -> query.matches(facts.document(entry)) }
        if (query.text.isBlank()) return filtered.take(limit)
        val documents = filtered.map { entry ->
            val item = entry.item
            val showTitle = (item as? Episode)?.let { e -> candidates.firstOrNull { it.item.id == e.showId }?.item?.title }
            SearchDocument(item.id, item.kind, item.title, yearOf(item), listOfNotNull(entry.metadata?.originalTitle, showTitle))
        }
        val byId = filtered.associateBy { it.item.id }
        return SearchMatcher.search(query.text, documents, limit).mapNotNull { byId[it.document.id] }
    }

    private class Facts(
        val genres: Map<String, Set<String>>,
        val people: Map<String, Set<String>>,
        val runtime: Map<String, Int>,
        val heights: Map<String, Int>,
        val ranges: Map<String, Set<DynamicRange>>,
        val episodesWatched: Map<String, Pair<Int, Int>>,
    ) {
        fun document(entry: LibraryEntry): SmartDocument {
            val id = entry.item.id.value
            val state = entry.watchState
            val (watchedEpisodes, totalEpisodes) = episodesWatched[id] ?: (0 to 0)
            val isShow = entry.item.kind == MediaKind.SHOW
            return SmartDocument(
                kind = entry.item.kind,
                genres = genres[id].orEmpty(),
                people = people[id].orEmpty(),
                year = yearOf(entry.item),
                runtimeMinutes = entry.metadata?.runtimeMinutes ?: runtime[id],
                maxHeight = heights[id],
                dynamicRanges = ranges[id].orEmpty(),
                watched = if (isShow) totalEpisodes > 0 && watchedEpisodes == totalEpisodes else state?.completed == true,
                inProgress = if (isShow) watchedEpisodes in 1 until totalEpisodes else state?.inProgress == true,
                favorite = entry.favorite,
            )
        }
    }

    private fun facts(): Facts {
        val versions = queries.versionFacts().executeAsList()
        val metadata = queries.metadataFacts().executeAsList()
        // Show-level facts aggregate their episodes' versions.
        fun owners(itemId: String, showId: String?) = listOfNotNull(itemId, showId)
        val heights = mutableMapOf<String, Int>()
        val ranges = mutableMapOf<String, MutableSet<DynamicRange>>()
        val runtime = mutableMapOf<String, Int>()
        for (row in versions) {
            val height = VideoStream(width = row.width?.toInt(), height = row.height?.toInt()).nominalHeight
            val range = enumOrNull<DynamicRange>(row.dynamic_range)
            for (owner in owners(row.item_id, row.show_id)) {
                if (height != null) heights[owner] = maxOf(heights[owner] ?: 0, height)
                if (range != null) ranges.getOrPut(owner) { mutableSetOf() } += range
            }
            row.duration_ms?.let { runtime.putIfAbsent(row.item_id, (it / 60_000).toInt()) }
        }
        val states = queries.allWatchStates().executeAsList().associate { it.item_id to (it.completed != 0L) }
        val episodes = queries.presentPlayables().executeAsList().filter { it.kind == MediaKind.EPISODE.name }
        val episodesWatched = episodes.groupBy { it.show_id!! }.mapValues { (_, list) ->
            list.count { states[it.id] == true } to list.size
        }
        return Facts(
            genres = metadata.associate { it.item_id to it.genres.lines().filter(String::isNotBlank).toSet() },
            people = queries.creditNames().executeAsList().groupBy({ it.item_id }, { it.name }).mapValues { it.value.toSet() },
            runtime = runtime,
            heights = heights,
            ranges = ranges,
            episodesWatched = episodesWatched,
        )
    }
}

private fun yearOf(item: MediaItem): Int? = when (item) {
    is Movie -> item.year
    is Show -> item.year
    is Episode -> item.airDate?.year
    else -> null
}
