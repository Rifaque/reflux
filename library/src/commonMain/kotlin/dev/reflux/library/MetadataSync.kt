package dev.reflux.library

import dev.reflux.core.identify.TitleText
import dev.reflux.core.metadata.Credit
import dev.reflux.core.metadata.CreditRole
import dev.reflux.core.metadata.EpisodeMetadata
import dev.reflux.core.metadata.MatchResult
import dev.reflux.core.metadata.MetadataKind
import dev.reflux.core.metadata.MetadataMatcher
import dev.reflux.core.metadata.MetadataProvider
import dev.reflux.core.metadata.MetadataQuery
import dev.reflux.core.metadata.ProviderRef
import dev.reflux.core.metadata.RemoteArtwork
import dev.reflux.core.metadata.SeasonMetadata
import dev.reflux.core.metadata.WorkMetadata
import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.CalendarDate
import dev.reflux.core.model.MediaKind
import dev.reflux.library.db.Item
import dev.reflux.library.db.Metadata
import dev.reflux.library.db.RefluxDatabase

/** Descriptive metadata of a work as stored by Reflux. */
data class ItemMetadata(
    /** The provider entry for movies and shows; null for seasons and episodes. */
    val ref: ProviderRef?,
    val title: String?,
    val originalTitle: String?,
    val overview: String?,
    val tagline: String?,
    val genres: List<String>,
    val runtimeMinutes: Int?,
    val releaseDate: CalendarDate?,
    val rating: Double?,
    val contentRating: String?,
    val externalIds: Map<String, String>,
)

/** Outcome of a metadata refresh. */
data class MetadataReport(
    val matched: Int = 0,
    /** Works with several plausible candidates, left for the Identify flow. */
    val ambiguous: Int = 0,
    val unmatched: Int = 0,
    val episodes: Int = 0,
    /** The provider could not be reached; the refresh stopped early and will resume next time. */
    val offline: Boolean = false,
)

internal enum class AttemptOutcome { MATCHED, AMBIGUOUS, NO_MATCH }

/**
 * Enriches identified works with provider metadata and artwork.
 *
 * Network calls happen outside database transactions; each work is stored atomically once fetched.
 * Identity is never changed here: providers describe works, the deterministic identifier defines them.
 */
internal class MetadataSync(database: RefluxDatabase, private val now: () -> Long) {
    private val queries = database.libraryQueries
    private val db = database

    suspend fun refresh(provider: MetadataProvider, language: String, limit: Int, retryAfterMs: Long): MetadataReport {
        var report = MetadataReport()
        val retryBefore = now() - retryAfterMs
        try {
            for (row in queries.worksNeedingMetadata(retryBefore, limit.toLong()).executeAsList()) {
                report = when (refreshWork(row, provider, language, pinned = null)) {
                    AttemptOutcome.MATCHED -> report.copy(matched = report.matched + 1)
                    AttemptOutcome.AMBIGUOUS -> report.copy(ambiguous = report.ambiguous + 1)
                    AttemptOutcome.NO_MATCH -> report.copy(unmatched = report.unmatched + 1)
                }
            }
            for (showId in queries.showsWithEpisodesNeedingMetadata(retryBefore, limit.toLong()).executeAsList()) {
                val ref = queries.metadataOf(showId).executeAsOneOrNull()?.provider_ref?.let(ProviderRef::parse) ?: continue
                if (ref.provider != provider.id) continue
                report = report.copy(episodes = report.episodes + syncEpisodes(showId, ref, provider, language, onlyMissing = true))
            }
        } catch (_: dev.reflux.core.metadata.MetadataUnavailableException) {
            return report.copy(offline = true)
        }
        return report
    }

