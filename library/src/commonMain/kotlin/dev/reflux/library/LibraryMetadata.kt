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
import dev.reflux.core.metadata.MetadataUnavailableException
import dev.reflux.core.metadata.ProviderRef
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

/** What [itemId] is described as, resolved from its provider entry and its sources' own descriptions. */
fun Library.metadata(itemId: MediaId): ItemMetadata? = resolvedMetadata(listOf(itemId.value))[itemId.value]

/**
 * Re-reads matched works whose provider metadata is older than [staleAfterMs] (90 days by default: well within
 * the six months TMDB's terms allow a copy to be kept), at most [limit] of them. Only the entry already matched is
 * read; a failure changes nothing. Returns how many were refreshed, or null when the provider was out of reach.
 */
suspend fun Library.refreshStaleMetadata(
    provider: MetadataProvider,
    language: String,
    staleAfterMs: Long = 90 * 24 * 3_600_000L,
    limit: Int = 10,
    retryAfterMs: Long = 24 * 3_600_000L,
): Int? = try {
    metadataSync.refreshStale(provider, language, staleAfterMs, limit, retryAfterMs)
} catch (_: MetadataUnavailableException) {
    null
}

/** The preference that allows Reflux to look things up online (metadata and artwork from a provider). */
const val ONLINE_METADATA: String = "metadata.online"

/**
 * Whether Reflux may look up metadata and artwork online (docs/METADATA.md, "Online lookups"). On unless the
 * viewer turned it off. Off, nothing is sent to a metadata provider: what sources say, what was fetched before,
 * and the file names are what the library shows, and nothing already known is forgotten.
 */
val Library.onlineMetadata: Boolean get() = setting(ONLINE_METADATA) != "off"

fun Library.setOnlineMetadata(on: Boolean) = setSetting(ONLINE_METADATA, if (on) null else "off")

/** What asking for a work's details again did. */
enum class MetadataRefresh {
    /** Its details and artwork were read again and kept. */
    REFRESHED,

    /** The provider could not be reached; everything known is kept as it was. */
    UNREACHABLE,

    /** The provider has no entry for it; what its sources say still stands. */
    NOT_FOUND,
}

/**
 * Reads a movie's or show's details and artwork again, or one season's ([itemId] a season or an episode of it):
 * the provider entry it is matched to (or the one the user chose), never a new search, so a refresh cannot turn
 * a known identity into a guess. Watch state, sources, and files are untouched, and nothing is removed until
 * what replaces it has arrived. The sources' own descriptions are refreshed by scanning them.
 */
suspend fun Library.refreshDetails(itemId: MediaId, provider: MetadataProvider, language: String): MetadataRefresh {
    val row = queries.itemById(itemId.value).executeAsOneOrNull() ?: return MetadataRefresh.NOT_FOUND
    return try {
        when (row.kind) {
            MediaKind.MOVIE.name, MediaKind.SHOW.name -> {
                queries.clearMetadataAttempt(row.id)
                when (metadataSync.refreshWork(row, provider, language, pinned = null)) {
                    AttemptOutcome.MATCHED -> MetadataRefresh.REFRESHED
                    else -> MetadataRefresh.NOT_FOUND
                }
            }
            else -> {
                val showId = row.show_id ?: return MetadataRefresh.NOT_FOUND
                val ref = queries.metadataOf(showId).executeAsOneOrNull()?.provider_ref?.let(ProviderRef::parse)
                    ?.takeIf { it.provider == provider.id } ?: return MetadataRefresh.NOT_FOUND
                metadataSync.syncEpisodes(showId, ref, provider, language, onlySeason = row.season_number?.toInt())
                MetadataRefresh.REFRESHED
            }
        }
    } catch (_: MetadataUnavailableException) {
        MetadataRefresh.UNREACHABLE
    }
}

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
    carryUserState(itemId, newId)
    queries.itemById(newId.value).executeAsOneOrNull()?.let { metadataSync.refreshWork(it, provider, language, candidate.ref) }
    return newId
}

/** How a movie or show came to be what it is, for the Identify flow. */
data class IdentificationStatus(
    /** Whether Reflux is unsure of it: some file of it was identified with low confidence. */
    val weak: Boolean,
    /** Whether the user said what it is (Identify), which [undoIdentification] gives back to Reflux. */
    val corrected: Boolean,
    /** The provider entry it is matched to, if any. */
    val match: ProviderRef?,
)

fun Library.identificationStatus(itemId: MediaId): IdentificationStatus? {
    if (rawItem(itemId) == null) return null
    val files = filesOf(itemId)
    val overridden = files.map { it.location.sourceId }.distinct().flatMap { source ->
        queries.overridesOfSource(source.value).executeAsList().map { MediaLocation(source, it.path) }
    }.toSet()
    return IdentificationStatus(
        weak = files.any { it.confidence < WEAK_CONFIDENCE },
        corrected = files.any { it.location in overridden },
        match = metadata(itemId)?.ref,
    )
}

/**
 * Gives a work back to automatic identification: forgets what the user said every file of it is, and the
 * provider entry they chose, and re-identifies those files from their names. Returns the work they make now
 * (a movie, or the show of the episodes), or null when none of them is in the library any more. Watch history
 * moves with the files, as for any re-identification.
 */
fun Library.undoIdentification(itemId: MediaId): MediaId? {
    val files = filesOf(itemId).map { it.location }
    if (files.isEmpty()) return null
    database.transaction {
        files.forEach { queries.deleteOverride(it.sourceId.value, it.path) }
        queries.unpinMetadata(itemId.value)
        queries.clearMetadataAttempt(itemId.value)
    }
    files.map { it.sourceId }.distinct().forEach { reidentify(it) }
    val playable = version(StableIds.versionId(files.first()))?.version?.itemId ?: return null
    return ((rawItem(playable) as? Episode)?.showId ?: playable).also { carryUserState(itemId, it) }
}

/**
 * What the user said about a work that is not about its files follows it to its new identity: that it is a
 * favorite, in their collections, and which version Play always uses (for a show, each episode's, which
 * follows the version it names to the episode that holds it now). Watch history moves with the files.
 */
private fun Library.carryUserState(from: MediaId, to: MediaId) {
    if (from == to) return
    database.transaction {
        queries.moveFavorite(to.value, from.value)
        queries.deleteFavoriteEverywhere(from.value)
        queries.moveCollectionMembership(to.value, from.value)
        queries.deleteCollectionMembershipOf(from.value)
        for (pin in queries.preferencesOfItemEverywhere(from.value).executeAsList()) {
            queries.setPreferredVersion(pin.profile_id, to.value, pin.version_id)
            queries.clearPreferredVersion(pin.profile_id, from.value)
        }
        queries.movePinsWithTheirVersions()
        // A favorite kept the old identity in the library with nothing in it; without one it goes.
        queries.deleteOrphanPlayables()
        queries.deleteEmptySeasons()
        queries.deleteEmptyShows()
    }
}

/** Every file of a movie, or of a show's episodes, with how confidently it was identified. */
private fun Library.filesOf(itemId: MediaId): List<IdentifiedFile> {
    val playables = when (rawItem(itemId)) {
        is Show -> queries.episodesOfShow(itemId.value).executeAsList().map { MediaId(it.id) }
        is Movie -> listOf(itemId)
        else -> emptyList()
    }
    return playables.flatMap { versions(it) }.map { IdentifiedFile(it.version.location, it.confidence) }
}

private data class IdentifiedFile(val location: MediaLocation, val confidence: Double)

/** Below this a file's identification is weak, as in [Library.lowConfidence]. */
internal const val WEAK_CONFIDENCE = 0.55
