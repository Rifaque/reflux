package dev.reflux.library

import dev.reflux.core.identify.ConfidenceLevel
import dev.reflux.core.identify.TitleText
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.Episode
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaItem
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.Season
import dev.reflux.core.model.SourceId
import dev.reflux.core.model.StreamInfoOrigin
import dev.reflux.core.model.VersionId
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.watch.WatchRules
import dev.reflux.library.db.LibraryQueries

/** A copy is a version of its work by construction, not by inference. */
private const val COPY_CONFIDENCE = 1.0

/** Writes scan plans and probe results into the library database. Callers own the transaction. */
internal class PlanApplier(private val queries: LibraryQueries, private val now: () -> Long) {

    /**
     * Applies a plan inside the caller's transaction: the plan becomes the complete contents of its source,
     * except below the [unread] folders, which a listing reported it could not read.
     */
    fun apply(plan: ScanPlan, unread: List<String> = emptyList()): ScanReport {
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
                identity_key = plan.identityKeys[item.id],
                added_at = time,
            )
            queries.updateItem(
                title = item.storedTitle(),
                titleKey = item.storedTitleKey(),
                sortKey = TitleText.sortKey(item.storedTitle()),
                year = item.yearOrNull()?.toLong(),
                episodeNumberEnd = (item as? Episode)?.episodeNumberEnd?.toLong(),
                externalHints = encodePairs(plan.externalHints[item.id].orEmpty()),
                identityKey = plan.identityKeys[item.id],
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

        // What lies below a folder that could not be listed was not seen, which says nothing about it: it stays,
        // with its subtitles and artwork, until a scan can look.
        fun unseen(path: String) = unread.any { it.isEmpty() || path == it || path.startsWith("$it/") }
        val planned = plan.versions.map { it.version.location.path }.toSet()
        val removed = existing.values.filter { it.path !in planned && !unseen(it.path) }
        for (row in removed) {
            queries.deleteVersion(row.id)
            queries.deleteTracksOfVersion(row.id)
            queries.deleteExternalSubtitlesOfVersion(row.id)
        }

        val keptSubtitles = existing.values.filter { it.path !in planned && unseen(it.path) }
            .flatMap { queries.externalSubtitlesOfVersion(it.id).executeAsList() }
        queries.deleteExternalSubtitlesOfSource(sourceId)
        for (sub in keptSubtitles) {
            queries.insertExternalSubtitle(sub.version_id, sub.source_id, sub.path, sub.format, sub.language, sub.forced, sub.hearing_impaired)
        }
        for ((version, _, _) in plan.versions) {
            for (sub in version.externalSubtitles) {
                queries.insertExternalSubtitle(
                    version.id.value, sourceId, sub.location.path, sub.format.name, sub.language,
                    sub.forced.toLong(), sub.hearingImpaired.toLong(),
                )
            }
        }

        val artworkOrigin = ScanPlanner.sourceArtworkOrigin(plan.sourceId)
        val keptArtwork = if (unread.isEmpty()) emptyList() else
            queries.artworkOfSource(sourceId, artworkOrigin).executeAsList().filter { unseen(it.locator) }
        queries.deleteArtworkOfSource(sourceId, artworkOrigin)
        for (art in keptArtwork) queries.upsertArtwork(art.item_id, art.kind, art.origin, art.source_id, art.locator)
        for (art in plan.artwork) {
            val locator = art.locator as ArtworkLocator.SourceFile
            queries.upsertArtwork(art.itemId.value, art.kind.name, art.origin, sourceId, locator.location.path)
        }

        // A work whose only file was re-identified carries its watch history to the new identity. Where
        // that identity has a history of its own (two works turned out to be one), the two become one.
        for ((from, to) in movedItems.distinct()) {
            if (!queries.itemHasVersions(from).executeAsOne()) moveWatchState(from, to)
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

    /**
     * Makes [versions] the complete contents of a source whose versions are copies Reflux made of versions
     * elsewhere (downloads). Nothing is identified: each copy already says which work it belongs to, and
     * no work is created for one. Runs inside the caller's transaction.
     */
    fun applyCopies(sourceId: SourceId, versions: List<MediaVersion>) {
        val time = now()
        val existing = queries.versionsOfSource(sourceId.value).executeAsList().associateBy { it.path }
        for (version in versions) {
            if (existing[version.location.path] == null) insertVersion(version, COPY_CONFIDENCE, "", null, time)
            else updateVersion(version, COPY_CONFIDENCE, "", null, time)
        }
        val kept = versions.map { it.location.path }.toSet()
        for (row in existing.values.filter { it.path !in kept }) {
            queries.deleteVersion(row.id)
            queries.deleteTracksOfVersion(row.id)
            queries.deleteExternalSubtitlesOfVersion(row.id)
        }
        queries.deleteExternalSubtitlesOfSource(sourceId.value)
        for (version in versions) {
            for (sub in version.externalSubtitles) {
                queries.insertExternalSubtitle(
                    version.id.value, sourceId.value, sub.location.path, sub.format.name, sub.language,
                    sub.forced.toLong(), sub.hearingImpaired.toLong(),
                )
            }
        }
        queries.deleteOrphanPlayables()
        queries.deleteEmptySeasons()
        queries.deleteEmptyShows()
        queries.deleteOrphanArtwork()
        queries.deleteOrphanMetadata()
        queries.deleteOrphanCredits()
        queries.deleteOrphanAttempts()
    }

    private fun moveWatchState(from: String, to: String) {
        val moving = queries.watchStateOf(from).executeAsOneOrNull()?.toModel() ?: return
        val existing = queries.watchStateOf(to).executeAsOneOrNull()?.toModel()
        queries.save(if (existing == null) moving.copy(itemId = MediaId(to)) else WatchRules.merge(MediaId(to), existing, moving))
        queries.deleteWatchState(from)
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

    /** Stores probed stream information for a version (inside the caller's transaction). */
    fun recordProbe(versionId: VersionId, stream: StreamInfo) {
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

    private fun MediaItem.storedTitle(): String = when (this) {
        is Episode -> episodeTitle ?: ""
        else -> title
    }

    private fun MediaItem.storedTitleKey(): String = TitleText.key(storedTitle())

}
