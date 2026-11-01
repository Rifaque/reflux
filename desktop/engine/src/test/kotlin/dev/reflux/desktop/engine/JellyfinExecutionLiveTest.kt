package dev.reflux.desktop.engine

import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.SourceId
import dev.reflux.core.net.HttpRequest
import dev.reflux.core.playback.AudioChoice
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DeliveryMode
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.DisplayCapabilities
import dev.reflux.core.playback.PlanPart
import dev.reflux.core.playback.PlaybackComparison
import dev.reflux.core.playback.PlaybackController
import dev.reflux.core.playback.SubtitleDirective
import dev.reflux.core.playback.WatchReporter
import dev.reflux.core.playback.PlayerState
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.playback.StreamFate
import dev.reflux.core.playback.SubtitleCapabilities
import dev.reflux.core.playback.SubtitleChoice
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.playback.SubtitlePlan
import dev.reflux.core.playback.SubtitleRenderer
import dev.reflux.core.playback.Support
import dev.reflux.core.playback.TrackPreferences
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoPlan
import dev.reflux.core.source.SourceCapability
import dev.reflux.library.Library
import dev.reflux.library.LibraryDatabase
import dev.reflux.library.net.JdkHttpFetcher
import dev.reflux.library.planPlayback
import dev.reflux.playback.mpv.DesktopCapabilities
import dev.reflux.playback.mpv.MpvPlayer
import dev.reflux.sources.jellyfin.JellyfinSource
import dev.reflux.sources.local.LocalFolderSource
import dev.reflux.sources.smb.SmbShareConfig
import dev.reflux.sources.smb.SmbSource
import dev.reflux.sources.webdav.WebDavConfig
import dev.reflux.sources.webdav.WebDavSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The whole path on a real server and the real desktop engine: scan, Best Version, plan, negotiation, playback, and
 * what the server says it did, compared with the plan. A client's limits are narrowed to make each case happen.
 * Runs where a server is named ([LiveJellyfin]); skips files the server lists and cannot serve.
 */
class JellyfinExecutionLiveTest {
    private val http = JdkHttpFetcher()
    private val viewer = TrackPreferences(listOf("en"))
    private val capabilities = DesktopCapabilities.of(DisplayCapabilities(1920, 1080))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private object Silent : WatchReporter {
        override fun progress(itemId: dev.reflux.core.model.MediaId, positionMs: Long, durationMs: Long?) = Unit
        override fun stopped(itemId: dev.reflux.core.model.MediaId, positionMs: Long, durationMs: Long?) = Unit
    }

    private class Setup(val server: JellyfinSource, val library: Library, val sources: (SourceId) -> JellyfinSource?, val versions: List<MediaVersion>)

    private suspend fun setup(device: String): Setup? {
        val live = LiveJellyfin.settings() ?: return null
        if (!MpvPlayer.isAvailable) return null
        val server = JellyfinSource(JellyfinSource.connect(live.url, live.user, live.password, http, device, "Reflux execution test"), http, "Live test")
        val directory = Files.createTempDirectory("reflux-execution-live").also { it.toFile().deleteOnExit() }
        val library = Library(LibraryDatabase.open(directory.resolve("library.db"))) { System.currentTimeMillis() }
        library.addSource(server, "live")
        library.scan(server)
        val versions = library.movies().flatMap { library.versions(it.item.id) }.map { it.version }
        return Setup(server, library, { id -> server.takeIf { it.descriptor.id == id } }, versions)
    }

    private suspend fun JellyfinSource.serves(version: MediaVersion): Boolean {
        val stream = playbackTarget(version.location.path)
        return http.send(HttpRequest(stream.uri, headers = stream.headers + ("Range" to "bytes=0-1023"))).ok
    }

    private fun MediaVersion.hevcEac3(): Boolean = stream.container == Container.MATROSKA && stream.video?.codec == VideoCodec.HEVC && stream.video?.bitDepth == 10 &&
        stream.audio.firstOrNull()?.codec == AudioCodec.EAC3 && stream.audio.none { it.codec == AudioCodec.AAC }

    private suspend fun Setup.reference(): MediaVersion =
        versions.firstOrNull { it.hevcEac3() && server.serves(it) }.also { assumeTrue("no served HEVC 10-bit, E-AC-3, Matroska file", it != null) }!!

