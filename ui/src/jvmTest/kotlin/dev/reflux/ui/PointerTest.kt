package dev.reflux.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import dev.reflux.core.playback.PlaybackFailure
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import dev.reflux.ui.state.FolderChoice
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drives the real screens with a mouse: pointer events enter the scene where a window delivers them, so
 * hit-testing, hover, drags, and the wheel are all exercised, not just the callbacks behind them.
 */
@OptIn(ExperimentalTestApi::class)
class PointerTest {
    private fun SemanticsNodeInteraction.mouseClick() = performMouseInput { click() }

    private fun DesktopComposeUiTest.mouseTo(position: Offset) = onRoot().performMouseInput { moveTo(position) }

    private fun DesktopComposeUiTest.exists(description: String) = onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()

    private fun DesktopComposeUiTest.top(text: String) = onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun aRowUnderThePointerOffersToPageSideways() = screen(populated()) {
        awaitText("Recently Added")
        assertFalse(exists("More of Recently Added"), "nothing floats over a row the pointer is not on")
        mouseTo(Offset(720f, top("Recently Added") + 160f))
        await("the pager to appear") { exists("More of Recently Added") }
        assertFalse(exists("Earlier in Recently Added"), "a row at its start only pages forward")

        onNodeWithContentDescription("More of Recently Added").mouseClick()
        await("the row to have moved") { exists("Earlier in Recently Added") }
        onNodeWithContentDescription("Earlier in Recently Added").mouseClick()
        await("the row to be back at its start") { !exists("Earlier in Recently Added") }
    }

    @Test
    fun theWheelScrollsThePageAndTheNavigationBarIsSolid() = screen(populated()) { app ->
        awaitText("Recently Added")
        val before = top("Recently Added")
        onRoot().performMouseInput {
            moveTo(Offset(720f, 600f))
            repeat(4) { scroll(3f) }
        }
        waitForIdle()
        assertTrue(top("Recently Added") < before - 100f, "the wheel moved the page")

        // Posters are now under the floating bar. Clicking the bar itself must not open what is beneath it.
        for (x in 520..1040 step 65) onRoot().performMouseInput { click(Offset(x.toFloat(), 42f)) }
        waitForIdle()
        assertEquals(Destination.Home, app.navigator.current)
    }

    @Test
    fun clickingAPosterOpensItsDetailsAndBackReturns() = screen(populated()) { app ->
        awaitText("Recently Added")
        onAllNodesWithContentDescription("Heat, 1995").onFirst().mouseClick()
        awaitText("Versions")
        assertTrue(app.navigator.current is Destination.Details)

        onNodeWithContentDescription("Add to favorites").mouseClick()
        await("the favorite to be saved") { exists("Remove from favorites") }
        onNodeWithContentDescription("Back").mouseClick()
        awaitText("Recently Added")
        assertEquals(Destination.Home, app.navigator.current)

        onNodeWithText("Movies").mouseClick()
        awaitText("10 titles")
        assertEquals(Destination.Movies, app.navigator.current)
    }

    @Test
    fun aVersionCanBeChosenWithAClick() {
        val platform = FakePlatform()
        screen(populated(), platform) { app ->
            app.navigator.open(Destination.Details(app.library.movies().first { it.item.title == "Heat" }.item.id))
            awaitText("Versions")
            onNode(hasText("1080p", substring = true)).mouseClick()
            await("playback to start") { app.playback != null }
            assertTrue("1080p" in platform.players.single().requests.single().target.uri, "the clicked version plays, not the best one")
        }
    }

