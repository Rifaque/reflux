package dev.reflux.desktop.gamepad

import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.io.path.name

/**
 * Controllers on Linux, through the kernel's joystick interface (`/dev/input/js*`).
 *
 * Each device is a stream of small events and needs nothing but read access, which desktop sessions grant
 * for game controllers. What each button number means comes from the device's capabilities in sysfs: the
 * kernel numbers joystick buttons in the order of their event codes, and those codes say what a button is
 * (`BTN_SOUTH`, `BTN_START`, ...) whichever driver the controller uses.
 *
 * @param devices where joystick device nodes live.
 * @param sysfs where their descriptions live (`<sysfs>/js0/device/...`).
 */
internal class LinuxGamepads(
    private val devices: Path = Path.of("/dev/input"),
    private val sysfs: Path = Path.of("/sys/class/input"),
) : GamepadReader {
    private val open = ConcurrentHashMap<String, Device>()
    private var scanAt = 0L

    @Volatile private var closed = false

    override fun poll(): List<GamepadState> {
        val now = System.nanoTime() / 1_000_000
        if (now >= scanAt) {
            scanAt = now + RESCAN_MS
            scan()
        }
        return open.values.sortedBy { it.name }.map { it.state.snapshot() }
    }

    override fun close() {
        closed = true
        open.values.forEach { it.reader.interrupt() }
        open.clear()
    }

    private fun scan() {
        val nodes = try {
            Files.newDirectoryStream(devices, "js[0-9]*").use { stream -> stream.associateBy { it.name } }
        } catch (_: IOException) {
            emptyMap() // no input devices here at all
        }
        open.keys.retainAll(nodes.keys)
        for ((name, node) in nodes) {
            if (open.containsKey(name) || !Files.isReadable(node)) continue
            val layout = JoystickLayout.read(sysfs.resolve(name).resolve("device")) ?: continue
            open[name] = Device(name, node, layout).also { it.reader.start() }
        }
    }

    /** One joystick node and the thread that blocks on its events. */
    private inner class Device(val name: String, node: Path, layout: JoystickLayout) {
        val state = JoystickState("js:$name", layout)

        val reader = thread(start = false, name = "reflux-gamepad-$name", isDaemon = true) {
            try {
                FileChannel.open(node, StandardOpenOption.READ).use { channel ->
                    val event = ByteBuffer.allocate(JoystickLayout.EVENT_SIZE).order(ByteOrder.LITTLE_ENDIAN)
                    while (!closed) {
                        event.clear()
                        while (event.hasRemaining()) if (channel.read(event) < 0) return@use
                        state.apply(event.getShort(4), event.get(6).toInt() and 0xFF, event.get(7).toInt() and 0xFF)
                    }
                }
            } catch (_: IOException) {
                open.remove(name, this) // unplugged, or closed while reading
            }
        }
    }

    private companion object {
        const val RESCAN_MS = 2_000L
    }
}

/** The state of one joystick device, accumulated from its events (`struct js_event`). */
internal class JoystickState(private val id: String, private val layout: JoystickLayout) {
    private val down = HashSet<GamepadButton>()
    private val tapped = HashSet<GamepadButton>()
    private val axes = FloatArray(JoystickLayout.ABS_COUNT)

    @Synchronized
    fun apply(value: Short, type: Int, number: Int) {
        // Events flagged as initial state describe what was already held when the device was opened.
        when (type and JoystickLayout.EVENT_INIT.inv()) {
            JoystickLayout.EVENT_BUTTON -> layout.button(number)?.let { button ->
                if (value.toInt() != 0) {
                    down += button
                    tapped += button
                } else {
                    down -= button
                }
            }
            JoystickLayout.EVENT_AXIS -> layout.axis(number)?.let { axes[it] = value / 32767f }
        }
    }

    /** What is held now, plus anything pressed and released again since the last snapshot. */
    @Synchronized
    fun snapshot(): GamepadState {
        val buttons = down + tapped + layout.axisButtons(axes)
        tapped.clear()
        return GamepadState(id, layout.family, buttons, axes[JoystickLayout.ABS_X], axes[JoystickLayout.ABS_Y])
    }
}

/**
 * What the button and axis numbers of one joystick device mean.
 *
 * @param keys event codes by button number.
 * @param absolutes axis codes by axis number.
 */
internal class JoystickLayout(val family: ControllerFamily, private val keys: List<Int>, private val absolutes: List<Int>) {
    fun button(number: Int): GamepadButton? {
        val code = keys.getOrNull(number) ?: return null
        return when (code) {
            BTN_SOUTH -> GamepadButton.FACE_SOUTH
            BTN_EAST -> GamepadButton.FACE_EAST
            // The Xbox driver reports its X and Y buttons under the names BTN_X and BTN_Y, which are the
            // codes of the north and west positions; on the controller they are west and north.
            BTN_NORTH -> if (family == ControllerFamily.XBOX) GamepadButton.FACE_WEST else GamepadButton.FACE_NORTH
            BTN_WEST -> if (family == ControllerFamily.XBOX) GamepadButton.FACE_NORTH else GamepadButton.FACE_WEST
            BTN_TL -> GamepadButton.SHOULDER_LEFT
            BTN_TR -> GamepadButton.SHOULDER_RIGHT
            BTN_TL2 -> GamepadButton.TRIGGER_LEFT
            BTN_TR2 -> GamepadButton.TRIGGER_RIGHT
            BTN_SELECT -> GamepadButton.SELECT
            BTN_START -> GamepadButton.START
            BTN_MODE -> GamepadButton.HOME
            BTN_THUMBL -> GamepadButton.STICK_LEFT
            BTN_THUMBR -> GamepadButton.STICK_RIGHT
            BTN_DPAD_UP -> GamepadButton.DPAD_UP
            BTN_DPAD_DOWN -> GamepadButton.DPAD_DOWN
            BTN_DPAD_LEFT -> GamepadButton.DPAD_LEFT
            BTN_DPAD_RIGHT -> GamepadButton.DPAD_RIGHT
            // Controllers without a dedicated driver only number their buttons.
            in BTN_JOYSTICK until BTN_GAMEPAD -> NumberedButtons.position(code - BTN_JOYSTICK, family)
            else -> null
        }
    }

