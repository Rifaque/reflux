package dev.reflux.desktop.gamepad

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton

/**
 * Controllers on Windows, through the two APIs that together cover every pad:
 *
 * - **XInput** for Xbox controllers and everything that presents itself as one.
 * - **The multimedia joystick API** for HID game controllers that are not XInput devices: DualShock 4,
 *   DualSense, Switch Pro, and generic pads, wired or Bluetooth.
 *
 * XInput devices also appear as joysticks, so those are recognized by their device path and read once.
 */
internal class WindowsGamepads : GamepadReader {
    private val xinput = XInput.load()
    private val joysticks = Joysticks.load()
    private val rawInput = runCatching { Native.load("user32", RawInput::class.java) }.getOrNull()

    private val state = Memory(XInput.STATE_SIZE)
    private val info = Memory(Joysticks.INFO_SIZE)
    private val caps = Memory(Joysticks.CAPS_SIZE)

    /** Slots without a controller are only re-checked now and then: asking about an empty slot is slow. */
    private val padProbeAt = LongArray(XInput.SLOTS)
    private var joystickScanAt = 0L
    private var connectedJoysticks = emptyList<Joystick>()

    private class Joystick(val id: Int, val family: ControllerFamily, val hasHat: Boolean, val xMin: Int, val xMax: Int, val yMin: Int, val yMax: Int)

    override fun poll(): List<GamepadState> {
        val now = System.nanoTime() / 1_000_000
        val pads = (0 until XInput.SLOTS).mapNotNull { slot -> readPad(slot, now) }
        if (now >= joystickScanAt) {
            joystickScanAt = now + RESCAN_MS
            connectedJoysticks = scanJoysticks()
        }
        return pads + connectedJoysticks.mapNotNull(::readJoystick)
    }

    private fun readPad(slot: Int, now: Long): GamepadState? {
        val api = xinput ?: return null
        if (now < padProbeAt[slot]) return null
        if (api.XInputGetState(slot, state) != 0) {
            padProbeAt[slot] = now + RESCAN_MS
            return null
        }
        return XInput.decode(slot, state.getShort(4).toInt() and 0xFFFF, state.getByte(6), state.getByte(7), state.getShort(8), state.getShort(10))
    }

    private fun scanJoysticks(): List<Joystick> {
        val api = joysticks ?: return emptyList()
        val xinputDevices = xinputDevices()
        return (0 until api.joyGetNumDevs().coerceAtMost(Joysticks.MAX_DEVICES)).mapNotNull { id ->
            info.clear()
            info.setInt(0, Joysticks.INFO_SIZE.toInt())
            info.setInt(4, Joysticks.RETURN_ALL)
            if (api.joyGetPosEx(id, info) != 0) return@mapNotNull null // not connected
            caps.clear()
            if (api.joyGetDevCapsW(Pointer.createConstant(id.toLong()), caps, Joysticks.CAPS_SIZE.toInt()) != 0) return@mapNotNull null
            val vendor = caps.getShort(0).toInt() and 0xFFFF
            val product = caps.getShort(2).toInt() and 0xFFFF
            if ((vendor to product) in xinputDevices) return@mapNotNull null // read through XInput instead
            // Windows also lists keyboards, mice, and headsets with vendor extensions here, as joysticks with nothing on them.
            if (!Joysticks.isGameController(buttons = caps.getInt(92), axes = caps.getInt(136))) return@mapNotNull null
            val hasHat = caps.getInt(128) and Joysticks.CAPS_HAS_POV != 0
            Joystick(id, ControllerFamilies.of(vendor, caps.getWideString(4)), hasHat, caps.getInt(68), caps.getInt(72), caps.getInt(76), caps.getInt(80))
        }
    }

