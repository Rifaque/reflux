package dev.reflux.core.playback

/**
 * A seek key held down: it accelerates with how long it has been held, shows where it would land, and seeks once.
 *
 * Every key repeat used to be a seek of its own, ten seconds each, at whatever rate a remote repeats: a held key made a
 * burst of seeks that the engine answered one at a time. Here a press is the ten-second seek it always was, issued at
 * once; what comes after it, while the key stays down, only moves the target, and the one seek that gets there is made
 * when the key is let go ([release]) or has gone quiet ([IDLE_MS], for a platform that tells nothing of a release).
 *
 * Which presses are repeats is the platform's to say (a repeat count, a key already down): separate quick presses are separate
 * seeks, never a hold. The pace is in media seconds per second held, so it does not depend on how often a given remote or keyboard repeats:
 * [SLOW_RATE] for the first second, [MEDIUM_RATE] until three, then [FAST_RATE]. Pure: the caller supplies the clock.
 */
class SeekHold {
    private var direction = 0
    private var held = false
    private var goal = 0L
    private var limit: Long? = null
    private var since = 0L
    private var lastEvent = 0L
    private var carried = 0.0
    private var moved = false

    /** Whether a key is being held. */
    val active: Boolean get() = held

    /** Where the seek would land if the key were let go now; null while no key is held beyond its first press. */
    val target: Long? get() = goal.takeIf { held && moved }

    /** A key goes down: [direction] is -1 or 1. Returns where the ten-second seek goes, at once, as before. Always a fresh hold, from [positionMs]. */
    fun press(direction: Int, positionMs: Long, durationMs: Long?, nowMs: Long): Long {
        require(direction == -1 || direction == 1)
        this.direction = direction
        held = true
        moved = false
        limit = durationMs
        since = nowMs
        lastEvent = nowMs
        carried = 0.0
        goal = clamp(positionMs + direction * STEP_MS)
        return goal
    }

    /** The key repeats: the target moves by the time since the last event, at the pace its hold has reached. Returns the target, or null with no hold. */
    fun repeat(nowMs: Long): Long? {
        if (!held) return null
        // The pace of an interval is the one its start had reached, so a coarse repeat never leaps a tier.
        val heldFor = lastEvent - since
        val rate = when {
            heldFor < FIRST_TIER_MS -> SLOW_RATE
            heldFor < SECOND_TIER_MS -> MEDIUM_RATE
            else -> FAST_RATE
        }
        // Seconds of media per second held is milliseconds of media per millisecond held.
        carried += rate * (nowMs - lastEvent).coerceAtLeast(0)
        lastEvent = nowMs
        val whole = (carried / 1000).toLong() * 1000
        if (whole > 0) {
            carried -= whole
            goal = clamp(goal + direction * whole)
            moved = true
        }
        return goal.takeIf { moved }
    }

    /** The key is let go. Returns the one seek still to make, or null when the hold never went past its first press. */
    fun release(): Long? {
        val pending = target
        held = false
        moved = false
        carried = 0.0
        return pending
    }

    private fun clamp(ms: Long): Long = ms.coerceIn(0L, limit ?: Long.MAX_VALUE)

    companion object {
        const val STEP_MS = 10_000L
        const val FIRST_TIER_MS = 1_000L
        const val SECOND_TIER_MS = 3_000L
        const val IDLE_MS = 400L
        const val SLOW_RATE = 10.0
        const val MEDIUM_RATE = 30.0
        const val FAST_RATE = 60.0
    }
}
