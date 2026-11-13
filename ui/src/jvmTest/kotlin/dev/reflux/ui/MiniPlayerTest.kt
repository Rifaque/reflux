package dev.reflux.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import dev.reflux.core.input.InputSignal
import dev.reflux.core.input.Key
import dev.reflux.core.input.SemanticAction
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.design.AdaptiveLayout
import dev.reflux.design.DockStyle
import dev.reflux.design.NavigationStyle
import dev.reflux.library.createProfile
import dev.reflux.ui.state.AccessibilityPreferences
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import dev.reflux.ui.state.PlayerPresentation
import dev.reflux.ui.state.VideoFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The mini-player on screen (docs/NAVIGATION.md, section 8): what is playing, in the navigation's dock, while the
 * page stays the viewer's. One session's other presentation, in every window that has a dock, with a meaning to
 * assistive technology and to a keyboard, and nowhere a device has no dock.
 */
@OptIn(ExperimentalTestApi::class)
class MiniPlayerTest {
    private fun docking() = FakePlatform(docksVideo = true)

    private val dock = SemanticsMatcher("the mini-player") { it.config.getOrNull(SemanticsProperties.PaneTitle) == "Mini player" }
    private fun inDock(description: String) = hasContentDescription(description) and hasAnyAncestor(dock)

    private fun DesktopComposeUiTest.exists(matcher: SemanticsMatcher) = onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    private fun DesktopComposeUiTest.exists(description: String) = onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
    private fun DesktopComposeUiTest.node(matcher: SemanticsMatcher): SemanticsNode = onNode(matcher).fetchSemanticsNode()

