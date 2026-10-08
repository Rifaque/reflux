package dev.reflux.core.identify

import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.source.FileRole
import dev.reflux.core.source.ScanRules

/** An external subtitle matched to a video by path rules. */
data class SubtitleSidecar(
    val videoPath: String,
    val subtitlePath: String,
    val format: SubtitleFormat,
    val language: String?,
    val forced: Boolean,
    val hearingImpaired: Boolean,
)

/** What an artwork image next to the media describes. */
sealed interface ArtworkScope {
    /** Belongs to the work in one video file (`Movie (2014)-poster.jpg`, `S01E01-thumb.jpg`). */
    data class Video(val videoPath: String) : ArtworkScope

    /** Belongs to whatever work a directory represents (`poster.jpg` in a movie or show folder). */
    data class Directory(val directoryPath: String) : ArtworkScope

    /** A season image stored in the show folder (`season02-poster.jpg`). */
    data class SeasonOf(val directoryPath: String, val season: Int) : ArtworkScope
}

data class ArtworkSidecar(val scope: ArtworkScope, val kind: ArtworkKind, val imagePath: String)

data class Sidecars(val subtitles: List<SubtitleSidecar>, val artwork: List<ArtworkSidecar>)

/**
 * Associates subtitle and image files with videos using deterministic naming rules.
 *
 * Existing user-provided artwork and subtitles are honored, but never required.
 */
object SidecarMatcher {
    private val artworkNames = mapOf(
        "poster" to ArtworkKind.POSTER, "folder" to ArtworkKind.POSTER, "cover" to ArtworkKind.POSTER,
        "movie" to ArtworkKind.POSTER, "show" to ArtworkKind.POSTER,
        "fanart" to ArtworkKind.BACKDROP, "backdrop" to ArtworkKind.BACKDROP, "background" to ArtworkKind.BACKDROP,
        "art" to ArtworkKind.BACKDROP,
        "logo" to ArtworkKind.LOGO, "clearlogo" to ArtworkKind.LOGO,
        "banner" to ArtworkKind.BANNER,
        "thumb" to ArtworkKind.THUMBNAIL, "landscape" to ArtworkKind.THUMBNAIL,
    )
    private val seasonArtwork = Regex("""^season[ ._-]?(\d{1,3}|specials)(?:-(poster|banner|fanart|landscape|thumb))?$""", RegexOption.IGNORE_CASE)
    private val subtitleFolders = setOf("subs", "subtitles", "sub", "subtitle")

    private val languages = mapOf(
        "english" to "en", "eng" to "en", "en" to "en", "french" to "fr", "fre" to "fr", "fra" to "fr", "fr" to "fr",
        "german" to "de", "ger" to "de", "deu" to "de", "de" to "de", "spanish" to "es", "spa" to "es", "es" to "es",
        "italian" to "it", "ita" to "it", "it" to "it", "portuguese" to "pt", "por" to "pt", "pt" to "pt",
        "dutch" to "nl", "dut" to "nl", "nld" to "nl", "nl" to "nl", "japanese" to "ja", "jpn" to "ja", "ja" to "ja",
        "korean" to "ko", "kor" to "ko", "ko" to "ko", "chinese" to "zh", "chi" to "zh", "zho" to "zh", "zh" to "zh",
        "russian" to "ru", "rus" to "ru", "ru" to "ru", "arabic" to "ar", "ara" to "ar", "ar" to "ar",
        "hindi" to "hi", "hin" to "hi", "swedish" to "sv", "swe" to "sv", "sv" to "sv", "norwegian" to "no",
        "nor" to "no", "no" to "no", "danish" to "da", "dan" to "da", "da" to "da", "finnish" to "fi", "fin" to "fi",
        "fi" to "fi", "polish" to "pl", "pol" to "pl", "pl" to "pl", "turkish" to "tr", "tur" to "tr", "tr" to "tr",
        "greek" to "el", "gre" to "el", "ell" to "el", "el" to "el", "hebrew" to "he", "heb" to "he", "he" to "he",
        "czech" to "cs", "cze" to "cs", "ces" to "cs", "cs" to "cs", "hungarian" to "hu", "hun" to "hu", "hu" to "hu",
        "indonesian" to "id", "ind" to "id", "thai" to "th", "tha" to "th", "th" to "th", "vietnamese" to "vi",
        "vie" to "vi", "vi" to "vi", "ukrainian" to "uk", "ukr" to "uk", "uk" to "uk", "romanian" to "ro",
        "rum" to "ro", "ron" to "ro", "ro" to "ro",
    )