    /** Looks up one movie or show. [pinned] forces a provider entry (from the Identify flow). */
    suspend fun refreshWork(row: Item, provider: MetadataProvider, language: String, pinned: ProviderRef?): AttemptOutcome {
        val kind = if (row.kind == MediaKind.SHOW.name) MetadataKind.SHOW else MetadataKind.MOVIE
        val hints = decodePairs(row.external_hints)
        var score = 1.0
        val ref = pinned
            ?: queries.metadataPinOf(row.id).executeAsOneOrNull()?.let(ProviderRef::parse)?.takeIf { it.provider == provider.id }
            ?: hints[provider.id]?.let { ProviderRef(provider.id, kind, it) }
            ?: hints.entries.firstNotNullOfOrNull { (source, id) -> provider.findByExternalId(kind, source, id) }
            ?: run {
                val query = MetadataQuery(kind, row.title, row.year?.toInt(), hints, language)
                when (val result = MetadataMatcher.match(query, provider.search(query))) {
                    is MatchResult.Accepted -> result.candidate.ref.also { score = result.score }
                    is MatchResult.Ambiguous -> return record(row.id, AttemptOutcome.AMBIGUOUS)
                    MatchResult.NoMatch -> return record(row.id, AttemptOutcome.NO_MATCH)
                }
            }
        val details = provider.details(ref, language) ?: return record(row.id, AttemptOutcome.NO_MATCH)
        store(row.id, details, score, provider.id, language)
        if (kind == MetadataKind.SHOW) syncEpisodes(row.id, ref, provider, language)
        return AttemptOutcome.MATCHED
    }

    private fun record(itemId: String, outcome: AttemptOutcome): AttemptOutcome {
        queries.recordMetadataAttempt(itemId, now(), outcome.name)
        return outcome
    }

    private fun store(itemId: String, details: WorkMetadata, score: Double, providerId: String, language: String) =
        db.transaction {
            queries.upsertMetadata(
                item_id = itemId,
                provider_ref = details.ref.toString(),
                title = details.title,
                sort_key = TitleText.sortKey(details.title),
                original_title = details.originalTitle,
                overview = details.overview,
                tagline = details.tagline,
                genres = details.genres.joinToString("\n"),
                runtime_minutes = details.runtimeMinutes?.toLong(),
                release_date = details.releaseDate?.toString(),
                rating = details.rating,
                content_rating = details.contentRating,
                external_ids = encodePairs(details.externalIds),
                match_score = score,
                fetched_at = now(),
            )
            queries.deleteCredits(itemId)
            selectCredits(details.credits).forEachIndexed { index, credit ->
                queries.insertCredit(itemId, index.toLong(), credit.name, credit.role.name, credit.character, credit.profileUrl)
            }
            storeArtwork(itemId, providerId, chooseArtwork(details.artwork, language))
            queries.recordMetadataAttempt(itemId, now(), AttemptOutcome.MATCHED.name)
        }

    private fun storeArtwork(itemId: String, providerId: String, artwork: Map<ArtworkKind, String>) {
        queries.deleteArtworkOfItemByOrigin(itemId, providerId)
        artwork.forEach { (kind, url) -> queries.upsertArtwork(itemId, kind.name, providerId, null, url) }
    }

    /**
     * Fetches season and episode metadata for the seasons present in the library. With [onlyMissing], only
     * episodes without metadata (and their seasons) are fetched. Returns the number of episodes stored.
     */
    suspend fun syncEpisodes(
        showId: String,
        ref: ProviderRef,
        provider: MetadataProvider,
        language: String,
        onlyMissing: Boolean = false,
    ): Int {
        val rows = queries.presentSeasonsAndEpisodesOfShow(showId).executeAsList()
        val described = if (onlyMissing) {
            queries.metadataOfItems(rows.map { it.id }).executeAsList().map { it.item_id }.toSet()
        } else {
            emptySet()
        }
        val episodes = rows.filter { it.kind == MediaKind.EPISODE.name && it.id !in described }
        val wantedSeasons = episodes.map { it.season_number!!.toInt() }.toSet()
        val seasons = rows.filter { it.kind == MediaKind.SEASON.name && it.season_number!!.toInt() in wantedSeasons }
            .associateBy { it.season_number!!.toInt() }
        val fetched = mutableMapOf<Int, SeasonMetadata?>()
        suspend fun season(number: Int): SeasonMetadata? = fetched.getOrPut(number) { provider.season(ref, number, language) }

        var stored = 0
        for ((number, seasonRow) in seasons) {
            val metadata = season(number) ?: continue
            storeSeason(seasonRow.id, metadata, provider.id)
        }
        for (episode in episodes) {
            val match = when {
                episode.episode_number != null ->
                    season(episode.season_number!!.toInt())?.episodes?.firstOrNull { it.episodeNumber.toLong() == episode.episode_number }
                episode.absolute_number != null -> absoluteEpisode(episode.absolute_number.toInt(), ::season)
                else -> null // dated episodes need air-date matching across seasons (not yet supported)
            }
            if (match == null) {
                queries.recordMetadataAttempt(episode.id, now(), AttemptOutcome.NO_MATCH.name)
            } else {
                storeEpisode(episode.id, match, ref, provider.id)
                stored++
            }
        }
        return stored
    }

