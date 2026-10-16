package dev.reflux.ui.state

import dev.reflux.core.input.SemanticAction
import dev.reflux.core.model.Availability
import dev.reflux.core.model.MediaId
import dev.reflux.core.playback.PlaybackFailure
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.ui.FakePlatform
import dev.reflux.ui.FakePlayer
import dev.reflux.ui.TestWorld
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A source that goes silent during playback (DECISIONS.md, 2026-10-16): thirty seconds of the viewer
 * waiting, then a stated failure with Try again and the way back. Time is the test scheduler's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SourceSilenceTest {
    private class Playing(val world: TestWorld, val platform: FakePlatform, val app: AppState, val itemId: MediaId) {
        val session: PlaybackSession get() = app.playback!!
        val player: FakePlayer get() = platform.players.last()
        val watch get() = world.library.watchState(itemId)
    }

    private suspend fun TestScope.playing(itemId: (TestWorld) -> MediaId = { it.episodes("Breaking Bad")[0].item.id }): Playing {
        val world = TestWorld().apply { populate() }
        val platform = FakePlatform()
        val app = appState(world, platform)
        val id = itemId(world)
        app.play(id)
        runCurrent()
        return Playing(world, platform, app, id)
    }

    /** Plays to [positionMs] and then runs out of data there. */
    private fun TestScope.stallAt(playing: Playing, positionMs: Long) {
        playing.player.playTo(positionMs)
        runCurrent()
        playing.player.buffer()
        runCurrent()
    }

    private fun TestScope.wait(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    /** Waits in real time for work the library does on I/O threads (checking a folder, rescanning it). */
    private fun TestScope.awaitReal(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out waiting for $what" }
            Thread.sleep(20)
            runCurrent()
        }
    }

    @Test
    fun aStallShorterThanTheLimitIsOnlyWaitingAndAnEndedWaitRestartsTheCount() = runTest {
        val playing = playing()
        stallAt(playing, 20 * 60_000L)
        wait(29_000)
        assertNull(playing.session.error, "waiting shows as waiting, and nothing else")

        playing.player.playTo(20 * 60_000L + 1_000)
        runCurrent()
        assertNull(playing.session.error)

        // A second wait starts from nothing: 29 s of it is still only waiting, though 58 s were waited in all.
        playing.player.buffer()
        runCurrent()
        wait(29_000)
        assertNull(playing.session.error)
        wait(1_000)
        assertNotNull(playing.session.error)
    }

    @Test
    fun thirtySecondsOfWaitingIsAStatedFailureOnceWithTryAgainAndTheWayBack() = runTest {
        val playing = playing()
        stallAt(playing, 20 * 60_000L)
        wait(30_000)
        val error = assertNotNull(playing.session.error)
        assertEquals(Problems.SOURCE_STOPPED, error.message)
        assertTrue(error.canRetry, "Try again is offered")
        assertEquals(1, playing.platform.reports.size)
        assertNotNull(playing.app.playback, "the player stays, where it was")

        // The engine keeps reporting while it waits; time keeps passing. The failure is stated once.
        repeat(5) {
            playing.player.buffer()
            playing.player.seekTo(20 * 60_000L)
            wait(30_000)
        }
        assertSame(error, playing.session.error)
        assertEquals(1, playing.platform.reports.size)

        playing.session.handle(SemanticAction.BACK)
        runCurrent()
        assertNull(playing.app.playback, "back is the way back")
    }

    @Test
    fun timePausedAndTimePlayingFromTheBufferDoNotCount() = runTest {
        val playing = playing()
        // Playing from what is buffered, however long, is not waiting.
        playing.player.playTo(10 * 60_000L)
        runCurrent()
        wait(5 * 60_000L)
        assertNull(playing.session.error)

        playing.player.buffer()
        runCurrent()
        wait(20_000)
        playing.session.handle(SemanticAction.PLAY_PAUSE)
        runCurrent()
        assertEquals(PlayerStatus.PAUSED, playing.player.state.value.status)
        wait(10 * 60_000L)
        assertNull(playing.session.error, "a paused player is not waiting")

        // Playing again with still no data is a new wait.
        playing.player.buffer()
        runCurrent()
        wait(29_000)
        assertNull(playing.session.error)
        wait(1_000)
        assertNotNull(playing.session.error)
    }

    @Test
    fun anEnginesOwnEarlierFailureIsShownAtOnceInTheSameWords() = runTest {
        val playing = playing()
        stallAt(playing, 20 * 60_000L)
        wait(2_000)
        // Media3 gives up through its own read timeout; it is not held back to match the limit.
        playing.player.fail("ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT: read timed out", PlaybackFailure.UNREACHABLE)
        runCurrent()
        val error = assertNotNull(playing.session.error)
        assertEquals(Problems.SOURCE_STOPPED, error.message)
        assertTrue(error.canRetry)
        assertEquals("ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT: read timed out", error.detail)

        wait(60_000)
        assertSame(error, playing.session.error, "and the waiting limit does not state it again")
        assertEquals(1, playing.platform.reports.size)
    }

    @Test
    fun dataReturningWhileTheFailureShowsClearsItAndPlaybackContinues() = runTest {
        val playing = playing()
        stallAt(playing, 20 * 60_000L)
        wait(45_000)
        assertNotNull(playing.session.error)

        playing.player.playTo(20 * 60_000L + 500)
        runCurrent()
        assertNull(playing.session.error)
        assertEquals(1, playing.player.requests.size, "nothing was started again")
        assertEquals(PlayerStatus.PLAYING, playing.player.state.value.status)

        // And a later stall is a new one, with its own thirty seconds.
        playing.player.buffer()
        runCurrent()
        wait(30_000)
        assertNotNull(playing.session.error)
        assertEquals(2, playing.platform.reports.size)
    }

    @Test
    fun tryAgainPlansAfreshFromThePositionKept() = runTest {
        val playing = playing()
        stallAt(playing, 20 * 60_000L)
        wait(30_000)
        val engines = playing.platform.players.size

        playing.session.handle(SemanticAction.SELECT)
        runCurrent()
        assertNull(playing.session.error)
        assertEquals(2, playing.player.requests.size, "planned and loaded again")
        assertEquals(20 * 60_000L, playing.player.requests.last().startPositionMs)
        assertEquals(engines, playing.platform.players.size, "in the same player, without leaving it")
        assertEquals(PlayerStatus.PLAYING, playing.player.state.value.status)
        assertNotNull(playing.app.playback)
    }

    @Test
    fun tryAgainLetsBestVersionChooseAgainAndKeepsThePin() = runTest {
        // The 1080p copy is pinned: the user prefers it to the 2160p one Best Version would choose.
        val playing = playing { world ->
            world.movie("Heat").item.id.also { heat ->
                world.library.setPreferredVersion(heat, world.library.versions(heat).single { "1080p" in it.version.location.path }.version.id)
            }
        }
        val library = playing.world.library
        val versions = library.versions(playing.itemId).map { it.version }
        val hd = versions.single { "1080p" in it.location.path }
        val uhd = versions.single { "2160p" in it.location.path }

        // Try again chooses the pinned copy again while it is there.
        assertTrue("1080p" in playing.player.requests.last().target.uri)
        stallAt(playing, 30 * 60_000L)
        wait(30_000)
        awaitReal("the source check") { playing.app.checking.isEmpty() }
        playing.session.retry()
        runCurrent()
        assertTrue("1080p" in playing.player.requests.last().target.uri, "the pin still decides")

        // That copy goes; the check after the next failure finds it gone, and Try again plays the other.
        Files.delete(playing.world.source.resolve(hd.location.path))
        stallAt(playing, 31 * 60_000L)
        wait(30_000)
        awaitReal("the rescan to drop the missing copy") { library.versions(playing.itemId).none { it.version.id == hd.id } }
        playing.session.retry()
        runCurrent()
        val request = playing.player.requests.last()
        assertTrue(uhd.location.path.substringAfterLast('/') in request.target.uri, "another available copy: ${request.target.uri}")
        assertEquals(31 * 60_000L, request.startPositionMs, "from the position kept")
    }

    @Test
    fun aStalledPlaybackAsksForTheSourceToBeCheckedAndOnlyTheCheckSaysOffline() = runTest {
        val playing = playing()
        val sourceId = playing.world.source.descriptor.id
        stallAt(playing, 20 * 60_000L)
        wait(30_000)
        awaitReal("the source check") { playing.app.checking.isEmpty() }
        assertEquals(Availability.AVAILABLE, playing.world.library.source(sourceId)?.availability, "a source that answers stays online")

        // The folder goes away: the check after the next stall finds it gone.
        playing.player.playTo(20 * 60_000L + 1_000)
        runCurrent()
        val root = playing.world.source.resolve("")
        Files.move(root, root.resolveSibling("Media (away)"))
        playing.player.buffer()
        runCurrent()
        assertEquals(Availability.AVAILABLE, playing.world.library.source(sourceId)?.availability, "a stall alone says nothing about the source")
        wait(30_000)
        awaitReal("the source to be found away") { playing.world.library.source(sourceId)?.availability == Availability.UNAVAILABLE }
        assertEquals(Problems.SOURCE_STOPPED, playing.session.error?.message, "and the player's failure stays the player's")
    }

    @Test
    fun aStallNeverEndsAWorkWhereverItHappens() = runTest {
        val playing = playing()
        // Past 90% of the running time (50 min), where an ending would count as watched.
        stallAt(playing, 48 * 60_000L)
        wait(30_000)
        assertNotNull(playing.session.error)
        playing.watch.let { state ->
            assertFalse(state?.completed == true, "the stall is not an ending")
            assertEquals(0, state?.playCount ?: 0)
            assertEquals(48 * 60_000L, state?.positionMs)
        }

        playing.session.retry()
        runCurrent()
        playing.watch.let { state ->
            assertFalse(state?.completed == true, "trying again is not an ending either")
            assertEquals(0, state?.playCount ?: 0)
            assertEquals(48 * 60_000L, state?.positionMs, "the position stays")
        }
        assertEquals(48 * 60_000L, playing.player.requests.last().startPositionMs)

        // A real ending still counts, once.
        playing.player.end()
        runCurrent()
        playing.watch.let { state ->
            assertTrue(state?.completed == true)
            assertEquals(1, state?.playCount)
        }
    }

    @Test
    fun leavingAStalledPlaybackKeepsThePositionReachedUnderTheUsualRule() = runTest {
        val playing = playing()
        stallAt(playing, 20 * 60_000L)
        wait(30_000)
        playing.session.close().join()
        val state = assertNotNull(playing.watch)
        assertEquals(20 * 60_000L, state.positionMs)
        assertFalse(state.completed)
        assertEquals(0, state.playCount)
    }

    @Test
    fun aStreamCutOffMidPlaybackIsAFailureNotAnEnding() = runTest {
        val playing = playing()
        playing.player.playTo(20 * 60_000L)
        runCurrent()
        playing.player.fail("the stream ended before the end of the media", PlaybackFailure.UNREACHABLE)
        runCurrent()
        assertEquals(Problems.SOURCE_STOPPED, playing.session.error?.message)
        playing.session.close().join()
        val state = assertNotNull(playing.watch)
        assertEquals(20 * 60_000L, state.positionMs)
        assertFalse(state.completed)
    }

    @Test
    fun aPlaybackThatNeverStartsCanBeTriedAgainWithoutLosingWhereTheViewerWas() = runTest {
        val playing = playing()
        stallAt(playing, 20 * 60_000L)
        wait(30_000)

        // Trying again, the engine never gets going: that is the limit for starting, and it is said so.
        playing.player.starts = false
        playing.session.retry()
        runCurrent()
        assertEquals(PlayerStatus.LOADING, playing.player.state.value.status)
        wait(29_000)
        assertNull(playing.session.error)
        wait(1_000)
        val error = assertNotNull(playing.session.error)
        assertEquals(Problems.PLAYBACK_STALLED, error.message)
        assertTrue(error.canRetry)

        // Nothing was played on that attempt, so nothing about it is recorded.
        playing.player.starts = true
        playing.session.retry()
        runCurrent()
        assertEquals(20 * 60_000L, playing.player.requests.last().startPositionMs)
        assertEquals(20 * 60_000L, playing.watch?.positionMs)
        assertEquals(PlayerStatus.PLAYING, playing.player.state.value.status)
    }
}
