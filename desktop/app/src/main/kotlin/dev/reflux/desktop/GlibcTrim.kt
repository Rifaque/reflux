package dev.reflux.desktop

import com.sun.jna.Library
import com.sun.jna.Native

/**
 * glibc's `malloc_trim(0)`: gives the memory its allocator holds free back to the system, from every arena. Linux with glibc
 * only; elsewhere (or with another C library) it does nothing. Used after the Linux integrated player's engine closes (B-084).
 */
internal object GlibcTrim {
    @Suppress("FunctionName")
    private interface C : Library {
        fun malloc_trim(pad: Long): Int
    }

    private val c: C? by lazy {
        if (!System.getProperty("os.name").startsWith("Linux")) null
        else runCatching { Native.load("c", C::class.java) }.getOrNull()?.takeIf { runCatching { it.malloc_trim(0) }.isSuccess }
    }

    /** True when memory was given back. */
    fun trim(): Boolean = runCatching { (c?.malloc_trim(0) ?: 0) != 0 }.getOrDefault(false)
}
