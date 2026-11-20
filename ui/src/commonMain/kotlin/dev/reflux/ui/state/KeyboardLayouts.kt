package dev.reflux.ui.state

import kotlin.math.abs

/** Which of the Reflux keyboard's two presentations is shown (F-P4): a controller's grid, or a desk's keyboard picture. */
enum class KeyboardMode { CONTROLLER, POINTER }

/** A key at its place: [x], [y], [width], [height] in key units (one letter key is 1 by 1). */
data class PlacedKey(val key: KeyboardKey, val x: Float, val y: Float, val width: Float, val height: Float = 1f) {
    val centerX: Float get() = x + width / 2f
    val centerY: Float get() = y + height / 2f
}

/**
 * One presentation of the Reflux keyboard as data (F-P4; planning/F-P4-ADAPTIVE-KEYBOARD.md, §6): every key with its
 * rectangle in key units, and the whole [width] by [height]. Built once per mode, kind, page and system layout, and
 * tested without a screen. Keys are identified by what they are ([KeyboardKey] equality), never by where they are, so
 * the same key in two layouts is one key: the morph moves it, and focus follows it.
 */
class KeyboardLayout(val keys: List<PlacedKey>, val note: String? = null) {
    val width: Float = keys.maxOfOrNull { it.x + it.width } ?: 0f
    val height: Float = keys.maxOfOrNull { it.y + it.height } ?: 0f

    /** The key's place, or null when this layout has no such key. */
    fun place(key: KeyboardKey): PlacedKey? = keys.firstOrNull { it.key == key }

    operator fun contains(key: KeyboardKey): Boolean = place(key) != null

    /** The rows, top to bottom, each left to right: a key belongs to the row its top is in. */
    val rows: List<List<PlacedKey>> by lazy { keys.groupBy { it.y }.toSortedMap().values.map { row -> row.sortedBy { it.x } } }

    /**
     * Where a direction goes from [from] (§4.4): left and right along its row, wrapping at the ends; up and down to the
     * key in the next row whose middle is nearest in x. Null at the top or the bottom (a boundary).
     */
    fun neighbour(from: KeyboardKey, dx: Int, dy: Int): KeyboardKey? {
        val here = place(from) ?: return keys.firstOrNull()?.key
        val rowIndex = rows.indexOfFirst { row -> row.any { it.key == from } }
        if (dy == 0) {
            val row = rows[rowIndex]
            val at = row.indexOfFirst { it.key == from }
            return row[((at + dx) % row.size + row.size) % row.size].key
        }
        val target = rows.getOrNull(rowIndex + dy) ?: return null
        return target.minByOrNull { abs(it.centerX - here.centerX) }?.key
    }

    /** The key of this layout nearest [x], [y] given as fractions of another layout's size (§9's last resort). */
    fun nearest(fx: Float, fy: Float): KeyboardKey? =
        keys.minByOrNull { val dx = it.centerX / width - fx; val dy = it.centerY / height - fy; dx * dx + dy * dy }?.key
}

/** One physical key of a desk keyboard as the system's layout types it: plain, and with Shift (§5.1). */
data class SystemKey(val plain: String, val shifted: String)

/**
 * The characters of the system's keyboard layout (owner's decision D3), by physical position: the ANSI rows of
 * [KeyboardLayouts.POSITIONS], plus [iso] (the key beside the left Shift) where the layout has one. Read by the shell
 * (Windows: `ToUnicodeEx`; Linux: XKB); null fields fall back to US.
 */
data class SystemKeymap(val name: String, val rows: List<List<SystemKey?>>, val iso: SystemKey? = null)

/** The layouts of the Reflux keyboard (F-P4, §4 and §5). */
object KeyboardLayouts {
    private fun type(text: String, shifted: String? = null) = KeyboardKey.Type(text, shifted)

    private fun row(y: Float, x: Float, keys: List<KeyboardKey>): List<PlacedKey> = keys.mapIndexed { i, key -> PlacedKey(key, x + i, y, 1f) }

    private fun chars(text: String) = text.map { type(it.toString()) }

    private val DIGITS = chars("1234567890")

