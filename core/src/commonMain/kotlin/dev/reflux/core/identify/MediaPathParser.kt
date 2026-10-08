package dev.reflux.core.identify

import dev.reflux.core.identify.IdentificationSignal.ABSOLUTE_EPISODE_NUMBER
import dev.reflux.core.identify.IdentificationSignal.CONTEXTUAL_EPISODE_NUMBER
import dev.reflux.core.identify.IdentificationSignal.DATED_EPISODE
import dev.reflux.core.identify.IdentificationSignal.EXTERNAL_ID_HINT
import dev.reflux.core.identify.IdentificationSignal.FOLDER_AGREES
import dev.reflux.core.identify.IdentificationSignal.GENERIC_FILE_NAME
import dev.reflux.core.identify.IdentificationSignal.RELEASE_TOKENS
import dev.reflux.core.identify.IdentificationSignal.SEASON_FOLDER
import dev.reflux.core.identify.IdentificationSignal.SHOW_FOLDER
import dev.reflux.core.identify.IdentificationSignal.STANDARD_EPISODE_MARKER
import dev.reflux.core.identify.IdentificationSignal.TITLE_FROM_FOLDER
import dev.reflux.core.identify.IdentificationSignal.WEAK_TITLE
import dev.reflux.core.identify.IdentificationSignal.YEAR_PRESENT

/**
 * Turns a source-relative path into [ParsedMedia] using the file name and its folders.
 *
 * Users are never required to follow a naming convention, but common ones (`Show/Season 01/S01E02.mkv`,
 * `Movie (2014)/Movie (2014).mkv`, scene release names, anime fansub names) are all understood.
 *
 * @param maxYear the latest plausible release year; numbers above it are treated as title text (`Blade Runner 2049`).
 */
class MediaPathParser(maxYear: Int) {
    private val names = NameParser(maxYear)

    private val seasonFolder = Regex(
        """^(?:season|series|staffel|saison|temporada|stagione|seizoen|sezon)[ ._-]*(\d{1,3})(?:\b.*)?$|^s(\d{1,3})$""",
        RegexOption.IGNORE_CASE,
    )
    private val specialsFolder = Regex("""^(?:specials?|extras?[ ._-]?season|season[ ._-]*0+)$""", RegexOption.IGNORE_CASE)
    private val seasonPackFolder = Regex(
        """(?<![\p{L}\p{N}])(?:s(\d{1,2})|season[ ._-]?(\d{1,2}))(?![\p{L}\p{N}])""",
        RegexOption.IGNORE_CASE,
    )
    private val leadingEpisodeNumber = Regex("""^(?:e|ep|episode)?[ ._-]*(\d{1,3})(?:[ ._]*-[ ._]*|[ ._]+|$)(.*)$""", RegexOption.IGNORE_CASE)
    private val extrasSuffix = Regex(
        """[ ._-]+(?:trailer|featurette|behindthescenes|deleted|deletedscene|interview|scene|short|other|extra)$""",
        RegexOption.IGNORE_CASE,
    )
    private val sampleToken = Regex("""(?<![\p{L}\p{N}])sample(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
    private val genericFileName = Regex(
        """^(?:movie|film|video|feature|main|title[ _-]?t?\d*|vts[ _]\d+[ _]\d+|disc[ _-]?\d*|playlist|index)$""",
        RegexOption.IGNORE_CASE,
    )

    /** Disc-rip style numeric names (`00001.m2ts`); only generic when no year says it is a title like "300". */
    private val numericFileName = Regex("""^\d{3,5}$""")
    private val miniseriesPart = Regex("""(?<![\p{L}\p{N}])(?:part|pt|chapter)[ ._-]?(0\d)(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)

    private val extrasFolders = setOf(
        "extras", "extra", "featurettes", "behind the scenes", "deleted scenes", "interviews", "scenes", "shorts",
        "trailers", "other", "others", "bonus", "bonus features", "samples", "sample",
    )
    private val discFolders = setOf("bdmv", "video_ts", "stream", "certificate", "audio_ts", "playlist")
    private val categoryFolders = setOf(
        "tv", "tv shows", "tvshows", "shows", "series", "tv series", "television", "anime", "cartoons", "kids",
        "movies", "films", "movie", "film", "media", "video", "videos", "downloads", "download", "complete",
        "completed", "library", "documentaries", "4k", "uhd", "hd", "new", "unsorted", "plex", "jellyfin",
    )

    /** Parses [path]; returns null when the path is not a video file. */
    fun parse(path: String): ParsedMedia? {
        val segments = path.split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null
        val fileName = segments.last()
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension !in dev.reflux.core.source.ScanRules.videoExtensions) return null
        val stem = fileName.substringBeforeLast('.')
        val dirs = segments.dropLast(1)
        val lowerDirs = dirs.map { it.lowercase() }

        if (lowerDirs.any { it in discFolders }) return special(ParsedKind.DISC_STRUCTURE, stem)
        if (lowerDirs.any { it in extrasFolders } || extrasSuffix.containsMatchIn(stem)) {
            return special(ParsedKind.EXTRA, stem)
        }
        if (sampleToken.containsMatchIn(stem)) return special(ParsedKind.SAMPLE, stem)

        val seasonContext = findSeasonContext(dirs)
        val file = names.parse(stem, allowEpisodeOnly = seasonContext != null)
        val episodeFile = when {
            file.episode != null -> file
            seasonContext != null -> contextualEpisode(stem, seasonContext.number)
                // "[Group] Show - 28" inside a season folder numbers episodes within that season.
                ?: absoluteCandidate(stem)?.let { it.copy(episode = EpisodeMarker.EpisodeOnly((it.episode as EpisodeMarker.Absolute).number, null)) }
            else -> absoluteCandidate(stem) ?: miniseriesEpisode(stem, dirs)
        }
        return if (episodeFile != null) {
            episode(episodeFile, extension, dirs, seasonContext)
        } else {
            movie(file, stem, extension, dirs)
        }
    }

