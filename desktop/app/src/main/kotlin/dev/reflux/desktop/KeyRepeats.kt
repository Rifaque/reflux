package dev.reflux.desktop

import dev.reflux.core.input.Key

/**
 * Tells a key being held from a key being pressed again, and says when a held key is let go.
 *
 * AWT sends a held key as presses with no release between them on Windows, and as a release and a press a moment apart on
 * X11 (its auto-repeat is a release and a press with the same timestamp). So a release is not believed at once: it is
 * acted on after [RELEASE_GRACE_MS], unless the same key is pressed again first, which makes that press a repeat.
 *
 * @param schedule runs an action after a delay and returns what cancels it; the toolkit's timer in the shell, a
 * manual clock in tests.
 * @param onReleased called when a key was really let go.
 */
internal class KeyRepeats(
    private val schedule: (delayMs: Long, action: () -> Unit) -> () -> Unit,
    private val onReleased: (Key) -> Unit,
) {
    private val down = HashSet<Key>()
    private val pending = HashMap<Key, () -> Unit>()

    /** A press of [key]. True when it is the held key repeating, false when it is a new press. */
    fun pressed(key: Key): Boolean {
        pending.remove(key)?.invoke()
        return !down.add(key)
    }

    /** A release of [key]: acted on after the grace, unless it is pressed again first. */
    fun released(key: Key) {
        if (key !in down) return
        pending.remove(key)?.invoke()
        pending[key] = schedule(RELEASE_GRACE_MS) {
            pending.remove(key)
            down.remove(key)
            onReleased(key)
        }
    }

    companion object {
        /** Long enough to span an X11 auto-repeat's release and press; short enough that a let-go key is acted on at once to the eye. */
        const val RELEASE_GRACE_MS = 40L
    }
}
