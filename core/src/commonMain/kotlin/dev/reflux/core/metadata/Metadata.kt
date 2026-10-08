package dev.reflux.core.metadata

import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.CalendarDate

/** Which provider catalog a work is looked up in. */
enum class MetadataKind { MOVIE, SHOW }

/** A provider's reference to a work, e.g. `tmdb` / `movie` / `157336`. */
data class ProviderRef(val provider: String, val kind: MetadataKind, val id: String) {
    override fun toString(): String = "$provider:${kind.name.lowercase()}:$id"

    companion object {
        fun parse(value: String): ProviderRef? {
            val parts = value.split(':')
            if (parts.size != 3) return null
            val kind = MetadataKind.entries.firstOrNull { it.name.equals(parts[1], ignoreCase = true) } ?: return null
            return ProviderRef(parts[0], kind, parts[2])
        }
    }
}

data class MetadataQuery(
    val kind: MetadataKind,
    val title: String,
    val year: Int?,
    /** IDs found in file names (`tmdb`, `imdb`, `tvdb`). An exact ID beats any title search. */
    val externalIds: Map<String, String> = emptyMap(),
    /** BCP 47 language for titles and overviews, e.g. `en-US`. */
    val language: String,
)

data class MetadataCandidate(
    val ref: ProviderRef,
    val title: String,
    val originalTitle: String? = null,
    val year: Int? = null,
    val overview: String? = null,
    val popularity: Double = 0.0,
    val posterUrl: String? = null,
)

data class RemoteArtwork(val kind: ArtworkKind, val url: String, val language: String? = null, val score: Double = 0.0)

data class Credit(val name: String, val role: CreditRole, val character: String? = null, val profileUrl: String? = null)

enum class CreditRole { ACTOR, DIRECTOR, WRITER, CREATOR }

/** A provider-defined franchise, e.g. "The Dark Knight Collection". */
data class ProviderCollection(val id: String, val name: String, val posterUrl: String? = null, val backdropUrl: String? = null)

/** Descriptive metadata for a movie or show. */
data class WorkMetadata(
    val ref: ProviderRef,
    val title: String,
    val originalTitle: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    val genres: List<String> = emptyList(),
    val runtimeMinutes: Int? = null,
    val releaseDate: CalendarDate? = null,
    /** Community rating on a 0–10 scale. */
    val rating: Double? = null,
    val contentRating: String? = null,
    val credits: List<Credit> = emptyList(),
    val artwork: List<RemoteArtwork> = emptyList(),
    val externalIds: Map<String, String> = emptyMap(),
    /** The franchise a movie belongs to, if the provider groups it. */
    val collection: ProviderCollection? = null,
)

data class EpisodeMetadata(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String?,
    val overview: String? = null,
    val airDate: CalendarDate? = null,
    val runtimeMinutes: Int? = null,
    val stillUrl: String? = null,
)

data class SeasonMetadata(
    val number: Int,
    val title: String? = null,
    val overview: String? = null,
    val posterUrl: String? = null,
    val episodes: List<EpisodeMetadata> = emptyList(),
)

/**
 * A metadata provider adapter (TMDB first). Providers enrich the library; they never decide identity on
 * their own — the deterministic [MetadataMatcher] does — and the library stays fully usable without one.
 */
interface MetadataProvider {
    val id: String

    suspend fun search(query: MetadataQuery): List<MetadataCandidate>

    /** Resolves an external ID (`imdb` → `tt0133093`) to this provider's reference, if known. */
    suspend fun findByExternalId(kind: MetadataKind, source: String, id: String): ProviderRef?

    suspend fun details(ref: ProviderRef, language: String): WorkMetadata?

    suspend fun season(ref: ProviderRef, seasonNumber: Int, language: String): SeasonMetadata?
}

/** Thrown when a provider cannot be reached or refuses requests; the caller retries later. */
class MetadataUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)