    /** The season a folder name denotes (`Season 2`, `S02`, `Specials` → 0), or null. */
    fun seasonOfFolder(name: String): Int? {
        val trimmed = name.trim()
        if (specialsFolder.matches(trimmed)) return 0
        val match = seasonFolder.matchEntire(trimmed) ?: return null
        return match.groupValues[1].ifEmpty { match.groupValues[2] }.toInt()
    }

    private fun special(kind: ParsedKind, stem: String) =
        ParsedMedia(kind = kind, title = NameParser.cleanTitle(stem))

    private data class SeasonContext(val number: Int, val index: Int)

    /** Nearest folder naming a season ("Season 2", "S02", "Specials", or a season-pack release folder). */
    private fun findSeasonContext(dirs: List<String>): SeasonContext? {
        for (i in dirs.indices.reversed()) {
            val name = dirs[i].trim()
            seasonOfFolder(name)?.let { return SeasonContext(it, i) }
            // Season-pack folders like "Show.S02.1080p.BluRay-GROUP" (but not single-episode release folders).
            val parsed = names.parse(name)
            if (parsed.episode == null) {
                seasonPackFolder.find(name)?.takeIf { it.range.first > 0 }?.let { m ->
                    val number = m.groupValues[1].ifEmpty { m.groupValues[2] }.toInt()
                    return SeasonContext(number, i)
                }
            }
        }
        return null
    }

    /** Episode number from a bare name inside a season folder: `01 - Pilot.mkv`, `Episode 1.mkv`, `101.mkv`. */
    private fun contextualEpisode(stem: String, season: Int): NameParts? {
        val match = leadingEpisodeNumber.matchEntire(stem.trim()) ?: return null
        var number = match.groupValues[1].toInt()
        // "101" inside "Season 1" means S01E01.
        if (match.groupValues[1].length == 3 && season in 1..9 && number / 100 == season) number %= 100
        val restParts = names.parse(match.groupValues[2].ifBlank { "x" })
        val episodeTitle = restParts.title.takeIf { match.groupValues[2].isNotBlank() && it.isNotBlank() }
        return NameParts(
            title = "",
            year = null,
            episode = EpisodeMarker.EpisodeOnly(number, null),
            episodeTitle = episodeTitle,
            edition = null,
            part = null,
            externalIds = emptyMap(),
            technical = restParts.technical,
            hasReleaseTokens = restParts.hasReleaseTokens,
        )
    }

