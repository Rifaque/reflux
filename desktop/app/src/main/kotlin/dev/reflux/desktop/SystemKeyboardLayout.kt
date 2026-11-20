package dev.reflux.desktop

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import dev.reflux.ui.state.KeyboardLayouts
import dev.reflux.ui.state.SystemKey
import dev.reflux.ui.state.SystemKeymap

/**
 * Reads the system's keyboard layout for the Reflux keyboard's desk picture (F-P4, owner's decision D3; planning/
 * F-P4-ADAPTIVE-KEYBOARD.md §5.1): what each physical key types, plain and with Shift, so the picture shows what typing
 * there gives (AZERTY, QWERTZ, …). Read-only: the process's keyboard state is never changed. Null where it cannot be
 * read reliably: an input-method layout (Chinese, Japanese, Korean), a key giving nothing printable, no display; the
 * caller then shows US with a note.
 */
internal object SystemKeyboardLayout {
    /**
     * Scan codes (set 1, which are also Linux's evdev codes) of the ANSI rows of [KeyboardLayouts.POSITIONS], left to
     * right, and of the ISO key beside the left Shift.
     */
    private val SCAN_CODES = listOf(
        listOf(0x29) + (0x02..0x0D).toList(),
        (0x10..0x1B).toList() + 0x2B,
        (0x1E..0x28).toList(),
        (0x2C..0x35).toList(),
    )
    private const val ISO_SCAN_CODE = 0x56

    private var reported = false

    fun read(os: String = System.getProperty("os.name")): SystemKeymap? = runCatching {
        when {
            os.startsWith("Windows") -> Windows.read()
            os.startsWith("Linux") -> Linux.read()
            else -> null
        }
    }.onFailure {
        // Once: a layout that cannot be read is the same every time the keyboard opens.
        if (!reported) {
            reported = true
            System.err.println("Reflux: the keyboard layout could not be read; the Reflux keyboard shows US. ${it.javaClass.simpleName}")
        }
    }.getOrNull()

    /** A keymap from what each position types, or null when a letter position gives nothing printable (not a direct layout). */
    private fun keymap(name: String, read: (scanCode: Int) -> SystemKey?): SystemKeymap? {
        val rows = SCAN_CODES.map { row -> row.map(read) }
        val letters = rows.drop(1).flatten()
        if (letters.count { it != null && it.plain.isNotBlank() } < letters.size - 2) return null
        return SystemKeymap(name, rows, read(ISO_SCAN_CODE)?.takeIf { it.plain.isNotBlank() && it.plain != rows[1].last()?.plain })
    }

    private object Windows {
        @Suppress("FunctionName")
        private interface User32 : Library {
            fun GetKeyboardLayout(thread: Int): Pointer?
            fun GetForegroundWindow(): Pointer?
            fun GetWindowThreadProcessId(window: Pointer?, process: Pointer?): Int
            fun MapVirtualKeyExW(code: Int, mapType: Int, layout: Pointer?): Int
            fun ToUnicodeEx(virtualKey: Int, scanCode: Int, keyState: ByteArray, buffer: CharArray, size: Int, flags: Int, layout: Pointer?): Int
        }

        private val user32: User32 by lazy { Native.load("user32", User32::class.java) }
        private const val MAPVK_VSC_TO_VK_EX = 3
        private const val VK_SHIFT = 0x10

        /** Windows 10 1607 and later: do not change the keyboard state (no dead key is left pending). */
        private const val NO_STATE_CHANGE = 0x4

        /** Primary languages whose layouts are input methods: Chinese, Japanese, Korean. */
        private val IME_LANGUAGES = setOf(0x04, 0x11, 0x12)

        fun read(): SystemKeymap? {
            // The layout of the thread whose window has focus (Reflux's, when it asks); layouts are per thread on Windows.
            val thread = user32.GetWindowThreadProcessId(user32.GetForegroundWindow(), null)
            val layout = user32.GetKeyboardLayout(thread) ?: return null
            val language = (Pointer.nativeValue(layout) and 0xFFFF).toInt()
            if ((language and 0x3FF) in IME_LANGUAGES) return null
            return keymap("Windows layout ${"%04x".format(language)}") { scanCode ->
                val virtualKey = user32.MapVirtualKeyExW(scanCode, MAPVK_VSC_TO_VK_EX, layout)
                if (virtualKey == 0) return@keymap null
                fun typed(shift: Boolean): String? {
                    val state = ByteArray(256)
                    if (shift) state[VK_SHIFT] = 0x80.toByte()
                    val buffer = CharArray(4)
                    val count = user32.ToUnicodeEx(virtualKey, scanCode, state, buffer, buffer.size, NO_STATE_CHANGE, layout)
                    // A dead key gives its spacing accent with a negative count: it is shown and typed as that (§5.1).
                    val length = if (count < 0) 1 else count
                    return String(buffer, 0, length.coerceAtMost(buffer.size)).takeIf { it.isNotEmpty() && it.all { c -> !c.isISOControl() } }
                }
                val plain = typed(false) ?: return@keymap null
                SystemKey(plain, typed(true) ?: plain)
            }
        }
    }

    private object Linux {
        @Suppress("FunctionName")
        private interface X11 : Library {
            fun XOpenDisplay(name: String?): Pointer?
            fun XCloseDisplay(display: Pointer): Int
            fun XkbKeycodeToKeysym(display: Pointer, keycode: Byte, group: Int, level: Int): Long
            fun XkbGetState(display: Pointer, device: Int, state: Pointer): Int
        }

        private val x11: X11 by lazy { Native.load("X11", X11::class.java) }
        private const val XKB_USE_CORE_KBD = 0x0100

        fun read(): SystemKeymap? {
            val display = x11.XOpenDisplay(null) ?: return null
            try {
                // XkbStateRec starts with the effective group (an unsigned char).
                val state = Memory(64).also { it.clear() }
                val group = if (x11.XkbGetState(display, XKB_USE_CORE_KBD, state) == 0) state.getByte(0).toInt() and 0xFF else 0
                return keymap("XKB group $group") { scanCode ->
                    val keycode = (scanCode + 8).toByte()
                    val plain = Keysyms.text(x11.XkbKeycodeToKeysym(display, keycode, group, 0)) ?: return@keymap null
                    SystemKey(plain, Keysyms.text(x11.XkbKeycodeToKeysym(display, keycode, group, 1)) ?: plain)
                }
            } finally {
                x11.XCloseDisplay(display)
            }
        }
    }
}

/** X keysyms as text: Latin-1 directly, Unicode keysyms by their code point, and the common dead keys as their accents. */
internal object Keysyms {
    private val DEAD = mapOf(
        0xfe50L to "`", 0xfe51L to "´", 0xfe52L to "^", 0xfe53L to "~", 0xfe54L to "¯", 0xfe55L to "˘", 0xfe56L to "˙",
        0xfe57L to "¨", 0xfe58L to "˚", 0xfe59L to "˝", 0xfe5aL to "ˇ", 0xfe5bL to "¸",
    )

    fun text(keysym: Long): String? = when {
        keysym in 0x20L..0x7EL || keysym in 0xA0L..0xFFL -> keysym.toInt().toChar().toString()
        keysym and 0xFF000000L == 0x01000000L -> String(Character.toChars((keysym and 0x00FFFFFFL).toInt()))
        else -> DEAD[keysym]
    }
}
