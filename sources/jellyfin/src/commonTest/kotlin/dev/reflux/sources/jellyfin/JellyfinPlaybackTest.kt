package dev.reflux.sources.jellyfin

import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.net.HttpRequest
import dev.reflux.core.net.HttpResult
import dev.reflux.core.playback.DeliveryMode
import dev.reflux.core.playback.PlaybackEvidence
import dev.reflux.core.playback.StreamFate
import dev.reflux.core.playback.SubtitleHandling
import dev.reflux.core.source.SourceCapability
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The answers are trimmed from what a real Jellyfin 12.2.0 server sent (docs/PLAYBACK.md, section 3). */
class JellyfinPlaybackTest {
    private val path = "items/m1/sources/s1"
    private val requests = mutableListOf<HttpRequest>()
    private var playbackInfo: () -> HttpResult = { HttpResult(200, directPlayAnswer.encodeToByteArray()) }
    private var sessions = "[]"

    private val http = HttpFetcher { request ->
        requests += request
        when {
            "PlaybackInfo" in request.url -> playbackInfo()
            request.url.substringBefore('?').endsWith("/Sessions") -> HttpResult(200, sessions.encodeToByteArray())
            else -> HttpResult(204, ByteArray(0))
        }
    }
    private val source = JellyfinSource(JellyfinCredentials("https://jf.example.com", "srv1", "u1", "tok", "dev1"), http)

    private val subtitleStreams =
        """{"Type":"Subtitle","Index":2,"Codec":"PGSSUB","DeliveryMethod":"Encode"},{"Type":"Subtitle","Index":5,"Codec":"subrip","DeliveryMethod":"External"}"""

    private fun answer(directPlay: Boolean, directStream: Boolean, url: String? = null, streams: String = subtitleStreams) = """
        {"PlaySessionId":"ps1","MediaSources":[{"Id":"s1","SupportsDirectPlay":$directPlay,"SupportsDirectStream":$directStream,"SupportsTranscoding":true,
         ${url?.let { "\"TranscodingUrl\":\"$it\"," }.orEmpty()}"MediaStreams":[{"Type":"Video","Index":0,"Codec":"hevc"},$streams]}]}
    """.trimIndent()

    private val directPlayAnswer = answer(directPlay = true, directStream = true)

    private fun url(reasons: String, subtitle: String = "") =
        "/videos/m1/master.m3u8?VideoCodec=hevc,h264&AudioCodec=aac&ApiKey=secret-key&SubtitleMethod=Encode$subtitle&TranscodeReasons=$reasons"

    private fun parsed(json: String) = assertNotNull(JellyfinPlaybackAnswer.parse(Json.parseToJsonElement(json).jsonObject, "s1"))

    private fun session(videoDirect: Boolean, audioDirect: Boolean, reasons: String) = JellyfinSessionFacts(
        nowPlayingItemId = null,
        playMethod = "Transcode",
        transcoding = JellyfinTranscoding(videoDirect, audioDirect, "hevc", "aac", "ts", "none", reasons.split(' ')),
    )

    private fun evidence(answer: JellyfinPlaybackAnswer?, session: JellyfinSessionFacts?, originalRequested: Boolean = false): PlaybackEvidence =
        JellyfinDelivery.evidence("test", originalRequested, "m1", answer, session, authenticated = true)

    @Test
    fun directPlayNeedsTheServerToListThePlaybackWithNoConversion() {
        val playing = JellyfinSessionFacts(nowPlayingItemId = "M1", playMethod = "DirectPlay", transcoding = null)
        val evidence = evidence(parsed(directPlayAnswer), playing, originalRequested = true)
        assertEquals(DeliveryMode.DIRECT_PLAY, evidence.delivery)
        assertEquals(StreamFate.PRESERVED, evidence.video)
        assertEquals(StreamFate.PRESERVED, evidence.audio)
    }

