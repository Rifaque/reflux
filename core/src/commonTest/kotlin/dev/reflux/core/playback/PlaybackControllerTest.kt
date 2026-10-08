package dev.reflux.core.playback

import dev.reflux.core.input.SemanticAction
import dev.reflux.core.model.MediaId
import dev.reflux.core.source.PlaybackTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakePlayer : Player {
    override val state = MutableStateFlow(PlayerState())
    override val capabilities: DeviceCapabilities = Devices.laptop1080
    val commands = mutableListOf<String>()

    override fun load(request: PlaybackRequest) {
        commands += "load:${request.startPositionMs}"
        state.value = PlayerState(status = PlayerStatus.LOADING)
    }
    override fun play() { commands += "play"; state.value = state.value.copy(status = PlayerStatus.PLAYING) }
    override fun pause() { commands += "pause"; state.value = state.value.copy(status = PlayerStatus.PAUSED) }
    override fun seekTo(positionMs: Long) { commands += "seek:$positionMs"; state.value = state.value.copy(positionMs = positionMs) }
    override fun setSpeed(speed: Double) { commands += "speed:$speed"; state.value = state.value.copy(speed = speed) }
    override fun setVolume(volume: Int) { commands += "volume:$volume"; state.value = state.value.copy(volume = volume) }
    override fun setMuted(muted: Boolean) { commands += "muted:$muted"; state.value = state.value.copy(muted = muted) }
    override fun selectAudio(trackId: String) { commands += "audio:$trackId"; state.value = state.value.copy(selectedAudioId = trackId) }
    override fun selectSubtitle(trackId: String?) { commands += "sub:$trackId"; state.value = state.value.copy(selectedSubtitleId = trackId) }
    override fun stop() { commands += "stop"; state.value = PlayerState() }
    override fun close() = Unit
}

private class RecordingReporter : WatchReporter {
    val events = mutableListOf<String>()
    override fun progress(itemId: MediaId, positionMs: Long, durationMs: Long?) { events += "progress:$positionMs" }
    override fun stopped(itemId: MediaId, positionMs: Long, durationMs: Long?) { events += "stopped:$positionMs" }
}

class PlaybackControllerTest {
    private val player = FakePlayer()
    private val reporter = RecordingReporter()
    private var skipped: Boolean? = null
    private val controller = PlaybackController(player, MediaId("m"), reporter, TrackPreferences(listOf("en"))) {
        skipped = it
        true
    }
    private val request = PlaybackRequest(PlaybackTarget("file:///m.mkv"), "M", startPositionMs = 5_000)

    private fun TestScope.started() {
        controller.start(backgroundScope, request)
    }

    private fun playing(position: Long, tracks: List<PlayerTrack> = emptyList()) {
        player.state.value = player.state.value.copy(
            status = PlayerStatus.PLAYING, positionMs = position, durationMs = 100_000, tracks = tracks,
        )
    }

    @Test
    fun reportsProgressPeriodicallyAndOnPause() = runTest(UnconfinedTestDispatcher()) {
        started()
        playing(5_000)
        playing(9_000)
        playing(15_000)
        playing(16_000)
        controller.handle(SemanticAction.PLAY_PAUSE)
        assertEquals(listOf("progress:5000", "progress:15000", "progress:16000"), reporter.events)
    }

    @Test
    fun endReportsCompletionOnce() = runTest(UnconfinedTestDispatcher()) {
        started()
        playing(99_000)
        player.state.value = player.state.value.copy(status = PlayerStatus.ENDED)
        controller.stop()
        assertEquals(listOf("progress:99000", "stopped:100000"), reporter.events)
    }

    @Test
    fun appliesDefaultTracksOnce() = runTest(UnconfinedTestDispatcher()) {
        started()
        val tracks = listOf(
            PlayerTrack("a1", TrackType.AUDIO, language = "jpn", default = true),
            PlayerTrack("a2", TrackType.AUDIO, language = "eng"),
            PlayerTrack("s1", TrackType.SUBTITLE, language = "eng", forced = true),
        )
        playing(5_000, tracks)
        playing(6_000, tracks)
        assertEquals(listOf("audio:a2", "sub:s1"), player.commands.filter { it.startsWith("audio") || it.startsWith("sub") })
    }

    @Test
    fun semanticActions() = runTest(UnconfinedTestDispatcher()) {
        started()
        playing(50_000)
        assertTrue(controller.handle(SemanticAction.SEEK_FORWARD))
        assertEquals(60_000, player.state.value.positionMs)
        controller.handle(SemanticAction.SEEK_BACKWARD)
        controller.handle(SemanticAction.SEEK_BACKWARD)
        assertEquals(40_000, player.state.value.positionMs)
        controller.handle(SemanticAction.SPEED_UP)
        assertEquals(1.25, player.state.value.speed)
        controller.handle(SemanticAction.VOLUME_DOWN)
        assertEquals(95, player.state.value.volume)
        controller.handle(SemanticAction.MUTE)
        assertTrue(player.state.value.muted)
        assertFalse(controller.handle(SemanticAction.NAVIGATE_UP))
    }

    @Test
    fun previousRestartsUnlessNearTheStart() = runTest(UnconfinedTestDispatcher()) {
        started()
        playing(60_000)
        controller.handle(SemanticAction.PREVIOUS)
        assertEquals(0, player.state.value.positionMs)
        assertEquals(null, skipped)
        controller.handle(SemanticAction.PREVIOUS)
        assertEquals(false, skipped)
        controller.handle(SemanticAction.NEXT)
        assertEquals(true, skipped)
    }

    @Test
    fun speedStepsAreBounded() {
        assertEquals(2.0, PlaybackController.nextSpeed(2.0, up = true))
        assertEquals(0.5, PlaybackController.nextSpeed(0.5, up = false))
        assertEquals(1.0, PlaybackController.nextSpeed(1.1, up = false))
    }
}