    @Test
    fun theSourcesPanelKeepsClicksToItselfAndClosesOnAnOutsideClick() = screen(populated()) { app ->
        awaitText("Recently Added")
        onNodeWithContentDescription("Media sources").mouseClick()
        awaitText("Add folder")

        // The panel's own background is not a way through to the posters behind it.
        val panel = onNodeWithText("Media sources").fetchSemanticsNode().boundsInRoot
        onRoot().performMouseInput { click(Offset(panel.left + 300f, panel.top + 6f)) }
        waitForIdle()
        assertTrue(app.sourcesOpen)
        assertEquals(Destination.Home, app.navigator.current)

        onRoot().performMouseInput { click(Offset(300f, 700f)) }
        waitForIdle()
        assertFalse(app.sourcesOpen)
        assertEquals(Destination.Home, app.navigator.current, "the click that closed the panel did not also open a poster")
    }

    @Test
    fun searchIsUsableWithAPointer() = screen(populated()) { app ->
        onNodeWithContentDescription("Search").mouseClick()
        awaitText("Try")
        onNodeWithText("90s movies").mouseClick()
        awaitText("Heat")
        assertEquals("90s movies", app.searchQuery)
        assertEquals(0, onAllNodesWithText("Dune").fetchSemanticsNodes().size)

        onNodeWithContentDescription("Clear search").mouseClick()
        awaitText("Try")
        assertEquals("", app.searchQuery)
        onNode(hasSetTextAction()).performTextInput("villeneuve movies")
        awaitText("Arrival")
        onAllNodesWithContentDescription("Arrival, 2016").onFirst().mouseClick()
        awaitText("Directed by Denis Villeneuve")

        onNodeWithContentDescription("Back").mouseClick()
        awaitText("Arrival")
        assertEquals(Destination.Search, app.navigator.current)
        assertEquals("villeneuve movies", app.searchQuery, "the search is still there to come back to")
    }

    private class Playing(val app: AppState, val platform: FakePlatform) {
        val player get() = platform.players.last()
        val state get() = player.state.value
    }

    /** Opens the player on the first episode of Breaking Bad, paused inside its intro so the controls stay up. */
    private fun player(block: DesktopComposeUiTest.(Playing) -> Unit) {
        val platform = FakePlatform()
        screen(populated(), platform) { app ->
            awaitText("Recently Added")
            runOnIdle { app.play(app.library.shows().first { it.item.title == "Breaking Bad" }.item.id) }
            awaitText("S1 · E1 · Pilot")
            platform.players.single().apply { playTo(95_000) }.pause()
            waitForIdle()
            block(Playing(app, platform))
        }
    }

    @Test
    fun transportControlsRespondToClicks() = player { playing ->
        onNodeWithContentDescription("Play").mouseClick()
        await("playback to resume") { playing.state.status == PlayerStatus.PLAYING }
        // The video itself is the biggest button: a click anywhere on it pauses.
        onRoot().performMouseInput { click(Offset(720f, 380f)) }
        await("the click on the video to pause") { playing.state.status == PlayerStatus.PAUSED }

        onNodeWithContentDescription("Forward 10 seconds").mouseClick()
        await("the skip forward") { playing.state.positionMs == 105_000L }
        onNodeWithContentDescription("Back 10 seconds").mouseClick()
        await("the skip back") { playing.state.positionMs == 95_000L }

        onNodeWithContentDescription("Mute").mouseClick()
        await("mute") { playing.state.muted }
        onNodeWithContentDescription("Volume").performMouseInput { click(center) }
        await("the volume slider to set the level and unmute") { !playing.state.muted && abs(playing.state.volume - 50) <= 6 }

        onNodeWithContentDescription("Fullscreen").mouseClick()
        await("fullscreen") { playing.platform.fullscreen }
        onNodeWithContentDescription("Exit fullscreen").mouseClick()
        await("windowed again") { !playing.platform.fullscreen }

        onNodeWithText("Skip intro").mouseClick()
        await("the intro to be skipped") { playing.state.positionMs == 150_000L }
        assertEquals(0, onAllNodesWithText("Skip intro").fetchSemanticsNodes().size)

        onNodeWithContentDescription("Next episode").mouseClick()
        awaitText("S1 · E2 · Cat's in the Bag...")
        onNodeWithContentDescription("Back to library").mouseClick()
        await("the player to close and release its engine") { playing.app.playback == null && playing.player.closed }
    }

