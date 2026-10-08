package dev.reflux.library

import dev.reflux.core.identify.ArtworkScope
import dev.reflux.core.identify.IdentificationSignal
import dev.reflux.core.identify.Identification
import dev.reflux.core.identify.Identifier
import dev.reflux.core.identify.IdentityKeys
import dev.reflux.core.identify.IdentityOverride
import dev.reflux.core.identify.MediaPathParser
import dev.reflux.core.identify.ParsedKind
import dev.reflux.core.identify.ParsedMedia
import dev.reflux.core.identify.SidecarMatcher
import dev.reflux.core.identify.TitleText
import dev.reflux.core.model.Artwork
import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.Episode
import dev.reflux.core.model.ExternalSubtitle
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaItem
import dev.reflux.core.model.MediaLocation
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.Movie
import dev.reflux.core.model.Season
import dev.reflux.core.model.Show
import dev.reflux.core.model.SourceId
import dev.reflux.core.model.StableIds
import dev.reflux.core.model.StreamInfoOrigin
import dev.reflux.core.source.FileRole
import dev.reflux.core.source.ScanRules
import dev.reflux.core.source.SourceFile

/** A version as planned by a scan, with the parse that produced it. */
internal data class PlannedVersion(val version: MediaVersion, val parsed: ParsedMedia)

/** Everything one scan of one source contributes to the library. Pure data; applying it is separate. */
internal data class ScanPlan(
    val sourceId: SourceId,
    /** Shows and seasons first, so parents precede children. */
    val items: List<MediaItem>,
    val versions: List<PlannedVersion>,
    val artwork: List<Artwork>,
    val skipped: Map<ParsedKind, Int>,
)

/**
 * Turns a file listing into a [ScanPlan]: parse, apply overrides, identify, choose titles, attach sidecars.
 *
 * Deterministic: the same listing, overrides, and hints always produce the same plan.
 */
internal class ScanPlanner(private val parser: MediaPathParser) {

    fun plan(
        sourceId: SourceId,
        files: List<SourceFile>,
        overrides: Map<String, IdentityOverride>,
        externalYearHints: Map<Pair<ParsedKind, String>, Set<Int>>,
    ): ScanPlan {
        val videos = files.filter { ScanRules.roleOf(it.path.substringAfterLast('/')) == FileRole.VIDEO }
        val skipped = mutableMapOf<ParsedKind, Int>()
        val parsedByFile = videos.mapNotNull { file ->
            val parsed = parser.parse(file.path) ?: return@mapNotNull null
            val effective = overrides[file.path]?.applyTo(parsed) ?: parsed
            if (effective.kind != ParsedKind.MOVIE && effective.kind != ParsedKind.EPISODE) {
                skipped[effective.kind] = (skipped[effective.kind] ?: 0) + 1
                null
            } else {
                file to effective
            }
        }

        val hints = mergeHints(externalYearHints, Identifier.yearHintsOf(parsedByFile.map { it.second }))
        val identifier = Identifier { kind, key -> hints[kind to key].orEmpty() }
        val identified = parsedByFile.mapNotNull { (file, parsed) -> identifier.identify(parsed)?.let { file to it } }

        val sidecars = SidecarMatcher.match(files.map { it.path })
        val subtitlesByVideo = sidecars.subtitles.groupBy { it.videoPath }

        val versions = identified.map { (file, identification) ->
            val location = MediaLocation(sourceId, file.path)
            val parsed = identification.parsed
            PlannedVersion(
                version = MediaVersion(
                    id = StableIds.versionId(location),
                    itemId = identification.playable.id,
                    location = location,
                    sizeBytes = file.sizeBytes,
                    modifiedAtEpochMs = file.modifiedAtEpochMs,
                    stream = parsed.stream,
                    streamOrigin = StreamInfoOrigin.FILENAME_HINTS,
                    edition = parsed.edition,
                    part = parsed.part,
                    externalSubtitles = subtitlesByVideo[file.path].orEmpty().map {
                        ExternalSubtitle(MediaLocation(sourceId, it.subtitlePath), it.format, it.language, it.forced, it.hearingImpaired)
                    },
                ),
                parsed = parsed,
            )
        }

        val items = chooseItems(identified.map { it.second })
        val identificationByPath = identified.associate { (file, identification) -> file.path to identification }
        val artwork = resolveArtwork(sourceId, sidecars.artwork, identificationByPath, items.map { it.id }.toSet())
        return ScanPlan(sourceId, items, versions, artwork, skipped)
    }

    private fun mergeHints(
        a: Map<Pair<ParsedKind, String>, Set<Int>>,
        b: Map<Pair<ParsedKind, String>, Set<Int>>,
    ): Map<Pair<ParsedKind, String>, Set<Int>> =
        (a.keys + b.keys).associateWith { a[it].orEmpty() + b[it].orEmpty() }

