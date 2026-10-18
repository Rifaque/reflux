package dev.reflux.ui.state

import dev.reflux.core.model.Episode
import dev.reflux.core.model.MediaItem
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.Movie
import dev.reflux.core.model.Show
import dev.reflux.core.model.WatchState
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.AudioStream
import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.PlaybackIssue
import dev.reflux.core.playback.PlayerTrack
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.search.SmartQuery
import dev.reflux.core.versions.SelectionCriterion
import dev.reflux.library.LibraryEntry
import kotlin.math.roundToInt

/** A system-language name for an ISO 639 code ("en", "eng" → "English"), or null when unknown. */
expect fun languageDisplayName(code: String): String?

/** User-facing wording for Media Core facts. Pure and deterministic, so every label is testable. */
object Formatting {
    /** A playback position: `1:02:03`, or `2:03` under an hour. */
    fun clock(ms: Long): String {
        val total = (ms.coerceAtLeast(0) / 1000)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        val ss = seconds.toString().padStart(2, '0')
        return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$ss" else "$minutes:$ss"
    }

    /** A runtime: `2h 14m`, `47m`. */
    fun runtime(minutes: Int): String = when {
        minutes >= 60 && minutes % 60 == 0 -> "${minutes / 60}h"
        minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
        else -> "${minutes}m"
    }

    fun size(bytes: Long): String = when {
        bytes >= 1L shl 30 -> "${oneDecimal(bytes / (1L shl 30).toDouble())} GB"
        bytes >= 1L shl 20 -> "${(bytes / (1L shl 20).toDouble()).roundToInt()} MB"
        else -> "${(bytes / 1024.0).roundToInt().coerceAtLeast(1)} KB"
    }

    private fun oneDecimal(value: Double): String {
        val tenths = (value * 10).roundToInt()
        return if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
    }

    fun year(item: MediaItem): Int? = when (item) {
        is Movie -> item.year
        is Show -> item.year
        is Episode -> item.airDate?.year
        else -> null
    }

    /** `S2 · E5`, `S2 · E5–6`, or the air date for daily shows. */
    fun episodeNumber(episode: Episode): String {
        val number = episode.episodeNumber ?: episode.absoluteNumber
            ?: return episode.airDate?.toString() ?: "Season ${episode.seasonNumber}"
        val range = episode.episodeNumberEnd?.let { "–$it" }.orEmpty()
        return if (episode.seasonNumber == 0) "Special · E$number$range" else "S${episode.seasonNumber} · E$number$range"
    }

    /** The facts line under a title: `1995 · 2h 50m · R · ★ 8.3`. Unknown facts are simply left out. */
    fun facts(entry: LibraryEntry, runtimeMinutes: Int? = entry.metadata?.runtimeMinutes): List<String> = listOfNotNull(
        year(entry.item)?.toString(),
        runtimeMinutes?.takeIf { it > 0 }?.let(::runtime),
        entry.metadata?.contentRating?.takeIf { it.isNotBlank() },
        entry.metadata?.rating?.takeIf { it > 0 }?.let { "★ ${oneDecimal(it)}" },
    )