    private suspend fun Setup.run(name: String, version: MediaVersion, device: DeviceCapabilities, audio: AudioChoice = AudioChoice.Automatic, subtitle: SubtitleChoice = SubtitleChoice.Off, originalFailed: Set<PlanPart> = emptySet(), check: (PlaybackComparison, PlayerState) -> Unit) {
        val plan = assertNotNull(library.planPlayback(version.itemId, device, sources, versionId = version.id, preferences = viewer, audio = audio, subtitle = subtitle, originalFailed = originalFailed, resume = false), name)
        val path = version.location.path
        try {
            MpvPlayer.create(device, mapOf("vo" to "null", "ao" to "null"))!!.use { player ->
                val started = System.nanoTime()
                val controller = PlaybackController(player, version.itemId, Silent, viewer)
                controller.start(this@JellyfinExecutionLiveTest.scope, plan.request)
                val first = withTimeout(60_000) { player.state.first { (it.status == PlayerStatus.PLAYING && it.positionMs > 200) || it.status == PlayerStatus.ERROR } }
                assertEquals(PlayerStatus.PLAYING, first.status, "$name: ${first.error}")
                val firstFrameMs = (System.nanoTime() - started) / 1_000_000
                val seekStart = System.nanoTime()
                val target = 120_000L
                player.seekTo(target)
                // Past the target and moving: the engine's position jumps there before a frame of it has decoded.
                withTimeout(60_000) { player.state.first { it.positionMs in (target + 300)..(target + 8_000) && it.status == PlayerStatus.PLAYING } }
                val seekMs = (System.nanoTime() - seekStart) / 1_000_000
                server.reportProgress(path, 0, paused = false)
                // A subtitle the server had to make comes when it comes.
                if (plan.request.tracks?.subtitle is SubtitleDirective.Attached) withTimeout(150_000) { player.state.first { it.selectedSubtitleId != null } }
                delay(500)
                val compared = PlaybackComparison.of(plan.planned!!, assertNotNull(server.playbackEvidence(path)), player.state.value)
                val transcoding = server.sessionFacts()?.transcoding
                println("LIVE $name: firstFrame=${firstFrameMs}ms seek=${seekMs}ms ${compared.summary()} TranscodingInfo=$transcoding selectedSubtitle=${player.state.value.selectedSubtitleId} subtitles=${compared.evidence?.subtitles}")
                assertEquals(emptySet(), compared.mismatches, "$name: ${compared.summary()}")
                check(compared, player.state.value)
            }
        } finally {
            server.reportStopped(path, 0)
            server.release(path)
        }
    }

    @Test
    fun everyDeliveryIsPlannedNegotiatedPlayedAndConfirmed() = runBlocking {
        val setup = setup("reflux-execution-test") ?: return@runBlocking assumeTrue("no live Jellyfin server or libmpv", false)
        val reference = setup.reference()

        setup.run("direct play", reference, capabilities) { compared, _ ->
            assertEquals(DeliveryMode.DIRECT_PLAY, compared.planned.delivery)
            assertEquals(StreamFate.PRESERVED, compared.evidence?.video)
        }
        setup.run("audio conversion", reference, capabilities.copy(audioDecoders = setOf(AudioCodec.AAC))) { compared, _ ->
            assertEquals(DeliveryMode.AUDIO_CONVERSION, compared.planned.delivery)
            assertEquals(StreamFate.PRESERVED, compared.evidence?.video)
            assertEquals(StreamFate.CONVERTED, compared.evidence?.audio)
        }
        setup.run("remux", reference, capabilities.copy(containers = setOf(Container.MP4))) { compared, _ ->
            assertEquals(DeliveryMode.REMUX, compared.planned.delivery)
            assertEquals(StreamFate.PRESERVED, compared.evidence?.video)
            assertEquals(StreamFate.PRESERVED, compared.evidence?.audio)
        }
        setup.run("video transcode", reference, capabilities.copy(videoDecoders = capabilities.videoDecoders.filter { it.codec != VideoCodec.HEVC })) { compared, _ ->
            assertEquals(VideoPlan.TRANSCODE, compared.planned.video)
            assertEquals(StreamFate.CONVERTED, compared.evidence?.video)
        }
        setup.run("original failed, planned around", reference, capabilities, originalFailed = setOf(PlanPart.VIDEO)) { compared, _ ->
            assertEquals(DeliveryMode.VIDEO_TRANSCODE, compared.planned.delivery)
        }
    }

