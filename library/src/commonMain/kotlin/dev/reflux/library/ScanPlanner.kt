package dev.reflux.library

import dev.reflux.core.identify.ArtworkScope
import dev.reflux.core.identify.Confidence
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
import dev.reflux.core.metadata.Credit
import dev.reflux.core.metadata.CreditRole
import dev.reflux.core.metadata.Description
import dev.reflux.core.source.CatalogSeason
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
import dev.reflux.core.model.CalendarDate
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.source.CatalogEntry
import dev.reflux.core.source.CatalogSubtitle
import dev.reflux.core.source.CatalogVersion
import dev.reflux.core.source.FileRole
import dev.reflux.core.source.ScanRules
import dev.reflux.core.source.SourceFile

/** A version as planned by a scan, with the description that produced it. */
internal data class PlannedVersion(
    val version: MediaVersion,
    val parsed: ParsedMedia,
    /** For catalog sources: the encoded source description, kept for offline re-identification. */
    val sourceIdentity: String? = null,
    /** The library of its source the version is in, for sources made of libraries. */
    val section: String? = null,
    /** For catalog sources: the encoded description of its work (and, once per scan, its show and season). */
    val sourceDescription: String? = null,
)

/** Everything one scan of one source contributes to the library. Pure data; applying it is separate. */
internal data class ScanPlan(
    val sourceId: SourceId,
    /** Shows and seasons first, so parents precede children. */
    val items: List<MediaItem>,
    val versions: List<PlannedVersion>,
    val artwork: List<Artwork>,
    val skipped: Map<ParsedKind, Int>,
    /** Provider IDs from file and folder names, per movie or show. */
    val externalHints: Map<MediaId, Map<String, String>> = emptyMap(),
    /** The (canonical) identity key of each movie and show. */
    val identityKeys: Map<MediaId, String> = emptyMap(),
    /** What the source itself says each work is about (catalog sources), at most one per work. */
    val descriptions: List<PlannedDescription> = emptyList(),
)

/** A source's own description of one work (movie, show, season, or episode). */
internal data class PlannedDescription(val itemId: MediaId, val description: Description)

/** A playable file or stream with what is known about it, before identification. */
private data class Candidate(
    val file: SourceFile,
    val parsed: ParsedMedia,
    val origin: StreamInfoOrigin,
    val subtitles: List<ExternalSubtitle>,
    val sourceIdentity: String? = null,
    val section: String? = null,
    val sourceDescription: String? = null,
)

/**
 * Turns a source listing into a [ScanPlan]: describe each file (parse, or take the catalog's description),
 * apply user overrides, identify, choose titles, and attach subtitles and artwork.
 *
 * Deterministic: the same listing, overrides, and hints always produce the same plan.
 */
