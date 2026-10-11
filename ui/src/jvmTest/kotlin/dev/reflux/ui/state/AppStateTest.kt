package dev.reflux.ui.state

import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.core.input.InputModality
import dev.reflux.core.input.InputSignal
import dev.reflux.core.input.Key
import dev.reflux.core.input.SemanticAction
import dev.reflux.core.model.Availability
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.source.MediaSource
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.core.versions.SelectionCriterion
import dev.reflux.ui.FakePlatform
import dev.reflux.ui.TestWorld
import dev.reflux.ui.testDevice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
internal fun TestScope.appState(world: TestWorld, platform: FakePlatform = FakePlatform()): AppState =
    AppState(world.library, world.pipeline, world.registry::resolve, platform, backgroundScope, StandardTestDispatcher(testScheduler))

@OptIn(ExperimentalCoroutinesApi::class)
class AppStateTest {
    @Test
    fun navigationUnwindsToHome() {
        val navigator = Navigator()
        assertFalse(navigator.back())
        navigator.show(Destination.Movies)
        navigator.open(Destination.Listing("Ready to Watch", "movies I haven't watched"))
        navigator.open(Destination.Listing("Ready to Watch", "movies I haven't watched"))
        assertEquals(Destination.Movies, navigator.section)
        assertTrue(navigator.back())
        assertEquals(Destination.Movies, navigator.current)
        navigator.show(Destination.Shows)
        assertTrue(navigator.back())
        assertEquals(Destination.Home, navigator.current)
        assertFalse(navigator.canGoBack)
    }

    @Test
    fun keysBecomeSemanticActionsWhileBrowsing() = runTest {
        val platform = FakePlatform()
        val app = appState(TestWorld(), platform)
        app.navigator.show(Destination.Movies)
        assertTrue(app.onKey(Key.ESCAPE))
        assertEquals(Destination.Home, app.navigator.current)
        assertFalse(app.onKey(Key.ESCAPE), "nothing to go back to at the root")

        assertTrue(app.onKey(Key.F))
        assertTrue(platform.fullscreen)
        assertFalse(app.onKey(Key.ENTER), "activating the focused control is the toolkit's job")

        app.sourcesOpen = true
        assertTrue(app.onKey(Key.BACKSPACE))
        assertFalse(app.sourcesOpen)
    }

    @Test
    fun typingIsNeverTakenForShortcuts() = runTest {
        val platform = FakePlatform()
        val app = appState(TestWorld(), platform)
        var cleared = 0
        app.clearFocus = { cleared++ }
        app.navigator.show(Destination.Search)
        app.textInputActive = true

        assertFalse(app.onKey(Key.F))
        assertFalse(app.onKey(Key.BACKSPACE))
        assertFalse(app.onKey(Key.SPACE))
        assertFalse(platform.fullscreen)
        assertEquals(Destination.Search, app.navigator.current)

        assertTrue(app.onKey(Key.ESCAPE))
        assertEquals(1, cleared)
    }

    @Test
    fun inputModalityFollowsWhatTheUserIsDoing() = runTest {
        val app = appState(TestWorld())
        val moves = mutableListOf<SemanticAction>()
        app.moveFocus = { moves += it; true }
        assertEquals(InputModality.POINTER, app.input.modality)

        assertTrue(app.onKey(Key.ARROW_DOWN))
        assertEquals(InputModality.KEYBOARD, app.input.modality)
        assertEquals(listOf(SemanticAction.NAVIGATE_DOWN), moves)

        app.onSignal(InputSignal.GamepadButtonPress(GamepadButton.FACE_SOUTH, ControllerFamily.PLAYSTATION))
        assertEquals(InputModality.DIRECTIONAL, app.input.modality)
        assertEquals(ControllerFamily.PLAYSTATION, app.input.controllerFamily)

        app.onSignal(InputSignal.PointerMove(3f, 2f))
        assertEquals(InputModality.DIRECTIONAL, app.input.modality, "a nudged mouse does not leave controller mode")
        app.onSignal(InputSignal.PointerMove(30f, 0f))
        assertEquals(InputModality.POINTER, app.input.modality)
        assertEquals(ControllerFamily.PLAYSTATION, app.input.controllerFamily, "remembered for button prompts")
    }

