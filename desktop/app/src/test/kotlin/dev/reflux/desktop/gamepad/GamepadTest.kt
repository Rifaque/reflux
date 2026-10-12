package dev.reflux.desktop.gamepad

import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GamepadTest {
    private val presses = mutableListOf<Pair<GamepadButton, ControllerFamily>>()
    private val translator = GamepadTranslator { button, family -> presses += button to family }

    private fun pad(vararg buttons: GamepadButton, x: Float = 0f, y: Float = 0f, id: String = "pad", family: ControllerFamily = ControllerFamily.PLAYSTATION) =
        listOf(GamepadState(id, family, buttons.toSet(), x, y))

    private fun pressed() = presses.map { it.first }.also { presses.clear() }

    /** Controllers are first seen at rest, as they are when the application starts. */
    init {
        translator.update(pad(), -1_000)
    }

    @Test
    fun aButtonIsPressedOnceHoweverLongItIsHeld() {
        translator.update(pad(GamepadButton.FACE_SOUTH), 0)
        translator.update(pad(GamepadButton.FACE_SOUTH), 16)
        translator.update(pad(GamepadButton.FACE_SOUTH), 2_000)
        assertEquals(listOf(GamepadButton.FACE_SOUTH to ControllerFamily.PLAYSTATION), presses.toList())
        translator.update(pad(), 2_016)
        translator.update(pad(GamepadButton.FACE_SOUTH), 2_032)
        assertEquals(2, presses.size)
    }

    @Test
    fun aHeldDirectionRepeatsAfterAPause() {
        translator.update(pad(GamepadButton.DPAD_RIGHT), 0)
        translator.update(pad(GamepadButton.DPAD_RIGHT), 400)
        assertEquals(1, presses.size, "a normal press moves once")
        translator.update(pad(GamepadButton.DPAD_RIGHT), 430)
        translator.update(pad(GamepadButton.DPAD_RIGHT), 500)
        translator.update(pad(GamepadButton.DPAD_RIGHT), 545)
        assertEquals(3, presses.size, "holding travels")
        translator.update(pad(), 560)
        translator.update(pad(GamepadButton.DPAD_RIGHT), 580)
        translator.update(pad(GamepadButton.DPAD_RIGHT), 700)
        assertEquals(4, presses.size, "a new press waits again before repeating")
    }

    @Test
    fun theStickNavigatesOnlyWhenClearlyDeflected() {
        translator.update(pad(x = 0.3f), 0)
        assertTrue(pressed().isEmpty(), "a stick resting off-center does nothing")
        translator.update(pad(x = 0.7f), 16)
        assertEquals(listOf(GamepadButton.DPAD_RIGHT), pressed())
        // Wobbling around the threshold is still the same push.
        translator.update(pad(x = 0.5f), 32)
        translator.update(pad(x = 0.62f), 48)
        assertTrue(pressed().isEmpty())
        translator.update(pad(x = 0.1f), 64)
        translator.update(pad(x = -0.9f, y = 0.5f), 80)
        assertEquals(listOf(GamepadButton.DPAD_LEFT), pressed(), "a diagonal push follows its stronger axis")
        translator.update(pad(), 96)
        translator.update(pad(y = 1f), 112)
        translator.update(pad(y = -1f), 128)
        assertEquals(listOf(GamepadButton.DPAD_DOWN, GamepadButton.DPAD_UP), pressed())
    }

    @Test
    fun buttonsPressedWhileRefluxIsNotInUseDoNothing() {
        translator.update(pad(GamepadButton.FACE_SOUTH), 0, active = false)
        translator.update(pad(GamepadButton.FACE_SOUTH, GamepadButton.DPAD_DOWN), 600, active = false)
        translator.update(pad(GamepadButton.FACE_SOUTH), 700, active = true)
        assertTrue(presses.isEmpty(), "a button already held when Reflux comes back is not a press")
        translator.update(pad(), 716)
        translator.update(pad(GamepadButton.FACE_EAST), 732)
        assertEquals(listOf(GamepadButton.FACE_EAST), pressed())
    }

    @Test
    fun controllersAreTrackedSeparatelyAndForgottenWhenUnplugged() {
        translator.update(pad(id = "a") + pad(id = "b", family = ControllerFamily.XBOX), -16)
        translator.update(pad(GamepadButton.FACE_SOUTH, id = "a") + pad(id = "b", family = ControllerFamily.XBOX), 0)
        translator.update(pad(GamepadButton.FACE_SOUTH, id = "a") + pad(GamepadButton.FACE_SOUTH, id = "b", family = ControllerFamily.XBOX), 16)
        assertEquals(listOf(ControllerFamily.PLAYSTATION, ControllerFamily.XBOX), presses.map { it.second })
        translator.update(pad(GamepadButton.FACE_SOUTH, id = "a"), 32)
        translator.update(pad(GamepadButton.FACE_SOUTH, id = "a") + pad(GamepadButton.FACE_SOUTH, id = "b", family = ControllerFamily.XBOX), 48)
        assertEquals(2, presses.size, "a controller that comes back with a button held has not pressed it")
        translator.update(pad(GamepadButton.FACE_SOUTH, id = "a") + pad(id = "b", family = ControllerFamily.XBOX), 64)
        translator.update(pad(GamepadButton.FACE_SOUTH, id = "a") + pad(GamepadButton.FACE_SOUTH, id = "b", family = ControllerFamily.XBOX), 80)
        assertEquals(3, presses.size, "once released, it presses like any other")
    }

    @Test
    fun whateverIsHeldWhenAControllerAppearsIsNotAPress() {
        // Found on real hardware: a device whose resting state reads as "up" navigated upward forever.
        val stuck = pad(GamepadButton.DPAD_UP, GamepadButton.FACE_SOUTH, x = 1f, id = "new")
        translator.update(stuck, 0)
        translator.update(stuck, 500)
        translator.update(stuck, 5_000)
        assertTrue(presses.isEmpty(), "neither pressed nor repeated")
        translator.update(pad(GamepadButton.DPAD_UP, id = "new"), 5_016)
        translator.update(pad(GamepadButton.DPAD_UP, GamepadButton.FACE_EAST, id = "new"), 5_032)
        assertEquals(listOf(GamepadButton.FACE_EAST), pressed(), "what is pressed afterwards counts")
    }

    @Test
    fun xinputStatesAreReadByPosition() {
        val state = XInput.decode(slot = 1, buttons = 0x1000 or 0x0001 or 0x0020, leftTrigger = 20, rightTrigger = 200.toByte(), thumbX = 16384, thumbY = 32767)
        assertEquals("xinput:1", state.id)
        assertEquals(ControllerFamily.XBOX, state.family)
        assertEquals(setOf(GamepadButton.FACE_SOUTH, GamepadButton.DPAD_UP, GamepadButton.SELECT, GamepadButton.TRIGGER_RIGHT), state.buttons)
        assertEquals(0.5f, state.stickX, 0.01f)
        assertEquals(-1f, state.stickY, 0.01f, "up on the stick is up on screen")
        assertEquals(setOf(GamepadButton.FACE_EAST, GamepadButton.FACE_WEST, GamepadButton.FACE_NORTH), XInput.decode(0, 0xE000, 0, 0, 0, 0).buttons)
    }

    @Test
    fun hidControllersAreReadByTheirFamilysLayout() {
        // DualSense and DualShock 4 number their buttons Square, Cross, Circle, Triangle, L1, R1, L2, R2, Create, Options.
        val dualSense = Joysticks.decode(0, ControllerFamily.PLAYSTATION, buttons = 0b10_0000_0010, pov = 9000, x = 0f, y = 0f)
        assertEquals(setOf(GamepadButton.FACE_SOUTH, GamepadButton.START, GamepadButton.DPAD_RIGHT), dualSense.buttons)
        assertEquals(setOf(GamepadButton.FACE_WEST, GamepadButton.FACE_NORTH), Joysticks.decode(0, ControllerFamily.PLAYSTATION, 0b1001, 0xFFFF, 0f, 0f).buttons)
        // A Switch Pro controller starts with B, which is its south button.
        assertEquals(setOf(GamepadButton.FACE_SOUTH), Joysticks.decode(0, ControllerFamily.NINTENDO, 0b1, 0xFFFF, 0f, 0f).buttons)
        assertEquals(setOf(GamepadButton.FACE_EAST), Joysticks.decode(0, ControllerFamily.NINTENDO, 0b10, 0xFFFF, 0f, 0f).buttons)

        assertEquals(setOf(GamepadButton.DPAD_UP, GamepadButton.DPAD_LEFT), Joysticks.decode(0, ControllerFamily.GENERIC, 0, 31500, 0f, 0f).buttons)
        assertEquals(setOf(GamepadButton.DPAD_DOWN), Joysticks.decode(0, ControllerFamily.GENERIC, 0, 18000, 0f, 0f).buttons)
        assertEquals(setOf(GamepadButton.DPAD_UP), Joysticks.decode(0, ControllerFamily.GENERIC, 0, 0, 0f, 0f).buttons)
        // A device without a hat reports 0 for it, which is also the angle of "up".
        assertTrue(Joysticks.decode(0, ControllerFamily.GENERIC, 0, pov = null, 0f, 0f).buttons.isEmpty())
        // Windows lists other HID devices as joysticks with nothing on them; those are not controllers.
        assertFalse(Joysticks.isGameController(buttons = 0, axes = 0))
        assertFalse(Joysticks.isGameController(buttons = 2, axes = 3), "a flight-stick throttle is not something to browse with")
        assertTrue(Joysticks.isGameController(buttons = 14, axes = 6), "a DualSense")
        assertEquals(-1f, Joysticks.normalize(0, 0, 65535))
        assertEquals(1f, Joysticks.normalize(65535, 0, 65535))
        assertEquals(0f, Joysticks.normalize(5, 5, 5), "an axis without a range rests")
    }

    @Test
    fun familiesComeFromTheVendorOrTheName() {
        assertEquals(ControllerFamily.PLAYSTATION, ControllerFamilies.of(0x054C))
        assertEquals(ControllerFamily.XBOX, ControllerFamilies.of(0x045E))
        assertEquals(ControllerFamily.NINTENDO, ControllerFamilies.of(0x057E))
        assertEquals(ControllerFamily.XBOX, ControllerFamilies.of(0x24C6, "PowerA Xbox One Controller"))
        assertEquals(ControllerFamily.PLAYSTATION, ControllerFamilies.of(0x0F0D, "HORI Wireless Controller"))
        assertEquals(ControllerFamily.GENERIC, ControllerFamilies.of(0x046D, "Logitech Dual Action"))
    }

    @Test
    fun xinputDevicesAreRecognizedSoTheyAreNotReadTwice() {
        assertEquals(0x045E to 0x02FF, Joysticks.xinputIds("""\\?\HID#VID_045E&PID_02FF&IG_00#8&1a2b3c4d&0&0000#{4d1e55b2-f16f-11cf-88cb-001111000030}"""))
        assertEquals(
            0x045E to 0x0B13,
            Joysticks.xinputIds("""\\?\HID#{00001812-0000-1000-8000-00805f9b34fb}_Dev_VID&02045e_PID&0b13_REV&0509_ac8ebd09b98e&IG_00#a&2336e19&3&0000#{4d1e55b2-f16f-11cf-88cb-001111000030}"""),
            "an Xbox controller connected over Bluetooth",
        )
        assertNull(Joysticks.xinputIds("""\\?\HID#VID_054C&PID_0CE6&MI_03#8&2f1e3a7&0&0000#{4d1e55b2-f16f-11cf-88cb-001111000030}"""), "a DualSense is not an XInput device")
    }

    // Linux ------------------------------------------------------------------------------------------

    /** Capabilities as the kernel prints them in sysfs for 64-bit words: an Xbox pad, a DualSense, a driverless pad. */
    private val xboxKeys = "7cdb000000000000 0 0 0 0"
    private val dualSenseKeys = "7fdb000000000000 0 0 0 0"
    private val numberedKeys = "fff00000000 0 0 0 0"
    private val sticksTriggersAndHat = "3003f"

    private fun layout(family: ControllerFamily, keys: String, abs: String = sticksTriggersAndHat) =
        JoystickLayout.of(family, JoystickLayout.bits(keys, 64), JoystickLayout.bits(abs, 64))

    @Test
    fun linuxButtonNumbersAreResolvedThroughTheDevicesCapabilities() {
        val dualSense = layout(ControllerFamily.PLAYSTATION, dualSenseKeys)
        assertEquals(
            listOf(GamepadButton.FACE_SOUTH, GamepadButton.FACE_EAST, GamepadButton.FACE_NORTH, GamepadButton.FACE_WEST, GamepadButton.SHOULDER_LEFT, GamepadButton.SHOULDER_RIGHT,
                GamepadButton.TRIGGER_LEFT, GamepadButton.TRIGGER_RIGHT, GamepadButton.SELECT, GamepadButton.START, GamepadButton.HOME, GamepadButton.STICK_LEFT, GamepadButton.STICK_RIGHT),
            (0..12).map { dualSense.button(it) },
        )
        assertNull(dualSense.button(13))

        // The Xbox driver has no trigger buttons, and names its X and Y after the positions they are not in.
        val xbox = layout(ControllerFamily.XBOX, xboxKeys)
        assertEquals(
            listOf(GamepadButton.FACE_SOUTH, GamepadButton.FACE_EAST, GamepadButton.FACE_WEST, GamepadButton.FACE_NORTH, GamepadButton.SHOULDER_LEFT, GamepadButton.SHOULDER_RIGHT,
                GamepadButton.SELECT, GamepadButton.START, GamepadButton.HOME, GamepadButton.STICK_LEFT, GamepadButton.STICK_RIGHT),
            (0..10).map { xbox.button(it) },
        )

        val numbered = layout(ControllerFamily.GENERIC, numberedKeys, abs = "30027")
        assertEquals(listOf(GamepadButton.FACE_WEST, GamepadButton.FACE_SOUTH, GamepadButton.FACE_EAST, GamepadButton.FACE_NORTH), (0..3).map { numbered.button(it) })
        assertEquals(GamepadButton.START, numbered.button(9))
    }

    @Test
    fun linuxEventsBecomeControllerState() {
        val state = JoystickState("js:js0", layout(ControllerFamily.XBOX, xboxKeys))
        fun button(number: Int, down: Boolean) = state.apply(if (down) 1 else 0, JoystickLayout.EVENT_BUTTON, number)
        fun axis(number: Int, value: Int) = state.apply(value.toShort(), JoystickLayout.EVENT_AXIS, number)

        // What a device reports when it is opened: everything at rest, flagged as initial state.
        state.apply(0, JoystickLayout.EVENT_BUTTON or JoystickLayout.EVENT_INIT, 0)
        state.apply(-32767, JoystickLayout.EVENT_AXIS or JoystickLayout.EVENT_INIT, 2)
        state.apply(-32767, JoystickLayout.EVENT_AXIS or JoystickLayout.EVENT_INIT, 5)
        assertTrue(state.snapshot().buttons.isEmpty(), "resting triggers are not pressed")

        button(0, down = true)
        axis(0, 16384)
        axis(1, -32767)
        axis(7, 32767) // the D-pad is a hat: axis 7 is its vertical
        axis(5, 20000) // right trigger pulled
        val snapshot = state.snapshot()
        assertEquals(setOf(GamepadButton.FACE_SOUTH, GamepadButton.DPAD_DOWN, GamepadButton.TRIGGER_RIGHT), snapshot.buttons)
        assertEquals(0.5f, snapshot.stickX, 0.01f)
        assertEquals(-1f, snapshot.stickY, 0.01f)
        assertEquals(ControllerFamily.XBOX, snapshot.family)

        // A tap that begins and ends between two polls is still seen once.
        button(0, down = false)
        button(1, down = true)
        button(1, down = false)
        assertTrue(GamepadButton.FACE_EAST in state.snapshot().buttons)
        assertTrue(GamepadButton.FACE_EAST !in state.snapshot().buttons)
    }

    @Test
    fun aControllersMotionSensorsAreNotAController() {
        // A DualSense also appears as a second joystick device that has axes (accelerometer, gyroscope) and no buttons.
        assertFalse(layout(ControllerFamily.PLAYSTATION, keys = "0", abs = "3f").isController)
        assertTrue(layout(ControllerFamily.PLAYSTATION, dualSenseKeys).isController)
        assertTrue(layout(ControllerFamily.GENERIC, numberedKeys, abs = "30027").isController)
    }

    @Test
    fun onAPadWithoutTriggersTheThirdAxesAreNotTriggers() {
        val state = JoystickState("js:js0", layout(ControllerFamily.GENERIC, numberedKeys, abs = "30027"))
        state.apply(32767, JoystickLayout.EVENT_AXIS, 2)
        state.apply(32767, JoystickLayout.EVENT_AXIS, 3)
        assertTrue(state.snapshot().buttons.isEmpty(), "that is its right stick")
    }

    @Test
    fun linuxControllersAreFoundAndReadFromDeviceNodes() {
        val root = Files.createTempDirectory("reflux-gamepads").also { it.toFile().deleteOnExit() }
        val devices = root.resolve("dev/input").createDirectories()
        val sysfs = root.resolve("sys/class/input").createDirectories()
        fun describe(node: String, name: String, vendor: String, keys: String) {
            val device = sysfs.resolve(node).resolve("device")
            device.resolve("id").createDirectories()
            device.resolve("capabilities").createDirectories()
            device.resolve("name").writeText("$name\n")
            device.resolve("id/vendor").writeText("$vendor\n")
            device.resolve("capabilities/key").writeText("$keys\n")
            device.resolve("capabilities/abs").writeText("$sticksTriggersAndHat\n")
        }
        fun events(vararg events: Triple<Int, Int, Int>): ByteArray {
            val buffer = ByteBuffer.allocate(events.size * JoystickLayout.EVENT_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            events.forEach { (value, type, number) -> buffer.putInt(0).putShort(value.toShort()).put(type.toByte()).put(number.toByte()) }
            return buffer.array()
        }
        describe("js0", "DualSense Wireless Controller", "054c", dualSenseKeys)
        devices.resolve("js0").writeBytes(events(Triple(1, JoystickLayout.EVENT_BUTTON, 1), Triple(32767, JoystickLayout.EVENT_AXIS, 0)))
        describe("js1", "Microsoft X-Box One pad", "045e", xboxKeys)
        devices.resolve("js1").writeBytes(events(Triple(1, JoystickLayout.EVENT_BUTTON, 2)))
        devices.resolve("event5").writeBytes(ByteArray(24)) // not a joystick node
        sysfs.resolve("js2").createDirectories() // a node without a description is skipped
        devices.resolve("js2").writeBytes(ByteArray(0))
        describe("js3", "DualSense Wireless Controller Motion Sensors", "054c", "0") // axes only: not a controller
        devices.resolve("js3").writeBytes(events(Triple(32767, JoystickLayout.EVENT_AXIS, 0)))

        LinuxGamepads(devices, sysfs).use { reader ->
            var states = emptyList<GamepadState>()
            val deadline = System.currentTimeMillis() + 5_000
            while (System.currentTimeMillis() < deadline && states.count { it.buttons.isNotEmpty() } < 2) {
                Thread.sleep(20)
                val polled = reader.poll()
                // Taps are reported once, so keep the snapshot that carried them.
                states = polled.map { now -> states.firstOrNull { it.id == now.id && it.buttons.isNotEmpty() } ?: now }
            }
            assertEquals(listOf("js:js0", "js:js1"), states.map { it.id })
            val dualSense = assertNotNull(states.firstOrNull { it.id == "js:js0" })
            assertEquals(ControllerFamily.PLAYSTATION, dualSense.family)
            assertEquals(setOf(GamepadButton.FACE_EAST), dualSense.buttons, "Circle")
            assertEquals(ControllerFamily.XBOX, states[1].family)
            assertEquals(setOf(GamepadButton.FACE_WEST), states[1].buttons, "X")
        }
        assertTrue(LinuxGamepads(Path.of(root.toString(), "missing"), sysfs).poll().isEmpty(), "a machine without input devices has no controllers")
    }

    @Test
    fun thePlatformReaderLoadsAndReportsWhatIsConnected() {
        val reader = GamepadInput.platformReader() ?: return
        reader.use {
            // Whatever is plugged in, reading must work and controllers at rest must not navigate.
            val states = it.poll()
            println("Connected controllers: " + states.joinToString { state -> "${state.id} (${state.family})" }.ifEmpty { "none" })
            assertTrue(states.all { state -> state.id.isNotBlank() })
        }
    }
}
