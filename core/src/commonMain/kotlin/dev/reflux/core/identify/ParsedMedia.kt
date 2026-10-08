package dev.reflux.core.identify

import dev.reflux.core.model.CalendarDate
import dev.reflux.core.playback.StreamInfo

/** What a video file appears to be. */
enum class ParsedKind {
    MOVIE,
    EPISODE,

    /** Trailers, featurettes, deleted scenes. Recognized so they do not pollute the library. */
    EXTRA,

    /** Release samples. */
    SAMPLE,

    /** Disc structures (BDMV, VIDEO_TS) that need whole-disc playback, which is not supported yet. */
    DISC_STRUCTURE,
}

/** Deterministic evidence used to compute identification confidence. Shown in diagnostics. */
enum class IdentificationSignal {
    YEAR_PRESENT,
    STANDARD_EPISODE_MARKER,
    CONTEXTUAL_EPISODE_NUMBER,
    ABSOLUTE_EPISODE_NUMBER,
    DATED_EPISODE,
    SHOW_FOLDER,
    SEASON_FOLDER,
    FOLDER_AGREES,
    TITLE_FROM_FOLDER,
    RELEASE_TOKENS,
    EXTERNAL_ID_HINT,
    GENERIC_FILE_NAME,
    WEAK_TITLE,
}

enum class ConfidenceLevel { HIGH, MEDIUM, LOW }

data class Confidence(val score: Double, val signals: Set<IdentificationSignal>) {
    val level: ConfidenceLevel = when {
        score >= 0.8 -> ConfidenceLevel.HIGH
        score >= 0.55 -> ConfidenceLevel.MEDIUM
        else -> ConfidenceLevel.LOW
    }
}

/**
 * Everything Reflux can learn about a video file from its path alone.
 *
 * For episodes, [title] and [year] describe the show.
 */
data class ParsedMedia(
    val kind: ParsedKind,
    val title: String,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeEnd: Int? = null,
    val absoluteEpisode: Int? = null,
    val airDate: CalendarDate? = null,
    val episodeTitle: String? = null,
    val edition: String? = null,
    val part: Int? = null,
    /** Provider IDs embedded in names, e.g. `{tmdb-157336}`. Keys: `tmdb`, `imdb`, `tvdb`. */
    val externalIds: Map<String, String> = emptyMap(),
    val stream: StreamInfo = StreamInfo(),
    val confidence: Confidence = Confidence(0.0, emptySet()),
)
