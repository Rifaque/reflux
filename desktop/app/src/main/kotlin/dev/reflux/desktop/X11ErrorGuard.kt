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
 * ones are routine (a request about a window that was just destroyed). libmpv installs its own while it has
 * a video output and, when that output goes away, resets the handler to Xlib's default, which *ends the
 * process* on any error. Left alone, Reflux would quit at some random moment after the player was closed.
 *
 * The guard remembers the toolkit's handler before any engine exists and puts it back: immediately when
 * asked, and continuously while a player is alive, since the engine swaps handlers on its own threads.
 */
internal class X11ErrorGuard private constructor(private val x11: X11, private val toolkitHandler: Pointer) {
    @Suppress("FunctionName")
    private interface X11 : Library {
        fun XSetErrorHandler(handler: Pointer?): Pointer?
    }

    @Volatile private var watching = false

    /** Puts the toolkit's handler back now. */
    fun restore() {
        x11.XSetErrorHandler(toolkitHandler)
    }

    /** Keeps restoring the handler until [release]; call before a playback engine is created. */
    @Synchronized
    fun hold() {
        if (watching) return
        watching = true
        thread(name = "reflux-x11-errors", isDaemon = true) {
            while (watching) {
                restore()
                Thread.sleep(WATCH_MS)
            }
            // The engine's last act is the reset; outlast it.
            repeat(LINGER_CHECKS) {
                restore()
                Thread.sleep(WATCH_MS)
            }
        }
    }

    /** Call after the engine is gone. */
    fun release() {
        restore()
        watching = false
    }

    companion object {
        private const val WATCH_MS = 2L
        private const val LINGER_CHECKS = 1_000

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
