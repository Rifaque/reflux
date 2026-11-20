package dev.reflux.core.input

/**
 * Which presentation the Reflux keyboard shows while it is open (F-P4, planning/F-P4-ADAPTIVE-KEYBOARD.md §7), with
 * hysteresis so that incidental input never rearranges the keys under someone:
 * - an explicit act switches at once: a controller button or the D-pad ([controllerPressed]); a click on the keyboard
 *   ([clicked]);
 * - pointer travel switches to the pointer only after [TRAVEL_DP] of it within [WINDOW_MS], over the keyboard;
 * - for [HOLD_MS] after a switch, motion alone toward the other presentation (pointer travel, a stick under
 *   [STRONG_STICK]) is ignored;
 * - a physical keyboard changes nothing (owner's decision D5), and neither does a wheel.
 * Pure: the caller supplies time, so every rule is tested without a screen.
 */
class KeyboardModeArbiter(initial: Mode, private var switchedAtMs: Long = Long.MIN_VALUE / 2) {
    enum class Mode { CONTROLLER, POINTER }

    var mode: Mode = initial
        private set

    private val travel = ArrayDeque<Pair<Long, Float>>()

    private fun switchTo(next: Mode, nowMs: Long): Boolean {
        if (next == mode) return false
        mode = next
        switchedAtMs = nowMs
        travel.clear()
        return true
    }

    private fun settled(nowMs: Long) = nowMs - switchedAtMs >= HOLD_MS

    /** A controller's or a remote's button or direction. Returns whether the presentation changed. */
    fun controllerPressed(nowMs: Long): Boolean = switchTo(Mode.CONTROLLER, nowMs)

    /** A stick moved to [magnitude] (0 to 1): a strong push is explicit; a gentle one counts only once settled. */
    fun stick(magnitude: Float, nowMs: Long): Boolean = when {
        magnitude >= STRONG_STICK -> switchTo(Mode.CONTROLLER, nowMs)
        magnitude >= InputModalityTracker.STICK_THRESHOLD && settled(nowMs) -> switchTo(Mode.CONTROLLER, nowMs)
        else -> false
    }

    /** A click on the keyboard: the key under it is pressed as drawn, then the pointer's presentation is shown. */
    fun clicked(nowMs: Long): Boolean = switchTo(Mode.POINTER, nowMs)

    /**
     * The pointer became the input in use (the app's own modality: deliberate travel anywhere, or a click). It switches once
     * settled after the last switch, so a controller's press and an incidental nudge never fight.
     */
    fun pointerTookOver(nowMs: Long): Boolean = if (settled(nowMs)) switchTo(Mode.POINTER, nowMs) else false

    /** The pointer moved by [dp] (its travel), [overKeyboard] or elsewhere. */
    fun pointerMoved(dp: Float, overKeyboard: Boolean, nowMs: Long): Boolean {
        if (mode == Mode.POINTER) return false
        travel.addLast(nowMs to dp)
        while (travel.isNotEmpty() && nowMs - travel.first().first > WINDOW_MS) travel.removeFirst()
        if (!overKeyboard || !settled(nowMs)) return false
        return if (travel.sumOf { it.second.toDouble() } >= TRAVEL_DP) switchTo(Mode.POINTER, nowMs) else false
    }

    companion object {
        const val TRAVEL_DP = 48f
        const val WINDOW_MS = 400L
        const val HOLD_MS = 500L
        const val STRONG_STICK = 0.8f
    }
}
