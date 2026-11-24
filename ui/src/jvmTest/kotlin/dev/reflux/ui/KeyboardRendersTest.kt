package dev.reflux.ui

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.core.input.InputSignal
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import dev.reflux.ui.state.InputKind
import dev.reflux.ui.state.KeyboardRequest
import dev.reflux.library.createProfile
import dev.reflux.library.setPin
import kotlin.test.Test

/**
 * Renders of the Reflux keyboard and the PIN pad as a controller or a remote sees them (CACHY-005, CACHY-006), at a desk,
 * on a television and on a phone, with a search typed so the results behind it show. `-Preflux.screenshots=<dir>` saves them.
 */
@OptIn(ExperimentalTestApi::class)
class KeyboardRendersTest {
    private fun DesktopComposeUiTest.openSearch(app: AppState, query: String) {
        awaitText("Recently Added")
        runOnIdle { app.onSignal(InputSignal.GamepadButtonPress(GamepadButton.DPAD_DOWN, ControllerFamily.XBOX)) }
        runOnIdle { app.go(Destination.Search) }
        await("the field") { onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        runOnIdle { app.searchQuery = query }
        await("the field has focus") { app.textInputActive }
        // As a viewer does: A on the focused field opens the keyboard, which knows where the field is.
        runOnIdle { app.onGamepadButton(GamepadButton.FACE_SOUTH, ControllerFamily.XBOX) }
        await("the keyboard") { app.keyboard != null }
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
    }

    private fun DesktopComposeUiTest.openPin(app: AppState) {
        awaitText("Recently Added")
        runOnIdle { app.onSignal(InputSignal.GamepadButtonPress(GamepadButton.DPAD_DOWN, ControllerFamily.XBOX)) }
        runOnIdle { app.openKeyboard(KeyboardRequest(this, "PIN", InputKind.PIN, { "" }, { }, {})) }
        await("the keypad") { app.keyboard != null }
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
    }

    @Test
    fun deskWithAController() = screen(populated()) { app ->
        openSearch(app, "he")
        save("keyboard-controller-desk")
    }

    @Test
    fun television() = screen(populated(), FakePlatform(television = true), width = 960, height = 540) { app ->
        openSearch(app, "he")
        save("keyboard-controller-tv")
    }

    @Test
    fun phone() = screen(populated(), width = 412, height = 915) { app ->
        openSearch(app, "he")
        save("keyboard-controller-phone")
    }

    @Test
    fun pinPadAtADesk() = screen(populated()) { app ->
        openPin(app)
        save("pin-pad-desk")
    }

    @Test
    fun theProfilePinGateWithAController() {
        val world = populated()
        val platform = FakePlatform()
        val sam = world.library.createProfile("Sam")
        world.library.setPin(sam, "2580", platform.pinKey!!)
        screen(world, platform) { app ->
            awaitText("Recently Added")
            await("the profiles") { app.profiles.size == 2 }
            runOnIdle { app.chooseProfile(app.profiles.first { it.name == "Sam" }) }
            awaitText("Enter this profile's PIN.")
            // The first press focuses the gate's start, the PIN field; confirm opens the number pad (InteractionContractTest).
            runOnIdle { app.onGamepadButton(GamepadButton.DPAD_DOWN, ControllerFamily.XBOX) }
            await("the field") { app.textInputActive }
            runOnIdle { app.onGamepadButton(GamepadButton.FACE_SOUTH, ControllerFamily.XBOX) }
            await("the keypad") { app.keyboard != null }
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            save("pin-gate-desk")
        }
    }

    @Test
    fun pinPadOnATelevision() = screen(populated(), FakePlatform(television = true), width = 960, height = 540) { app ->
        openPin(app)
        save("pin-pad-tv")
    }
}
