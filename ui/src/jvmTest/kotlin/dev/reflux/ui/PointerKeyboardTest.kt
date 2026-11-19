package dev.reflux.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performMouseInput
import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.core.input.InputModality
import dev.reflux.ui.state.Destination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B-051: on a Windows or Linux desk, Search offers the Reflux keyboard to someone with only a mouse. Keys are clicked where
 * they are, with a press that is felt and gone; the lit key is a controller's and a remote's alone. Nowhere else is the
 * button offered.
 */
@OptIn(ExperimentalTestApi::class)
class PointerKeyboardTest {
    private fun SemanticsNodeInteraction.mouseClick() = performMouseInput { click() }

    private fun DesktopComposeUiTest.key(name: String) = onNode(hasTestTag("key:$name"))

    private fun DesktopComposeUiTest.litKeys(): Int =
        onAllNodes(SemanticsMatcher("a key") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("key:") == true && it.config.getOrNull(SemanticsProperties.Selected) == true })
            .fetchSemanticsNodes().size

    private fun DesktopComposeUiTest.toSearch(app: dev.reflux.ui.state.AppState) {
        awaitText("Recently Added")
        runOnIdle { app.go(Destination.Search) }
        await("search") { app.navigator.current == Destination.Search }
        waitForIdle()
    }

    @Test
    fun aMouseAloneSearchesWithTheRefluxKeyboard() {
        screen(populated(), FakePlatform(pointerKeyboard = true)) { app ->
            toSearch(app)
            assertTrue(onAllNodesWithContentDescription("On-screen keyboard").fetchSemanticsNodes().isNotEmpty(), "offered with the field empty")
            onNodeWithContentDescription("On-screen keyboard").mouseClick()
            await("the keyboard") { app.keyboard != null }
            assertEquals(InputModality.POINTER, app.input.modality)
            for (letter in listOf("h", "e", "a", "t")) {
                key(letter).mouseClick()
                waitForIdle()
                assertEquals(0, litKeys(), "no key is left lit by a click ($letter)")
            }
            await("typed into Search") { app.searchQuery == "heat" }
            await("what was found") { saysAnywhere("Heat, 1995") }
            // Erase, a space, Shift: all the keyboard's own keys work by mouse.
            key("Erase").mouseClick()
            await("erased") { app.searchQuery == "hea" }
            key("Space").mouseClick()
            await("a space") { app.searchQuery == "hea " }
            key("Shift").mouseClick()
            key("T").mouseClick()
            await("a capital") { app.searchQuery == "hea T" }
            assertEquals(0, litKeys())
            // Both buttons beside the field: clear, and the keyboard.
            assertTrue(onAllNodesWithContentDescription("Clear search").fetchSemanticsNodes().isNotEmpty())
            assertTrue(onAllNodesWithContentDescription("On-screen keyboard").fetchSemanticsNodes().isNotEmpty())
            // A controller picks it up: its key is lit again, and moves with the D-pad.
            runOnIdle { app.onGamepadButton(GamepadButton.DPAD_RIGHT, ControllerFamily.XBOX) }
            await("a lit key for the controller") { litKeys() == 1 }
            // Done finishes typing.
            runOnIdle { app.onGamepadButton(GamepadButton.START, ControllerFamily.XBOX) }
            await("closed") { app.keyboard == null }
            assertEquals("hea T", app.searchQuery)
        }
    }

    @Test
    fun notOfferedOnAPhoneATabletOrATelevision() {
        for (platform in listOf(FakePlatform(), FakePlatform(television = true))) {
            screen(populated(), platform) { app ->
                toSearch(app)
                assertTrue(onAllNodesWithContentDescription("On-screen keyboard").fetchSemanticsNodes().isEmpty(), "no keyboard button where the platform has its own")
                assertNull(app.keyboard)
            }
        }
    }

    @Test
    fun theControllersLitKeyIsUnchanged() {
        screen(populated(), FakePlatform(pointerKeyboard = true)) { app ->
            awaitText("Recently Added")
            runOnIdle { app.onGamepadButton(GamepadButton.DPAD_DOWN, ControllerFamily.XBOX) }
            repeat(3) { runOnIdle { app.onGamepadButton(GamepadButton.SHOULDER_RIGHT, ControllerFamily.XBOX) } }
            await("the field") { app.navigator.current == Destination.Search && app.textInputActive }
            runOnIdle { app.onGamepadButton(GamepadButton.FACE_SOUTH, ControllerFamily.XBOX) }
            assertNotNull(app.keyboard)
            await("one lit key") { litKeys() == 1 }
        }
    }
}
