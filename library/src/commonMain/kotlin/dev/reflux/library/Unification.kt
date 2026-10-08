package dev.reflux.library

import dev.reflux.core.metadata.ProviderRef
import dev.reflux.core.model.MediaKind
import dev.reflux.core.model.SourceId

/**
 * Merges movies and shows that are the same work under different names — typically because two sources name it
 * differently ("Star Wars" vs "Star Wars: Episode IV – A New Hope") — when they share a provider ID (from
 * metadata or from IDs embedded in names or reported by a server).
 *
 * The oldest work survives. The other's identity key becomes an alias of it, so its files (and, for shows, its
 * episodes) are re-identified into the surviving work; watch history follows its versions, and favorites and
 * collection membership move along. Files are never touched. Returns the number of works merged.
 */
fun Library.unifyWorks(): Int {
    val rows = queries.unificationCandidates().executeAsList()
    // Union-find over works that share any provider identifier of the same kind.
    val parent = rows.indices.toMutableList()
    fun find(i: Int): Int {
        var x = i
        while (parent[x] != x) {
            parent[x] = parent[parent[x]]
            x = parent[x]
        }
        return x
    }
    val owners = mutableMapOf<String, Int>()
    rows.forEachIndexed { index, row ->
        val kind = if (row.kind == MediaKind.SHOW.name) "show" else "movie"
        val ids = buildSet {
            row.provider_ref?.let(ProviderRef::parse)?.let { add("${it.provider}:$kind:${it.id}") }
            (decodePairs(row.external_hints) + decodePairs(row.external_ids.orEmpty())).forEach { (source, id) ->
                add(if (source == "imdb") "imdb:$id" else "$source:$kind:$id")
            }
        }
        for (id in ids) {
            val owner = owners.getOrPut("$kind|$id") { index }
            parent[find(index)] = find(owner)
        }
    }
    val groups = rows.indices.groupBy(::find).values.filter { it.size > 1 }
    if (groups.isEmpty()) return 0

    val affectedSources = mutableSetOf<SourceId>()
    var merged = 0
    database.transaction {
        for (group in groups) {
            val members = group.map { rows[it] }.sortedWith(compareBy({ it.added_at }, { it.id }))
            val survivor = members.first()
            for (other in members.drop(1)) {
                val otherKey = other.identity_key ?: continue
                queries.insertAlias(otherKey, survivor.identity_key!!, now())
                queries.moveFavorite(survivor.id, other.id)
                queries.deleteFavorite(other.id)
                queries.moveCollectionMembership(survivor.id, other.id)
                queries.deleteCollectionMembershipOf(other.id)
                queries.sourcesOfWork(other.id).executeAsList().mapTo(affectedSources, ::SourceId)
                merged++
            }
        }
    }
    affectedSources.forEach(::reidentify)
    return merged
}
