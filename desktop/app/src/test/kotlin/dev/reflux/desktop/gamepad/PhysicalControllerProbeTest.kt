package dev.reflux.desktop.gamepad

import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What Reflux's Linux reader makes of the controller plugged into this machine (CACHY-004, CACHY-005): its family, and what each
 * of its buttons means to Reflux. Read only: nothing is written to the device and it is never rumbled. Only on request
 * (REFLUX_PHYSICAL_CONTROLLER=1), on a machine with a controller at /dev/input/js0.
 */
class PhysicalControllerProbeTest {
    @Test
    fun theAttachedControllerIsReadAndItsButtonsNamed() {
        assumeTrue("probe not requested", System.getenv("REFLUX_PHYSICAL_CONTROLLER") == "1")
        val device = Path.of("/sys/class/input/js0/device")
        assumeTrue("no controller at js0", Files.isDirectory(device))
        val layout = JoystickLayout.read(device)
        assertTrue(layout != null, "the reader understands the controller")
        val named = (0 until 32).mapNotNull { n -> layout!!.button(n)?.let { "$n=$it" } }
        println("PROBE name=${Files.readString(device.resolve("name")).trim()} family=${layout!!.family} buttons=$named")
        val reader = LinuxGamepads()
        try {
            var states = reader.poll()
            repeat(20) { if (states.isEmpty()) { Thread.sleep(100); states = reader.poll() } }
            println("PROBE detected=${states.map { "${it.id}:${it.family}" }}")
            assertTrue(states.isNotEmpty(), "the reader finds the controller")
        } finally {
            reader.close()
        }
    }
}
