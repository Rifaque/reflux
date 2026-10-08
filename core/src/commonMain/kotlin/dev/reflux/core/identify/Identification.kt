package dev.reflux.core.identify

import dev.reflux.core.model.Episode
import dev.reflux.core.model.Movie
import dev.reflux.core.model.PlayableItem
import dev.reflux.core.model.Season
import dev.reflux.core.model.Show
import dev.reflux.core.model.StableIds

/**
 * A user's correction of what a file is. Stored by Reflux; never written to the file.
 *
 * For episodes, [title] and [year] describe the show.
 */
data class IdentityOverride(
    val kind: ParsedKind,
    val title: String,
    val year: Int?,
    val season: Int? = null,
    val episode: Int? = null,
) {
    init {
        require(kind == ParsedKind.MOVIE || kind == ParsedKind.EPISODE) { "only movies and episodes can be identified" }
        require(kind != ParsedKind.EPISODE || (season != null && episode != null)) { "episodes need season and episode" }
    }

    /** Applies the override on top of what the parser found, keeping technical facts from the file. */
    fun applyTo(parsed: ParsedMedia): ParsedMedia = parsed.copy(
        kind = kind,
        title = title,
        year = year,
        season = season,
        episode = episode,
        episodeEnd = null,
        absoluteEpisode = null,
        airDate = null,
        episodeTitle = if (kind == ParsedKind.EPISODE) parsed.episodeTitle else null,
        confidence = Confidence(1.0, setOf()),
    )
}

/** The works a file belongs to. */
sealed interface Identification {
    val playable: PlayableItem
    val parsed: ParsedMedia

    data class OfMovie(val movie: Movie, override val parsed: ParsedMedia) : Identification {
        override val playable: PlayableItem get() = movie
    }

    data class OfEpisode(
        val show: Show,
        val season: Season,
        val episode: Episode,
        override val parsed: ParsedMedia,
    ) : Identification {
        override val playable: PlayableItem get() = episode
    }
}

/**
 * Stable identity keys. Two files with the same key are versions of the same work.
 *
 * Keys are built from normalized titles, so `The.Dark.Knight.2008` and `The Dark Knight (2008)` agree.
 */
object IdentityKeys {
    fun movie(title: String, year: Int?): String = "movie:${TitleText.key(title)}:${year ?: ""}"

    fun show(title: String, year: Int?): String = "show:${TitleText.key(title)}" + (year?.let { ":$it" } ?: "")

    fun season(showKey: String, number: Int): String = "$showKey/s$number"

    fun episode(showKey: String, parsed: ParsedMedia): String {
        val season = season(showKey, parsed.season ?: 1)
        return when {
            parsed.episode != null -> "$season/e${parsed.episode}"
            parsed.absoluteEpisode != null -> "$showKey/a${parsed.absoluteEpisode}"
            parsed.airDate != null -> "$showKey/d${parsed.airDate}"
            else -> "$season/e0"
        }
    }
}

/**
 * Converts parsed files into Media Core works.
 *
 * Works are keyed by [IdentityKeys]; [canonicalKey] lets the library redirect a key to an equivalent work.
 *
 * [yearHints] maps `kind + title key` to every year known for that title across the library. A file without
 * a year joins the single known year for its title (`Dune.mkv` next to `Dune (2021)`); when several years are
 * known the title is ambiguous and the file stays separate rather than being guessed into the wrong work.
 */
class Identifier(
    private val yearHints: (kind: ParsedKind, titleKey: String) -> Set<Int> = { _, _ -> emptySet() },
    /**
     * Maps a movie or show identity key to the key of the work it was unified with (e.g. two names for the same
     * provider entry), or returns the key unchanged.
     */
    private val canonicalKey: (String) -> String = { it },
) {

    fun identify(parsed: ParsedMedia): Identification? = when (parsed.kind) {
        ParsedKind.MOVIE -> identifyMovie(parsed)
        ParsedKind.EPISODE -> identifyEpisode(parsed)
        else -> null
    }

    private fun resolvedYear(kind: ParsedKind, parsed: ParsedMedia): Int? =
        parsed.year ?: yearHints(kind, TitleText.key(parsed.title)).singleOrNull()

    private fun identifyMovie(parsed: ParsedMedia): Identification {
        val year = resolvedYear(ParsedKind.MOVIE, parsed)
        val key = canonicalKey(IdentityKeys.movie(parsed.title, year))
        val movie = Movie(id = StableIds.mediaId(key), title = parsed.title, year = year)
        return Identification.OfMovie(movie, parsed.copy(year = year))
    }

    private fun identifyEpisode(parsed: ParsedMedia): Identification {
        val year = resolvedYear(ParsedKind.EPISODE, parsed)
        val showKey = canonicalKey(IdentityKeys.show(parsed.title, year))
        val show = Show(id = StableIds.mediaId(showKey), title = parsed.title, year = year)
        val seasonNumber = parsed.season ?: 1
        val season = Season(
            id = StableIds.mediaId(IdentityKeys.season(showKey, seasonNumber)),
            showId = show.id,
            number = seasonNumber,
        )
        val episode = Episode(
            id = StableIds.mediaId(IdentityKeys.episode(showKey, parsed)),
            showId = show.id,
            seasonId = season.id,
            seasonNumber = seasonNumber,
            episodeNumber = parsed.episode,
            episodeNumberEnd = parsed.episodeEnd,
            absoluteNumber = parsed.absoluteEpisode,
            airDate = parsed.airDate,
            episodeTitle = parsed.episodeTitle,
        )
        return Identification.OfEpisode(show, season, episode, parsed.copy(year = year))
    }

    companion object {
        /** Builds year hints from a collection of parsed files (plus anything already known). */
        fun yearHintsOf(parsed: Iterable<ParsedMedia>): Map<Pair<ParsedKind, String>, Set<Int>> =
            parsed.filter { it.year != null && (it.kind == ParsedKind.MOVIE || it.kind == ParsedKind.EPISODE) }
                .groupBy({ it.kind to TitleText.key(it.title) }, { it.year!! })
                .mapValues { it.value.toSet() }
    }
}

