package dev.reflux.core.search

import dev.reflux.core.identify.TitleText
import dev.reflux.core.model.MediaKind
import dev.reflux.core.playback.DynamicRange

/** A structured interpretation of a search query. Every field is optional; [text] matches titles. */
data class SmartQuery(
    val kinds: Set<MediaKind> = emptySet(),
    val watched: Boolean? = null,
    val inProgress: Boolean = false,
    val favorite: Boolean = false,
    val minHeight: Int? = null,
    val dynamicRange: DynamicRange? = null,
    /** Any HDR format (HDR10, HDR10+, Dolby Vision, HLG). */
    val hdr: Boolean = false,
    /** Canonical genre names as known to the library. All must match. */
    val genres: Set<String> = emptySet(),
    /** Full names of people as known to the library. All must match. */
    val people: Set<String> = emptySet(),
    val years: IntRange? = null,
    val minRuntimeMinutes: Int? = null,
    val maxRuntimeMinutes: Int? = null,
    val text: String = "",
) {
    /** Whether anything beyond plain title text was understood. */
    val structured: Boolean
        get() = kinds.isNotEmpty() || watched != null || inProgress || favorite || minHeight != null ||
            dynamicRange != null || hdr || genres.isNotEmpty() || people.isNotEmpty() || years != null ||
            minRuntimeMinutes != null || maxRuntimeMinutes != null
}

/** Names the parser may recognize, taken from the user's own library. */
data class SearchVocabulary(val genres: Set<String>, val people: Set<String>)

/**
 * Deterministic natural-ish query parsing: phrases are matched by rules in a fixed order, recognized words
 * are consumed, and whatever remains is title text. No language model is involved.
 *
 * Examples: `4k movies I haven't watched`, `Nolan movies`, `science fiction under 2 hours`,
 * `movies with Ryan Gosling`, `90s comedies`, `dolby vision`, `unfinished shows`.
 */
object SmartQueryParser {
    fun parse(query: String, vocabulary: SearchVocabulary, currentYear: Int): SmartQuery {
        val prepared = query.replace("+", " plus ").replace(Regex("""(\d+)[.,](\d+)""")) { "${it.groupValues[1]} point ${it.groupValues[2]}" }
        val tokens = TitleText.key(prepared).split(' ').filter { it.isNotEmpty() }.toMutableList()
        val consumed = BooleanArray(tokens.size)
        var result = SmartQuery()
        var kindWord = false

        fun consume(range: IntRange) = range.forEach { consumed[it] = true }

        /** Finds an unconsumed phrase; returns its token range. */
        fun find(phrase: List<String>, from: Int = 0): IntRange? {
            if (phrase.isEmpty()) return null
            for (start in from..tokens.size - phrase.size) {
                if ((phrase.indices).all { !consumed[start + it] && tokens[start + it] == phrase[it] }) {
                    return start until start + phrase.size
                }
            }
            return null
        }

        fun matchAny(phrases: List<String>, action: () -> Unit): Boolean {
            for (phrase in phrases.sortedByDescending { it.length }) {
                val range = find(phrase.split(' ')) ?: continue
                consume(range)
                action()
                return true
            }
            return false
        }

        // Conversational lead-ins carry no meaning ("show me" must not read as TV shows).
        for (phrase in LEAD_INS) find(phrase.split(' '))?.let(::consume)

        // Watch state (longest phrases first so "not watched" wins over "watched").
        matchAny(UNWATCHED) { result = result.copy(watched = false) } ||
            matchAny(WATCHED) { result = result.copy(watched = true) }
        matchAny(IN_PROGRESS) { result = result.copy(inProgress = true) }
        matchAny(FAVORITES) { result = result.copy(favorite = true) }

        // Kinds.
        for ((words, kind) in KIND_WORDS) {
            while (true) {
                val range = words.firstNotNullOfOrNull { find(it.split(' ')) } ?: break
                consume(range)
                kindWord = true
                result = result.copy(kinds = result.kinds + kind)
            }
        }

        // Picture quality.
        matchAny(DOLBY_VISION) { result = result.copy(dynamicRange = DynamicRange.DOLBY_VISION) }
        matchAny(HDR) { result = result.copy(hdr = true) }
        for ((words, height) in RESOLUTIONS) matchAny(words) { result = result.copy(minHeight = maxOf(result.minHeight ?: 0, height)) }

        // Runtime: "under 2 hours", "less than 90 minutes", "over an hour".
        for (i in tokens.indices) {
            if (consumed[i]) continue
            val comparator = COMPARATORS.entries.firstOrNull { (phrase, _) ->
                val words = phrase.split(' ')
                i + words.size <= tokens.size && words.indices.all { tokens[i + it] == words[it] && !consumed[i + it] }
            } ?: continue
            val after = i + comparator.key.split(' ').size
            val (minutes, length) = duration(tokens, after) ?: continue
            consume(i until after + length)
            result = if (comparator.value) result.copy(maxRuntimeMinutes = minutes) else result.copy(minRuntimeMinutes = minutes)
        }

        // Years and decades.
        for (i in tokens.indices) {
            if (consumed[i]) continue
            decade(tokens[i], currentYear)?.let { range ->
                consume(i..i)
                if (i > 0 && !consumed[i - 1] && tokens[i - 1] in YEAR_PREPOSITIONS) consume(i - 1 until i)
                result = result.copy(years = range)
            }
        }
        for (i in tokens.indices) {
            if (consumed[i]) continue
            val year = tokens[i].toIntOrNull()?.takeIf { tokens[i].length == 4 && it in 1888..currentYear + 1 } ?: continue
            val preposition = tokens.getOrNull(i - 1)?.takeIf { i > 0 && !consumed[i - 1] }
            val range = when (preposition) {
                "before" -> 1888..year - 1
                "after" -> year + 1..currentYear + 1
                "since" -> year..currentYear + 1
                "from", "in", "of" -> year..year
                else -> if (kindWord || result.structured) year..year else null // a bare year may be a title ("2012")
            } ?: continue
            consume(i..i)
            if (preposition != null) consume(i - 1 until i)
            result = result.copy(years = range)
        }

        // Genres from the library's vocabulary, with common synonyms and plurals.
        val genreKeys = vocabulary.genres.associateBy { TitleText.key(it) }
        val genrePhrases = genreKeys.keys.map { it to genreKeys.getValue(it) } +
            GENRE_SYNONYMS.mapNotNull { (synonym, canonical) -> genreKeys[canonical]?.let { synonym to it } }
        for ((phrase, genre) in genrePhrases.sortedByDescending { it.first.length }) {
            for (variant in listOf(phrase, plural(phrase))) {
                val range = find(variant.split(' ')) ?: continue
                consume(range)
                result = result.copy(genres = result.genres + genre)
            }
        }

        // People: a known full name anywhere; a surname only after "with/starring/by" or next to a kind ("Nolan movies").
        val people = vocabulary.people.map { it to TitleText.key(it).split(' ') }
        for (i in tokens.indices) {
            if (consumed[i]) continue
            val explicit = i > 0 && tokens[i - 1] in PERSON_PREPOSITIONS && !consumed[i - 1]
            val nextToKind = kindWord && ((i + 1 < tokens.size && consumed[i + 1]) || (i > 0 && consumed[i - 1]))
            val match = matchPerson(tokens, i, consumed, people, allowSurname = explicit || nextToKind) ?: continue
            consume(match.second)
            if (explicit) consume(i - 1 until i)
            result = result.copy(people = result.people + match.first)
        }

        val text = tokens.filterIndexed { index, token -> !consumed[index] && (token !in STOP_WORDS || !result.structured) }
            .joinToString(" ")
        return result.copy(text = if (result.structured) text else TitleText.key(query))
    }