    /** Matches sidecars among all [paths] of one scan. */
    fun match(paths: Collection<String>): Sidecars {
        val allPaths = paths.toSet()
        val byDirectory = paths.groupBy { it.substringBeforeLast('/', "") }
        val videosByDirectory = byDirectory.mapValues { (_, files) -> files.filter { ScanRules.roleOf(it.fileName()) == FileRole.VIDEO } }
        val subtitles = mutableListOf<SubtitleSidecar>()
        val artwork = mutableListOf<ArtworkSidecar>()

        for ((directory, files) in byDirectory) {
            val videos = videosByDirectory[directory].orEmpty()
            for (file in files) {
                when (ScanRules.roleOf(file.fileName())) {
                    FileRole.SUBTITLE -> subtitles += matchSubtitle(file, directory, allPaths, videos, videosByDirectory)
                    FileRole.IMAGE -> matchArtwork(file, directory, videos)?.let { artwork += it }
                    else -> Unit
                }
            }
        }
        return Sidecars(subtitles, artwork)
    }

    private fun matchSubtitle(
        path: String,
        directory: String,
        allPaths: Set<String>,
        videos: List<String>,
        videosByDirectory: Map<String, List<String>>,
    ): List<SubtitleSidecar> {
        val format = SubtitleFormat.fromExtension(path.substringAfterLast('.')) ?: return emptyList()
        // VobSub: the .idx file is the subtitle entry point; its .sub companion is not listed separately.
        if (path.endsWith(".sub", ignoreCase = true) && path.substringBeforeLast('.') + ".idx" in allPaths) return emptyList()
        val stem = path.fileName().substringBeforeLast('.')

        // Same folder: "<video stem>[.lang][.forced|.sdh].srt"
        val owner = videos
            .map { it to it.fileName().substringBeforeLast('.') }
            .filter { (_, videoStem) -> stem == videoStem || stem.startsWith("$videoStem.") }
            .maxByOrNull { (_, videoStem) -> videoStem.length }
        if (owner != null) {
            val suffix = stem.removePrefix(owner.second).trimStart('.')
            return listOf(sidecar(owner.first, path, format, suffix))
        }
        if (videos.size == 1 && directory.isNotEmpty() && stem.split('.', ' ', '_', '-').any { languages.containsKey(it.lowercase()) }) {
            return listOf(sidecar(videos.single(), path, format, stem))
        }

        // "Subs/English.srt" or "Subs/<video stem>/2_English.srt" next to a single video.
        val segments = directory.split('/')
        val subsIndex = segments.indexOfLast { it.lowercase() in subtitleFolders }
        if (subsIndex >= 0) {
            val videoDir = segments.take(subsIndex).joinToString("/")
            val candidates = videosByDirectory[videoDir].orEmpty()
            val video = if (candidates.size == 1) {
                candidates.single()
            } else {
                val named = segments.getOrNull(subsIndex + 1)
                candidates.firstOrNull { it.fileName().substringBeforeLast('.') == named }
            }
            if (video != null) return listOf(sidecar(video, path, format, stem))
        }
        return emptyList()
    }

    private fun sidecar(video: String, path: String, format: SubtitleFormat, descriptor: String): SubtitleSidecar {
        val tokens = descriptor.lowercase().split('.', ' ', '_', '-', '[', ']', '(', ')').filter { it.isNotEmpty() }
        return SubtitleSidecar(
            videoPath = video,
            subtitlePath = path,
            format = format,
            language = tokens.firstNotNullOfOrNull { languages[it] },
            forced = "forced" in tokens || "foreign" in tokens,
            hearingImpaired = tokens.any { it == "sdh" || it == "cc" || it == "hi" },
        )
    }

    private fun matchArtwork(path: String, directory: String, videos: List<String>): ArtworkSidecar? {
        val stem = path.fileName().substringBeforeLast('.')
        val lower = stem.lowercase()
        seasonArtwork.matchEntire(lower)?.let { m ->
            val season = if (m.groupValues[1] == "specials") 0 else m.groupValues[1].toInt()
            val kind = when (m.groupValues[2]) {
                "banner" -> ArtworkKind.BANNER
                "fanart" -> ArtworkKind.BACKDROP
                "landscape", "thumb" -> ArtworkKind.THUMBNAIL
                else -> ArtworkKind.POSTER
            }
            return ArtworkSidecar(ArtworkScope.SeasonOf(directory, season), kind, path)
        }
        artworkNames[lower]?.let { return ArtworkSidecar(ArtworkScope.Directory(directory), it, path) }

        // "<video stem>-poster.jpg", "<video stem>-thumb.jpg", or "<video stem>.jpg"
        for (video in videos) {
            val videoStem = video.fileName().substringBeforeLast('.')
            if (stem == videoStem) return ArtworkSidecar(ArtworkScope.Video(video), ArtworkKind.THUMBNAIL, path)
            if (stem.startsWith("$videoStem-")) {
                val kind = artworkNames[stem.removePrefix("$videoStem-").lowercase()] ?: continue
                return ArtworkSidecar(ArtworkScope.Video(video), kind, path)
            }
        }
        return null
    }

    private fun String.fileName(): String = substringAfterLast('/')
}
