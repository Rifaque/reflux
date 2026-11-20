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
 *
 * B-050: the joystick API corrupts the process's heap when a controller is connected while it is being asked (measured on
 * Windows 11 with a GameSir G7 Pro: a probe asking only it crashed on reconnect; XInput and raw input, asked the same way,
 * never did). So it is asked only when raw input lists a game controller that XInput does not read, and not at all while the
 * set of game controllers is changing: it is left alone until that set has been the same for [SETTLE_MS].
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

    /** The game controllers raw input listed last time, and since when that list has been the same. */
    private var controllersSeen: Controllers? = null
    private var controllersSince = 0L

    private val vibration = Memory(4)

    /**
     * An XInput controller's two motors at [strength] (F-002). The joystick API has no motors to drive, so a PlayStation,
     * Switch, or generic pad read through it does not rumble here: it is not offered as one that can.
     */
    override fun canRumble(id: String): Boolean = xinput != null && id.startsWith(XInput.XINPUT_ID_PREFIX)

    override fun rumble(id: String, strength: Float): Boolean {
        val api = xinput ?: return false
        val slot = id.removePrefix(XInput.XINPUT_ID_PREFIX).toIntOrNull() ?: return false
        val speed = (strength.coerceIn(0f, 1f) * 65535).toInt()
        vibration.setShort(0, speed.toShort())
        vibration.setShort(2, speed.toShort())
        return synchronized(vibration) { api.XInputSetState(slot, vibration) == 0 }
    }

    private class Joystick(val id: Int, val family: ControllerFamily, val hasHat: Boolean, val xMin: Int, val xMax: Int, val yMin: Int, val yMax: Int, val zMin: Int, val zMax: Int)

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
        return XInput.decode(slot, state.getShort(4).toInt() and 0xFFFF, state.getByte(6), state.getByte(7), state.getShort(8), state.getShort(10), state.getShort(12))
    }

    private fun scanJoysticks(): List<Joystick> {
        val api = joysticks ?: return emptyList()
        val now = System.nanoTime() / 1_000_000
        val controllers = controllers() ?: return emptyList()
        if (controllers != controllersSeen) {
            controllersSeen = controllers
            controllersSince = now
        }
        // Only XInput pads (or none): nothing for the joystick API to read, so it is never asked.
        if (controllers.others == 0) return emptyList()
        // Something came or went a moment ago: the joystick API is not asked until that has settled.
        if (now - controllersSince < SETTLE_MS) {
            joystickScanAt = controllersSince + SETTLE_MS
            return emptyList()
        }
        val xinputDevices = controllers.xinput
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
            // The right stick's horizontal axis is the joystick API's Z on PlayStation and most HID pads (B-029).
            Joystick(id, ControllerFamilies.of(vendor, caps.getWideString(4)), hasHat, caps.getInt(68), caps.getInt(72), caps.getInt(76), caps.getInt(80), caps.getInt(84), caps.getInt(88))
        }
    }

    private fun readJoystick(joystick: Joystick): GamepadState? {
        val api = joysticks ?: return null
        info.setInt(0, Joysticks.INFO_SIZE.toInt())
        info.setInt(4, Joysticks.RETURN_ALL)
        if (api.joyGetPosEx(joystick.id, info) != 0) {
            // Unplugged: not asked again until the controllers have been looked at afresh, and have settled.
            connectedJoysticks = emptyList()
            joystickScanAt = 0
            return null
        }
        return Joysticks.decode(
            joystick.id, joystick.family,
            buttons = info.getInt(32), pov = info.getInt(40).takeIf { joystick.hasHat },
            x = Joysticks.normalize(info.getInt(8), joystick.xMin, joystick.xMax),
            y = Joysticks.normalize(info.getInt(12), joystick.yMin, joystick.yMax),
            rightX = if (joystick.zMax > joystick.zMin) Joysticks.normalize(info.getInt(16), joystick.zMin, joystick.zMax) else 0f,
        )
    }

    /**
     * The game controllers raw input lists (HID joysticks and gamepads): the vendor and product of those XInput reads, from
     * the `IG_` marker in their device paths, and the device paths of all of them. Null where raw input cannot be asked.
     */
    private data class Controllers(val paths: Set<String>) {
        val xinput: Set<Pair<Int, Int>> = paths.mapNotNull(Joysticks::xinputIds).toSet()
        val others: Int = paths.count { Joysticks.xinputIds(it) == null }
    }

    private fun controllers(): Controllers? {
        val api = rawInput ?: return null
        return runCatching {
            val count = IntByReference()
            if (api.GetRawInputDeviceList(null, count, RawInput.ENTRY_SIZE) != 0) return null
            if (count.value == 0) return Controllers(emptySet())
            val list = Memory(RawInput.ENTRY_SIZE.toLong() * count.value)
            val read = api.GetRawInputDeviceList(list, count, RawInput.ENTRY_SIZE)
            if (read < 0) return null
            val device = Memory(RawInput.DEVICE_INFO_SIZE.toLong())
            val paths = (0 until read).mapNotNull { index ->
                if (list.getInt(index.toLong() * RawInput.ENTRY_SIZE + Native.POINTER_SIZE) != RawInput.TYPE_HID) return@mapNotNull null
                val handle = list.getPointer(index.toLong() * RawInput.ENTRY_SIZE) ?: return@mapNotNull null
                device.clear()
                device.setInt(0, RawInput.DEVICE_INFO_SIZE)
                val size = IntByReference(RawInput.DEVICE_INFO_SIZE)
                if (api.GetRawInputDeviceInfoW(handle, RawInput.DEVICE_INFO, device, size) <= 0) return@mapNotNull null
                val usagePage = device.getShort(20).toInt() and 0xFFFF
                val usage = device.getShort(22).toInt() and 0xFFFF
                if (!RawInput.isGameController(usagePage, usage)) return@mapNotNull null
                val length = IntByReference()
                api.GetRawInputDeviceInfoW(handle, RawInput.DEVICE_NAME, null, length)
                if (length.value <= 0) return@mapNotNull null
                val name = Memory((length.value + 1) * 2L)
                if (api.GetRawInputDeviceInfoW(handle, RawInput.DEVICE_NAME, name, length) <= 0) return@mapNotNull null
                name.getWideString(0)
            }
            Controllers(paths.toSet())
        }.getOrNull()
    }

    private companion object {
        const val RESCAN_MS = 2_000L

        /** How long the set of game controllers must stay the same before the joystick API is asked again (B-050). */
        const val SETTLE_MS = 4_000L
    }
}

