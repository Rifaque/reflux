package dev.reflux.core.playback

import dev.reflux.core.model.ExternalSubtitle
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaLocation
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.SourceId
import dev.reflux.core.model.StreamInfoOrigin
import dev.reflux.core.model.VersionId
import dev.reflux.core.playback.Support.SUPPORTED
import dev.reflux.core.source.SourceCapability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a viewer chose for a work's subtitles, kept as a description of the track and matched against each version
 * (docs/planning/M10-PLAYER-SPEC.md, section 9): another version, or the next episode, numbers its tracks otherwise.
 */
class SubtitlePreferenceTest {
    private fun track(language: String?, forced: Boolean = false, sdh: Boolean = false) = SubtitleStream(SubtitleFormat.SRT, language, forced = forced, hearingImpaired = sdh)

    private fun version(subtitles: List<SubtitleStream>, external: List<ExternalSubtitle> = emptyList()) = MediaVersion(
        VersionId("v"), MediaId("m"), MediaLocation(SourceId("s"), "film.mkv"), 1, 0,
        StreamInfo(Container.MATROSKA, VideoStream(VideoCodec.H264, 1920, 1080, 8), listOf(AudioStream(AudioCodec.AAC, 2, "en", default = true)), subtitles),
        StreamInfoOrigin.PROBE, externalSubtitles = external,
    )

    private val client = DeviceCapabilities(
        videoDecoders = listOf(VideoDecoderSupport(VideoCodec.H264, 3840, 2160, 8, true, frameAndDepthOnly = true)),
        audioDecoders = setOf(AudioCodec.AAC),
        maxOutputChannels = 8,
        display = DisplayCapabilities(3840, 2160),
        containers = setOf(Container.MATROSKA),
        subtitleFormats = emptySet(),
        subtitles = SubtitleCapabilities(mapOf(SubtitleFormat.SRT to SubtitleRenderer(SUPPORTED, SUPPORTED, SubtitleFidelity.FULL))),
    )

    private fun plan(version: MediaVersion, choice: SubtitleChoice, languages: List<String> = listOf("en")) = PlaybackPlanner.plan(
        PlaybackRequirements(version, TrackPreferences(languages), setOf(SourceCapability.ENUMERATE_FILES), AudioChoice.Automatic, choice), client,
    )

    @Test
    fun aPreferenceIsKeptAsTextAndReadBackAsItWas() {
        for (pref in listOf(SubtitlePreference.OFF, SubtitlePreference(false, "en"), SubtitlePreference(false, "fr", forced = true), SubtitlePreference(false, "en", hearingImpaired = true), SubtitlePreference(false, null))) {
            assertEquals(pref, SubtitlePreference.decode(pref.encode()), pref.encode())
        }
    }

    @Test
    fun whatIsDamagedOrUnknownIsForgottenNotGuessedAt() {
        assertNull(SubtitlePreference.decode(null))
        assertNull(SubtitlePreference.decode(""))
        assertNull(SubtitlePreference.decode("maybe"))
        assertNull(SubtitlePreference.decode("on;forced=1"))
    }

    @Test
    fun aWorksKeyIsItsOwnAndStaysOffStreamPositions() {
        assertEquals("subtitles.work:abc", SubtitlePreference.key("abc"))
    }

    @Test
    fun theTrackTheDescriptionMatchesIsChosenWhateverItsPosition() {
        // The same choice, in two versions that number their tracks differently.
        val first = version(listOf(track("en"), track("fr")))
        val second = version(listOf(track("de"), track("fr"), track("en")))
        val french = SubtitlePreference(false, "fr").choice()
        assertEquals(1, plan(first, french).subtitleIndex)
        assertEquals(1, plan(second, french).subtitleIndex)
        assertEquals(SubtitlePlan.LOCAL_RENDER, plan(second, french).subtitles)
        assertEquals(2, plan(second, SubtitlePreference(false, "eng").choice()).subtitleIndex, "ISO 639-1 and 639-2 are the same language")
    }

    @Test
    fun forcedAndHearingImpairedAreMatchedBeforeTheFirstInTheLanguage() {
        val v = version(listOf(track("en", sdh = true), track("en", forced = true), track("en")))
        assertEquals(2, plan(v, SubtitlePreference(false, "en").choice()).subtitleIndex, "plain is what a plain choice meant")
        assertEquals(1, plan(v, SubtitlePreference(false, "en", forced = true).choice()).subtitleIndex)
        assertEquals(0, plan(v, SubtitlePreference(false, "en", hearingImpaired = true).choice()).subtitleIndex)
        // Forced agrees but there is no forced track with SDH: forced wins over the first.
        assertEquals(1, plan(v, SubtitlePreference(false, "en", forced = true, hearingImpaired = true).choice()).subtitleIndex)
    }

    @Test
    fun embeddedTracksAreMatchedBeforeFiles() {
        val v = version(listOf(track("en")), external = listOf(ExternalSubtitle(MediaLocation(SourceId("s"), "film.en.srt"), SubtitleFormat.SRT, "en")))
        val plan = plan(v, SubtitlePreference(false, "en").choice())
        assertEquals(0, plan.subtitleIndex)
        assertEquals(false, plan.subtitleExternal)
        val onlyFile = plan(version(emptyList(), v.externalSubtitles), SubtitlePreference(false, "en").choice())
        assertTrue(onlyFile.subtitleExternal)
        assertEquals(SubtitlePlan.EXTERNAL, onlyFile.subtitles)
    }

    @Test
    fun aLanguagelessPreferenceMatchesOnlyTracksWithoutOne() {
        val v = version(listOf(track("en"), track(null)))
        assertEquals(1, plan(v, SubtitlePreference(false, null).choice()).subtitleIndex)
        assertEquals(0, plan(v, SubtitlePreference(false, "en").choice()).subtitleIndex)
    }

    @Test
    fun whereNothingMatchesTheChoiceIsTheAutomaticOne() {
        // English audio, English the viewer's language: automatic shows no subtitle, and so does a preference for Spanish nothing has.
        val v = version(listOf(track("en"), track("fr")))
        val unmatched = plan(v, SubtitlePreference(false, "es").choice())
        val automatic = plan(v, SubtitleChoice.Automatic)
        assertEquals(automatic.subtitleIndex, unmatched.subtitleIndex)
        assertEquals(automatic.subtitles, unmatched.subtitles)
        // Where automatic would show one (audio in a language the viewer does not prefer), the unmatched preference gets it too.
        val foreign = plan(v, SubtitlePreference(false, "es").choice(), languages = listOf("de"))
        assertEquals(plan(v, SubtitleChoice.Automatic, languages = listOf("de")).subtitleIndex, foreign.subtitleIndex)
    }

    @Test
    fun offIsAChoiceToo() {
        val v = version(listOf(track("en"), track("fr")))
        val plan = plan(v, SubtitlePreference.OFF.choice(), languages = listOf("de"))
        assertEquals(SubtitlePlan.NONE, plan.subtitles)
        assertNull(plan.subtitleIndex)
    }
}
