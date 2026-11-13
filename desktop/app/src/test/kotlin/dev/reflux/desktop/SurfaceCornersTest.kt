package dev.reflux.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The region cut into the dock picture's window: its radius in device pixels, whatever the display's scale. */
class SurfaceCornersTest {
    @Test
    fun theRadiusIsTheDocksPictureRadiusInTheDisplaysOwnPixels() {
        // 16 dp at 100%, 125%, 150%, 200% (96, 120, 144, 192 dpi).
        assertEquals(16, cornerRegion(640, 360, 96, 16f)?.radiusPx)
        assertEquals(20, cornerRegion(800, 450, 120, 16f)?.radiusPx)
        assertEquals(24, cornerRegion(960, 540, 144, 16f)?.radiusPx)
        assertEquals(32, cornerRegion(1280, 720, 192, 16f)?.radiusPx)
        assertEquals(CornerRegion(800, 450, 20), cornerRegion(800, 450, 120, 16f))
    }

    @Test
    fun thereIsNothingToCutAcrossTheWholeWindowOrWithoutASurface() {
        assertNull(cornerRegion(1920, 1080, 96, 0f), "the whole window is square")
        assertNull(cornerRegion(0, 360, 96, 16f))
        assertNull(cornerRegion(640, 0, 96, 16f))
    }

    @Test
    fun aRadiusNeverExceedsHalfTheShorterSide() {
        assertEquals(10, cornerRegion(400, 20, 96, 16f)?.radiusPx)
        assertEquals(CornerRegion(40, 40, 20), cornerRegion(40, 40, 192, 16f))
    }

    @Test
    fun aDpiBelowTheBaselineIsTakenAsTheBaseline() {
        assertEquals(16, cornerRegion(640, 360, 0, 16f)?.radiusPx)
    }
}
