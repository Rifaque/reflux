package dev.reflux.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What the systems say about text size, as they say it. A setting that cannot be read is simply not there. */
class DesktopAccessibilityTest {
    @Test
    fun windowsSaysAPercentage() {
        val output = "\r\nHKEY_CURRENT_USER\\SOFTWARE\\Microsoft\\Accessibility\r\n    TextScaleFactor    REG_DWORD    0x96\r\n\r\n"
        assertEquals(1.5f, DesktopAccessibility.windowsTextScale(output))
    }

    @Test
    fun windowsWithoutTheSettingSaysNothing() {
        assertNull(DesktopAccessibility.windowsTextScale("ERROR: The system was unable to find the specified registry key or value."))
    }

    @Test
    fun gnomeSaysAFactor() {
        assertEquals(1.25f, DesktopAccessibility.linuxTextScale("1.25\n"))
    }

    @Test
    fun whatIsNotAPlausibleSizeIsIgnored() {
        assertNull(DesktopAccessibility.linuxTextScale("No such schema"))
        assertNull(DesktopAccessibility.linuxTextScale("40"))
        assertNull(DesktopAccessibility.windowsTextScale("REG_DWORD    0xFFFF"))
    }

    @Test
    fun readingTheRealSystemNeverFails() {
        // Whatever this machine says, or does not.
        DesktopAccessibility.read()
    }
}
