package dev.reflux.core.playback

import dev.reflux.core.model.MediaVersion
import dev.reflux.core.source.SourceCapability

/**
 * A library read as a compatibility matrix: its distinct kinds of media, each with what [PlaybackPlanner] answers for
 * one device. It decides nothing and is not read by playback. It is how evidence is gathered (docs/PLAYBACK.md,
 * section 15), so a real walk plays each kind once instead of a thousand files or a chosen few.
 */
object PlaybackSurvey {
    /** A kind of media with the same plan, how many versions have it, and one of them to play. */
    data class Case(
        /** What it is, such as `MATROSKA HEVC/10 + EAC3 6ch atmos, PGS`. Level is left out: only its plan tells cases apart. */
        val media: String,
        val plan: PlannedPlayback,
        val subtitle: SubtitleChoice,
        val subtitleFormat: SubtitleFormat?,
        val versions: Int,
        val example: MediaVersion,
    ) {
        fun summary(): String = "$versions × $media: ${plan.outcome} ${plan.delivery}, video ${plan.video}, audio ${plan.audio}, " +
            "subtitles ${plan.subtitles}, range ${plan.range}, reasons ${plan.reasons.sortedBy { it.name }}, unverified ${plan.unverified.sortedBy { it.name }}"
    }

    /**
     * Every version with subtitles off, and again with the first track of each subtitle format chosen. The largest
     * kinds come first, and the order is the same on every run.
     */
    fun of(versions: List<MediaVersion>, preferences: TrackPreferences, source: Set<SourceCapability>, device: DeviceCapabilities): List<Case> {
        val found = LinkedHashMap<Pair<String, PlannedPlayback>, Pair<Case, Int>>()
        fun add(version: MediaVersion, subtitle: SubtitleChoice, format: SubtitleFormat?) {
            val plan = PlaybackPlanner.plan(PlaybackRequirements(version, preferences, source, subtitle = subtitle), device)
            val media = describe(version.stream, plan.audioIndex, format)
            // Which track is a fact of one file, and says nothing of the kind of media.
            val key = media to plan.copy(audioIndex = null, subtitleIndex = null)
            val (case, count) = found[key] ?: (Case(media, plan, subtitle, format, 0, version) to 0)
            found[key] = case to count + 1
        }
        for (version in versions.sortedBy { it.id.value }) {
            add(version, SubtitleChoice.Off, null)
            version.stream.subtitles.map { it.format }.distinct().forEach { format ->
                add(version, SubtitleChoice.Explicit(version.stream.subtitles.indexOfFirst { it.format == format }), format)
            }
        }
        return found.values.map { (case, count) -> case.copy(versions = count) }.sortedWith(compareBy({ -it.versions }, { it.media }, { it.plan.delivery.name }))
    }

    /**
     * What is worth playing: every case with subtitles off, and each subtitle format once for each way it is carried.
     * A subtitle does not change with the codec under it, and every further case costs the server a real conversion.
     */
    fun walk(cases: List<Case>): List<Case> =
        cases.filter { it.subtitleFormat == null } +
            cases.filter { it.subtitleFormat != null }.distinctBy { Triple(it.subtitleFormat, it.plan.delivery, it.plan.subtitles) }

    /**
     * One version for each video size, frame rate, profile, and level the library has. A plan only tells these apart
     * when a decoder declares a limit, and an engine may not keep to what it declares.
     */
    fun edges(versions: List<MediaVersion>): List<Pair<String, MediaVersion>> =
        versions.sortedBy { it.id.value }.mapNotNull { version ->
            version.stream.video?.let { video ->
                val height = video.height?.let { if (it > 730) "1080" else if (it > 500) "720" else "SD" } ?: "?"
                "${video.codec}/${video.bitDepth ?: "?"} ${video.profile ?: "?"} L${video.level ?: "?"} ${height}p${video.frameRate?.let { kotlin.math.round(it).toInt() } ?: "?"}" to version
            }
        }.distinctBy { it.first }.sortedBy { it.first }

    private fun describe(stream: StreamInfo, audioIndex: Int?, format: SubtitleFormat?): String = buildString {
        append(stream.container)
        append(' ').append(stream.video?.let { "${it.codec}/${it.bitDepth ?: "?"} ${it.dynamicRange}" } ?: "no video")
        val audio = audioIndex?.let(stream.audio::getOrNull)
        append(" + ").append(audio?.let { "${it.codec} ${it.channels ?: "?"}ch${if (it.atmos) " atmos" else ""}" } ?: "no audio")
        if (format != null) append(", ").append(format)
    }
}
