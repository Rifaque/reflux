package dev.reflux.ui.state

import androidx.compose.runtime.Immutable
import dev.reflux.core.metadata.Credit
import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.Availability
import dev.reflux.core.model.Episode
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaItem
import dev.reflux.core.model.Movie
import dev.reflux.core.model.Season
import dev.reflux.core.model.Show
import dev.reflux.core.model.SourceId
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.TrackPreferences
import dev.reflux.core.source.SourceCapability
import dev.reflux.core.search.SmartQuery
import dev.reflux.core.versions.RankedVersion
import dev.reflux.core.versions.SelectionCriterion
import dev.reflux.core.versions.VersionSelection
import dev.reflux.core.watch.Timeline
import dev.reflux.core.watch.TimelineMatch
import dev.reflux.core.watch.Timelines
import dev.reflux.core.watch.WatchRules
import dev.reflux.library.Library
import dev.reflux.library.identificationStatus
import dev.reflux.library.userSources
import dev.reflux.library.LibraryEntry
import dev.reflux.library.SourceRecord
import dev.reflux.library.CastMember
import dev.reflux.library.cast
import dev.reflux.library.pinnedVersions
import dev.reflux.library.episodeVersionChoices
import dev.reflux.library.versionChoices
import dev.reflux.library.downloads
import dev.reflux.library.worksOf
import dev.reflux.core.offline.DownloadState
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
    /** It is kept on this device: a film or an episode, or every episode of a show. */
    val downloaded: Boolean = false,
    /** Some episodes of a show are kept on this device, not all of them. */
    val downloadedPartly: Boolean = false,
) {
    val id: MediaId get() = entry.item.id
    val watched: Boolean get() = entry.watchState?.completed == true
    val progress: Float? get() = Formatting.progress(entry.watchState)
    val offline: Boolean get() = entry.availability == Availability.UNAVAILABLE
}

internal fun Library.cards(entries: List<LibraryEntry>): List<MediaCard> {
    val shows = mutableMapOf<MediaId, LibraryEntry?>()
    val kept = downloads().filter { it.state == DownloadState.COMPLETED }.map { it.itemId }.toSet()
    // A show is kept when every episode of it is; one with some of them kept says so, differently.
    val keptOfShow = kept.mapNotNull { item(it) as? Episode }.groupBy { it.showId }
    val ofShow = mutableMapOf<MediaId, Pair<Boolean, Boolean>>()
    fun show(item: MediaItem): Pair<Boolean, Boolean> = ofShow.getOrPut(item.id) {
        val episodes = when (item) {
            is Show -> keptOfShow[item.id].orEmpty()
            is Season -> keptOfShow[item.showId].orEmpty().filter { it.seasonId == item.id }
            else -> emptyList()
        }
        if (episodes.isEmpty()) return@getOrPut false to false
        val all = episodes.size >= worksOf(item.id).size
        all to !all
    }
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
                    downloaded = item.id in kept,
                )
            }
            // A season without a poster of its own shows its show's: the picture of what it is part of.
            is Season -> MediaCard(
                entry = entry,
                title = item.title,
                caption = null,
                poster = entry.artwork[ArtworkKind.POSTER] ?: shows.getOrPut(item.showId) { entry(item.showId) }?.artwork?.get(ArtworkKind.POSTER),
                wide = entry.artwork[ArtworkKind.BACKDROP] ?: shows.getOrPut(item.showId) { entry(item.showId) }?.artwork?.get(ArtworkKind.BACKDROP),
                detailsId = item.showId,
                downloaded = item.id in kept || show(item).first,
                downloadedPartly = show(item).second,
            )
            else -> MediaCard(
                entry = entry,
                title = item.title,
                caption = Formatting.year(item)?.toString(),
                poster = entry.artwork[ArtworkKind.POSTER],
                wide = entry.artwork[ArtworkKind.BACKDROP],
                detailsId = if (item is Season) item.showId else item.id,
                downloaded = item.id in kept || show(item).first,
                downloadedPartly = show(item).second,
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
    return HomeModel(featured, overview, rows, userSources())
}

@Immutable
data class SearchModel(val interpretation: List<String>, val cards: List<MediaCard>)

internal fun Library.searchModel(query: String): SearchModel {
    val result = smartSearch(query)
    val understood: SmartQuery = result.query
    return SearchModel(if (understood.structured) Formatting.interpretation(understood) else emptyList(), cards(result.results))
}

/**
 * One playable version on a details page, with where it lives and how it will play here. What a person
 * chooses a copy by ([quality]: resolution, HDR, edition) is kept apart from the technical facts that only
 * explain it ([technical]: codecs, size), so the page can lead with the first.
 */
@Immutable
data class VersionRow(
    val ranked: RankedVersion,
    val sourceName: String,
    val quality: List<String>,
    val technical: List<String>,
    val notes: List<String>,
    val best: Boolean,
    /** The user's choice for the work ("Always play this"), whether or not it is the one Play uses this time. */
    val pinned: Boolean = false,
    /** Whether it can be made the user's choice: one of several versions, and one this device plays. */
    val pinnable: Boolean = false,
)

