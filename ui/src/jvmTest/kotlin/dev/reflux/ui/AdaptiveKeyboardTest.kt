package dev.reflux.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.core.input.InputModality
import dev.reflux.core.input.Key
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import dev.reflux.ui.state.KeyboardKey
import dev.reflux.ui.state.KeyboardMode
import dev.reflux.ui.state.SystemKey
import dev.reflux.ui.state.SystemKeymap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * F-P4 in the running experience: the desk keyboard for a pointer on a desk, the grid for a controller, live switching
 * with nothing lost, keys moving rather than jumping, title completions, and a physical keyboard as its own path.
 */
@OptIn(ExperimentalTestApi::class)
class AdaptiveKeyboardTest {
    private fun DesktopComposeUiTest.key(name: String) =
        onNode(SemanticsMatcher("key $name") { it.config.getOrNull(SemanticsProperties.TestTag) == "key:$name" }, useUnmergedTree = true)

    private fun DesktopComposeUiTest.keyBounds(name: String) = key(name).fetchSemanticsNode().boundsInRoot

    private fun DesktopComposeUiTest.openSearchKeyboard(app: AppState) {
        runOnIdle { app.go(Destination.Search) }
        await("the field") { onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        runOnIdle { app.openKeyboard(searchRequest(app)) }
        await("the keyboard") { app.keyboard != null }
        mainClock.advanceTimeBy(800)
        waitForIdle()
    }

    private fun searchRequest(app: AppState) = dev.reflux.ui.state.KeyboardRequest(this, "Search", dev.reflux.ui.state.InputKind.SEARCH, { app.searchQuery }, { app.searchQuery = it }, {})

    private fun DesktopComposeUiTest.press(app: AppState, button: GamepadButton) {
        runOnIdle { app.onGamepadButton(button, ControllerFamily.XBOX) }
        mainClock.advanceTimeBy(100)
        waitForIdle()
    }

    @Test
    fun aPointerOnADeskGetsTheDeskKeyboard_aControllerMorphsItIntoTheGrid_andNothingIsLost() {
        val platform = FakePlatform(deskKeyboard = true)
        screen(populated(), platform, width = 1440, height = 900) { app ->
            awaitText("Recently Added")
            openSearchKeyboard(app)
            assertEquals(KeyboardMode.POINTER, app.keyboard?.mode, "a pointer on a desk: the desk keyboard")
            assertNotNull(runCatching { keyBounds("Caps Lock") }.getOrNull(), "with a desk keyboard's keys")
            save("keyboard-desk")
            key("h").performClick()
            key("e").performClick()
            assertEquals("he", app.searchQuery)
            val deskQ = keyBounds("q")
            // A controller's press: the grid, the keys moving to it.
            press(app, GamepadButton.DPAD_RIGHT)
            assertEquals(KeyboardMode.CONTROLLER, app.keyboard?.mode)
            mainClock.advanceTimeBy(120)
            waitForIdle()
            val midway = keyBounds("q")
            mainClock.advanceTimeBy(900)
            waitForIdle()
            val gridQ = keyBounds("q")
            save("keyboard-grid-after-morph")
            assertTrue(gridQ != deskQ, "q has a place of its own in the grid")
            val between = { a: Float, b: Float, c: Float -> c in minOf(a, b) - 1f..maxOf(a, b) + 1f }
            assertTrue(between(deskQ.left, gridQ.left, midway.left) && between(deskQ.width, gridQ.width, midway.width), "q travels between its two places: $deskQ, $midway, $gridQ")
            assertEquals("he", app.searchQuery, "nothing typed is lost")
            assertEquals(2, app.keyboard?.cursor)
            assertNull(runCatching { keyBounds("Caps Lock") }.getOrNull(), "keys only the desk has are gone")
            // Focus went to the key last clicked, then moved right from it.
            assertEquals(KeyboardKey.Type("r"), app.keyboard?.chosen)
        }
    }

    @Test
    fun incidentalPointerMovementDoesNotSwitch_aDeliberateOneDoes() {
        val platform = FakePlatform(deskKeyboard = true)
        screen(populated(), platform, width = 1440, height = 900) { app ->
            awaitText("Recently Added")
            runOnIdle { app.onSignal(dev.reflux.core.input.InputSignal.GamepadButtonPress(GamepadButton.DPAD_DOWN, ControllerFamily.XBOX)) }
            openSearchKeyboard(app)
            assertEquals(KeyboardMode.CONTROLLER, app.keyboard?.mode, "a controller in hand: the grid")
            runOnIdle { app.keyboardPointerTravel(10f) }
            assertEquals(KeyboardMode.CONTROLLER, app.keyboard?.mode, "a nudge changes nothing")
            Thread.sleep(600)
            runOnIdle { repeat(6) { app.keyboardPointerTravel(10f) } }
            assertEquals(KeyboardMode.POINTER, app.keyboard?.mode, "travel over the keyboard does")
        }
    }

    @Test
    fun aPhysicalKeyboardNeverPressesOrLightsAVirtualKey_andEnterFinishes() {
        val platform = FakePlatform(deskKeyboard = true)
        screen(populated(), platform, width = 1440, height = 900) { app ->
            awaitText("Recently Added")
            runOnIdle { app.onSignal(dev.reflux.core.input.InputSignal.GamepadButtonPress(GamepadButton.DPAD_DOWN, ControllerFamily.XBOX)) }
            openSearchKeyboard(app)
            val chosen = app.keyboard?.chosen
            runOnIdle { app.searchQuery = "heat" }
            for (k in listOf(Key.ARROW_RIGHT, Key.ARROW_DOWN, Key.SPACE, Key.BACKSPACE)) {
                runOnIdle { app.onKey(k) }
            }
            assertNotNull(app.keyboard, "physical typing never hides it")
            assertEquals(chosen, app.keyboard?.chosen, "and never moves its chosen key")
            assertEquals("heat", app.searchQuery, "no virtual key was pressed")
            assertEquals(KeyboardMode.CONTROLLER, app.keyboard?.mode, "nor changes its presentation")
            runOnIdle { app.onKey(Key.ENTER) }
            assertNull(app.keyboard, "Enter finishes, as the field's own action")
        }
    }

    @Test
    fun completionsComeFromTheLibraryAndFillTheQueryWithoutOpeningAnything() {
        val platform = FakePlatform(deskKeyboard = true)
        screen(populated(), platform, width = 1440, height = 900) { app ->
            awaitText("Recently Added")
            openSearchKeyboard(app)
            runOnIdle { app.searchQuery = "bla" }
            await("a completion") { app.keyboard?.completions?.contains("Blade Runner 2049") == true }
            assertNull(app.keyboard?.completion, "offered, never focused")
            save("keyboard-completions")
            onNode(hasTestTag("completion:Blade Runner 2049"), useUnmergedTree = true).performClick()
            assertEquals("Blade Runner 2049", app.searchQuery)
            assertNotNull(app.keyboard, "the keyboard stays open")
            assertEquals(Destination.Search, app.navigator.current, "nothing was opened")
        }
    }

    @Test
    fun anUnreadableLayoutShowsUsWithANote_andATelevisionHasOnlyTheGrid() {
        screen(populated(), FakePlatform(deskKeyboard = true, keymap = null), width = 1440, height = 900) { app ->
            awaitText("Recently Added")
            openSearchKeyboard(app)
            await("the note") { onAllNodes(hasTestTag("keyboard-note"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        }
        val azerty = SystemKeymap("AZERTY", listOf(emptyList(), listOf(SystemKey("a", "A"), SystemKey("z", "Z")), emptyList(), emptyList()))
        screen(populated(), FakePlatform(deskKeyboard = true, keymap = azerty), width = 1440, height = 900) { app ->
            awaitText("Recently Added")
            openSearchKeyboard(app)
            assertFalse(onAllNodes(hasTestTag("keyboard-note"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "the system's layout: no note")
            save("keyboard-desk-azerty")
        }
        television(populated()) { app ->
            awaitText("Recently Added")
            openSearchKeyboard(app)
            assertEquals(KeyboardMode.CONTROLLER, app.keyboard?.mode)
            runOnIdle { app.keyboardPointerTravel(500f) }
            assertEquals(KeyboardMode.CONTROLLER, app.keyboard?.mode, "a television has no desk keyboard")
            assertTrue(app.input.modality == InputModality.DIRECTIONAL)
        }
    }
}
