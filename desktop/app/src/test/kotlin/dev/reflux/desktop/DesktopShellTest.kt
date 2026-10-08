package dev.reflux.desktop

import dev.reflux.core.input.InputContext
import dev.reflux.core.input.InputMapping
import dev.reflux.core.input.Key
import dev.reflux.core.input.SemanticAction
import org.junit.Assume.assumeTrue
import java.awt.Canvas
import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopShellTest {
    private val source = Canvas()

    private fun press(code: Int, modifiers: Int = 0) = KeyEvent(source, KeyEvent.KEY_PRESSED, 0L, modifiers, code, KeyEvent.CHAR_UNDEFINED)

    @Test
    fun keyboardKeysAreNormalizedBeforeReachingTheProduct() {
        assertEquals(Key.ARROW_LEFT, press(KeyEvent.VK_LEFT).toRefluxKey())
        assertEquals(Key.SPACE, press(KeyEvent.VK_SPACE).toRefluxKey())
        assertEquals(Key.ESCAPE, press(KeyEvent.VK_ESCAPE).toRefluxKey())
        assertEquals(Key.PERIOD, press(KeyEvent.VK_PERIOD).toRefluxKey())
        assertNull(press(KeyEvent.VK_Q).toRefluxKey(), "keys without a meaning are left to the toolkit")
    }

    @Test
    fun f11IsFullscreenLikeEveryDesktopApp() {
        val key = press(KeyEvent.VK_F11).toRefluxKey()
        assertEquals(SemanticAction.FULLSCREEN, key?.let { InputMapping.map(it, InputContext.BROWSE) })
        assertEquals(SemanticAction.FULLSCREEN, key?.let { InputMapping.map(it, InputContext.PLAYBACK) })
    }

    @Test
    fun systemShortcutsAreNeverIntercepted() {
        assertNull(press(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK).toRefluxKey())
        assertNull(press(KeyEvent.VK_LEFT, KeyEvent.ALT_DOWN_MASK).toRefluxKey())
        assertNull(press(KeyEvent.VK_M, KeyEvent.META_DOWN_MASK).toRefluxKey())
    }

    @Test
    fun theNativeFolderDialogIsAvailableOnWindows() {
        assumeTrue("Windows only", System.getProperty("os.name").startsWith("Windows"))
        assertTrue(WindowsFolderDialog.available())
    }
}
