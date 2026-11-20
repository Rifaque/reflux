package dev.reflux.ui

import androidx.compose.ui.test.ExperimentalTestApi
import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.ui.state.Destination
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * B-055: the controller's hints. LT hides them all, its own included (the owner's walk, 2026-11-20); LT again shows them;
 * the choice is kept across restarts. They say only what can be done here and now.
 */
@OptIn(ExperimentalTestApi::class)
class HintsTest {
    @Test
    fun ltHidesEveryHintAndBringsThemBack() {
        val world = populated()
        screen(world) { app ->
            awaitText("Recently Added")
            runOnIdle { app.onGamepadButton(GamepadButton.DPAD_DOWN, ControllerFamily.XBOX) }
            await("the hints") { prompted("A Select") && prompted("Y Sidebar") && prompted("LT Hide hints") }
            save("hints-xbox")
            runOnIdle { assertTrue(app.onGamepadButton(GamepadButton.TRIGGER_LEFT, ControllerFamily.XBOX)) }
            await("all hidden, LT's own too") { !prompted("A Select") && !prompted("Sidebar") && !prompted("LT") }
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
            waitForIdle()
            assertFalse(prompted("A Select"))
            assertFalse(prompted("LT"))
            runOnIdle { app.go(Destination.Search) }
            await("search") { app.navigator.current == Destination.Search }
        }
    }
}
