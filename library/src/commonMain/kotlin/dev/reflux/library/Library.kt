package dev.reflux.library

import app.cash.sqldelight.coroutines.asFlow
import dev.reflux.core.identify.ConfidenceLevel
import dev.reflux.core.identify.IdentityKeys
import dev.reflux.core.identify.IdentityOverride
import dev.reflux.core.metadata.Credit
import dev.reflux.core.metadata.CreditRole
import dev.reflux.core.metadata.MetadataCandidate
import dev.reflux.core.metadata.MetadataKind
import dev.reflux.core.metadata.MetadataMatcher
import dev.reflux.core.metadata.MetadataProvider
import dev.reflux.core.metadata.MetadataQuery
import dev.reflux.core.metadata.ScoredCandidate
import dev.reflux.core.identify.MediaPathParser
import dev.reflux.core.identify.ParsedKind
import dev.reflux.core.identify.TitleText
import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.Availability
import dev.reflux.core.model.CalendarDate
import dev.reflux.core.model.Episode
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaItem
import dev.reflux.core.model.MediaKind
import dev.reflux.core.model.MediaLocation
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.Movie
import dev.reflux.core.model.Season
import dev.reflux.core.model.Show
import dev.reflux.core.model.SourceId
import dev.reflux.core.model.StableIds
import dev.reflux.core.model.VersionId
import dev.reflux.core.model.WatchState
import dev.reflux.core.model.StreamInfoOrigin
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.MediaProber
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.playback.WatchReporter
import dev.reflux.core.playback.withHints
import dev.reflux.core.search.SearchDocument
import dev.reflux.core.search.SearchMatcher
import dev.reflux.core.search.SmartQueryParser
import dev.reflux.core.source.CatalogEntry
import dev.reflux.core.source.CatalogSource
import dev.reflux.core.source.FileEnumeratingSource
import dev.reflux.core.source.SourceUserState
import dev.reflux.core.source.FileRole
import dev.reflux.core.source.ScanRules
import dev.reflux.core.source.MediaSource
import dev.reflux.core.source.SourceFile
import dev.reflux.core.source.SourceUnavailableException
import dev.reflux.core.versions.VersionCandidate
import dev.reflux.core.versions.VersionSelection
import dev.reflux.core.versions.VersionSelector
import dev.reflux.core.watch.NextUp
import dev.reflux.core.watch.WatchRules
import dev.reflux.library.db.Item
import dev.reflux.library.db.RefluxDatabase
import dev.reflux.library.db.Watch_state
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList

/**
 * The local-first library: Reflux's persistent understanding of the user's media.
 *
 * Everything here keeps working while sources are offline. Scans only ever *add to or correct* the cached
 * library for sources that are reachable; an unreachable or suspiciously empty source never erases it.
 *
 * @param now wall-clock time in epoch milliseconds.
 */
