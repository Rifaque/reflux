package dev.reflux.library

import dev.reflux.core.identify.IdentityKeys
import dev.reflux.core.identify.IdentityOverride
import dev.reflux.core.identify.ParsedKind
import dev.reflux.core.metadata.Credit
import dev.reflux.core.metadata.CreditRole
import dev.reflux.core.metadata.MetadataCandidate
import dev.reflux.core.metadata.MetadataKind
import dev.reflux.core.metadata.MetadataMatcher
import dev.reflux.core.metadata.MetadataProvider
import dev.reflux.core.metadata.MetadataQuery
import dev.reflux.core.metadata.ScoredCandidate
import dev.reflux.core.model.Episode
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaKind
import dev.reflux.core.model.MediaLocation
import dev.reflux.core.model.Movie
import dev.reflux.core.model.Show
import dev.reflux.core.model.StableIds

/**
 * Looks up metadata and artwork for works that have none yet, newest first. Unmatched works are retried
 * after [retryAfterMs]. Safe to call while offline: it stops early and reports [MetadataReport.offline].
 */
suspend fun Library.refreshMetadata(
    provider: MetadataProvider,
    language: String,
    limit: Int = 100,
    retryAfterMs: Long = 7 * 24 * 3_600_000L,
): MetadataReport = metadataSync.refresh(provider, language, limit, retryAfterMs)

fun Library.metadata(itemId: MediaId): ItemMetadata? = queries.metadataOf(itemId.value).executeAsOneOrNull()?.toModel()

fun Library.credits(itemId: MediaId): List<Credit> = queries.creditsOf(itemId.value).executeAsList().map {
    Credit(it.name, enumOrNull<CreditRole>(it.role) ?: CreditRole.ACTOR, it.character, it.profile_url)
}

/** Candidates for the Identify flow: the work's own title and year, or what the user typed. */
suspend fun Library.searchMetadata(
    itemId: MediaId,
    provider: MetadataProvider,
    language: String,
    title: String? = null,
    year: Int? = null,
): List<ScoredCandidate> {
    val row = queries.itemById(itemId.value).executeAsOneOrNull() ?: return emptyList()
    val kind = if (row.kind == MediaKind.SHOW.name) MetadataKind.SHOW else MetadataKind.MOVIE
    val query = MetadataQuery(kind, title ?: row.title, if (title != null) year else year ?: row.year?.toInt(), language = language)
    return MetadataMatcher.rank(query, provider.search(query))
}

/**
 * "Identify this movie/show → choose": re-identifies every file of the work as [candidate], remembers the
 * choice, and fetches its metadata. Files are never touched. Returns the work's (possibly new) ID.
 */
suspend fun Library.identifyAs(itemId: MediaId, candidate: MetadataCandidate, provider: MetadataProvider, language: String): MediaId {
    val item = rawItem(itemId) ?: return itemId
    val newId: MediaId
    val overrides: List<Pair<MediaLocation, IdentityOverride>>
    when (item) {
        is Movie -> {
            newId = StableIds.mediaId(IdentityKeys.movie(candidate.title, candidate.year))
            overrides = versions(itemId).map { it.version.location to IdentityOverride(ParsedKind.MOVIE, candidate.title, candidate.year) }
        }
        is Show -> {
            newId = StableIds.mediaId(IdentityKeys.show(candidate.title, candidate.year))
            overrides = queries.episodesOfShow(itemId.value).executeAsList().map { it.toModel() as Episode }.flatMap { episode ->
                val number = episode.episodeNumber ?: episode.absoluteNumber ?: return@flatMap emptyList()
                versions(episode.id).map {
                    it.version.location to IdentityOverride(ParsedKind.EPISODE, candidate.title, candidate.year, episode.seasonNumber, number)
                }
            }
        }
        else -> return itemId
    }
    database.transaction {
        for ((location, override) in overrides) {
            queries.upsertOverride(
                location.sourceId.value, location.path, override.kind.name, override.title, override.year?.toLong(),
                override.season?.toLong(), override.episode?.toLong(), now(),
            )
        }
        queries.pinMetadata(newId.value, candidate.ref.toString())
        queries.clearMetadataAttempt(newId.value)
    }
    overrides.map { it.first.sourceId }.distinct().forEach { reidentify(it) }
    queries.itemById(newId.value).executeAsOneOrNull()?.let { metadataSync.refreshWork(it, provider, language, candidate.ref) }
    return newId
}
