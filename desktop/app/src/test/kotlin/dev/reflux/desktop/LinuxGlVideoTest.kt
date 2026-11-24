package dev.reflux.desktop

import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B-084: the Linux integrated picture's choices that need no window. Which decoding it asks for by the OpenGL Skiko draws with
 * (measured 2026-11-24, CACHY-011), the engine's options (no window of its own), and that a component without Skiko's OpenGL
 * layer is never taken for one: the picture is then not drawn in the scene and the reason is given, so the log never
 * mistakes the fallback for the integrated picture.
 */
class LinuxGlVideoTest {
    @Test
    fun onMesaTheFrameIsDecodedByTheGpuThatDrawsIt() {
        val intel = "GraphicsApi: OPENGL\nOS: linux x64\nVendor: Intel\nModel: Mesa Intel(R) Graphics (ARL)"
        assertEquals("vaapi-copy,auto-copy-safe", DesktopPlatform.renderedHwdec(intel))
        assertEquals("vaapi-copy,auto-copy-safe", DesktopPlatform.renderedHwdec("Vendor: AMD\nModel: AMD Radeon Graphics (radeonsi)"))
        assertEquals("vaapi-copy,auto-copy-safe", DesktopPlatform.renderedHwdec(null), "unknown: never a second GPU first")
    }

    @Test
    fun onNvidiasOpenGLDecodingNeedsNoCopy() {
        assertEquals("auto-safe", DesktopPlatform.renderedHwdec("Vendor: NVIDIA Corporation\nModel: NVIDIA GeForce RTX 5060 Laptop GPU/PCIe/SSE2"))
    }

    @Test
    fun theEngineHasNoWindowOfItsOwn() {
        val options = DesktopPlatform.RENDERED_OPTIONS
        assertEquals("libmpv", options["vo"])
        assertFalse("wid" in options, "no window handed to the engine")
        assertFalse("hwdec" in options, "decoding is chosen per OpenGL, when the engine is made")
    }

    @Test
    fun aComponentWithoutSkikosOpenGLIsNotTakenForIt() {
        val panel = JPanel()
        assertNull(SkikoGl.layer(panel))
        assertNull(SkikoGl.directContext(panel))
        assertNull(SkikoGl.renderInfo(panel))
        assertFalse(SkikoGl.renderImmediately(panel))
        val video = LinuxGlVideo(panel)
        assertFalse(video.available)
        assertTrue("Skia layer" in video.unavailableReason() || "libGL" in video.unavailableReason(), video.unavailableReason())
    }

    @Test
    fun theLogNamesEachRendererApart() {
        assertNotEquals(LinuxGlVideo.RENDERER, LinuxGlVideo.FALLBACK)
        assertEquals("linux-mpv-gl-integrated", LinuxGlVideo.RENDERER)
        assertEquals("linux-x11-shape-fallback", LinuxGlVideo.FALLBACK)
        assertEquals(SkikoGl.TESTED_VERSION, SkikoGl.version, "Skiko changed: the reflection in SkikoGl must be verified again")
    }
}
