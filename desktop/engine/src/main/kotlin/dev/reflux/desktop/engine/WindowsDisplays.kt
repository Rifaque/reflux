package dev.reflux.desktop.engine

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference

/**
 * What Windows says about the monitor a window is on: its current mode in pixels, and whether it is showing
 * HDR now. Read through `user32` (`MonitorFromWindow`, `GetMonitorInfoW`, `EnumDisplaySettingsW`) and the
 * display configuration API (`QueryDisplayConfig`, `DisplayConfigGetDeviceInfo`), which need no COM and no
 * graphics device. Every call is answered from the system's own state in well under a millisecond.
 *
 * Nothing here is cached: a mode or HDR switch in the system's settings shows at the next read.
 */
internal class WindowsDisplays {
    @Suppress("FunctionName")
    private interface User32 : Library {
        fun GetDesktopWindow(): Pointer
        fun MonitorFromWindow(window: Pointer, flags: Int): Pointer?
        fun GetMonitorInfoW(monitor: Pointer, info: Pointer): Boolean
        fun EnumDisplaySettingsW(device: WString, mode: Int, devMode: Pointer): Boolean
        fun GetDisplayConfigBufferSizes(flags: Int, paths: IntByReference, modes: IntByReference): Int
        fun QueryDisplayConfig(flags: Int, paths: IntByReference, pathInfo: Pointer, modes: IntByReference, modeInfo: Pointer, topology: Pointer?): Int
        fun DisplayConfigGetDeviceInfo(packet: Pointer): Int
    }

    private val user32: User32 by lazy { Native.load("user32", User32::class.java) }

    /** What one monitor reports. [hdr] is null when the system would not say. */
    data class Monitor(val device: String, val width: Int, val height: Int, val hdr: HdrState?)

    /** The primary monitor, for when there is no window yet. */
    fun primary(): Monitor? = monitorOf(user32.GetDesktopWindow())

    /** The monitor [window] (a native window handle) is mostly on, or null when Windows does not say. */
    fun monitorOf(window: Pointer): Monitor? {
        val monitor = user32.MonitorFromWindow(window, MONITOR_DEFAULTTONEAREST) ?: return null
        val info = Memory(MONITORINFOEX_SIZE.toLong()).apply { clear(); setInt(0, MONITORINFOEX_SIZE) }
        if (!user32.GetMonitorInfoW(monitor, info)) return null
        val device = info.getWideString(MONITORINFOEX_DEVICE)
        val mode = Memory(DEVMODE_SIZE.toLong()).apply { clear(); setShort(DEVMODE_DM_SIZE, DEVMODE_SIZE.toShort()) }
        if (!user32.EnumDisplaySettingsW(WString(device), ENUM_CURRENT_SETTINGS, mode)) return null
        return Monitor(device, mode.getInt(DEVMODE_PELS_WIDTH), mode.getInt(DEVMODE_PELS_HEIGHT), hdrOf(device))
    }

    /** The HDR state of the display whose GDI name is [device] ("\\.\DISPLAY1"). */
    private fun hdrOf(device: String): HdrState? {
        val paths = IntByReference()
        val modes = IntByReference()
        if (user32.GetDisplayConfigBufferSizes(QDC_ONLY_ACTIVE_PATHS, paths, modes) != 0 || paths.value == 0) return null
        val pathInfo = Memory(paths.value.toLong() * PATH_SIZE)
        val modeInfo = Memory(modes.value.coerceAtLeast(1).toLong() * MODE_SIZE)
        if (user32.QueryDisplayConfig(QDC_ONLY_ACTIVE_PATHS, paths, pathInfo, modes, modeInfo, null) != 0) return null
        for (index in 0 until paths.value) {
            val path = pathInfo.share(index.toLong() * PATH_SIZE)
            if (sourceName(path) != device) continue
            return advancedColor2(path) ?: advancedColor(path)
        }
        return null
    }

    private fun sourceName(path: Pointer): String? {
        val packet = header(SOURCE_NAME_SIZE, DEVICE_INFO_GET_SOURCE_NAME, path.getInt(0), path.getInt(4), path.getInt(8))
        if (user32.DisplayConfigGetDeviceInfo(packet) != 0) return null
        return packet.getWideString(HEADER_SIZE.toLong())
    }

