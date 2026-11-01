package dev.reflux.core.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackComparisonTest {
    private val original = PlannedPlayback(
        PlanOutcome.PLAYS, DeliveryMode.DIRECT_PLAY, VideoPlan.ORIGINAL_HARDWARE_DECODE, AudioPlan.ORIGINAL_LOCAL_DECODE, SubtitlePlan.NONE, RangePlan.PRESERVED, audioIndex = 0,
    )
    private val audioConverted = original.copy(delivery = DeliveryMode.AUDIO_CONVERSION, audio = AudioPlan.CONVERT)

    private fun actual(delivery: DeliveryMode, video: StreamFate, audio: StreamFate, burned: Boolean = false) =
        PlaybackEvidence("test", null, delivery, video, audio, subtitleTriggeredConversion = burned)

    private val playing = PlayerState(status = PlayerStatus.PLAYING)

    @Test
    fun aPlanThatHappenedHasNoMismatch() {
        val compared = PlaybackComparison.of(original, actual(DeliveryMode.DIRECT_PLAY, StreamFate.PRESERVED, StreamFate.PRESERVED), playing)
        assertEquals(emptySet(), compared.mismatches)
        assertEquals(emptySet(), compared.unconfirmed)
    }

    @Test
    fun aServerTranscodeOfAPlannedOriginalIsCaught() {
        val compared = PlaybackComparison.of(original, actual(DeliveryMode.VIDEO_TRANSCODE, StreamFate.CONVERTED, StreamFate.PRESERVED), playing)
        assertEquals(setOf(PlanMismatch.DELIVERY, PlanMismatch.VIDEO), compared.mismatches)
        assertTrue("MISMATCH" in compared.summary())
    }

    @Test
    fun anAudioConversionIsConfirmedAsOne() {
        val compared = PlaybackComparison.of(audioConverted, actual(DeliveryMode.AUDIO_CONVERSION, StreamFate.PRESERVED, StreamFate.CONVERTED), playing)
        assertEquals(emptySet(), compared.mismatches)
    }

    @Test
    fun whatNobodyCouldSeeIsUnconfirmedAndNotAMismatch() {
        val compared = PlaybackComparison.of(original, actual(DeliveryMode.UNKNOWN, StreamFate.UNKNOWN, StreamFate.UNKNOWN), playing)
        assertEquals(emptySet(), compared.mismatches)
        assertEquals(setOf(PlanPart.CONTAINER, PlanPart.VIDEO, PlanPart.AUDIO), compared.unconfirmed)
    }

    @Test
    fun anUnplannedBurnInIsCaughtAndAPlannedOneIsNot() {
        val burning = actual(DeliveryMode.VIDEO_TRANSCODE, StreamFate.CONVERTED, StreamFate.CONVERTED, burned = true)
        val other = audioConverted.copy(delivery = DeliveryMode.VIDEO_TRANSCODE, video = VideoPlan.TRANSCODE)
        assertTrue(PlanMismatch.SUBTITLES in PlaybackComparison.of(other, burning, playing).mismatches)
        val planned = other.copy(subtitles = SubtitlePlan.BURN_IN)
        assertEquals(emptySet(), PlaybackComparison.of(planned, burning, playing).mismatches)
    }

    @Test
    fun theDecoderInUseIsHeldToThePlan() {
        val software = playing.copy(videoDecoder = DecoderInUse("c2.android.hevc.decoder", hardware = false))
        assertEquals(setOf(PlanMismatch.DECODER), PlaybackComparison.of(original, null, software).mismatches)
        val hardware = playing.copy(videoDecoder = DecoderInUse("d3d11va", hardware = true))
        assertEquals(emptySet(), PlaybackComparison.of(original, null, hardware).mismatches)
        val unknown = playing.copy(videoDecoder = DecoderInUse("x", hardware = null))
        assertEquals(emptySet(), PlaybackComparison.of(original, null, unknown).mismatches, "an unknown decoder is no mismatch")
    }

    @Test
    fun anotherTrackThanPlannedIsCaught() {
        val tracks = listOf(PlayerTrack("a", TrackType.AUDIO, position = 0), PlayerTrack("b", TrackType.AUDIO, position = 1))
        val wrong = playing.copy(tracks = tracks, selectedAudioId = "b")
        assertEquals(setOf(PlanMismatch.AUDIO_TRACK), PlaybackComparison.of(original, null, wrong).mismatches)
        assertEquals(emptySet(), PlaybackComparison.of(original, null, wrong.copy(selectedAudioId = "a")).mismatches)
        assertEquals(emptySet(), PlaybackComparison.of(audioConverted, null, wrong).mismatches, "a converted stream has its one track")
    }

    @Test
    fun aSourceThatKeepsNoAccountStillHasTheEnginesSide() {
        val compared = PlaybackComparison.of(original, null, playing)
        assertEquals(emptySet(), compared.mismatches)
        assertEquals(emptySet(), compared.unconfirmed)
    }
}
