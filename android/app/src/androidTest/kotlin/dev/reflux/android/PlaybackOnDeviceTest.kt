package dev.reflux.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.reflux.android.playback.Media3Player
import dev.reflux.core.model.SourceId
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.net.HttpRequest
import dev.reflux.core.playback.PlaybackController
import dev.reflux.core.playback.PlaybackFailure
import dev.reflux.core.playback.PlaybackRequest
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.playback.TrackPreferences
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.core.watch.ResumeBasis
import dev.reflux.library.SyncingWatchReporter
import dev.reflux.library.planPlayback
import dev.reflux.sources.jellyfin.JellyfinSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Real playback on the device: Media3 decoding a generated clip, driven through the same contract and the
 * same controller as the desktop engine, and reporting into the same watch state.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackOnDeviceTest {
    private val worlds = ArrayList<DeviceWorld>()
    private val players = ArrayList<Media3Player>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun world(name: String) = DeviceWorld(name).also { worlds += it }
    private fun player(world: DeviceWorld) = Media3Player(context, world.environment.capabilities).also { players += it }

    @After
    fun cleanUp() {
        players.forEach(Media3Player::close)
        scope.cancel()
        worlds.forEach(DeviceWorld::close)
    }

    @Test
    fun theEnginePlaysSeeksAndEnds() {
        val world = world("engine")
        val clip = world.clip("clip.mp4")
        val player = player(world)
        player.load(PlaybackRequest(PlaybackTarget(world.tree.open(clip.path)), "Clip"))
        assertEquals(PlayerStatus.LOADING, player.state.value.status)

        val playing = player.await("playback to start") { it.status == PlayerStatus.PLAYING && it.durationMs != null && it.positionMs > 300 }
        assertTrue(abs(playing.durationMs!! - 180_000) < 2_000, "the clip runs three minutes: ${playing.durationMs}")
        // The engine names languages its own way (two letters); track selection understands both forms.
        assertEquals("en", playing.audioTracks.single().language)
        assertNotNull(playing.selectedAudioId)
        assertNotNull(player.videoAspect.value, "the picture's shape is known, for laying out the surface")

        player.pause()
        player.await("pause") { it.status == PlayerStatus.PAUSED }
        player.seekTo(120_000)
        player.await("the seek") { abs(it.positionMs - 120_000) < 1_500 }
        assertEquals(PlayerStatus.PAUSED, player.state.value.status, "seeking while paused stays paused")
        player.setSpeed(2.0)
        player.play()
        val faster = player.await("playback at double speed") { it.status == PlayerStatus.PLAYING && it.speed == 2.0 && it.positionMs > 121_000 }
        assertTrue(faster.positionMs < 180_000)

        player.seekTo(178_500)
        val ended = player.await("the end") { it.status == PlayerStatus.ENDED }
        assertEquals(ended.durationMs, ended.positionMs, "an ending is at the end, so that it counts as watched")
    }

    @Test
    fun audioThisDeviceCannotDecodeIsNotPassedOffAsNormalPlayback() {
        val world = world("silent")
        assumeTrue("this device has a DTS decoder", AudioCodec.DTS !in world.environment.capabilities.audioDecoders)
        val file = File(context.cacheDir, "dts.mkv")
        InstrumentationRegistry.getInstrumentation().context.assets.open("dts.mkv").use { input -> file.outputStream().use { input.copyTo(it) } }
        val player = player(world)
        player.load(PlaybackRequest(PlaybackTarget(android.net.Uri.fromFile(file).toString()), "DTS"))
        val state = player.await("the picture to play") { it.status == PlayerStatus.PLAYING && it.positionMs > 1_000 }
        assertTrue(state.audioUnavailable, "picture without sound is said: $state")
        assertEquals(listOf("audio/vnd.dts"), state.unsupportedAudioCodecs)
        file.delete()
    }

    /** How many codecs the system's resource manager counts as this process's. */
    private fun codecsHeld(): Int {
        val dump = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("dumpsys media.resource_manager")
            .let { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText() }
        val mine = dump.substringAfter("Pid: ${android.os.Process.myPid()}\n", "").substringBefore("Pid: ").substringBefore("Events logs")
        return Regex("codec/video-codec|codec/audio-codec").findAll(mine).count()
    }

    @Test
    fun leavingTheAppLetsGoOfTheDecodersAndKeepsThePlace() {
        val world = world("standby")
        val clip = world.clip("clip.mp4")
        val player = player(world)
        player.load(PlaybackRequest(PlaybackTarget(world.tree.open(clip.path)), "Clip"))
        player.await("playback to start") { it.status == PlayerStatus.PLAYING && it.positionMs > 2_000 }
        player.pause()
        val paused = player.await("pause") { it.status == PlayerStatus.PAUSED }
        assumeTrue("this device's resource manager lists the decoder", codecsHeld() > 0)

        // Out of sight (the activity stopped): the decoders go, and what the player says does not change.
        player.standBy()
        awaitTrue("the decoders to be released") { codecsHeld() == 0 }
        val standing = player.state.value
        assertEquals(PlayerStatus.PAUSED, standing.status)
        assertTrue(abs(standing.positionMs - paused.positionMs) < 250, "kept ${standing.positionMs}, paused at ${paused.positionMs}")
        assertEquals(paused.selectedAudioId, standing.selectedAudioId)

        // Seen again: paused where it was, with its decoders, and Play goes on from there.
        player.wake()
        awaitTrue("the decoders to come back") { codecsHeld() > 0 }
        assertEquals(PlayerStatus.PAUSED, player.state.value.status)
        assertTrue(abs(player.state.value.positionMs - paused.positionMs) < 500, "resumed at ${player.state.value.positionMs}, left at ${paused.positionMs}")
        player.play()
        player.await("playback to go on") { it.status == PlayerStatus.PLAYING && it.positionMs > paused.positionMs + 500 }

        // Play while out of sight (a remote's or headset's play key reaching the session in the background) is not
        // obeyed: Reflux does not play in the background, and does not take the decoders back for it.
        player.pause()
        val left = player.await("pause") { it.status == PlayerStatus.PAUSED }
        player.standBy()
        awaitTrue("the decoders to be released") { codecsHeld() == 0 }
        player.play()
        val audio = context.getSystemService(android.media.AudioManager::class.java)
        for (action in listOf(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.ACTION_UP)) {
            audio.dispatchMediaKeyEvent(android.view.KeyEvent(action, android.view.KeyEvent.KEYCODE_MEDIA_PLAY))
        }
        Thread.sleep(3_000)
        assertEquals(0, codecsHeld(), "no decoder was taken back")
        assertEquals(PlayerStatus.PAUSED, player.state.value.status)
        assertTrue(abs(player.state.value.positionMs - left.positionMs) < 250, "the place did not move")

        // Seen again, Play is the viewer's once more.
        player.wake()
        player.play()
        player.await("playback once seen") { it.status == PlayerStatus.PLAYING && it.positionMs > left.positionMs + 500 }
    }

    private fun awaitTrue(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!condition()) {
            assertTrue(System.currentTimeMillis() < deadline, "timed out waiting for $what")
            Thread.sleep(200)
        }
    }

    @Test
    fun aMissingFileIsSaidToBeUnreachable() {
        val world = world("missing")
        val player = player(world)
        player.load(PlaybackRequest(PlaybackTarget(File(world.media, "gone.mp4").toURI().toString()), "Gone"))
        val failed = player.await("the failure") { it.status == PlayerStatus.ERROR }
        assertEquals(PlaybackFailure.UNREACHABLE, failed.failure)
        assertNotNull(failed.error)
    }

    @Test
    fun watchingOnTheDeviceMovesTheWorksProgressAndPlaybackResumesThere() = runBlocking {
        val world = world("resume")
        world.clip("Movies/Night Drive (2021)/Night Drive (2021).mp4")
        val library = world.environment.library
        world.environment.pipeline.refresh()
        val movie = world.movie("Night Drive")

        // First viewing: from the start, on to 1:40, and out a little later.
        val first = assertNotNull(library.planPlayback(movie, world.environment.capabilities, world.sources))
        assertEquals(null, first.request.startPositionMs)
        val player = player(world)
        val controller = PlaybackController(player, movie, SyncingWatchReporter(library, first.version.version, first.source, scope), TrackPreferences(listOf("en")))
        controller.start(scope, first.request)
        player.await("playback to start") { it.status == PlayerStatus.PLAYING }
        player.seekTo(100_000)
        player.await("playback past the seek") { it.status == PlayerStatus.PLAYING && it.positionMs > 101_000 }
        controller.stop()
        player.close()

        // The progress is the work's, with the timeline of the copy that played.
        val state = assertNotNull(library.watchState(movie))
        assertTrue(state.positionMs in 101_000L..110_000L, "about 1:41: ${state.positionMs}")
        assertEquals(first.version.version.id, state.versionId)
        assertTrue(abs(state.durationMs!! - 180_000) < 2_000)

        // Second viewing: the plan starts there, and so does the engine.
        val second = assertNotNull(library.planPlayback(movie, world.environment.capabilities, world.sources))
        assertEquals(ResumeBasis.SAME_VERSION, second.resume.basis)
        assertEquals(state.positionMs, second.request.startPositionMs)
        val again = player(world)
        again.load(second.request)
        val resumed = again.await("playback to resume") { it.status == PlayerStatus.PLAYING }
        assertTrue(resumed.positionMs >= state.positionMs - 1_500 && resumed.positionMs < state.positionMs + 5_000, "resumed at ${resumed.positionMs}, left at ${state.positionMs}")
    }

    /**
     * The whole promise against a real Jellyfin server, when one is named (`-Preflux.jellyfin=http://10.0.2.2:8096`
     * reaches a server on the machine running the emulator; `tools/ci/jellyfin-test-server.sh` starts one):
     * a film watched from this device's storage to 00:30:12 continues at 00:30:12 from the server, and what
     * is then watched from the server continues from the local file. Real engine, real database, real server.
     */
    @Test
    fun aFilmContinuesBetweenThisDeviceAndARealJellyfinServer() {
        val url = InstrumentationRegistry.getArguments().getString("refluxJellyfin").orEmpty()
        assumeTrue("no Jellyfin server named (-Preflux.jellyfin=<url>)", url.isNotBlank())
        runBlocking { continueWithJellyfin(url) }
    }

    private suspend fun continueWithJellyfin(url: String) {
        val world = world("jellyfin")
        val http = world.environment.http
        val server = JellyfinSource(JellyfinSource.connect(url, "reflux", "reflux-test", http, deviceId = "reflux-android-test"), http, "Jellyfin")
        val film = server.catalog().toList().first { entry -> entry.versions.any { (it.stream.durationMs ?: 0) >= 35 * MINUTE } }
        val path = film.versions.first().path
        server.setPlayed(path, false)

        // The same film on this device's storage: the server's own file, fetched once.
        val stream = server.playbackTarget(path)
        val bytes = http.send(HttpRequest(stream.uri, headers = stream.headers)).body
        File(world.media, "Movies/Night Drive (2021)").mkdirs()
        File(world.media, "Movies/Night Drive (2021)/Night Drive (2021).mkv").writeBytes(bytes)
        world.add(server, "live")
        val library = world.environment.library
        world.environment.pipeline.refresh()
        val movie = world.movie("Night Drive")
        assertEquals(2, library.versions(movie).size, "one work: the local file and the server's copy")
        val device = world.environment.capabilities

        // Played from the device's storage to 00:30:12.
        val local = assertNotNull(library.planPlayback(movie, device, world.sources))
        assertEquals(world.folder.descriptor.id, local.version.version.location.sourceId, "the local copy plays while both are there")
        val leftAt = 30 * MINUTE + 12_000
        watch(world, movie, local, leftAt)
        assertTrue(abs(library.watchState(movie)!!.positionMs - leftAt) < 4_000)

        // The local file is no longer there. Play continues from the server, where the viewer was.
        world.tree.present = false
        world.environment.pipeline.refresh()
        val remote = assertNotNull(library.planPlayback(movie, device, world.sources))
        assertEquals(server.descriptor.id, remote.version.version.location.sourceId)
        assertEquals(ResumeBasis.SAME_TIMELINE, remote.resume.basis)
        val streaming = player(world)
        streaming.load(remote.request)
        val resumed = streaming.await("the server's stream to play", timeoutMs = 60_000) { it.status == PlayerStatus.PLAYING }
        assertTrue(abs(resumed.positionMs - leftAt) < 6_000, "resumed from the server at ${resumed.positionMs / 1000} s, left at ${leftAt / 1000} s")
        streaming.close()

        // Watched on from the server to 00:33:00, which the server also hears of.
        val further = 33 * MINUTE
        watch(world, movie, remote, further)
        delay(1_500)
        val onServer = server.catalog().toList().single { entry -> entry.versions.any { it.path == path } }.userState!!
        assertTrue(abs(library.watchState(movie)!!.positionMs - further) < 4_000)
        assertTrue(abs(onServer.positionMs - library.watchState(movie)!!.positionMs) < 3_000, "the server has ${onServer.positionMs / 1000} s")

        // Back with the local file, it continues from there.
        world.tree.present = true
        world.environment.pipeline.refresh()
        val home = assertNotNull(library.planPlayback(movie, device, world.sources))
        assertEquals(world.folder.descriptor.id, home.version.version.location.sourceId)
        assertTrue(abs(home.request.startPositionMs!! - further) < 4_000, "continues locally at ${home.request.startPositionMs}")
        server.setPlayed(path, false)
    }

    /** Plays [plan] on a real engine, jumps close to [untilMs], lets it play past it, and leaves, as a viewer would. */
    private suspend fun watch(world: DeviceWorld, movie: dev.reflux.core.model.MediaId, plan: dev.reflux.library.PlaybackPlan, untilMs: Long) {
        val player = player(world)
        val controller = PlaybackController(player, movie, SyncingWatchReporter(world.environment.library, plan.version.version, plan.source, scope), TrackPreferences(listOf("en")))
        controller.start(scope, plan.request)
        player.await("playback to start", timeoutMs = 60_000) { it.status == PlayerStatus.PLAYING }
        player.seekTo(untilMs - 2_000)
        player.await("playback to reach ${untilMs / 1000} s", timeoutMs = 60_000) { it.status == PlayerStatus.PLAYING && it.positionMs >= untilMs }
        controller.stop()
        player.close()
        delay(500)
    }
}
