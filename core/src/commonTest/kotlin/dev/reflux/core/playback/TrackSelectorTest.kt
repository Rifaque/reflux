package dev.reflux.core.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackSelectorTest {
    private val english = TrackPreferences(listOf("en"))

    private fun audio(id: String, language: String?, default: Boolean = false, channels: Int = 2) =
        PlayerTrack(id, TrackType.AUDIO, language = language, default = default, channels = channels)

    private fun sub(id: String, language: String?, forced: Boolean = false, sdh: Boolean = false, default: Boolean = false) =
        PlayerTrack(id, TrackType.SUBTITLE, language = language, forced = forced, hearingImpaired = sdh, default = default)

    @Test
    fun audioPrefersTheUsersLanguage() {
        val tracks = listOf(audio("1", "jpn", default = true), audio("2", "eng"), audio("3", "eng", channels = 6))
        assertEquals("3", TrackSelector.selectAudio(tracks, english)?.id)
    }

    @Test
    fun audioFallsBackToTheFilesDefault() {
        val tracks = listOf(audio("1", "jpn"), audio("2", "fre", default = true))
        assertEquals("2", TrackSelector.selectAudio(tracks, english)?.id)
        assertEquals("1", TrackSelector.selectAudio(listOf(audio("1", null), audio("2", null)), english)?.id)
    }

    @Test
    fun foreignAudioGetsFullSubtitles() {
        val tracks = listOf(audio("a", "jpn"), sub("s1", "eng", sdh = true), sub("s2", "eng"), sub("s3", "fre"))
        val audio = TrackSelector.selectAudio(tracks, english)
        assertEquals("s2", TrackSelector.selectSubtitle(tracks, audio, english)?.id)
    }

    @Test
    fun nativeAudioGetsOnlyForcedSubtitles() {
        val tracks = listOf(audio("a", "eng"), sub("full", "eng"), sub("forced", "eng", forced = true))
        val audio = TrackSelector.selectAudio(tracks, english)
        assertEquals("forced", TrackSelector.selectSubtitle(tracks, audio, english)?.id)
        assertNull(TrackSelector.selectSubtitle(listOf(audio("a", "eng"), sub("full", "eng")), audio, english))
    }

    @Test
    fun modesOverrideTheDefault() {
        val tracks = listOf(audio("a", "eng"), sub("full", "eng"), sub("forced", "eng", forced = true))
        val audio = tracks.first()
        assertEquals("full", TrackSelector.selectSubtitle(tracks, audio, english.copy(subtitleMode = SubtitleMode.ALWAYS))?.id)
        assertNull(TrackSelector.selectSubtitle(tracks, audio, english.copy(subtitleMode = SubtitleMode.OFF)))
    }

    @Test
    fun languageCodesAreNormalized() {
        assertTrue(TrackSelector.sameLanguage("eng", "en-US"))
        assertTrue(TrackSelector.sameLanguage("ger", "deu"))
    }
}
