package dev.reflux.desktop.engine

import dev.reflux.core.net.HttpRequest
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DeliveryMode
import dev.reflux.core.playback.DisplayCapabilities
import dev.reflux.core.playback.PlaybackRequest
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.playback.StreamFate
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.source.CatalogVersion
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.library.net.JdkHttpFetcher
import dev.reflux.playback.mpv.DesktopCapabilities
import dev.reflux.playback.mpv.MpvPlayer
import dev.reflux.sources.jellyfin.JellyfinDelivery
import dev.reflux.sources.jellyfin.JellyfinPlaybackAsk
import dev.reflux.sources.jellyfin.JellyfinSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What a real Jellyfin server does, read from its own session: never from a flag or from what was asked for.
 *
 * Runs when a server is named ([LiveJellyfin]) and holds an HEVC 10-bit, E-AC-3, Matroska file it can really serve:
 * a listed file may be gone from the server's disk, so each is tried. Starts a few seconds of each conversion
 * and stops it. The direct-play test reports a playback at position zero to that user's account.
 * Prints what it observed, without addresses or keys, for docs/VERIFICATION.md.
 */
class JellyfinTruthLiveTest {
    private val http = JdkHttpFetcher()

    private suspend fun connect(device: String): Pair<JellyfinSource, String>? {
        val live = LiveJellyfin.settings() ?: return null
        return JellyfinSource(JellyfinSource.connect(live.url, live.user, live.password, http, device, "Reflux truth test"), http, "Live test") to live.url.trimEnd('/')
    }

    private suspend fun JellyfinSource.serves(version: CatalogVersion): Boolean {
        val stream = playbackTarget(version.path)
        return http.send(HttpRequest(stream.uri, headers = stream.headers + ("Range" to "bytes=0-1023"))).ok
    }

    /** With no AAC track: the profiles below take AAC, and such a file would rightly just play. */
    private suspend fun JellyfinSource.reference(): CatalogVersion = catalog().toList().flatMap { it.versions }.firstOrNull { version ->
        val stream = version.stream
        stream.container == Container.MATROSKA && stream.video?.codec == VideoCodec.HEVC && stream.video?.bitDepth == 10 &&
            stream.audio.firstOrNull()?.codec == AudioCodec.EAC3 && stream.audio.none { it.codec == AudioCodec.AAC } && serves(version)
    }.also { assumeTrue("no served HEVC 10-bit, E-AC-3, Matroska file", it != null) }!!

    /** Fetches [target]'s first HLS segment, which is what makes a server start converting. */
    private suspend fun firstSegment(target: PlaybackTarget): Int {
        suspend fun text(url: String) = http.send(HttpRequest(url, headers = target.headers)).also { assertTrue(it.ok, "HTTP ${it.status}") }.text()
        val directory = target.uri.substringBefore("master.m3u8")
        val variant = text(target.uri).lines().first { it.isNotBlank() && !it.startsWith("#") }
        val segment = text(directory + variant).lines().first { it.isNotBlank() && !it.startsWith("#") }
        return http.send(HttpRequest(directory + segment, headers = target.headers)).also { assertTrue(it.ok, "segment HTTP ${it.status}") }.body.size
    }

    private fun profile(video: String, audio: String, container: String, copyVideo: String, copyAudio: String, subtitles: String = "[]") = """
        {"DirectPlayProfiles":[{"Container":"$container","Type":"Video","VideoCodec":"$video","AudioCodec":"$audio"}],
         "TranscodingProfiles":[{"Container":"ts","Type":"Video","VideoCodec":"$copyVideo","AudioCodec":"$copyAudio","Context":"Streaming","Protocol":"hls","MaxAudioChannels":"6","BreakOnNonKeyFrames":true}],
         "CodecProfiles":[],"SubtitleProfiles":$subtitles}
    """.trimIndent()

