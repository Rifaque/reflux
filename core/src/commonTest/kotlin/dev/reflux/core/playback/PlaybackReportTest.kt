package dev.reflux.core.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackReportTest {
    private fun plan(
        delivery: DeliveryMode = DeliveryMode.DIRECT_PLAY,
        video: VideoPlan = VideoPlan.ORIGINAL_HARDWARE_DECODE,
        audio: AudioPlan = AudioPlan.ORIGINAL_LOCAL_DECODE,
        subtitles: SubtitlePlan = SubtitlePlan.NONE,
        range: RangePlan = RangePlan.PRESERVED,
        outcome: PlanOutcome = PlanOutcome.PLAYS,
        vararg reasons: PlanReason,
    ) = PlannedPlayback(outcome, delivery, video, audio, subtitles, range, reasons = reasons.toList())

    private val playing = PlayerState(
        status = PlayerStatus.PLAYING,
        tracks = listOf(PlayerTrack("a", TrackType.AUDIO, language = "en", channels = 6, position = 0)),
        selectedAudioId = "a",
        videoDecoder = DecoderInUse("c2.qti.hevc.decoder", true),
    )

    private fun evidence(delivery: DeliveryMode, audio: StreamFate = StreamFate.PRESERVED) =
        PlaybackEvidence("a server stream", null, delivery, if (delivery == DeliveryMode.VIDEO_TRANSCODE) StreamFate.CONVERTED else StreamFate.PRESERVED, audio)

    private fun compared(plan: PlannedPlayback, evidence: PlaybackEvidence?, state: PlayerState = playing) = PlaybackComparison.of(plan, evidence, state)

    private fun text(lines: List<PlaybackReport.Line>, heading: String) = lines.single { it.heading == heading }.text

    @Test
    fun directPlaySaysTheOriginalAndTheDecoder() {
        val plan = plan()
        val lines = PlaybackReport.of(plan, compared(plan, evidence(DeliveryMode.DIRECT_PLAY)), playing)
        assertEquals("The original file, as it is.", text(lines, "How it plays"))
        assertEquals("The original, decoded in hardware (c2.qti.hevc.decoder).", text(lines, "Picture"))
        assertEquals("The original, decoded here (en, 6 channels).", text(lines, "Sound"))
        assertEquals("Sent the original file.", text(lines, "What the server did"))
        assertEquals("As planned.", text(lines, "Planned against actual"))
    }

    @Test
    fun anAudioConversionSaysThePictureIsKept() {
        val plan = plan(DeliveryMode.AUDIO_CONVERSION, audio = AudioPlan.CONVERT, reasons = arrayOf(PlanReason.AUDIO_NOT_PLAYABLE))
        val lines = PlaybackReport.of(plan, compared(plan, evidence(DeliveryMode.AUDIO_CONVERSION, StreamFate.CONVERTED)), playing)
        assertEquals("The server converts the sound; the picture is the original.", text(lines, "How it plays"))
        assertTrue(text(lines, "Sound").startsWith("Converted by the server"))
        assertEquals("Converted the sound, kept the picture.", text(lines, "What the server did"))
    }

    @Test
    fun aTranscodeForSubtitlesSaysSo() {
        val plan = plan(DeliveryMode.VIDEO_TRANSCODE, VideoPlan.TRANSCODE, AudioPlan.CONVERT, SubtitlePlan.BURN_IN, reasons = arrayOf(PlanReason.SUBTITLE_BURNED_IN, PlanReason.AUDIO_NOT_PLAYABLE))
        val lines = PlaybackReport.of(plan, null, playing)
        assertEquals("The server converts the picture, because the subtitles you chose have to be drawn into it.", text(lines, "How it plays"))
        assertTrue(text(lines, "Subtitles").startsWith("Drawn into the picture"))
        assertEquals("Not compared yet.", text(lines, "Planned against actual"))
    }

    @Test
    fun aTranscodeForThePictureSaysWhy() {
        val plan = plan(DeliveryMode.VIDEO_TRANSCODE, VideoPlan.TRANSCODE, reasons = arrayOf(PlanReason.VIDEO_NOT_DECODABLE))
        assertEquals("The server converts the picture, because this device can't decode the original.", text(PlaybackReport.of(plan, null, playing), "How it plays"))
    }

    @Test
    fun aRemuxForSeekingIsPlannedAgainOnceAndSaysTheServerMayNotTell() {
        val plan = plan(DeliveryMode.REMUX, reasons = arrayOf(PlanReason.ORIGINAL_FAILED, PlanReason.SEEKING_LIMITED))
        val lines = PlaybackReport.of(plan, compared(plan, evidence(DeliveryMode.UNKNOWN)), playing)
        assertTrue(text(lines, "How it plays").endsWith("this device can't skip through it as it is."))
        assertEquals("Once, because this device couldn't move to the place you wanted in the original.", text(lines, "Planned again"))
        assertTrue(text(lines, "What the server did").startsWith("It didn't say."))
        assertTrue(text(lines, "Planned against actual").startsWith("Nothing differs; not confirmed"), text(lines, "Planned against actual"))
    }

    @Test
    fun noSoundAndNoPlaybackAreSaid() {
        val silent = plan(audio = AudioPlan.NONE, outcome = PlanOutcome.PLAYS_WITHOUT_AUDIO, reasons = arrayOf(PlanReason.AUDIO_UNAVAILABLE))
        assertEquals("None: no audio track can play here.", text(PlaybackReport.of(silent, null, playing), "Sound"))
        val none = plan(DeliveryMode.UNKNOWN, VideoPlan.NONE, AudioPlan.NONE, outcome = PlanOutcome.CANNOT_PLAY, reasons = arrayOf(PlanReason.VIDEO_NOT_DECODABLE, PlanReason.NO_TRANSCODE_AVAILABLE))
        val lines = PlaybackReport.of(none, null, PlayerState())
        assertTrue(text(lines, "How it plays").startsWith("It can't be played here"))
        assertFalse(lines.any { it.heading == "What the server did" }, "nothing was asked of anyone")
    }

    @Test
    fun aMismatchIsNamed() {
        val plan = plan()
        val lines = PlaybackReport.of(plan, compared(plan, evidence(DeliveryMode.VIDEO_TRANSCODE)), playing)
        assertTrue(text(lines, "Planned against actual").startsWith("Different from the plan: delivery"), text(lines, "Planned against actual"))
    }

    @Test
    fun aLocalFileHasNoServerToAsk() {
        val plan = plan()
        assertEquals("This source keeps no account; it delivers the file.", text(PlaybackReport.of(plan, compared(plan, null), playing), "What the server did"))
    }

    @Test
    fun hdrAndLanguageCompromisesAreSaid() {
        val plan = plan(range = RangePlan.TONE_MAPPED, reasons = arrayOf(PlanReason.AUDIO_OTHER_LANGUAGE, PlanReason.AUDIO_DOWNMIXED))
        val lines = PlaybackReport.of(plan, null, playing)
        assertEquals("HDR, shown as SDR on this screen.", text(lines, "Colour"))
        assertTrue("another one does" in text(lines, "Sound") && "Mixed down" in text(lines, "Sound"))
    }

    @Test
    fun theReportCarriesNoAddressOrKey() {
        val plan = plan(DeliveryMode.AUDIO_CONVERSION, audio = AudioPlan.CONVERT)
        val evidence = PlaybackEvidence("a server stream", "url=https://server/master.m3u8?api_key=0123456789abcdef0123456789abcdef", DeliveryMode.AUDIO_CONVERSION, StreamFate.PRESERVED, StreamFate.CONVERTED)
        val all = PlaybackReport.summary(PlaybackReport.of(plan, compared(plan, evidence), playing))
        assertFalse("http" in all || "api_key" in all || "0123456789abcdef" in all, all)
    }
}
