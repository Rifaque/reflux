package dev.reflux.playback.mpv

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure

/**
 * The subset of the libmpv client API Reflux uses (`mpv/client.h`, API 2.x).
 *
 * Only stable, string-based entry points are bound so the binding works across libmpv versions.
 */
@Suppress("FunctionName", "ktlint:standard:function-naming")
internal interface LibMpv : Library {
    fun mpv_client_api_version(): Long
    fun mpv_create(): Pointer?
    fun mpv_initialize(ctx: Pointer): Int
    fun mpv_terminate_destroy(ctx: Pointer)
    fun mpv_set_option_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_set_property_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_get_property_string(ctx: Pointer, name: String): Pointer?
    fun mpv_command(ctx: Pointer, args: Array<String?>): Int
    fun mpv_observe_property(ctx: Pointer, replyUserdata: Long, name: String, format: Int): Int
    fun mpv_wait_event(ctx: Pointer, timeout: Double): Pointer
    fun mpv_wakeup(ctx: Pointer)
    fun mpv_free(data: Pointer)
    fun mpv_error_string(error: Int): String

    companion object {
        /** Loads libmpv, or returns null when it is not installed. */
        fun loadOrNull(): LibMpv? {
            for (name in listOf("mpv", "mpv-2", "libmpv-2", "libmpv.so.2", "libmpv.so.1")) {
                val library = runCatching { Native.load(name, LibMpv::class.java) }.getOrNull()
                if (library != null) return library
            }
            return null
        }

        const val FORMAT_NONE = 0
        const val FORMAT_STRING = 1
        const val FORMAT_FLAG = 3
        const val FORMAT_INT64 = 4
        const val FORMAT_DOUBLE = 5

        const val EVENT_NONE = 0
        const val EVENT_SHUTDOWN = 1
        const val EVENT_START_FILE = 6
        const val EVENT_END_FILE = 7
        const val EVENT_FILE_LOADED = 8
        const val EVENT_VIDEO_RECONFIG = 17
        const val EVENT_PLAYBACK_RESTART = 21
        const val EVENT_PROPERTY_CHANGE = 22

        const val END_FILE_REASON_EOF = 0
        const val END_FILE_REASON_STOP = 2
        const val END_FILE_REASON_QUIT = 3
        const val END_FILE_REASON_ERROR = 4
    }
}

/** `mpv_event` */
@Structure.FieldOrder("eventId", "error", "replyUserdata", "data")
internal class MpvEvent(pointer: Pointer) : Structure(pointer) {
    @JvmField var eventId: Int = 0
    @JvmField var error: Int = 0
    @JvmField var replyUserdata: Long = 0
    @JvmField var data: Pointer? = null

    init {
        read()
    }
}

/** `mpv_event_property` */
@Structure.FieldOrder("name", "format", "data")
internal class MpvEventProperty(pointer: Pointer) : Structure(pointer) {
    @JvmField var name: String? = null
    @JvmField var format: Int = 0
    @JvmField var data: Pointer? = null

    init {
        read()
    }
}

/** `mpv_event_end_file` (leading fields only). */
@Structure.FieldOrder("reason", "error")
internal class MpvEventEndFile(pointer: Pointer) : Structure(pointer) {
    @JvmField var reason: Int = 0
    @JvmField var error: Int = 0

    init {
        read()
    }
}

/** Reads a string property and frees the libmpv allocation. */
internal fun LibMpv.getString(ctx: Pointer, name: String): String? {
    val pointer = mpv_get_property_string(ctx, name) ?: return null
    return try {
        pointer.getString(0, "UTF-8")
    } finally {
        mpv_free(pointer)
    }
}