    @Test
    fun playingAMovieUsesBestVersionAndResumes() = runTest {
        val world = TestWorld().apply { populate() }
        val platform = FakePlatform()
        val app = appState(world, platform)
        val heat = world.movie("Heat").item.id
        world.library.stopped(heat, 30 * 60_000L, 170 * 60_000L)

        app.play(heat)
        runCurrent()
        val session = assertNotNull(app.playback)
        val player = platform.players.single()
        val request = player.requests.single()
        assertTrue("2160p" in request.target.uri, "the 4K HDR copy is best on a 4K HDR display")
        assertEquals(30 * 60_000L, request.startPositionMs)
        assertEquals("Heat", session.title)
        assertEquals("1995", session.subtitle)

        player.playTo(40 * 60_000L)
        runCurrent()
        session.close()
        runCurrent()
        assertNull(app.playback)
        assertEquals(listOf(player), platform.released.toList())
        assertEquals(40 * 60_000L, world.library.watchState(heat)?.positionMs)
    }

    @Test
    fun playFromTheStartIgnoresTheResumePosition() = runTest {
        val world = TestWorld().apply { populate() }
        val platform = FakePlatform()
        val app = appState(world, platform)
        val heat = world.movie("Heat").item.id
        world.library.stopped(heat, 30 * 60_000L, 170 * 60_000L)
        val standard = world.library.versions(heat).single { it.version.stream.video?.height == 1080 }.version.id

        app.play(heat, versionId = standard, resume = false)
        runCurrent()
        val request = platform.players.single().requests.single()
        assertNull(request.startPositionMs)
        assertTrue("1080p" in request.target.uri)
    }

    @Test
    fun playingAShowContinuesWithItsNextEpisode() = runTest {
        val world = TestWorld().apply { populate() }
        val platform = FakePlatform()
        val app = appState(world, platform)
        val show = world.show("Breaking Bad").item.id

        app.play(show)
        runCurrent()
        assertEquals("S1 · E1 · Pilot", app.playback?.subtitle)
        app.playback?.close()
        runCurrent()

        world.library.setWatched(world.episodes("Breaking Bad")[0].item.id, true)
        app.play(show)
        runCurrent()
        assertEquals("Breaking Bad", app.playback?.title)
        assertEquals("S1 · E2 · Cat's in the Bag...", app.playback?.subtitle)
    }

    @Test
    fun anOfflineSourceIsExplainedInsteadOfFailing() = runTest {
        val world = TestWorld().apply { populate() }
        val platform = FakePlatform()
        val app = appState(world, platform)
        val heat = world.movie("Heat").item.id
        world.registry.unregister(world.source.descriptor.id) // the library remembers it; nothing can open it

        app.play(heat)
        runCurrent()
        assertNull(app.playback)
        assertTrue(platform.players.isEmpty(), "no video surface is shown for something that cannot play")
        assertTrue(assertNotNull(app.notice).contains("offline"))
        advanceTimeBy(5_000)
        runCurrent()
        assertNull(app.notice, "notices clear themselves")
    }

    @Test
    fun browsingNeverDependsOnAPlaybackEngine() = runTest {
        val world = TestWorld().apply { populate() }
        val platform = FakePlatform().apply { canPlay = false }
        val app = appState(world, platform)

        app.play(world.movie("Heat").item.id)
        runCurrent()
        assertNull(app.playback)
        assertTrue(assertNotNull(app.notice).contains("playback engine"))
        assertFalse(world.library.homeModel().empty)
    }

    @Test
    fun favoritesAndWatchedStateAreUserState() = runTest {
        val world = TestWorld().apply { populate() }
        val app = appState(world)
        val arrival = world.movie("Arrival")
        val before = app.revision

        app.toggleFavorite(arrival)
        app.setWatched(world.show("Severance").item.id, true)
        runCurrent()
        assertTrue(world.movie("Arrival").favorite)
        assertTrue(world.episodes("Severance").all { it.watchState?.completed == true })
        assertTrue(app.revision > before, "screens reload")
    }

    @Test
    fun addingAFolderScansItWithoutFurtherInput() = runTest {
        val world = TestWorld()
        val platform = FakePlatform()
        val app = appState(world, platform)
        assertTrue(world.library.homeModel().empty)
        platform.chooseFolder = {
            world.file("Heat (1995)/Heat.1995.1080p.mkv")
            FolderChoice.Added(world.source.descriptor.id)
        }

        app.addFolder().join() // scanning reads the folder on real I/O threads
        val home = world.library.homeModel()
        assertEquals("Heat", home.featured?.title)
        assertEquals(listOf("recent", "movies"), home.rows.map { it.id })
    }