    /** Progress through a work, 0..1, or null when there is nothing to show. */
    fun progress(state: WatchState?): Float? {
        val duration = state?.durationMs?.takeIf { it > 0 } ?: return null
        if (state.completed || state.positionMs <= 0) return null
        return (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    }

    /** `32 min left`, for works in progress. */
    fun remaining(state: WatchState?): String? {
        val duration = state?.durationMs ?: return null
        if (!state.inProgress) return null
        val minutes = ((duration - state.positionMs) / 60_000.0).roundToInt().coerceAtLeast(1)
        return if (minutes >= 60) "${runtime(minutes)} left" else "$minutes min left"
    }

    fun resolution(nominalHeight: Int?): String? = when {
        nominalHeight == null -> null
        nominalHeight >= 4320 -> "8K"
        nominalHeight >= 2160 -> "4K"
        nominalHeight >= 1440 -> "1440p"
        nominalHeight >= 1080 -> "1080p"
        nominalHeight >= 720 -> "720p"
        else -> "SD"
    }

    fun videoCodec(codec: VideoCodec): String? = when (codec) {
        VideoCodec.H264 -> "H.264"
        VideoCodec.HEVC -> "HEVC"
        VideoCodec.AV1 -> "AV1"
        VideoCodec.VP9 -> "VP9"
        VideoCodec.VP8 -> "VP8"
        VideoCodec.MPEG2 -> "MPEG-2"
        VideoCodec.MPEG4_PART2 -> "MPEG-4"
        VideoCodec.VC1 -> "VC-1"
        VideoCodec.UNKNOWN -> null
    }

    fun dynamicRange(range: DynamicRange): String? = when (range) {
        DynamicRange.SDR -> null
        DynamicRange.HLG -> "HLG"
        DynamicRange.HDR10 -> "HDR10"
        DynamicRange.HDR10_PLUS -> "HDR10+"
        DynamicRange.DOLBY_VISION -> "Dolby Vision"
    }

    fun audioCodec(codec: AudioCodec): String? = when (codec) {
        AudioCodec.AAC -> "AAC"
        AudioCodec.MP3 -> "MP3"
        AudioCodec.AC3 -> "Dolby Digital"
        AudioCodec.EAC3 -> "Dolby Digital+"
        AudioCodec.DTS -> "DTS"
        AudioCodec.DTS_HD_MA -> "DTS-HD MA"
        AudioCodec.TRUEHD -> "TrueHD"
        AudioCodec.FLAC -> "FLAC"
        AudioCodec.ALAC -> "ALAC"
        AudioCodec.PCM -> "PCM"
        AudioCodec.OPUS -> "Opus"
        AudioCodec.VORBIS -> "Vorbis"
        AudioCodec.UNKNOWN -> null
    }

    fun channels(count: Int?): String? = when (count) {
        null -> null
        1 -> "Mono"
        2 -> "Stereo"
        6 -> "5.1"
        8 -> "7.1"
        else -> "$count ch"
    }

    fun audio(stream: AudioStream): String? =
        listOfNotNull(audioCodec(stream.codec), channels(stream.channels), "Atmos".takeIf { stream.atmos })
            .joinToString(" ").takeIf { it.isNotEmpty() }


    /** What a person tells copies apart by: `4K · Dolby Vision · Extended`. Never empty. */
    fun versionQuality(version: MediaVersion): List<String> {
        val video = version.stream.video
        return listOfNotNull(resolution(video?.nominalHeight), video?.dynamicRange?.let(::dynamicRange), version.edition)
            .ifEmpty { listOf(video?.codec?.let(::videoCodec) ?: "Unknown quality") }
    }

    /**
     * That a version is not the same cut as the recommended one, with its running time where it is known
     * (`Another cut, 58m`), so it is never taken for another copy of the same thing.
     */
    fun otherCut(version: MediaVersion): String =
        version.stream.durationMs?.takeIf { it >= 60_000 }?.let { "Another cut, ${runtime((it / 60_000).toInt())}" } ?: "Another cut"

    /** What explains a copy's quality without being the reason to pick it: `HEVC · TrueHD 7.1 Atmos`. */
    fun versionTechnical(version: MediaVersion): List<String> {
        val quality = versionQuality(version)
        return listOfNotNull(version.stream.video?.codec?.let(::videoCodec), version.stream.audio.firstOrNull()?.let(::audio)).filter { it !in quality }
    }

    /** The quality badges shown on a details page: the best the library has, e.g. `4K`, `Dolby Vision`. */
    fun qualityBadges(versions: List<MediaVersion>): List<String> {
        val videos = versions.mapNotNull { it.stream.video }
        return listOfNotNull(
            resolution(videos.mapNotNull { it.nominalHeight }.maxOrNull())?.takeIf { it == "4K" || it == "8K" },
            videos.maxOfOrNull { it.dynamicRange }?.let(::dynamicRange),
            "Atmos".takeIf { versions.any { v -> v.stream.audio.any { it.atmos } } },
        )
    }

    /** Why a version will not play at its best on this device, in plain words. */
    fun issue(issue: PlaybackIssue): String = when (issue) {
        PlaybackIssue.CONTAINER_UNSUPPORTED -> "This file format can't be played on this device"
        PlaybackIssue.VIDEO_CODEC_UNSUPPORTED -> "This video format can't be played on this device"
        PlaybackIssue.VIDEO_RESOLUTION_UNSUPPORTED -> "The resolution is too high for this device"
        PlaybackIssue.VIDEO_BIT_DEPTH_UNSUPPORTED -> "The color depth isn't supported on this device"
        PlaybackIssue.DOLBY_VISION_UNSUPPORTED -> "This Dolby Vision format can't be shown on this device"
        PlaybackIssue.NO_PLAYABLE_AUDIO -> "No audio track can be played on this device"
        PlaybackIssue.SOFTWARE_VIDEO_DECODE -> "Decoded without hardware acceleration"
        PlaybackIssue.HDR_TONE_MAPPED -> "HDR adapted to this display"
        PlaybackIssue.HDR_NOT_DISPLAYABLE -> "HDR colors may look washed out on this display"
        PlaybackIssue.DOLBY_VISION_BASE_LAYER -> "Plays as HDR10 instead of Dolby Vision"
        PlaybackIssue.AUDIO_DOWNMIXED -> "Surround sound mixed down for this output"
    }

    /** Why Best Version preferred the chosen version over the runner-up. */
    fun criterion(criterion: SelectionCriterion): String = when (criterion) {
        SelectionCriterion.USER_PREFERENCE -> "Your choice"
        SelectionCriterion.AVAILABILITY -> "The only one available right now"
        SelectionCriterion.COMPATIBILITY -> "The one this device can play"
        SelectionCriterion.FEWER_COMPROMISES -> "Plays best on this device"
        SelectionCriterion.CONTINUITY -> "The cut you are watching"
        SelectionCriterion.RESOLUTION -> "Highest resolution for this display"
        SelectionCriterion.DYNAMIC_RANGE -> "Best HDR format for this display"
        SelectionCriterion.AUDIO -> "Best audio for this device"
        SelectionCriterion.LOCALITY -> "Closest copy"
        SelectionCriterion.BITRATE -> "Highest bitrate"
        SelectionCriterion.STABLE_ORDER -> "Equivalent copies"
    }

    /** How a smart search was understood, as chips: `Movies`, `Unwatched`, `4K`, `Science Fiction`. */
    fun interpretation(query: SmartQuery): List<String> = buildList {
        query.kinds.sortedBy { it.ordinal }.forEach { add(it.name.lowercase().replaceFirstChar(Char::uppercase) + "s") }
        query.watched?.let { add(if (it) "Watched" else "Unwatched") }
        if (query.inProgress) add("In progress")
        if (query.favorite) add("Favorites")
        query.minHeight?.let { add(resolution(it) ?: "${it}p") }
        query.dynamicRange?.let { range -> dynamicRange(range)?.let(::add) }
        if (query.hdr) add("HDR")
        addAll(query.genres)
        addAll(query.people)
        query.years?.let { add(if (it.first == it.last) "${it.first}" else "${it.first}–${it.last}") }
        query.maxRuntimeMinutes?.let { add("Under ${runtime(it)}") }
        query.minRuntimeMinutes?.let { add("Over ${runtime(it)}") }
        if (query.text.isNotBlank()) add("“${query.text}”")
    }

    /** An audio or subtitle track in a picker: `English · 5.1 · Director's commentary`. */
    fun track(track: PlayerTrack): String {
        val language = track.language?.let { languageDisplayName(it) ?: it.uppercase() }
        val title = track.title?.takeIf { it.isNotBlank() && !it.equals(language, ignoreCase = true) }
        return listOfNotNull(
            language,
            channels(track.channels),
            title,
            "Forced".takeIf { track.forced },
            "SDH".takeIf { track.hearingImpaired },
        ).joinToString(" · ").ifEmpty { "Track ${track.id}" }
    }

    fun speed(speed: Double): String {
        val hundredths = (speed * 100).roundToInt()
        val whole = hundredths / 100
        val fraction = (hundredths % 100).toString().padStart(2, '0').trimEnd('0')
        return if (fraction.isEmpty()) "$whole×" else "$whole.$fraction×"
    }
}