internal class ScanPlanner(
    private val parser: MediaPathParser,
    /** Unified identity keys (alias → canonical), see [Library.unifyWorks]. */
    private val aliases: Map<String, String> = emptyMap(),
) {
    private fun canonical(key: String): String {
        var current = key
        repeat(MAX_ALIAS_DEPTH) { current = aliases[current] ?: return current }
        return current
    }


    /** Plans a file-enumerating source: everything is learned from paths and sidecar files. */
    fun plan(
        sourceId: SourceId,
        files: List<SourceFile>,
        overrides: Map<String, IdentityOverride>,
        externalYearHints: Map<Pair<ParsedKind, String>, Set<Int>>,
        /** The source's name, which titles episodes at its top whose path names no show. */
        sourceName: String = "",
    ): ScanPlan {
        val videos = files.filter { ScanRules.roleOf(it.path.substringAfterLast('/')) == FileRole.VIDEO }
        val skipped = mutableMapOf<ParsedKind, Int>()
        val sidecars = SidecarMatcher.match(files.map { it.path })
        val subtitlesByVideo = sidecars.subtitles.groupBy { it.videoPath }
        val candidates = videos.mapNotNull { file ->
            val parsed = parser.parse(file.path) ?: return@mapNotNull null
            val effective = (overrides[file.path]?.applyTo(parsed) ?: parsed).let { grouped(sourceId, sourceName, file.path, it) }
            if (effective.kind != ParsedKind.MOVIE && effective.kind != ParsedKind.EPISODE) {
                skipped[effective.kind] = (skipped[effective.kind] ?: 0) + 1
                return@mapNotNull null
            }
            val subtitles = subtitlesByVideo[file.path].orEmpty().map {
                ExternalSubtitle(MediaLocation(sourceId, it.subtitlePath), it.format, it.language, it.forced, it.hearingImpaired)
            }
            Candidate(file, effective, StreamInfoOrigin.FILENAME_HINTS, subtitles)
        }
        val assembled = assemble(sourceId, candidates, externalYearHints)
        val artwork = resolveArtwork(sourceId, sidecars.artwork, assembled.identificationByPath, assembled.items.map { it.id }.toSet())
        return ScanPlan(sourceId, assembled.items, assembled.versions, artwork, skipped, assembled.externalHints, assembled.identityKeys)
    }

    /**
     * An episode whose path names no show (`S01E01.mkv` at a source's top, `TV/Season 1/S01E02.mkv`) belongs to
     * a show of its folder, titled after that folder or the source, and kept apart from every other folder's
     * (DECISIONS.md, 2026-10-17). It stays a weak identification, offered to Identify; a user's correction
     * gives it a title of its own and takes it out of the group.
     */
    private fun grouped(sourceId: SourceId, sourceName: String, path: String, parsed: ParsedMedia): ParsedMedia {
        if (parsed.kind != ParsedKind.EPISODE || parsed.title.isNotBlank()) return parsed
        val folder = parser.groupingFolder(path)
        val title = folder.substringAfterLast('/').ifEmpty { sourceName }.ifBlank { "Episodes" }
        return parsed.copy(title = title, group = "${sourceId.value}/$folder")
    }

    /** Plans a catalog source: identities, streams, subtitles, and artwork come from the source. */
    fun planCatalog(
        sourceId: SourceId,
        entries: List<CatalogEntry>,
        overrides: Map<String, IdentityOverride>,
        externalYearHints: Map<Pair<ParsedKind, String>, Set<Int>>,
    ): ScanPlan {
        val sourceConfidence = Confidence(0.95, setOf(IdentificationSignal.SOURCE_IDENTIFIED))
        // A show's and a season's description go with the first of its episodes listed, not with every one.
        val describedShows = HashSet<String>()
        val candidates = entries.flatMap { entry ->
            entry.versions.map { version ->
                val described = entry.identity.copy(
                    stream = version.stream,
                    edition = version.edition ?: entry.identity.edition,
                    confidence = sourceConfidence,
                )
                Candidate(
                    file = SourceFile(version.path, version.sizeBytes, version.modifiedAtEpochMs),
                    parsed = overrides[version.path]?.applyTo(described) ?: described,
                    origin = StreamInfoOrigin.SOURCE,
                    subtitles = version.subtitles.map {
                        ExternalSubtitle(MediaLocation(sourceId, it.path), it.format, it.language, it.forced, it.hearingImpaired)
                    },
                    sourceIdentity = encodeCatalogEntry(entry, version),
                    section = entry.section,
                    sourceDescription = encodeCatalogDescription(entry, describedShows),
                )
            }
        }
        val assembled = assemble(sourceId, candidates, externalYearHints)
        val origin = sourceArtworkOrigin(sourceId)
        val known = assembled.items.map { it.id }.toSet()
        fun art(id: MediaId?, images: Map<ArtworkKind, String>) =
            id?.let { images.map { (kind, path) -> Artwork(id, kind, ArtworkLocator.SourceFile(MediaLocation(sourceId, path)), origin) } }.orEmpty()
        val identified = entries.mapNotNull { entry ->
            entry.versions.firstNotNullOfOrNull { assembled.identificationByPath[it.path] }?.let { entry to it }
        }
        val artwork = identified.flatMap { (entry, identification) ->
            val episode = identification as? Identification.OfEpisode
            art(identification.playable.id, entry.artwork) + art(episode?.show?.id, entry.showArtwork) +
                art(episode?.season?.id, entry.season?.artwork.orEmpty())
        }.filter { it.itemId in known }.distinctBy { it.itemId to it.kind }
        // A work listed several times (its versions, or every episode naming its show) is described by its first listing.
        val descriptions = identified.flatMap { (entry, identification) ->
            val episode = identification as? Identification.OfEpisode
            listOfNotNull(
                entry.description?.let { PlannedDescription(identification.playable.id, it) },
                episode?.let { e -> entry.showDescription?.let { PlannedDescription(e.show.id, it) } },
                episode?.let { e -> entry.season?.description()?.let { PlannedDescription(e.season.id, it) } },
            )
        }.filter { it.itemId in known && !it.description.empty }.distinctBy { it.itemId }
        return ScanPlan(sourceId, assembled.items, assembled.versions, artwork, emptyMap(), assembled.externalHints, assembled.identityKeys, descriptions)
    }

    private class Assembled(
        val items: List<MediaItem>,
        val versions: List<PlannedVersion>,
        val identificationByPath: Map<String, Identification>,
        val externalHints: Map<MediaId, Map<String, String>>,
        val identityKeys: Map<MediaId, String>,
    )

    private fun assemble(
        sourceId: SourceId,
        candidates: List<Candidate>,
        externalYearHints: Map<Pair<ParsedKind, String>, Set<Int>>,
    ): Assembled {
        val hints = mergeHints(externalYearHints, Identifier.yearHintsOf(candidates.map { it.parsed }))
        val identifier = Identifier(yearHints = { kind, key -> hints[kind to key].orEmpty() }, canonicalKey = ::canonical)
        val identified = candidates.mapNotNull { candidate -> identifier.identify(candidate.parsed)?.let { candidate to it } }

        val versions = identified.map { (candidate, identification) ->
            val location = MediaLocation(sourceId, candidate.file.path)
            val parsed = identification.parsed
            PlannedVersion(
                version = MediaVersion(
                    id = StableIds.versionId(location),
                    itemId = identification.playable.id,
                    location = location,
                    sizeBytes = candidate.file.sizeBytes,
                    modifiedAtEpochMs = candidate.file.modifiedAtEpochMs,
                    stream = parsed.stream,
                    streamOrigin = candidate.origin,
                    edition = parsed.edition,
                    part = parsed.part,
                    externalSubtitles = candidate.subtitles,
                ),
                parsed = parsed,
                sourceIdentity = candidate.sourceIdentity,
                section = candidate.section,
                sourceDescription = candidate.sourceDescription,
            )
        }
        val externalHints = identified.groupBy({ (_, identification) ->
            when (identification) {
                is Identification.OfMovie -> identification.movie.id
                is Identification.OfEpisode -> identification.show.id
            }
        }, { (_, identification) -> identification.parsed.externalIds })
            .mapValues { (_, maps) -> maps.fold(emptyMap<String, String>()) { acc, map -> map + acc } }
            .filterValues { it.isNotEmpty() }
        val identityKeys = identified.associate { (_, identification) ->
            val parsed = identification.parsed
            when (identification) {
                is Identification.OfMovie -> identification.movie.id to canonical(IdentityKeys.movie(parsed.title, parsed.year))
                is Identification.OfEpisode -> identification.show.id to canonical(IdentityKeys.show(parsed.title, parsed.year, parsed.group))
            }
        }
        return Assembled(
            items = chooseItems(identified.map { it.second }),
            versions = versions,
            identificationByPath = identified.associate { (candidate, identification) -> candidate.file.path to identification },
            externalHints = externalHints,
            identityKeys = identityKeys,
        )
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
            IdentificationSignal.TITLE_FROM_FOLDER in signals ||
            IdentificationSignal.SOURCE_IDENTIFIED in signals
    }

    private fun resolveArtwork(
        sourceId: SourceId,
        sidecars: List<dev.reflux.core.identify.ArtworkSidecar>,
        identificationByPath: Map<String, Identification>,
        knownItems: Set<MediaId>,
    ): List<Artwork> {
        val origin = sourceArtworkOrigin(sourceId)
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
        /** Artwork that came with a source (sidecar files, a media server's images). It wins over provider artwork. */
        fun sourceArtworkOrigin(sourceId: SourceId): String = "$SOURCE_ARTWORK_PREFIX${sourceId.value}"

        const val SOURCE_ARTWORK_PREFIX = "source:"
        private const val MAX_ALIAS_DEPTH = 8

        /** Encodes what a catalog source said about one version (identity, edition, subtitles, artwork). */
        fun encodeCatalogEntry(entry: CatalogEntry, version: CatalogVersion): String {
            val identity = entry.identity
            val pairs = buildMap {
                put("kind", identity.kind.name)
                put("title", identity.title)
                identity.year?.let { put("year", it.toString()) }
                identity.season?.let { put("season", it.toString()) }
                identity.episode?.let { put("episode", it.toString()) }
                identity.episodeEnd?.let { put("episodeEnd", it.toString()) }
                identity.absoluteEpisode?.let { put("absolute", it.toString()) }
                identity.airDate?.let { put("airDate", it.toString()) }
                identity.episodeTitle?.let { put("episodeTitle", it) }
                (version.edition ?: identity.edition)?.let { put("edition", it) }
                entry.section?.let { put("section", it) }
                identity.externalIds.forEach { (key, value) -> put("id.$key", value) }
                entry.artwork.forEach { (kind, path) -> put("art.${kind.name}", path) }
                entry.showArtwork.forEach { (kind, path) -> put("showArt.${kind.name}", path) }
                // A season's poster is artwork, light, and goes with every episode; its words go with the description.
                entry.season?.artwork?.forEach { (kind, path) -> put("seasonArt.${kind.name}", path) }
                version.subtitles.forEachIndexed { index, sub ->
                    put("sub.$index", listOf(sub.path, sub.format.name, sub.language.orEmpty(), sub.forced, sub.hearingImpaired).joinToString("\t"))
                }
            }
            return encodePairs(pairs.mapValues { it.value.replace('\n', ' ') })
        }

        /**
         * Encodes what a catalog source says a version's work is about: its description and, the first time [shows]
         * meets its show (or season) in a scan, the show's and season's. Null when it says nothing.
         */
        fun encodeCatalogDescription(entry: CatalogEntry, shows: MutableSet<String>): String? {
            val identity = entry.identity
            val pairs = buildMap {
                entry.description?.let { encodeDescription("desc.", it, this) }
                if (identity.kind == ParsedKind.EPISODE) {
                    val show = "${identity.title}|${identity.year}"
                    if (shows.add(show)) entry.showDescription?.let { encodeDescription("showDesc.", it, this) }
                    if (shows.add("$show|${identity.season}")) entry.season?.let { season ->
                        season.title?.let { put("season.title", it) }
                        season.overview?.let { put("season.overview", it) }
                        season.airDate?.let { put("season.airDate", it.toString()) }
                    }
                }
            }
            return encodePairs(pairs.mapValues { it.value.replace('\n', ' ') }).takeIf { pairs.isNotEmpty() }
        }

        /** Rebuilds a single-version catalog entry from [encoded] and its [description] (see [encodeCatalogEntry]). */
        fun decodeCatalogEntry(encoded: String, path: String, size: Long, modified: Long, version: MediaVersion, description: String? = null): CatalogEntry? {
            val pairs = decodePairs(encoded) + description?.let(::decodePairs).orEmpty()
            val kind = pairs["kind"]?.let { enumOrNull<ParsedKind>(it) } ?: return null
            fun artwork(prefix: String) = pairs.filterKeys { it.startsWith(prefix) }.mapNotNull { (key, value) ->
                enumOrNull<ArtworkKind>(key.removePrefix(prefix))?.let { it to value }
            }.toMap()
            val subtitles = pairs.filterKeys { it.startsWith("sub.") }.toSortedMap().values.mapNotNull { value ->
                val parts = value.split('\t')
                if (parts.size != 5) return@mapNotNull null
                CatalogSubtitle(parts[0], enumOrNull<SubtitleFormat>(parts[1]) ?: SubtitleFormat.UNKNOWN, parts[2].ifEmpty { null }, parts[3].toBoolean(), parts[4].toBoolean())
            }
            return CatalogEntry(
                identity = ParsedMedia(
                    kind = kind,
                    title = pairs["title"].orEmpty(),
                    year = pairs["year"]?.toIntOrNull(),
                    season = pairs["season"]?.toIntOrNull(),
                    episode = pairs["episode"]?.toIntOrNull(),
                    episodeEnd = pairs["episodeEnd"]?.toIntOrNull(),
                    absoluteEpisode = pairs["absolute"]?.toIntOrNull(),
                    airDate = pairs["airDate"]?.let(CalendarDate::parse),
                    episodeTitle = pairs["episodeTitle"],
                    edition = pairs["edition"],
                    externalIds = pairs.filterKeys { it.startsWith("id.") }.mapKeys { it.key.removePrefix("id.") },
                ),
                versions = listOf(CatalogVersion(path, size, modified, version.stream, pairs["edition"], subtitles)),
                artwork = artwork("art."),
                showArtwork = artwork("showArt."),
                section = pairs["section"],
                description = decodeDescription("desc.", pairs),
                showDescription = decodeDescription("showDesc.", pairs),
                season = CatalogSeason(
                    title = pairs["season.title"],
                    overview = pairs["season.overview"],
                    airDate = pairs["season.airDate"]?.let(CalendarDate::parse),
                    artwork = artwork("seasonArt."),
                ).takeIf { it.title != null || it.overview != null || it.airDate != null || it.artwork.isNotEmpty() },
            )
        }

        /** Separates the values of a list, and the parts of a credit, in an encoded description. */
        private const val LIST = "\t"

        private fun encodeDescription(prefix: String, d: Description, into: MutableMap<String, String>) {
            fun put(key: String, value: Any?) {
                if (value != null && value.toString().isNotEmpty()) into["$prefix$key"] = value.toString()
            }
            put("title", d.title)
            put("originalTitle", d.originalTitle)
            put("overview", d.overview)
            put("tagline", d.tagline)
            put("genres", d.genres.joinToString(LIST))
            put("runtime", d.runtimeMinutes)
            put("released", d.releaseDate)
            put("ended", d.endDate)
            put("rating", d.rating)
            put("contentRating", d.contentRating)
            put("studios", d.studios.joinToString(LIST))
            d.credits.forEachIndexed { index, credit -> put("credit.${index.toString().padStart(3, '0')}", encodeCredit(credit)) }
        }

        private fun decodeDescription(prefix: String, pairs: Map<String, String>): Description? {
            fun get(key: String) = pairs["$prefix$key"]
            val credits = pairs.filterKeys { it.startsWith("${prefix}credit.") }.toSortedMap().values.mapNotNull(::decodeCredit)
            return Description(
                title = get("title"),
                originalTitle = get("originalTitle"),
                overview = get("overview"),
                tagline = get("tagline"),
                genres = get("genres")?.split(LIST).orEmpty().filter { it.isNotBlank() },
                runtimeMinutes = get("runtime")?.toIntOrNull(),
                releaseDate = get("released")?.let(CalendarDate::parse),
                endDate = get("ended")?.let(CalendarDate::parse),
                rating = get("rating")?.toDoubleOrNull(),
                contentRating = get("contentRating"),
                studios = get("studios")?.split(LIST).orEmpty().filter { it.isNotBlank() },
                credits = credits,
            ).takeUnless { it.empty }
        }

        /** A credit as one line: name, role, character, and its picture, tab separated. */
        internal fun encodeCredit(credit: Credit): String =
            listOf(credit.name, credit.role.name, credit.character.orEmpty(), credit.profileUrl.orEmpty())
                .joinToString(LIST) { it.replace('\t', ' ').replace('\n', ' ') }

        internal fun decodeCredit(line: String): Credit? {
            val parts = line.split(LIST)
            if (parts.size != 4 || parts[0].isBlank()) return null
            return Credit(parts[0], enumOrNull<CreditRole>(parts[1]) ?: CreditRole.ACTOR, parts[2].ifEmpty { null }, parts[3].ifEmpty { null })
        }
    }
}

/** A season's description, as a catalog source gives it. */
internal fun CatalogSeason.description(): Description? =
    Description(title = title, overview = overview, releaseDate = airDate).takeUnless { it.empty }

internal fun MediaItem.titleKey(): String = TitleText.key(title)

internal fun MediaItem.yearOrNull(): Int? = when (this) {
    is Movie -> year
    is Show -> year
    else -> null
}
