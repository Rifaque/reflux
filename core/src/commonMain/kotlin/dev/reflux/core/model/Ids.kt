package dev.reflux.core.model

import kotlin.jvm.JvmInline

/** Reflux-owned identity of a work: a movie, show, season, or episode. Independent of any file or source. */
@JvmInline
value class MediaId(val value: String) {
    override fun toString(): String = value
}

/** Identity of one playable version of a work at one location. */
@JvmInline
value class VersionId(val value: String) {
    override fun toString(): String = value
}

/** Identity of a configured media source (a local folder, a Jellyfin server, ...). */
@JvmInline
value class SourceId(val value: String) {
    override fun toString(): String = value
}

/**
 * Deterministic identifiers.
 *
 * IDs are derived from stable keys so that rebuilding the index from the same sources yields the same IDs.
 * The hash is FNV-1a 64-bit: not cryptographic, but stable across platforms and runtimes.
 */
object StableIds {
    fun mediaId(identityKey: String): MediaId = MediaId("m" + fnv1a64(identityKey))

    fun versionId(location: MediaLocation): VersionId =
        VersionId("v" + fnv1a64(location.sourceId.value + "\u0000" + location.path))

    internal fun fnv1a64(input: String): String {
        var hash = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
        val prime = 0x100000001b3L
        for (byte in input.encodeToByteArray()) {
            hash = hash xor (byte.toLong() and 0xff)
            hash *= prime
        }
        return hash.toULong().toString(16).padStart(16, '0')
    }
}