    @Test
    fun cardsAndDetailsDescribeWorksTheWayUsersNameThem() = runTest {
        val world = TestWorld().apply { populate() }
        val library = world.library
        val episodes = world.episodes("Breaking Bad")
        library.setWatched(episodes[0].item.id, true)
        world.clock += 1_000
        library.progress(episodes[1].item.id, 12 * 60_000L, 47 * 60_000L)

        val card = library.cards(library.continueWatching()).single()
        assertEquals("Breaking Bad", card.title)
        assertEquals("S1 · E2 · Cat's in the Bag...", card.caption)
        assertEquals(world.show("Breaking Bad").item.id, card.detailsId)
        assertNotNull(card.poster, "an episode card borrows its show's poster")

        val show = assertNotNull(library.detailsModel(card.detailsId, testDevice))
        assertEquals("Continue S1 · E2", show.playLabel)
        assertEquals(episodes[1].item.id, show.playId)
        assertEquals(listOf(5, 3), show.seasons.map { it.episodes.size })

        val heat = assertNotNull(library.detailsModel(world.movie("Heat").item.id, testDevice))
        assertEquals("Play", heat.playLabel)
        assertEquals(listOf(true, false), heat.versions.map { it.best })
        assertEquals(SelectionCriterion.RESOLUTION, heat.bestReason)
        assertEquals(listOf("4K", "HDR10"), heat.badges)
        assertTrue(heat.versions.all { it.sourceName == "Media" && it.ranked.candidate.availability == Availability.AVAILABLE })
    }

    @Test
    fun aKeyDuringPlaybackGoesToThePlayer() = runTest {
        val world = TestWorld().apply { populate() }
        val platform = FakePlatform()
        val app = appState(world, platform)
        app.play(world.movie("Arrival").item.id)
        runCurrent()
        val player = platform.players.single()

        assertTrue(app.onKey(Key.SPACE))
        assertEquals(PlayerStatus.PAUSED, player.state.value.status)
        assertTrue(app.onKey(Key.ARROW_RIGHT))
        assertEquals(10_000L, player.state.value.positionMs)
        assertTrue(app.onKey(Key.ESCAPE))
        runCurrent()
        assertNull(app.playback)
        assertEquals(Destination.Home, app.navigator.current, "Back closed the player, not the page beneath it")
    }

    @Test
    fun aCancelledFolderChoiceChangesNothing() = runTest {
        val world = TestWorld()
        val platform = FakePlatform()
        val app = appState(world, platform)
        val before = app.revision

        app.addFolder().join()
        assertNull(app.notice, "closing the picker is not an error")
        assertEquals(before, app.revision)
        assertTrue(platform.reports.isEmpty())
    }

    @Test
    fun aFolderThatCannotBeUsedIsExplained() = runTest {
        val world = TestWorld()
        val platform = FakePlatform()
        val app = appState(world, platform)

        platform.chooseFolder = { FolderChoice.Unreadable("Backup Drive") }
        app.addFolder().join()
        assertTrue(assertNotNull(app.notice).contains("Backup Drive"))

        platform.chooseFolder = { FolderChoice.NotAFolder("This PC") }
        app.addFolder().join()
        assertTrue(assertNotNull(app.notice).contains("This PC"))

        platform.chooseFolder = { FolderChoice.AlreadyInLibrary(world.source.descriptor.id, "Media") }
        world.file("Heat (1995)/Heat.1995.1080p.mkv")
        val rescan = app.addFolder()
        runCurrent()
        assertTrue(assertNotNull(app.notice).contains("already in your library"))
        rescan.join() // scanning reads the folder on real I/O threads
        assertEquals("Heat", world.library.movies().single().item.title, "choosing a known folder again rescans it")
    }

    @Test
    fun aFailingFolderPickerIsReportedNotFatal() = runTest {
        val platform = FakePlatform()
        val app = appState(TestWorld(), platform)
        platform.chooseFolder = { error("the dialog crashed") }

        app.addFolder().join()
        assertNotNull(app.notice)
        assertEquals(1, platform.reports.size, "the cause is kept for diagnostics")

        // The failure does not leave the picker locked.
        var asked = 0
        platform.chooseFolder = {
            asked++
            FolderChoice.Cancelled
        }
        app.addFolder().join()
        assertEquals(1, asked)
    }

    @Test
    fun theFolderPickerOpensOnceHoweverOftenItIsAskedFor() = runTest {
        val platform = FakePlatform()
        val app = appState(TestWorld(), platform)
        val open = CompletableDeferred<FolderChoice>()
        var asked = 0
        platform.chooseFolder = {
            asked++
            open.await()
        }

        val first = app.addFolder()
        runCurrent()
        app.addFolder().join()
        assertEquals(1, asked, "a second request while the picker is open is ignored")
        open.complete(FolderChoice.Cancelled)
        first.join()
    }

