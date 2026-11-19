package dev.reflux.desktop.gamepad

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

/**
 * A controller's motors on Linux (F-002; the owner's decision of 2026-11-19: controller feedback on by default on Linux as on
 * Windows), through the kernel's force-feedback interface: the `FF_RUMBLE` effect of the controller's event device.
 *
 * The joystick node Reflux reads (`/dev/input/jsN`) has no motors; the same controller's event node does
 * (`/sys/class/input/jsN/device/eventM`). A controller is offered rumble only when that node says it can rumble
 * (`capabilities/ff` has `FF_RUMBLE`) and the session may write to it, which desktop sessions grant for game controllers
 * (systemd's `uaccess`); anything else is a quiet no-op, as on Windows for a controller without motors.
 *
 * One effect per controller is uploaded (`EVIOCSFF`) and changed in place; it is played and stopped by writing an `EV_FF`
 * event. The node is closed, and the effect goes with it, when the controller goes or Reflux closes.
 */
internal class LinuxRumble(
    private val devices: Path = Path.of("/dev/input"),
    private val sysfs: Path = Path.of("/sys/class/input"),
    private val system: ForceFeedback? = ForceFeedback.load(),
) : AutoCloseable {
    private class Motor(val fd: Int, var effect: Short = -1)

    private val motors = HashMap<String, Motor>()
    private val refused = HashSet<String>()

    /** The event node of joystick [js] (its sysfs name, `js0`), if it can rumble and may be written to. */
    internal fun eventNode(js: String): Path? = try {
        val device = sysfs.resolve(js).resolve("device")
        val event = Files.newDirectoryStream(device, "event[0-9]*").use { it.firstOrNull() } ?: return null
        val ff = Files.readString(device.resolve("capabilities/ff"))
        if (FF_RUMBLE !in JoystickLayout.bits(ff)) return null
        devices.resolve(event.name).takeIf { Files.isWritable(it) }
    } catch (_: IOException) {
        null
    }

    /** Whether the controller Reflux knows as [id] (`js:js0`) has motors Reflux can drive. */
    @Synchronized
    fun canRumble(id: String): Boolean {
        val js = id.removePrefix(ID_PREFIX).takeIf { id.startsWith(ID_PREFIX) } ?: return false
        if (system == null || js in refused) return false
        return js in motors || eventNode(js) != null
    }

    /** Runs [id]'s motors at [strength] (0 stops them). False when they could not be driven. */
    @Synchronized
    fun rumble(id: String, strength: Float): Boolean {
        val api = system ?: return false
        val js = id.removePrefix(ID_PREFIX).takeIf { id.startsWith(ID_PREFIX) } ?: return false
        val motor = motors[js] ?: run {
            if (strength <= 0f || js in refused) return false
            val node = eventNode(js) ?: return false
            val fd = api.open(node.toString(), O_RDWR or O_CLOEXEC)
            if (fd < 0) {
                refused += js
                return false
            }
            Motor(fd).also { motors[js] = it }
        }
        if (strength <= 0f) {
            if (motor.effect >= 0) api.write(motor.fd, play(motor.effect, on = false))
            return true
        }
        val effect = rumbleEffect(motor.effect, strength)
        if (api.ioctl(motor.fd, EVIOCSFF, effect) < 0) {
            forget(js)
            return false
        }
        motor.effect = effect.getShort(2)
        return api.write(motor.fd, play(motor.effect, on = true))
    }

    /** [js] is gone (unplugged): its node is closed and nothing more is sent to it. */
    @Synchronized
    fun forget(js: String) {
        val motor = motors.remove(js) ?: return
        runCatching { system?.close(motor.fd) }
    }

    /** Keeps only the controllers in [present] (their sysfs names). */
    @Synchronized
    fun retain(present: Set<String>) {
        (motors.keys - present).forEach(::forget)
        refused.retainAll(present)
    }

    @Synchronized
    override fun close() {
        motors.keys.toList().forEach { js ->
            motors[js]?.let { motor -> if (motor.effect >= 0) runCatching { system?.write(motor.fd, play(motor.effect, on = false)) } }
            forget(js)
        }
    }

    /** The system calls this needs, so that what is sent can be checked without a controller (LinuxRumbleTest). */
    internal interface ForceFeedback {
        fun open(path: String, flags: Int): Int
        fun ioctl(fd: Int, request: Long, effect: Memory): Int
        fun write(fd: Int, event: ByteArray): Boolean
        fun close(fd: Int)

        companion object {
            fun load(): ForceFeedback? = if (!System.getProperty("os.name").startsWith("Linux")) null else runCatching {
                val c = Native.load("c", LibC::class.java)
                object : ForceFeedback {
                    override fun open(path: String, flags: Int) = c.open(path, flags)
                    override fun ioctl(fd: Int, request: Long, effect: Memory) = c.ioctl(fd, request, effect)
                    override fun write(fd: Int, event: ByteArray): Boolean {
                        val buffer = Memory(event.size.toLong()).apply { write(0, event, 0, event.size) }
                        return c.write(fd, buffer, event.size.toLong()) == event.size.toLong()
                    }
                    override fun close(fd: Int) {
                        c.close(fd)
                    }
                }
            }.getOrNull()
        }
    }

    @Suppress("FunctionName")
    private interface LibC : Library {
        fun open(path: String, flags: Int): Int
        fun ioctl(fd: Int, request: Long, argument: Pointer): Int
        fun write(fd: Int, buffer: Pointer, count: Long): Long
        fun close(fd: Int): Int
    }

    internal companion object {
        const val ID_PREFIX = "js:"
        const val FF_RUMBLE = 0x50
        const val EV_FF = 0x15
        private const val O_RDWR = 0x2
        private const val O_CLOEXEC = 0x80000

        /** `struct ff_effect` on a 64-bit kernel: type, id, direction, trigger, replay, then the effect at offset 16. */
        const val EFFECT_SIZE = 48

        /** `EVIOCSFF`: `_IOW('E', 0x80, struct ff_effect)`. */
        const val EVIOCSFF = (1L shl 30) or (EFFECT_SIZE.toLong() shl 16) or ('E'.code.toLong() shl 8) or 0x80L

        /** How long one upload may play at most; Reflux stops it sooner (its patterns are tens of milliseconds). */
        const val MAX_REPLAY_MS = 1_000

        /**
         * An `FF_RUMBLE` effect at [strength] on both motors, the strong (low-frequency) one a little softer, so a light tick
         * is a tick and not a thud. [id] is the effect's own id once uploaded, or -1 for a new one.
         */
        fun rumbleEffect(id: Short, strength: Float): Memory {
            val effect = Memory(EFFECT_SIZE.toLong())
            effect.clear()
            val level = strength.coerceIn(0f, 1f)
            effect.setShort(0, FF_RUMBLE.toShort()) // type
            effect.setShort(2, id) // id: -1 asks the kernel for a new one, which it writes back
            effect.setShort(4, 0) // direction
            effect.setShort(10, MAX_REPLAY_MS.toShort()) // replay.length
            effect.setShort(12, 0) // replay.delay
            effect.setShort(16, (level * 0.8f * 0xFFFF).toInt().toShort()) // u.rumble.strong_magnitude
            effect.setShort(18, (level * 0xFFFF).toInt().toShort()) // u.rumble.weak_magnitude
            return effect
        }

        /** `struct input_event` on a 64-bit system: a zero time, then `EV_FF`, the effect, and 1 to play or 0 to stop. */
        fun play(effect: Short, on: Boolean): ByteArray = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).apply {
            putLong(0L)
            putLong(0L)
            putShort(EV_FF.toShort())
            putShort(effect)
            putInt(if (on) 1 else 0)
        }.array()
    }
}
