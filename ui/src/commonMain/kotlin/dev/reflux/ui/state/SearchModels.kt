package dev.reflux.ui.state

import androidx.compose.runtime.Immutable
import dev.reflux.core.model.Episode
import dev.reflux.core.model.Movie
import dev.reflux.core.model.Show
import dev.reflux.library.Library
import dev.reflux.library.LibraryEntry
import dev.reflux.library.profileSetting
import dev.reflux.library.setProfileSetting

/**
 * What Search looks in (B-057): everything, or movies or shows alone. A filter, kept apart from suggestions: it narrows
 * whatever is asked, typed or chosen, and the counts beside every shortcut.
 */
enum class SearchScope(val label: String) {
    ALL("All"),
    MOVIES("Movies"),
    SHOWS("Shows"),
    ;

    /** Whether [entry] is in this scope: an episode belongs to Shows. */
    fun admits(entry: LibraryEntry): Boolean = when (this) {
        ALL -> true
        MOVIES -> entry.item is Movie
        SHOWS -> entry.item is Show || entry.item is Episode
    }
}

/** A shortcut to a smart search, with how many titles it finds here, now (B-057). */
@Immutable
data class QuickFind(
    val title: String,
    /** The smart search it runs, in words the parser understands. */
    val query: String,
    /** What stands for it: a short word ("4K") or, where null, an icon chosen by [kind]. */
    val emblem: String?,
    val kind: QuickFindKind,
    val count: Int,
)

enum class QuickFindKind { CONTINUE, UNWATCHED, FAVORITES, QUALITY, LENGTH, DECADE }

/** A genre of the library, and how many titles it has (B-057). */
@Immutable
data class GenreFind(val name: String, val count: Int)

/** What the idle Search page offers: only what this library has (B-057). */
@Immutable
data class SearchDiscovery(val quickFinds: List<QuickFind>, val genres: List<GenreFind>, val recent: List<String>) {
    val isEmpty: Boolean get() = quickFinds.isEmpty() && genres.isEmpty() && recent.isEmpty()
}

/** One section of results: a kind, its heading where there are several kinds, and its cards. */
@Immutable
data class SearchSection(val heading: String?, val cards: List<MediaCard>)

/**
 * The idle page's shortcuts, worked out from the library itself (B-057): each candidate is a smart search the parser
 * understands, offered only when it finds something here, with its count; the library's own genres by size. Counted
 * within [scope]. One pass over the library per candidate, run off the interface thread, once per change of library.
 */
internal fun Library.searchDiscovery(scope: SearchScope): SearchDiscovery {
    fun count(query: String): Int = smartSearch(query, limit = 10_000).results.count(scope::admits)
    val candidates = buildList {
        add(QuickFind("Continue watching", "unfinished", null, QuickFindKind.CONTINUE, 0))
        add(QuickFind("Not seen yet", "unwatched", "New", QuickFindKind.UNWATCHED, 0))
        add(QuickFind("Favorites", "favorites", null, QuickFindKind.FAVORITES, 0))
        add(QuickFind("In 4K", "4k", "4K", QuickFindKind.QUALITY, 0))
        add(QuickFind("In HDR", "hdr", "HDR", QuickFindKind.QUALITY, 0))
        add(QuickFind("Under 2 hours", "under 2 hours", "2h", QuickFindKind.LENGTH, 0))
    }
    val entries = buildList {
        if (scope != SearchScope.SHOWS) addAll(movies())
        if (scope != SearchScope.MOVIES) addAll(shows())
    }
    // The library's two biggest decades, by release year.
    val decades = entries.mapNotNull { year(it) }.groupingBy { it / 10 * 10 }.eachCount()
        .entries.sortedByDescending { it.value }.take(2).sortedBy { it.key }
        .map { (decade, _) -> QuickFind("The ${decade}s", "${decade}s", "’${(decade % 100).toString().padStart(2, '0')}", QuickFindKind.DECADE, 0) }
    val quickFinds = (candidates + decades).map { it.copy(count = count(it.query)) }.filter { it.count > 0 }
    val genres = entries.flatMap { entry -> entry.metadata?.genres.orEmpty().distinct() }
        .groupingBy { it }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(MAX_GENRES).map { GenreFind(it.key, it.value) }
    return SearchDiscovery(quickFinds, genres, recentSearches())
}

private fun year(entry: LibraryEntry): Int? = when (val item = entry.item) {
    is Movie -> item.year
    is Show -> item.year
    else -> null
}

/** This profile's recent searches on this device, newest first (B-057). Never sent anywhere. */
internal fun Library.recentSearches(): List<String> =
    profileSetting(RECENT_SEARCHES)?.lines()?.filter { it.isNotBlank() }.orEmpty()

/** Puts [query] first among the recent searches, once, keeping [MAX_RECENT]. */
internal fun Library.rememberSearch(query: String) {
    val clean = query.trim().takeIf { it.length >= 2 } ?: return
    val next = (listOf(clean) + recentSearches().filterNot { it.equals(clean, ignoreCase = true) }).take(MAX_RECENT)
    setProfileSetting(RECENT_SEARCHES, next.joinToString("\n"))
}

internal fun Library.forgetSearches() = setProfileSetting(RECENT_SEARCHES, null)

/** Results in sections by kind, headed only when there is more than one (B-057). */
internal fun searchSections(cards: List<MediaCard>, scope: SearchScope): List<SearchSection> {
    val admitted = cards.filter { scope.admits(it.entry) }
    val movies = admitted.filter { it.entry.item is Movie }
    val shows = admitted.filter { it.entry.item is Show }
    val episodes = admitted.filter { it.entry.item is Episode }
    val groups = listOf("Movies" to movies, "Shows" to shows, "Episodes" to episodes).filter { it.second.isNotEmpty() }
    return if (groups.size <= 1) listOf(SearchSection(null, admitted)) else groups.map { (heading, list) -> SearchSection(heading, list) }
}

private const val RECENT_SEARCHES = "recent_searches"
internal const val MAX_RECENT = 6
private const val MAX_GENRES = 12
