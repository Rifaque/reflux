package dev.reflux.ui.state

import dev.reflux.core.model.Episode
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
}
