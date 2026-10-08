package dev.reflux.desktop

import dev.reflux.core.input.Key
import java.awt.event.KeyEvent

/**
 * Normalizes an AWT key press into a Reflux [Key], or null for keys Reflux has no meaning for.
 *
 * Presses with Ctrl, Alt, or Meta held are left alone so system and toolkit shortcuts keep working.
 */
internal fun KeyEvent.toRefluxKey(): Key? {
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
        KeyEvent.VK_PERIOD -> Key.PERIOD
        KeyEvent.VK_COMMA -> Key.COMMA
        KeyEvent.VK_PAGE_UP -> Key.PAGE_UP
        KeyEvent.VK_PAGE_DOWN -> Key.PAGE_DOWN
        KeyEvent.VK_CONTEXT_MENU -> Key.MENU
        KeyEvent.VK_STOP -> Key.MEDIA_STOP
        else -> null
    }
}
