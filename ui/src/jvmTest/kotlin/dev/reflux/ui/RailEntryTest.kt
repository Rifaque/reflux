package dev.reflux.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The owner's walk on Windows (2026-11-20). B-072: Right from the sidebar into a page just chosen there, however soon it
 * is pressed. B-071: hidden hints leave nothing on screen, not even the bar's empty pill. B-073: a row's paging arrows are
 * the mouse's, gone while a controller is in use even with the pointer resting over the row.
 */
@OptIn(ExperimentalTestApi::class)
class RailEntryTest {
    private fun DesktopComposeUiTest.press(app: AppState, b: GamepadButton) {
        runOnIdle { app.onGamepadButton(b, ControllerFamily.XBOX) }
        waitForIdle()
    }

    private fun DesktopComposeUiTest.focused(): Rect? = runCatching { onNode(isFocused()).fetchSemanticsNode().boundsInRoot }.getOrNull()

    /** The sidebar's width at 1440: the page starts after it. */
    private fun DesktopComposeUiTest.inPage() = focused()?.let { it.left > 220f } == true

    @Test
    fun rightFromTheSidebarEntersHomeHoweverSoonAfterChoosingIt() {
        // Before the fix: within about a quarter of a second of choosing Home, Right was lost (focus on the leaving page,
        // then nothing) or stayed in the sidebar; a second Right was needed.
        for (soon in listOf(0L, 16L, 120L, 250L)) {
            screen(populated()) { app ->
                awaitText("Recently Added")
                press(app, GamepadButton.DPAD_DOWN)
                press(app, GamepadButton.DPAD_LEFT)
                press(app, GamepadButton.DPAD_DOWN)
                press(app, GamepadButton.FACE_SOUTH)
                await("movies") { app.navigator.current == Destination.Movies }
                press(app, GamepadButton.DPAD_UP)
                mainClock.autoAdvance = false
                runOnUiThread { app.onGamepadButton(GamepadButton.FACE_SOUTH, ControllerFamily.XBOX) }
                mainClock.advanceTimeBy(soon)
                runOnUiThread { app.onGamepadButton(GamepadButton.DPAD_RIGHT, ControllerFamily.XBOX) }
                mainClock.autoAdvance = true
                waitForIdle()
                mainClock.advanceTimeBy(2_000)
                waitForIdle()
                assertTrue(app.navigator.current == Destination.Home && inPage(), "Right $soon ms after choosing Home entered it (focus at ${focused()})")
            }
        }
    }

    @Test
    fun hiddenHintsLeaveNoBarBehind() {
        screen(populated()) { app ->
            awaitText("Recently Added")
            press(app, GamepadButton.DPAD_DOWN)
            await("the hints") { prompted("A Select") }
            press(app, GamepadButton.TRIGGER_LEFT)
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            assertTrue(onAllNodes(androidx.compose.ui.test.SemanticsMatcher("a hint bar") { node ->
                node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.startsWith("prompts:") == true
            }).fetchSemanticsNodes().isEmpty(), "no bar, empty or not, once the hints are hidden")
            press(app, GamepadButton.TRIGGER_LEFT)
            await("back") { prompted("A Select") }
        }
    }

    @Test
    fun aRowsArrowsAreTheMousesAndFadeWhenTheControllerMoves() {
        screen(populated()) { app ->
            awaitText("Recently Added")
            val more = hasContentDescription("More of Recently Added")
            // The mouse over the row, moving: its arrow to page.
            val row = onAllNodes(androidx.compose.ui.test.hasScrollAction()).fetchSemanticsNodes()
                .first { it.boundsInRoot.width > it.boundsInRoot.height && it.boundsInRoot.left > 200f }
            onRoot().performMouseInput { moveTo(androidx.compose.ui.geometry.Offset(row.boundsInRoot.center.x - 40f, row.boundsInRoot.center.y)); repeat(6) { moveBy(androidx.compose.ui.geometry.Offset(8f, 0f)) } }
            await("the mouse's arrow") { onAllNodes(more).fetchSemanticsNodes().isNotEmpty() }
            // The controller moves; the pointer rests where it was: the arrows fade.
            press(app, GamepadButton.DPAD_DOWN)
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            assertFalse(onAllNodes(more).fetchSemanticsNodes().isNotEmpty(), "no arrows while the controller is in use")
        }
    }
}