    @Test
    fun anEngineThatWillNotStartIsExplainedAndReported() = runTest {
        val world = TestWorld().apply { populate() }
        val platform = FakePlatform().apply { failToStart = true }
        val app = appState(world, platform)

        app.play(world.movie("Heat").item.id)
        runCurrent()
        assertNull(app.playback)
        assertTrue(assertNotNull(app.notice).contains("couldn't start"))
        assertEquals(1, platform.reports.size)

        // Nothing is left half-started: the next attempt works.
        platform.failToStart = false
        app.play(world.movie("Heat").item.id)
        runCurrent()
        assertNotNull(app.playback)
    }

    @Test
    fun aSourceThatFailsWhileResolvingAFileDoesNotEndTheApp() = runTest {
        val world = TestWorld().apply { populate() }
        val platform = FakePlatform()
        val app = appState(world, platform)
        world.registry.register(object : MediaSource by world.source {
            override suspend fun playbackTarget(path: String): PlaybackTarget = error("the share dropped the connection")
        })

        app.play(world.movie("Heat").item.id)
        runCurrent()
        assertNull(app.playback)
        assertNotNull(app.notice)
        assertTrue(platform.players.isEmpty())
        assertEquals(1, platform.reports.size)
    }

    @Test
    fun anOfflineSourceIsNamed() = runTest {
        val world = TestWorld().apply { populate() }
        val app = appState(world)
        world.registry.unregister(world.source.descriptor.id)

        app.play(world.movie("Heat").item.id)
        runCurrent()
        assertEquals("This can't be played right now. Media is offline.", app.notice)
    }

    @Test
    fun longerNoticesStayLongEnoughToRead() = runTest {
        val app = appState(TestWorld())
        app.notify("Short.")
        advanceTimeBy(4_600)
        runCurrent()
        assertNull(app.notice)

        app.notify("Reflux can't open the folder you chose. Check that it is connected and that you are allowed to read it, then try again.")
        advanceTimeBy(4_600)
        runCurrent()
        assertNotNull(app.notice)
        advanceTimeBy(10_000)
        runCurrent()
        assertNull(app.notice)
    }

    @Test
    fun controllerButtonsBecomeSemanticActions() = runTest {
        val platform = FakePlatform()
        val app = appState(TestWorld(), platform)
        val moves = mutableListOf<SemanticAction>()
        var activations = 0
        app.moveFocus = { moves += it; true }
        app.activateFocused = { activations++; true }
        val pad = ControllerFamily.PLAYSTATION

        assertTrue(app.onGamepadButton(GamepadButton.DPAD_LEFT, pad))
        assertEquals(listOf(SemanticAction.NAVIGATE_LEFT), moves)
        assertEquals(InputModality.DIRECTIONAL, app.input.modality)
        assertTrue(app.onGamepadButton(GamepadButton.FACE_SOUTH, pad), "a controller's confirm has no key, so Reflux activates the focused control")
        assertEquals(1, activations)

        app.navigator.show(Destination.Movies)
        assertTrue(app.onGamepadButton(GamepadButton.FACE_EAST, pad))
        assertEquals(Destination.Home, app.navigator.current)
        assertTrue(app.onGamepadButton(GamepadButton.START, pad))
        assertTrue(app.sourcesOpen)
        assertTrue(app.onGamepadButton(GamepadButton.FACE_EAST, pad))
        assertFalse(app.sourcesOpen)
        assertTrue(app.onGamepadButton(GamepadButton.SELECT, pad))
        assertTrue(platform.fullscreen)
        assertFalse(app.onGamepadButton(GamepadButton.STICK_LEFT, pad), "buttons without a meaning are ignored")

        // Nintendo's confirm and cancel are the other way round; positions, not labels, are what is mapped.
        assertTrue(app.onGamepadButton(GamepadButton.FACE_EAST, ControllerFamily.NINTENDO))
        assertEquals(2, activations)
    }

    @Test
    fun backLeavesATextFieldBeforeItLeavesThePage() = runTest {
        val app = appState(TestWorld())
        var cleared = 0
        app.clearFocus = { cleared++ }
        app.navigator.show(Destination.Search)
        app.textInputActive = true

        assertTrue(app.onGamepadButton(GamepadButton.FACE_EAST, ControllerFamily.XBOX))
        assertEquals(1, cleared)
        assertEquals(Destination.Search, app.navigator.current)
    }
}