    /**
     * One item per ID. When several files describe the same work, the most trustworthy description wins:
     * curated folder names first, then confidence, then alphabetical order for determinism.
     */
    private fun chooseItems(identifications: List<Identification>): List<MediaItem> {
        val ranked = identifications.sortedWith(
            compareByDescending<Identification> { it.parsed.isCurated() }
                .thenByDescending { it.parsed.confidence.score }
                .thenBy { it.parsed.title },
        )
        val shows = linkedMapOf<MediaId, Show>()
        val seasons = linkedMapOf<MediaId, Season>()
        val playables = linkedMapOf<MediaId, MediaItem>()
        for (identification in ranked) {
            when (identification) {
                is Identification.OfMovie -> playables.getOrPut(identification.movie.id) { identification.movie }
                is Identification.OfEpisode -> {
                    shows.getOrPut(identification.show.id) { identification.show }
                    seasons.getOrPut(identification.season.id) { identification.season }
                    val existing = playables[identification.episode.id] as Episode?
                    playables[identification.episode.id] = mergeEpisode(existing, identification.episode)
                }
            }
        }
        return shows.values.sortedBy { it.id.value } +
            seasons.values.sortedBy { it.id.value } +
            playables.values.sortedBy { it.id.value }
    }

    /** Keeps the first (best-ranked) episode but fills gaps such as a missing title from other versions. */
    private fun mergeEpisode(existing: Episode?, candidate: Episode): Episode {
        if (existing == null) return candidate
        return existing.copy(
            episodeTitle = existing.episodeTitle ?: candidate.episodeTitle,
            episodeNumberEnd = existing.episodeNumberEnd ?: candidate.episodeNumberEnd,
        )
    }

    private fun ParsedMedia.isCurated(): Boolean {
        val signals = confidence.signals
        return IdentificationSignal.SHOW_FOLDER in signals ||
            IdentificationSignal.FOLDER_AGREES in signals ||
            IdentificationSignal.TITLE_FROM_FOLDER in signals
    }

    private fun resolveArtwork(
        sourceId: SourceId,
        sidecars: List<dev.reflux.core.identify.ArtworkSidecar>,
        identificationByPath: Map<String, Identification>,
        knownItems: Set<MediaId>,
    ): List<Artwork> {
        val origin = localArtworkOrigin(sourceId)
        val resolved = sidecars.mapNotNull { sidecar ->
            val target = when (val scope = sidecar.scope) {
                is ArtworkScope.Video -> identificationByPath[scope.videoPath]?.let { identification ->
                    val kind = if (identification is Identification.OfMovie && sidecar.kind == ArtworkKind.THUMBNAIL) {
                        ArtworkKind.POSTER // "Movie (2014).jpg" is a poster by convention
                    } else {
                        sidecar.kind
                    }
                    Triple(identification.playable.id, kind, 0)
                }
                is ArtworkScope.SeasonOf -> singleShowUnder(scope.directoryPath, identificationByPath)?.let { show ->
                    val seasonKey = IdentityKeys.season(IdentityKeys.show(show.title, show.year), scope.season)
                    Triple(StableIds.mediaId(seasonKey), sidecar.kind, 1)
                }
                is ArtworkScope.Directory -> directoryOwner(scope.directoryPath, identificationByPath)?.let {
                    Triple(it, sidecar.kind, 2)
                }
            } ?: return@mapNotNull null
            if (target.first !in knownItems) return@mapNotNull null
            Triple(target, sidecar.imagePath, nameRank(sidecar.imagePath))
        }
        // Most specific scope first, then the conventional name, then path order.
        return resolved
            .sortedWith(compareBy({ it.first.third }, { it.third }, { it.second }))
            .distinctBy { it.first.first to it.first.second }
            .map { (target, path, _) ->
                Artwork(target.first, target.second, ArtworkLocator.SourceFile(MediaLocation(sourceId, path)), origin)
            }
    }

    private fun nameRank(path: String): Int =
        when (path.substringAfterLast('/').substringBeforeLast('.').lowercase()) {
            "poster", "fanart", "logo" -> 0
            else -> 1
        }

    /** The work a directory represents: its single movie, or the show (or season) all its episodes belong to. */
    private fun directoryOwner(directory: String, identificationByPath: Map<String, Identification>): MediaId? {
        val direct = identificationByPath.filterKeys { it.substringBeforeLast('/', "") == directory }.values
        val movies = direct.filterIsInstance<Identification.OfMovie>().map { it.movie.id }.distinct()
        if (movies.size == 1 && direct.all { it is Identification.OfMovie }) return movies.single()

        val prefix = if (directory.isEmpty()) "" else "$directory/"
        val below = identificationByPath.filterKeys { it.startsWith(prefix) }.values
        val episodes = below.filterIsInstance<Identification.OfEpisode>()
        if (episodes.isEmpty() || episodes.size != below.size) return null
        val shows = episodes.map { it.show.id }.distinct()
        if (shows.size != 1) return null
        val folderSeason = parser.seasonOfFolder(directory.substringAfterLast('/'))
        if (folderSeason != null) {
            val seasons = episodes.map { it.season }.distinct()
            return seasons.singleOrNull()?.id
        }
        return shows.single()
    }

    private fun singleShowUnder(directory: String, identificationByPath: Map<String, Identification>): Show? {
        val prefix = if (directory.isEmpty()) "" else "$directory/"
        return identificationByPath.filterKeys { it.startsWith(prefix) }.values
            .filterIsInstance<Identification.OfEpisode>()
            .map { it.show }
            .distinct()
            .singleOrNull()
    }

    companion object {
        fun localArtworkOrigin(sourceId: SourceId): String = "local:${sourceId.value}"
    }
}

internal fun MediaItem.titleKey(): String = TitleText.key(title)

internal fun MediaItem.yearOrNull(): Int? = when (this) {
    is Movie -> year
    is Show -> year
    else -> null
}