    @Test
    fun requestingTheOriginalIsNotEvidenceOfDirectPlay() {
        val answer = parsed(directPlayAnswer)
        for (session in listOf(null, JellyfinSessionFacts(null, null, null), JellyfinSessionFacts("another", "DirectPlay", null))) {
            val evidence = evidence(answer, session, originalRequested = true)
            assertEquals(DeliveryMode.UNKNOWN, evidence.delivery, "the server does not list this playback: $session")
            assertEquals(StreamFate.UNKNOWN, evidence.video)
            assertEquals(StreamFate.UNKNOWN, evidence.audio)
        }
    }

    @Test
    fun thePlayMethodTheServerRepeatsIsNotEvidence() {
        val claimed = JellyfinSessionFacts(nowPlayingItemId = "m1", playMethod = "DirectPlay", transcoding = null)
        assertEquals(DeliveryMode.UNKNOWN, evidence(parsed(directPlayAnswer), claimed, originalRequested = false).delivery)
        val converting = session(videoDirect = false, audioDirect = true, "VideoCodecNotSupported").copy(playMethod = "DirectPlay")
        assertEquals(DeliveryMode.VIDEO_TRANSCODE, evidence(parsed(directPlayAnswer), converting, originalRequested = true).delivery)
    }

    @Test
    fun remuxIsBothStreamsCopied() {
        val evidence = evidence(parsed(answer(false, false, url("ContainerNotSupported"))), session(videoDirect = true, audioDirect = true, "ContainerNotSupported"))
        assertEquals(DeliveryMode.REMUX, evidence.delivery)
        assertEquals(StreamFate.PRESERVED, evidence.video)
        assertEquals(StreamFate.PRESERVED, evidence.audio)
    }

    @Test
    fun audioConversionKeepsTheOriginalVideo() {
        val evidence = evidence(parsed(answer(false, false, url("AudioCodecNotSupported"))), session(videoDirect = true, audioDirect = false, "AudioCodecNotSupported"))
        assertEquals(DeliveryMode.AUDIO_CONVERSION, evidence.delivery)
        assertEquals(StreamFate.PRESERVED, evidence.video)
        assertEquals(StreamFate.CONVERTED, evidence.audio)
        assertEquals(listOf("AudioCodecNotSupported"), evidence.reasons)
        assertFalse(evidence.subtitleTriggeredConversion)
    }

    @Test
    fun aVideoTranscodeMayKeepTheOriginalAudio() {
        val kept = evidence(parsed(answer(false, false, url("VideoCodecNotSupported"))), session(videoDirect = false, audioDirect = true, "VideoCodecNotSupported"))
        assertEquals(DeliveryMode.VIDEO_TRANSCODE, kept.delivery)
        assertEquals(StreamFate.CONVERTED, kept.video)
        assertEquals(StreamFate.PRESERVED, kept.audio)
        val both = evidence(null, session(videoDirect = false, audioDirect = false, "VideoCodecNotSupported AudioCodecNotSupported"))
        assertEquals(StreamFate.CONVERTED, both.audio)
    }

    @Test
    fun supportsDirectStreamNeverDecidesTheMode() {
        // A real server said false for each of these, and copied the video in two of them.
        for (flag in listOf(true, false)) {
            val parsed = parsed(answer(false, flag, url("AudioCodecNotSupported")))
            assertEquals(DeliveryMode.AUDIO_CONVERSION, evidence(parsed, session(videoDirect = true, audioDirect = false, "AudioCodecNotSupported")).delivery)
            assertEquals(DeliveryMode.VIDEO_TRANSCODE, evidence(parsed, session(videoDirect = false, audioDirect = true, "VideoCodecNotSupported")).delivery)
            assertEquals(DeliveryMode.UNKNOWN, evidence(parsed, null).delivery, "and alone it says nothing: SupportsDirectStream=$flag")
        }
    }

    @Test
    fun aListedConversionThatDoesNotSayWhatItCopiedIsNotGuessed() {
        val unclear = JellyfinTranscoding(null, null, "hevc", "aac", "ts", null, emptyList())
        assertEquals(DeliveryMode.UNKNOWN, JellyfinDelivery.modeOf(unclear))
    }