    @Test
    fun theScrubberSeeksWhereItIsDraggedTo() = player { playing ->
        val timeline = onNodeWithContentDescription("Timeline")
        val width = timeline.fetchSemanticsNode().size.width
        timeline.performMouseInput {
            moveTo(Offset(width * 0.1f, centerY))
            press()
            moveTo(Offset(width * 0.3f, centerY))
        }
        // While dragging, the clock previews the target but the engine is left alone.
        await("the clock to preview the drag") { onAllNodesWithText("1:35").fetchSemanticsNodes().isEmpty() }
        assertEquals(95_000L, playing.state.positionMs)
        timeline.performMouseInput {
            moveTo(Offset(width * 0.5f, centerY))
            release()
        }
        await("the seek on release", { playing.state }) { abs(playing.state.positionMs - 25 * 60_000L) < 20_000L }

        // A plain click on the bar seeks too. The bar is as wide as the clocks beside it leave it, and they just changed.
        timeline.performMouseInput { click(Offset(this.width * 0.8f, centerY)) }
        await("the click to seek", { playing.state }) { abs(playing.state.positionMs - 40 * 60_000L) < 20_000L }
    }

    @Test
    fun chaptersTracksAndSpeedAreChosenFromMenus() = player { playing ->
        onNodeWithContentDescription("Chapters").mouseClick()
        awaitText("Part Two")
        onNodeWithText("Part Two").mouseClick()
        await("the chapter to be played") { playing.state.positionMs == 25 * 60_000L }
        assertNull(playing.app.playback?.menu, "choosing closes the menu")

        onNodeWithContentDescription("Audio").mouseClick()
        awaitText("AUDIO")
        onNode(hasText("French", substring = true)).mouseClick()
        await("the audio track to change") { playing.state.selectedAudioId == "2" }

        onNodeWithContentDescription("Subtitles").mouseClick()
        awaitText("Off")
        onNode(hasText("English", substring = true)).mouseClick()
        await("subtitles to turn on") { playing.state.selectedSubtitleId == "1" }
        onNodeWithContentDescription("Subtitles").mouseClick()
        awaitText("Off")
        onNodeWithText("Off").mouseClick()
        await("subtitles to turn off") { playing.state.selectedSubtitleId == null }

        onNodeWithContentDescription("Playback speed").mouseClick()
        awaitText("1.5×")
        // The menu's siblings are one click away, without going back to the bar.
        onNodeWithText("CHAPTERS").mouseClick()
        awaitText("Part Two")
        onNodeWithText("SPEED").mouseClick()
        onNodeWithText("1.5×").mouseClick()
        await("the speed to change") { playing.state.speed == 1.5 }

        // A click outside an open menu closes it and does not also toggle playback.
        onNodeWithContentDescription("Playback speed").mouseClick()
        awaitText("Normal")
        onRoot().performMouseInput { click(Offset(300f, 300f)) }
        await("the menu to close") { playing.app.playback?.menu == null }
        assertEquals(PlayerStatus.PAUSED, playing.state.status)
    }

    @Test
    fun theWheelIsTheVolumeAndADoubleClickIsFullscreen() = player { playing ->
        onRoot().performMouseInput {
            moveTo(Offset(720f, 380f))
            scroll(1f)
        }
        await("the wheel to turn the volume down") { playing.state.volume == 95 }
        onRoot().performMouseInput { scroll(-1f) }
        await("the wheel to turn the volume up") { playing.state.volume == 100 }

        onRoot().performMouseInput { doubleClick(Offset(720f, 380f)) }
        await("fullscreen") { playing.platform.fullscreen }
        assertEquals(PlayerStatus.PAUSED, playing.state.status, "the two clicks cancel out; only fullscreen changes")
    }