    /** Absolute-numbered anime-style names are accepted only with fansub-style brackets or release tokens. */
    private fun absoluteCandidate(stem: String): NameParts? {
        val parts = names.parseAbsolute(stem) ?: return null
        val number = (parts.episode as EpisodeMarker.Absolute).number
        val plausible = stem.trimStart().startsWith("[") || (parts.hasReleaseTokens && number < 1000)
        return parts.takeIf { plausible && it.title.isNotBlank() }
    }

    /**
     * Miniseries parts (`Band.of.Brothers.Part.01.Currahee`) inside a folder named after the series. The part
     * number must be zero-padded and the folder must agree, because movies also use "Part 1".
     */
    private fun miniseriesEpisode(stem: String, dirs: List<String>): NameParts? {
        val match = miniseriesPart.find(stem)?.takeIf { it.range.first > 0 } ?: return null
        val folder = dirs.lastOrNull()?.let { names.parse(it) } ?: return null
        val title = NameParser.cleanTitle(stem.substring(0, match.range.first))
        if (TitleText.key(title) != folder.titleKey || title.isBlank()) return null
        val rest = names.parse(stem.substring(match.range.last + 1).ifBlank { "x" })
        return NameParts(
            title = title,
            year = folder.year,
            episode = EpisodeMarker.EpisodeOnly(match.groupValues[1].toInt(), null),
            episodeTitle = rest.title.takeIf { stem.substring(match.range.last + 1).isNotBlank() && it.isNotBlank() },
            edition = null,
            part = null,
            externalIds = emptyMap(),
            technical = rest.technical,
            hasReleaseTokens = rest.hasReleaseTokens,
        )
    }

    private fun episode(file: NameParts, extension: String, dirs: List<String>, seasonContext: SeasonContext?): ParsedMedia {
        val signals = mutableSetOf<IdentificationSignal>()
        var score = 0.45
        val showFolder = findShowFolder(dirs, seasonContext, file)
        val showFolderParts = showFolder?.let { names.parse(it) }

        val title: String
        val year: Int?
        if (showFolderParts != null && showFolderParts.title.isNotBlank()) {
            title = showFolderParts.title
            year = showFolderParts.year ?: file.year
            signals += SHOW_FOLDER
            score += 0.15
            if (file.title.isNotBlank() && TitleText.key(file.title) == TitleText.key(title)) {
                signals += FOLDER_AGREES
                score += 0.05
            }
        } else {
            title = file.title
            year = file.year
        }
        if (seasonContext != null) {
            signals += SEASON_FOLDER
            score += 0.05
        }

        var season: Int? = null
        var episode: Int? = null
        var episodeEnd: Int? = null
        var absolute: Int? = null
        var airDate: dev.reflux.core.model.CalendarDate? = null
        when (val marker = file.episode) {
            is EpisodeMarker.Standard -> {
                season = marker.season; episode = marker.episode; episodeEnd = marker.episodeEnd
                signals += STANDARD_EPISODE_MARKER; score += 0.3
            }
            is EpisodeMarker.EpisodeOnly -> {
                season = seasonContext?.number ?: 1; episode = marker.episode; episodeEnd = marker.episodeEnd
                signals += CONTEXTUAL_EPISODE_NUMBER; score += 0.15
            }
            is EpisodeMarker.Absolute -> {
                season = seasonContext?.number ?: 1; absolute = marker.number
                signals += ABSOLUTE_EPISODE_NUMBER; score += 0.1
            }
            is EpisodeMarker.Dated -> {
                season = marker.date.year; airDate = marker.date
                signals += DATED_EPISODE; score += 0.2
            }
            null -> Unit
        }
        if (year != null) signals += YEAR_PRESENT
        val externalIds = (showFolderParts?.externalIds ?: emptyMap()) + file.externalIds
        if (externalIds.isNotEmpty()) {
            signals += EXTERNAL_ID_HINT; score += 0.1
        }
        if (TitleText.key(title).length < 2) {
            signals += WEAK_TITLE; score -= 0.4
        }
        return ParsedMedia(
            kind = ParsedKind.EPISODE,
            title = title,
            year = year,
            season = season,
            episode = episode,
            episodeEnd = episodeEnd,
            absoluteEpisode = absolute,
            airDate = airDate,
            episodeTitle = file.episodeTitle,
            externalIds = externalIds,
            stream = ReleaseTokens.streamHints(file.technical, extension),
            confidence = Confidence(score.coerceIn(0.0, 1.0), signals),
        )
    }

