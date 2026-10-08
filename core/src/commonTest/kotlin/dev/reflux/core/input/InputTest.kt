package dev.reflux.core.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InputMappingTest {
    @Test
    fun confirmFollowsFamilyConvention() {
        assertEquals(SemanticAction.SELECT, InputMapping.map(GamepadButton.FACE_SOUTH, ControllerFamily.XBOX, InputContext.BROWSE))
        assertEquals(SemanticAction.SELECT, InputMapping.map(GamepadButton.FACE_SOUTH, ControllerFamily.PLAYSTATION, InputContext.BROWSE))
        assertEquals(SemanticAction.SELECT, InputMapping.map(GamepadButton.FACE_EAST, ControllerFamily.NINTENDO, InputContext.BROWSE))
        assertEquals(SemanticAction.BACK, InputMapping.map(GamepadButton.FACE_EAST, ControllerFamily.PLAYSTATION, InputContext.BROWSE))
        assertEquals(SemanticAction.BACK, InputMapping.map(GamepadButton.FACE_SOUTH, ControllerFamily.NINTENDO, InputContext.BROWSE))
    }

    @Test
    fun contextChangesMeaning() {
        assertEquals(SemanticAction.NAVIGATE_RIGHT, InputMapping.map(GamepadButton.DPAD_RIGHT, ControllerFamily.XBOX, InputContext.BROWSE))
        assertEquals(SemanticAction.SEEK_FORWARD, InputMapping.map(GamepadButton.DPAD_RIGHT, ControllerFamily.XBOX, InputContext.PLAYBACK))
        assertEquals(SemanticAction.PLAY_PAUSE, InputMapping.map(GamepadButton.FACE_SOUTH, ControllerFamily.XBOX, InputContext.PLAYBACK))
        assertEquals(SemanticAction.PLAY_PAUSE, InputMapping.map(Key.SPACE, InputContext.PLAYBACK))
        assertEquals(SemanticAction.SELECT, InputMapping.map(Key.DPAD_CENTER, InputContext.BROWSE))
        assertNull(InputMapping.map(Key.J, InputContext.BROWSE))
    }

    @Test
    fun glyphsUseFamilyLabels() {
        assertEquals("Cross", InputMapping.glyph(GamepadButton.FACE_SOUTH, ControllerFamily.PLAYSTATION))
        assertEquals("A", InputMapping.glyph(GamepadButton.FACE_SOUTH, ControllerFamily.XBOX))
        assertEquals("B", InputMapping.glyph(GamepadButton.FACE_SOUTH, ControllerFamily.NINTENDO))
        assertEquals("Circle", InputMapping.glyph(InputMapping.cancelButton(ControllerFamily.PLAYSTATION), ControllerFamily.PLAYSTATION))
    }

    @Test
    fun everyActionIsReachableFromKeyboardOrController() {
        val reachable = mutableSetOf<SemanticAction>()
        for (context in InputContext.entries) {
            Key.entries.mapNotNullTo(reachable) { InputMapping.map(it, context) }
            GamepadButton.entries.mapNotNullTo(reachable) { InputMapping.map(it, ControllerFamily.XBOX, context) }
        }
        assertEquals(SemanticAction.entries.toSet(), reachable)
    }
}

class InputModalityTrackerTest {
    @Test
    fun firstControllerPressMorphsToDirectional() {
        val tracker = InputModalityTracker(InputModality.POINTER)
        val state = tracker.onSignal(InputSignal.GamepadButtonPress(GamepadButton.DPAD_DOWN, ControllerFamily.PLAYSTATION))
        assertEquals(InputState(InputModality.DIRECTIONAL, ControllerFamily.PLAYSTATION), state)
    }

    @Test
    fun stickDriftIsIgnored() {
        val tracker = InputModalityTracker(InputModality.POINTER)
        assertEquals(InputModality.POINTER, tracker.onSignal(InputSignal.GamepadAxis(0.2f, ControllerFamily.XBOX)).modality)
        assertEquals(InputModality.DIRECTIONAL, tracker.onSignal(InputSignal.GamepadAxis(0.8f, ControllerFamily.XBOX)).modality)
    }

    @Test
    fun smallMouseBumpsDoNotLeaveControllerMode() {
        val tracker = InputModalityTracker(InputModality.DIRECTIONAL)
        assertEquals(InputModality.DIRECTIONAL, tracker.onSignal(InputSignal.PointerMove(3f, 4f)).modality)
        assertEquals(InputModality.DIRECTIONAL, tracker.onSignal(InputSignal.GamepadButtonPress(GamepadButton.DPAD_UP, ControllerFamily.XBOX)).modality)
        // Travel restarts after other input.
        assertEquals(InputModality.DIRECTIONAL, tracker.onSignal(InputSignal.PointerMove(10f, 10f)).modality)
        assertEquals(InputModality.POINTER, tracker.onSignal(InputSignal.PointerMove(10f, 0f)).modality)
    }

    @Test
    fun controllerFamilyIsRememberedAcrossModalities() {
        val tracker = InputModalityTracker(InputModality.TOUCH)
        tracker.onSignal(InputSignal.GamepadButtonPress(GamepadButton.FACE_SOUTH, ControllerFamily.NINTENDO))
        val touched = tracker.onSignal(InputSignal.Touch)
        assertEquals(InputState(InputModality.TOUCH, ControllerFamily.NINTENDO), touched)
    }

    @Test
    fun keysOnlySwitchForNavigation() {
        val tracker = InputModalityTracker(InputModality.POINTER)
        assertEquals(InputModality.POINTER, tracker.onSignal(InputSignal.KeyPress(Key.MEDIA_PLAY_PAUSE)).modality)
        assertEquals(InputModality.KEYBOARD, tracker.onSignal(InputSignal.KeyPress(Key.ARROW_DOWN)).modality)
        assertEquals(InputModality.DIRECTIONAL, tracker.onSignal(InputSignal.KeyPress(Key.DPAD_CENTER)).modality)
    }
}
