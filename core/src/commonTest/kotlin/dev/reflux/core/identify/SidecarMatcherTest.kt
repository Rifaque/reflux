package dev.reflux.core.identify

import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.playback.SubtitleFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SidecarMatcherTest {
    @Test
    fun subtitlesNamedAfterTheVideo() {
        val result = SidecarMatcher.match(
            listOf(
                "Movies/Heat (1995)/Heat (1995).mkv",
                "Movies/Heat (1995)/Heat (1995).srt",
                "Movies/Heat (1995)/Heat (1995).en.forced.srt",
                "Movies/Heat (1995)/Heat (1995).French.sdh.ass",
            ),
        )
        val subs = result.subtitles.associateBy { it.subtitlePath.substringAfterLast('/') }
        assertEquals(3, subs.size)
        assertEquals(null, subs.getValue("Heat (1995).srt").language)
        val forced = subs.getValue("Heat (1995).en.forced.srt")
        assertEquals("en", forced.language)
        assertTrue(forced.forced)
        val french = subs.getValue("Heat (1995).French.sdh.ass")
        assertEquals("fr", french.language)
        assertTrue(french.hearingImpaired)
        assertEquals(SubtitleFormat.ASS, french.format)
    }

    @Test
    fun subtitlesMatchTheLongestVideoStem() {
        val result = SidecarMatcher.match(
            listOf("Show/S01E01.mkv", "Show/S01E01.Extended.mkv", "Show/S01E01.Extended.en.srt"),
        )
        assertEquals("Show/S01E01.Extended.mkv", result.subtitles.single().videoPath)
    }

    @Test
    fun subsFolderNextToSingleVideo() {
        val result = SidecarMatcher.match(
            listOf("Arrival.2016/Arrival.2016.mkv", "Arrival.2016/Subs/English.srt", "Arrival.2016/Subs/Spanish.srt"),
        )
        assertEquals(setOf("en", "es"), result.subtitles.map { it.language }.toSet())
        assertTrue(result.subtitles.all { it.videoPath == "Arrival.2016/Arrival.2016.mkv" })
    }

    @Test
    fun episodeSubsFoldersAreMatchedByName() {
        val result = SidecarMatcher.match(
            listOf("S1/Show.S01E01.mkv", "S1/Show.S01E02.mkv", "S1/Subs/Show.S01E02/2_English.srt"),
        )
        assertEquals("S1/Show.S01E02.mkv", result.subtitles.single().videoPath)
    }

    @Test
    fun vobSubPairsAreListedOnce() {
        val result = SidecarMatcher.match(listOf("M/M.mkv", "M/M.idx", "M/M.sub"))
        assertEquals(listOf("M/M.idx"), result.subtitles.map { it.subtitlePath })
    }

    @Test
    fun artworkScopes() {
        val result = SidecarMatcher.match(
            listOf(
                "Movies/Heat (1995)/Heat (1995).mkv",
                "Movies/Heat (1995)/poster.jpg",
                "Movies/Heat (1995)/fanart.jpg",
                "Movies/Heat (1995)/Heat (1995)-logo.png",
                "TV/Lost/season01-poster.jpg",
                "TV/Lost/season-specials-poster.jpg",
                "TV/Lost/Season 1/Lost.S01E01.mkv",
                "TV/Lost/Season 1/Lost.S01E01-thumb.jpg",
                "TV/Lost/Season 1/random.jpg",
            ),
        )
        val byFile = result.artwork.associateBy { it.imagePath.substringAfterLast('/') }
        assertEquals(ArtworkSidecar(ArtworkScope.Directory("Movies/Heat (1995)"), ArtworkKind.POSTER, "Movies/Heat (1995)/poster.jpg"), byFile["poster.jpg"])
        assertEquals(ArtworkKind.BACKDROP, byFile.getValue("fanart.jpg").kind)
        assertEquals(ArtworkScope.Video("Movies/Heat (1995)/Heat (1995).mkv"), byFile.getValue("Heat (1995)-logo.png").scope)
        assertEquals(ArtworkScope.SeasonOf("TV/Lost", 1), byFile.getValue("season01-poster.jpg").scope)
        assertEquals(ArtworkScope.SeasonOf("TV/Lost", 0), byFile.getValue("season-specials-poster.jpg").scope)
        assertEquals(ArtworkKind.THUMBNAIL, byFile.getValue("Lost.S01E01-thumb.jpg").kind)
        assertTrue("random.jpg" !in byFile)
    }
}
