package dev.reflux.desktop

import dev.reflux.desktop.engine.ObservedCapabilities
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.beans.PropertyChangeListener
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Keeps [capabilities] told which display [window] is on: when the window has been moved (once it settles),
 * when the toolkit puts it on another screen, and when it becomes the active window again, which is when a
 * resolution or HDR change made in the system's settings shows. Each reading happens on a thread of its own,
 * never the interface's, and a reading that finds nothing new changes nothing.
 */
internal class DisplayWatcher(private val window: Window, private val capabilities: ObservedCapabilities) : AutoCloseable {
    private val executor = Executors.newSingleThreadScheduledExecutor { Thread(it, "reflux-display").apply { isDaemon = true } }
    private var pending: ScheduledFuture<*>? = null

    private val moved = object : ComponentAdapter() {
        override fun componentMoved(e: ComponentEvent) = schedule(SETTLE_MS)
        override fun componentResized(e: ComponentEvent) = schedule(SETTLE_MS)
    }
    private val screen = PropertyChangeListener { schedule(0) }
    private val activated = object : WindowAdapter() {
        override fun windowActivated(e: WindowEvent) = schedule(0)
    }

    init {
        window.addComponentListener(moved)
        window.addPropertyChangeListener(GRAPHICS_CONFIGURATION, screen)
        window.addWindowListener(activated)
        schedule(0)
    }

    @Synchronized
    private fun schedule(delayMs: Long) {
        if (executor.isShutdown) return
        pending?.cancel(false)
        pending = executor.schedule({ runCatching { capabilities.observeDisplay(window) } }, delayMs, TimeUnit.MILLISECONDS)
    }

    @Synchronized
    override fun close() {
        window.removeComponentListener(moved)
        window.removePropertyChangeListener(GRAPHICS_CONFIGURATION, screen)
        window.removeWindowListener(activated)
        executor.shutdownNow()
    }

    private companion object {
        /** A window being dragged moves many times a second; it is read once it has stopped. */
        const val SETTLE_MS = 250L

        /** The property the toolkit changes when a window goes to another screen. */
        const val GRAPHICS_CONFIGURATION = "graphicsConfiguration"
    }
}
