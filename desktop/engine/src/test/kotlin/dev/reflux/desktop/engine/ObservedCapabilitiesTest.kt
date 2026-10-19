package dev.reflux.desktop.engine

import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.VideoCodec
import dev.reflux.playback.mpv.DecodeEvidence
import dev.reflux.playback.mpv.DecodeKey
import dev.reflux.playback.mpv.DecodeObservation
import dev.reflux.playback.mpv.HardwareDecode
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the desktop says it can show and decode, from readings written down here: two monitors that differ, a
 * window that moves between them, a display that cannot be read. No real screen is involved, except in the
 * last test, which reads this machine's own on Windows.
 */
class ObservedCapabilitiesTest {
    /** Two monitors: a 1080p SDR one where Reflux starts, and a 4K one that shows HDR. Which one the window is on is [on]. */
    private class TwoMonitors {
        val primary = ObservedDisplay(1920, 1080, HdrState.UNSUPPORTED)
        val secondary = ObservedDisplay(3840, 2160, HdrState.ON)
        var on: ObservedDisplay? = primary
        var reads = 0
        val reader = DisplayReader { reads++; on }
    }

    private val reports = mutableListOf<String>()

    @Test
    fun theDisplayIsTheOneTheWindowIsOnAndFollowsIt() {
        val monitors = TwoMonitors()
        val capabilities = ObservedCapabilities(monitors.reader) { reports += it }
        var changes = 0
        capabilities.onChange = { changes++ }

        assertTrue(capabilities.observeDisplay(null))
        assertEquals(1920, capabilities.current().display.maxWidth)

        monitors.on = monitors.secondary
        assertTrue(capabilities.observeDisplay(null), "moved to the other monitor")
        assertEquals(3840 to 2160, capabilities.current().display.let { it.maxWidth to it.maxHeight })
        assertEquals(HdrState.ON, capabilities.currentDisplay.hdr)

        assertFalse(capabilities.observeDisplay(null), "read again where it is: nothing new")
        assertEquals(2, changes, "told only of what changed")

        monitors.on = monitors.primary
        assertTrue(capabilities.observeDisplay(null), "and back")
        assertEquals(1080, capabilities.current().display.maxHeight)
        assertEquals(3, changes)
        assertTrue(reports.any { it.startsWith("Display for playback: 3840x2160") })
    }

    @Test
    fun anHdrDisplayIsNotClaimedWhileTheEngineSendsSdr() {
        val monitors = TwoMonitors().apply { on = secondary }
        val capabilities = ObservedCapabilities(monitors.reader) { reports += it }
        capabilities.observeDisplay(null)
        assertEquals(setOf(DynamicRange.SDR), capabilities.current().display.dynamicRanges)
        assertTrue(capabilities.current().display.canToneMap)
        assertTrue(reports.single().contains("HDR on (played as SDR"), reports.single())
    }

    @Test
    fun aDisplayThatCannotBeReadIsUnknownNotAGuess() {
        val monitors = TwoMonitors().apply { on = null }
        val capabilities = ObservedCapabilities(monitors.reader)
        assertFalse(capabilities.observeDisplay(null), "unknown from the start: nothing changed")
        assertNull(capabilities.current().display.maxWidth)
        assertNull(capabilities.current().display.maxHeight)
        val failing = ObservedCapabilities({ error("no screen") })
        failing.observeDisplay(null)
        assertNull(failing.current().display.maxHeight, "a reader that fails is unknown, not an error")
    }

    @Test
    fun whatIsLearnedOfDecodingReachesBestVersion() {
        val capabilities = ObservedCapabilities(TwoMonitors().reader) { reports += it }
        var changes = 0
        capabilities.onChange = { changes++ }
        assertTrue(capabilities.current().videoDecoders.none { it.hardware == true }, "nothing known before the probe")

        val probed = DecodeEvidence(setOf(VideoCodec.H264, VideoCodec.HEVC), mapOf(DecodeKey(VideoCodec.HEVC, 10) to HardwareDecode.Yes(3840, 2160)))
        assertTrue(capabilities.probed(probed, elapsedMs = 1_234))
        assertEquals(true, capabilities.current().decoderFor(VideoCodec.HEVC)?.hardware)
        assertTrue(reports.last().startsWith("Hardware decoding (1234 ms to find out): HEVC 10-bit hardware"))

        assertTrue(capabilities.decoded(DecodeObservation(VideoCodec.HEVC, 10, 1920, 1080, hardware = false)), "playback disagreed")
        assertEquals(HardwareDecode.No, capabilities.currentDecode.hardware[DecodeKey(VideoCodec.HEVC, 10)])
        assertFalse(capabilities.decoded(DecodeObservation(VideoCodec.HEVC, 10, 1920, 1080, hardware = false)), "the same again changes nothing")
        assertEquals(2, changes)
    }

    @Test
    fun windowsColourInformationIsReadAsHdrOnlyWhenItIsHdr() {
        assertEquals(HdrState.ON, HdrState.fromColorInfo2(supported = true, activeColorMode = 2))
        assertEquals(HdrState.OFF, HdrState.fromColorInfo2(supported = true, activeColorMode = 0))
        assertEquals(HdrState.UNSUPPORTED, HdrState.fromColorInfo2(supported = false, activeColorMode = 1), "wide colour on an SDR display is not HDR")
        assertEquals(HdrState.ON, HdrState.fromColorInfo(supported = true, enabled = true, wideColorEnforced = false, forceDisabled = false))
        assertEquals(HdrState.OFF, HdrState.fromColorInfo(supported = true, enabled = false, wideColorEnforced = false, forceDisabled = false))
        assertEquals(HdrState.UNSUPPORTED, HdrState.fromColorInfo(supported = true, enabled = true, wideColorEnforced = true, forceDisabled = false), "automatic colour management")
        assertEquals(HdrState.OFF, HdrState.fromColorInfo(supported = true, enabled = true, wideColorEnforced = false, forceDisabled = true))
        assertEquals(HdrState.UNSUPPORTED, HdrState.fromColorInfo(supported = false, enabled = false, wideColorEnforced = false, forceDisabled = false))
    }

    @Test
    fun thisWindowsMachinesOwnMonitorCanBeRead() {
        assumeTrue("Windows only", System.getProperty("os.name").startsWith("Windows"))
        val monitor = assertNotNull(WindowsDisplays().primary())
        assertTrue(monitor.width > 0 && monitor.height > 0, "a real mode")
        assertTrue(monitor.device.startsWith("\\\\.\\"), "a GDI display name")
        println("This machine's monitor: ${monitor.width}x${monitor.height}, HDR ${monitor.hdr}")
    }
}
