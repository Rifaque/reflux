package dev.reflux.ui

import androidx.compose.ui.test.ExperimentalTestApi
import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.ui.state.Destination
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * B-055: the controller's hints while browsing. LT hides them, and only LT's own hint stays to bring them back; LT again
 * shows them; the choice is kept. They say only what can be done here and now.
 */
@OptIn(ExperimentalTestApi::class)
class HintsTest {
    @Test
    fun ltHidesTheHintsLeavingItsOwnAndBringsThemBack() {
        val world = populated()
        screen(world) { app ->
            awaitText("Recently Added")
            runOnIdle { app.onGamepadButton(GamepadButton.DPAD_DOWN, ControllerFamily.XBOX) }
            await("the hints") { prompted("A Select") && prompted("Y Sidebar") && prompted("LT Hide hints") }
            save("hints-xbox")
            runOnIdle { assertTrue(app.onGamepadButton(GamepadButton.TRIGGER_LEFT, ControllerFamily.XBOX)) }
            await("hidden but for LT") { prompted("LT Show hints") && !prompted("A Select") && !prompted("Sidebar") }
            waitForIdle()
            save("hints-hidden")
            // Hidden, the buttons still work: Y still goes to the sidebar.
            runOnIdle { assertTrue(app.onGamepadButton(GamepadButton.FACE_NORTH, ControllerFamily.XBOX)) }
            runOnIdle { app.onGamepadButton(GamepadButton.FACE_NORTH, ControllerFamily.XBOX) }
            runOnIdle { app.onGamepadButton(GamepadButton.TRIGGER_LEFT, ControllerFamily.XBOX) }
            await("shown again") { prompted("A Select") && prompted("LT Hide hints") }
            // A PlayStation pad is shown in its own terms.
            runOnIdle { app.onGamepadButton(GamepadButton.DPAD_RIGHT, ControllerFamily.PLAYSTATION) }
            await("in a PlayStation's terms") { prompted("Cross Select") && prompted("L2 Hide hints") }
            save("hints-playstation")
            // Hidden once more, and kept for the next time Reflux opens.
            runOnIdle { app.onGamepadButton(GamepadButton.TRIGGER_LEFT, ControllerFamily.PLAYSTATION) }
            await("hidden") { app.hintsHidden }
            await("kept") { world.library.setting("controller_hints") == "hidden" }
        }
        screen(world) { app ->
            awaitText("Recently Added")
            await("still hidden after a restart") { app.hintsHidden }
            runOnIdle { app.onGamepadButton(GamepadButton.DPAD_DOWN, ControllerFamily.XBOX) }
            await("only LT's hint") { prompted("LT Show hints") }
            assertFalse(prompted("A Select"))
            runOnIdle { app.go(Destination.Search) }
            await("search") { app.navigator.current == Destination.Search }
        }
    }
}
