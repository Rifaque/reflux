package dev.reflux.desktop.gamepad

import com.sun.jna.Memory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Linux controller rumble (F-002, on by default on Linux too): what is offered, what is sent to the kernel, and that a
 * controller without motors, a node Reflux may not write, an unplugged controller and closing are all handled.
 * The kernel is faked; a real controller's walk is in VERIFICATION.md.
 */
class LinuxRumbleTest {
    private val root: Path = Files.createTempDirectory("reflux-rumble")
    private val devices = root.resolve("dev").createDirectories()
    private val sysfs = root.resolve("sys").createDirectories()

    /** A controller at jsN with event node eventM; [ff] its force-feedback capability bitmap. */
    private fun controller(js: String, event: String, ff: String) {
        val device = sysfs.resolve(js).resolve("device").createDirectories()
        device.resolve(event).createDirectories()
        device.resolve("capabilities").createDirectories().resolve("ff").writeText("$ff\n")
        devices.resolve(event).createFile()
        devices.resolve(js).createFile()
    }

    private class Kernel : LinuxRumble.ForceFeedback {
        val opened = mutableListOf<String>()
        val effects = mutableListOf<ByteArray>()
        val events = mutableListOf<ByteArray>()
        val closed = mutableListOf<Int>()
        var refuseOpen = false
        override fun open(path: String, flags: Int): Int = if (refuseOpen) -1 else { opened += path; 40 + opened.size }
        override fun ioctl(fd: Int, request: Long, effect: Memory): Int {
            assertEquals(LinuxRumble.EVIOCSFF, request)
            if (effect.getShort(2) < 0) effect.setShort(2, 7) // the kernel gives a new effect its id
            effects += effect.getByteArray(0, LinuxRumble.EFFECT_SIZE)
            return 0
        }
        override fun write(fd: Int, event: ByteArray): Boolean { events += event; return true }
        override fun close(fd: Int) { closed += fd }
    }

    private fun ByteArray.short(at: Int) = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).getShort(at).toInt() and 0xFFFF
    private fun ByteArray.int(at: Int) = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).getInt(at)

    @Test
    fun theRequestIsTheKernelsOwn() {
        // _IOW('E', 0x80, struct ff_effect) with a 48-byte effect, as <linux/input.h> defines it on 64-bit.
        assertEquals(0x40304580L, LinuxRumble.EVIOCSFF)
    }

    @Test
    fun aControllerThatCanRumbleIsDrivenThroughItsEventNode() {
        // FF_RUMBLE is bit 0x50 (80): the second 64-bit word's bit 16.
        controller("js0", "event5", "107030000 0")
        val kernel = Kernel()
        val motors = LinuxRumble(devices, sysfs, kernel)
        assertTrue(motors.canRumble("js:js0"))
        assertTrue(motors.rumble("js:js0", 0.5f))
        assertEquals(listOf(devices.resolve("event5").toString()), kernel.opened)
        val effect = kernel.effects.single()
        assertEquals(LinuxRumble.FF_RUMBLE, effect.short(0), "an FF_RUMBLE effect")
        assertEquals(LinuxRumble.MAX_REPLAY_MS, effect.short(10), "never longer than a second if nothing stops it")
        assertTrue(effect.short(18) in 32_000..33_500, "the weak motor at half: ${effect.short(18)}")
        assertTrue(effect.short(16) < effect.short(18), "the strong one softer")
        val play = kernel.events.single()
        assertEquals(LinuxRumble.EV_FF, play.short(16))
        assertEquals(7, play.short(18), "the effect the kernel named")
        assertEquals(1, play.int(20), "played")
        // Stopping, and a second rumble changing the same effect in place.
        assertTrue(motors.rumble("js:js0", 0f))
        assertEquals(0, kernel.events.last().int(20))
        motors.rumble("js:js0", 0.2f)
        assertEquals(7, kernel.effects.last().short(2), "the same effect, updated")
        assertEquals(1, kernel.opened.size, "the node opened once")
        // Closing stops it and closes the node.
        motors.close()
        assertEquals(0, kernel.events.last().int(20))
        assertEquals(1, kernel.closed.size)
    }

    @Test
    fun withoutMotorsOrPermissionNothingIsOffered() {
        controller("js0", "event5", "0") // no force feedback at all
        val kernel = Kernel()
        val motors = LinuxRumble(devices, sysfs, kernel)
        assertFalse(motors.canRumble("js:js0"))
        assertFalse(motors.rumble("js:js0", 0.5f))
        assertTrue(kernel.opened.isEmpty())
        assertFalse(motors.canRumble("xinput:0"), "only joystick ids are Linux's")
        // A node the session may not open: refused once, then not offered again.
        controller("js1", "event6", "107030000 0")
        kernel.refuseOpen = true
        assertTrue(motors.canRumble("js:js1"))
        assertFalse(motors.rumble("js:js1", 0.5f))
        assertFalse(motors.canRumble("js:js1"))
        // No force-feedback interface at all (not Linux): a quiet no-op.
        val none = LinuxRumble(devices, sysfs, system = null)
        assertFalse(none.canRumble("js:js0"))
        assertFalse(none.rumble("js:js0", 1f))
    }

    @Test
    fun anUnpluggedControllerIsForgotten() {
        controller("js0", "event5", "107030000 0")
        val kernel = Kernel()
        val motors = LinuxRumble(devices, sysfs, kernel)
        motors.rumble("js:js0", 0.4f)
        motors.retain(emptySet())
        assertEquals(1, kernel.closed.size, "its node closed")
        motors.close()
        assertEquals(1, kernel.closed.size, "and not again")
    }

    @Test
    fun onLinuxTheKernelInterfaceLoadsAndAMissingNodeIsRefusedQuietly() {
        org.junit.Assume.assumeTrue(System.getProperty("os.name").startsWith("Linux"))
        val system = LinuxRumble.ForceFeedback.load()
        kotlin.test.assertNotNull(system, "libc's open, ioctl and write are reached")
        assertTrue(system.open("/dev/input/event-reflux-none", 0x2) < 0, "a node that is not there is refused")
        // The real reader on this machine: whatever controllers it has, asking about one that is not there is a no-op.
        val motors = LinuxRumble()
        assertFalse(motors.canRumble("js:js99"))
        assertFalse(motors.rumble("js:js99", 0.5f))
        motors.close()
    }
}