    /** The letter rows of a page, as the controller grid shows them (§4.1). */
    private fun letterRows(kind: InputKind, page: KeyboardPage): List<List<KeyboardKey>> = when (page) {
        KeyboardPage.LETTERS -> listOf(
            DIGITS,
            chars("qwertyuiop"),
            chars(if (kind == InputKind.ADDRESS) "asdfghjkl:" else "asdfghjkl'"),
            chars(if (kind == InputKind.ADDRESS) "zxcvbnm/.-" else "zxcvbnm,.-"),
        )
        KeyboardPage.SYMBOLS -> listOf(
            DIGITS,
            chars("!@#\$%^&*()"),
            chars("-_=+[]{};:"),
            chars("'\",./\\?|~`"),
        )
    }

    /** The PIN pad, the same for every input: three by four and Done. */
    private val PIN = listOf(
        row(0f, 0f, chars("123")),
        row(1f, 0f, chars("456")),
        row(2f, 0f, chars("789")),
        listOf(PlacedKey(KeyboardKey.Clear, 0f, 3f, 1f), PlacedKey(type("0"), 1f, 3f, 1f), PlacedKey(KeyboardKey.Erase, 2f, 3f, 1f)),
        listOf(PlacedKey(KeyboardKey.Done, 0f, 4f, 3f)),
    ).flatten()

    /**
     * The controller's grid (§4.1): ten aligned columns with the digits on top, an action column (Erase, Space, Done)
     * at the right of the first three rows, and a short bottom row (Shift, &123, cursor back and forward, Clear). An
     * address puts `http://` and `.com` in the action column's free places.
     */
    fun controller(kind: InputKind, page: KeyboardPage): KeyboardLayout {
        if (kind == InputKind.PIN) return KeyboardLayout(PIN)
        val address = kind == InputKind.ADDRESS && page == KeyboardPage.LETTERS
        val rows = letterRows(kind, page)
        val keys = rows.flatMapIndexed { y, row -> row(y.toFloat(), 0f, row) }.toMutableList()
        keys += PlacedKey(KeyboardKey.Erase, 10f, 0f, 1.5f)
        keys += PlacedKey(KeyboardKey.Space, 10f, 1f, 1.5f)
        keys += PlacedKey(KeyboardKey.Done, 10f, 2f, 1.5f)
        if (address) keys += PlacedKey(type("http://"), 10f, 3f, 1.5f)
        keys += PlacedKey(KeyboardKey.Shift, 0f, 4f, 2f)
        keys += PlacedKey(KeyboardKey.Page, 2f, 4f, 2f)
        keys += PlacedKey(KeyboardKey.CursorBack, 4f, 4f, 1.5f)
        keys += PlacedKey(KeyboardKey.CursorForward, 5.5f, 4f, 1.5f)
        keys += PlacedKey(KeyboardKey.Clear, 7f, 4f, 3f)
        if (address) keys += PlacedKey(type(".com"), 10f, 4f, 1.5f)
        return KeyboardLayout(keys)
    }

    /** US ANSI, what the mouse keyboard shows where the system's layout cannot be read (§5.1's fallback). */
    val US = SystemKeymap(
        "US",
        listOf(
            "`~ 1! 2@ 3# 4$ 5% 6^ 7& 8* 9( 0) -_ =+",
            "qQ wW eE rR tT yY uU iI oO pP [{ ]} \\|",
            "aA sS dD fF gG hH jJ kK lL ;: '\"",
            "zZ xX cC vV bB nN mM ,< .> /?",
        ).map { line -> line.split(' ').map { SystemKey(it.substring(0, 1), it.substring(1, 2)) } },
    )

    /** How many keys each ANSI row has, which is what a keymap's rows are read against. */
    val POSITIONS = listOf(13, 13, 11, 10)

    /**
     * What the mouse keyboard's &123 page puts in its letter positions, in order (§5: like a phone's symbols page): the
     * symbols a board types with Shift, then a few more. Any already on the board unshifted is skipped, so no key is twice.
     */
    private const val MOUSE_SYMBOLS = "!@#\$%^&*()_+{}|:\"<>?~«»¿¡°€£¥§•"

