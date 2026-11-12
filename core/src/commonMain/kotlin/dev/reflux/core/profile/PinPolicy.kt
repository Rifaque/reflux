package dev.reflux.core.profile

/**
 * The rules of a profile's optional PIN (docs/WATCH_STATE.md, "Profiles"): four digits that keep one viewer's
 * profile private from another on a shared device. It is a privacy lock, not a credential: ten thousand values
 * cannot resist a determined guesser who can read the device's storage, so its strength is in what is enforced
 * here (how fast guesses may come) and in the key the platform keeps for checking it, never in its length.
 */
object PinPolicy {
    const val LENGTH = 4

    /** Wrong entries allowed before the profile waits. */
    const val FREE_ATTEMPTS = 5

    /** The first wait, doubled by every further wrong entry, up to [MAX_LOCK_MS]. */
    const val FIRST_LOCK_MS: Long = 30_000

    const val MAX_LOCK_MS: Long = 15 * 60_000

    /** Whether [pin] has the form of a PIN: exactly [LENGTH] ASCII digits. */
    fun valid(pin: String): Boolean = pin.length == LENGTH && pin.all { it in '0'..'9' }

    /**
     * How long the profile waits after [failures] wrong entries in a row, or null when it does not: none for the
     * first [FREE_ATTEMPTS], then 30 seconds, 1 minute, 2, 4, 8, and 15 minutes from then on.
     */
    fun lockAfter(failures: Int): Long? {
        if (failures < FREE_ATTEMPTS) return null
        val doublings = (failures - FREE_ATTEMPTS).coerceAtMost(10)
        return (FIRST_LOCK_MS shl doublings).coerceAtMost(MAX_LOCK_MS)
    }

    /** Compares two verifiers in time that does not depend on where they differ. */
    fun same(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var difference = 0
        for (i in a.indices) difference = difference or (a[i].code xor b[i].code)
        return difference == 0
    }
}

/** What came of entering a PIN. */
sealed interface PinCheck {
    /** Right: the profile opens, and the count of wrong entries starts again. */
    data object Accepted : PinCheck

    /** Wrong. [lockedUntilEpochMs] is set when this entry made the profile wait. */
    data class Rejected(val lockedUntilEpochMs: Long?) : PinCheck

    /** The profile is waiting after too many wrong entries; nothing was checked. */
    data class Locked(val untilEpochMs: Long) : PinCheck
}
