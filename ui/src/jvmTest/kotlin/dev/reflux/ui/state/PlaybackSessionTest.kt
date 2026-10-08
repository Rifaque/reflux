package dev.reflux.ui.state

import dev.reflux.core.input.SemanticAction
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.ui.FakePlatform
import dev.reflux.ui.TestWorld
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSessionTest {
    private class Playing(val world: TestWorld, val platform: FakePlatform, val app: AppState) {
        val session: PlaybackSession get() = app.playback!!
        val player get() = platform.players.last()
    }

    private suspend fun TestScope.playing(show: String, episode: Int): Playing {
        val world = TestWorld().apply { populate() }
        val platform = FakePlatform()
        val app = appState(world, platform)
        app.play(world.episodes(show)[episode].item.id)
        runCurrent()
        return Playing(world, platform, app)
    }

    @Test
    fun theNextEpisodeStartsAfterACountdown() = runTest {
        val playing = playing("Breaking Bad", 0)
        val session = playing.session
        assertEquals("S1 · E2 · Cat's in the Bag...", session.next?.let { playing.world.library.heading(it.item).second })
        assertNull(session.previous)

        playing.player.end()
        runCurrent()
        val prompt = assertNotNull(session.upNext)
        assertFalse(prompt.asks)
        assertEquals(10_000, prompt.remainingMs)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(5_000, session.upNext?.remainingMs)
        advanceTimeBy(5_100)
        runCurrent()

        assertNull(session.upNext)
        assertEquals(2, playing.player.requests.size)
        assertEquals("S1 · E2 · Cat's in the Bag...", session.subtitle)
        assertTrue(playing.world.episodes("Breaking Bad")[0].watchState?.completed == true, "the finished episode is watched")
        assertEquals(1, playing.platform.players.size, "the same engine keeps playing")
    }

    @Test
    fun theCountdownCanBeCancelledOrSkipped() = runTest {
        val playing = playing("Breaking Bad", 0)
        playing.player.end()
        runCurrent()
        playing.session.cancelUpNext()
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(1, playing.player.requests.size)
        assertNotNull(playing.app.playback, "cancelling stays in the player")

        val again = playing("Breaking Bad", 0)
        again.player.end()
        runCurrent()
        assertTrue(again.session.handle(SemanticAction.PLAY_PAUSE))
        runCurrent()
        assertEquals(2, again.player.requests.size, "Play during the countdown starts the next episode now")
    }

    @Test
    fun aSleepingViewerIsAskedBeforeTheWholeSeasonPlays() = runTest {
        val playing = playing("Breaking Bad", 0)
        repeat(3) {
            playing.player.end()
            runCurrent()
            advanceTimeBy(10_100)
            runCurrent()
        }
        assertEquals(4, playing.player.requests.size)

        playing.player.end()
        runCurrent()
        assertTrue(assertNotNull(playing.session.upNext).asks)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(4, playing.player.requests.size, "nothing plays until the viewer answers")

        playing.session.continueWatching()
        runCurrent()
        assertEquals(5, playing.player.requests.size)
        assertEquals("S1 · E5 · Gray Matter", playing.session.subtitle)
    }

    @Test
    fun theLastEpisodeEndsThePlayer() = runTest {
        val playing = playing("Severance", 3)
        val player = playing.player
        player.end()
        runCurrent()
        assertNull(playing.app.playback)
        assertTrue(player.closed)
        assertTrue(playing.world.episodes("Severance")[3].watchState?.completed == true)
    }

    @Test
    fun episodesCanBeSteppedThroughDirectly() = runTest {
        val playing = playing("Breaking Bad", 1)
        playing.player.playTo(20 * 60_000L)
        runCurrent()
        assertTrue(playing.session.handle(SemanticAction.NEXT))
        runCurrent()
        assertEquals("S1 · E3 · ...And the Bag's in the River", playing.session.subtitle)
        assertEquals(20 * 60_000L, playing.world.episodes("Breaking Bad")[1].watchState?.positionMs, "the episode left behind keeps its position")

        // Previous restarts the current episode unless it has barely begun.
        playing.player.playTo(20 * 60_000L)
        playing.session.handle(SemanticAction.PREVIOUS)
        runCurrent()
        assertEquals(0L, playing.player.state.value.positionMs)
        assertEquals(2, playing.player.requests.size)
        playing.session.handle(SemanticAction.PREVIOUS)
        runCurrent()
        assertEquals("S1 · E2 · Cat's in the Bag...", playing.session.subtitle)
    }

    @Test
    fun semanticActionsDriveTheEngine() = runTest {
        val playing = playing("Breaking Bad", 0)
        val session = playing.session
        val player = playing.player
        val before = session.interactions

        session.handle(SemanticAction.SEEK_FORWARD)
        assertEquals(10_000L, player.state.value.positionMs)
        session.handle(SemanticAction.NAVIGATE_DOWN)
        assertEquals(95, player.state.value.volume)
        session.handle(SemanticAction.MUTE)
        assertTrue(player.state.value.muted)
        session.handle(SemanticAction.SPEED_UP)
        assertEquals(1.25, player.state.value.speed)
        session.selectSubtitle("1")
        assertEquals("1", player.state.value.selectedSubtitleId)
        assertTrue(session.interactions > before, "every action reveals the controls")

        session.handle(SemanticAction.FULLSCREEN)
        assertTrue(playing.platform.fullscreen)
        session.handle(SemanticAction.BACK)
        runCurrent()
        assertFalse(playing.platform.fullscreen, "leaving the player restores the window")
        assertEquals(PlayerStatus.IDLE, player.state.value.status)
        assertTrue(player.closed)
    }

    @Test
    fun volumeCarriesOverToTheNextSession() = runTest {
        val playing = playing("Breaking Bad", 0)
        playing.session.setVolume(40)
        playing.session.close()
        runCurrent()

        playing.app.play(playing.world.movie("Heat").item.id)
        runCurrent()
        assertEquals(2, playing.platform.players.size)
        assertEquals(40, playing.player.state.value.volume)
    }

    @Test
    fun aPlaybackFailureIsShownAndTheUserCanLeave() = runTest {
        val playing = playing("Breaking Bad", 0)
        playing.player.fail("unsupported format")
        runCurrent()
        assertTrue(assertNotNull(playing.session.error).contains("unsupported format"))
        assertNotNull(playing.app.playback, "the player stays open to explain")
        playing.session.close()
        runCurrent()
        assertNull(playing.app.playback)
    }
}
