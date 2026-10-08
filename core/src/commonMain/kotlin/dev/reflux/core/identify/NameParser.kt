package dev.reflux.core.identify

import dev.reflux.core.model.CalendarDate

/** An episode marker found in a name. */
internal sealed interface EpisodeMarker {
    /** `S01E02`, `S01E02-E03`, `1x02`, `Season 1 Episode 2`. */
    data class Standard(val season: Int, val episode: Int, val episodeEnd: Int?) : EpisodeMarker

    /** `E02`, `Episode 2`, or a leading number inside a season folder. Season comes from context. */
    data class EpisodeOnly(val episode: Int, val episodeEnd: Int?) : EpisodeMarker

    /** `Show - 012`: absolute numbering, typical for anime releases. */
    data class Absolute(val number: Int) : EpisodeMarker

    /** `Show 2024.03.14`: date-based episodes. */
    data class Dated(val date: CalendarDate) : EpisodeMarker
}

/** The result of parsing a single path segment (a file stem or a directory name). */
internal data class NameParts(
    val title: String,
    val year: Int?,
    val episode: EpisodeMarker?,
    val episodeTitle: String?,
    val edition: String?,
    val part: Int?,
    val externalIds: Map<String, String>,
    /** Text known to be technical rather than title text; used for stream hints. */
    val technical: String,
    val hasReleaseTokens: Boolean,
) {
    val titleKey: String get() = TitleText.key(title)
}

/**
 * Parses one name into title, year, episode marker, edition, and technical remainder.
 *
 * Rules are deterministic and ordered from most to least specific. See docs/METADATA.md.
 */
internal class NameParser(private val maxYear: Int) {
    private val externalIdPattern = Regex(
        """[\[{]\s*(tmdb|imdb|tvdb)(?:id)?\s*[-=:]\s*([a-z0-9]+)\s*[\]}]""",
        RegexOption.IGNORE_CASE,
    )
    private val plexEditionPattern = Regex("""\{\s*edition-([^}]+)}""", RegexOption.IGNORE_CASE)
    private val bracketPattern = Regex("""\[([^\]]*)]|\{([^}]*)}""")
    private val bracketYear = Regex("""^\s*((?:18|19|20)\d{2})\s*$""")

