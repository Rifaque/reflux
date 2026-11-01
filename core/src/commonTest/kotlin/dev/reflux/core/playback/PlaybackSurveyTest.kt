package dev.reflux.core.playback

import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaLocation
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.SourceId
import dev.reflux.core.model.StreamInfoOrigin
import dev.reflux.core.model.VersionId
import dev.reflux.core.source.SourceCapability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackSurveyTest {
    private val viewer = TrackPreferences(listOf("en"))
    private val server = setOf(SourceCapability.SERVER_REMUX, SourceCapability.SERVER_AUDIO_CONVERSION, SourceCapability.SERVER_TRANSCODE, SourceCapability.SERVER_SUBTITLE_EXTRACTION, SourceCapability.SERVER_BURN_IN)
    private val h264 = VideoStream(VideoCodec.H264, 1920, 1080, 8, level = 41)
    private val client = Devices.laptop1080
    private val noDts = client.copy(audioDecoders = client.audioDecoders - AudioCodec.DTS)

    private fun version(id: String, video: VideoStream = h264, audio: AudioCodec = AudioCodec.AAC, subtitles: List<SubtitleFormat> = emptyList()) = MediaVersion(
        VersionId(id), MediaId(id), MediaLocation(SourceId("s"), "$id.mkv"), 1, 0,
        StreamInfo(Container.MATROSKA, video, listOf(AudioStream(audio, 6, "en", default = true)), subtitles.map { SubtitleStream(it, "en") }),
        StreamInfoOrigin.SOURCE,
    )

    @Test
    fun theSameKindOfMediaIsOneCase() {
        val cases = PlaybackSurvey.of(listOf(version("a"), version("b"), version("c", audio = AudioCodec.AC3)), viewer, server, client)
        assertEquals(listOf(2, 1), cases.map { it.versions })
        assertEquals("MATROSKA H264/8 SDR + AAC 6ch", cases.first().media)
    }

    @Test
    fun aCaseCarriesThePlannersAnswerUnchanged() {
        val film = version("a", audio = AudioCodec.DTS)
        val case = PlaybackSurvey.of(listOf(film), viewer, server, noDts).single()
        assertEquals(PlaybackPlanner.plan(PlaybackRequirements(film, viewer, server, subtitle = SubtitleChoice.Off), noDts), case.plan)
        assertEquals(DeliveryMode.AUDIO_CONVERSION, case.plan.delivery)
    }

    @Test
    fun oneKindOfMediaWithTwoPlansIsTwoCases() {
        // A level past what the decoder declares changes the plan, though it is left out of the description.
        val limited = client.copy(videoDecoders = listOf(VideoDecoderSupport(VideoCodec.H264, 1920, 1080, 8, hardware = true, profiles = mapOf(VideoProfile.HIGH to 41))))
        val profiled = h264.copy(profile = VideoProfile.HIGH)
        val cases = PlaybackSurvey.of(listOf(version("a", profiled), version("b", profiled.copy(level = 51))), viewer, server, limited)
        assertEquals(2, cases.size, "${cases.map { it.summary() }}")
        assertEquals(1, cases.map { it.media }.distinct().size)
    }

    @Test
    fun eachSubtitleFormatIsAskedAboutByChoosingItsFirstTrack() {
        val film = version("a", subtitles = listOf(SubtitleFormat.SRT, SubtitleFormat.PGS, SubtitleFormat.PGS))
        val cases = PlaybackSurvey.of(listOf(film), viewer, server, client)
        assertEquals(listOf(null, SubtitleFormat.PGS, SubtitleFormat.SRT), cases.map { it.subtitleFormat }.sortedBy { it?.name })
        assertEquals(SubtitleChoice.Explicit(1), cases.single { it.subtitleFormat == SubtitleFormat.PGS }.subtitle)
    }

    @Test
    fun theWalkKeepsEveryCaseWithoutSubtitlesAndEachSubtitleOnce() {
        val films = listOf(version("a", subtitles = listOf(SubtitleFormat.PGS)), version("b", audio = AudioCodec.AC3, subtitles = listOf(SubtitleFormat.PGS)))
        val cases = PlaybackSurvey.of(films, viewer, server, client)
        assertEquals(4, cases.size)
        val walk = PlaybackSurvey.walk(cases)
        assertEquals(3, walk.size, "the second PGS plan is the same way of carrying it")
        assertEquals(1, walk.count { it.subtitleFormat == SubtitleFormat.PGS })
    }

    @Test
    fun aCarriedSubtitleWalksOncePerWayOfCarryingIt() {
        // The same format, drawn from the file on one version and from a conversion on the other.
        val films = listOf(version("a", subtitles = listOf(SubtitleFormat.PGS)), version("b", audio = AudioCodec.DTS, subtitles = listOf(SubtitleFormat.PGS)))
        val walk = PlaybackSurvey.walk(PlaybackSurvey.of(films, viewer, server, noDts))
        assertEquals(2, walk.count { it.subtitleFormat == SubtitleFormat.PGS })
    }

    @Test
    fun theOrderIsTheSameOnEveryRun() {
        val films = (1..5).map { version("f$it", audio = if (it % 2 == 0) AudioCodec.AC3 else AudioCodec.AAC) }
        val once = PlaybackSurvey.of(films, viewer, server, client)
        assertEquals(once, PlaybackSurvey.of(films.reversed(), viewer, server, client))
        assertTrue(once.zipWithNext().all { (a, b) -> a.versions >= b.versions })
    }
}