    private fun readJoystick(joystick: Joystick): GamepadState? {
        val api = joysticks ?: return null
        info.setInt(0, Joysticks.INFO_SIZE.toInt())
        info.setInt(4, Joysticks.RETURN_ALL)
        if (api.joyGetPosEx(joystick.id, info) != 0) {
            joystickScanAt = 0 // unplugged: look again on the next poll
            return null
        }
        return Joysticks.decode(
            joystick.id, joystick.family,
            buttons = info.getInt(32), pov = info.getInt(40).takeIf { joystick.hasHat },
            x = Joysticks.normalize(info.getInt(8), joystick.xMin, joystick.xMax),
            y = Joysticks.normalize(info.getInt(12), joystick.yMin, joystick.yMax),
        )
    }

    /** Vendor and product IDs of connected XInput devices, from the `IG_` marker in their device paths. */
    private fun xinputDevices(): Set<Pair<Int, Int>> {
        val api = rawInput ?: return emptySet()
        return runCatching {
            val count = IntByReference()
            if (api.GetRawInputDeviceList(null, count, RawInput.ENTRY_SIZE) != 0 || count.value == 0) return emptySet()
            val list = Memory(RawInput.ENTRY_SIZE.toLong() * count.value)
            val read = api.GetRawInputDeviceList(list, count, RawInput.ENTRY_SIZE)
            (0 until read.coerceAtLeast(0)).mapNotNull { index ->
                val handle = list.getPointer(index.toLong() * RawInput.ENTRY_SIZE) ?: return@mapNotNull null
                val length = IntByReference()
                api.GetRawInputDeviceInfoW(handle, RawInput.DEVICE_NAME, null, length)
                if (length.value <= 0) return@mapNotNull null
                val name = Memory((length.value + 1) * 2L)
                if (api.GetRawInputDeviceInfoW(handle, RawInput.DEVICE_NAME, name, length) <= 0) return@mapNotNull null
                Joysticks.xinputIds(name.getWideString(0))
            }.toSet()
        }.getOrDefault(emptySet())
    }

    private companion object {
        const val RESCAN_MS = 2_000L
    }
}

@Suppress("FunctionName")
internal interface XInput : Library {
    fun XInputGetState(index: Int, state: Pointer): Int

    companion object {
        const val SLOTS = 4
        const val STATE_SIZE = 16L
        private const val TRIGGER_PRESSED = 96 // of 255: clearly pulled, not resting

        fun load(): XInput? = listOf("xinput1_4", "xinput1_3", "xinput9_1_0").firstNotNullOfOrNull { name ->
            runCatching { Native.load(name, XInput::class.java) }.getOrNull()
        }

        private val bits = listOf(
            0x0001 to GamepadButton.DPAD_UP, 0x0002 to GamepadButton.DPAD_DOWN, 0x0004 to GamepadButton.DPAD_LEFT, 0x0008 to GamepadButton.DPAD_RIGHT,
            0x0010 to GamepadButton.START, 0x0020 to GamepadButton.SELECT, 0x0040 to GamepadButton.STICK_LEFT, 0x0080 to GamepadButton.STICK_RIGHT,
            0x0100 to GamepadButton.SHOULDER_LEFT, 0x0200 to GamepadButton.SHOULDER_RIGHT,
            0x1000 to GamepadButton.FACE_SOUTH, 0x2000 to GamepadButton.FACE_EAST, 0x4000 to GamepadButton.FACE_WEST, 0x8000 to GamepadButton.FACE_NORTH,
        )

        /** `XINPUT_GAMEPAD` as a [GamepadState]. XInput's Y axis points up; Reflux's points down. */
        fun decode(slot: Int, buttons: Int, leftTrigger: Byte, rightTrigger: Byte, thumbX: Short, thumbY: Short): GamepadState {
            val pressed = bits.filter { buttons and it.first != 0 }.mapTo(mutableSetOf()) { it.second }
            if ((leftTrigger.toInt() and 0xFF) >= TRIGGER_PRESSED) pressed += GamepadButton.TRIGGER_LEFT
            if ((rightTrigger.toInt() and 0xFF) >= TRIGGER_PRESSED) pressed += GamepadButton.TRIGGER_RIGHT
            return GamepadState("xinput:$slot", ControllerFamily.XBOX, pressed, thumbX / 32767f, -(thumbY / 32767f))
        }
    }
}