    /** Matches a full name ("ryan gosling") or a unique surname ("nolan") starting at [i]. */
    private fun matchPerson(
        tokens: List<String>,
        i: Int,
        consumed: BooleanArray,
        people: List<Pair<String, List<String>>>,
        allowSurname: Boolean,
    ): Pair<String, IntRange>? {
        val full = people.filter { (_, words) ->
            words.size > 1 && i + words.size <= tokens.size && words.indices.all { tokens[i + it] == words[it] && !consumed[i + it] }
        }.maxByOrNull { it.second.size }
        if (full != null) return full.first to (i until i + full.second.size)
        if (!allowSurname) return null
        val bySurname = people.filter { (_, words) -> words.size > 1 && words.last() == tokens[i] }
        return bySurname.singleOrNull()?.let { it.first to (i..i) }
    }

    /** Parses "2 hours", "90 minutes", "an hour", "1.5 hours" (prepared as "1 point 5 hours"). */
    private fun duration(tokens: List<String>, start: Int): Pair<Int, Int>? {
        var index = start
        var amount: Double = when (val word = tokens.getOrNull(index)) {
            null -> return null
            "an", "a", "one" -> 1.0
            in NUMBER_WORDS -> NUMBER_WORDS.getValue(word).toDouble()
            else -> word.toDoubleOrNull() ?: return null
        }
        index++
        if (tokens.getOrNull(index) == "point") {
            val fraction = tokens.getOrNull(index + 1)?.takeIf { it.all(Char::isDigit) } ?: return null
            amount += "0.$fraction".toDouble()
            index += 2
        }
        if (tokens.getOrNull(index) == "and" && tokens.getOrNull(index + 1) == "a" && tokens.getOrNull(index + 2) == "half") {
            amount += 0.5
            index += 3
        }
        val unit = tokens.getOrNull(index)
        val minutes = when (unit) {
            in HOUR_WORDS -> amount * 60
            in MINUTE_WORDS -> amount
            else -> return null
        }
        return minutes.toInt() to (index + 1 - start)
    }