    /** Where the stand-in for the shell's video surface is, whether or not it is inside the window. */
    private fun DesktopComposeUiTest.video(): Rect = onNode(hasTestTag(VIDEO_SURFACE)).fetchSemanticsNode().let { Rect(it.positionInRoot, androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat())) }

    private fun DesktopComposeUiTest.playing(app: AppState, platform: FakePlatform, title: String = "Heat") {
        runOnIdle { app.play(app.library.movies().first { it.item.title == title }.item.id) }
        await("the player") { platform.players.isNotEmpty() && app.playback != null }
        await("the timeline") { exists("Timeline") }
    }

    private fun DesktopComposeUiTest.docked(app: AppState, platform: FakePlatform, title: String = "Heat") {
        playing(app, platform, title)
        runOnIdle { assertTrue(app.playback!!.collapse()) }
        await("the mini-player") { exists(dock) && app.videoFrame is VideoFrame.Docked }
        waitForIdle()
    }

    private fun near(a: Rect, b: Rect, tolerance: Float = 1.5f) =
        kotlin.math.abs(a.left - b.left) <= tolerance && kotlin.math.abs(a.top - b.top) <= tolerance && kotlin.math.abs(a.right - b.right) <= tolerance && kotlin.math.abs(a.bottom - b.bottom) <= tolerance

    @Test
    fun thePlayerStepsAsideIntoTheDockAndComesBack() {
        val platform = docking()
        screen(populated(), platform) { app ->
            awaitText("Recently Added")
            playing(app, platform)
            val session = app.playback!!
            assertTrue(near(video(), Rect(0f, 0f, 1440f, 900f)), "the picture is the whole window: ${video()}")
            assertFalse(saysAnywhere("Recently Added"), "and the page behind the player is not there")
            save("mini-player-full")

            onNodeWithContentDescription("Minimize player").performClick()
            await("the mini-player") { exists(dock) && app.videoFrame is VideoFrame.Docked }
            waitForIdle()
            assertSame(session, app.playback)
            assertEquals(PlayerPresentation.MINI, session.presentation)
            assertEquals(1, platform.players.size)
            // The picture is where the dock's picture box is, in the page's bottom corner, and the page is back.
            val picture = node(inDock("Heat, 1995")).boundsInRoot
            assertTrue(near(video(), picture), "the picture ${video()} is in the dock's box $picture")
            assertTrue(picture.left > 1440 / 2 && picture.top > 900 / 2 && picture.right < 1440 && picture.bottom < 900, "in the bottom corner: $picture")
            assertTrue(saysAnywhere("Recently Added"), "the page is the viewer's again")
            assertFalse(exists("Timeline"), "the full player's controls are gone with it")
            save("mini-player-desktop")

            // The picture is the way back.
            onNode(inDock("Heat, 1995")).performSemanticsAction(SemanticsActions.OnClick)
            await("the player back") { exists("Timeline") }
            assertSame(session, app.playback)
            assertEquals(PlayerPresentation.FULL, session.presentation)
            assertTrue(near(video(), Rect(0f, 0f, 1440f, 900f)))
            assertFalse(exists(dock))
            assertEquals(1, platform.players.size, "one engine throughout")
        }
    }

    @Test
    fun theDockOperatesTheSessionItShows() {
        val platform = docking()
        screen(populated(), platform) { app ->
            awaitText("Recently Added")
            docked(app, platform)
            val player = platform.players.single()
            onNode(inDock("Pause")).performClick()
            await("paused") { player.state.value.status == PlayerStatus.PAUSED }
            await("the button to say Play") { exists(inDock("Play")) }
            // How it stands is part of what the one stop says.
            assertEquals("Paused", node(inDock("Heat, 1995")).config.getOrNull(SemanticsProperties.StateDescription))
            onNode(inDock("Play")).performClick()
            await("playing") { player.state.value.status == PlayerStatus.PLAYING }
            onNode(inDock("Stop playing")).performClick()
            await("the player to be gone") { app.playback == null && !exists(dock) }
            assertTrue(player.closed)
        }
    }

    @Test
    fun itMeansSomethingToAssistiveTechnology() {
        val platform = docking()
        screen(populated(), platform) { app ->
            awaitText("Recently Added")
            docked(app, platform)
            // One named part of the screen, with three stops: what is playing (the way back), play or pause, stop.
            val picture = node(inDock("Heat, 1995"))
            assertEquals(Role.Button, picture.config.getOrNull(SemanticsProperties.Role))
            assertEquals("return to the player", picture.config.getOrNull(SemanticsActions.OnClick)?.label)
            for (name in listOf("Pause", "Stop playing")) {
                assertEquals(Role.Button, node(inDock(name)).config.getOrNull(SemanticsProperties.Role), name)
            }
            val stops = onAllNodes(hasAnyAncestor(dock)).fetchSemanticsNodes().filter { it.config.contains(SemanticsActions.OnClick) }
            assertEquals(3, stops.size, "three stops: ${stops.map { it.config.getOrNull(SemanticsProperties.ContentDescription) }}")
            // Every one of them takes a keyboard's and a controller's focus.
            assertTrue(stops.all { it.config.contains(SemanticsProperties.Focused) }, "each can be focused")
            // The page beside it is not inert, and the navigation is where it was.
            assertTrue(exists(destination("Movies")))

            // What needs the viewer is said as it happens, from a node that was already there.
            runOnIdle { platform.players.single().fail("boom") }
            val said = SemanticsMatcher("says Playback stopped") {
                it.config.getOrNull(SemanticsProperties.LiveRegion) != null && it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "Playback stopped" } == true
            }
            await("the failure to be said") { exists(said and hasAnyAncestor(dock)) }
            assertEquals("Playback stopped", node(inDock("Heat, 1995")).config.getOrNull(SemanticsProperties.StateDescription))
        }
    }

    @Test
    fun aDeviceWithoutADockHasNoMiniPlayer() {
        val plain = FakePlatform()
        screen(populated(), plain) { app ->
            awaitText("Recently Added")
            playing(app, plain)
            assertFalse(exists("Minimize player"), "nothing offers what the shell cannot do")
        }
        // A television's player is the whole screen, whatever its shell can do with a surface.
        val tv = FakePlatform(television = true, docksVideo = true)
        television(populated(), tv) { app ->
            awaitText("Recently Added")
            runOnIdle { app.play(app.library.movies().first { it.item.title == "Heat" }.item.id) }
            await("the player") { app.playback != null && exists("Timeline") }
            assertFalse(exists("Minimize player"))
            runOnIdle { app.onKey(Key.I) }
            waitForIdle()
            assertEquals(PlayerPresentation.FULL, app.playback?.presentation)
            assertFalse(exists(dock))
        }
    }

    @Test
    fun everyWindowWithADockShowsItClearOfTheNavigation() {
        // By window class, not by device: a phone upright and on its side, a tablet, a small and a large desktop window.
        for ((width, height) in listOf(412 to 915, 915 to 412, 640 to 800, 800 to 1280, 1100 to 800, 1280 to 720, 1920 to 1080)) {
            val platform = docking()
            screen(populated(), platform, width = width, height = height) { app ->
                await("the library") { exists(destination("Home")) }
                runOnIdle { app.onSignal(InputSignal.Touch) }
                docked(app, platform)
                val layout = AdaptiveLayout.profile(width.toFloat(), height.toFloat(), false, app.input.modality)
                val spec = assertNotNull(layout.dock)
                val whole = Rect(0f, 0f, width.toFloat(), height.toFloat())
                val box = node(dock).boundsInRoot
                val picture = node(inDock("Heat, 1995")).boundsInRoot
                val what = "${width}x$height (${layout.navigation})"
                assertTrue(box.left >= 0 && box.top >= 0 && box.right <= whole.right && box.bottom <= whole.bottom, "the dock is inside the window at $what: $box")
                assertTrue(near(video(), picture), "the picture is in its box at $what: ${video()} against $picture")
                assertEquals(16f / 9f, picture.width / picture.height, 0.02f, "the box is the shape of a picture at $what")
                // Never over the destinations, wherever they are.
                val destinations = listOf("Home", "Movies", "Shows", "Search").map { node(destination(it)).boundsInRoot }
                assertTrue(destinations.none { it.overlaps(box) }, "the dock is clear of the destinations at $what: $box against $destinations")
                if (layout.navigation == NavigationStyle.BOTTOM_BAR) {
                    assertEquals(DockStyle.BAR, spec.style)
                    assertTrue(box.bottom <= destinations.minOf { it.top }, "above the phone's bar: $box")
                } else {
                    assertTrue(box.left > destinations.maxOf { it.right }, "beside the navigation, in the page: $box")
                    assertTrue(whole.right - box.right <= 32f && whole.bottom - box.bottom <= 32f, "in the bottom corner at $what: $box")
                }
                // A finger gets 48 dp.
                for (name in listOf("Pause", "Stop playing")) {
                    val button = node(inDock(name)).boundsInRoot
                    assertTrue(button.width >= 48f && button.height >= 48f, "$name is ${button.width} x ${button.height} at $what")
                    assertTrue(button.right <= box.right && button.bottom <= box.bottom, "$name is inside the dock at $what")
                }
                save("mini-player-${width}x$height")
            }
        }
    }

    @Test
    fun aLargeTextSizeGrowsTheDockInsideTheWindow() {
        for ((width, height) in listOf(412 to 915, 1440 to 900)) {
            val platform = docking()
            screen(populated(), platform, width = width, height = height, fontScale = 2f) { app ->
                await("the library") { exists(destination("Home")) }
                docked(app, platform)
                val box = node(dock).boundsInRoot
                assertTrue(box.left >= 0 && box.right <= width && box.top >= 0 && box.bottom <= height, "inside the window at 200%: $box")
                for (name in listOf("Heat, 1995", "Pause", "Stop playing")) assertTrue(exists(inDock(name)), "$name is still there at 200%")
                assertTrue(near(video(), node(inDock("Heat, 1995")).boundsInRoot), "and the picture is still in its box")
                save("mini-player-${width}x$height-text-200")
            }
        }
    }

    @Test
    fun withMotionReducedThePictureIsWhereItBelongsAtOnce() {
        val platform = docking()
        platform.accessibility = AccessibilityPreferences(reduceMotion = true)
        screen(populated(), platform) { app ->
            awaitText("Recently Added")
            playing(app, platform)
            mainClock.autoAdvance = false
            runOnIdle { app.playback!!.collapse() }
            // A few frames: enough for the dock to be laid out, far too few for anything to travel.
            repeat(4) { mainClock.advanceTimeByFrame() }
            waitForIdle()
            assertTrue(app.videoFrame is VideoFrame.Docked)
            assertTrue(near(video(), node(inDock("Heat, 1995")).boundsInRoot), "no journey: ${video()}")
            runOnIdle { app.playback!!.expand() }
            repeat(4) { mainClock.advanceTimeByFrame() }
            waitForIdle()
            assertTrue(near(video(), Rect(0f, 0f, 1440f, 900f)), "and none back: ${video()}")
        }
    }

    @Test
    fun whatCoversThePageTakesThePictureOutOfSight() {
        val offline = OfflineWorld()
        val platform = FakePlatform(docksVideo = true, offline = offline.offline)
        screen(offline.world, platform) { app ->
            await("the library") { exists(destination("Home")) }
            runOnIdle { app.play(app.library.movies().first().item.id) }
            await("the player") { app.playback != null && exists("Timeline") }
            runOnIdle { app.playback!!.collapse() }
            await("the mini-player") { exists(dock) && app.videoFrame is VideoFrame.Docked }
            val player = platform.players.single()

            // A sheet over the page: a native surface would show through it, so the surface leaves the window. The
            // dock stays, without its picture, and is not there for assistive technology while the sheet has the screen.
            runOnIdle { app.offlineOpen = true }
            await("the picture out of sight") { app.videoFrame == VideoFrame.Hidden && video().right <= 0f }
            assertEquals(PlayerStatus.PLAYING, player.state.value.status, "it plays on")
            assertFalse(exists(inDock("Stop playing")), "what is under the sheet is not a stop")
            runOnIdle { app.offlineOpen = false }
            await("the picture back in its box") { app.videoFrame is VideoFrame.Docked && exists(inDock("Stop playing")) }
            waitForIdle()
            assertTrue(near(video(), node(inDock(app.playback!!.title + ", " + app.playback!!.subtitle)).boundsInRoot))
            assertEquals(1, platform.players.size)
        }
    }

    @Test
    fun askingWhoIsWatchingCoversTheDockToo() {
        val world = populated()
        world.library.createProfile("Sam")
        val platform = docking()
        screen(world, platform) { app ->
            awaitText("Recently Added")
            docked(app, platform)
            runOnIdle { app.askWhoIsWatching() }
            awaitText("Who's watching?")
            await("the dock to be gone") { !exists(dock) && app.videoFrame == VideoFrame.Hidden }
            assertTrue(video().right <= 0f, "the picture is not left showing through the question")
            assertNotNull(app.playback)
        }
    }

    @Test
    fun aPageEndsClearOfTheDock() {
        val platform = docking()
        screen(populated(), platform, width = 412, height = 915) { app ->
            await("the library") { exists(destination("Home")) }
            runOnIdle { app.navigator.show(Destination.Movies) }
            await("the films") { onAllNodesWithText("Movies").fetchSemanticsNodes().isNotEmpty() }
            docked(app, platform)
            // Notices are said above it, never over it.
            runOnIdle { app.notify("Its source isn't connected") }
            awaitText("Its source isn't connected")
            val notice = onAllNodesWithText("Its source isn't connected").fetchSemanticsNodes().first().boundsInRoot
            val box = node(dock).boundsInRoot
            assertFalse(notice.overlaps(box), "the notice $notice is clear of the dock $box")
            assertNull(app.watching)
        }
    }

    // A back gesture on its way (docs/planning/M10-PLAYER-SPEC.md, section 4.4) ---------------------------------------

    @Test
    fun aBackGestureInProgressShrinksThePictureAndNothingElseAndCancellingLetsItGo() {
        val platform = docking()
        screen(populated(), platform) { app ->
            awaitText("Recently Added")
            playing(app, platform)
            val session = app.playback!!
            val whole = Rect(0f, 0f, 1440f, 900f)
            assertTrue(near(video(), whole))

            runOnIdle { app.backPreview = 0.5f }
            waitForIdle()
            val half = video()
            assertTrue(half.width < whole.width && half.width > whole.width * 0.9f, "on its way: $half")
            assertTrue(kotlin.math.abs((half.left + half.right) / 2 - 720f) < 2f && kotlin.math.abs((half.top + half.bottom) / 2 - 450f) < 2f, "about its middle: $half")
            assertEquals(PlayerPresentation.FULL, session.presentation, "the session moves when the gesture completes")
            assertSame(session, app.watching)

            runOnIdle { app.backPreview = 1f }
            waitForIdle()
            assertTrue(near(video(), Rect(72f, 45f, 1368f, 855f)), "the end of the preview is the window at 90%: ${video()}")

            // Taken back: nothing was changed but the preview, so the picture is the whole window again and nothing else moved.
            runOnIdle { app.backPreview = null }
            waitForIdle()
            assertTrue(near(video(), whole), "restored: ${video()}")
            assertEquals(PlayerPresentation.FULL, session.presentation)
            assertSame(session, app.watching)
            assertEquals(1, platform.players.size)
        }
    }

    @Test
    fun aBackGestureThatIsCompletedStepsAsideIntoTheDock() {
        val platform = docking()
        screen(populated(), platform) { app ->
            awaitText("Recently Added")
            playing(app, platform)
            val session = app.playback!!
            runOnIdle {
                app.backPreview = 1f
                app.backPreview = null
                app.dispatch(SemanticAction.BACK)
            }
            await("the mini-player") { exists(dock) && app.videoFrame is VideoFrame.Docked }
            assertSame(session, app.playback)
            assertEquals(PlayerPresentation.MINI, session.presentation)
            assertEquals(1, platform.players.size)
        }
    }
}