    /**
     * The folder naming the show: the nearest ancestor that is not a season folder, a release folder,
     * or a generic category folder. Without a season folder in between, the folder must agree with the
     * file's own title, so a show isn't named after an unrelated folder like "Stuff".
     */
    private fun findShowFolder(dirs: List<String>, seasonContext: SeasonContext?, file: NameParts): String? {
        val searchEnd = seasonContext?.index ?: dirs.size
        for (i in (0 until searchEnd).reversed()) {
            val name = dirs[i]
            if (name.lowercase() in categoryFolders) continue
            if (seasonOfFolder(name) != null) continue
            val parts = names.parse(name)
            if (parts.episode != null) continue
            if (seasonPackFolder.find(name)?.takeIf { it.range.first > 0 } != null) continue
            if (parts.title.isBlank()) continue
            val separatedBySeason = seasonContext != null
            val fileKey = TitleText.key(file.title)
            val folderKey = parts.titleKey
            val agrees = fileKey.isEmpty() || fileKey == folderKey ||
                fileKey.startsWith("$folderKey ") || folderKey.startsWith("$fileKey ")
            return if (separatedBySeason || agrees) name else null
        }
        return null
    }

    private fun movie(file: NameParts, stem: String, extension: String, dirs: List<String>): ParsedMedia {
        val signals = mutableSetOf<IdentificationSignal>()
        var score = 0.55
        val folderName = dirs.lastOrNull { it.lowercase() !in categoryFolders }
            ?.takeIf { it == dirs.last() }
        val folder = folderName?.let { names.parse(it) }?.takeIf { it.title.isNotBlank() && it.episode == null }
        val generic = genericFileName.matches(file.title.trim()) || TitleText.key(file.title).isEmpty() ||
            (file.year == null && numericFileName.matches(file.title.trim()))

        var title = file.title
        var year = file.year
        if (folder != null) {
            val fileKey = TitleText.key(file.title)
            val related = fileKey == folder.titleKey ||
                fileKey.startsWith(folder.titleKey + " ") || folder.titleKey.startsWith("$fileKey ")
            when {
                generic -> {
                    title = folder.title; year = folder.year
                    signals += TITLE_FROM_FOLDER
                }
                related && (year == null || folder.year == null || folder.year == year) -> {
                    // Same work: prefer the folder's curated title and fill in a missing year.
                    if (fileKey == folder.titleKey) title = folder.title
                    year = year ?: folder.year
                    signals += FOLDER_AGREES
                    score += 0.1
                }
            }
        }
        if (generic) signals += GENERIC_FILE_NAME
        if (year != null) {
            signals += YEAR_PRESENT
            score += 0.3
        }
        if (file.hasReleaseTokens) {
            signals += RELEASE_TOKENS
            score += 0.05
        }
        val externalIds = (folder?.externalIds ?: emptyMap()) + file.externalIds
        if (externalIds.isNotEmpty()) {
            signals += EXTERNAL_ID_HINT
            score += 0.2
        }
        // One-character titles exist ("9", "M", "Q"), but only a year makes them trustworthy.
        if (generic && TITLE_FROM_FOLDER !in signals || (TitleText.key(title).length < 2 && year == null)) {
            signals += WEAK_TITLE
            score -= 0.4
        }
        return ParsedMedia(
            kind = ParsedKind.MOVIE,
            title = title.ifBlank { NameParser.cleanTitle(stem) },
            year = year,
            edition = file.edition ?: folder?.edition,
            part = file.part,
            externalIds = externalIds,
            stream = ReleaseTokens.streamHints(file.technical, extension),
            confidence = Confidence(score.coerceIn(0.0, 1.0), signals),
        )
    }
}
