package dev.reflux.library

import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.StableIds

/** A group of works shown together. */
sealed interface MediaCollection {
    val id: String
    val name: String

    /** A franchise from the metadata provider, shown automatically once two of its movies are present. */
    data class Franchise(
        override val id: String,
        override val name: String,
        val artwork: Map<ArtworkKind, ArtworkLocator>,
        val presentCount: Int,
    ) : MediaCollection

    /** A list the user curates. */
    data class Manual(override val id: String, override val name: String) : MediaCollection

    /** A saved smart search, always current ("4k movies I haven't watched"). */
    data class Smart(override val id: String, override val name: String, val query: String) : MediaCollection
}

/** Franchises with at least two movies in the library, alphabetically. */
fun Library.franchises(): List<MediaCollection.Franchise> = queries.franchises().executeAsList().map { row ->
    MediaCollection.Franchise(
        id = row.id,
        name = row.name,
        artwork = buildMap {
            row.poster_url?.let { put(ArtworkKind.POSTER, ArtworkLocator.Remote(it)) }
            row.backdrop_url?.let { put(ArtworkKind.BACKDROP, ArtworkLocator.Remote(it)) }
        },
        presentCount = row.present.toInt(),
    )
}

/** The franchise a movie belongs to, if any. */
fun Library.franchiseOf(movieId: MediaId): MediaCollection.Franchise? {
    val row = queries.franchiseOf(movieId.value).executeAsOneOrNull() ?: return null
    return franchises().firstOrNull { it.id == row.id }
}

/** The user's own collections, oldest first. */
fun Library.userCollections(): List<MediaCollection> = queries.userCollections().executeAsList().map { row ->
    if (row.smart_query != null) MediaCollection.Smart(row.id, row.name, row.smart_query) else MediaCollection.Manual(row.id, row.name)
}

/** Creates a manual collection, or a smart one when [smartQuery] is given. Names need not be unique. */
fun Library.createCollection(name: String, smartQuery: String? = null): MediaCollection {
    val created = now()
    val id = "c" + StableIds.hash("collection:$name:$created:${queries.userCollections().executeAsList().size}")
    queries.insertUserCollection(id, name, smartQuery, created)
    return if (smartQuery != null) MediaCollection.Smart(id, name, smartQuery) else MediaCollection.Manual(id, name)
}

fun Library.renameCollection(id: String, name: String) = queries.renameUserCollection(name, id)

fun Library.deleteCollection(id: String) = database.transaction {
    queries.deleteUserCollectionItems(id)
    queries.deleteUserCollection(id)
}

fun Library.addToCollection(id: String, itemId: MediaId) = queries.addToUserCollection(id, itemId.value, now())

fun Library.removeFromCollection(id: String, itemId: MediaId) = queries.removeFromUserCollection(id, itemId.value)

/** The works in a collection, in the collection's natural order. Missing works are skipped. */
fun Library.collectionEntries(collection: MediaCollection): List<LibraryEntry> = when (collection) {
    is MediaCollection.Franchise -> entries(queries.franchiseMovies(collection.id).executeAsList().map { it.toModel() })
    is MediaCollection.Manual -> {
        val ids = queries.userCollectionItemIds(collection.id).executeAsList()
        val items = itemsByIds(ids).associateBy { it.id.value }
        entries(ids.mapNotNull { items[it] })
    }
    is MediaCollection.Smart -> smartSearch(collection.query).results
}
