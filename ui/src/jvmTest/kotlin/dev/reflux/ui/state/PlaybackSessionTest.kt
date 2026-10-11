package dev.reflux.ui.state

import dev.reflux.core.input.Key
import dev.reflux.core.input.SemanticAction
import dev.reflux.core.playback.PlaybackFailure
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
        assertEquals("unsupported format", assertNotNull(playing.session.error).detail)
        assertNotNull(playing.app.playback, "the player stays open to explain")
        playing.session.close()
        runCurrent()
        assertNull(playing.app.playback)
    }

    @Test
    fun failuresAreExplainedInTheUsersTermsAndKeptForDiagnostics() = runTest {
        val playing = playing("Breaking Bad", 0)
        playing.player.fail("loading failed", PlaybackFailure.UNREACHABLE)
        runCurrent()
        val error = assertNotNull(playing.session.error)
        assertTrue("moved or deleted" in error.message)
        assertEquals("loading failed", error.detail)
        assertTrue(playing.platform.reports.single().let { "Breaking Bad" in it && "UNREACHABLE" in it && "loading failed" in it })

        val unsupported = playing("Breaking Bad", 0)
        unsupported.player.fail("unrecognized file format", PlaybackFailure.UNSUPPORTED)
        runCurrent()
        assertTrue("format" in assertNotNull(unsupported.session.error).message)
    }

    @Test
    fun aFileThatDisappearedLeavesTheLibraryAfterItFailsToPlay() = runTest {
        val playing = playing("Severance", 0)
        val episode = playing.world.episodes("Severance")[0].item.id
        java.nio.file.Files.delete(playing.world.source.resolve("Severance/Season 1/Severance.S01E01.2160p.mkv"))

        playing.player.fail("loading failed", PlaybackFailure.UNREACHABLE)
        runCurrent()
        // The rescan reads the folder on real I/O threads.
        val deadline = System.currentTimeMillis() + 10_000
        while (playing.world.episodes("Severance").any { it.item.id == episode } && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            runCurrent()
        }
        assertEquals(3, playing.world.episodes("Severance").size, "the library no longer offers the missing episode")
    }

    @Test
    fun afterAFailureEveryWayOnIsTheWayOut() = runTest {
        val playing = playing("Breaking Bad", 0)
        playing.player.fail("no audio or video data played", PlaybackFailure.UNSUPPORTED)
        runCurrent()
        playing.session.handle(SemanticAction.MENU)
        assertNull(playing.session.menu, "no menus for something that is not playing")
        playing.session.handle(SemanticAction.PLAY_PAUSE)
        runCurrent()
        assertNull(playing.app.playback)
    }

    @Test
    fun menusAreDrivenBySemanticActions() = runTest {
        val playing = playing("Breaking Bad", 0)
        val session = playing.session
        val player = playing.player
        assertEquals(listOf(PlayerMenu.CHAPTERS, PlayerMenu.AUDIO, PlayerMenu.SUBTITLES, PlayerMenu.SPEED), session.menus())

        session.handle(SemanticAction.MENU)
        assertEquals(PlayerMenu.SUBTITLES, session.menu)
        assertEquals(listOf("Off" to true, "English" to false), session.options(PlayerMenu.SUBTITLES).take(2).map { it.label to it.selected })
        assertEquals(0, session.highlighted, "a menu opens on what is in effect")
        session.handle(SemanticAction.NAVIGATE_UP)
        assertEquals(0, session.highlighted)
        repeat(5) { session.handle(SemanticAction.NAVIGATE_DOWN) }
        assertEquals(2, session.highlighted, "the highlight stops at the last option")
        session.handle(SemanticAction.NAVIGATE_UP)
        session.handle(SemanticAction.SELECT)
        assertEquals("1", player.state.value.selectedSubtitleId)
        assertNull(session.menu)

        session.handle(SemanticAction.MENU)
        assertEquals(1, session.highlighted)
        session.handle(SemanticAction.NAVIGATE_RIGHT)
        assertEquals(PlayerMenu.SPEED, session.menu)
        session.handle(SemanticAction.NAVIGATE_RIGHT)
        assertEquals(PlayerMenu.SPEED, session.menu, "there is no menu past the last")
        assertEquals("Normal", session.options(PlayerMenu.SPEED)[session.highlighted].label)
        session.handle(SemanticAction.NAVIGATE_DOWN)
        session.handle(SemanticAction.SELECT)
        assertEquals(1.25, player.state.value.speed)

        session.toggleMenu(PlayerMenu.AUDIO)
        session.handle(SemanticAction.BACK)
        assertNull(session.menu)
        assertNotNull(playing.app.playback, "Back closes the menu first")
        session.handle(SemanticAction.BACK)
        runCurrent()
        assertNull(playing.app.playback)
    }

    @Test
    fun keysMeanNavigationWhileAMenuIsOpenAndPlaybackOtherwise() = runTest {
        val playing = playing("Breaking Bad", 0)
        val player = playing.player
        playing.app.onKey(Key.ARROW_RIGHT)
        assertEquals(10_000L, player.state.value.positionMs)

        playing.session.toggleMenu(PlayerMenu.CHAPTERS)
        playing.app.onKey(Key.ARROW_DOWN)
        playing.app.onKey(Key.ARROW_RIGHT)
        assertEquals(PlayerMenu.AUDIO, playing.session.menu)
        assertEquals(10_000L, player.state.value.positionMs, "arrows moved through the menus instead of seeking")
        assertEquals(100, player.state.value.volume)
        playing.app.onKey(Key.ARROW_DOWN)
        playing.app.onKey(Key.ENTER)
        assertEquals("2", player.state.value.selectedAudioId)
        assertEquals(PlayerStatus.PLAYING, player.state.value.status, "Enter chose the track; it did not pause")

        playing.app.onKey(Key.ESCAPE)
        runCurrent()
        assertNull(playing.app.playback)
    }

    @Test
    fun chaptersCanBeJumpedTo() = runTest {
        val playing = playing("Breaking Bad", 0)
        playing.player.playTo(200_000)
        val chapters = playing.session.options(PlayerMenu.CHAPTERS)
        assertEquals(listOf("Intro", "Part One", "Part Two", "Credits"), chapters.map { it.label })
        assertEquals(listOf("1:00", "2:30", "25:00", "47:00"), chapters.map { it.detail })
        assertEquals("Part One", chapters.single { it.selected }.label)

        playing.session.toggleMenu(PlayerMenu.CHAPTERS)
        assertEquals(1, playing.session.highlighted)
        playing.session.choose(PlayerMenu.CHAPTERS, 2)
        assertEquals(25 * 60_000L, playing.player.state.value.positionMs)

        playing.player.chapters = emptyList()
        playing.session.playNext()
        runCurrent()
        assertFalse(PlayerMenu.CHAPTERS in playing.session.menus(), "no chapter menu for a file without chapters")
    }

    @Test
    fun aTrackThatIsGoneIsRefusedWithAWord() = runTest {
        val playing = playing("Breaking Bad", 0)
        playing.session.selectAudio("9")
        assertEquals("1", playing.player.state.value.selectedAudioId)
        assertTrue(assertNotNull(playing.app.notice).contains("no longer available"))

        playing.session.selectSubtitle("9")
        assertNull(playing.player.state.value.selectedSubtitleId)
        playing.session.selectSubtitle(null)
        assertNull(playing.player.state.value.selectedSubtitleId, "turning subtitles off is always possible")
    }

    @Test
    fun aNextEpisodeOnAnOfflineSourceIsExplained() = runTest {
        val playing = playing("Breaking Bad", 0)
        playing.world.registry.unregister(playing.world.source.descriptor.id)
        playing.session.playNext()
        runCurrent()
        assertNull(playing.app.playback)
        assertEquals("This can't be played right now. Media is offline.", playing.app.notice)
    }
}