/**
 * A season of a show page. [versions] is, for each episode with more than one version to choose between, how
 * many there are; an episode in one copy (and its download) is not in it, so its row offers nothing more.
 */
@Immutable
data class SeasonModel(
    val season: Season,
    val episodes: List<MediaCard>,
    val versions: Map<MediaId, Int> = emptyMap(),
    /** The season's own name, where it has one beyond its number ("Book One: Water"). */
    val name: String? = null,
    val overview: String? = null,
    /** When it began airing. */
    val aired: dev.reflux.core.model.CalendarDate? = null,
    /** Its poster, or the show's where it has none of its own. */
    val poster: ArtworkLocator? = null,
)

@Immutable
data class DetailsModel(
    val card: MediaCard,
    val overview: String?,
    val tagline: String?,
    val facts: List<String>,
    val genres: List<String>,
    val badges: List<String>,
    val backdrop: ArtworkLocator?,
    /** Who is in it, directors and creators first, with their pictures wherever those live. */
    val credits: List<CastMember>,
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
    /** Whether Reflux is unsure what this is, which the page says, offering Identify. */
    val weak: Boolean = false,
    /** For an episode's own page: the show it belongs to, where its other episodes are and what identifies it. */
    val showId: MediaId? = null,
    /**
     * Said above the versions when the user's choice is not what Play uses this time, and why (its source is
     * away, this device cannot play it, another cut is being resumed); the choice itself is kept.
     */
    val pinNote: String? = null,
    /** Its title in its own language, where that is not the title shown. */
    val originalTitle: String? = null,
)

/**
 * Everything a details page shows, or null when the work is not in the library: unknown, or remembered only
 * for its watch history because its last file is gone.
 */
internal fun Library.detailsModel(
    itemId: MediaId,
    device: DeviceCapabilities,
    preferences: TrackPreferences? = null,
    sourceCapabilities: (SourceId) -> Set<SourceCapability> = { emptySet() },
    scope: dev.reflux.library.LibraryScope = dev.reflux.library.LibraryScope.Unified,
): DetailsModel? {
    val entry = entry(itemId, scope) ?: return null
    val card = cards(listOf(entry)).single()
    val metadata = entry.metadata
    val names = mutableMapOf<SourceId, String>()
    return when (val item = entry.item) {
        is Show -> {
            val detail = showDetail(item.id, scope) ?: return null
            val choices = episodeVersionChoices(item.id).filterValues { it > 1 }
            val seasons = detail.seasons.map { season ->
                val described = season.entry.metadata
                SeasonModel(
                    season.season,
                    cards(season.episodes),
                    season.episodes.mapNotNull { e -> choices[e.item.id]?.let { e.item.id to it } }.toMap(),
                    name = described?.title?.takeUnless { Formatting.genericSeasonName(it, season.season.number) },
                    overview = described?.overview,
                    aired = described?.releaseDate,
                    poster = season.entry.artwork[ArtworkKind.POSTER] ?: entry.artwork[ArtworkKind.POSTER],
                )
            }
            if (seasons.none { it.episodes.isNotEmpty() }) return null
            val next = detail.nextUp ?: (detail.seasons.firstOrNull { it.season.number > 0 } ?: detail.seasons.firstOrNull())
                ?.episodes?.firstOrNull()?.item as? Episode
            val started = detail.nextUp != null
            DetailsModel(
                card = card,
                overview = metadata?.overview,
                tagline = metadata?.tagline,
                // When it aired, on what, and how much of it there is: what a show is, said once.
                facts = listOfNotNull(Formatting.airedYears(metadata?.releaseDate?.year ?: Formatting.year(item), metadata?.endDate?.year)) +
                    Formatting.facts(entry, runtimeMinutes = null).drop(if (Formatting.year(item) != null) 1 else 0) +
                    listOfNotNull(metadata?.studios?.firstOrNull()) +
                    listOfNotNull(seasons.count { it.season.number > 0 }.takeIf { it > 0 }?.let { if (it == 1) "1 season" else "$it seasons" }),
                genres = metadata?.genres.orEmpty(),
                badges = emptyList(),
                backdrop = entry.artwork[ArtworkKind.BACKDROP],
                credits = cast(itemId),
                playId = next?.id,
                playLabel = next?.let { (if (started) "Continue " else "Play ") + Formatting.episodeNumber(it) } ?: "Play",
                resumes = false,
                versions = emptyList(),
                bestReason = null,
                seasons = seasons,
                initialSeason = seasons.indexOfFirst { it.season.id == next?.seasonId }.coerceAtLeast(0),
                weak = identificationStatus(itemId)?.weak == true,
                originalTitle = Formatting.originalTitle(metadata?.originalTitle, card.title),
            )
        }
        is Movie -> {
            val selection = selectVersion(item.id, device, preferences = preferences, sourceCapabilities = sourceCapabilities, scope = scope)
            if (selection.ranked.isEmpty()) return null
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
                credits = cast(itemId),
                playId = item.id,
                playLabel = if (resumes) "Resume" else "Play",
                resumes = resumes,
                versions = versionRows(item.id, selection, names),
                bestReason = selection.decidedBy.takeIf { selection.ranked.size > 1 },
                seasons = emptyList(),
                initialSeason = 0,
                weak = identificationStatus(itemId)?.weak == true,
                pinNote = pinNote(item.id, selection),
                originalTitle = Formatting.originalTitle(metadata?.originalTitle, card.title),
            )
        }
        // An episode's own page: its versions, as a film's, under its show's name. Reached from the episode's
        // row when it has several, and from outside Reflux (Watch Next), which names the episode.
        is Episode -> {
            val selection = selectVersion(item.id, device, preferences = preferences, sourceCapabilities = sourceCapabilities, scope = scope)
            if (selection.ranked.isEmpty()) return null
            val runtime = metadata?.runtimeMinutes ?: selection.ranked.firstNotNullOfOrNull { it.version.stream.durationMs }?.let { (it / 60_000).toInt() }
            val resumes = WatchRules.resumePosition(entry.watchState) != null
            DetailsModel(
                card = card,
                overview = metadata?.overview,
                tagline = card.caption,
                // When it aired and how long it is: an episode's year is its show's, so its date says more.
                facts = listOfNotNull(item.airDate?.let(Formatting::date) ?: metadata?.releaseDate?.let(Formatting::date)) +
                    Formatting.facts(entry, runtime).drop(if (Formatting.year(item) != null) 1 else 0),
                genres = entry(item.showId)?.metadata?.genres.orEmpty(),
                badges = Formatting.qualityBadges(selection.ranked.map { it.version }),
                backdrop = card.wide,
                // Its own credits: guest cast and directors, not the show's whole cast again.
                credits = cast(itemId),
                playId = item.id,
                playLabel = if (resumes) "Resume" else "Play",
                resumes = resumes,
                versions = versionRows(item.id, selection, names),
                bestReason = selection.decidedBy.takeIf { selection.ranked.size > 1 },
                seasons = emptyList(),
                initialSeason = 0,
                showId = item.showId,
                pinNote = pinNote(item.id, selection),
            )
        }
        else -> null
    }
}