    /** Windows 11 24H2 and later: the colour mode the display is in. */
    private fun advancedColor2(path: Pointer): HdrState? {
        val packet = header(ADVANCED_COLOR_2_SIZE, DEVICE_INFO_GET_ADVANCED_COLOR_INFO_2, path.getInt(20), path.getInt(24), path.getInt(28))
        if (user32.DisplayConfigGetDeviceInfo(packet) != 0) return null
        val bits = packet.getInt(HEADER_SIZE.toLong())
        return HdrState.fromColorInfo2(supported = bits and (1 shl 4) != 0, activeColorMode = packet.getInt(HEADER_SIZE + 12L))
    }

    /** Earlier Windows 10 and 11: advanced colour, which is HDR unless only wide colour is forced on. */
    private fun advancedColor(path: Pointer): HdrState? {
        val packet = header(ADVANCED_COLOR_SIZE, DEVICE_INFO_GET_ADVANCED_COLOR_INFO, path.getInt(20), path.getInt(24), path.getInt(28))
        if (user32.DisplayConfigGetDeviceInfo(packet) != 0) return null
        val bits = packet.getInt(HEADER_SIZE.toLong())
        return HdrState.fromColorInfo(
            supported = bits and 1 != 0,
            enabled = bits and 2 != 0,
            wideColorEnforced = bits and 4 != 0,
            forceDisabled = bits and 8 != 0,
        )
    }

    private fun header(size: Int, type: Int, adapterLow: Int, adapterHigh: Int, id: Int) = Memory(size.toLong()).apply {
        clear()
        setInt(0, type)
        setInt(4, size)
        setInt(8, adapterLow)
        setInt(12, adapterHigh)
        setInt(16, id)
    }

    private companion object {
        const val MONITOR_DEFAULTTONEAREST = 2
        const val ENUM_CURRENT_SETTINGS = -1
        const val QDC_ONLY_ACTIVE_PATHS = 2

        /** `MONITORINFOEXW`: size, two rectangles, flags, then the device name. */
        const val MONITORINFOEX_SIZE = 104
        const val MONITORINFOEX_DEVICE = 40L

        /** `DEVMODEW` for displays. */
        const val DEVMODE_SIZE = 220
        const val DEVMODE_DM_SIZE = 68L
        const val DEVMODE_PELS_WIDTH = 172L
        const val DEVMODE_PELS_HEIGHT = 176L

        /** `DISPLAYCONFIG_PATH_INFO` (source at 0: adapter LUID, id; target at 20: adapter LUID, id) and `DISPLAYCONFIG_MODE_INFO`. */
        const val PATH_SIZE = 72
        const val MODE_SIZE = 64

        const val HEADER_SIZE = 20
        const val DEVICE_INFO_GET_SOURCE_NAME = 1
        const val SOURCE_NAME_SIZE = HEADER_SIZE + 64
        const val DEVICE_INFO_GET_ADVANCED_COLOR_INFO = 9
        const val ADVANCED_COLOR_SIZE = HEADER_SIZE + 12
        const val DEVICE_INFO_GET_ADVANCED_COLOR_INFO_2 = 15
        const val ADVANCED_COLOR_2_SIZE = HEADER_SIZE + 16
    }
}

/** Whether a display shows HDR, as its system reports it. */
enum class HdrState {
    /** It is showing HDR now. */
    ON,

    /** It can, and HDR is switched off in the system's settings. */
    OFF,

    /** It cannot. */
    UNSUPPORTED,
    ;

    companion object {
        /** `DISPLAYCONFIG_GET_ADVANCED_COLOR_INFO_2`: `activeColorMode` 2 is HDR (0 SDR, 1 wide colour). */
        fun fromColorInfo2(supported: Boolean, activeColorMode: Int): HdrState = when {
            activeColorMode == 2 -> ON
            supported -> OFF
            else -> UNSUPPORTED
        }

        /**
         * `DISPLAYCONFIG_GET_ADVANCED_COLOR_INFO`: advanced colour is HDR unless it is only wide colour that
         * the system forces on an SDR display (automatic colour management).
         */
        fun fromColorInfo(supported: Boolean, enabled: Boolean, wideColorEnforced: Boolean, forceDisabled: Boolean): HdrState = when {
            !supported || wideColorEnforced -> UNSUPPORTED
            enabled && !forceDisabled -> ON
            else -> OFF
        }
    }
}
