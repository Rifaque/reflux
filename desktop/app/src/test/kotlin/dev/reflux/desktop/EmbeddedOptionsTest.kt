package dev.reflux.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * B-058: on Linux the engine draws into an X11 window, so its GPU output is held to X11 contexts; left to choose, it took a
 * Wayland surface of its own on a GNOME Wayland session and covered the player's controls.
 */
class EmbeddedOptionsTest {
    @Test
    fun onLinuxTheEngineIsHeldToX11Contexts() {
        val options = DesktopPlatform.embeddedOptions(42L, linux = true, guarded = true)
        assertEquals("x11egl,x11,x11vk", options["gpu-context"])
        assertEquals("42", options["wid"])
        assertEquals("yes", options["force-window"])
        assertEquals("no", options["input-cursor"])
    }

    @Test
    fun elsewhereTheEngineChoosesItsOwnContext() {
        val options = DesktopPlatform.embeddedOptions(7L, linux = false, guarded = false)
        assertFalse("gpu-context" in options)
        assertFalse("force-window" in options)
        assertEquals("7", options["wid"])
        assertEquals("0", options["osd-level"])
    }

    @Test
    fun withoutTheErrorGuardLinuxStillGetsX11Contexts() {
        val options = DesktopPlatform.embeddedOptions(1L, linux = true, guarded = false)
        assertEquals("x11egl,x11,x11vk", options["gpu-context"])
        assertFalse("force-window" in options)
    }
}
