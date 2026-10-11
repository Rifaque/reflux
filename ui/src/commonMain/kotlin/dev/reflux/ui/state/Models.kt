package dev.reflux.ui.state

import androidx.compose.runtime.Immutable
import dev.reflux.core.metadata.Credit
import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.Availability
import dev.reflux.core.model.Episode
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.Movie
import dev.reflux.core.model.Season
import dev.reflux.core.model.Show
import dev.reflux.core.model.SourceId
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.search.SmartQuery
import dev.reflux.core.versions.RankedVersion
import dev.reflux.core.versions.SelectionCriterion
import dev.reflux.core.watch.WatchRules
import dev.reflux.library.Library
import dev.reflux.library.LibraryEntry
import dev.reflux.library.SourceRecord
import dev.reflux.library.credits
import dev.reflux.library.home

/**
 * A work ready to be drawn as a card. Episodes borrow their show's title and artwork, so a card always
 * says what the user would call it ("Breaking Bad", not "Episode 2").
 */
@Immutable
data class MediaCard(
    val entry: LibraryEntry,
    val title: String,
    val caption: String?,
    val poster: ArtworkLocator?,
    /** Landscape artwork: an episode still or a backdrop. */
    val wide: ArtworkLocator?,
    /** Where selecting the card leads: the work itself, or the show of an episode. */
    val detailsId: MediaId,
) {
    val id: MediaId get() = entry.item.id
    val watched: Boolean get() = entry.watchState?.completed == true
    val progress: Float? get() = Formatting.progress(entry.watchState)
    val offline: Boolean get() = entry.availability == Availability.UNAVAILABLE
}

internal fun Library.cards(entries: List<LibraryEntry>): List<MediaCard> {
    val shows = mutableMapOf<MediaId, LibraryEntry?>()
    return entries.map { entry ->
        when (val item = entry.item) {
            is Episode -> {
                val show = shows.getOrPut(item.showId) { entry(item.showId) }
                MediaCard(
                    entry = entry,
                    title = show?.item?.title ?: item.title,
                    caption = listOfNotNull(Formatting.episodeNumber(item), item.episodeTitle).joinToString(" · "),
                    poster = show?.artwork?.get(ArtworkKind.POSTER),
                    wide = entry.artwork[ArtworkKind.THUMBNAIL] ?: show?.artwork?.get(ArtworkKind.BACKDROP),
                    detailsId = item.showId,
                )
            }
            else -> MediaCard(
                entry = entry,
                title = item.title,
                caption = Formatting.year(item)?.toString(),
                poster = entry.artwork[ArtworkKind.POSTER],
                wide = entry.artwork[ArtworkKind.BACKDROP],
                detailsId = if (item is Season) item.showId else item.id,
            )
        }
    }
}

@Immutable
data class HomeRowModel(
    val id: String,
    val title: String,
    val cards: List<MediaCard>,
    /** A smart search that lists the whole row. */
    val seeAll: String?,
    /** Rows about progress use landscape cards with room for what is left. */
    val wide: Boolean,
)

@Immutable
data class HomeModel(val featured: MediaCard?, val featuredOverview: String?, val rows: List<HomeRowModel>, val sources: List<SourceRecord>) {
    val empty: Boolean get() = rows.isEmpty()
}

internal fun Library.homeModel(): HomeModel {
    val feed = home()
    val rows = feed.rows.map { HomeRowModel(it.id, it.title, cards(it.entries), it.seeAll, wide = it.id == "continue") }
    // Home always opens on something: without backdrops (no metadata yet), the first work of the first row.
    val featured = feed.featured?.let { cards(listOf(it)).single() } ?: rows.firstOrNull()?.cards?.firstOrNull()
    val overview = featured?.let { entry(it.detailsId)?.metadata?.overview ?: it.entry.metadata?.overview }
    return HomeModel(featured, overview, rows, sources())
}

@Immutable
data class SearchModel(val interpretation: List<String>, val cards: List<MediaCard>)

internal fun Library.searchModel(query: String): SearchModel {
    val result = smartSearch(query)
    val understood: SmartQuery = result.query
    return SearchModel(if (understood.structured) Formatting.interpretation(understood) else emptyList(), cards(result.results))
}

/** One playable version on a details page, with where it lives and how it will play here. */
@Immutable
data class VersionRow(
    val ranked: RankedVersion,
    val sourceName: String,
    val facts: List<String>,
    val notes: List<String>,
    val best: Boolean,
)