    @Test
    fun anExplicitTrackIsTheOneTheServerConverts() = runBlocking {
        val setup = setup("reflux-execution-track-test") ?: return@runBlocking assumeTrue("no live Jellyfin server or libmpv", false)
        val version = setup.versions.firstOrNull { v -> v.stream.audio.size > 1 && v.stream.video?.codec == VideoCodec.HEVC && setup.server.serves(v) }
        assumeTrue("no served file with two audio tracks", version != null)
        setup.run("explicit second track, converted", version!!, capabilities.copy(audioDecoders = emptySet()), audio = AudioChoice.Explicit(1)) { compared, state ->
            assertEquals(1, compared.planned.audioIndex)
            assertEquals(StreamFate.CONVERTED, compared.evidence?.audio)
            assertEquals(1, state.audioTracks.size, "the converted stream carries the one track asked for")
        }
    }

    @Test
    fun aBitmapSubtitleDoesNotConvertTheVideoUnlessPlannedTo() = runBlocking {
        val setup = setup("reflux-execution-subtitle-test") ?: return@runBlocking assumeTrue("no live Jellyfin server or libmpv", false)
        val version = setup.versions.firstOrNull { v -> v.stream.video?.codec == VideoCodec.HEVC && v.stream.subtitles.any { it.format == SubtitleFormat.PGS } && v.stream.audio.none { it.codec == AudioCodec.AAC } && setup.server.serves(v) }
        assumeTrue("no served HEVC file with PGS and no AAC", version != null)
        val track = version!!.stream.subtitles.indexOfFirst { it.format == SubtitleFormat.PGS }

        setup.run("audio conversion, PGS beside the stream", version, capabilities.copy(audioDecoders = setOf(AudioCodec.AAC)), subtitle = SubtitleChoice.Explicit(track)) { compared, state ->
            assertEquals(SubtitlePlan.EXTERNAL, compared.planned.subtitles)
            assertEquals(StreamFate.PRESERVED, compared.evidence?.video, "the audio-only problem stays one")
            assertTrue(state.selectedSubtitleId != null, "and the file the server extracted is selected: ${state.subtitleTracks}")
        }
        val undrawable = capabilities.copy(subtitles = SubtitleCapabilities(capabilities.subtitles.renderers + (SubtitleFormat.PGS to SubtitleRenderer(Support.UNSUPPORTED, Support.UNSUPPORTED))))
        setup.run("PGS nothing can draw, chosen", version, undrawable, subtitle = SubtitleChoice.Explicit(track)) { compared, _ ->
            assertEquals(SubtitlePlan.BURN_IN, compared.planned.subtitles)
            assertEquals(StreamFate.CONVERTED, compared.evidence?.video)
            assertTrue(compared.evidence?.subtitleTriggeredConversion == true)
        }
    }

    /** Not live: read here because this is the module that can see every adapter. */
    @Test
    fun onlyAServerDeclaresConversions() {
        val conversions = setOf(
            SourceCapability.SERVER_REMUX, SourceCapability.SERVER_AUDIO_CONVERSION, SourceCapability.SERVER_TRANSCODE,
            SourceCapability.SERVER_SUBTITLE_EXTRACTION, SourceCapability.SERVER_SUBTITLE_CONVERSION, SourceCapability.SERVER_BURN_IN,
        )
        val folder = Files.createTempDirectory("reflux-adapters").also { it.toFile().deleteOnExit() }
        val local = LocalFolderSource(folder).descriptor.capabilities
        val webDav = WebDavSource(WebDavConfig("http://nas.test/media"), http).descriptor.capabilities
        for (declared in listOf(local, webDav)) assertFalse(declared.any { it in conversions }, "$declared")
        assertTrue(SmbSourceFiles.declaresNoConversions(conversions))
    }

    private object SmbSourceFiles {
        fun declaresNoConversions(conversions: Set<SourceCapability>): Boolean {
            val streams = dev.reflux.sources.smb.LocalStreamServer()
            return try {
                SmbSource(SmbShareConfig("nas.test", "media"), streams).descriptor.capabilities.none { it in conversions }
            } finally {
                streams.close()
            }
        }
    }
}
