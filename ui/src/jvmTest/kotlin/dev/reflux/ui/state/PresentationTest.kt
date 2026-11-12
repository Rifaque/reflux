package dev.reflux.ui.state

import androidx.compose.ui.geometry.Rect
import dev.reflux.core.input.Key
import dev.reflux.core.input.SemanticAction
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.library.createProfile
import dev.reflux.library.profiles
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
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * One playback session, several presentations (docs/PLAYBACK.md, "One session, several presentations"): the full
 * player and the mini-player are two ways of showing the same session. Moving between them makes no engine, plans
 * nothing, loads nothing, and changes neither what is recorded nor whose it is.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PresentationTest {
    private class Playing(val world: TestWorld, val platform: FakePlatform, val app: AppState) {
        val session: PlaybackSession get() = app.playback!!
        val player get() = platform.players.last()
        fun movie(title: String) = world.movie(title).item.id
    }

    private suspend fun TestScope.playing(platform: FakePlatform = FakePlatform(docksVideo = true), title: String = "Heat"): Playing {
        val world = TestWorld().apply { populate() }
        val app = appState(world, platform)
        runCurrent()
        app.play(world.movie(title).item.id)
        runCurrent()
        return Playing(world, platform, app)
    }

    @Test
    fun theMiniPlayerAndTheFullPlayerAreOneSession() = runTest {
        val playing = playing()
        val session = playing.session
        val player = playing.player
        player.playTo(40 * MINUTE)
        runCurrent()

        assertTrue(session.collapse())
        runCurrent()
        assertEquals(PlayerPresentation.MINI, session.presentation)
        assertSame(session, playing.app.playback, "the same session, shown another way")
        assertSame(session, playing.app.docked)
        assertNull(playing.app.watching, "nothing has the screen: the viewer is browsing")
        assertEquals(1, playing.platform.players.size, "no second engine")
        assertEquals(1, player.requests.size, "nothing was loaded again")
        assertFalse(player.closed)
        assertEquals(PlayerStatus.PLAYING, player.state.value.status, "and it never stopped playing")
        assertEquals(40 * MINUTE, player.state.value.positionMs)

        player.playTo(41 * MINUTE)
        session.expand()
        runCurrent()
        assertEquals(PlayerPresentation.FULL, session.presentation)
        assertSame(session, playing.app.watching)
        assertEquals(1, playing.platform.players.size)
        assertEquals(1, player.requests.size)
        assertEquals(41 * MINUTE, player.state.value.positionMs)

        // What it reports is what it would have reported had it never left the screen.
        session.collapse()
        player.playTo(42 * MINUTE)
        session.close()
        runCurrent()
        assertEquals(42 * MINUTE, playing.world.library.watchState(playing.movie("Heat"))?.positionMs)
        assertEquals(listOf(player), playing.platform.released.toList(), "the one engine, released once")
    }

    @Test
    fun whereThereIsNoDockThePlayerIsTheWholeWindowOrClosed() = runTest {
        // A shell that cannot place its surface, and a television whose shell could: neither has a mini-player.
        for (platform in listOf(FakePlatform(), FakePlatform(television = true, docksVideo = true))) {
            val playing = playing(platform)
            assertFalse(playing.app.miniPlayerAvailable)
            assertFalse(playing.session.collapse())
            assertTrue(playing.app.onKey(Key.I), "the key is the player's, and does nothing there")
            runCurrent()
            assertEquals(PlayerPresentation.FULL, playing.session.presentation)
            assertSame(playing.session, playing.app.watching)
            assertEquals(VideoFrame.Full, playing.app.videoFrame)
        }
    }

    @Test
    fun aFailureIsAnsweredWhereItIsNotPutAside() = runTest {
        val playing = playing()
        playing.player.fail("boom")
        runCurrent()
        assertNotNull(playing.session.error)
        assertFalse(playing.session.collapse(), "a failure is read and answered in the player")
        assertEquals(PlayerPresentation.FULL, playing.session.presentation)
    }

    @Test
    fun aFailureInTheMiniPlayerWaitsThereAndTheKeyBringsThePlayerBack() = runTest {
        val playing = playing()
        playing.session.collapse()
        playing.player.fail("boom")
        runCurrent()
        // Nothing takes the screen from someone who is browsing: the dock says so, and the player is a key away.
        assertEquals(PlayerPresentation.MINI, playing.session.presentation)
        assertNotNull(playing.session.error)
        assertTrue(playing.app.onKey(Key.I))
        assertEquals(PlayerPresentation.FULL, playing.session.presentation)
    }

    @Test
    fun whileItPlaysBesideThePageTheKeysAreThePages() = runTest {
        val playing = playing()
        val app = playing.app
        app.navigator.open(Destination.Details(playing.movie("Arrival")))
        playing.session.collapse()
        runCurrent()

        // Escape is Back for the page, and what plays beside it plays on.
        assertTrue(app.onKey(Key.ESCAPE))
        assertEquals(Destination.Home, app.navigator.current)
        assertSame(playing.session, app.playback)
        // Space and Enter are the focused control's, as anywhere while browsing; arrows move focus, and do not seek.
        assertFalse(app.onKey(Key.SPACE))
        app.onKey(Key.ARROW_RIGHT)
        assertEquals(PlayerStatus.PLAYING, playing.player.state.value.status)
        assertEquals(0, playing.player.state.value.positionMs)
        // Destinations are reached, which the full player does not let through.
        assertTrue(app.onKey(Key.SHORTCUT_2))
        assertEquals(Destination.Movies, app.navigator.current)

        // What only a player can mean still reaches it: media keys, mute, and the key that brings it back.
        assertTrue(app.onKey(Key.MEDIA_PLAY_PAUSE))
        assertEquals(PlayerStatus.PAUSED, playing.player.state.value.status)
        assertTrue(app.onKey(Key.MEDIA_PLAY_PAUSE))
        assertTrue(app.onKey(Key.M))
        assertTrue(playing.player.state.value.muted)
        assertTrue(app.onKey(Key.I))
        assertEquals(PlayerPresentation.FULL, playing.session.presentation)
        // And in the player, the same key puts it aside again.
        assertTrue(app.onKey(Key.I))
        assertEquals(PlayerPresentation.MINI, playing.session.presentation)
        assertTrue(app.onKey(Key.MEDIA_STOP))
        runCurrent()
        assertNull(app.playback)
    }

    @Test
    fun aLetterWithNothingPlayingIsStillJustALetter() = runTest {
        val world = TestWorld().apply { populate() }
        val app = appState(world, FakePlatform(docksVideo = true))
        runCurrent()
        assertFalse(app.onKey(Key.I))
        assertFalse(app.dispatch(SemanticAction.MINI_PLAYER))
    }

    @Test
    fun steppingAsideLeavesFullscreen() = runTest {
        val playing = playing()
        playing.platform.requestFullscreen(true)
        assertTrue(playing.session.collapse())
        assertFalse(playing.platform.fullscreen, "browsing happens in the window the viewer had")
        playing.session.expand()
        assertFalse(playing.platform.fullscreen, "and coming back does not take the screen unasked")
    }

    @Test
    fun anOpenListClosesWhenThePlayerStepsAside() = runTest {
        val playing = playing()
        playing.session.toggleMenu(PlayerMenu.AUDIO)
        playing.session.collapse()
        assertNull(playing.session.menu)
    }

    @Test
    fun playingSomethingElseReplacesTheSessionAndRecordsItsPlace() = runTest {
        val playing = playing()
        val first = playing.session
        val engine = playing.player
        engine.playTo(40 * MINUTE)
        first.collapse()
        runCurrent()

        playing.app.play(playing.movie("Arrival"))
        runCurrent()
        val second = assertNotNull(playing.app.playback)
        assertTrue(second !== first && second.title == "Arrival")
        assertEquals(PlayerPresentation.FULL, second.presentation, "what the viewer just chose has the screen")
        assertEquals(2, playing.platform.players.size)
        assertTrue(engine.closed, "one engine at a time: the first was released before the second was made")
        assertEquals(listOf(engine), playing.platform.released.toList())
        assertEquals(40 * MINUTE, playing.world.library.watchState(playing.movie("Heat"))?.positionMs)
    }

    @Test
    fun playOnWhatIsAlreadyPlayingBesideThePageBringsItBack() = runTest {
        val playing = playing()
        val session = playing.session
        session.collapse()
        playing.app.play(playing.movie("Heat"))
        runCurrent()
        assertSame(session, playing.app.playback)
        assertEquals(PlayerPresentation.FULL, session.presentation)
        assertEquals(1, playing.platform.players.size, "not started a second time")
    }

    @Test
    fun somethingThatCannotBePlayedLeavesWhatIsPlayingAlone() = runTest {
        val playing = playing()
        val session = playing.session
        session.collapse()
        playing.platform.failToStart = true
        playing.app.play(playing.movie("Arrival"))
        runCurrent()
        // The engine for the next could not be made; but the first was only given up for something that could play.
        assertEquals(1, playing.platform.players.size)
        assertNotNull(playing.app.notice)
    }

    @Test
    fun theFullPlayerStillKeepsEverythingElseFromStarting() = runTest {
        val playing = playing()
        playing.app.play(playing.movie("Arrival"))
        runCurrent()
        assertEquals("Heat", playing.app.playback?.title)
        assertEquals(1, playing.platform.players.size)
    }

    @Test
    fun aPlaybackInTheMiniPlayerStaysItsProfiles() = runTest {
        val playing = playing()
        val app = playing.app
        val library = playing.world.library
        val main = library.profiles().single().id
        val sam = library.createProfile("Sam")
        app.loadProfiles()
        val heat = playing.movie("Heat")

        playing.player.playTo(40 * MINUTE)
        playing.session.collapse()
        runCurrent()
        // Sam takes over while Main's film plays beside the page: it ends first, and is recorded as Main's.
        app.chooseProfile(app.profiles.first { it.id == sam })
        runCurrent()
        assertEquals(sam, app.activeProfile?.id)
        assertNull(app.playback, "a profile switch ends the session")
        assertTrue(playing.player.closed)
        assertEquals(40 * MINUTE, library.watchState(heat, main)?.positionMs, "what Main watched is Main's")
        assertNull(library.watchState(heat, sam), "and none of it is Sam's")
        assertEquals(VideoFrame.Full, app.videoFrame)
    }

    @Test
    fun whatIsRecordedFromTheDockIsRecordedForWhoeverStartedIt() = runTest {
        val playing = playing()
        val library = playing.world.library
        val main = library.profiles().single().id
        val heat = playing.movie("Heat")
        playing.session.collapse()
        playing.player.playTo(30 * MINUTE)
        // Long enough for the session's own periodic report, with nobody touching the player.
        advanceTimeBy(60_000)
        runCurrent()
        playing.session.recorded()
        assertEquals(30 * MINUTE, library.watchState(heat, main)?.positionMs)
    }

    @Test
    fun takingASourceAwayEndsWhatPlaysFromItFirst() = runTest {
        val playing = playing()
        val source = playing.world.source.descriptor.id
        playing.player.playTo(40 * MINUTE)
        playing.session.collapse()
        runCurrent()
        playing.app.removeSource(source)
        runCurrent()
        assertNull(playing.app.playback)
        assertTrue(playing.player.closed)
        assertEquals(listOf(source), playing.platform.removed.toList())
        assertEquals(40 * MINUTE, playing.world.library.watchState(playing.movie("Heat"))?.positionMs, "its place was recorded before the source went")
    }

    @Test
    fun theShellIsToldWhereThePictureGoes() = runTest {
        val playing = playing()
        val app = playing.app
        assertEquals(VideoFrame.Full, app.videoFrame)
        playing.session.collapse()
        assertEquals(VideoFrame.Full, app.videoFrame, "until the dock is laid out, the surface stays where it is")
        val slot = Rect(900f, 500f, 1220f, 680f)
        app.dockSlot = slot
        assertEquals(VideoFrame.Docked(slot), app.videoFrame)
        // A sheet over the page, and the question of who is watching: the picture is not left showing through them.
        app.offlineOpen = true
        assertEquals(VideoFrame.Hidden, app.videoFrame)
        app.offlineOpen = false
        assertEquals(VideoFrame.Docked(slot), app.videoFrame)
        app.askWhoIsWatching()
        runCurrent()
        assertEquals(VideoFrame.Hidden, app.videoFrame)
        assertEquals(PlayerStatus.PLAYING, playing.player.state.value.status, "out of sight, it plays on")
        app.dispatch(SemanticAction.BACK)
        assertEquals(VideoFrame.Docked(slot), app.videoFrame)
        playing.session.expand()
        assertEquals(VideoFrame.Full, app.videoFrame)
    }

    @Test
    fun theNextEpisodeFollowsInTheDock() = runTest {
        val platform = FakePlatform(docksVideo = true)
        val world = TestWorld().apply { populate() }
        val app = appState(world, platform)
        runCurrent()
        app.play(world.episodes("Breaking Bad")[0].item.id)
        runCurrent()
        val session = app.playback!!
        session.collapse()
        platform.players.last().end()
        runCurrent()
        assertNotNull(session.upNext, "the next episode is offered there as it is in the player")
        assertEquals(PlayerPresentation.MINI, session.presentation, "and nothing takes the screen to offer it")
        session.handle(SemanticAction.PLAY_PAUSE)
        runCurrent()
        assertTrue(session.subtitle?.startsWith("S1 · E2") == true, "the dock's button took the offer: ${session.subtitle}")
        assertSame(session, app.playback)
        assertEquals(PlayerPresentation.MINI, session.presentation)
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
