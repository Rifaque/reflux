package dev.reflux.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.input.key.Key
import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.ui.state.Destination
import dev.reflux.ui.state.HapticEvent
import dev.reflux.ui.state.Haptics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a controller in the hand feels while browsing (F-002; owner's decision 2026-11-19: on by default, subtle): a light
 * tick where focus moves, a confirmation when Select takes effect, a firmer tick at the end of a row. A keyboard pressing
 * the same keys feels nothing, and rumble off is nothing at all.
 */
@OptIn(ExperimentalTestApi::class)
class ControllerFeelTest {
    private val tick = Haptics.PATTERNS.getValue(HapticEvent.NAVIGATE)
    private val confirm = Haptics.PATTERNS.getValue(HapticEvent.CONFIRM)

    @Test
    fun aControllerFeelsMovesAndChoicesLightly() {
        val pad = RecordingRumble()
        screen(populated(), FakePlatform().apply { rumble = pad }) { app ->
            awaitText("Recently Added")
            runOnIdle { app.onGamepadButton(GamepadButton.DPAD_DOWN, ControllerFamily.XBOX) }
            waitForIdle()
            assertEquals(listOf(tick), pad.pulses, "a move is a light tick")
            // Into the rail and down to Movies, then Select.
            runOnIdle { app.onGamepadButton(GamepadButton.DPAD_LEFT, ControllerFamily.XBOX) }
            waitForIdle()
            pad.pulses.clear()
            Thread.sleep(Haptics.MIN_INTERVAL_MS.getValue(HapticEvent.NAVIGATE) + 50)
            runOnIdle { app.onGamepadButton(GamepadButton.DPAD_DOWN, ControllerFamily.XBOX) }
            waitForIdle()
            runOnIdle { app.onGamepadButton(GamepadButton.FACE_SOUTH, ControllerFamily.XBOX) }
            await("movies") { app.navigator.current == Destination.Movies }
            assertTrue(confirm in pad.pulses, "Select that took effect is a confirmation: ${pad.pulses}")
            pad.pulses.forEach { (strength, duration) -> assertTrue(strength <= 0.35f && duration <= 45L, "subtle: $strength for $duration ms") }
        }
    }

    @Test
    fun aKeyboardFeelsNothingAndRumbleOffIsNothing() {
        val pad = RecordingRumble()
        screen(populated(), FakePlatform().apply { rumble = pad }) { app ->
            awaitText("Recently Added")
            onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            waitForIdle()
            onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            waitForIdle()
            assertTrue(pad.pulses.isEmpty(), "keys from a keyboard are not felt on a controller: ${pad.pulses}")
            runOnIdle { app.setControllerRumble(false) }
            waitForIdle()
            runOnIdle { app.onGamepadButton(GamepadButton.DPAD_DOWN, ControllerFamily.XBOX) }
            runOnIdle { app.onGamepadButton(GamepadButton.FACE_SOUTH, ControllerFamily.XBOX) }
            waitForIdle()
            assertTrue(pad.pulses.isEmpty(), "rumble off: nothing at all: ${pad.pulses}")
        }
    }
}
