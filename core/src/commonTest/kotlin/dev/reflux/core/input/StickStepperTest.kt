package dev.reflux.core.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The right stick as scene steps (B-029): a dead zone with hysteresis, one step at once, repeats, acceleration. */
class StickStepperTest {
    /** Holds the stick at [value] from [from] to [until], looked at every 16 ms like the player does; the steps taken. */
    private fun StickStepper.hold(value: Float, from: Long, until: Long): List<Int> =
        (from..until step 16).map { update(value, it) }.filter { it != 0 }

    @Test
    fun drift_andAStickRestingOffCentre_doNothing() {
        val stick = StickStepper()
        assertEquals(emptyList(), stick.hold(0.2f, 0, 2_000))
        assertEquals(emptyList(), stick.hold(-0.45f, 2_000, 4_000))
        assertFalse(stick.engaged)
    }

    @Test
    fun aPushStepsOnceAtOnce_andATapIsExactlyOneScene() {
        val stick = StickStepper()
        assertEquals(1, stick.update(0.8f, 0))
        assertEquals(0, stick.update(0.8f, 100))
        assertEquals(0, stick.update(0f, 150))
        assertFalse(stick.engaged)
        assertEquals(-1, stick.update(-0.9f, 300), "the other way is a new push")
    }

    @Test
    fun theStickStaysEngagedUntilItFallsUnderTheReleasePoint() {
        val stick = StickStepper()
        assertEquals(1, stick.update(0.55f, 0))
        // Wobbling under the engage point but over the release point: still held, no new first step.
        assertEquals(0, stick.update(0.4f, 50))
        assertTrue(stick.engaged)
        assertEquals(0, stick.update(0.25f, 60))
        assertFalse(stick.engaged)
    }

    @Test
    fun holdingRepeats_fasterTheFurtherItIsPushed() {
        val gentle = StickStepper().hold(0.55f, 0, 1_900).size
        val full = StickStepper().hold(1f, 0, 1_900).size
        assertTrue(gentle in 4..8, "a gentle push repeats about every quarter second: $gentle")
        assertTrue(full > gentle * 2, "fully over repeats much faster: $full against $gentle")
    }

    @Test
    fun aLongHoldAccelerates() {
        val stick = StickStepper()
        val steps = stick.hold(1f, 0, 5_000)
        assertEquals(1, steps.first())
        assertTrue(steps.any { it == StickStepper.FAST_STEPS }, "after two seconds, several scenes a step")
        assertEquals(StickStepper.FASTER_STEPS, steps.last(), "after four, more")
    }

    @Test
    fun resetForgetsTheStick() {
        val stick = StickStepper()
        stick.update(1f, 0)
        stick.reset()
        assertFalse(stick.engaged)
        assertEquals(1, stick.update(1f, 10), "the next look at a held stick is a new push")
    }
}