/**
 * A work's versions as its page lists them, best first. Every version is listed, a copy kept on the device
 * among them; whether a choice can be pinned counts a copy and the version it was made from as one, since
 * pinning either pins the origin. A version on another timeline than the recommended one (another cut, or a
 * running time too far apart) says so, so copies that are not the same film or episode never look alike.
 */
private fun Library.versionRows(itemId: MediaId, selection: VersionSelection, names: MutableMap<SourceId, String>): List<VersionRow> {
    val best = selection.best
    val pins = pinnedVersions(itemId)
    val choices = versionChoices(itemId, selection.ranked.map { it.version.id })
    val reference = (best ?: selection.ranked.first()).version.let(Timeline::of)
    return selection.ranked.map { ranked ->
        val sourceId = ranked.version.location.sourceId
        val otherCut = ranked.version.id != best?.version?.id && Timelines.compare(reference, Timeline.of(ranked.version)) == TimelineMatch.DIFFERENT
        VersionRow(
            ranked = ranked,
            sourceName = names.getOrPut(sourceId) { source(sourceId)?.displayName ?: "Unknown source" },
            quality = Formatting.versionQuality(ranked.version),
            technical = Formatting.versionTechnical(ranked.version) + Formatting.size(ranked.version.sizeBytes),
            notes = listOfNotNull(Formatting.otherCut(ranked.version).takeIf { otherCut }) +
                if (ranked.candidate.availability == Availability.UNAVAILABLE) {
                    listOf("Not connected")
                } else {
                    ranked.assessment.compromises.map(Formatting::issue)
                },
            best = ranked.version.id == best?.version?.id,
            pinned = ranked.version.id in pins,
            // A pin never makes Reflux play something this device cannot play (WATCH_STATE.md).
            pinnable = choices > 1 && ranked.playable,
        )
    }
}

/**
 * Why the user's pinned version is not the one Play uses this time, when it is not; null when it is, or when
 * nothing is pinned. Best Version's own order decides: what is reachable and playable here, then the cut
 * being resumed, then the pin (WATCH_STATE.md). The pin is kept either way.
 */
private fun Library.pinNote(itemId: MediaId, selection: VersionSelection): String? {
    val pins = pinnedVersions(itemId)
    if (pins.isEmpty() || selection.best?.version?.id in pins) return null
    val pinned = selection.ranked.filter { it.version.id in pins }
    val instead = if (selection.best != null) "Play uses the best version here" else "nothing here can play now"
    return when {
        pinned.isEmpty() -> "Your choice is no longer in your library, so Play uses the best version here."
        pinned.all { it.candidate.availability == Availability.UNAVAILABLE } -> "Your choice isn't connected right now, so $instead. It stays your choice."
        pinned.none { it.playable } -> "Your choice can't be played on this device, so $instead. It stays your choice."
        else -> "Play continues the cut you're part-way through. Your choice stays for next time."
    }
}
