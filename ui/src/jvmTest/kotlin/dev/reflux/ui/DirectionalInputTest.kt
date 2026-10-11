package dev.reflux.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.core.input.InputModality
import dev.reflux.core.input.Key
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import dev.reflux.ui.state.PlayerMenu
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drives the real screens with a controller and a keyboard through the semantic input path: buttons by
 * position, mapped to actions, moving real toolkit focus.
 */
@OptIn(ExperimentalTestApi::class)
class DirectionalInputTest {
    private fun DesktopComposeUiTest.press(app: AppState, button: GamepadButton, family: ControllerFamily = ControllerFamily.PLAYSTATION): Boolean {
        val handled = runOnIdle { app.onGamepadButton(button, family) }
        waitForIdle()
        return handled
    }

    private fun DesktopComposeUiTest.focusedBounds(): Rect = onNode(isFocused()).fetchSemanticsNode().boundsInRoot

    private fun DesktopComposeUiTest.shows(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun theFirstPressMorphsTheLayoutAndFocusesTheMainAction() = screen(populated()) { app ->
        awaitText("Recently Added")
        assertEquals(InputModality.POINTER, app.input.modality)
        assertFalse(shows("Select"), "no button prompts for someone using a mouse")

        assertTrue(press(app, GamepadButton.DPAD_DOWN))
        assertEquals(InputModality.DIRECTIONAL, app.input.modality)
        onNodeWithText("Play").assertIsFocused()
        assertEquals(Destination.Home, app.navigator.current, "the same screen, now laid out for a controller")
        // Prompts name the buttons of the controller in the user's hands.
        assertTrue(shows("Cross") && shows("Options"))
        save("home-controller-focus")

        press(app, GamepadButton.DPAD_DOWN, ControllerFamily.XBOX)
        assertTrue(shows("A") && shows("Menu") && !shows("Cross"), "an Xbox pad is prompted in its own terms")
    }

    @Test
    fun aControllerBrowsesOpensAndGoesBack() = screen(populated()) { app ->
        awaitText("Recently Added")
        press(app, GamepadButton.DPAD_DOWN)
        press(app, GamepadButton.DPAD_DOWN)
        val first = focusedBounds()
        press(app, GamepadButton.DPAD_RIGHT)
        val second = focusedBounds()
        assertTrue(second.left > first.left && abs(second.top - first.top) < 12f, "right moves along the row ($first then $second)")

        assertTrue(press(app, GamepadButton.FACE_SOUTH), "confirm activates the focused card")
        await("details to open") { app.navigator.current is Destination.Details }
        awaitText("Cast")
        // A new screen has no focus yet: the next press lands on its main action.
        press(app, GamepadButton.DPAD_DOWN)
        onNode(isFocused()).assert(hasText("Play", substring = true))

        assertTrue(press(app, GamepadButton.FACE_EAST))
        awaitText("Recently Added")
        assertEquals(Destination.Home, app.navigator.current)
        assertFalse(press(app, GamepadButton.FACE_EAST), "nothing to go back to at the root")
    }

    @Test
    fun nintendoConfirmsWithItsEastButton() = screen(populated()) { app ->
        awaitText("Recently Added")
        press(app, GamepadButton.DPAD_DOWN, ControllerFamily.NINTENDO)
        press(app, GamepadButton.DPAD_RIGHT, ControllerFamily.NINTENDO)
        onNodeWithText("Details").assertIsFocused()
        press(app, GamepadButton.FACE_EAST, ControllerFamily.NINTENDO)
        await("details to open") { app.navigator.current is Destination.Details }
        press(app, GamepadButton.FACE_SOUTH, ControllerFamily.NINTENDO)
        assertEquals(Destination.Home, app.navigator.current)
    }

    @Test
    fun focusNeverEndsUpUnderTheNavigationBarOrAgainstAnEdge() = screen(populated()) { app ->
        awaitText("Recently Added")
        val viewport = Rect(0f, 0f, 1440f, 900f)
        val barBottom = 14f + 56f
        repeat(9) { step ->
            press(app, GamepadButton.DPAD_DOWN)
            val bounds = focusedBounds()
            assertTrue(bounds.top >= barBottom && bounds.bottom <= viewport.bottom, "step $step: the focused item is fully visible below the bar ($bounds)")
        }
        // Back up the page: items scrolled under the bar come back out from under it.
        repeat(5) { step ->
            press(app, GamepadButton.DPAD_UP)
            val bounds = focusedBounds()
            assertTrue(bounds.top >= barBottom, "step $step up: clear of the bar ($bounds)")
        }
        repeat(9) { step ->
            press(app, GamepadButton.DPAD_RIGHT)
            val bounds = focusedBounds()
            assertTrue(bounds.left >= 0f && bounds.right <= viewport.right, "step $step right: fully inside the window ($bounds)")
        }
    }

    @Test
    fun theSourcesPanelTakesAndKeepsFocus() = screen(populated()) { app ->
        awaitText("Recently Added")
        press(app, GamepadButton.DPAD_DOWN)
        assertTrue(press(app, GamepadButton.START))
        awaitText("Add folder")
        onNodeWithText("Add folder").assertIsFocused()
        val panel = onNodeWithText("Media sources").fetchSemanticsNode().boundsInRoot
        for (button in listOf(GamepadButton.DPAD_DOWN, GamepadButton.DPAD_LEFT, GamepadButton.DPAD_DOWN, GamepadButton.DPAD_LEFT, GamepadButton.DPAD_UP, GamepadButton.DPAD_UP)) {
            press(app, button)
            val bounds = focusedBounds()
            assertTrue(bounds.left >= panel.left - 24f && bounds.top >= panel.top - 24f, "focus stays in the panel after $button ($bounds)")
        }
        assertTrue(press(app, GamepadButton.FACE_EAST))
        assertFalse(app.sourcesOpen)
        assertEquals(Destination.Home, app.navigator.current, "Back closed the panel, not the page")
    }

    @Test
    fun theViewButtonTogglesFullscreenAnywhere() {
        val platform = FakePlatform()
        screen(populated(), platform) { app ->
            awaitText("Recently Added")
            press(app, GamepadButton.SELECT)
            assertTrue(platform.fullscreen)
            press(app, GamepadButton.SELECT, ControllerFamily.XBOX)
            assertFalse(platform.fullscreen)
        }
    }

    @Test
    fun theKeyboardSharesTheSamePath() = screen(populated()) { app ->
        awaitText("Recently Added")
        runOnIdle { app.onKey(Key.ARROW_DOWN) }
        waitForIdle()
        assertEquals(InputModality.KEYBOARD, app.input.modality)
        onNodeWithText("Play").assertIsFocused()
        assertFalse(shows("Select"), "button prompts are for controllers")
        runOnIdle { app.onKey(Key.ARROW_RIGHT) }
        waitForIdle()
        onNodeWithText("Details").assertIsFocused()
    }

    @Test
    fun aControllerPlaysSeeksAndUsesThePlayerMenus() {
        val platform = FakePlatform()
        screen(populated(), platform) { app ->
            awaitText("Recently Added")
            runOnIdle { app.play(app.library.shows().first { it.item.title == "Breaking Bad" }.item.id) }
            awaitText("S1 · E1 · Pilot")
            val player = platform.players.single()
            val session = assertNotNull(app.playback)

            press(app, GamepadButton.FACE_SOUTH)
            assertEquals(PlayerStatus.PAUSED, player.state.value.status)
            press(app, GamepadButton.DPAD_RIGHT)
            assertEquals(10_000L, player.state.value.positionMs)
            press(app, GamepadButton.DPAD_LEFT)
            assertEquals(0L, player.state.value.positionMs)
            assertTrue(shows("Options"), "the player prompts for its own actions")

            // Options opens the menus; the D-pad now moves through them instead of seeking.
            press(app, GamepadButton.START)
            assertEquals(PlayerMenu.SUBTITLES, session.menu)
            awaitText("Off")
            save("player-menu-controller")
            press(app, GamepadButton.DPAD_DOWN)
            press(app, GamepadButton.FACE_SOUTH)
            assertEquals("1", player.state.value.selectedSubtitleId)
            assertNull(session.menu)
            assertEquals(0L, player.state.value.positionMs, "navigating a menu did not seek")

            press(app, GamepadButton.START)
            press(app, GamepadButton.DPAD_LEFT)
            assertEquals(PlayerMenu.AUDIO, session.menu)
            press(app, GamepadButton.DPAD_LEFT)
            assertEquals(PlayerMenu.CHAPTERS, session.menu)
            press(app, GamepadButton.DPAD_DOWN)
            press(app, GamepadButton.FACE_SOUTH)
            assertEquals(150_000L, player.state.value.positionMs, "one down from the first chapter")

            press(app, GamepadButton.START)
            assertNotEquals(null, session.menu)
            press(app, GamepadButton.FACE_EAST)
            assertNull(session.menu)
            assertNotNull(app.playback, "Back closed the menu, not the player")

            press(app, GamepadButton.SHOULDER_RIGHT)
            awaitText("S1 · E2 · Cat's in the Bag...")
            press(app, GamepadButton.SELECT)
            assertTrue(platform.fullscreen)
            press(app, GamepadButton.FACE_EAST)
            await("the player to close") { app.playback == null }
            assertFalse(platform.fullscreen, "leaving the player restores the window")
        }
    }
}