    /**
     * Whether this device has buttons to navigate with. A controller also shows up as devices that only have
     * axes (its motion sensors); read as sticks, tilting the controller would move the focus.
     */
    val isController: Boolean get() = keys.indices.count { button(it) != null } >= 4

    /** The axis code of axis [number], when it is one Reflux reads. */
    fun axis(number: Int): Int? = absolutes.getOrNull(number)?.takeIf { it < ABS_COUNT }

    /** Buttons that this controller reports as axes: the D-pad as a hat, and analog triggers. */
    fun axisButtons(axes: FloatArray): Set<GamepadButton> = buildSet {
        if (axes[ABS_HAT0X] < -0.5f) add(GamepadButton.DPAD_LEFT)
        if (axes[ABS_HAT0X] > 0.5f) add(GamepadButton.DPAD_RIGHT)
        if (axes[ABS_HAT0Y] < -0.5f) add(GamepadButton.DPAD_UP)
        if (axes[ABS_HAT0Y] > 0.5f) add(GamepadButton.DPAD_DOWN)
        // A trigger rests at one end of its axis and is clearly pulled past the middle.
        if (analogTriggers && axes[ABS_Z] > 0f) add(GamepadButton.TRIGGER_LEFT)
        if (analogTriggers && axes[ABS_RZ] > 0f) add(GamepadButton.TRIGGER_RIGHT)
    }

    /** Whether Z and RZ are triggers. On pads without stick-click and trigger buttons they are the right stick. */
    private val analogTriggers = BTN_SOUTH in keys && ABS_RX in absolutes && ABS_Z in absolutes && ABS_RZ in absolutes

    companion object {
        const val EVENT_SIZE = 8 // struct js_event: u32 time, s16 value, u8 type, u8 number
        const val EVENT_BUTTON = 0x01
        const val EVENT_AXIS = 0x02
        const val EVENT_INIT = 0x80

        const val ABS_X = 0x00
        const val ABS_Y = 0x01
        const val ABS_Z = 0x02
        const val ABS_RX = 0x03
        const val ABS_RZ = 0x05
        const val ABS_HAT0X = 0x10
        const val ABS_HAT0Y = 0x11
        const val ABS_COUNT = 0x40

        private const val BTN_MISC = 0x100
        private const val BTN_JOYSTICK = 0x120
        private const val BTN_GAMEPAD = 0x130
        private const val BTN_SOUTH = 0x130
        private const val BTN_EAST = 0x131
        private const val BTN_NORTH = 0x133
        private const val BTN_WEST = 0x134
        private const val BTN_TL = 0x136
        private const val BTN_TR = 0x137
        private const val BTN_TL2 = 0x138
        private const val BTN_TR2 = 0x139
        private const val BTN_SELECT = 0x13A
        private const val BTN_START = 0x13B
        private const val BTN_MODE = 0x13C
        private const val BTN_THUMBL = 0x13D
        private const val BTN_THUMBR = 0x13E
        private const val BTN_DPAD_UP = 0x220
        private const val BTN_DPAD_DOWN = 0x221
        private const val BTN_DPAD_LEFT = 0x222
        private const val BTN_DPAD_RIGHT = 0x223
        private const val KEY_MAX = 0x2FF

        /** Reads a device's description from its sysfs directory, or returns null when it is not a readable joystick. */
        fun read(device: Path): JoystickLayout? = try {
            val name = Files.readString(device.resolve("name")).trim()
            val vendor = Files.readString(device.resolve("id/vendor")).trim().toInt(16)
            of(
                ControllerFamilies.of(vendor, name),
                bits(Files.readString(device.resolve("capabilities/key"))),
                bits(Files.readString(device.resolve("capabilities/abs"))),
            ).takeIf { it.isController }
        } catch (_: IOException) {
            null
        } catch (_: NumberFormatException) {
            null
        }

        /**
         * The layout of a device with the given key and axis capabilities, numbered the way the kernel's
         * joystick interface numbers them: buttons from `BTN_JOYSTICK` upward first, then the ones below
         * it, and axes in code order.
         */
        fun of(family: ControllerFamily, keyBits: Set<Int>, absBits: Set<Int>): JoystickLayout {
            val keys = (BTN_JOYSTICK..KEY_MAX).filter { it in keyBits } + (BTN_MISC until BTN_JOYSTICK).filter { it in keyBits }
            return JoystickLayout(family, keys, absBits.filter { it < ABS_COUNT }.sorted())
        }

        /** The set bits of a sysfs capability bitmap: hexadecimal words, most significant first. */
        fun bits(bitmap: String, wordBits: Int = if (System.getProperty("os.arch").contains("64")) 64 else 32): Set<Int> {
            val words = bitmap.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }.reversed()
            return buildSet {
                words.forEachIndexed { index, word ->
                    val value = word.toULong(16)
                    for (bit in 0 until wordBits) if ((value shr bit) and 1uL == 1uL) add(index * wordBits + bit)
                }
            }
        }
    }
}
