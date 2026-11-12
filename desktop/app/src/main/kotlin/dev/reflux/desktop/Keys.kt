package dev.reflux.desktop

import dev.reflux.core.input.Key
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent

/**
 * Normalizes an AWT key press into a Reflux [Key], or null for keys Reflux has no meaning for.
 *
 * Presses with Ctrl, Alt, or Meta held are left alone so system and toolkit shortcuts keep working, except Reflux's own
 * destination shortcuts (Ctrl or Command with 1 to 6).
 */
internal fun KeyEvent.toRefluxKey(): Key? {
    // Ctrl (Command on a Mac) with 1 to 6: the global destinations, as tabs are reached in a browser.
    if ((isControlDown || isMetaDown) && !isAltDown && !isShiftDown) {
        return when (keyCode) {
            KeyEvent.VK_1, KeyEvent.VK_NUMPAD1 -> Key.SHORTCUT_1
            KeyEvent.VK_2, KeyEvent.VK_NUMPAD2 -> Key.SHORTCUT_2
            KeyEvent.VK_3, KeyEvent.VK_NUMPAD3 -> Key.SHORTCUT_3
            KeyEvent.VK_4, KeyEvent.VK_NUMPAD4 -> Key.SHORTCUT_4
            KeyEvent.VK_5, KeyEvent.VK_NUMPAD5 -> Key.SHORTCUT_5
            KeyEvent.VK_6, KeyEvent.VK_NUMPAD6 -> Key.SHORTCUT_6
            else -> null
        }
    }
    if (isControlDown || isAltDown || isMetaDown) return null
    return when (keyCode) {
        KeyEvent.VK_UP, KeyEvent.VK_KP_UP -> Key.ARROW_UP
        KeyEvent.VK_DOWN, KeyEvent.VK_KP_DOWN -> Key.ARROW_DOWN
        KeyEvent.VK_LEFT, KeyEvent.VK_KP_LEFT -> Key.ARROW_LEFT
        KeyEvent.VK_RIGHT, KeyEvent.VK_KP_RIGHT -> Key.ARROW_RIGHT
        KeyEvent.VK_ENTER -> Key.ENTER
        KeyEvent.VK_SPACE -> Key.SPACE
        KeyEvent.VK_ESCAPE -> Key.ESCAPE
        KeyEvent.VK_BACK_SPACE -> Key.BACKSPACE
        KeyEvent.VK_TAB -> Key.TAB
        // F11 is the desktop convention for fullscreen, next to the player's F.
        KeyEvent.VK_F, KeyEvent.VK_F11 -> Key.F
        KeyEvent.VK_M -> Key.M
        KeyEvent.VK_K -> Key.K
        KeyEvent.VK_J -> Key.J
        KeyEvent.VK_L -> Key.L
        KeyEvent.VK_O -> Key.O
        KeyEvent.VK_I -> Key.I
        KeyEvent.VK_PERIOD -> Key.PERIOD
        KeyEvent.VK_COMMA -> Key.COMMA
        KeyEvent.VK_PAGE_UP -> Key.PAGE_UP
        KeyEvent.VK_PAGE_DOWN -> Key.PAGE_DOWN
        KeyEvent.VK_CONTEXT_MENU -> Key.MENU
        KeyEvent.VK_STOP -> Key.MEDIA_STOP
        else -> null
    }
}

/**
 * Whether this is a press of a mouse's back button. Only Windows numbers it reliably (the fourth button);
 * elsewhere the toolkit's numbering of extra buttons overlaps with scroll wheels, so it is left alone.
 */
internal fun MouseEvent.isBackButtonPress(os: String = System.getProperty("os.name")): Boolean =
    id == MouseEvent.MOUSE_PRESSED && button == 4 && os.startsWith("Windows")
