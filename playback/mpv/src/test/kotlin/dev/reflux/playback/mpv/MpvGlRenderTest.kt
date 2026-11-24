package dev.reflux.playback.mpv

import dev.reflux.core.playback.DisplayCapabilities
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B-084: libmpv's render API as Reflux binds it (MpvGlRender). Without an OpenGL context (here there is none: no function is
 * found) libmpv refuses the render context, the reason is kept and nothing crashes, and the engine still closes; that refusal is
 * what sends the Linux player to its native-window fallback. Drawing itself needs a real window's OpenGL context and is
 * covered by the desktop's window tests (HyprlandWalkTest, IntegratedPlayerLifecycleTest).
 */
class MpvGlRenderTest {
    private val capabilities = DesktopCapabilities.of(DisplayCapabilities(1920, 1080))

    private fun needsEngine() {
        val what = "libmpv not installed"
        if (System.getenv("REFLUX_REQUIRE_ENGINE_TESTS") == "true") assertTrue(MpvPlayer.isAvailable, what) else assumeTrue(what, MpvPlayer.isAvailable)
    }

    @Test
    fun withoutOpenGLTheRenderContextIsRefusedAndTheEngineStillCloses() {
        needsEngine()
        val player = MpvPlayer.create(capabilities, mapOf("vo" to "libmpv", "ao" to "null"))!!
        var asked = 0
        var updates = 0
        val render = MpvGlRender.create(player, { _, _ -> asked++; null }, { updates++ })
        assertNull(render, "no OpenGL, no render context")
        assertTrue(asked > 0, "libmpv looked for OpenGL's functions through Reflux's lookup")
        assertTrue(MpvPlayer.lastFailure.orEmpty().contains("render context"), "the reason is kept: ${MpvPlayer.lastFailure}")
        assertEquals(0, updates, "nothing to render was announced")
        player.close()
        assertNull(player.property("hwdec-current"), "a closed engine answers nothing")
    }

    @Test
    fun theEnginesPropertiesCanBeReadForDiagnostics() {
        needsEngine()
        MpvPlayer.create(capabilities, mapOf("vo" to "null", "ao" to "null")).let { it!! }.use { player ->
            assertEquals("null", player.property("vo"))
            assertTrue(player.property("mpv-version").orEmpty().startsWith("mpv"), "${player.property("mpv-version")}")
        }
    }
}
