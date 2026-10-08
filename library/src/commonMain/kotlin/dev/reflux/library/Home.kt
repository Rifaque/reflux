package dev.reflux.library

import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.MediaKind
import dev.reflux.core.model.Movie
import dev.reflux.core.model.Show

/** One row of the Home screen. [seeAll] is a smart search that shows the complete row, when meaningful. */
data class HomeRow(val id: String, val title: String, val entries: List<LibraryEntry>, val seeAll: String? = null)

data class HomeFeed(
    /** The work featured at the top of Home, with a backdrop. Null for an empty or artless library. */
    val featured: LibraryEntry?,
    val rows: List<HomeRow>,
)

/**
 * The default Home screen, composed by fixed rules so it is useful on first launch with no configuration:
 * what to continue, what is new, the user's favorites, standout picture quality, franchises, and the
 * library's strongest genres. Empty rows are omitted; rows never repeat their whole content.
 */
fun Library.home(rowLimit: Int = 20): HomeFeed {
    val rows = mutableListOf<HomeRow>()
    fun add(id: String, title: String, entries: List<LibraryEntry>, seeAll: String? = null, minimum: Int = 1) {
        if (entries.size >= minimum) rows += HomeRow(id, title, entries.take(rowLimit), seeAll)
    }
    val continueWatching = continueWatching(rowLimit)
    add("continue", "Continue Watching", continueWatching)
    val recent = recentlyAdded(rowLimit)
    add("recent", "Recently Added", recent)
    add("favorites", "Favorites", favorites())

    val movies = movies()
    val shows = shows()
    val unwatchedMovies = movies.filter { it.watchState?.completed != true }
    add("unwatched", "Ready to Watch", unwatchedMovies.sortedByDescending { it.metadata?.rating ?: 0.0 }, "movies I haven't watched", minimum = 4)
    add("quality", "Stunning in 4K HDR", smartSearch("4k hdr").results, "4k hdr", minimum = 4)
    franchises().forEach { franchise ->
        add("franchise:${franchise.id}", franchise.name, collectionEntries(franchise), minimum = 2)
    }
    if (shows.isNotEmpty()) add("shows", "TV Shows", shows, "shows")
    for (genre in topGenres(movies, count = 4)) {
        add("genre:$genre", genre, smartSearch("$genre movies").results, "$genre movies", minimum = 4)
    }
    if (movies.isNotEmpty()) add("movies", "Movies", movies, "movies")

    val featured = (continueWatching + recent + movies + shows)
        .firstOrNull { it.artwork.containsKey(ArtworkKind.BACKDROP) && (it.item is Movie || it.item is Show || it.item.kind == MediaKind.EPISODE) }
    return HomeFeed(featured, rows)
}

/** Genres with the most movies, ties broken alphabetically. */
private fun topGenres(movies: List<LibraryEntry>, count: Int): List<String> =
    movies.flatMap { it.metadata?.genres.orEmpty() }
        .groupingBy { it }.eachCount()
        .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(count).map { it.key }