    @Test
    fun aSelectedBitmapSubtitleThatForcesAVideoTranscodeIsNamed() {
        // Live: only audio was unsupported, and asking for the PGS track planned a burn-in, reasons unchanged.
        val answer = parsed(answer(false, false, url("AudioCodecNotSupported", "&SubtitleStreamIndex=2")))
        assertEquals(2, answer.burnInIndex)
        val evidence = evidence(answer, session(videoDirect = false, audioDirect = false, "AudioCodecNotSupported"))
        assertEquals(DeliveryMode.VIDEO_TRANSCODE, evidence.delivery)
        assertTrue(evidence.subtitleTriggeredConversion)
        assertEquals(SubtitleHandling.SERVER_BURN_IN, evidence.subtitles.single { it.index == 2 }.handling)
    }

    @Test
    fun theServersOwnSubtitleReasonCountsToo() {
        val evidence = evidence(null, session(videoDirect = false, audioDirect = true, "SubtitleCodecNotSupported"))
        assertTrue(evidence.subtitleTriggeredConversion)
    }

    @Test
    fun subtitleMethodEncodeWithNoTrackSelectedIsNoBurnIn() {
        // Real servers write SubtitleMethod=Encode on URLs that select no subtitle.
        val answer = parsed(answer(false, false, url("VideoCodecNotSupported")))
        assertNull(answer.burnInIndex)
        val evidence = evidence(answer, session(videoDirect = false, audioDirect = true, "VideoCodecNotSupported"))
        assertFalse(evidence.subtitleTriggeredConversion, "the video has its own reason")
        assertEquals(SubtitleHandling.CAPABILITY_UNKNOWN, evidence.subtitles.single { it.index == 2 }.handling)
        val minus = parsed(answer(false, false, url("VideoCodecNotSupported", "&SubtitleStreamIndex=-1")))
        assertNull(minus.burnInIndex)
    }

    @Test
    fun aPgsTrackInAFileIsNotAClaimThatItCanBeDrawn() {
        val playing = JellyfinSessionFacts("m1", "DirectPlay", null)
        val evidence = evidence(parsed(directPlayAnswer), playing, originalRequested = true)
        assertEquals(DeliveryMode.DIRECT_PLAY, evidence.delivery)
        assertEquals(SubtitleHandling.CAPABILITY_UNKNOWN, evidence.subtitles.single { it.format == "PGSSUB" }.handling)
        assertFalse(evidence.subtitleTriggeredConversion)
    }

    @Test
    fun whereAProfileSaysHowSubtitlesTravelThatIsReported() {
        val streams = """{"Type":"Subtitle","Index":2,"Codec":"PGSSUB","DeliveryMethod":"External"},{"Type":"Subtitle","Index":3,"Codec":"subrip","DeliveryMethod":"Embed"},{"Type":"Subtitle","Index":4,"Codec":"ass","DeliveryMethod":"Drop"}"""
        val evidence = evidence(parsed(answer(true, true, streams = streams)), null)
        assertEquals(
            listOf(SubtitleHandling.EXTERNAL, SubtitleHandling.LOCAL_RENDER, SubtitleHandling.CAPABILITY_UNKNOWN),
            evidence.subtitles.map { it.handling },
        )
    }

    @Test
    fun credentialsDoNotPrintTheirToken() {
        val printed = JellyfinCredentials("https://jf.example.com", "srv1", "u1", "secret-token", "dev1").toString()
        assertFalse("secret-token" in printed, printed)
    }

    @Test
    fun theKeyInATranscodingUrlIsNeverPrinted() {
        val answer = parsed(answer(false, false, url("AudioCodecNotSupported")))
        val evidence = evidence(answer, session(videoDirect = true, audioDirect = false, "AudioCodecNotSupported"))
        for (text in listOf(answer.toString(), answer.summary(), evidence.summary(), evidence.toString())) {
            assertFalse("secret-key" in text, text)
        }
    }