    private suspend fun observe(name: String, server: JellyfinSource, path: String, ask: JellyfinPlaybackAsk) = run {
        val answer = assertNotNull(server.playbackInfo(path, ask), name)
        val target = assertNotNull(server.transcodingTarget(answer), "$name: the server offered a conversion")
        try {
            val bytes = firstSegment(target)
            var session = server.sessionFacts()
            var waited = 0
            while (session?.transcoding == null && waited++ < 40) {
                delay(250)
                session = server.sessionFacts()
            }
            val evidence = JellyfinDelivery.evidence(name, originalRequested = false, path.split('/')[1], answer, session, authenticated = true)
            println("LIVE $name: ${evidence.delivery} video=${evidence.video} audio=${evidence.audio} supportsDirectStream=${answer.supportsDirectStream} TranscodingInfo=${session?.transcoding} subtitleTriggered=${evidence.subtitleTriggeredConversion} segmentBytes=$bytes")
            evidence
        } finally {
            answer.playSessionId?.let { server.stopConversion(it) }
        }
    }

    @Test
    fun whatTheServerDidIsReadFromItsSession() = runBlocking {
        val (server) = connect("reflux-truth-test") ?: return@runBlocking assumeTrue("no live Jellyfin server named", false)
        val path = server.reference().path

        val audio = observe("audio-only conversion", server, path, JellyfinPlaybackAsk(profile("h264,hevc", "aac", "mkv", "hevc,h264", "aac"), allowConversion = true))
        assertEquals(DeliveryMode.AUDIO_CONVERSION, audio.delivery)
        assertEquals(StreamFate.PRESERVED, audio.video)
        assertEquals(StreamFate.CONVERTED, audio.audio)

        val remux = observe("remux", server, path, JellyfinPlaybackAsk(profile("h264,hevc", "aac,ac3,eac3", "mp4", "hevc,h264", "eac3,ac3,aac"), allowConversion = true))
        assertEquals(DeliveryMode.REMUX, remux.delivery)
        assertEquals(StreamFate.PRESERVED, remux.audio)

        val video = observe("video transcode", server, path, JellyfinPlaybackAsk(profile("h264", "aac,ac3,eac3", "mkv", "h264", "eac3,ac3,aac"), allowConversion = true))
        assertEquals(DeliveryMode.VIDEO_TRANSCODE, video.delivery)
        assertEquals(StreamFate.CONVERTED, video.video)
        assertEquals(StreamFate.PRESERVED, video.audio)
    }

    @Test
    fun aBitmapSubtitleAskedOfTheServerConvertsTheVideoAndDeclaredExternalItDoesNot() = runBlocking {
        val (server) = connect("reflux-truth-subtitle-test") ?: return@runBlocking assumeTrue("no live Jellyfin server named", false)
        val versions = server.catalog().toList().flatMap { it.versions }.filter { version ->
            version.stream.video?.codec == VideoCodec.HEVC && version.stream.audio.firstOrNull()?.codec == AudioCodec.EAC3 &&
                version.stream.subtitles.any { it.format.bitmap } && server.serves(version)
        }
        assumeTrue("no served HEVC, E-AC-3 file with a bitmap subtitle", versions.isNotEmpty())
        val path = versions.first().path
        val track = assertNotNull(server.playbackInfo(path)).subtitles.first { it.codec.equals("PGSSUB", ignoreCase = true) }.index
        val audioProblem = profile("h264,hevc", "aac", "mkv", "hevc,h264", "aac")

        val burned = observe("audio problem, PGS asked of the server", server, path, JellyfinPlaybackAsk(audioProblem, subtitleStreamIndex = track, allowConversion = true))
        assertEquals(DeliveryMode.VIDEO_TRANSCODE, burned.delivery, "an audio-only problem, and the subtitle converted the video")
        assertTrue(burned.subtitleTriggeredConversion)

        val external = """[{"Format":"pgssub","Method":"External"}]"""
        val kept = observe(
            "audio problem, PGS declared External", server, path,
            JellyfinPlaybackAsk(profile("h264,hevc", "aac", "mkv", "hevc,h264", "aac", external), subtitleStreamIndex = track, allowConversion = true),
        )
        assertEquals(DeliveryMode.AUDIO_CONVERSION, kept.delivery)
        assertEquals(false, kept.subtitleTriggeredConversion)
    }

