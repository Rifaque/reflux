package dev.reflux.desktop

import dev.reflux.core.input.InputContext
import dev.reflux.core.input.InputMapping
import dev.reflux.core.input.Key
import dev.reflux.core.input.SemanticAction
import org.junit.Assume.assumeTrue
import java.awt.Component
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopShellTest {
    /** A component that exists without a display, so these tests also run on headless machines. */
    private val source = object : Component() {}

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

    @Test
    fun theBackButtonOfAMouseIsOnlyTrustedWhereItsNumberIsKnown() {
        val press = runCatching { MouseEvent(source, MouseEvent.MOUSE_PRESSED, 0L, 0, 10, 10, 1, false, 4) }.getOrNull()
        assumeTrue("needs a pointing device with extra buttons", press != null)
        assertTrue(press!!.isBackButtonPress("Windows 11"))
        assertFalse(press.isBackButtonPress("Linux"), "elsewhere that number can be a scroll wheel")
        assertFalse(MouseEvent(source, MouseEvent.MOUSE_PRESSED, 0L, 0, 10, 10, 1, false, MouseEvent.BUTTON1).isBackButtonPress("Windows 11"))
    }

    @Test
    fun onLinuxTheDesktopsOwnFolderDialogIsUsed() {
        val asked = mutableListOf<String>()
        fun desktop(name: String, answers: Map<String, Pair<Int, String>?>) =
            DesktopFolderDialog.show("Choose", desktop = name, home = "/home/me") { command ->
                asked += command.first()
                answers[command.first()]
            }

        // GNOME and most desktops ship zenity; it prints the chosen folder.
        val chosen = assertNotNull(desktop("ubuntu:GNOME", mapOf("zenity" to (0 to "/media/Movies\n"))))
        assertEquals(Path.of("/media/Movies"), chosen.folder)
        assertEquals(listOf("zenity"), asked.toList())

        // KDE has its own.
        asked.clear()
        assertEquals(Path.of("/srv/tv"), desktop("KDE", mapOf("kdialog" to (0 to "/srv/tv"), "zenity" to (0 to "/wrong")))?.folder)
        assertEquals(listOf("kdialog"), asked.toList())

        // Closing the dialog is an answer too: nothing was chosen, and no second dialog appears.
        asked.clear()
        val cancelled = assertNotNull(desktop("GNOME", mapOf("zenity" to (1 to ""), "kdialog" to (0 to "/wrong"))))
        assertNull(cancelled.folder)
        assertEquals(listOf("zenity"), asked.toList())

        // A helper that is missing or cannot open a window is passed over; with none left, the toolkit's chooser is used.
        asked.clear()
        assertEquals(Path.of("/data"), desktop("XFCE", mapOf("zenity" to (255 to ""), "kdialog" to (0 to "/data")))?.folder)
        assertNull(desktop("sway", emptyMap()))
    }
}