    /** Maps an absolute episode number onto provider seasons (season 1 onwards, specials excluded). */
    private suspend fun absoluteEpisode(number: Int, season: suspend (Int) -> SeasonMetadata?): EpisodeMetadata? {
        var remaining = number
        var seasonNumber = 1
        while (seasonNumber <= MAX_SEASONS_FOR_ABSOLUTE) {
            val metadata = season(seasonNumber) ?: return null
            val sorted = metadata.episodes.sortedBy { it.episodeNumber }
            if (remaining <= sorted.size) return sorted[remaining - 1]
            remaining -= sorted.size
            seasonNumber++
        }
        return null
    }

    private fun storeSeason(itemId: String, season: SeasonMetadata, providerId: String) = db.transaction {
        upsertSimple(itemId, season.title, season.overview, null, null, "$providerId:season:${season.number}")
        storeArtwork(itemId, providerId, listOfNotNull(season.posterUrl?.let { ArtworkKind.POSTER to it }).toMap())
    }

    private fun storeEpisode(itemId: String, episode: EpisodeMetadata, show: ProviderRef, providerId: String) = db.transaction {
        upsertSimple(
            itemId, episode.title, episode.overview, episode.airDate, episode.runtimeMinutes,
            "$show/${episode.seasonNumber}/${episode.episodeNumber}",
        )
        storeArtwork(itemId, providerId, listOfNotNull(episode.stillUrl?.let { ArtworkKind.THUMBNAIL to it }).toMap())
        queries.recordMetadataAttempt(itemId, now(), AttemptOutcome.MATCHED.name)
    }

    private fun upsertSimple(itemId: String, title: String?, overview: String?, date: CalendarDate?, runtime: Int?, ref: String) {
        queries.upsertMetadata(
            item_id = itemId, provider_ref = ref, title = title, sort_key = title?.let(TitleText::sortKey),
            original_title = null, overview = overview, tagline = null, genres = "", runtime_minutes = runtime?.toLong(),
            release_date = date?.toString(), rating = null, content_rating = null, external_ids = "", match_score = 1.0,
            fetched_at = now(),
        )
    }

    companion object {
        private const val MAX_SEASONS_FOR_ABSOLUTE = 50
        private const val MAX_ACTORS = 20

        /** Directors and creators first, then the top-billed cast. */
        fun selectCredits(credits: List<Credit>): List<Credit> =
            credits.filter { it.role == CreditRole.DIRECTOR || it.role == CreditRole.CREATOR }.distinctBy { it.name } +
                credits.filter { it.role == CreditRole.ACTOR }.take(MAX_ACTORS)

        /**
         * One image per kind. Posters and logos prefer the user's language, then text-free images; backdrops
         * prefer text-free images so titles are not shown twice. Ties go to the provider's community score.
         */
        fun chooseArtwork(artwork: List<RemoteArtwork>, language: String): Map<ArtworkKind, String> {
            val lang = language.substringBefore('-').lowercase()
            return artwork.groupBy { it.kind }.mapValues { (kind, images) ->
                images.sortedWith(
                    compareBy<RemoteArtwork> { image ->
                        val matches = image.language?.lowercase() == lang
                        when (kind) {
                            ArtworkKind.BACKDROP, ArtworkKind.THUMBNAIL -> if (image.language == null) 0 else if (matches) 1 else 2
                            else -> if (matches) 0 else if (image.language == null) 1 else 2
                        }
                    }.thenByDescending { it.score },
                ).first().url
            }
        }
    }
}

internal fun Metadata.toModel(): ItemMetadata =
    ItemMetadata(
        ref = ProviderRef.parse(provider_ref),
        title = title,
        originalTitle = original_title,
        overview = overview,
        tagline = tagline,
        genres = genres.lines().filter { it.isNotBlank() },
        runtimeMinutes = runtime_minutes?.toInt(),
        releaseDate = release_date?.let(CalendarDate::parse),
        rating = rating,
        contentRating = content_rating,
        externalIds = decodePairs(external_ids),
    )
