package dev.reflux.desktop

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import kotlinx.coroutines.delay
import java.awt.Frame
import java.awt.Rectangle

/**
 * Switches the main window in and out of fullscreen without recreating it.
 *
 * The window's native handle must survive the switch, because the playing engine renders into a child of
 * it. On Windows the toolkit's own fullscreen keeps the title bar on a decorated window, so the frame is
 * removed and the window is stretched over its monitor instead (borderless fullscreen). Elsewhere the
 * toolkit's fullscreen is a real one and is used as is.
 */
internal class Fullscreen(private val window: ComposeWindow, private val state: WindowState) {
    private val borderless = if (System.getProperty("os.name").startsWith("Windows")) runCatching { Borderless(window) }.getOrNull() else null
    private var windowed = WindowPlacement.Floating
    private var bounds = Rectangle()
    private var active = false

    suspend fun set(fullscreen: Boolean) {
        if (fullscreen == active) return
        active = fullscreen
        if (borderless != null) {
            if (fullscreen) borderless.enter() else borderless.leave()
        } else if (fullscreen) {
            windowed = state.placement
            bounds = window.bounds
            state.placement = WindowPlacement.Fullscreen
        } else {
            state.placement = windowed
            // A window manager puts the frame back on its own schedule, and not always where it was
            // (X11 managers tend to drop it in a corner), so its place is restored once it has settled.
            if (windowed == WindowPlacement.Floating) {
                repeat(RESTORE_CHECKS) {
                    delay(RESTORE_CHECK_MS)
                    if (active) return
                    if (window.bounds != bounds) window.bounds = bounds
                }
            }
        }
    }

    private companion object {
        const val RESTORE_CHECKS = 6
        const val RESTORE_CHECK_MS = 80L
    }
}

private class Borderless(private val window: ComposeWindow) {
    @Suppress("FunctionName")
    private interface User32 : Library {
        fun GetWindowLongPtrW(window: Pointer, index: Int): Long
        fun SetWindowLongPtrW(window: Pointer, index: Int, value: Long): Long
        fun SetWindowPos(window: Pointer, insertAfter: Pointer?, x: Int, y: Int, width: Int, height: Int, flags: Int): Boolean
        fun MonitorFromWindow(window: Pointer, flags: Int): Pointer?
        fun GetMonitorInfoW(monitor: Pointer, info: Pointer): Boolean
    }

    private val user32 = Native.load("user32", User32::class.java)
    private var style = 0L
    private var bounds = Rectangle()
    private var maximized = false

    fun enter() {
        val handle = Native.getWindowPointer(window)
        maximized = window.extendedState and Frame.MAXIMIZED_BOTH == Frame.MAXIMIZED_BOTH
        if (maximized) window.extendedState = Frame.NORMAL
        bounds = window.bounds
        style = user32.GetWindowLongPtrW(handle, GWL_STYLE)
        val monitor = user32.MonitorFromWindow(handle, MONITOR_DEFAULTTONEAREST) ?: return
        val info = Memory(MONITORINFO_SIZE).apply { setInt(0, MONITORINFO_SIZE.toInt()) }
        if (!user32.GetMonitorInfoW(monitor, info)) return
        // rcMonitor: left, top, right, bottom in physical pixels.
        val left = info.getInt(4)
        val top = info.getInt(8)
        user32.SetWindowLongPtrW(handle, GWL_STYLE, style and (WS_CAPTION or WS_THICKFRAME).inv())
        user32.SetWindowPos(handle, null, left, top, info.getInt(12) - left, info.getInt(16) - top, SWP_NOZORDER or SWP_FRAMECHANGED)
    }

    fun leave() {
        val handle = Native.getWindowPointer(window)
        user32.SetWindowLongPtrW(handle, GWL_STYLE, style)
        user32.SetWindowPos(handle, null, 0, 0, 0, 0, SWP_NOMOVE or SWP_NOSIZE or SWP_NOZORDER or SWP_FRAMECHANGED)
        window.bounds = bounds
        if (maximized) window.extendedState = Frame.MAXIMIZED_BOTH
    }

    private companion object {
        const val GWL_STYLE = -16
        const val WS_CAPTION = 0x00C00000L
        const val WS_THICKFRAME = 0x00040000L
        const val SWP_NOSIZE = 0x0001
        const val SWP_NOMOVE = 0x0002
        const val SWP_NOZORDER = 0x0004
        const val SWP_FRAMECHANGED = 0x0020
        const val MONITOR_DEFAULTTONEAREST = 2
        const val MONITORINFO_SIZE = 40L
    }
}