    private fun decade(token: String, currentYear: Int): IntRange? {
        val match = Regex("""^(\d{2}|\d{4})s$""").matchEntire(token) ?: return null
        val digits = match.groupValues[1]
        val start = if (digits.length == 2) {
            val century = if (digits.toInt() + 2000 <= currentYear) 2000 else 1900
            century + digits.toInt()
        } else {
            digits.toInt()
        }
        if (start % 10 != 0) return null
        return start..start + 9
    }

    private fun plural(word: String): String = when {
        word.endsWith("y") && !word.endsWith("ey") -> word.dropLast(1) + "ies"
        word.endsWith("s") -> word
        else -> word + "s"
    }

    private val LEAD_INS = listOf("show me", "find me", "give me", "i want to watch", "something to watch", "i want")
    private val UNWATCHED = listOf(
        "unwatched", "not watched", "havent watched", "have not watched", "havent seen", "have not seen", "not seen",
        "unseen", "never watched", "never seen", "new to me",
    )
    private val WATCHED = listOf("watched", "already watched", "seen", "already seen")
    private val IN_PROGRESS = listOf("in progress", "unfinished", "partially watched", "started", "continue watching")
    private val FAVORITES = listOf("favorites", "favourites", "favorite", "favourite", "my favorites", "my favourites")
    private val KIND_WORDS = listOf(
        listOf("movies", "movie", "films", "film") to MediaKind.MOVIE,
        listOf("tv shows", "tv show", "shows", "show", "series", "tv") to MediaKind.SHOW,
        listOf("episodes", "episode") to MediaKind.EPISODE,
    )
    private val DOLBY_VISION = listOf("dolby vision", "dovi", "dv")
    private val HDR = listOf("hdr10 plus", "hdr10", "hdr")
    private val RESOLUTIONS = listOf(
        listOf("8k") to 4320,
        listOf("4k", "uhd", "2160p", "ultra hd") to 2160,
        listOf("1080p", "full hd", "fhd") to 1080,
        listOf("720p", "hd") to 720,
    )
    /** Phrase → whether it is an upper bound. */
    private val COMPARATORS = mapOf(
        "under" to true, "less than" to true, "shorter than" to true, "below" to true, "at most" to true,
        "within" to true, "over" to false, "more than" to false, "longer than" to false, "at least" to false,
    )
    private val HOUR_WORDS = setOf("hour", "hours", "hr", "hrs", "h")
    private val MINUTE_WORDS = setOf("minute", "minutes", "min", "mins", "m")
    private val NUMBER_WORDS = mapOf("two" to 2, "three" to 3, "four" to 4, "ninety" to 90, "half" to 0)
    private val YEAR_PREPOSITIONS = setOf("from", "in", "the", "of")
    private val PERSON_PREPOSITIONS = setOf("with", "starring", "by", "featuring")
    private val GENRE_SYNONYMS = listOf(
        "sci fi" to "science fiction", "scifi" to "science fiction", "romcom" to "romance",
        "animated" to "animation", "cartoons" to "animation", "kids" to "family", "documentaries" to "documentary",
        "doc" to "documentary", "docs" to "documentary", "funny" to "comedy", "scary" to "horror", "romantic" to "romance",
        "thrillers" to "thriller", "westerns" to "western", "musicals" to "music", "war movies" to "war",
    )
    private val STOP_WORDS = setOf(
        "i", "me", "my", "a", "an", "the", "that", "which", "are", "is", "to", "yet", "all", "some", "any", "show",
        "find", "list", "of", "and", "in", "from", "directed", "made", "for", "want", "watch", "something",
    )
}

/** What [SmartQuery] needs to know about a work. */
data class SmartDocument(
    val kind: MediaKind,
    val genres: Set<String>,
    val people: Set<String>,
    val year: Int?,
    val runtimeMinutes: Int?,
    /** Best nominal resolution among the work's versions (see [dev.reflux.core.playback.VideoStream.nominalHeight]). */
    val maxHeight: Int?,
    val dynamicRanges: Set<DynamicRange>,
    val watched: Boolean,
    val inProgress: Boolean,
    val favorite: Boolean,
)

/** Applies the structured part of a [SmartQuery]; title text is matched separately by [SearchMatcher]. */
fun SmartQuery.matches(document: SmartDocument): Boolean {
    if (kinds.isNotEmpty() && document.kind !in kinds) return false
    if (watched != null && document.watched != watched) return false
    if (inProgress && !document.inProgress) return false
    if (favorite && !document.favorite) return false
    if (minHeight != null && (document.maxHeight ?: 0) < minHeight * 9 / 10) return false
    if (dynamicRange != null && dynamicRange !in document.dynamicRanges) return false
    if (hdr && document.dynamicRanges.none { it != DynamicRange.SDR }) return false
    if (!document.genres.containsAll(genres)) return false
    if (!document.people.containsAll(people)) return false
    if (years != null && (document.year == null || document.year !in years)) return false
    if (maxRuntimeMinutes != null && (document.runtimeMinutes == null || document.runtimeMinutes > maxRuntimeMinutes)) return false
    if (minRuntimeMinutes != null && (document.runtimeMinutes == null || document.runtimeMinutes < minRuntimeMinutes)) return false
    return true
}
