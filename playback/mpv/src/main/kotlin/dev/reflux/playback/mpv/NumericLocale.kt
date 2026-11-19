package dev.reflux.playback.mpv

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

/**
 * libmpv's one requirement of the process it lives in (`mpv/client.h`, "Basic environment requirements"): "The
 * LC_NUMERIC locale category must be set to "C"", and `mpv_create()` returns NULL when it is not (B-031). On Linux
 * the JVM adopts the person's locale for every category when it starts, so on nearly every desktop (`en_US.UTF-8` as
 * much as `de_DE.UTF-8`) libmpv refused to start at all ("Non-C locale detected").
 *
 * Only the numeric category is set, and only to "C": how numbers are formatted *in C code*. Language, text encoding,
 * dates, sorting, and everything the JVM formats (Java has its own `Locale`, which this does not touch) stay the
 * person's. It is set once, as early as possible ([prepareProcess], before any window or thread that could read it),
 * and checked again before every engine is made, written only if something set it back: a read, never a switch back
 * and forth around calls. Windows is left alone: its C runtime starts in the "C" locale and libmpv there was never
 * refused.
 */
object NumericLocale {
    @Suppress("FunctionName")
    private interface C : Library {
        fun setlocale(category: Int, locale: String?): String?
    }

    private val linux = System.getProperty("os.name").orEmpty().startsWith("Linux")
    private val libc: C? by lazy { if (linux) runCatching { Native.load("c", C::class.java) }.getOrNull() else null }

    /** What the numeric locale was before Reflux set it to "C", once it has; null when it never needed to. */
    @Volatile
    var adjustedFrom: String? = null
        private set

    /**
     * Makes the numeric locale "C" for libmpv, if this platform needs it. Called first thing by an application that
     * will use libmpv; every engine and probe calls it again. Returns what it is now, or null where it is not managed.
     */
    @Synchronized
    fun prepareProcess(): String? {
        val c = libc ?: return null
        val current = c.setlocale(LC_NUMERIC, null)
        if (!needsChange(current)) return current
        val now = c.setlocale(LC_NUMERIC, "C")
        if (now == "C" && adjustedFrom == null) adjustedFrom = current
        return now
    }

    /** The numeric locale as libmpv will see it, for a failure's explanation; null where it is not managed. */
    fun current(): String? = libc?.setlocale(LC_NUMERIC, null)

    /** Whether [current] is not what libmpv requires. Exactly "C"; an unknown one is left alone rather than guessed at. */
    internal fun needsChange(current: String?): Boolean = current != null && current != "C"

    /** glibc's and musl's `LC_NUMERIC`. */
    private const val LC_NUMERIC = 1
}

/**
 * `mpv_create()`, with the process made fit for it first. Every engine and probe is made through this. Null as
 * `mpv_create` is; [MpvPlayer.lastFailure] says why.
 */
internal fun LibMpv.createHandle(): Pointer? {
    NumericLocale.prepareProcess()
    val handle = mpv_create()
    if (handle == null) {
        val locale = NumericLocale.current()
        MpvPlayer.lastFailure = if (locale != null && NumericLocale.needsChange(locale)) {
            "libmpv refused to start: the process's numeric locale is $locale and could not be set to C"
        } else {
            "libmpv refused to start (mpv_create returned nothing)"
        }
    }
    return handle
}