    @Test
    fun aPlaybackIsObservedAndReportedAsWhatItIs() = runTest {
        sessions = """[{"DeviceId":"other","NowPlayingItem":{"Id":"x"}},{"DeviceId":"dev1","NowPlayingItem":{"Id":"m1"},"PlayState":{"PlayMethod":"DirectPlay"}}]"""
        source.reportProgress(path, 60_000, paused = false)
        source.reportProgress(path, 70_000, paused = false)

        val posts = requests.filter { it.method == "POST" }
        assertEquals(listOf("Items/m1/PlaybackInfo", "Sessions/Playing", "Sessions/Playing/Progress"), posts.map { it.url.removePrefix("https://jf.example.com/") })
        val asked = posts[0].body!!.decodeToString()
        assertTrue("\"SubtitleStreamIndex\":-1" in asked && "\"EnableTranscoding\":false" in asked, asked)
        assertTrue("\"SubtitleProfiles\":[]" in asked, "no subtitle format is claimed: $asked")
        for (report in posts.drop(1).map { it.body!!.decodeToString() }) {
            assertTrue("\"PlayMethod\":\"DirectPlay\"" in report && "\"PlaySessionId\":\"ps1\"" in report, report)
        }

        val evidence = assertNotNull(source.playbackEvidence(path))
        assertEquals(DeliveryMode.DIRECT_PLAY, evidence.delivery)
        assertEquals(true, evidence.authenticated)
        assertEquals(1, requests.count { "PlaybackInfo" in it.url }, "what the server answered at the start is kept")
        assertTrue("SupportsDirectPlay=true" in evidence.answered!!, evidence.answered)
    }

    @Test
    fun aTokenTheServerRefusesIsSaidAndStopsNothing() = runTest {
        playbackInfo = { HttpResult(401, ByteArray(0)) }
        source.reportProgress(path, 60_000, paused = false)
        assertTrue(requests.any { it.url.endsWith("Sessions/Playing") }, "the report still goes")
        val evidence = assertNotNull(source.playbackEvidence(path))
        assertEquals(false, evidence.authenticated)
        assertNull(evidence.answered)
        assertEquals(DeliveryMode.UNKNOWN, evidence.delivery)
    }

    @Test
    fun aServerThatDoesNotAnswerHoldsNothingUp() = runTest {
        var calls = 0
        playbackInfo = {
            calls++
            error("offline")
        }
        source.reportProgress(path, 60_000, paused = false)
        assertEquals(1, calls)
        assertTrue(requests.any { it.url.endsWith("Sessions/Playing") })
        assertEquals(null, assertNotNull(source.playbackEvidence(path)).authenticated, "unreachable is not refused")
    }

    @Test
    fun aServerThatNeverAnswersIsGivenUpOn() = runTest {
        val slow = HttpFetcher { request ->
            requests += request
            if ("PlaybackInfo" in request.url) delay(600_000)
            HttpResult(204, ByteArray(0))
        }
        val quiet = JellyfinSource(JellyfinCredentials("https://jf.example.com", "srv1", "u1", "tok", "dev1"), slow)
        quiet.reportProgress(path, 60_000, paused = false)
        assertTrue(requests.any { it.url.endsWith("Sessions/Playing") }, "the report goes on without the answer")
        assertTrue(testScheduler.currentTime < 10_000, "after ${testScheduler.currentTime} ms")
    }

    @Test
    fun aServerDeclaresWhatItCanConvert() {
        val declared = source.descriptor.capabilities
        for (capability in listOf(SourceCapability.SERVER_REMUX, SourceCapability.SERVER_AUDIO_CONVERSION, SourceCapability.SERVER_TRANSCODE, SourceCapability.SERVER_SUBTITLE_EXTRACTION, SourceCapability.SERVER_BURN_IN)) {
            assertTrue(capability in declared, "$capability")
        }
    }

    @Test
    fun whatIsNotAVersionHasNoEvidence() = runTest {
        assertNull(source.playbackEvidence("items/m1/images/Primary"))
    }

    @Test
    fun aCopyForOfflineIsStillTheFileItself() = runTest {
        assertEquals("https://jf.example.com/Videos/m1/stream?static=true&mediaSourceId=s1", source.downloadTarget(path).uri)
    }
}
