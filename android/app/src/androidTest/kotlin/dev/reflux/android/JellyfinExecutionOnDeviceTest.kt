package dev.reflux.android

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.reflux.android.engine.AndroidFetcher
import dev.reflux.android.playback.AndroidCapabilities
import dev.reflux.android.playback.Media3Player
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaLocation
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.SourceId
import dev.reflux.core.model.StreamInfoOrigin
import dev.reflux.core.model.VersionId
import dev.reflux.core.net.HttpRequest
import dev.reflux.core.playback.AudioChoice
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.AudioDirective
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DeliveryMode
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.PlanOutcome
import dev.reflux.core.playback.PlanReason
import dev.reflux.core.playback.PlaybackComparison
import dev.reflux.core.playback.PlaybackController
import dev.reflux.core.playback.PlaybackPlanner
import dev.reflux.core.playback.PlaybackRequest
import dev.reflux.core.playback.PlaybackRequirements
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.playback.StreamFate
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.playback.SubtitleChoice
import dev.reflux.core.playback.SubtitleDirective
import dev.reflux.core.playback.TrackDirective
import dev.reflux.core.playback.TrackPreferences
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoPlan
import dev.reflux.core.playback.AudioStream
import dev.reflux.core.playback.WatchReporter
import dev.reflux.core.source.ConnectionKind
import dev.reflux.core.source.ConnectionRequest
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.library.ConnectResult
import dev.reflux.sources.jellyfin.JellyfinSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Planned playback on this device's real engine. The server and account are never arguments, which would put them in
 * process listings: `tools/live/run-on-device.py` leaves them in a shell-only file for the length of the run, read
 * here through the shell. Without it those tests skip.
 */
