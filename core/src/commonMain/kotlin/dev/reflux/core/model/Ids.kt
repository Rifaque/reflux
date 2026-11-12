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
 * A local viewer of this installation (docs/WATCH_STATE.md, "Profiles"): whose watch history, favorites,
 * collections, pinned versions, preferences, and server accounts something is. Never shared between devices.
 */
@JvmInline
value class ProfileId(val value: String) {
    override fun toString(): String = value

    companion object {
        /** The profile every installation starts with, and the one an earlier library's state became. */
        val DEFAULT = ProfileId("default")
    }
}

/**
 * Deterministic identifiers.
 *
 * IDs are derived from stable keys so that rebuilding the index from the same sources yields the same IDs.
 * The hash is FNV-1a 64-bit: not cryptographic, but stable across platforms and runtimes.
 */
object StableIds {
    fun mediaId(identityKey: String): MediaId = MediaId("m" + fnv1a64(identityKey))

    /** A source ID from the adapter type and its identifying configuration (e.g. a folder path). */
    fun sourceId(type: String, identity: String): SourceId = SourceId(type + "-" + fnv1a64(identity))

    fun versionId(location: MediaLocation): VersionId =
        VersionId("v" + fnv1a64(location.sourceId.value + "\u0000" + location.path))

    /** A stable 64-bit hex digest of [input], for deriving opaque IDs. */
    fun hash(input: String): String = fnv1a64(input)

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