@Suppress("FunctionName")
internal interface XInput : Library {
    fun XInputGetState(index: Int, state: Pointer): Int

    /** `XINPUT_VIBRATION`: two `WORD`s, the low- and high-frequency motors' speeds. */
    fun XInputSetState(index: Int, vibration: Pointer): Int

    companion object {
        const val SLOTS = 4
        const val STATE_SIZE = 16L
        private const val TRIGGER_PRESSED = 96 // of 255: clearly pulled, not resting
        const val XINPUT_ID_PREFIX = "xinput:"

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
        fun decode(slot: Int, buttons: Int, leftTrigger: Byte, rightTrigger: Byte, thumbX: Short, thumbY: Short, rightThumbX: Short = 0): GamepadState {
            val pressed = bits.filter { buttons and it.first != 0 }.mapTo(mutableSetOf()) { it.second }
            if ((leftTrigger.toInt() and 0xFF) >= TRIGGER_PRESSED) pressed += GamepadButton.TRIGGER_LEFT
            if ((rightTrigger.toInt() and 0xFF) >= TRIGGER_PRESSED) pressed += GamepadButton.TRIGGER_RIGHT
            return GamepadState("$XINPUT_ID_PREFIX$slot", ControllerFamily.XBOX, pressed, thumbX / 32767f, -(thumbY / 32767f), (rightThumbX / 32767f).coerceIn(-1f, 1f))
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
        fun decode(id: Int, family: ControllerFamily, buttons: Int, pov: Int?, x: Float, y: Float, rightX: Float = 0f): GamepadState {
            val pressed = NumberedButtons.fromMask(buttons, family).toMutableSet()
            val angle = pov?.and(0xFFFF) ?: POV_CENTERED
            if (angle != POV_CENTERED) {
                if (angle >= 29250 || angle <= 6750) pressed += GamepadButton.DPAD_UP
                if (angle in 2250..15750) pressed += GamepadButton.DPAD_RIGHT
                if (angle in 11250..24750) pressed += GamepadButton.DPAD_DOWN
                if (angle in 20250..33750) pressed += GamepadButton.DPAD_LEFT
            }
            return GamepadState("joystick:$id", family, pressed, x, y, rightX)
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
internal interface RawInput : Library {
    fun GetRawInputDeviceList(list: Pointer?, count: IntByReference, entrySize: Int): Int
    fun GetRawInputDeviceInfoW(device: Pointer, command: Int, data: Pointer?, size: IntByReference): Int

    companion object {
        /** `RAWINPUTDEVICELIST`: a handle and a type, padded to pointer alignment. */
        val ENTRY_SIZE = Native.POINTER_SIZE * 2
        const val DEVICE_NAME = 0x20000007
        const val DEVICE_INFO = 0x2000000b

        /** `RID_DEVICE_INFO`: its size, its type, and for a HID device vendor, product, version, usage page and usage. */
        const val DEVICE_INFO_SIZE = 32
        const val TYPE_HID = 2

        /** A HID joystick (generic desktop page, usage 4) or gamepad (5): what a game controller reports itself as. */
        fun isGameController(usagePage: Int, usage: Int): Boolean = usagePage == 0x01 && (usage == 0x04 || usage == 0x05)
    }
}
