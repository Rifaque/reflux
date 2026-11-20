package dev.reflux.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performMouseInput
import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.core.input.InputSignal
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import dev.reflux.ui.state.InputKind
import dev.reflux.ui.state.KeyboardKey
import dev.reflux.ui.state.KeyboardMode
import dev.reflux.ui.state.KeyboardRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The owner's walk of the adaptive keyboard on Windows (2026-11-20), with real mouse events rather than semantics clicks:
 * every key types by mouse and by controller in either presentation, the pointer taking over shows the desk keyboard
 * without a key having to be clicked, a click on the panel does too, and Tab takes the offered title.
 */
@OptIn(ExperimentalTestApi::class)
class KeyboardWalkTest {
    private fun DesktopComposeUiTest.key(name: String) =
        onNode(SemanticsMatcher("key $name") { it.config.getOrNull(SemanticsProperties.TestTag) == "key:$name" }, useUnmergedTree = true)

    private fun DesktopComposeUiTest.mouse(name: String) {
        key(name).performMouseInput { click() }
        mainClock.advanceTimeBy(200)
        waitForIdle()
    }

    private fun DesktopComposeUiTest.press(app: AppState, button: GamepadButton) {
        runOnIdle {
            app.onSignal(InputSignal.GamepadButtonPress(button, ControllerFamily.XBOX))
            app.onGamepadButton(button, ControllerFamily.XBOX)
        }
        mainClock.advanceTimeBy(100)
        waitForIdle()
    }

    private fun DesktopComposeUiTest.open(app: AppState, controller: Boolean) {
        awaitText("Recently Added")
        if (controller) runOnIdle { app.onSignal(InputSignal.GamepadButtonPress(GamepadButton.DPAD_DOWN, ControllerFamily.XBOX)) }
        runOnIdle { app.go(Destination.Search) }
        await("the field") { onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        runOnIdle { app.openKeyboard(KeyboardRequest(this, "Search", InputKind.SEARCH, { app.searchQuery }, { app.searchQuery = it }, {})) }
        await("the keyboard") { app.keyboard != null }
        mainClock.advanceTimeBy(800)
        waitForIdle()
    }

    @Test
    fun everyKeyTypesByMouseInTheDeskKeyboard() {
        screen(populated(), FakePlatform(deskKeyboard = true), width = 1440, height = 900) { app ->
            open(app, controller = false)
            assertEquals(KeyboardMode.POINTER, app.keyboard?.mode)
            for (k in listOf("h", "e", "a", "t")) mouse(k)
            assertEquals("heat", app.searchQuery)
            mouse("Erase")
            assertEquals("hea", app.searchQuery)
            mouse("Space")
            assertEquals("hea ", app.searchQuery)
            mouse("Shift"); mouse("T")
            assertEquals("hea T", app.searchQuery)
            mouse("Caps Lock"); mouse("A"); mouse("B"); mouse("Caps Lock, on"); mouse("c")
            assertEquals("hea TABc", app.searchQuery)
            mouse("Clear")
            assertEquals("", app.searchQuery)
            for (k in listOf("b", "l", "a")) mouse(k)
            await("a completion") { app.keyboard?.completions?.contains("Blade Runner 2049") == true }
            mouse("Tab, take the suggestion")
            assertEquals("Blade Runner 2049", app.searchQuery, "Tab takes the offered title")
            assertNotNull(app.keyboard)
        }
    }

    @Test
    fun aControllerTypesWithItsFirstPress_andAMouseClickTypesInTheGrid() {
        screen(populated(), FakePlatform(deskKeyboard = true), width = 1440, height = 900) { app ->
            open(app, controller = true)
            assertEquals(KeyboardMode.CONTROLLER, app.keyboard?.mode)
            val first = app.keyboard?.chosen
            press(app, GamepadButton.FACE_SOUTH)
            assertEquals((first as KeyboardKey.Type).text, app.searchQuery, "the first A types its key")
            press(app, GamepadButton.DPAD_RIGHT); press(app, GamepadButton.FACE_SOUTH)
            assertEquals(2, app.searchQuery.length)
            press(app, GamepadButton.FACE_WEST)
            assertEquals(1, app.searchQuery.length, "X erases")
            // The mouse moving in takes over (the hints and the keyboard together); the key clicked then is typed.
            Thread.sleep(600)
            key("k").performMouseInput { moveTo(center - androidx.compose.ui.geometry.Offset(120f, 0f)); repeat(6) { moveBy(androidx.compose.ui.geometry.Offset(20f, 0f)) } }
            mainClock.advanceTimeBy(100)
            waitForIdle()
            assertEquals(KeyboardMode.POINTER, app.keyboard?.mode, "the mouse moving in shows the desk keyboard")
            // The keys finish moving into place, then the mouse aims at one.
            mainClock.advanceTimeBy(3000)
            waitForIdle()
            mouse("k")
            assertEquals(2, app.searchQuery.length)
            assertEquals('k', app.searchQuery.last())
            assertEquals(KeyboardMode.POINTER, app.keyboard?.mode)
            // Back to the controller: its first press after the click types, at the clicked key moved on.
            Thread.sleep(600)
            press(app, GamepadButton.FACE_SOUTH)
            assertEquals(3, app.searchQuery.length, "a controller's first press after the mouse types")
        }
    }

    @Test
    fun thePointerTakingOverShowsTheDeskKeyboardWithoutAClickOnAKey() {
        screen(populated(), FakePlatform(deskKeyboard = true), width = 1440, height = 900) { app ->
            open(app, controller = true)
            assertEquals(KeyboardMode.CONTROLLER, app.keyboard?.mode)
            Thread.sleep(600)
            // Deliberate pointer travel anywhere, as the app's modality sees it (the hints change with it).
            runOnIdle { repeat(8) { app.onSignal(InputSignal.PointerMove(20f, 20f)) } }
            mainClock.advanceTimeBy(700)
            waitForIdle()
            assertEquals(dev.reflux.core.input.InputModality.POINTER, app.input.modality, "the hints follow the pointer")
            assertEquals(KeyboardMode.POINTER, app.keyboard?.mode, "and so does the keyboard, with no key clicked")
        }
    }
}
