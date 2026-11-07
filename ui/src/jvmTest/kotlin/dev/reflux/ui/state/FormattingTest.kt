package dev.reflux.ui.state

import dev.reflux.core.model.Episode
import dev.reflux.core.model.MediaLocation
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.SourceId
import dev.reflux.core.model.StreamInfoOrigin
import dev.reflux.core.model.VersionId
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoStream
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.WatchState
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.AudioStream
import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.PlayerTrack
import dev.reflux.core.playback.TrackType
import dev.reflux.core.search.SmartQuery
import dev.reflux.core.model.MediaKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormattingTest {
    private fun version(stream: StreamInfo, edition: String? = null) = MediaVersion(
        VersionId("v"), MediaId("m"), MediaLocation(SourceId("s"), "a.mkv"), 0, 0, stream, StreamInfoOrigin.PROBE, edition,
    )

    @Test
    fun versionsLeadWithWhatTheyAreChosenBy() {
        val uhd = version(
            StreamInfo(
                video = VideoStream(VideoCodec.HEVC, 3840, 2160, dynamicRange = DynamicRange.DOLBY_VISION),
                audio = listOf(AudioStream(AudioCodec.TRUEHD, 8, atmos = true)),
            ),
            edition = "Extended",
        )
        assertEquals(listOf("4K", "Dolby Vision", "Extended"), Formatting.versionQuality(uhd))
        assertEquals("HEVC", Formatting.versionTechnical(uhd).first())
        // A copy known only by its codec still says something first.
        val unknown = version(StreamInfo(video = VideoStream(VideoCodec.H264)))
        assertEquals(listOf("H.264"), Formatting.versionQuality(unknown))
        assertEquals(emptyList(), Formatting.versionTechnical(unknown))
    }

    @Test
    fun clockShowsHoursOnlyWhenNeeded() {
        assertEquals("0:00", Formatting.clock(0))
        assertEquals("2:03", Formatting.clock(123_000))
        assertEquals("1:02:03", Formatting.clock(3_723_000))
        assertEquals("0:00", Formatting.clock(-500))
    }

    @Test
    fun runtimesAndSizesReadNaturally() {
        assertEquals("47m", Formatting.runtime(47))
        assertEquals("2h", Formatting.runtime(120))
        assertEquals("2h 50m", Formatting.runtime(170))
        assertEquals("42.1 GB", Formatting.size(45_204_000_000))
        assertEquals("2 GB", Formatting.size(2L shl 30))
        assertEquals("700 MB", Formatting.size(700L shl 20))
    }

    @Test
    fun resolutionUsesTheNamesPeopleUse() {
        assertEquals("4K", Formatting.resolution(2160))
        assertEquals("1080p", Formatting.resolution(1080))
        assertEquals("SD", Formatting.resolution(480))
        assertNull(Formatting.resolution(null))
        assertNull(Formatting.dynamicRange(DynamicRange.SDR))
        assertEquals("Dolby Vision", Formatting.dynamicRange(DynamicRange.DOLBY_VISION))
    }

    @Test
    fun audioNamesTheFormatLayoutAndAtmos() {
        assertEquals("TrueHD 7.1 Atmos", Formatting.audio(AudioStream(AudioCodec.TRUEHD, channels = 8, atmos = true)))
        assertEquals("AAC Stereo", Formatting.audio(AudioStream(AudioCodec.AAC, channels = 2)))
        assertNull(Formatting.audio(AudioStream()))
    }

    @Test
    fun progressIsShownOnlyForWorksInProgress() {
        val id = MediaId("m1")
        assertNull(Formatting.progress(null))
        assertNull(Formatting.progress(WatchState(id, positionMs = 0, durationMs = 100)))
        assertNull(Formatting.progress(WatchState(id, positionMs = 50, durationMs = 100, completed = true)))
        assertEquals(0.5f, Formatting.progress(WatchState(id, positionMs = 50, durationMs = 100)))
        assertEquals("35 min left", Formatting.remaining(WatchState(id, positionMs = 12 * 60_000L, durationMs = 47 * 60_000L)))
        assertEquals("1h 48m left", Formatting.remaining(WatchState(id, positionMs = 62 * 60_000L, durationMs = 170 * 60_000L)))
    }

    @Test
    fun episodesAreNumberedTheWayTheyAreSpoken() {
        fun episode(season: Int, number: Int?, end: Int? = null) =
            Episode(MediaId("e"), MediaId("s"), MediaId("n"), seasonNumber = season, episodeNumber = number, episodeNumberEnd = end)
        assertEquals("S2 · E5", Formatting.episodeNumber(episode(2, 5)))
        assertEquals("S1 · E1–2", Formatting.episodeNumber(episode(1, 1, 2)))
        assertEquals("Special · E3", Formatting.episodeNumber(episode(0, 3)))
    }

    @Test
    fun searchInterpretationBecomesChips() {
        val query = SmartQuery(kinds = setOf(MediaKind.MOVIE), watched = false, minHeight = 2160, genres = setOf("Science Fiction"), maxRuntimeMinutes = 120, text = "blade")
        assertEquals(listOf("Movies", "Unwatched", "4K", "Science Fiction", "Under 2h", "“blade”"), Formatting.interpretation(query))
    }

    @Test
    fun tracksAreNamedByLanguageAndWhatSetsThemApart() {
        assertEquals("English · 5.1", Formatting.track(PlayerTrack("1", TrackType.AUDIO, language = "en", channels = 6)))
        assertEquals("English · Commentary", Formatting.track(PlayerTrack("2", TrackType.AUDIO, language = "eng", title = "Commentary")))
        assertEquals("Spanish · Forced", Formatting.track(PlayerTrack("3", TrackType.SUBTITLE, language = "es", forced = true)))
        assertEquals("Track 4", Formatting.track(PlayerTrack("4", TrackType.SUBTITLE)))
        assertEquals("1.25×", Formatting.speed(1.25))
        assertEquals("2×", Formatting.speed(2.0))
        assertEquals("0.5×", Formatting.speed(0.5))
    }

    @Test
    fun datesYearsSeasonNamesAndOriginalTitles() {
        assertEquals("Jan 20, 2008", Formatting.date(dev.reflux.core.model.CalendarDate(2008, 1, 20)))
        assertEquals("2008–2013", Formatting.airedYears(2008, 2013))
        assertEquals("2022", Formatting.airedYears(2022, 2022))
        assertEquals("2022", Formatting.airedYears(2022, null))
        assertNull(Formatting.airedYears(null, 2013))
        assertEquals(true, Formatting.genericSeasonName("Season 2", 2))
        assertEquals(true, Formatting.genericSeasonName("Season 02", 2))
        assertEquals(true, Formatting.genericSeasonName("Specials", 0))
        assertEquals(false, Formatting.genericSeasonName("Book One: Water", 1))
        assertEquals(false, Formatting.genericSeasonName("Season 3", 2))
        assertEquals("Le Fabuleux Destin d'Amélie Poulain", Formatting.originalTitle("Le Fabuleux Destin d'Amélie Poulain", "Amélie"))
        assertNull(Formatting.originalTitle("Heat", "Heat"))
        assertNull(Formatting.originalTitle("Mad Max - Fury Road", "Mad Max: Fury Road"), "punctuation is not another name")
        assertNull(Formatting.originalTitle(" ", "Heat"))
    }
}