class Library(
    internal val database: RefluxDatabase,
    internal val now: () -> Long,
) : WatchReporter {
    internal val queries = database.libraryQueries
    private val metadataSync = MetadataSync(database, now)
    private val smartSearch = SmartSearch(database)
    private val parser get() = MediaPathParser(maxYear = yearOf(now()) + 1)

    // Sources ------------------------------------------------------------------------------------

    /** Registers a source (idempotent). [config] is what the adapter needs to be recreated later. */
    fun addSource(source: MediaSource, config: String) {
        val d = source.descriptor
        queries.insertSource(d.id.value, d.type, d.displayName, d.locality.name, config, Availability.UNKNOWN.name, now())
    }

    fun sources(): List<SourceRecord> = queries.allSources().executeAsList().map { it.toModel() }

    fun source(id: SourceId): SourceRecord? = queries.sourceById(id.value).executeAsOneOrNull()?.toModel()

    /** Removes a source and everything Reflux derived from it. Watch history of its works is kept. */
    fun removeSource(id: SourceId) = database.transaction {
        applyPlan(ScanPlan(id, emptyList(), emptyList(), emptyList(), emptyMap()))
        queries.deleteSource(id.value)
    }

    /** Records a reachability check, e.g. on start-up or when a drive is plugged in. */
    suspend fun refreshAvailability(source: MediaSource): Availability {
        val availability = source.availability()
        queries.setSourceAvailability(availability.name, now(), source.descriptor.id.value)
        return availability
    }

    // Scanning -----------------------------------------------------------------------------------

    /**
     * Scans a source and updates the library: file-enumerating sources are parsed and identified by Reflux,
     * catalog sources contribute their own identification. Safe to call repeatedly.
     */
    suspend fun scan(source: MediaSource): ScanReport {
        val id = source.descriptor.id
        check(queries.sourceById(id.value).executeAsOneOrNull() != null) { "source $id is not registered" }
        if (source !is FileEnumeratingSource && source !is CatalogSource) return ScanReport(id, ScanReport.Status.COMPLETED)
        if (refreshAvailability(source) != Availability.AVAILABLE) return ScanReport(id, ScanReport.Status.UNAVAILABLE)
        return try {
            when (source) {
                is CatalogSource -> scanCatalog(id, source.catalog().toList())
                is FileEnumeratingSource -> scanFiles(id, source.files().toList())
                else -> error("unreachable")
            }
        } catch (_: SourceUnavailableException) {
            queries.setSourceAvailability(Availability.UNAVAILABLE.name, now(), id.value)
            ScanReport(id, ScanReport.Status.UNAVAILABLE)
        }
    }

    private fun scanFiles(id: SourceId, files: List<SourceFile>): ScanReport {
        val hasVideo = files.any { ScanRules.roleOf(it.path.substringAfterLast('/')) == FileRole.VIDEO }
        if (!hasVideo && hasKnownVersions(id)) return keepEmpty(id)
        return database.transactionWithResult {
            val plan = ScanPlanner(parser).plan(id, files, overrides(id), yearHints(id))
            val report = applyPlan(plan)
            queries.markSourceScanned(now(), id.value)
            report
        }
    }

    private fun scanCatalog(id: SourceId, entries: List<CatalogEntry>): ScanReport {
        if (entries.isEmpty() && hasKnownVersions(id)) return keepEmpty(id)
        return database.transactionWithResult {
            val plan = ScanPlanner(parser).planCatalog(id, entries, overrides(id), yearHints(id))
            val report = applyPlan(plan)
            val itemByPath = plan.versions.associate { it.version.location.path to it.version.itemId }
            for (entry in entries) {
                val state = entry.userState ?: continue
                val itemId = entry.versions.firstNotNullOfOrNull { itemByPath[it.path] } ?: continue
                importSourceState(itemId, state)
            }
            queries.markSourceScanned(now(), id.value)
            report
        }
    }

    private fun hasKnownVersions(id: SourceId): Boolean = queries.versionsOfSource(id.value).executeAsList().isNotEmpty()

    /** An empty listing from a source Reflux knows media on is treated as a disconnection, never a deletion. */
    private fun keepEmpty(id: SourceId): ScanReport {
        queries.setSourceAvailability(Availability.UNAVAILABLE.name, now(), id.value)
        return ScanReport(id, ScanReport.Status.EMPTY_KEPT)
    }

    /**
     * Adopts a source's watch state when it is newer than Reflux's, so watching on the server's own clients is
     * reflected here. Server favorites are added; local favorites are never removed by a server.
     */
    private fun importSourceState(itemId: MediaId, state: SourceUserState) {
        val local = watchState(itemId)
        val sourcePlayed = state.lastPlayedAtEpochMs
        val localPlayed = local?.lastPlayedAtEpochMs
        val sourceIsNewer = sourcePlayed != null && (localPlayed == null || sourcePlayed > localPlayed)
        val playedWithoutDate = state.played && sourcePlayed == null && local == null
        if (sourceIsNewer || playedWithoutDate) {
            saveWatchState(
                WatchState(
                    itemId = itemId,
                    positionMs = if (state.played) 0 else state.positionMs,
                    durationMs = local?.durationMs,
                    completed = state.played,
                    playCount = maxOf(local?.playCount ?: 0, state.playCount, if (state.played) 1 else 0),
                    lastPlayedAtEpochMs = sourcePlayed ?: localPlayed,
                ),
            )
        }
        if (state.favorite) queries.insertFavorite(itemId.value, now())
    }

    /**
     * Re-identifies a source from the file listing Reflux already knows, without touching the source.
     * Used after identity corrections; works while the source is offline.
     */
    fun reidentify(sourceId: SourceId): ScanReport {
        val versions = queries.versionsOfSource(sourceId.value).executeAsList()
        if (versions.any { it.source_identity != null }) {
            val entries = versions.mapNotNull { row ->
                val stream = version(VersionId(row.id))?.version ?: return@mapNotNull null
                ScanPlanner.decodeCatalogEntry(row.source_identity ?: return@mapNotNull null, row.path, row.size_bytes, row.modified_at, stream)
            }
            return database.transactionWithResult {
                applyPlan(ScanPlanner(parser).planCatalog(sourceId, entries, overrides(sourceId), yearHints(sourceId)))
            }
        }
        val files = versions.map { SourceFile(it.path, it.size_bytes, it.modified_at) } +
            queries.externalSubtitlePathsOfSource(sourceId.value).executeAsList().map { SourceFile(it, 0, 0) } +
            queries.artworkPathsOfSource(sourceId.value).executeAsList().map { SourceFile(it, 0, 0) }
        val report = database.transactionWithResult {
            val plan = ScanPlanner(parser).plan(sourceId, files.distinctBy { it.path }, overrides(sourceId), yearHints(sourceId))
            applyPlan(plan)
        }
        return report
    }

    private fun overrides(sourceId: SourceId): Map<String, IdentityOverride> =
        queries.overridesOfSource(sourceId.value).executeAsList().mapNotNull { row ->
            val kind = enumOrNull<ParsedKind>(row.kind) ?: return@mapNotNull null
            row.path to runCatching {
                IdentityOverride(kind, row.title, row.year?.toInt(), row.season?.toInt(), row.episode?.toInt())
            }.getOrElse { return@mapNotNull null }
        }.toMap()

    private fun yearHints(sourceId: SourceId): Map<Pair<ParsedKind, String>, Set<Int>> =
        queries.yearHintsExcludingSource(sourceId.value).executeAsList()
            .groupBy(
                { (if (it.kind == MediaKind.SHOW.name) ParsedKind.EPISODE else ParsedKind.MOVIE) to it.title_key },
                { it.year!!.toInt() },
            )
            .mapValues { it.value.toSet() }

    /** Applies a plan inside the caller's transaction: the plan becomes the complete contents of its source. */
    private fun applyPlan(plan: ScanPlan): ScanReport {
        val time = now()
        val sourceId = plan.sourceId.value
        val existing = queries.versionsOfSource(sourceId).executeAsList().associateBy { it.path }

        for (item in plan.items) {
            queries.insertItemIfAbsent(
                id = item.id.value,
                kind = item.kind.name,
                title = item.storedTitle(),
                title_key = item.storedTitleKey(),
                sort_key = TitleText.sortKey(item.storedTitle()),
                year = item.yearOrNull()?.toLong(),
                show_id = (item as? Season)?.showId?.value ?: (item as? Episode)?.showId?.value,
                season_id = (item as? Episode)?.seasonId?.value,
                season_number = ((item as? Season)?.number ?: (item as? Episode)?.seasonNumber)?.toLong(),
                episode_number = (item as? Episode)?.episodeNumber?.toLong(),
                episode_number_end = (item as? Episode)?.episodeNumberEnd?.toLong(),
                absolute_number = (item as? Episode)?.absoluteNumber?.toLong(),
                air_date = (item as? Episode)?.airDate?.toString(),
                external_hints = encodePairs(plan.externalHints[item.id].orEmpty()),
                added_at = time,
            )
            queries.updateItem(
                title = item.storedTitle(),
                titleKey = item.storedTitleKey(),
                sortKey = TitleText.sortKey(item.storedTitle()),
                year = item.yearOrNull()?.toLong(),
                episodeNumberEnd = (item as? Episode)?.episodeNumberEnd?.toLong(),
                externalHints = encodePairs(plan.externalHints[item.id].orEmpty()),
                id = item.id.value,
            )
        }

        var added = 0
        var updated = 0
        var unchanged = 0
        val movedItems = mutableListOf<Pair<String, String>>()
        for ((version, parsed, sourceIdentity) in plan.versions) {
            val previous = existing[version.location.path]
            val signals = parsed.confidence.signals.joinToString(",") { it.name }
            val fileChanged = previous == null ||
                previous.size_bytes != version.sizeBytes || previous.modified_at != version.modifiedAtEpochMs
            when {
                previous == null -> {
                    insertVersion(version, parsed.confidence.score, signals, sourceIdentity, time)
                    added++
                }
                !fileChanged && previous.stream_origin != version.streamOrigin.name -> {
                    // Keep probed stream data for unchanged files; only identity may change.
                    queries.updateVersionIdentity(
                        version.itemId.value, version.edition, version.part?.toLong(), parsed.confidence.score, signals,
                        time, version.id.value,
                    )
                    if (previous.item_id != version.itemId.value) updated++ else unchanged++
                }
                else -> {
                    updateVersion(version, parsed.confidence.score, signals, sourceIdentity, time)
                    if (fileChanged || previous.item_id != version.itemId.value) updated++ else unchanged++
                }
            }
            if (previous != null && previous.item_id != version.itemId.value) {
                movedItems += previous.item_id to version.itemId.value
            }
        }

        val planned = plan.versions.map { it.version.location.path }.toSet()
        val removed = existing.values.filter { it.path !in planned }
        for (row in removed) {
            queries.deleteVersion(row.id)
            queries.deleteTracksOfVersion(row.id)
            queries.deleteExternalSubtitlesOfVersion(row.id)
        }

        queries.deleteExternalSubtitlesOfSource(sourceId)
        for ((version, _, _) in plan.versions) {
            for (sub in version.externalSubtitles) {
                queries.insertExternalSubtitle(
                    version.id.value, sourceId, sub.location.path, sub.format.name, sub.language,
                    sub.forced.toLong(), sub.hearingImpaired.toLong(),
                )
            }
        }

        queries.deleteArtworkOfSource(sourceId, ScanPlanner.sourceArtworkOrigin(plan.sourceId))
        for (art in plan.artwork) {
            val locator = art.locator as ArtworkLocator.SourceFile
            queries.upsertArtwork(art.itemId.value, art.kind.name, art.origin, sourceId, locator.location.path)
        }

        // A work whose only file was re-identified carries its watch history to the new identity.
        for ((from, to) in movedItems.distinct()) {
            if (!queries.itemHasVersions(from).executeAsOne()) queries.moveWatchState(to, from)
        }
        queries.deleteOrphanPlayables()
        queries.deleteEmptySeasons()
        queries.deleteEmptyShows()
        queries.deleteOrphanArtwork()
        queries.deleteOrphanMetadata()
        queries.deleteOrphanCredits()
        queries.deleteOrphanAttempts()

        return ScanReport(
            sourceId = plan.sourceId,
            status = ScanReport.Status.COMPLETED,
            added = added,
            updated = updated,
            removed = removed.size,
            unchanged = unchanged,
            skipped = plan.skipped,
            lowConfidence = plan.versions.count { it.parsed.confidence.level == ConfidenceLevel.LOW },
        )
    }

    private fun insertVersion(version: MediaVersion, confidence: Double, signals: String, sourceIdentity: String?, time: Long) {
        val video = version.stream.video
        queries.insertVersionIfAbsent(
            id = version.id.value,
            item_id = version.itemId.value,
            source_id = version.location.sourceId.value,
            path = version.location.path,
            size_bytes = version.sizeBytes,
            modified_at = version.modifiedAtEpochMs,
            stream_origin = version.streamOrigin.name,
            container = version.stream.container.name,
            video_codec = video?.codec?.name,
            width = video?.width?.toLong(),
            height = video?.height?.toLong(),
            bit_depth = video?.bitDepth?.toLong(),
            dynamic_range = video?.dynamicRange?.name,
            dolby_vision_profile = video?.dolbyVisionProfile?.toLong(),
            frame_rate = video?.frameRate,
            duration_ms = version.stream.durationMs,
            bitrate_bps = version.stream.bitrateBps,
            edition = version.edition,
            part = version.part?.toLong(),
            confidence = confidence,
            signals = signals,
            source_identity = sourceIdentity,
            first_seen_at = time,
            last_seen_at = time,
        )
        replaceTracks(version)
    }

    private fun updateVersion(version: MediaVersion, confidence: Double, signals: String, sourceIdentity: String?, time: Long) {
        val video = version.stream.video
        queries.updateVersion(
            itemId = version.itemId.value,
            sizeBytes = version.sizeBytes,
            modifiedAt = version.modifiedAtEpochMs,
            streamOrigin = version.streamOrigin.name,
            container = version.stream.container.name,
            videoCodec = video?.codec?.name,
            width = video?.width?.toLong(),
            height = video?.height?.toLong(),
            bitDepth = video?.bitDepth?.toLong(),
            dynamicRange = video?.dynamicRange?.name,
            dolbyVisionProfile = video?.dolbyVisionProfile?.toLong(),
            frameRate = video?.frameRate,
            durationMs = version.stream.durationMs,
            bitrateBps = version.stream.bitrateBps,
            edition = version.edition,
            part = version.part?.toLong(),
            confidence = confidence,
            signals = signals,
            sourceIdentity = sourceIdentity,
            lastSeenAt = time,
            id = version.id.value,
        )
        replaceTracks(version)
    }

    private fun replaceTracks(version: MediaVersion) = replaceTracks(version.id.value, version.stream)

    private fun replaceTracks(id: String, stream: StreamInfo) {
        queries.deleteTracksOfVersion(id)
        stream.audio.forEachIndexed { index, track ->
            queries.insertTrack(
                id, index.toLong(), TRACK_AUDIO, track.codec.name, track.channels?.toLong(), track.language,
                track.atmos.toLong(), 0, track.default.toLong(),
            )
        }
        stream.subtitles.forEachIndexed { index, track ->
            queries.insertTrack(
                id, index.toLong(), TRACK_SUBTITLE, track.format.name, null, track.language, 0,
                track.forced.toLong(), track.default.toLong(),
            )
        }
    }

    // Probing ------------------------------------------------------------------------------------

    /**
     * Probes versions that are still described by file-name hints, newest first, on available sources.
     * Returns how many were probed successfully. Failures are remembered until the file changes.
     */
    suspend fun probePending(sources: (SourceId) -> MediaSource?, prober: MediaProber, limit: Int = 50): Int {
        var probed = 0
        for (id in queries.versionsAwaitingProbe(limit.toLong()).executeAsList()) {
            val info = version(VersionId(id)) ?: continue
            val source = sources(info.version.location.sourceId) ?: continue
            val stream = runCatching { prober.probe(source.playbackTarget(info.version.location.path)) }.getOrNull()
            if (stream == null) {
                queries.markProbeAttempted(now(), id)
            } else {
                recordProbe(info.version.id, stream.withHints(info.version.stream))
                probed++
            }
        }
        return probed
    }

    /** Stores probed stream information for a version. */
    fun recordProbe(versionId: VersionId, stream: StreamInfo) = database.transaction {
        val video = stream.video
        queries.updateVersionStream(
            streamOrigin = StreamInfoOrigin.PROBE.name,
            container = stream.container.name,
            videoCodec = video?.codec?.name,
            width = video?.width?.toLong(),
            height = video?.height?.toLong(),
            bitDepth = video?.bitDepth?.toLong(),
            dynamicRange = video?.dynamicRange?.name,
            dolbyVisionProfile = video?.dolbyVisionProfile?.toLong(),
            frameRate = video?.frameRate,
            durationMs = stream.durationMs,
            bitrateBps = stream.bitrateBps,
            probedAt = now(),
            id = versionId.value,
        )
        replaceTracks(versionId.value, stream)
    }

    // Metadata -----------------------------------------------------------------------------------

    /**
     * Looks up metadata and artwork for works that have none yet, newest first. Unmatched works are retried
     * after [retryAfterMs]. Safe to call while offline: it stops early and reports [MetadataReport.offline].
     */
    suspend fun refreshMetadata(
        provider: MetadataProvider,
        language: String,
        limit: Int = 100,
        retryAfterMs: Long = 7 * 24 * 3_600_000L,
    ): MetadataReport = metadataSync.refresh(provider, language, limit, retryAfterMs)

    fun metadata(itemId: MediaId): ItemMetadata? = queries.metadataOf(itemId.value).executeAsOneOrNull()?.toModel()

    fun credits(itemId: MediaId): List<Credit> = queries.creditsOf(itemId.value).executeAsList().map {
        Credit(it.name, enumOrNull<CreditRole>(it.role) ?: CreditRole.ACTOR, it.character, it.profile_url)
    }

    /** Candidates for the Identify flow: the work's own title and year, or what the user typed. */
    suspend fun searchMetadata(
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
    suspend fun identifyAs(itemId: MediaId, candidate: MetadataCandidate, provider: MetadataProvider, language: String): MediaId {
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

    // Identity corrections -----------------------------------------------------------------------

    /** "Identify this movie/show": remembers what a file is and re-identifies its source immediately. */
    fun identify(location: MediaLocation, override: IdentityOverride) {
        queries.upsertOverride(
            location.sourceId.value, location.path, override.kind.name, override.title, override.year?.toLong(),
            override.season?.toLong(), override.episode?.toLong(), now(),
        )
        reidentify(location.sourceId)
    }

    fun clearIdentification(location: MediaLocation) {
        queries.deleteOverride(location.sourceId.value, location.path)
        reidentify(location.sourceId)
    }

    // Reading ------------------------------------------------------------------------------------

    fun movies(): List<LibraryEntry> = entries(queries.presentMovies().executeAsList().map { it.toModel() })

    fun shows(): List<LibraryEntry> = entries(queries.presentShows().executeAsList().map { it.toModel() })

    fun recentlyAdded(limit: Int = 20): List<LibraryEntry> =
        entries(queries.recentlyAdded(limit.toLong()).executeAsList().map { row ->
            Item(
                row.id, row.kind, row.title, row.title_key, row.sort_key, row.year, row.show_id, row.season_id,
                row.season_number, row.episode_number, row.episode_number_end, row.absolute_number, row.air_date,
                row.external_hints, row.added_at,
            ).toModel()
        })

    /** A work with its display title (provider metadata wins over the parsed title). */
    fun item(id: MediaId): MediaItem? = rawItem(id)?.let { it.withMetadata(metadata(id)) }

    private fun rawItem(id: MediaId): MediaItem? = queries.itemById(id.value).executeAsOneOrNull()?.toModel()

    fun entry(id: MediaId): LibraryEntry? = item(id)?.let { entries(listOf(it)).single() }

    fun showDetail(showId: MediaId): ShowDetail? {
        val show = item(showId) as? Show ?: return null
        val seasons = queries.presentSeasonsOfShow(showId.value).executeAsList().map { it.toModel() as Season }
        val seasonDetails = seasons.sortedBy { if (it.number == 0) Int.MAX_VALUE else it.number }.map { season ->
            val episodes = queries.presentEpisodesOfSeason(season.id.value).executeAsList().map { it.toModel() as Episode }
                .sortedWith(NextUp.episodeOrder)
            SeasonDetail(season, entries(listOf(season)).single(), entries(episodes))
        }
        val allEpisodes = seasonDetails.flatMap { detail -> detail.episodes.map { it.item as Episode } }
        return ShowDetail(entries(listOf(show)).single(), seasonDetails, NextUp.of(allEpisodes, watchStates()))
    }

    /** All versions of a playable work with source state, in stable order. */
    fun versions(itemId: MediaId): List<VersionInfo> =
        queries.versionsOfItem(itemId.value, versionInfoOf(::tracks, ::subtitles)).executeAsList()

    fun version(id: VersionId): VersionInfo? =
        queries.versionById(id.value, versionInfoOf(::tracks, ::subtitles)).executeAsOneOrNull()

    /** Best Version for this device, honoring the user's pinned version. */
    fun selectVersion(itemId: MediaId, device: DeviceCapabilities): VersionSelection {
        val candidates = versions(itemId).map { VersionCandidate(it.version, it.availability, it.locality) }
        val preferred = queries.preferredVersion(itemId.value).executeAsOneOrNull()?.let(::VersionId)
        return VersionSelector.select(candidates, device, preferred)
    }

    fun setPreferredVersion(itemId: MediaId, versionId: VersionId?) {
        if (versionId == null) queries.clearPreferredVersion(itemId.value)
        else queries.setPreferredVersion(itemId.value, versionId.value)
    }

    /**
     * Continue Watching: unfinished works and the next episode of started shows, most recent first,
     * one entry per show.
     */
    fun continueWatching(limit: Int = 20): List<LibraryEntry> {
        val states = watchStates()
        val result = linkedMapOf<MediaId, MediaItem>() // keyed by movie or show
        val recent = queries.recentlyPlayed(500, ::Watch_state).executeAsList().map { it.toModel() }
        val items = itemsByIds(recent.map { it.itemId.value }).associateBy { it.id }
        for (state in recent) {
            when (val item = items[state.itemId]) {
                is Movie -> if (WatchRules.resumePosition(state) != null && hasVersions(item.id)) result.getOrPut(item.id) { item }
                is Episode -> if (item.showId !in result) {
                    val episodes = queries.episodesOfShow(item.showId.value).executeAsList().map { it.toModel() as Episode }
                    NextUp.of(episodes, states)?.let { next ->
                        if (hasVersions(next.id)) result[item.showId] = next
                    }
                }
                else -> Unit
            }
            if (result.size >= limit) break
        }
        return entries(result.values.toList())
    }

    /** Deterministic title search across movies, shows, and episodes. */
    fun search(query: String, limit: Int = 50): List<LibraryEntry> {
        val movies = queries.presentMovies().executeAsList()
        val shows = queries.presentShows().executeAsList()
        val episodes = queries.presentPlayables().executeAsList().filter { it.kind == MediaKind.EPISODE.name }
        val metadata = metadataOf((movies + shows + episodes).map { it.id })
        val showTitles = shows.associate { it.id to (metadata[it.id]?.title ?: it.title) }
        fun document(row: Item, kind: MediaKind, aliases: List<String?> = emptyList()): SearchDocument? {
            val meta = metadata[row.id]
            val title = meta?.title ?: row.title.ifEmpty { return null }
            val year = meta?.releaseDate?.year ?: row.year?.toInt()
            return SearchDocument(MediaId(row.id), kind, title, year, (aliases + row.title + meta?.originalTitle).filterNotNull().filter { it.isNotEmpty() && it != title }.distinct())
        }
        val documents = movies.mapNotNull { document(it, MediaKind.MOVIE) } +
            shows.mapNotNull { document(it, MediaKind.SHOW) } +
            episodes.mapNotNull { document(it, MediaKind.EPISODE, listOf(showTitles[it.show_id])) }
        val hits = SearchMatcher.search(query, documents, limit)
        val byId = entries(itemsByIds(hits.map { it.document.id.value })).associateBy { it.item.id }
        return hits.mapNotNull { byId[it.document.id] }
    }

    /**
     * Deterministic smart search: understands kinds, watch state, quality, genres, people, years, and
     * runtimes ("4k movies I haven't watched", "Nolan movies", "90s comedies under 2 hours"). Queries with
     * nothing structured fall back to title search. The interpretation is returned for display.
     */
    fun smartSearch(query: String, limit: Int = 100): SmartSearchResult {
        val parsed = SmartQueryParser.parse(query, smartSearch.vocabulary(), yearOf(now()))
        if (!parsed.structured) return SmartSearchResult(parsed, search(query, limit))
        val kinds = parsed.kinds.ifEmpty { setOf(MediaKind.MOVIE, MediaKind.SHOW) }
        val candidates = buildList {
            if (MediaKind.MOVIE in kinds) addAll(movies())
            if (MediaKind.SHOW in kinds) addAll(shows())
            if (MediaKind.EPISODE in kinds) {
                addAll(entries(queries.presentPlayables().executeAsList().filter { it.kind == MediaKind.EPISODE.name }.map { it.toModel() }))
            }
        }
        return SmartSearchResult(parsed, smartSearch.evaluate(parsed, candidates, limit))
    }

    /** Versions whose identification is weak, for diagnostics and the Identify flow. */
    fun lowConfidence(): List<VersionInfo> =
        queries.lowConfidenceVersions(0.55).executeAsList().mapNotNull { version(VersionId(it.id)) }

    /** Emits whenever library contents change, so UIs can refresh. */
    fun changes(): Flow<Unit> = queries.presentPlayables().asFlow().map { }

    // Watch state and favorites --------------------------------------------------------------------

    fun watchState(itemId: MediaId): WatchState? = queries.watchStateOf(itemId.value).executeAsOneOrNull()?.toModel()

    /** Periodic playback progress (see [WatchRules.onProgress]). */
    override fun progress(itemId: MediaId, positionMs: Long, durationMs: Long?) =
        saveWatchState(WatchRules.onProgress(watchState(itemId), itemId, positionMs, durationMs, now()))

    /** End of a playback session (see [WatchRules.onStop]). */
    override fun stopped(itemId: MediaId, positionMs: Long, durationMs: Long?) =
        saveWatchState(WatchRules.onStop(watchState(itemId), itemId, positionMs, durationMs, now()))

    /** Marks a work as watched or unwatched. Shows and seasons apply to all their episodes. */
    fun setWatched(itemId: MediaId, watched: Boolean) = database.transaction {
        for (id in playablesOf(itemId)) {
            val previous = watchState(id)
            saveWatchState(if (watched) WatchRules.markWatched(previous, id, now()) else WatchRules.markUnwatched(previous, id))
        }
    }

    fun setFavorite(itemId: MediaId, favorite: Boolean) {
        if (favorite) queries.insertFavorite(itemId.value, now()) else queries.deleteFavorite(itemId.value)
    }

    /** Favorites, most recently added first. */
    fun favorites(): List<LibraryEntry> {
        val order = queries.allFavorites().executeAsList().map { it.item_id }
        val items = itemsByIds(order).associateBy { it.id.value }
        return entries(order.mapNotNull { items[it] })
    }

    private fun saveWatchState(state: WatchState) {
        queries.upsertWatchState(
            state.itemId.value, state.positionMs, state.durationMs, state.completed.toLong(), state.playCount.toLong(),
            state.lastPlayedAtEpochMs,
        )
    }

    private fun playablesOf(itemId: MediaId): List<MediaId> = when (val item = item(itemId)) {
        is Show -> queries.episodesOfShow(item.id.value).executeAsList().map { MediaId(it.id) }
        is Season -> queries.episodesOfShow(item.showId.value).executeAsList()
            .filter { it.season_id == item.id.value }.map { MediaId(it.id) }
        null -> emptyList()
        else -> listOf(itemId)
    }

    // Helpers ------------------------------------------------------------------------------------

    private fun watchStates(): Map<MediaId, WatchState> =
        queries.allWatchStates().executeAsList().associate { MediaId(it.item_id) to it.toModel() }

    private fun tracks(versionId: String) = queries.tracksOfVersion(versionId).executeAsList()

    private fun subtitles(versionId: String) = queries.externalSubtitlesOfVersion(versionId).executeAsList()

    private fun metadataOf(ids: List<String>): Map<String, ItemMetadata> =
        ids.chunked(QUERY_CHUNK).flatMap { queries.metadataOfItems(it).executeAsList() }.associate { it.item_id to it.toModel() }

    internal fun itemsByIds(ids: List<String>): List<MediaItem> =
        ids.chunked(QUERY_CHUNK).flatMap { queries.itemsByIds(it).executeAsList() }.map { it.toModel() }

    private fun hasVersions(id: MediaId): Boolean = queries.itemHasVersions(id.value).executeAsOne()

    internal fun entries(items: List<MediaItem>): List<LibraryEntry> {
        if (items.isEmpty()) return emptyList()
        val ids = items.map { it.id.value }
        val availability = itemAvailability()
        val favorites = queries.allFavorites().executeAsList().map { it.item_id }.toSet()
        val states = ids.chunked(QUERY_CHUNK).flatMap { queries.watchStatesOfItems(it).executeAsList() }
            .associate { it.item_id to it.toModel() }
        val metadata = metadataOf(ids)
        val artwork = ids.chunked(QUERY_CHUNK).flatMap { queries.artworkOfItems(it).executeAsList() }
            .sortedBy { if (it.origin.startsWith(ScanPlanner.SOURCE_ARTWORK_PREFIX)) 0 else 1 } // the user's own artwork wins
            .groupBy { it.item_id }
            .mapValues { (_, rows) ->
                rows.distinctBy { it.kind }.mapNotNull { row ->
                    val kind = enumOrNull<ArtworkKind>(row.kind) ?: return@mapNotNull null
                    val locator = if (row.source_id != null) {
                        ArtworkLocator.SourceFile(MediaLocation(SourceId(row.source_id), row.locator))
                    } else {
                        ArtworkLocator.Remote(row.locator)
                    }
                    kind to locator
                }.toMap()
            }
        return items.map { item ->
            val itemMetadata = metadata[item.id.value]
            LibraryEntry(
                item = item.withMetadata(itemMetadata),
                artwork = artwork[item.id.value].orEmpty(),
                watchState = states[item.id.value],
                availability = availability(item),
                favorite = item.id.value in favorites,
                metadata = itemMetadata,
            )
        }
    }

    /** Availability per item: available if any version is, unknown if any might be, otherwise unavailable. */
    private fun itemAvailability(): (MediaItem) -> Availability {
        val rows = queries.itemAvailability().executeAsList()
        val byItem = rows.groupBy({ it.item_id }, { enumOrNull<Availability>(it.availability) ?: Availability.UNKNOWN })
        val episodesByParent = queries.presentPlayables().executeAsList()
            .filter { it.kind == MediaKind.EPISODE.name }
            .flatMap { listOf(it.show_id!! to it.id, it.season_id!! to it.id) }
            .groupBy({ it.first }, { it.second })
        fun combine(values: List<Availability>): Availability = when {
            Availability.AVAILABLE in values -> Availability.AVAILABLE
            Availability.UNKNOWN in values -> Availability.UNKNOWN
            else -> Availability.UNAVAILABLE
        }
        return { item ->
            when (item) {
                is Show, is Season -> combine(episodesByParent[item.id.value].orEmpty().flatMap { byItem[it].orEmpty() })
                else -> combine(byItem[item.id.value].orEmpty())
            }
        }
    }

    private fun MediaItem.storedTitle(): String = when (this) {
        is Episode -> episodeTitle ?: ""
        else -> title
    }

    private fun MediaItem.storedTitleKey(): String = TitleText.key(storedTitle())

    companion object {
        /** Stays below SQLite's historical limit of 999 bound parameters. */
        private const val QUERY_CHUNK = 500

        /** The calendar year of an epoch-millisecond timestamp (UTC). */
        internal fun yearOf(epochMs: Long): Int = dateOf(epochMs).year

        /** The UTC calendar date of an epoch-millisecond timestamp (proleptic Gregorian, civil-from-days). */
        internal fun dateOf(epochMs: Long): CalendarDate {
            val days: Long = epochMs.floorDiv(86_400_000L) + 719_468L
            val era: Long = days.floorDiv(146_097L)
            val dayOfEra: Long = days - era * 146_097L
            val yearOfEra: Long = (dayOfEra - dayOfEra / 1_460L + dayOfEra / 36_524L - dayOfEra / 146_096L) / 365L
            val dayOfYear: Long = dayOfEra - (365L * yearOfEra + yearOfEra / 4L - yearOfEra / 100L)
            val mp: Long = (5L * dayOfYear + 2L) / 153L
            val day = (dayOfYear - (153L * mp + 2L) / 5L + 1L).toInt()
            val month = if (mp < 10L) (mp + 3L).toInt() else (mp - 9L).toInt()
            val year = (yearOfEra + era * 400L).toInt() + if (month <= 2) 1 else 0
            return CalendarDate(year, month, day)
        }
    }
}
