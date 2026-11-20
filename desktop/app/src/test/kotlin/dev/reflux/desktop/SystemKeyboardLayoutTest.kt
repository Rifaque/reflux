package dev.reflux.desktop

import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The system keyboard layout as the Reflux keyboard's desk picture reads it (F-P4, owner's decision D3). */
class SystemKeyboardLayoutTest {
    @Test
    fun keysymsBecomeText() {
        assertEquals("q", Keysyms.text(0x71))
        assertEquals("é", Keysyms.text(0xe9))
        assertEquals("ß", Keysyms.text(0xdf))
        assertEquals("€", Keysyms.text(0x10020ac), "a Unicode keysym")
        assertEquals("^", Keysyms.text(0xfe52), "a dead key types its spacing accent")
        assertNull(Keysyms.text(0xff0d), "Return is not text")
    }

    /** On Windows: whatever the layout, every letter position of the board gives a character, or the reader says it cannot. */
    @Test
    fun onWindowsTheActiveLayoutIsReadOrRefused() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val keymap = SystemKeyboardLayout.read() ?: return
        assertEquals(listOf(13, 13, 11, 10), keymap.rows.map { it.size })
        assertNotNull(keymap.rows[1][0]?.plain, "the top letter row's first key types something")
        println("windows layout ${keymap.name}: ${keymap.rows.drop(1).joinToString(" | ") { row -> row.joinToString("") { it?.plain ?: "?" } }}")
    }

    /**
     * On Linux with a display whose XKB layout the run set (`REFLUX_XKB_TEST=de` after `setxkbmap de`): German QWERTZ's z
     * and y, and ä and ö where US has ' and ;.
     */
    @Test
    fun onLinuxAGermanLayoutIsReadAsQwertz() {
        assumeTrue(System.getProperty("os.name").startsWith("Linux") && System.getenv("REFLUX_XKB_TEST") == "de")
        val keymap = assertNotNull(SystemKeyboardLayout.read(), "the X keyboard map")
        assertEquals("z", keymap.rows[1][5]?.plain, "QWERTZ")
        assertEquals("y", keymap.rows[3][0]?.plain)
        assertEquals("ö", keymap.rows[2][9]?.plain)
        assertEquals("ä", keymap.rows[2][10]?.plain)
        assertEquals("!", keymap.rows[0][1]?.shifted)
        println("xkb layout ${keymap.name}: ${keymap.rows.drop(1).joinToString(" | ") { row -> row.joinToString("") { it?.plain ?: "?" } }}")
    }
}