    private val standardMarker = Regex(
        """(?<![\p{L}\p{N}])s(\d{1,3})[ ._-]?e(\d{1,4})((?:-?e\d{1,4}|-\d{1,3}(?![\dpi]))*)(?![\p{L}\p{N}])""",
        RegexOption.IGNORE_CASE,
    )
    private val crossMarker = Regex("""(?<![\p{L}\p{N}])(\d{1,2})x(\d{2,3})(?:-(?:\d{1,2}x)?(\d{2,3}))?(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
    private val wordsMarker = Regex(
        """(?<![\p{L}\p{N}])season[ ._-]*(\d{1,3})[ ._-]*episode[ ._-]*(\d{1,4})(?![\p{L}\p{N}])""",
        RegexOption.IGNORE_CASE,
    )
    private val episodeOnlyMarker = Regex(
        """(?<![\p{L}\p{N}])(?:e|ep|episode)[ ._-]?(\d{1,4})(?:-e?(\d{1,4}))?(?![\p{L}\p{N}])""",
        RegexOption.IGNORE_CASE,
    )
    private val dateMarker = Regex("""(?<![\p{N}])((?:19|20)\d{2})[ ._-](\d{2})[ ._-](\d{2})(?![\p{N}])""")
    private val absoluteMarker = Regex("""^(.+?)[ ._]+-[ ._]+(\d{1,4})(?:v\d)?(?=[ ._\-(\[]|$)""")
    private val yearPattern = Regex("""(?<![\p{L}\p{N}])((?:18|19|20)\d{2})(?![\p{L}\p{N}])""")
    private val partPattern = Regex("""(?<![\p{L}\p{N}])(?:cd|disc|disk|pt)[ ._-]?(\d{1,2})(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
    private val trailingPartPattern = Regex("""(?<![\p{L}\p{N}])part[ ._-]?(\d{1,2})(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)

    /**
     * @param allowEpisodeOnly whether bare `E02` / `Episode 2` markers count. They are only trusted when the
     *   surrounding folders already indicate a show, because titles like "Star Wars Episode 2" are movies.
     */
    fun parse(raw: String, allowEpisodeOnly: Boolean = false): NameParts {
        val externalIds = linkedMapOf<String, String>()
        externalIdPattern.findAll(raw).forEach { externalIds[it.groupValues[1].lowercase()] = it.groupValues[2] }
        var edition = plexEditionPattern.find(raw)?.groupValues?.get(1)?.trim()
        val bracketTechnical = StringBuilder()
        var text = raw.replace(externalIdPattern, " ").replace(plexEditionPattern, " ")
        text = bracketPattern.replace(text) { match ->
            val content = match.groupValues[1].ifEmpty { match.groupValues[2] }
            if (bracketYear.matches(content)) {
                " (${content.trim()}) "
            } else {
                bracketTechnical.append(' ').append(content)
                " "
            }
        }.trim()

        val episode = findEpisode(text, allowEpisodeOnly)
        if (episode != null) {
            val (marker, range) = episode
            val title = cleanTitle(text.substring(0, range.first))
            val rest = text.substring(range.last + 1)
            val restCut = firstTerminator(rest, allowAtStart = true)
            val episodeTitle = cleanTitle(rest.substring(0, restCut)).ifEmpty { null }
            val yearInTitle = trailingYear(title)
            return NameParts(
                title = yearInTitle?.first ?: title,
                year = yearInTitle?.second,
                episode = marker,
                episodeTitle = episodeTitle,
                edition = edition,
                part = null,
                externalIds = externalIds,
                technical = rest.substring(restCut) + bracketTechnical,
                hasReleaseTokens = restCut < rest.length || bracketTechnical.isNotBlank(),
            )
        }

        // Movie-like name: title [year] [edition] [technical...]
        val head = text.substring(0, firstTerminator(text, allowAtStart = false))
        val yearMatch = yearPattern.findAll(head)
            .filter { it.range.first > 0 && it.groupValues[1].toInt() in 1888..maxYear }
            .lastOrNull()
        val editionMatch = ReleaseTokens.findEdition(text)?.takeIf { it.range.first > 0 }
        val partMatch = partPattern.find(text)?.takeIf { it.range.first > 0 }
        val titleEnd = listOfNotNull(yearMatch?.range?.first, editionMatch?.range?.first, partMatch?.range?.first, head.length).min()
        val tail = text.substring(titleEnd)
        if (edition == null) edition = editionMatch?.let(ReleaseTokens::editionLabel)
        val afterYear = yearMatch?.range?.last?.plus(1) ?: head.length
        val part = partMatch?.groupValues?.get(1)?.toInt()
            ?: trailingPartPattern.find(text.substring(afterYear))?.groupValues?.get(1)?.toInt()
        return NameParts(
            title = cleanTitle(text.substring(0, titleEnd)),
            year = yearMatch?.groupValues?.get(1)?.toInt(),
            episode = null,
            episodeTitle = null,
            edition = edition,
            part = part,
            externalIds = externalIds,
            technical = tail + bracketTechnical,
            hasReleaseTokens = head.length < text.length || bracketTechnical.isNotBlank(),
        )
    }

    /** Parses an absolute-numbered name (`Show - 012`), used only when context says the file is an episode. */
    fun parseAbsolute(raw: String): NameParts? {
        val base = parse(raw)
        if (base.episode != null) return null
        val stripped = bracketPattern.replace(raw, " ").trim()
        val match = absoluteMarker.find(stripped) ?: return null
        val number = match.groupValues[2].toInt()
        if (match.groupValues[2].length == 4 && number in 1888..maxYear) return null
        val rest = stripped.substring(match.range.last + 1)
        return base.copy(
            title = cleanTitle(match.groupValues[1]),
            year = null,
            episode = EpisodeMarker.Absolute(number),
            technical = rest + " " + base.technical,
        )
    }

    private fun findEpisode(text: String, allowEpisodeOnly: Boolean): Pair<EpisodeMarker, IntRange>? {
        standardMarker.find(text)?.let { m ->
            val first = m.groupValues[2].toInt()
            val extra = Regex("""\d+""").findAll(m.groupValues[3]).map { it.value.toInt() }.toList()
            val end = extra.maxOrNull()?.takeIf { it > first }
            return EpisodeMarker.Standard(m.groupValues[1].toInt(), first, end) to m.range
        }
        crossMarker.find(text)?.let { m ->
            val first = m.groupValues[2].toInt()
            val end = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.takeIf { it > first }
            return EpisodeMarker.Standard(m.groupValues[1].toInt(), first, end) to m.range
        }
        wordsMarker.find(text)?.let { m ->
            return EpisodeMarker.Standard(m.groupValues[1].toInt(), m.groupValues[2].toInt(), null) to m.range
        }
        dateMarker.find(text)?.let { m ->
            val date = runCatching {
                CalendarDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            }.getOrNull()
            if (date != null && m.range.first > 0) return EpisodeMarker.Dated(date) to m.range
        }
        if (allowEpisodeOnly) episodeOnlyMarker.find(text)?.let { m ->
            val first = m.groupValues[1].toInt()
            val end = m.groupValues[2].takeIf { it.isNotEmpty() }?.toInt()?.takeIf { it > first }
            return EpisodeMarker.EpisodeOnly(first, end) to m.range
        }
        return null
    }

    private fun firstTerminator(text: String, allowAtStart: Boolean): Int =
        ReleaseTokens.titleTerminator.findAll(text)
            .firstOrNull { allowAtStart || it.range.first > 0 }
            ?.range?.first
            ?: text.length

    private fun trailingYear(title: String): Pair<String, Int>? {
        val match = Regex("""^(.+?)[ ._]*\(?((?:18|19|20)\d{2})\)?$""").find(title) ?: return null
        val year = match.groupValues[2].toInt()
        if (year > maxYear) return null
        return cleanTitle(match.groupValues[1]) to year
    }

    companion object {
        /** Turns a raw title region into display text: separators normalized, edges trimmed, casing fixed. */
        fun cleanTitle(region: String): String {
            var text = region.replace('_', ' ')
            if (!text.contains(' ')) {
                // Dot-separated release name: join single-letter acronyms (S.H.I.E.L.D), then dots become spaces.
                text = text.replace(Regex("""(?<![\p{L}\p{N}])\p{L}(?:\.\p{L})+(?![\p{L}\p{N}])""")) {
                    it.value.replace(".", "")
                }
                text = text.replace('.', ' ')
            }
            text = text.replace(Regex("""\(\s*\)"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
                .trim('-', '–', '—', ':', ',', ';', '(', '[', '+', '~', ' ')
                .trimEnd('.')
                .trim()
            return TitleText.display(text)
        }
    }
}
