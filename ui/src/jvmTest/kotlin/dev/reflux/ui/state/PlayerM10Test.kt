package dev.reflux.ui.state

import dev.reflux.core.input.Key
import dev.reflux.core.input.SemanticAction
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.playback.SubtitlePreference
import dev.reflux.library.activeProfile
import dev.reflux.library.createProfile
import dev.reflux.library.subtitlePreference
import dev.reflux.library.switchProfile
import dev.reflux.ui.FakePlatform
import dev.reflux.ui.TestWorld
import dev.reflux.ui.player.miniAction
import dev.reflux.ui.player.miniStatus
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
 * M10's session contract (docs/planning/M10-PLAYER-SPEC.md): the system's window as a presentation of the one session,
 * what Back does from the state alone, a failure that stays where the viewer is, whose a playback is, what a viewer's
 * subtitle choice is kept as, and a held seek key being one seek.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerM10Test {
    private class Playing(val world: TestWorld, val platform: FakePlatform, val app: AppState) {
        val session: PlaybackSession get() = app.playback!!
        val player get() = platform.players.last()
        fun movie(title: String) = world.movie(title).item.id
    }

    /** A movie by its title, or a show, whose Next Up episode plays. */
    private fun TestWorld.work(title: String) = library.movies().firstOrNull { it.item.title == title }?.item?.id ?: show(title).item.id

    private fun handheld() = FakePlatform(docksVideo = true, systemPresentation = true)

    private suspend fun TestScope.playing(platform: FakePlatform = handheld(), title: String = "Heat"): Playing {
        val world = TestWorld().apply { populate() }
        val app = appState(world, platform)
        runCurrent()
        app.play(world.work(title))
        runCurrent()
        return Playing(world, platform, app)
    }

    // The system's window ----------------------------------------------------------------------------------------

    @Test
    fun theSystemsWindowIsAThirdWayOfShowingTheSameSession() = runTest {
        val playing = playing()
        val session = playing.session
        val player = playing.player
        player.playTo(30 * MINUTE)
        runCurrent()
        val owner = session.owner

        assertTrue(session.enterSystem())
        assertEquals(PlayerPresentation.SYSTEM, session.presentation)
        assertSame(session, playing.app.playback)
        assertNull(playing.app.watching)
        assertNull(playing.app.docked)
        assertEquals(VideoFrame.Full, playing.app.videoFrame, "the picture is the window")
        assertEquals(1, playing.platform.players.size, "no second engine")
        assertEquals(1, player.requests.size, "nothing loaded or planned again")
        assertFalse(player.closed)
        assertEquals(PlayerStatus.PLAYING, player.state.value.status)
        assertEquals(owner, session.owner)

        assertFalse(session.collapse(), "there is no dock in the system's window")
        session.expand()
        assertEquals(PlayerPresentation.SYSTEM, session.presentation, "expanding is the system's to do, through leaveSystem")

        player.playTo(31 * MINUTE)
        session.leaveSystem(expanded = true)
        assertEquals(PlayerPresentation.FULL, session.presentation)
        assertSame(session, playing.app.watching)
        assertEquals(1, player.requests.size)
        assertEquals(31 * MINUTE, player.state.value.positionMs)
    }

    @Test
    fun theSystemsWindowIsRefusedWhereThereIsNone() = runTest {
        for (platform in listOf(FakePlatform(docksVideo = true), FakePlatform(television = true, docksVideo = true, systemPresentation = true))) {
            val playing = playing(platform)
            assertFalse(playing.session.enterSystem())
            assertEquals(PlayerPresentation.FULL, playing.session.presentation)
        }
    }

    @Test
    fun comingBackFromTheSystemsWindowAlwaysGivesTheFullPlayerWhateverItCameFrom() = runTest {
        val playing = playing()
        playing.session.collapse()
        assertTrue(playing.session.enterSystem())
        playing.session.leaveSystem(expanded = true)
        assertEquals(PlayerPresentation.FULL, playing.session.presentation, "the viewer asked for the picture: they have it, not the dock")
    }

    @Test
    fun closingTheSystemsWindowEndsThePlaybackAndRecordsItsPlace() = runTest {
        val playing = playing()
        val player = playing.player
        player.playTo(44 * MINUTE)
        runCurrent()
        playing.session.enterSystem()
        playing.session.leaveSystem(expanded = false)
        runCurrent()
        assertNull(playing.app.playback)
        assertEquals(listOf(player), playing.platform.released.toList())
        assertEquals(44 * MINUTE, playing.world.library.watchState(playing.movie("Heat"))?.positionMs)
    }

    @Test
    fun theSystemsWindowHasNoReflux() = runTest {
        val playing = playing()
        playing.session.enterSystem()
        assertFalse(playing.app.dispatch(SemanticAction.BACK))
        assertFalse(playing.app.dispatch(SemanticAction.PLAY_PAUSE), "its controls are the system's, through the media session")
        assertEquals(BackRoute.SYSTEM, playing.app.backRoute)
        playing.session.leaveSystem(expanded = true)
        runCurrent()
        // Leaving a session that is not in the window changes nothing.
        playing.session.leaveSystem(expanded = false)
        assertSame(playing.session, playing.app.playback)
    }

    @Test
    fun aFailureInTheSystemsWindowIsWaitingWhenTheViewerComesBack() = runTest {
        val playing = playing()
        playing.session.enterSystem()
        playing.player.fail("boom")
        runCurrent()
        assertNotNull(playing.session.error)
        assertEquals(PlayerPresentation.SYSTEM, playing.session.presentation, "nothing leaves the window by itself")
        playing.session.leaveSystem(expanded = true)
        assertEquals(PlayerPresentation.FULL, playing.session.presentation)
        assertNotNull(playing.session.error, "the player says what happened")
    }

    // Back ---------------------------------------------------------------------------------------------------------

    @Test
    fun backInTheFullPlayerStepsAsideWhereThereIsAMiniPlayer() = runTest {
        val playing = playing()
        assertEquals(BackRoute.COLLAPSE, playing.app.backRoute)
        assertTrue(playing.app.dispatch(SemanticAction.BACK))
        assertEquals(PlayerPresentation.MINI, playing.session.presentation)
        assertEquals(PlayerStatus.PLAYING, playing.player.state.value.status, "and it plays on")
    }

    @Test
    fun anOpenListIsClosedBeforeThePlayerIsLeft() = runTest {
        val playing = playing()
        playing.session.toggleMenu(PlayerMenu.AUDIO)
        assertEquals(BackRoute.APP, playing.app.backRoute)
        assertTrue(playing.app.dispatch(SemanticAction.BACK))
        assertNull(playing.session.menu)
        assertEquals(PlayerPresentation.FULL, playing.session.presentation)
        assertEquals(BackRoute.COLLAPSE, playing.app.backRoute)
    }

    @Test
    fun aFailureIsClosedByBackNotPutAside() = runTest {
        val playing = playing()
        playing.player.fail("boom")
        runCurrent()
        assertEquals(BackRoute.APP, playing.app.backRoute)
        playing.app.dispatch(SemanticAction.BACK)
        runCurrent()
        assertNull(playing.app.playback)
    }

    @Test
    fun whereThereIsNoMiniPlayerBackClosesThePlayer() = runTest {
        for (platform in listOf(FakePlatform(), FakePlatform(television = true, docksVideo = true))) {
            val playing = playing(platform)
            assertEquals(BackRoute.APP, playing.app.backRoute)
            assertTrue(playing.app.dispatch(SemanticAction.BACK))
            runCurrent()
            assertNull(playing.app.playback)
        }
    }

    @Test
    fun backWhileBrowsingWithAMiniPlayerIsNavigationAndNeverEndsIt() = runTest {
        val playing = playing()
        playing.app.navigator.open(Destination.Details(playing.movie("Arrival")))
        playing.session.collapse()
        assertEquals(BackRoute.APP, playing.app.backRoute)
        assertTrue(playing.app.dispatch(SemanticAction.BACK))
        assertEquals(Destination.Home, playing.app.navigator.current)
        assertSame(playing.session, playing.app.playback)
    }

    @Test
    fun atHomeBackTakesAPlayingMiniPlayerIntoTheSystemsWindow() = runTest {
        val playing = playing()
        playing.session.collapse()
        assertEquals(BackRoute.SYSTEM_PRESENTATION, playing.app.backRoute)
        assertTrue(playing.app.dispatch(SemanticAction.BACK))
        assertEquals(1, playing.platform.systemRequests)
        assertSame(playing.session, playing.app.playback, "leaving is not stopping")
        // Buffering is playing too: the viewer is waiting for it.
        playing.player.buffer()
        runCurrent()
        assertEquals(BackRoute.SYSTEM_PRESENTATION, playing.app.backRoute)
    }

    @Test
    fun atHomeBackWithAPausedEndedOrFailedMiniPlayerIsTheSystemsOwn() = runTest {
        val paused = playing()
        paused.session.collapse()
        paused.session.handle(SemanticAction.PLAY_PAUSE)
        runCurrent()
        assertEquals(PlayerStatus.PAUSED, paused.player.state.value.status)
        assertEquals(BackRoute.SYSTEM, paused.app.backRoute)
        assertFalse(paused.app.dispatch(SemanticAction.BACK), "the system's Back runs; nothing was stopped")
        assertEquals(0, paused.platform.systemRequests)
        assertSame(paused.session, paused.app.playback)
        assertEquals(PlayerPresentation.MINI, paused.session.presentation)

        val failed = playing()
        failed.session.collapse()
        failed.player.fail("boom")
        runCurrent()
        assertEquals(BackRoute.SYSTEM, failed.app.backRoute)
        assertFalse(failed.app.dispatch(SemanticAction.BACK))

        val ended = playing(title = "Heat")
        ended.session.collapse()
        ended.player.end()
        runCurrent()
        assertEquals(BackRoute.SYSTEM, ended.app.backRoute)
        assertFalse(ended.app.dispatch(SemanticAction.BACK))
    }

    @Test
    fun aMiniPlayerWithAQuestionForTheViewerIsNotTakenAwayFromThem() = runTest {
        val playing = playing(title = "Breaking Bad")
        playing.session.collapse()
        playing.player.end()
        runCurrent()
        assertNotNull(playing.session.upNext)
        assertEquals(BackRoute.SYSTEM, playing.app.backRoute)
    }

    @Test
    fun theSystemDecidesWhetherItsWindowIsGiven() = runTest {
        val playing = playing()
        playing.platform.systemAccepts = false
        playing.session.collapse()
        assertFalse(playing.app.dispatch(SemanticAction.BACK), "refused: the system's Back runs instead")
        assertEquals(1, playing.platform.systemRequests)
    }

    @Test
    fun whereTheSystemHasNoWindowAtHomeBackIsTheSystemsOwn() = runTest {
        val playing = playing(FakePlatform(docksVideo = true))
        playing.session.collapse()
        assertEquals(BackRoute.SYSTEM, playing.app.backRoute)
        assertFalse(playing.app.dispatch(SemanticAction.BACK))
        assertEquals(0, playing.platform.systemRequests)
        assertEquals(PlayerPresentation.MINI, playing.session.presentation, "and the desktop never leaves: nothing happens")
    }

    @Test
    fun theRouteAgreesWithTheActionInEveryStateTheseTestsReach() = runTest {
        // Whatever the state, Back is handled by Reflux exactly where the route says so.
        data class Case(val name: String, val setup: suspend TestScope.(Playing) -> Unit)
        val cases = listOf(
            Case("full player") { },
            Case("a list open") { it.session.toggleMenu(PlayerMenu.SPEED) },
            Case("failure") { it.player.fail("x"); runCurrent() },
            Case("mini") { it.session.collapse() },
            Case("mini, paused") { it.session.collapse(); it.session.handle(SemanticAction.PLAY_PAUSE); runCurrent() },
            Case("mini, failed") { it.session.collapse(); it.player.fail("x"); runCurrent() },
            Case("mini, page open") { it.session.collapse(); it.app.navigator.open(Destination.Details(it.movie("Arrival"))) },
            Case("mini, sources sheet") { it.session.collapse(); it.app.offlineOpen = true },
            Case("system window") { it.session.enterSystem() },
        )
        for (case in cases) {
            val playing = playing()
            case.setup(this, playing)
            val route = playing.app.backRoute
            val handled = playing.app.dispatch(SemanticAction.BACK)
            assertEquals(route != BackRoute.SYSTEM, handled, "${case.name}: route $route, handled $handled")
        }
        // And with nothing playing.
        val world = TestWorld().apply { populate() }
        val app = appState(world, handheld())
        runCurrent()
        assertEquals(BackRoute.SYSTEM, app.backRoute)
        assertFalse(app.dispatch(SemanticAction.BACK))
        app.navigator.open(Destination.Details(world.movie("Heat").item.id))
        assertEquals(BackRoute.APP, app.backRoute)
        assertTrue(app.dispatch(SemanticAction.BACK))
        assertEquals(BackRoute.SYSTEM, app.backRoute)
    }

    @Test
    fun aGesturesProgressIsOnlyAPreviewAndCancellingItChangesNothing() = runTest {
        val playing = playing()
        playing.app.backPreview = 0.4f
        playing.app.backPreview = 0.7f
        assertEquals(PlayerPresentation.FULL, playing.session.presentation, "the session moves when the gesture completes")
        assertEquals(BackRoute.COLLAPSE, playing.app.backRoute)
        playing.app.backPreview = null
        assertEquals(PlayerPresentation.FULL, playing.session.presentation)
        assertSame(playing.session, playing.app.watching)
        // Completed: the preview ends and the presentation changes.
        playing.app.backPreview = 1f
        playing.app.backPreview = null
        playing.app.dispatch(SemanticAction.BACK)
        assertEquals(PlayerPresentation.MINI, playing.session.presentation)
    }

    // A failure stays where the viewer is -------------------------------------------------------------------------

    @Test
    fun aDeadPictureIsNotLeftShowingInTheDock() = runTest {
        val playing = playing()
        playing.session.collapse()
        playing.app.dockSlot = androidx.compose.ui.geometry.Rect(0f, 0f, 96f, 54f)
        assertTrue(playing.app.videoFrame is VideoFrame.Docked)
        playing.player.fail("boom")
        runCurrent()
        assertEquals(VideoFrame.Hidden, playing.app.videoFrame)
        assertEquals(PlayerPresentation.MINI, playing.session.presentation, "and the viewer's page is not taken from them")
    }

    @Test
    fun aNextEpisodeThatCannotBePlayedIsAFailureInTheDockToo() = runTest {
        val playing = playing(title = "Breaking Bad")
        playing.session.collapse()
        playing.world.registry.unregister(playing.world.source.descriptor.id)
        playing.session.playNext()
        runCurrent()
        assertEquals(PlayerPresentation.MINI, playing.session.presentation)
        assertSame(playing.session, playing.app.docked)
        assertNotNull(playing.session.error)
        assertEquals(VideoFrame.Hidden, playing.app.videoFrame)
        assertEquals("Playback stopped", miniStatus(playing.session, playing.session.state.value))
        assertNull(miniAction(playing.session, playing.session.state.value), "nothing to try again: Close is the way out")
    }

    @Test
    fun aFailureThatCanBeRetriedOffersIt() = runTest {
        val playing = playing()
        playing.session.collapse()
        playing.player.fail("boom", dev.reflux.core.playback.PlaybackFailure.UNREACHABLE)
        runCurrent()
        assertEquals("Try again", miniAction(playing.session, playing.session.state.value)?.second)
    }

    // Whose it is ----------------------------------------------------------------------------------------------------

    @Test
    fun aPlaybackBelongsToTheProfileThatStartedItInEveryPresentation() = runTest {
        val playing = playing()
        val owner = playing.world.library.activeProfile()
        assertEquals(owner, playing.session.owner)
        playing.session.collapse()
        playing.session.enterSystem()
        playing.session.leaveSystem(expanded = true)
        assertEquals(owner, playing.session.owner)
        playing.player.playTo(20 * MINUTE)
        // Whoever is watching by the time it reports, it is recorded for the viewer it was for.
        val sam = playing.world.library.createProfile("Sam")
        playing.world.library.switchProfile(sam)
        playing.session.close()
        runCurrent()
        assertEquals(20 * MINUTE, playing.world.library.watchState(playing.movie("Heat"), owner)?.positionMs)
        assertNull(playing.world.library.watchState(playing.movie("Heat"), sam))
    }

    // What a viewer chose for subtitles --------------------------------------------------------------------------

    @Test
    fun aSubtitleTheViewerPicksIsKeptAsTheirsForTheWorkAndNotAsAPosition() = runTest {
        val playing = playing(title = "Breaking Bad")
        val library = playing.world.library
        val episode = playing.session.entry!!.item.id
        playing.session.selectSubtitle("2") // the forced Spanish track the engine offers
        runCurrent()
        assertEquals(SubtitlePreference(false, "es", forced = true, hearingImpaired = false), library.subtitlePreference(episode))
        // Any other episode of the show finds it.
        val other = playing.session.next!!.item.id
        assertEquals(SubtitlePreference(false, "es", forced = true), library.subtitlePreference(other))

        playing.session.selectSubtitle(null)
        runCurrent()
        assertEquals(SubtitlePreference.OFF, library.subtitlePreference(other))
    }

    @Test
    fun whatTheAutomaticChoiceMadeIsNeverKept() = runTest {
        val playing = playing(title = "Breaking Bad")
        playing.player.playTo(10 * MINUTE)
        runCurrent()
        assertNull(playing.world.library.subtitlePreference(playing.session.entry!!.item.id))
    }

    @Test
    fun theChoiceIsKeptForTheSessionsOwnerWhoeverIsWatchingLater() = runTest {
        val playing = playing(title = "Breaking Bad")
        val library = playing.world.library
        val episode = playing.session.entry!!.item.id
        val owner = playing.session.owner
        val sam = library.createProfile("Sam")
        playing.session.selectSubtitle("1")
        runCurrent()
        assertEquals("en", library.subtitlePreference(episode, owner)?.language)
        assertNull(library.subtitlePreference(episode, sam))
    }

    // A held seek key is one seek ------------------------------------------------------------------------------------

    private fun seekTargets(player: dev.reflux.ui.FakePlayer): List<Long> = player.seeks.toList()

    @Test
    fun aHeldSeekKeyIsOneSeekMadeWhenItIsLetGo() = runTest {
        val playing = playing()
        val app = playing.app
        playing.player.playTo(10 * MINUTE)
        runCurrent()
        val before = playing.player.seeks.size

        // The key goes down: the ten-second seek it always was, at once.
        assertTrue(app.onKey(Key.ARROW_RIGHT))
        assertEquals(before + 1, playing.player.seeks.size)
        assertEquals(10 * MINUTE + 10_000, playing.player.seeks.last())
        // And twenty repeats, as a remote makes them: a target, shown, and no seeks.
        val session = playing.session
        val first = UiClock.nowMs()
        repeat(20) { i -> session.seekKey(SemanticAction.SEEK_FORWARD, repeat = true, nowMs = first + (i + 1) * 100L) }
        assertEquals(before + 1, playing.player.seeks.size, "a held key is not a burst of seeks")
        val target = assertNotNull(session.seekPreview)
        assertTrue(target > 10 * MINUTE + 20_000, "and it has gone further than the first press")
        // Let go: one seek, to where it showed.
        app.onKeyReleased(Key.ARROW_RIGHT)
        assertEquals(before + 2, playing.player.seeks.size)
        assertEquals(target, playing.player.seeks.last())
        assertNull(session.seekPreview)
    }

    @Test
    fun aHeldSeekKeyThatSendsNoReleaseSeeksWhenItGoesQuiet() = runTest {
        val playing = playing()
        playing.player.playTo(10 * MINUTE)
        runCurrent()
        val session = playing.session
        session.seekKey(SemanticAction.SEEK_FORWARD, repeat = false, nowMs = 0)
        session.seekKey(SemanticAction.SEEK_FORWARD, repeat = true, nowMs = 1_000)
        val target = assertNotNull(session.seekPreview)
        val seeks = playing.player.seeks.size
        advanceTimeBy(300)
        runCurrent()
        assertEquals(seeks, playing.player.seeks.size, "still within the quiet")
        advanceTimeBy(200)
        runCurrent()
        assertEquals(seeks + 1, playing.player.seeks.size)
        assertEquals(target, playing.player.seeks.last())
    }

    @Test
    fun aHeldSeekNeverGoesPastTheEnd() = runTest {
        val playing = playing()
        playing.player.playTo(49 * MINUTE + 30_000)
        runCurrent()
        val session = playing.session
        session.seekKey(SemanticAction.SEEK_FORWARD, repeat = false, nowMs = 0)
        val seeks = playing.player.seeks.size
        for (i in 1..60) session.seekKey(SemanticAction.SEEK_FORWARD, repeat = true, nowMs = i * 100L)
        assertEquals(seeks, playing.player.seeks.size)
        session.seekKeyReleased()
        assertEquals(playing.player.durationMs, playing.player.seeks.last(), "never past the end")
    }

    @Test
    fun quickSeparatePressesAreSeparateSeeksNotAHold() = runTest {
        // A double-tap gesture, or an arrow tapped quickly: each one is the ten-second seek it was before M10.
        val playing = playing()
        playing.player.playTo(20 * MINUTE)
        runCurrent()
        val before = playing.player.seeks.size
        val session = playing.session
        for (i in 0 until 5) session.seekKey(SemanticAction.SEEK_FORWARD, repeat = false, nowMs = i * 150L)
        assertEquals(before + 5, playing.player.seeks.size)
        assertNull(session.seekPreview)
    }

    @Test
    fun theOtherDirectionAndTheNextPressAreFreshHolds() = runTest {
        val playing = playing()
        playing.player.playTo(10 * MINUTE)
        runCurrent()
        val session = playing.session
        val before = playing.player.seeks.size
        session.seekKey(SemanticAction.SEEK_FORWARD, repeat = false, nowMs = 0)
        session.seekKey(SemanticAction.SEEK_FORWARD, repeat = true, nowMs = 1_000)
        val held = assertNotNull(session.seekPreview)
        // Left goes down while right was held: right's one seek is made first, and left is a press of its own.
        session.seekKey(SemanticAction.SEEK_BACKWARD, repeat = false, nowMs = 1_100)
        assertNull(session.seekPreview)
        assertEquals(3, playing.player.seeks.size - before)
        assertEquals(held, playing.player.seeks[before + 1])
        assertEquals(held - 10_000, playing.player.seeks.last(), "left goes ten seconds from where the viewer now is")
    }

    @Test
    fun withAListOpenTheArrowsAreTheListsAndRepeatsAreLeftAlone() = runTest {
        val playing = playing()
        playing.session.toggleMenu(PlayerMenu.AUDIO)
        val seeks = playing.player.seeks.size
        assertTrue(playing.app.onKey(Key.ARROW_RIGHT, repeat = true))
        assertEquals(seeks, playing.player.seeks.size)
        assertNull(playing.session.seekPreview)
    }

    @Test
    fun theMediaKeysKeepTheirMeaningInEveryPresentation() = runTest {
        val playing = playing()
        playing.session.collapse()
        assertTrue(playing.app.onKey(Key.MEDIA_PLAY_PAUSE))
        assertEquals(PlayerStatus.PAUSED, playing.player.state.value.status)
        assertTrue(playing.app.onKey(Key.MEDIA_PLAY_PAUSE))
        assertEquals(PlayerStatus.PLAYING, playing.player.state.value.status)
    }

    // The television's subtitle list ---------------------------------------------------------------------------------

    private val audioOnly = listOf(dev.reflux.core.playback.PlayerTrack("1", dev.reflux.core.playback.TrackType.AUDIO, language = "en", default = true, position = 0))

    @Test
    fun aTelevisionThatCannotReadSidecarsSaysSoInsteadOfOfferingNothing() = runTest {
        val platform = FakePlatform(television = true, docksVideo = true, readsSidecarSubtitles = false).also { it.engineTracks = audioOnly }
        val playing = playing(platform)
        val session = playing.session
        assertTrue(PlayerMenu.SUBTITLES in session.menus(), "the list is there, to say why it is empty")
        val options = session.options(PlayerMenu.SUBTITLES)
        assertEquals(listOf(Problems.SIDECARS_UNREADABLE), options.map { it.label })
        assertEquals("Subtitle files beside videos can't be read on this TV", options.single().label)
        // Nothing can be chosen: the one line is read, and choosing it changes nothing.
        session.toggleMenu(PlayerMenu.SUBTITLES)
        session.choose(PlayerMenu.SUBTITLES, 0)
        runCurrent()
        assertNull(playing.player.state.value.selectedSubtitleId)
        assertNull(session.menu, "the list closes as any choice closes it")
        assertNull(playing.world.library.subtitlePreference(session.entry!!.item.id), "and a choice that was not made is not remembered")
    }

    @Test
    fun aTelevisionWithASubtitleInTheFileOffersItAsEverywhere() = runTest {
        val platform = FakePlatform(television = true, docksVideo = true, readsSidecarSubtitles = false)
        val playing = playing(platform)
        val labels = playing.session.options(PlayerMenu.SUBTITLES).map { it.label }
        assertTrue(Problems.SIDECARS_UNREADABLE !in labels, "there is something to choose, and no explanation")
        assertEquals("Off", labels.first())
        assertTrue(labels.size > 1)
    }

    @Test
    fun whereSidecarsAreReadAFileWithNoSubtitleHasNoSubtitleList() = runTest {
        val platform = FakePlatform(docksVideo = true).also { it.engineTracks = audioOnly }
        val playing = playing(platform)
        assertFalse(PlayerMenu.SUBTITLES in playing.session.menus(), "nothing to say: a file beside the video would have been found")
    }
}

private const val MINUTE = 60_000L