@Immutable
data class SeasonModel(val season: Season, val episodes: List<MediaCard>)

@Immutable
data class DetailsModel(
    val card: MediaCard,
    val overview: String?,
    val tagline: String?,
    val facts: List<String>,
    val genres: List<String>,
    val badges: List<String>,
    val backdrop: ArtworkLocator?,
    val credits: List<Credit>,
    /** What the main button plays, and how it is labelled ("Resume", "Continue S1 · E3"). */
    val playId: MediaId?,
    val playLabel: String,
    /** Set when the main button resumes, so "Play from start" can be offered too. */
    val resumes: Boolean,
    val versions: List<VersionRow>,
    val bestReason: SelectionCriterion?,
    val seasons: List<SeasonModel>,
    /** The season to open on: the one holding the episode to continue with. */
    val initialSeason: Int,
)

/**
 * Everything a details page shows, or null when the work is not in the library: unknown, or remembered only
 * for its watch history because its last file is gone.
 */
internal fun Library.detailsModel(itemId: MediaId, device: DeviceCapabilities): DetailsModel? {
    val entry = entry(itemId) ?: return null
    val card = cards(listOf(entry)).single()
    val metadata = entry.metadata
    val names = mutableMapOf<SourceId, String>()
    return when (val item = entry.item) {
        is Show -> {
            val detail = showDetail(item.id) ?: return null
            val seasons = detail.seasons.map { SeasonModel(it.season, cards(it.episodes)) }
            if (seasons.none { it.episodes.isNotEmpty() }) return null
            val next = detail.nextUp ?: (detail.seasons.firstOrNull { it.season.number > 0 } ?: detail.seasons.firstOrNull())
                ?.episodes?.firstOrNull()?.item as? Episode
            val started = detail.nextUp != null
            DetailsModel(
                card = card,
                overview = metadata?.overview,
                tagline = metadata?.tagline,
                facts = Formatting.facts(entry, runtimeMinutes = null) +
                    listOfNotNull(seasons.count { it.season.number > 0 }.takeIf { it > 0 }?.let { if (it == 1) "1 season" else "$it seasons" }),
                genres = metadata?.genres.orEmpty(),
                badges = emptyList(),
                backdrop = entry.artwork[ArtworkKind.BACKDROP],
                credits = credits(itemId),
                playId = next?.id,
                playLabel = next?.let { (if (started) "Continue " else "Play ") + Formatting.episodeNumber(it) } ?: "Play",
                resumes = false,
                versions = emptyList(),
                bestReason = null,
                seasons = seasons,
                initialSeason = seasons.indexOfFirst { it.season.id == next?.seasonId }.coerceAtLeast(0),
            )
        }
        is Movie -> {
            val selection = selectVersion(item.id, device)
            if (selection.ranked.isEmpty()) return null
            val best = selection.best
            val runtime = metadata?.runtimeMinutes ?: selection.ranked.firstNotNullOfOrNull { it.version.stream.durationMs }?.let { (it / 60_000).toInt() }
            val resumes = WatchRules.resumePosition(entry.watchState) != null
            DetailsModel(
                card = card,
                overview = metadata?.overview,
                tagline = metadata?.tagline,
                facts = Formatting.facts(entry, runtime),
                genres = metadata?.genres.orEmpty(),
                badges = Formatting.qualityBadges(selection.ranked.map { it.version }),
                backdrop = entry.artwork[ArtworkKind.BACKDROP],
                credits = credits(itemId),
                playId = item.id,
                playLabel = if (resumes) "Resume" else "Play",
                resumes = resumes,
                versions = selection.ranked.map { ranked ->
                    val sourceId = ranked.version.location.sourceId
                    VersionRow(
                        ranked = ranked,
                        sourceName = names.getOrPut(sourceId) { source(sourceId)?.displayName ?: "Unknown source" },
                        facts = Formatting.version(ranked.version) + Formatting.size(ranked.version.sizeBytes),
                        notes = if (ranked.candidate.availability == Availability.UNAVAILABLE) {
                            listOf("Offline")
                        } else {
                            ranked.assessment.compromises.map(Formatting::issue)
                        },
                        best = ranked.version.id == best?.version?.id,
                    )
                },
                bestReason = selection.decidedBy.takeIf { selection.ranked.size > 1 },
                seasons = emptyList(),
                initialSeason = 0,
            )
        }
        else -> null
    }
}
