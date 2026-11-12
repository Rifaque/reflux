package dev.reflux.android.launcher

import dev.reflux.core.model.MediaId
import dev.reflux.core.model.ProfileId
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * The link a Watch Next program opens Reflux with: the work it continues, and the profile whose Continue Watching it
 * was (docs/WATCH_STATE.md, "Profiles"), so that it opens in that profile without asking who is watching. Links
 * written before profiles name only the work; they open in the profile that is active.
 */
object WatchNextLink {
    private const val LEGACY = "reflux://play/"
    private const val PREFIX = "reflux://watch/"

    /** Where a link leads. */
    data class Target(val itemId: MediaId, val profile: ProfileId?)

    fun of(itemId: MediaId, profile: ProfileId? = null): String =
        if (profile == null) LEGACY + encode(itemId.value) else PREFIX + encode(profile.value) + "/" + encode(itemId.value)

    /** Where a link leads, or null for anything that is not one of these links. */
    fun target(link: String?): Target? {
        if (link == null) return null
        if (link.startsWith(LEGACY)) return part(link.removePrefix(LEGACY))?.let { Target(MediaId(it), null) }
        if (!link.startsWith(PREFIX)) return null
        val rest = link.removePrefix(PREFIX).split('/')
        if (rest.size != 2) return null
        val profile = part(rest[0]) ?: return null
        val item = part(rest[1]) ?: return null
        return Target(MediaId(item), ProfileId(profile))
    }

    /** The work a link names, or null for anything that is not one of these links. */
    fun itemId(link: String?): MediaId? = target(link)?.itemId

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun part(encoded: String): String? {
        if (encoded.isEmpty() || '/' in encoded || '?' in encoded) return null
        return runCatching { URLDecoder.decode(encoded, Charsets.UTF_8.name()) }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
