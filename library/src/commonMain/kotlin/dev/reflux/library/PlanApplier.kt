package dev.reflux.library

import dev.reflux.core.identify.ConfidenceLevel
import dev.reflux.core.identify.TitleText
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.Episode
import dev.reflux.core.model.MediaItem
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.Season
import dev.reflux.core.model.StreamInfoOrigin
import dev.reflux.core.model.VersionId
import dev.reflux.core.playback.StreamInfo
import dev.reflux.library.db.LibraryQueries

/** Writes scan plans and probe results into the library database. Callers own the transaction. */
internal class PlanApplier(private val queries: LibraryQueries, private val now: () -> Long) {

    /** Applies a plan inside the caller's transaction: the plan becomes the complete contents of its source. */
    fun apply(plan: ScanPlan): ScanReport {
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