    /**
     * The desk keyboard (§5): a staggered picture of a physical one, from [keymap] (the system's layout, or [US]), with
     * Backspace (Erase), Caps, Enter (Done), both Shifts, &123, the cursor, a wide Space, Clear and Hide. What each key
     * shows is what it types. A PIN is the pad.
     */
    fun pointer(kind: InputKind, page: KeyboardPage, keymap: SystemKeymap = US, note: String? = null): KeyboardLayout {
        if (kind == InputKind.PIN) return KeyboardLayout(PIN)
        val rows = POSITIONS.indices.map { r -> (0 until POSITIONS[r]).map { c -> keymap.rows.getOrNull(r)?.getOrNull(c) ?: US.rows[r][c]!! } }
        val letterPositions = mapOf(1 to 0..9, 2 to 0..8, 3 to 0..6)
        fun isLetterPosition(r: Int, c: Int) = letterPositions[r]?.contains(c) == true
        // The symbols page's characters: in reading order through the letter positions, none already on the board.
        val board = rows.flatMapIndexed { r, row -> row.filterIndexed { c, _ -> !isLetterPosition(r, c) }.map { it.plain } }.toSet() + listOfNotNull(keymap.iso?.plain)
        val symbols = ArrayDeque(MOUSE_SYMBOLS.map { it.toString() }.filter { it !in board })
        val symbolAt = mutableMapOf<Pair<Int, Int>, String>()
        if (page == KeyboardPage.SYMBOLS) for (r in 1..3) for (c in letterPositions.getValue(r)) symbols.removeFirstOrNull()?.let { symbolAt[r to c] = it }
        fun keyOf(r: Int, c: Int, system: SystemKey): KeyboardKey {
            symbolAt[r to c]?.let { return type(it) }
            return type(system.plain, system.shifted.takeIf { it != system.plain.uppercase() })
        }
        val keys = mutableListOf<PlacedKey>()
        rows[0].forEachIndexed { c, k -> keys += PlacedKey(keyOf(0, c, k), c.toFloat(), 0f, 1f) }
        keys += PlacedKey(KeyboardKey.Erase, 13f, 0f, 2f)
        // Tab's place is left empty: no field asks for one (§5, "hidden otherwise").
        rows[1].forEachIndexed { c, k -> keys += PlacedKey(keyOf(1, c, k), 1.5f + c, 1f, if (c == 12) 1.5f else 1f) }
        keys += PlacedKey(KeyboardKey.Caps, 0f, 2f, 1.75f)
        rows[2].forEachIndexed { c, k -> keys += PlacedKey(keyOf(2, c, k), 1.75f + c, 2f, 1f) }
        keys += PlacedKey(KeyboardKey.Done, 12.75f, 2f, 2.25f)
        val iso = keymap.iso
        val shiftWidth = if (iso != null) 1.25f else 2.25f
        keys += PlacedKey(KeyboardKey.Shift, 0f, 3f, shiftWidth)
        if (iso != null) keys += PlacedKey(keyOf(3, -1, iso), 1.25f, 3f, 1f)
        rows[3].forEachIndexed { c, k -> keys += PlacedKey(keyOf(3, c, k), 2.25f + c, 3f, 1f) }
        keys += PlacedKey(KeyboardKey.ShiftRight, 12.25f, 3f, 2.75f)
        keys += PlacedKey(KeyboardKey.Page, 0f, 4f, 1.5f)
        keys += PlacedKey(KeyboardKey.CursorBack, 1.5f, 4f, 1f)
        keys += PlacedKey(KeyboardKey.CursorForward, 2.5f, 4f, 1f)
        keys += PlacedKey(KeyboardKey.Space, 3.5f, 4f, 6.5f)
        keys += PlacedKey(KeyboardKey.Clear, 10f, 4f, 2.5f)
        keys += PlacedKey(KeyboardKey.Hide, 12.5f, 4f, 2.5f)
        if (kind == InputKind.ADDRESS && page == KeyboardPage.LETTERS) {
            // An address's runs sit above the board's right end, where a desk keyboard has nothing.
            keys.replaceAll { it.copy(y = it.y + 1f) }
            keys += PlacedKey(type("http://"), 11f, 0f, 2f)
            keys += PlacedKey(type(".com"), 13f, 0f, 2f)
        }
        return KeyboardLayout(keys, note)
    }
}
