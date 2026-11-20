package dev.reflux.core.input

import dev.reflux.core.input.KeyboardModeArbiter.Mode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The keyboard's presentation (F-P4 §7): explicit acts switch at once; motion only when deliberate, and never right after a switch. */
class KeyboardModeArbiterTest {
    @Test
    fun aButtonOrAClickSwitchesAtOnce() {
        val arbiter = KeyboardModeArbiter(Mode.POINTER)
        assertTrue(arbiter.controllerPressed(1_000))
        assertEquals(Mode.CONTROLLER, arbiter.mode)
        assertTrue(arbiter.clicked(1_050), "a click is explicit, even inside the hold")
        assertEquals(Mode.POINTER, arbiter.mode)
        assertFalse(arbiter.clicked(1_060), "already there")
    }

    @Test
    fun pointerTravelSwitchesOnlyAfter48DpWithin400MsOverTheKeyboard() {
        val arbiter = KeyboardModeArbiter(Mode.CONTROLLER)
        // A nudge: 20 dp, then nothing.
        assertFalse(arbiter.pointerMoved(20f, true, 1_000))
        // Slow travel: 30 dp spread over a second never adds up within 400 ms.
        assertFalse(arbiter.pointerMoved(15f, true, 1_500))
        assertFalse(arbiter.pointerMoved(15f, true, 2_000))
        // Travel elsewhere on screen does not count, however far.
        assertFalse(arbiter.pointerMoved(200f, false, 2_100))
        assertEquals(Mode.CONTROLLER, arbiter.mode)
        // Deliberate travel over the keyboard.
        assertFalse(arbiter.pointerMoved(25f, true, 3_000))
        assertTrue(arbiter.pointerMoved(25f, true, 3_100))
        assertEquals(Mode.POINTER, arbiter.mode)
    }

    @Test
    fun motionRightAfterASwitchIsIgnored_explicitActsAreNot() {
        val arbiter = KeyboardModeArbiter(Mode.POINTER)
        arbiter.controllerPressed(1_000)
        assertFalse(arbiter.pointerMoved(100f, true, 1_200), "within 500 ms of the switch, travel does not switch back")
        assertEquals(Mode.CONTROLLER, arbiter.mode)
        assertTrue(arbiter.pointerMoved(60f, true, 1_600), "after it, it does")
        // A gentle stick inside the hold is ignored; a strong one is explicit.
        assertFalse(arbiter.stick(0.6f, 1_700))
        assertEquals(Mode.POINTER, arbiter.mode)
        assertTrue(arbiter.stick(0.9f, 1_750))
        assertEquals(Mode.CONTROLLER, arbiter.mode)
    }

    @Test
    fun flickerSequencesSettleOnTheLastExplicitAct() {
        val arbiter = KeyboardModeArbiter(Mode.CONTROLLER)
        var switches = 0
        for (t in 0 until 40) {
            // A pointer jiggled over the keyboard while the controller is in use: small moves between presses.
            if (arbiter.pointerMoved(5f, true, 1_000L + t * 50)) switches++
            if (t % 4 == 0 && arbiter.controllerPressed(1_000L + t * 50 + 10)) switches++
        }
        assertEquals(0, switches, "jiggling never takes the keyboard from the controller")
        assertEquals(Mode.CONTROLLER, arbiter.mode)
    }
}
