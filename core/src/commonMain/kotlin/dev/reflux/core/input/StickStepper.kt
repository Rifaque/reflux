package dev.reflux.core.input

import kotlin.math.abs
import kotlin.math.sign

/**
 * Turns one analogue axis into discrete steps (B-029: the right stick moving through scene previews).
 *
 * - **Dead zone with hysteresis:** a deflection of [engage] or more starts stepping, and it continues until the stick
 *   falls under [release], so a worn stick resting off centre, or one wobbling at the threshold, does nothing.
 * - **One step at once,** then repeats after [FIRST_REPEAT_MS], faster the further the stick is pushed
 *   ([SLOW_REPEAT_MS] at [engage] to [FAST_REPEAT_MS] fully over).
 * - **Acceleration while held:** after [ACCELERATE_AFTER_MS] each repeat is [FAST_STEPS] steps, after twice that
 *   [FASTER_STEPS], so a long film is crossed in seconds while a tap still moves exactly one scene.
 * - Reversing the stick is a new push in the other direction; nothing carries over.
 *
 * Pure: the caller supplies the time, so it is tested without a clock or a controller.
 */
class StickStepper(private val engage: Float = ENGAGE, private val release: Float = RELEASE) {
    private var direction = 0
    private var heldSinceMs = 0L
    private var nextAtMs = 0L

    /** Whether the stick is held past its dead zone now. */
    val engaged: Boolean get() = direction != 0

    /** The steps to take now (negative is back) for the axis at [value] (−1 to 1) at [nowMs]; usually 0. */
    fun update(value: Float, nowMs: Long): Int {
        val magnitude = abs(value)
        val pushed = sign(value).toInt()
        if (direction != 0 && (magnitude < release || pushed != direction)) direction = 0
        if (direction == 0) {
            if (magnitude < engage) return 0
            direction = pushed
            heldSinceMs = nowMs
            nextAtMs = nowMs + FIRST_REPEAT_MS
            return direction
        }
        if (nowMs < nextAtMs) return 0
        val reach = ((magnitude - engage) / (1f - engage)).coerceIn(0f, 1f)
        nextAtMs = nowMs + (SLOW_REPEAT_MS - (SLOW_REPEAT_MS - FAST_REPEAT_MS) * reach).toLong()
        val held = nowMs - heldSinceMs
        val steps = when {
            held >= ACCELERATE_AFTER_MS * 2 -> FASTER_STEPS
            held >= ACCELERATE_AFTER_MS -> FAST_STEPS
            else -> 1
        }
        return direction * steps
    }

    /** Forgets the stick (a controller gone, the scrub ended), so the next push starts afresh. */
    fun reset() {
        direction = 0
    }

    companion object {
        const val ENGAGE = 0.5f
        const val RELEASE = 0.3f
        const val FIRST_REPEAT_MS = 400L
        const val SLOW_REPEAT_MS = 260L
        const val FAST_REPEAT_MS = 70L
        const val ACCELERATE_AFTER_MS = 2_000L
        const val FAST_STEPS = 3
        const val FASTER_STEPS = 8
    }
}