    @Test
    fun aRealPlaybackOfTheOriginalIsDirectPlayOnTheServer() = runBlocking {
        assumeTrue("libmpv not installed", MpvPlayer.isAvailable)
        val (server) = connect("reflux-truth-play-test") ?: return@runBlocking assumeTrue("no live Jellyfin server named", false)
        val path = server.reference().path
        val capabilities = DesktopCapabilities.of(DisplayCapabilities(1920, 1080))
        MpvPlayer.create(capabilities, mapOf("vo" to "null", "ao" to "null"))!!.use { player ->
            player.load(PlaybackRequest(server.playbackTarget(path), "Reference"))
            withTimeout(30_000) { player.state.first { it.status == PlayerStatus.PLAYING && it.positionMs > 200 } }
            try {
                server.reportProgress(path, 0, paused = false)
                val evidence = assertNotNull(server.playbackEvidence(path))
                println("LIVE direct play: ${evidence.summary()}")
                println("LIVE direct play session: ${server.sessionFacts()}")
                assertEquals(DeliveryMode.DIRECT_PLAY, evidence.delivery)
                assertEquals(StreamFate.PRESERVED, evidence.video)
                assertEquals(StreamFate.PRESERVED, evidence.audio)
                assertEquals(true, evidence.authenticated)
                assertEquals("DirectPlay", server.sessionFacts()?.playMethod)
            } finally {
                server.reportStopped(path, 0)
            }
        }
    }

    @Test
    fun theEngineAuthenticatesWhereTheServerChecks() = runBlocking {
        assumeTrue("libmpv not installed", MpvPlayer.isAvailable)
        val (server, url) = connect("reflux-truth-auth-test") ?: return@runBlocking assumeTrue("no live Jellyfin server named", false)
        val version = server.reference()
        val itemId = version.path.split('/')[1]
        // The static stream takes anyone (docs/PLAYBACK.md, section 3); this address needs a valid token.
        val checked = "$url/Items/$itemId/Download"
        val headers = server.playbackTarget(version.path).headers
        assertEquals(401, http.send(HttpRequest(checked, headers = mapOf("Range" to "bytes=0-1023"))).status, "the control: refused without a token")
        val capabilities = DesktopCapabilities.of(DisplayCapabilities(1920, 1080))

        MpvPlayer.create(capabilities, mapOf("vo" to "null", "ao" to "null"))!!.use { player ->
            player.load(PlaybackRequest(PlaybackTarget(checked, headers), "Authenticated"))
            val state = withTimeout(30_000) { player.state.first { it.status == PlayerStatus.PLAYING && it.positionMs > 200 || it.status == PlayerStatus.ERROR } }
            println("LIVE authenticated engine request: ${state.status}, ${state.tracks.size} tracks, duration ${state.durationMs}")
            assertEquals(PlayerStatus.PLAYING, state.status, "the engine opened a stream that demands its token: ${state.error}")
        }
        MpvPlayer.create(capabilities, mapOf("vo" to "null", "ao" to "null"))!!.use { player ->
            player.load(PlaybackRequest(PlaybackTarget(checked), "Refused"))
            val state = withTimeout(30_000) { player.state.first { it.status == PlayerStatus.ERROR || it.status == PlayerStatus.PLAYING } }
            println("LIVE unauthenticated engine request: ${state.status} ${state.failure}")
            assertEquals(PlayerStatus.ERROR, state.status, "and the same request without it is refused")
        }
    }
}
