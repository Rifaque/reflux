package dev.reflux.desktop

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * B-002: on Windows the toolkit starts with Java2D's own Direct3D pipeline off, which is what keeps the player's controls
 * window sized right on a display whose scale differs from the primary's (the reasons are on [prepareToolkit]; the walk is
 * in VERIFICATION.md). These check the decision itself; that it is made before the toolkit starts is what `main` and the lazy
 * `HiddenCursor` in Main.kt guarantee.
 */
class ToolkitPreparationTest {
    private var saved: String? = null

    @BeforeTest
    fun save() {
        saved = System.getProperty(JAVA2D_D3D)
        System.clearProperty(JAVA2D_D3D)
    }

    @AfterTest
    fun restore() {
        saved?.let { System.setProperty(JAVA2D_D3D, it) } ?: System.clearProperty(JAVA2D_D3D)
    }

    @Test
    fun onWindowsJava2dsDirect3dPipelineIsTurnedOff() {
        prepareToolkit("Windows 11")
        assertEquals("false", System.getProperty(JAVA2D_D3D))
    }

    @Test
    fun aChoiceGivenOnTheCommandLineIsKept() {
        System.setProperty(JAVA2D_D3D, "true")
        prepareToolkit("Windows 11")
        assertEquals("true", System.getProperty(JAVA2D_D3D))
    }

    @Test
    fun elsewhereNothingIsChanged() {
        prepareToolkit("Linux")
        assertNull(System.getProperty(JAVA2D_D3D))
        prepareToolkit("Mac OS X")
        assertNull(System.getProperty(JAVA2D_D3D))
    }
}
