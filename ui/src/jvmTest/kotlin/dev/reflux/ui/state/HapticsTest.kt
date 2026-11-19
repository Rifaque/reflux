package dev.reflux.ui.state

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The haptics gateway (F-001, F-002): its defaults, its preferences, its limits, and that nothing in Reflux reaches a
 * vibrator or a controller's motors any other way.
 */
class HapticsTest {
    private class Device(override val available: Boolean = true) : DeviceHaptics {
        val felt = mutableListOf<HapticEvent>()
        override fun perform(event: HapticEvent) {
            felt += event
        }
    }

    private class Pad(var hasMotors: Boolean = true) : ControllerRumble {
        val pulses = mutableListOf<Pair<Float, Long>>()
        var stops = 0
        override fun available() = hasMotors
        override fun rumble(strength: Float, durationMs: Long) {
            pulses += strength to durationMs
        }
        override fun stop() {
            stops++
        }
    }

    private var clock = 0L

    @Test
    fun interfaceHapticsAreOffUntilTurnedOnAndThenOnlyForTheirEvents() {
        val device = Device()
        val haptics = Haptics({ device }, { null }, { clock })
        assertFalse(haptics.interfaceOn, "off by default")
        HapticEvent.entries.forEach { haptics.touch(it); clock += 1_000 }
        assertTrue(device.felt.isEmpty(), "no vibration of any kind before opting in")
        haptics.interfaceOn = true
        haptics.touch(HapticEvent.CONFIRM)
        assertEquals(listOf(HapticEvent.CONFIRM), device.felt)
        // A controller's events never make the device vibrate.
        clock += 1_000
        haptics.controller(HapticEvent.BOUNDARY)
        assertEquals(1, device.felt.size)
    }

    @Test
    fun rumbleIsOnByDefaultAndOffMeansNone() {
        val pad = Pad()
        val haptics = Haptics({ null }, { pad }, { clock })
        assertTrue(haptics.rumbleOn, "on by default")
        haptics.controller(HapticEvent.BOUNDARY)
        assertEquals(1, pad.pulses.size)
        val (strength, duration) = pad.pulses.single()
        assertTrue(strength <= 0.6f && duration <= 150L, "short and light: $strength for $duration ms")
        haptics.rumbleOn = false
        clock += 1_000
        HapticEvent.entries.forEach { haptics.controller(it) }
        assertEquals(1, pad.pulses.size, "off: nothing")
    }

    @Test
    fun repeatsAreFeltOnceInAWhileNotEveryTime() {
        val pad = Pad()
        val haptics = Haptics({ null }, { pad }, { clock })
        // A held direction against the end of a row repeats about every 110 ms.
        repeat(20) {
            haptics.controller(HapticEvent.BOUNDARY)
            clock += 110
        }
        assertTrue(pad.pulses.size in 4..6, "${pad.pulses.size} rumbles over 2.2 s of repeats")
    }

    @Test
    fun unsupportedHardwareIsAQuietNoOp() {
        val device = Device(available = false)
        val pad = Pad(hasMotors = false)
        val haptics = Haptics({ device }, { pad }, { clock })
        haptics.interfaceOn = true
        HapticEvent.entries.forEach { haptics.touch(it); haptics.controller(it) }
        assertTrue(device.felt.isEmpty() && pad.pulses.isEmpty())
        assertFalse(haptics.interfaceAvailable)
        // No platform at all.
        val none = Haptics({ null }, { null }, { clock })
        none.interfaceOn = true
        HapticEvent.entries.forEach { none.touch(it); none.controller(it) }
        none.stopRumble()
    }

    @Test
    fun aFailingPlatformNeverReachesTheCaller() {
        val haptics = Haptics({ object : DeviceHaptics { override val available = true; override fun perform(event: HapticEvent) = error("vibrator gone") } }, {
            object : ControllerRumble {
                override fun available() = true
                override fun rumble(strength: Float, durationMs: Long) = error("unplugged")
                override fun stop() = error("unplugged")
            }
        }, { clock })
        haptics.interfaceOn = true
        haptics.touch(HapticEvent.ERROR)
        haptics.controller(HapticEvent.ERROR)
        haptics.stopRumble()
    }

    @Test
    fun stoppingReachesTheController() {
        val pad = Pad()
        val haptics = Haptics({ null }, { pad }, { clock })
        haptics.controller(HapticEvent.ERROR)
        haptics.stopRumble()
        assertEquals(1, pad.stops)
    }

    /**
     * Every way to make something vibrate is named here, and only the gateway's platform halves may use one: the Android
     * shell's `AndroidHaptics.kt` and the desktop's controller readers. A feature that reaches a vibrator, a controller's
     * motors, or the toolkit's haptics directly would bypass the viewer's choice, and fails this.
     */
    @Test
    fun nothingButTheGatewayReachesAVibrator() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
        val forbidden = listOf(
            Regex("""performHapticFeedback\("""), Regex("""\bVibrator\b"""), Regex("""VibratorManager"""), Regex("""\.vibrate\("""),
            Regex("""VibrationEffect"""), Regex("""XInputSetState"""), Regex("""LocalHapticFeedback\.current"""),
        )
        val allowed = setOf("AndroidHaptics.kt", "WindowsGamepads.kt", "HapticsTest.kt")
        // The toolkit's own haptics, routed into the gateway: a declaration of the hook, not a use of a vibrator.
        val routed = Regex("override fun performHapticFeedback\\(hapticFeedbackType")
        val sources = listOf("ui/src", "android/app/src/main", "desktop/app/src/main", "core/src", "library/src", "playback")
            .map { File(root, it) }.filter { it.exists() }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" && "/build/" !in it.invariantSeparatorsPath }.toList() }
        val offenders = sources.filter { it.name !in allowed }.flatMap { file ->
            file.readLines().withIndex().filter { (_, line) -> !line.trimStart().startsWith("*") && !line.trimStart().startsWith("//") && !routed.containsMatchIn(line) && forbidden.any { it.containsMatchIn(line) } }
                .map { (index, line) -> "${file.relativeTo(root)}:${index + 1}: ${line.trim()}" }
        }
        assertTrue(sources.size > 100, "the sources were found (${sources.size})")
        assertEquals(emptyList(), offenders, "only the haptics gateway's platform halves may make anything vibrate")
    }
}
