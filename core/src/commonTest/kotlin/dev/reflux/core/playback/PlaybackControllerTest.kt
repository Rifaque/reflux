package dev.reflux.core.playback

import dev.reflux.core.input.SemanticAction
import dev.reflux.core.model.MediaId
import dev.reflux.core.source.PlaybackTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
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
    override fun selectNoAudio() { commands += "audio:none"; state.value = state.value.copy(selectedAudioId = null) }
    override fun selectSubtitle(trackId: String?) { commands += "sub:$trackId"; state.value = state.value.copy(selectedSubtitleId = trackId) }
    override fun stop() { commands += "stop"; state.value = PlayerState() }
    override fun close() = Unit
}

private class RecordingReporter : WatchReporter {
    val events = mutableListOf<String>()
    override fun progress(itemId: MediaId, positionMs: Long, durationMs: Long?) { events += "progress:$positionMs" }
    override fun stopped(itemId: MediaId, positionMs: Long, durationMs: Long?) { events += "stopped:$positionMs" }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlaybackControllerTest {
    private val fileTracks = listOf(
        PlayerTrack("11", TrackType.AUDIO, "en", position = 0),
        PlayerTrack("12", TrackType.AUDIO, "fr", position = 1),
        PlayerTrack("21", TrackType.SUBTITLE, "en", position = 0),
        PlayerTrack("22", TrackType.SUBTITLE, "en", external = true, position = 0),
    )

    private fun TestScope.directed(directive: TrackDirective, tracks: List<PlayerTrack> = fileTracks) {
        controller.start(backgroundScope, request.copy(tracks = directive))
        runCurrent()
        player.state.value = player.state.value.copy(status = PlayerStatus.PLAYING, tracks = tracks)
        runCurrent()
    }

    @Test
    fun thePlansAudioTrackIsTheOneSelectedWhateverDefaultSelectionWouldTake() = runTest {
        directed(TrackDirective(AudioDirective.Track(1), SubtitleDirective.Off))
        assertTrue("audio:12" in player.commands, "${player.commands}")
    }

    @Test
    fun aViewersTrackTheEngineDoesNotHaveIsNotReplaced() = runTest {
        directed(TrackDirective(AudioDirective.Track(5, explicit = true), SubtitleDirective.Off))
        assertTrue("audio:none" in player.commands, "${player.commands}")
        assertTrue(player.commands.none { it == "audio:11" || it == "audio:12" })
    }

    @Test
    fun aPlansTrackTheEngineDoesNotHaveFallsBackToSelectionWhenAutomatic() = runTest {
        directed(TrackDirective(AudioDirective.Track(5), SubtitleDirective.Off))
        assertTrue("audio:11" in player.commands, "${player.commands}")
    }

    @Test
    fun aPlanWithoutAudioPlaysNone() = runTest {
        directed(TrackDirective(AudioDirective.None, SubtitleDirective.Off))
        assertTrue("audio:none" in player.commands, "${player.commands}")
    }

    @Test
    fun aConvertedStreamsOneTrackIsLeftAlone() = runTest {
        directed(TrackDirective(AudioDirective.EngineChoice, SubtitleDirective.Off), listOf(PlayerTrack("1", TrackType.AUDIO, "en")))
        assertTrue("audio:none" !in player.commands, "${player.commands}")
    }

    @Test
    fun thePlansSubtitleIsTheOneSelected() = runTest {
        directed(TrackDirective(AudioDirective.Track(0), SubtitleDirective.Embedded(0)))
        assertTrue("sub:21" in player.commands, "${player.commands}")
    }

    @Test
    fun anAttachedSubtitleIsFoundAmongTheAttachments() = runTest {
        directed(TrackDirective(AudioDirective.Track(0), SubtitleDirective.Attached(0)))
        assertTrue("sub:22" in player.commands, "${player.commands}")
    }

    @Test
    fun anAttachmentThatArrivesLateIsSelectedWhenItComes() = runTest {
        directed(TrackDirective(AudioDirective.Track(0), SubtitleDirective.Attached(0)), fileTracks.filterNot { it.external })
        assertTrue(player.commands.none { it.startsWith("sub:2") }, "${player.commands}")
        player.state.value = player.state.value.copy(tracks = fileTracks)
        runCurrent()
        assertTrue("sub:22" in player.commands, "${player.commands}")
    }

    @Test
    fun aSubtitleChosenMeanwhileIsNotTakenAwayByOneThatArrivesLate() = runTest {
        directed(TrackDirective(AudioDirective.Track(0), SubtitleDirective.Attached(0)), fileTracks.filterNot { it.external })
        player.selectSubtitle("21")
        runCurrent()
        player.state.value = player.state.value.copy(tracks = fileTracks)
        runCurrent()
        assertTrue("sub:22" !in player.commands, "${player.commands}")
    }

    @Test
    fun subtitlesOffStayOffWhereTheFileWouldTurnThemOn() = runTest {
        val tracks = fileTracks.map { if (it.id == "21") it.copy(forced = true) else it }
        directed(TrackDirective(AudioDirective.Track(0), SubtitleDirective.Off), tracks)
        assertTrue(player.commands.none { it == "sub:21" || it == "sub:22" }, "${player.commands}")
    }

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
    fun pauseWorksWhilePlaybackIsWaitingForASeekOrForData() = runTest(UnconfinedTestDispatcher()) {
        started()
        playing(5_000)
        player.state.value = player.state.value.copy(status = PlayerStatus.BUFFERING)
        controller.handle(SemanticAction.PLAY_PAUSE)
        assertEquals("pause", player.commands.last(), "buffering playback is playback: the button pauses it")
        controller.handle(SemanticAction.PLAY_PAUSE)
        assertEquals("play", player.commands.last())
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
    fun stoppingToPlayAgainRecordsProgressAndNeverAnEnding() = runTest(UnconfinedTestDispatcher()) {
        started()
        // Past 90%, waiting for data that does not come; Try again stops the playback to plan it afresh.
        playing(95_000)
        player.state.value = player.state.value.copy(status = PlayerStatus.BUFFERING)
        controller.stop(final = false)
        assertEquals(listOf("progress:95000", "progress:95000"), reporter.events, "no stop, so nothing can complete the work")
        controller.stop()
        assertEquals(2, reporter.events.size, "and the playback's record is closed")
    }

    @Test
    fun aPlaybackThatNeverGotGoingRecordsNothing() = runTest(UnconfinedTestDispatcher()) {
        // Still opening (an engine that never starts, a source that never answers), or failed to open: the
        // engine's position is not the viewer's, and recording it would lose where they were.
        started()
        controller.stop()
        player.state.value = PlayerState(status = PlayerStatus.ERROR, failure = PlaybackFailure.UNREACHABLE)
        started()
        player.state.value = PlayerState(status = PlayerStatus.ERROR, failure = PlaybackFailure.UNREACHABLE)
        controller.stop(final = false)
        assertEquals(emptyList(), reporter.events)

        started()
        playing(30_000)
        player.state.value = player.state.value.copy(status = PlayerStatus.ERROR, failure = PlaybackFailure.UNREACHABLE)
        controller.stop()
        assertEquals(listOf("progress:30000", "stopped:30000"), reporter.events, "one that played keeps the position it reached")
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
    fun aSeekTheEngineCannotDoIsNotSentToIt() {
        val refused = mutableListOf<Long>()
        val controller = PlaybackController(player, MediaId("m"), RecordingReporter(), TrackPreferences(listOf("en")), onSeekRefused = { refused += it })
        player.state.value = PlayerState(status = PlayerStatus.PLAYING, positionMs = 30_000, durationMs = 60_000, seekable = false)
        controller.handle(SemanticAction.SEEK_FORWARD)
        assertEquals(listOf(40_000L), refused)
        assertFalse(player.commands.any { it.startsWith("seek") }, "${player.commands}")

        player.state.value = player.state.value.copy(seekable = null)
        controller.handle(SemanticAction.SEEK_FORWARD)
        assertEquals(listOf("seek:40000"), player.commands, "an engine that has not said is asked")
    }

    @Test
    fun speedStepsAreBounded() {
        assertEquals(2.0, PlaybackController.nextSpeed(2.0, up = true))
        assertEquals(0.5, PlaybackController.nextSpeed(0.5, up = false))
        assertEquals(1.0, PlaybackController.nextSpeed(1.1, up = false))
    }
}
