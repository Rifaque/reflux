package dev.reflux.desktop

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** B-084: the holes cut in the Linux video surface for the controls drawn beneath it (LinuxVideoShape). */
class LinuxVideoShapeTest {
    private val whole = Rect(0f, 0f, 1920f, 1080f)

    @Test
    fun aControlIsCutWhereItIsInTheSurfacesPixels() {
        assertEquals(listOf(IntBox(0, 0, 1920, 116)), videoHoles(1920, 1080, whole, listOf(Rect(0f, 0f, 1920f, 116f))))
    }

    @Test
    fun aHoleIsGrownToWholePixelsSoNothingDrawnIsLeftUnderThePicture() {
        assertEquals(listOf(IntBox(10, 20, 31, 11)), videoHoles(1920, 1080, whole, listOf(Rect(10.4f, 20.6f, 40.2f, 30.1f))))
    }

    @Test
    fun aSurfaceThatIsNotTheWholeWindowCutsInItsOwnPixels() {
        // The mini-player's dock: the surface at (1500, 800), 320 x 180; its loader in the middle of it.
        val dock = Rect(1500f, 800f, 1820f, 980f)
        assertEquals(listOf(IntBox(146, 76, 28, 28)), videoHoles(320, 180, dock, listOf(Rect(1646f, 876f, 1674f, 904f))))
    }

    @Test
    fun aSurfaceScaledByTheToolkitIsCutInDevicePixels() {
        // Laid out at 960 x 540 window pixels, the surface is 1920 x 1080 device pixels.
        assertEquals(listOf(IntBox(0, 1000, 1920, 80)), videoHoles(1920, 1080, Rect(0f, 0f, 960f, 540f), listOf(Rect(0f, 500f, 960f, 540f))))
    }

    @Test
    fun whatIsOutsideTheSurfaceOrHasNoAreaIsNotAHole() {
        val holes = videoHoles(1920, 1080, whole, listOf(Rect(2000f, 0f, 2100f, 50f), Rect(100f, 100f, 100f, 200f), Rect(-50f, -50f, 30f, 40f)))
        assertEquals(listOf(IntBox(0, 0, 30, 40)), holes, "only the part inside the surface, kept to it")
        assertTrue(videoHoles(0, 0, whole, listOf(Rect(0f, 0f, 10f, 10f))).isEmpty(), "no surface, no holes")
    }

    @Test
    fun onLinuxThePlayerIsOneWindow() {
        assertEquals(System.getProperty("os.name").startsWith("Linux"), SHAPED_VIDEO)
    }
}