@RunWith(AndroidJUnit4::class)
class JellyfinExecutionOnDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val players = ArrayList<Media3Player>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val viewer = TrackPreferences(listOf("en"))

    @After
    fun cleanUp() {
        players.forEach(Media3Player::close)
        scope.cancel()
    }

    /** What the runner left, read as the shell user, who alone can. Empty where it left nothing. */
    private fun liveSettings(): Map<String, String> {
        val pipe = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cat /data/local/tmp/reflux-live.env")
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(pipe).bufferedReader().readLines()
            .filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
    }

    private fun report(line: String) {
        Log.i("RefluxExecution", line)
    }

    private object Nothing : WatchReporter {
        override fun progress(itemId: MediaId, positionMs: Long, durationMs: Long?) = Unit
        override fun stopped(itemId: MediaId, positionMs: Long, durationMs: Long?) = Unit
    }

    private fun clip(): String {
        val file = File(context.cacheDir, "tracks.mkv")
        InstrumentationRegistry.getInstrumentation().context.assets.open("tracks.mkv").use { input -> file.outputStream().use { input.copyTo(it) } }
        return android.net.Uri.fromFile(file).toString()
    }

    private fun tracks(device: DeviceCapabilities, choice: AudioChoice) = PlaybackPlanner.plan(
        PlaybackRequirements(
            MediaVersion(
                VersionId("v"), MediaId("m"), MediaLocation(SourceId("s"), "tracks.mkv"), 1, 0,
                StreamInfo(Container.MATROSKA, audio = listOf(AudioStream(AudioCodec.DTS, 2, "en", default = true), AudioStream(AudioCodec.AAC, 2, "fr"))),
                StreamInfoOrigin.PROBE,
            ),
            viewer, emptySet(), choice,
        ),
        device,
    )

    private fun play(device: DeviceCapabilities, directive: TrackDirective): Media3Player {
        val player = Media3Player(context, device).also { players += it }
        PlaybackController(player, MediaId("m"), Nothing, viewer).start(scope, PlaybackRequest(PlaybackTarget(clip()), "Tracks", tracks = directive))
        return player
    }

    /**
     * Not a test: the way to connect the installed application for a walk through its real screens, on request
     * (`-e refluxApp true`), without a password being typed through adb. It uses the application's own connection,
     * so the password goes no further than a sealed token, and lists what is on the server by what makes it a case.
     * `-e refluxForgetFacts <item>` leaves a work with no known video, so that playing it is a real attempt at an unknown.
     */
    @Test
    fun prepareTheInstalledApplication() = runBlocking<Unit> {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("not asked for", arguments.getString("refluxApp") == "true")
        val live = liveSettings()
        assumeTrue("no live server named", live["REFLUX_JELLYFIN_TEST"] != null)
        val environment = (context.applicationContext as RefluxApplication).environment
        val result = environment.connections.connect(
            ConnectionKind.SERVER, ConnectionRequest(live.getValue("REFLUX_JELLYFIN_TEST"), live["REFLUX_JELLYFIN_TEST_USER"], live["REFLUX_JELLYFIN_TEST_PASSWORD"]),
        )
        report("connected: ${result is ConnectResult.Connected}")
        environment.pipeline.refresh()
        val episodes = environment.library.shows().flatMap { show -> environment.library.showDetail(show.item.id)?.seasons.orEmpty().flatMap { it.episodes } }
        val versions = (environment.library.movies() + episodes).flatMap { environment.library.versions(it.item.id) }.map { it.version }
        report("films: ${versions.size}; with stream indexes: ${versions.count { v -> v.stream.audio.isNotEmpty() && v.stream.audio.all { it.sourceIndex != null } }}")
        fun list(name: String, match: (StreamInfo) -> Boolean) = versions.filter { match(it.stream) }.take(3).forEach { report("case $name ${it.itemId.value} ${it.location.fileName.take(0)}audio=${it.stream.audio.map { a -> a.codec.name + ":" + a.language }} subs=${it.stream.subtitles.map { s -> s.format.name + ":" + s.language + (if (s.forced) ":forced" else "") }}") }
        list("h264-aac") { it.video?.codec == VideoCodec.H264 && it.video?.bitDepth == 8 && it.audio.firstOrNull()?.codec == AudioCodec.AAC && it.audio.size == 1 }
        list("h264-dts") { it.video?.codec == VideoCodec.H264 && it.audio.any { a -> a.codec == AudioCodec.DTS } }
        list("hevc10-eac3") { it.video?.codec == VideoCodec.HEVC && it.video?.bitDepth == 10 && it.audio.none { a -> a.codec == AudioCodec.AAC } && it.audio.firstOrNull()?.codec == AudioCodec.EAC3 }
        list("hevc10-aac") { it.video?.codec == VideoCodec.HEVC && it.video?.bitDepth == 10 && it.audio.firstOrNull()?.codec == AudioCodec.AAC }
        list("hevc-pgs") { it.video?.codec == VideoCodec.HEVC && it.subtitles.any { s -> s.format == dev.reflux.core.playback.SubtitleFormat.PGS } }
        list("ass") { it.subtitles.any { s -> s.format == dev.reflux.core.playback.SubtitleFormat.ASS } }
        list("two-languages") { it.audio.map { a -> a.language }.distinct().size > 1 }
        arguments.getString("refluxForgetFacts")?.let { item ->
            val version = environment.library.versions(MediaId(item)).first().version
            environment.library.recordProbe(version.id, StreamInfo(Container.UNKNOWN, null, version.stream.audio, version.stream.subtitles))
            report("forgot the video of $item")
        }
    }

    @Test
    fun anExplicitTrackThatCannotPlayIsNotReplacedByAnotherLanguage() {
        val device = AndroidCapabilities.of(context)
        assumeTrue("this device has a DTS decoder", AudioCodec.DTS !in device.audioDecoders)
        val plan = tracks(device, AudioChoice.Explicit(0))
        assertEquals(PlanOutcome.PLAYS_WITHOUT_AUDIO, plan.outcome)
        val player = play(device, TrackDirective(AudioDirective.None, SubtitleDirective.Off))
        val state = player.await("the picture") { it.status == PlayerStatus.PLAYING && it.positionMs > 1_500 }
        assertTrue(state.audioUnavailable, "French is there and is not played: $state")
        assertNull(state.selectedAudioId)
    }

    @Test
    fun automaticSelectionFollowsThePlanToTheTrackThatPlays() {
        val device = AndroidCapabilities.of(context)
        assumeTrue("this device has a DTS decoder", AudioCodec.DTS !in device.audioDecoders)
        val plan = tracks(device, AudioChoice.Automatic)
        assertEquals(1, plan.audioIndex)
        assertTrue(PlanReason.AUDIO_OTHER_LANGUAGE in plan.reasons)
        val player = play(device, TrackDirective(AudioDirective.Track(1), SubtitleDirective.Off))
        val state = player.await("the track the plan chose") { it.status == PlayerStatus.PLAYING && it.selectedAudioId != null && it.positionMs > 500 }
        assertEquals(1, state.audioTracks.first { it.id == state.selectedAudioId }.position, "a place in the file, counted past the track this device cannot play")
        assertEquals(emptySet(), PlaybackComparison.of(plan, null, state).mismatches)
    }

    @Test
    fun plansAreCarriedOutThroughARealServer() = runBlocking {
        val live = liveSettings()
        val url = live["REFLUX_JELLYFIN_TEST"]
        assumeTrue("no live server named", !url.isNullOrBlank())
        val http = AndroidFetcher(context, "Reflux test")
        report("connecting")
        val server = JellyfinSource(JellyfinSource.connect(url!!, live.getValue("REFLUX_JELLYFIN_TEST_USER"), live["REFLUX_JELLYFIN_TEST_PASSWORD"].orEmpty(), http, "reflux-device-test"), http, "Live")
        report("connected")
        val listed = server.catalog().toList().flatMap { it.versions }
        report("listed ${listed.size} versions")
        val found = listed.firstOrNull { version ->
            val stream = version.stream
            stream.container == Container.MATROSKA && stream.video?.codec == VideoCodec.HEVC && stream.video?.bitDepth == 10 &&
                stream.audio.firstOrNull()?.codec == AudioCodec.EAC3 && stream.audio.none { it.codec == AudioCodec.AAC } &&
                server.playbackTarget(version.path).let { http.send(HttpRequest(it.uri, headers = it.headers + ("Range" to "bytes=0-1023"))).ok }
        }
        assumeTrue("no served HEVC 10-bit, E-AC-3 file", found != null)
        val version = MediaVersion(VersionId("live"), MediaId("live"), MediaLocation(server.descriptor.id, found!!.path), found.sizeBytes, 0, found.stream, StreamInfoOrigin.SOURCE)
        val device = AndroidCapabilities.of(context)

        fun run(name: String, client: DeviceCapabilities) = runBlocking {
            val path = version.location.path
            val plan = PlaybackPlanner.plan(PlaybackRequirements(version, viewer, server.descriptor.capabilities, subtitle = SubtitleChoice.Off), client)
            val delivery = assertNotNull(server.negotiate(version, plan, client), name)
            val player = Media3Player(context, client).also { players += it }
            try {
                val started = System.nanoTime()
                val index = plan.audioIndex
                val audio = if (plan.delivery == DeliveryMode.DIRECT_PLAY && index != null) AudioDirective.Track(index) else AudioDirective.EngineChoice
                PlaybackController(player, MediaId("live"), Nothing, viewer).start(scope, PlaybackRequest(delivery.target, name, tracks = TrackDirective(audio, SubtitleDirective.Off)))
                val first = player.await("$name to start", 60_000) { it.status == PlayerStatus.PLAYING && it.positionMs > 300 }
                val firstFrameMs = (System.nanoTime() - started) / 1_000_000
                val seekFrom = System.nanoTime()
                player.seekTo(120_000)
                player.await("$name to seek", 60_000) { it.positionMs in 120_300L..128_000L && it.status == PlayerStatus.PLAYING }
                val seekMs = (System.nanoTime() - seekFrom) / 1_000_000
                server.reportProgress(path, 0, paused = false)
                kotlinx.coroutines.delay(500)
                val compared = PlaybackComparison.of(plan, assertNotNull(server.playbackEvidence(path)), player.state.value)
                report("$name firstFrame=${firstFrameMs}ms seek=${seekMs}ms ${compared.summary()} transcoding=${server.sessionFacts()?.transcoding} audioTracks=${first.audioTracks.size}")
                assertEquals(emptySet(), compared.mismatches, "$name: ${compared.summary()}")
                compared
            } finally {
                player.stop()
                server.reportStopped(path, 0)
                server.release(path)
            }
        }

        val direct = run("as this device is", device)
        report("plan for this device: ${direct.planned.delivery} ${direct.planned.video} ${direct.planned.audio}")
        val converted = run("no surround decoders", device.copy(audioDecoders = device.audioDecoders - setOf(AudioCodec.EAC3, AudioCodec.AC3), audioPassthrough = emptySet()))
        assertEquals(StreamFate.CONVERTED, converted.evidence?.audio)
        // Where this device decodes the video, only the audio changes. An emulator with no 10-bit HEVC plans a transcode.
        if (direct.planned.video != VideoPlan.TRANSCODE) {
            assertEquals(DeliveryMode.AUDIO_CONVERSION, converted.planned.delivery)
            assertEquals(StreamFate.PRESERVED, converted.evidence?.video)
        }
    }
}