/** `winmm`'s joystick functions: every HID game controller, polled, with buttons as a bit mask. */
@Suppress("FunctionName")
internal interface Joysticks : Library {
    fun joyGetNumDevs(): Int
    fun joyGetPosEx(id: Int, info: Pointer): Int
    fun joyGetDevCapsW(id: Pointer, caps: Pointer, size: Int): Int

    companion object {
        const val MAX_DEVICES = 16
        const val INFO_SIZE = 52L // JOYINFOEX
        const val CAPS_SIZE = 728L // JOYCAPSW
        const val RETURN_ALL = 0xFF
        const val CAPS_HAS_POV = 0x0010
        private const val POV_CENTERED = 0xFFFF

        fun load(): Joysticks? = runCatching { Native.load("winmm", Joysticks::class.java) }.getOrNull()

        fun normalize(value: Int, min: Int, max: Int): Float =
            if (max <= min) 0f else ((value.toLong() and 0xFFFFFFFFL) - min).toFloat() / (max - min) * 2f - 1f

        /** Whether a joystick device is something to navigate with: face buttons and at least one stick. */
        fun isGameController(buttons: Int, axes: Int): Boolean = buttons >= 4 && axes >= 2

        /**
         * `JOYINFOEX` as a [GamepadState]. The hat reports its angle in hundredths of a degree, clockwise from
         * up; [pov] is null for a device without a hat, whose reading of 0 would otherwise mean "up".
         */
        fun decode(id: Int, family: ControllerFamily, buttons: Int, pov: Int?, x: Float, y: Float): GamepadState {
            val pressed = NumberedButtons.fromMask(buttons, family).toMutableSet()
            val angle = pov?.and(0xFFFF) ?: POV_CENTERED
            if (angle != POV_CENTERED) {
                if (angle >= 29250 || angle <= 6750) pressed += GamepadButton.DPAD_UP
                if (angle in 2250..15750) pressed += GamepadButton.DPAD_RIGHT
                if (angle in 11250..24750) pressed += GamepadButton.DPAD_DOWN
                if (angle in 20250..33750) pressed += GamepadButton.DPAD_LEFT
            }
            return GamepadState("joystick:$id", family, pressed, x, y)
        }

        private val usbIds = Regex("""VID_([0-9A-F]{4})&PID_([0-9A-F]{4})""", RegexOption.IGNORE_CASE)
        private val bluetoothIds = Regex("""VID&[0-9A-F]{2}([0-9A-F]{4})_PID&([0-9A-F]{4})""", RegexOption.IGNORE_CASE)

        /** The vendor and product of an XInput device from its raw-input device path, or null for anything else. */
        fun xinputIds(devicePath: String): Pair<Int, Int>? {
            if (!devicePath.contains("IG_", ignoreCase = true)) return null
            val match = usbIds.find(devicePath) ?: bluetoothIds.find(devicePath) ?: return null
            return match.groupValues[1].toInt(16) to match.groupValues[2].toInt(16)
        }
    }
}

@Suppress("FunctionName")
private interface RawInput : Library {
    fun GetRawInputDeviceList(list: Pointer?, count: IntByReference, entrySize: Int): Int
    fun GetRawInputDeviceInfoW(device: Pointer, command: Int, data: Pointer?, size: IntByReference): Int

    companion object {
        /** `RAWINPUTDEVICELIST`: a handle and a type, padded to pointer alignment. */
        val ENTRY_SIZE = Native.POINTER_SIZE * 2
        const val DEVICE_NAME = 0x20000007
    }
}