    @Test
    fun playbackThatIsWaitingCanBePaused() = player { playing ->
        playing.player.buffer()
        waitForIdle()
        assertTrue(exists("Pause") && !exists("Play"), "the button says what it will do")
        onRoot().performMouseInput { click(Offset(720f, 380f)) }
        await("the click on the video to pause") { playing.state.status == PlayerStatus.PAUSED }
        assertTrue(exists("Play"))
    }

    @Test
    fun controlsLeaveWhilePlayingAndReturnWithThePointer() = player { playing ->
        playing.player.play()
        waitForIdle()
        // The test's clock, not the machine's speed, decides how long "a few seconds" takes.
        mainClock.advanceTimeBy(5_000)
        waitForIdle()
        assertFalse(exists("Pause") || exists("Play"), "the controls are away after a few seconds of playback")
        mainClock.autoAdvance = false
        onRoot().performMouseInput {
            moveTo(Offset(600f, 300f))
            moveTo(Offset(640f, 320f))
        }
        mainClock.advanceTimeBy(400)
        assertTrue(exists("Pause"), "moving the pointer brings them back")
        mainClock.advanceTimeBy(5_000)
        assertFalse(exists("Pause"))
    }

    @Test
    fun thePostPlayPromptsAnswerToClicks() = player { playing ->
        playing.player.end()
        awaitText("Play now")
        onNodeWithText("Cancel").mouseClick()
        await("the countdown to be cancelled") { playing.app.playback?.upNext == null }
        assertEquals(1, playing.player.requests.size)

        runOnIdle { playing.app.playback?.playNext() }
        awaitText("S1 · E2 · Cat's in the Bag...")
        playing.player.end()
        awaitText("Play now")
        onNodeWithText("Play now").mouseClick()
        awaitText("S1 · E3 · ...And the Bag's in the River")
        assertNotNull(playing.app.playback)
    }

    @Test
    fun aPlaybackFailureIsExplainedAndTheWayBackIsOneClick() = player { playing ->
        playing.player.fail("loading failed", PlaybackFailure.UNREACHABLE)
        awaitText("PLAYBACK STOPPED")
        onNode(hasText("moved or deleted", substring = true)).assertExists()
        onNodeWithText("Details: loading failed").assertExists()
        assertFalse(exists("Timeline"), "controls for something that is not playing are not offered")
        onNodeWithText("Back to library").mouseClick()
        await("the player to close") { playing.app.playback == null }
    }

    @Test
    fun firstRunAnswersToAClick() {
        val world = TestWorld()
        val platform = FakePlatform()
        var asked = 0
        // Before the folder is chosen there is no source at all; choosing it registers one, as a shell does.
        world.library.removeSource(world.source.descriptor.id)
        platform.chooseFolder = {
            asked++
            world.file("Heat (1995)/Heat.1995.1080p.mkv")
            world.library.addSource(world.source, "media")
            FolderChoice.Added(world.source.descriptor.id)
        }
        screen(world, platform) {
            awaitText("Point Reflux at your media")
            onNodeWithText("Choose a folder").mouseClick()
            awaitText("Recently Added")
            assertEquals(1, asked)
            onAllNodesWithText("Heat").fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
        }
    }

    @Test
    fun removingTheOnlySourceLeadsBackToTheFirstRun() {
        val world = populated()
        val platform = FakePlatform()
        platform.remove = { id ->
            world.library.removeSource(id)
            world.registry.unregister(id)
        }
        screen(world, platform) { app ->
            awaitText("Recently Added")
            onNodeWithContentDescription("Media sources").mouseClick()
            onNodeWithContentDescription("Remove Media").mouseClick()
            awaitText("Remove Media?")
            // Asking is not removing.
            onNodeWithText("Keep").mouseClick()
            assertTrue(platform.removed.isEmpty())
            onNodeWithContentDescription("Remove Media").mouseClick()
            onNodeWithText("Remove").mouseClick()
            awaitText("Point Reflux at your media")
            assertEquals(listOf(world.source.descriptor.id), platform.removed.toList())
            assertTrue(app.library.movies().isEmpty())
            assertEquals(Destination.Home, app.navigator.current)
        }
    }
}
