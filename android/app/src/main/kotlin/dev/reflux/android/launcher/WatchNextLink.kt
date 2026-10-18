package dev.reflux.android.launcher

import dev.reflux.core.model.MediaId
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * The address a launcher program opens: Reflux, playing one work. It names the work by Reflux's own media
 * identity, so it stays valid whichever copy, source, or version will play.
 */
object WatchNextLink {
    private const val PREFIX = "reflux://play/"

    fun of(itemId: MediaId): String = PREFIX + URLEncoder.encode(itemId.value, Charsets.UTF_8.name())

    /** The work a link names, or null for anything that is not one of these links. */
    fun itemId(link: String?): MediaId? {
        if (link == null || !link.startsWith(PREFIX)) return null
        val encoded = link.removePrefix(PREFIX).takeIf { it.isNotEmpty() && '/' !in it && '?' !in it } ?: return null
        return runCatching { URLDecoder.decode(encoded, Charsets.UTF_8.name()) }.getOrNull()?.takeIf { it.isNotBlank() }?.let(::MediaId)
    }
}
