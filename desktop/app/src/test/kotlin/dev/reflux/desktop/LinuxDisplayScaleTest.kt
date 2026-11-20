package dev.reflux.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** B-063: the scale a Linux desktop asks for, which Java's X11 toolkit leaves out, applied once and only once. */
class LinuxDisplayScaleTest {
    @Test
    fun theDesktopsFractionalScaleIsAppliedOnTopOfAWholeToolkitScale() {
        assertEquals(1.5f, LinuxDisplayScale.contentScale(xftDpi = 144.0, toolkitScale = 1.0), "150%: Java drew it at 100%")
        assertEquals(1.25f, LinuxDisplayScale.contentScale(xftDpi = 120.0, toolkitScale = 1.0))
        assertEquals(1.75f, LinuxDisplayScale.contentScale(xftDpi = 168.0, toolkitScale = 1.0))
        assertEquals(2f, LinuxDisplayScale.contentScale(xftDpi = 192.0, toolkitScale = 1.0))
        // With GDK_SCALE=2, the toolkit's part is not added again.
        assertEquals(1f, LinuxDisplayScale.contentScale(xftDpi = 192.0, toolkitScale = 2.0), "never scaled twice")
        assertEquals(1.5f, LinuxDisplayScale.contentScale(xftDpi = 288.0, toolkitScale = 2.0))
    }

    @Test
    fun nothingIsAddedAt100PercentOrWhenTheDesktopSaysNothing() {
        assertEquals(1f, LinuxDisplayScale.contentScale(xftDpi = 96.0, toolkitScale = 1.0))
        assertEquals(1f, LinuxDisplayScale.contentScale(xftDpi = 99.0, toolkitScale = 1.0), "under 5% more is noise")
        assertEquals(1f, LinuxDisplayScale.contentScale(xftDpi = null, toolkitScale = 1.0))
        assertEquals(1f, LinuxDisplayScale.contentScale(xftDpi = 72.0, toolkitScale = 1.0), "never smaller than the toolkit's")
    }

    @Test
    fun gnomesLargeTextIsTakenOutBecauseRefluxAlreadyAppliesItToText() {
        // GNOME at 100% with Large Text: Xft.dpi 120 is the text factor alone; the layout stays at 100%.
        assertEquals(1f, LinuxDisplayScale.contentScale(xftDpi = 120.0, toolkitScale = 1.0, textScale = 1.25))
        assertEquals(1.5f, LinuxDisplayScale.contentScale(xftDpi = 180.0, toolkitScale = 1.0, textScale = 1.25))
    }

    @Test
    fun anOverrideIsTheWholeScale_roundedToTheDesktopsSteps() {
        assertEquals(1.5f, LinuxDisplayScale.contentScale(xftDpi = null, toolkitScale = 1.0, override = 1.5f))
        assertEquals(1f, LinuxDisplayScale.contentScale(xftDpi = 144.0, toolkitScale = 1.0, override = 1f))
        assertEquals(1.25f, LinuxDisplayScale.contentScale(xftDpi = 121.0, toolkitScale = 1.0), "to the nearest eighth")
        assertEquals(4f, LinuxDisplayScale.contentScale(xftDpi = 960.0, toolkitScale = 1.0), "at most 4")
    }

    @Test
    fun xftDpiIsReadFromTheResources() {
        assertEquals(144.0, LinuxDisplayScale.parseXftDpi("Xcursor.size:\t24\nXft.dpi:\t144\nXft.antialias:\t1\n"))
        assertNull(LinuxDisplayScale.parseXftDpi("Xcursor.size:\t24\n"))
        assertNull(LinuxDisplayScale.parseXftDpi("Xft.dpi:\tnope\n"))
    }
}
