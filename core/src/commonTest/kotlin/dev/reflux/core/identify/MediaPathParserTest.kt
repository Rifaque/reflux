package dev.reflux.core.identify

import dev.reflux.core.model.CalendarDate
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.VideoCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaPathParserTest {
    private val parser = MediaPathParser(maxYear = 2026)

    private fun parse(path: String): ParsedMedia = parser.parse(path) ?: error("not parsed: $path")

    private fun assertMovie(path: String, title: String, year: Int?) {
        val parsed = parse(path)
        assertEquals(ParsedKind.MOVIE, parsed.kind, "kind of $path")
        assertEquals(title, parsed.title, "title of $path")
        assertEquals(year, parsed.year, "year of $path")
    }

    private fun assertEpisode(path: String, show: String, season: Int?, episode: Int?, year: Int? = null) {
        val parsed = parse(path)
        assertEquals(ParsedKind.EPISODE, parsed.kind, "kind of $path")
        assertEquals(show, parsed.title, "show of $path")
        assertEquals(season, parsed.season, "season of $path")
        assertEquals(episode, parsed.episode, "episode of $path")
        assertEquals(year, parsed.year, "year of $path")
    }

    @Test
    fun ignoresNonVideoFiles() {
        assertNull(parser.parse("Movies/poster.jpg"))
        assertNull(parser.parse("Movies/Interstellar (2014).nfo"))
    }

    @Test
    fun cleanMovieNames() {
        assertMovie("Interstellar (2014).mkv", "Interstellar", 2014)
        assertMovie("Movies/Dune (2021).mkv", "Dune", 2021)
        assertMovie("Oppenheimer (2023).mp4", "Oppenheimer", 2023)
        assertMovie("Amélie (2001).mkv", "Amélie", 2001)
    }

    @Test
    fun sceneReleaseNames() {
        assertMovie("The.Dark.Knight.2008.1080p.BluRay.x264-GROUP.mkv", "The Dark Knight", 2008)
        assertMovie("the.matrix.1999.2160p.uhd.bluray.x265-grp.mkv", "The Matrix", 1999)
        assertMovie("Mad_Max_Fury_Road_2015_720p.mkv", "Mad Max Fury Road", 2015)
        assertMovie("Inception 2010 1080p WEB-DL DDP5.1 H.264.mkv", "Inception", 2010)
    }

    @Test
    fun numbersInTitles() {
        assertMovie("2001 A Space Odyssey (1968).mkv", "2001 A Space Odyssey", 1968)
        assertMovie("Blade.Runner.2049.2017.1080p.mkv", "Blade Runner 2049", 2017)
        assertMovie("1917 (2019).mkv", "1917", 2019)
        assertMovie("1917.mkv", "1917", null)
        assertMovie("2012.2009.720p.mkv", "2012", 2009)
        assertMovie("Blade Runner 2049.mkv", "Blade Runner 2049", null)
    }

    @Test
    fun ambiguousWordsDoNotEndTitles() {
        assertMovie("Charlotte's Web (2006).mkv", "Charlotte's Web", 2006)
        assertMovie("Charlotte's Web.mkv", "Charlotte's Web", null)
        assertMovie("Mad Max (1979).mkv", "Mad Max", 1979)
    }

    @Test
    fun folderProvidesYearAndCuratedTitle() {
        assertMovie("Movies/Interstellar (2014)/interstellar.1080p.mkv", "Interstellar", 2014)
        assertMovie("Movies/Mr. Nobody (2009)/Mr.Nobody.mkv", "Mr. Nobody", 2009)
        val generic = parse("Movies/Heat (1995)/movie.mkv")
        assertEquals("Heat", generic.title)
        assertEquals(1995, generic.year)
        assertTrue(IdentificationSignal.TITLE_FROM_FOLDER in generic.confidence.signals)
    }

    @Test
    fun unrelatedFolderDoesNotOverrideFile() {
        assertMovie("Movies/Nolan/Memento (2000).mkv", "Memento", 2000)
        assertMovie("Stuff/Weird Folder (1999)/Arrival.2016.mkv", "Arrival", 2016)
    }

    @Test
    fun genericFileWithoutUsefulFolderIsLowConfidence() {
        val parsed = parse("movie.mkv")
        assertEquals(ConfidenceLevel.LOW, parsed.confidence.level)
        assertTrue(IdentificationSignal.WEAK_TITLE in parsed.confidence.signals)
    }

    @Test
    fun editionsAndParts() {
        val cut = parse("Blade Runner (1982) Final Cut 1080p.mkv")
        assertEquals("Blade Runner", cut.title)
        assertEquals(1982, cut.year)
        assertEquals("Final Cut", cut.edition)

        val plex = parse("Movies/Aliens (1986) {edition-Special Edition}.mkv")
        assertEquals("Aliens", plex.title)
        assertEquals("Special Edition", plex.edition)

        val directors = parse("Kingdom.of.Heaven.2005.Directors.Cut.1080p.BluRay.mkv")
        assertEquals("Kingdom of Heaven", directors.title)
        assertEquals("Director's Cut", directors.edition)

        val part = parse("Seven Samurai (1954) cd2.avi")
        assertEquals("Seven Samurai", part.title)
        assertEquals(2, part.part)

        assertMovie("Harry Potter and the Deathly Hallows Part 1 (2010).mkv", "Harry Potter and the Deathly Hallows Part 1", 2010)
    }

    @Test
    fun externalIdHints() {
        val parsed = parse("Movies/Interstellar (2014) {tmdb-157336}/Interstellar (2014).mkv")
        assertEquals("Interstellar", parsed.title)
        assertEquals(mapOf("tmdb" to "157336"), parsed.externalIds)
        assertEquals(ConfidenceLevel.HIGH, parsed.confidence.level)
    }

    @Test
    fun standardEpisodeMarkers() {
        assertEpisode("TV/Breaking Bad/Season 1/Breaking.Bad.S01E02.720p.mkv", "Breaking Bad", 1, 2)
        assertEpisode("Breaking.Bad.S05E14.Ozymandias.1080p.mkv", "Breaking Bad", 5, 14)
        assertEpisode("The Office (US)/Season 2/The Office (US) - 2x05 - Halloween.mkv", "The Office (US)", 2, 5)
        assertEpisode("Shows/Fargo/fargo s1e1.mkv", "Fargo", 1, 1)
        assertEpisode("Lost - Season 1 Episode 4.avi", "Lost", 1, 4)
    }

    @Test
    fun episodeTitlesAreExtracted() {
        assertEquals("Ozymandias", parse("Breaking.Bad.S05E14.Ozymandias.1080p.mkv").episodeTitle)
        assertEquals("Pilot", parse("Show - S01E01 - Pilot.mkv").episodeTitle)
        assertNull(parse("Show.S01E01.1080p.WEB.h264-GRP.mkv").episodeTitle)
    }

    @Test
    fun multiEpisodeFiles() {
        val a = parse("Show/Season 1/Show - S01E01-E02 - Pilot.mkv")
        assertEquals(1, a.episode)
        assertEquals(2, a.episodeEnd)
        assertEquals(3, parse("Show.S01E01E02E03.mkv").episodeEnd)
        assertEquals(2, parse("Show S01E01-02.mkv").episodeEnd)
        assertNull(parse("Show - S01E01 - 1080p.mkv").episodeEnd)
    }

    @Test
    fun showFolderWinsOverAbbreviatedFileName() {
        assertEpisode("TV/Marvel's Agents of S.H.I.E.L.D/Season 1/S01E01.mkv", "Marvel's Agents of S.H.I.E.L.D", 1, 1)
        assertEpisode("TV/Doctor Who (2005)/Season 1/Doctor.Who.2005.S01E01.mkv", "Doctor Who", 1, 1, year = 2005)
        assertEpisode("Mr. Robot/Mr.Robot.S01E01.mkv", "Mr. Robot", 1, 1)
    }

    @Test
    fun unrelatedFolderIsNotTheShow() {
        assertEpisode("Stuff/Breaking.Bad.S01E01.mkv", "Breaking Bad", 1, 1)
        assertEpisode("Downloads/Breaking.Bad.S01E01.720p-GRP/Breaking.Bad.S01E01.720p-GRP.mkv", "Breaking Bad", 1, 1)
    }

    @Test
    fun seasonPackReleaseFolders() {
        assertEpisode(
            "TV/Breaking.Bad.S02.1080p.BluRay.x264-GRP/Breaking.Bad.S02E03.1080p.mkv",
            "Breaking Bad", 2, 3,
        )
    }

    @Test
    fun bareEpisodeNamesInsideSeasonFolders() {
        assertEpisode("Firefly/Season 1/01 - Serenity.mkv", "Firefly", 1, 1)
        assertEquals("Serenity", parse("Firefly/Season 1/01 - Serenity.mkv").episodeTitle)
        assertEpisode("Firefly/Season 1/Episode 3.mkv", "Firefly", 1, 3)
        assertEpisode("Firefly/Season 1/103.mkv", "Firefly", 1, 3)
        assertEpisode("Firefly/S02/E05.mkv", "Firefly", 2, 5)
        assertEpisode("Firefly/Specials/01 - Here's How It Was.mkv", "Firefly", 0, 1)
        assertEpisode("Dark/Staffel 2/Dark - E04.mkv", "Dark", 2, 4)
    }

    @Test
    fun episodeOnlyMarkersNeedShowContext() {
        assertMovie("Star Wars Episode 2 Attack of the Clones (2002).mkv", "Star Wars Episode 2 Attack of the Clones", 2002)
    }

    @Test
    fun absoluteNumberedAnime() {
        val parsed = parse("Anime/[SubsPlease] Jujutsu Kaisen - 24 (1080p) [A1B2C3D4].mkv")
        assertEquals(ParsedKind.EPISODE, parsed.kind)
        assertEquals("Jujutsu Kaisen", parsed.title)
        assertEquals(24, parsed.absoluteEpisode)
        assertEquals(1, parsed.season)
        assertEquals(1080, parsed.stream.video?.height)
    }

    @Test
    fun datedEpisodes() {
        val parsed = parse("The.Daily.Show.2024.03.14.Guest.Name.720p.WEB.mkv")
        assertEquals(ParsedKind.EPISODE, parsed.kind)
        assertEquals("The Daily Show", parsed.title)
        assertEquals(CalendarDate(2024, 3, 14), parsed.airDate)
        assertEquals("Guest Name", parsed.episodeTitle)
    }

    @Test
    fun showsWithNumericNames() {
        assertEpisode("1923/Season 1/1923.S01E01.mkv", "1923", 1, 1)
        assertEpisode("The.100.S01E01.mkv", "The 100", 1, 1)
    }

    @Test
    fun extrasSamplesAndDiscs() {
        assertEquals(ParsedKind.EXTRA, parse("Movies/Heat (1995)/Extras/Making Of.mkv").kind)
        assertEquals(ParsedKind.EXTRA, parse("Movies/Heat (1995)/Heat-trailer.mkv").kind)
        assertEquals(ParsedKind.EXTRA, parse("Movies/Heat (1995)/Behind The Scenes/Interview.mkv").kind)
        assertEquals(ParsedKind.SAMPLE, parse("Heat.1995.1080p/Heat.1995.1080p.sample.mkv").kind)
        assertEquals(ParsedKind.DISC_STRUCTURE, parse("Heat (1995)/BDMV/STREAM/00001.m2ts").kind)
    }

    @Test
    fun technicalHints() {
        val parsed = parse("Dune.Part.Two.2024.2160p.UHD.BluRay.REMUX.DV.HDR10.HEVC.TrueHD.7.1.Atmos-GRP.mkv")
        assertEquals("Dune Part Two", parsed.title)
        assertEquals(2024, parsed.year)
        val video = parsed.stream.video!!
        assertEquals(VideoCodec.HEVC, video.codec)
        assertEquals(2160, video.height)
        assertEquals(DynamicRange.DOLBY_VISION, video.dynamicRange)
        assertEquals(10, video.bitDepth)
        val audio = parsed.stream.audio.single()
        assertEquals(AudioCodec.TRUEHD, audio.codec)
        assertEquals(8, audio.channels)
        assertTrue(audio.atmos)
        assertEquals(Container.MATROSKA, parsed.stream.container)
    }

    @Test
    fun hintsNeverComeFromTitleText() {
        val parsed = parse("The Web (2010).mp4")
        assertEquals("The Web", parsed.title)
        assertNull(parsed.stream.video)
        assertEquals(Container.MP4, parsed.stream.container)
    }

    @Test
    fun casingIsNormalizedOnlyForSingleCaseNames() {
        assertMovie("THE.GODFATHER.1972.mkv", "The Godfather", 1972)
        assertMovie("the lord of the rings the two towers (2002).mkv", "The Lord of the Rings the Two Towers", 2002)
        assertMovie("WALL·E (2008).mkv", "WALL·E", 2008)
        assertMovie("Rocky II (1979).mkv", "Rocky II", 1979)
    }

    @Test
    fun confidenceReflectsEvidence() {
        assertEquals(ConfidenceLevel.HIGH, parse("Movies/Heat (1995)/Heat (1995).mkv").confidence.level)
        assertEquals(ConfidenceLevel.HIGH, parse("TV/Lost/Season 1/Lost.S01E01.mkv").confidence.level)
        assertEquals(ConfidenceLevel.MEDIUM, parse("Some Film.mkv").confidence.level)
    }
}
