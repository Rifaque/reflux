package dev.reflux.android.launcher

import dev.reflux.core.model.ProfileId

/**
 * Where the launcher cards a viewer removed are remembered: per profile and work, so that one viewer taking a card
 * off the launcher never keeps another viewer's card for the same work off it (docs/WATCH_STATE.md, "Profiles").
 */
object WatchNextDismissals {
    private const val SEPARATOR = "\u001F"

    fun prefix(profile: ProfileId): String = profile.value + SEPARATOR

    /** Whether a stored key names a profile; one from before profiles does not, and is whose nobody can say. */
    fun scoped(key: String): Boolean = SEPARATOR in key

    /** [profile]'s dismissals out of everything stored, by work. */
    fun of(profile: ProfileId, stored: Map<String, *>): Map<String, Long> {
        val mine = prefix(profile)
        return stored.mapNotNull { (key, value) -> if (key.startsWith(mine) && value is Long) key.removePrefix(mine) to value else null }.toMap()
    }
}
