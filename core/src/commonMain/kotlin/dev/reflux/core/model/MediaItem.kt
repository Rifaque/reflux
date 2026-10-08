package dev.reflux.core.model

/** What kind of work an item is. */
enum class MediaKind { MOVIE, SHOW, SEASON, EPISODE }

/**
 * A work in the user's library: what the media *is*, independent of which files or sources hold it.
 *
 * Playable works (movies, episodes) are connected to their physical files through [MediaVersion]s.
 */
sealed interface MediaItem {
    val id: MediaId
    val kind: MediaKind
    val title: String
}

/** Items that can own playable versions. */
sealed interface PlayableItem : MediaItem

data class Movie(
    override val id: MediaId,
    override val title: String,
    val year: Int?,
    val edition: String? = null,
) : PlayableItem {
    override val kind: MediaKind get() = MediaKind.MOVIE
}

data class Show(
    override val id: MediaId,
    override val title: String,
    val year: Int?,
) : MediaItem {
    override val kind: MediaKind get() = MediaKind.SHOW
}

data class Season(
    override val id: MediaId,
    val showId: MediaId,
    /** Season number; 0 is specials. */
    val number: Int,
) : MediaItem {
    override val kind: MediaKind get() = MediaKind.SEASON
    override val title: String get() = if (number == 0) "Specials" else "Season $number"
}

data class Episode(
    override val id: MediaId,
    val showId: MediaId,
    val seasonId: MediaId,
    val seasonNumber: Int,
    /** Episode number within the season, when known. */
    val episodeNumber: Int?,
    /** Last episode number for multi-episode files (e.g. S01E01-E02). */
    val episodeNumberEnd: Int? = null,
    /** Absolute episode number for absolute-numbered (typically anime) releases. */
    val absoluteNumber: Int? = null,
    /** Air date for date-based (typically daily) shows. */
    val airDate: CalendarDate? = null,
    val episodeTitle: String? = null,
) : PlayableItem {
    override val kind: MediaKind get() = MediaKind.EPISODE

    override val title: String
        get() = episodeTitle ?: defaultTitle()

    private fun defaultTitle(): String = when {
        episodeNumber != null && episodeNumberEnd != null -> "Episodes $episodeNumber–$episodeNumberEnd"
        episodeNumber != null -> "Episode $episodeNumber"
        absoluteNumber != null -> "Episode $absoluteNumber"
        airDate != null -> airDate.toString()
        else -> "Episode"
    }
}

/** A calendar date without time zone, used for air dates. */
data class CalendarDate(val year: Int, val month: Int, val day: Int) : Comparable<CalendarDate> {
    init {
        require(month in 1..12) { "month out of range: $month" }
        require(day in 1..31) { "day out of range: $day" }
    }

    override fun compareTo(other: CalendarDate): Int =
        compareValuesBy(this, other, CalendarDate::year, CalendarDate::month, CalendarDate::day)

    override fun toString(): String =
        "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

    companion object {
        fun parse(value: String): CalendarDate? {
            val match = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").matchEntire(value) ?: return null
            val (y, m, d) = match.destructured
            return runCatching { CalendarDate(y.toInt(), m.toInt(), d.toInt()) }.getOrNull()
        }
    }
}
