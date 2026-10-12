package dev.reflux.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.awt.Toolkit
import kotlin.concurrent.thread

/**
 * Keeps the toolkit's X11 error handler in place while libmpv is in the process.
 *
 * Xlib has one error handler per process. The toolkit installs one that tolerates errors, because harmless
 * ones are routine (a request about a window that was just destroyed). libmpv installs its own for every
 * video output it tries and, when an output goes away, resets the handler to Xlib's default, which *ends
 * the process* on any error. Left alone, Reflux would quit at some random moment after the player closed.
 *
 * The engine cannot be told not to, so the guard remembers the toolkit's handler before any engine exists
 * and keeps putting it back while one is alive. Measured, the resets happen in two short phases: while the
 * engine starts (once per output it tries before one works) and when it is destroyed. During those the
 * guard watches without pause, which leaves each reset in effect for well under a microsecond; in between
 * it only looks every few milliseconds. The shell also keeps its own windows from being torn down in those
 * phases (see `Main`), so that the toolkit's routine errors do not arrive then.
 */
internal class X11ErrorGuard private constructor(private val x11: X11, private val toolkitHandler: Pointer) {
    @Suppress("FunctionName")
    private interface X11 : Library {
        fun XSetErrorHandler(handler: Pointer?): Pointer?
    }

    @Volatile private var watching = false

    /** Until this time (`System.nanoTime`) the watcher does not sleep between looks. */
    @Volatile private var closelyUntil = 0L

    /** Puts the toolkit's handler back now. */
    fun restore() {
        x11.XSetErrorHandler(toolkitHandler)
    }

    /** Starts guarding for an engine that is about to be created; ends with [release]. */
    @Synchronized
    fun hold() {
        closelyUntil = System.nanoTime() + STARTUP_NS
        if (watching) return
        watching = true
        thread(name = "reflux-x11-errors", isDaemon = true) {
            while (watching) pause()
            // The engine's last act is a reset; outlast it.
            val lingerUntil = System.nanoTime() + LINGER_NS
            while (!watching && System.nanoTime() < lingerUntil) pause()
        }
    }

    /** Runs [teardown], which destroys the engine, under continuous watch. */
    fun <T> closely(teardown: () -> T): T {
        closelyUntil = Long.MAX_VALUE
        try {
            return teardown()
        } finally {
            restore()
            closelyUntil = System.nanoTime() + SETTLE_NS
        }
    }

    /** Call after the engine is gone. */
    fun release() {
        restore()
        watching = false
    }

    private fun pause() {
        restore()
        if (System.nanoTime() < closelyUntil) Thread.onSpinWait() else Thread.sleep(WATCH_MS)
    }

    companion object {
        private const val WATCH_MS = 2L

        /** How long an engine may take to settle on a video output. */
        private const val STARTUP_NS = 1_500_000_000L
        private const val SETTLE_NS = 100_000_000L
        private const val LINGER_NS = 2_000_000_000L

        /** Whether the toolkit is talking to an X server, which is when the guard is needed. */
        val needed: Boolean get() = Toolkit.getDefaultToolkit().javaClass.name == "sun.awt.X11.XToolkit"

        /**
         * The guard for this process: null when the toolkit is not on X11, or when Xlib could not be reached
         * (which leaves playback working but exposed to the problem described above).
         */
        fun create(): X11ErrorGuard? {
            if (!needed) return null
            // Installed systems carry the versioned library; the unversioned name only exists with development files.
            val x11 = listOf("libX11.so.6", "X11").firstNotNullOfOrNull { name -> runCatching { Native.load(name, X11::class.java) }.getOrNull() } ?: return null
            // Setting a handler is the only way to learn the current one; it is put straight back.
            val current = x11.XSetErrorHandler(null) ?: return null
            x11.XSetErrorHandler(current)
            return X11ErrorGuard(x11, current)
        }
    }
}
