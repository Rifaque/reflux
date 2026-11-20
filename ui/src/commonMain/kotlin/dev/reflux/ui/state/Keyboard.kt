package dev.reflux.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.reflux.core.input.SemanticAction

/** What a field holds, which decides the keyboard offered for it: the system's on a touchscreen, Reflux's for a controller or a remote. */
enum class InputKind { ADDRESS, NAME, SEARCH, SECRET, PIN }

/**
 * A field asking for the Reflux keyboard (B-023): what it is called and holds, how to read and change its text, and
 * what finishing does. The field stays the owner of its text: the keyboard reads it back after every change, so a
 * field that filters what it takes (a PIN's four digits) is always what is shown.
 */
class KeyboardRequest(
    /** The field, for closing the keyboard when it goes. */
    val field: Any,
    val label: String,
    val kind: InputKind,
    val read: () -> String,
    val write: (String) -> Unit,
    /** The field's own action: go on to the next field, search, connect. */
    val done: () -> Unit,
)

/**
 * One key of the Reflux keyboard, by what it is (F-P4's stable key identity): the same key in the controller's grid and
 * on the desk keyboard is one key, wherever each layout puts it ([KeyboardLayouts]).
 */
sealed class KeyboardKey {
    /**
     * Types [text]: a letter (cased by Shift), a digit, a symbol, or a run such as `http://`. [shifted] is what it types
     * with Shift where that is not simply its capital (a desk keyboard's `1` and `!`). A key is its [text]: the same
     * character in two layouts is one key, whatever its Shift gives.
     */
    class Type(val text: String, val shifted: String? = null) : KeyboardKey() {
        override fun equals(other: Any?): Boolean = other is Type && other.text == text
        override fun hashCode(): Int = text.hashCode()
        override fun toString(): String = "Type($text)"
    }
    data object Shift : KeyboardKey()
    /** A desk keyboard's right-hand Shift: Shift by another name. */
    data object ShiftRight : KeyboardKey()
    /** Capitals until pressed again (a desk keyboard's Caps Lock): letters only, as the system does. */
    data object Caps : KeyboardKey()
    /** Letters and symbols. */
    data object Page : KeyboardKey()
    data object Space : KeyboardKey()
    data object Erase : KeyboardKey()
    data object Clear : KeyboardKey()
    data object CursorBack : KeyboardKey()
    data object CursorForward : KeyboardKey()
    data object Done : KeyboardKey()
    /** Puts the keyboard away (the desk keyboard's Hide). */
    data object Hide : KeyboardKey()
}

enum class KeyboardPage { LETTERS, SYMBOLS }

/** Capitals: none, the next letter only, or every letter until Shift again. */
enum class ShiftState { OFF, ONCE, LOCKED }

/**
 * The Reflux keyboard while it is open: its layout for the field's [InputKind], the chosen key, Shift, the page, and
 * the cursor. Pure state, so every rule here is tested without a screen. Directional input moves the chosen key
 * across rows by where keys are (the nearest one above or below), so rows of different keys stay predictable.
 */
class KeyboardSession(
    val request: KeyboardRequest,
    mode: KeyboardMode = KeyboardMode.CONTROLLER,
    /** The desk keyboard's layout, read from the system (D3), or US where it could not be read. */
    private val keymap: SystemKeymap = KeyboardLayouts.US,
    /** Said in the desk keyboard's corner when [keymap] is the fallback ("US layout"). */
    private val keymapNote: String? = null,
) {
    val kind: InputKind get() = request.kind
    val label: String get() = request.label

    /** Which presentation is shown (F-P4): it changes nothing typed, chosen, or set. */
    var mode by mutableStateOf(mode)
        private set

    var page by mutableStateOf(KeyboardPage.LETTERS)
        private set
    var shift by mutableStateOf(if (request.kind == InputKind.NAME && request.read().isEmpty()) ShiftState.ONCE else ShiftState.OFF)
        private set

    /** The key a controller or a remote has chosen: an identity, so it stays the same key when the layout changes. */
    var chosen: KeyboardKey by mutableStateOf(initialKey(request.kind))
        private set

    /** Where the pointer last pressed or hovered, for mapping focus back to the controller's grid (§9). */
    private var lastClicked: KeyboardKey? = null
    private var lastHovered: KeyboardKey? = null

    private var cursorAt by mutableIntStateOf(request.read().length)

    /** The text was this when the cursor was last placed: if it changes from elsewhere (a physical keyboard), the cursor goes to its end. */
    private var known = request.read()

    val text: String get() = request.read()

    /**
     * Where the cursor is, between 0 and the text's length: where it was put while the field shows what the keyboard last
     * wrote; at the end of the field's text otherwise (a field that refused a character, an edit from a physical keyboard,
     * or the frame before a field hands back what was written).
     */
    val cursor: Int
        get() {
            val now = text
            return if (now == known) cursorAt.coerceIn(0, now.length) else now.length
        }

    /** Whether what is typed is hidden (a password, a PIN). */
    val secret: Boolean get() = kind == InputKind.SECRET || kind == InputKind.PIN

    /** The layout shown now, for this mode, field and page. */
    val layout: KeyboardLayout
        get() = when (mode) {
            KeyboardMode.CONTROLLER -> KeyboardLayouts.controller(kind, page)
            KeyboardMode.POINTER -> KeyboardLayouts.pointer(kind, page, keymap, keymapNote)
        }

    /** The controller's grid, which directions move through whatever is shown (a press of the D-pad shows it). */
    private val grid: KeyboardLayout get() = KeyboardLayouts.controller(kind, page)

    /** The rows of the controller's grid, each left to right. */
    val rows: List<List<KeyboardKey>> get() = grid.rows.map { row -> row.map { it.key } }

    /** The chosen key's row and column in the controller's grid. */
    val row: Int get() = rows.indexOfFirst { chosen in it }.coerceAtLeast(0)
    val column: Int get() = rows.getOrNull(row)?.indexOf(chosen)?.coerceAtLeast(0) ?: 0

    val selected: KeyboardKey get() = chosen

    /**
     * Title completions for a search (owner's decision D2), from the library only, newest first; set by the experience
     * layer as the query changes. They never take focus: [completion] is set only when the viewer moves into them.
     */
    var completions: List<String> by mutableStateOf(emptyList())
        private set

    /** The completion a controller or a physical keyboard is on, or null while focus is on the keys. */
    var completion: Int? by mutableStateOf(null)
        private set

    /** What [key] types now: with Shift once, its Shift character; with capitals locked, letters as capitals. */
    fun typed(key: KeyboardKey.Type): String = when {
        shift == ShiftState.ONCE -> key.shifted ?: if (key.text.length == 1) key.text.uppercase() else key.text
        shift == ShiftState.LOCKED && key.text.length == 1 && key.text[0].isLetter() -> key.text.uppercase()
        else -> key.text
    }

    /**
     * Shows the keyboard as [next] (F-P4, §9): nothing typed, set, or paged changes. Into the controller's grid, focus goes
     * to the key last clicked if the grid has it, else to the key the controller last had, else to the grid's key nearest
     * where the pointer last was: never simply the first key.
     */
    fun show(next: KeyboardMode) {
        if (next == mode) return
        if (next == KeyboardMode.CONTROLLER) {
            val shown = layout
            val clicked = lastClicked?.takeIf { it in grid }
            val hovered = lastHovered?.let { key -> shown.place(key) }?.let { grid.nearest(it.centerX / shown.width, it.centerY / shown.height) }
            chosen = clicked ?: chosen.takeIf { it in grid } ?: hovered ?: chosen
        }
        mode = next
        lastClicked = null
    }

    /** The pointer is over [key] (or over none): remembered for mapping focus if a controller takes over. */
    fun hovering(key: KeyboardKey?) {
        if (key != null) lastHovered = key
    }

    /** Sets the completions offered for the query; the one focused stays within them, or focus goes back to the keys. */
    fun offer(titles: List<String>) {
        completions = titles
        completion = completion?.takeIf { it < titles.size }
    }

    /** Moves focus into the completions (Up from the top row, Tab from the field), at [index]; false when there are none. */
    fun enterCompletions(index: Int = 0): Boolean {
        if (completions.isEmpty()) return false
        completion = index.coerceIn(0, completions.lastIndex)
        return true
    }

    /** Focus back from the completions to the key it came from. */
    fun leaveCompletions() {
        completion = null
    }

    /**
     * Puts completion [index] into the field (D2): the whole title, the cursor at its end. The keyboard stays open and
     * nothing is opened; Done then goes to the results, where that title is first.
     */
    fun complete(index: Int): Outcome {
        val title = completions.getOrNull(index) ?: return Outcome.EDGE
        put(title, title.length)
        shift = ShiftState.OFF
        return Outcome.HANDLED
    }

    /**
     * A semantic action while the keyboard is open. Returns [Outcome.DONE] when typing is finished (the Done key, or
     * Start), [Outcome.EDGE] when a direction has nowhere to go, else [Outcome.HANDLED].
     */
    fun handle(action: SemanticAction): Outcome = completion?.let { at -> inCompletions(at, action) } ?: when (action) {
        SemanticAction.NAVIGATE_LEFT -> move(0, -1)
        SemanticAction.NAVIGATE_RIGHT -> move(0, 1)
        SemanticAction.NAVIGATE_UP -> move(-1, 0)
        SemanticAction.NAVIGATE_DOWN -> move(1, 0)
        SemanticAction.SELECT -> press(selected)
        SemanticAction.TEXT_ERASE -> press(KeyboardKey.Erase)
        SemanticAction.TEXT_SPACE -> if (kind == InputKind.PIN) Outcome.EDGE else press(KeyboardKey.Space)
        SemanticAction.CURSOR_BACK -> press(KeyboardKey.CursorBack)
        SemanticAction.CURSOR_FORWARD -> press(KeyboardKey.CursorForward)
        SemanticAction.TEXT_SHIFT -> if (kind == InputKind.PIN) Outcome.EDGE else press(KeyboardKey.Shift)
        SemanticAction.TEXT_SYMBOLS -> if (kind == InputKind.PIN) Outcome.EDGE else press(KeyboardKey.Page)
        SemanticAction.TEXT_DONE -> Outcome.DONE
        else -> Outcome.HANDLED
    }

    /** Directions and confirm while focus is on completion [at]: along the row, Down back to the keys, confirm fills it in. */
    private fun inCompletions(at: Int, action: SemanticAction): Outcome? = when (action) {
        SemanticAction.NAVIGATE_LEFT -> if (at > 0) Outcome.HANDLED.also { completion = at - 1 } else Outcome.EDGE
        SemanticAction.NAVIGATE_RIGHT -> if (at < completions.lastIndex) Outcome.HANDLED.also { completion = at + 1 } else Outcome.EDGE
        SemanticAction.NAVIGATE_UP -> Outcome.EDGE
        SemanticAction.NAVIGATE_DOWN -> Outcome.HANDLED.also { leaveCompletions() }
        SemanticAction.SELECT -> complete(at)
        else -> null
    }

    /** A pointer pressed [key] where it is drawn: typed, and remembered for mapping focus (§9). */
    fun click(key: KeyboardKey): Outcome {
        lastClicked = key
        return press(key)
    }

    /** Presses [key], as confirm on it, a touch, or a click does. */
    fun press(key: KeyboardKey): Outcome {
        val now = text
        // Text changed from elsewhere (a physical keyboard) since the keyboard last wrote: typing goes on at its end.
        if (now != known) {
            known = now
            cursorAt = now.length
        }
        val at = cursor
        when (key) {
            is KeyboardKey.Type -> {
                put(now.substring(0, at) + typed(key) + now.substring(at), at + typed(key).length)
                if (shift == ShiftState.ONCE) shift = ShiftState.OFF
            }
            KeyboardKey.Space -> put(now.substring(0, at) + " " + now.substring(at), at + 1)
            KeyboardKey.Erase -> if (at > 0) put(now.substring(0, at - 1) + now.substring(at), at - 1) else return Outcome.EDGE
            KeyboardKey.Clear -> put("", 0)
            KeyboardKey.CursorBack -> if (at > 0) place(at - 1) else return Outcome.EDGE
            KeyboardKey.CursorForward -> if (at < now.length) place(at + 1) else return Outcome.EDGE
            // The controller's Shift steps through once, locked, off; a desk keyboard's Shift is once or off, and its Caps
            // locks or unlocks, as a physical one does.
            KeyboardKey.Shift, KeyboardKey.ShiftRight -> shift = if (mode == KeyboardMode.POINTER) {
                if (shift == ShiftState.ONCE) ShiftState.OFF else ShiftState.ONCE
            } else {
                when (shift) {
                    ShiftState.OFF -> ShiftState.ONCE
                    ShiftState.ONCE -> ShiftState.LOCKED
                    ShiftState.LOCKED -> ShiftState.OFF
                }
            }
            KeyboardKey.Caps -> shift = if (shift == ShiftState.LOCKED) ShiftState.OFF else ShiftState.LOCKED
            KeyboardKey.Page -> {
                page = if (page == KeyboardPage.LETTERS) KeyboardPage.SYMBOLS else KeyboardPage.LETTERS
                clamp()
            }
            KeyboardKey.Done -> return Outcome.DONE
            KeyboardKey.Hide -> return Outcome.HIDE
        }
        return Outcome.HANDLED
    }

    /** Chooses the key at [rowIndex], [columnIndex] of the controller's grid, as tests and a finger on a key do. */
    fun choose(rowIndex: Int, columnIndex: Int) {
        val grid = rows
        val r = rowIndex.coerceIn(0, grid.lastIndex)
        chosen = grid[r][columnIndex.coerceIn(0, grid[r].lastIndex)]
    }

    /** Chooses [key] itself. */
    fun choose(key: KeyboardKey) {
        if (key in grid) chosen = key
    }

    /**
     * Writes [next] to the field and places the cursor at [at]. A field hands its new text back only once it is composed
     * again, so what was written is the record, not what the field says this instant (reading it back at once put the
     * cursor at the end after every key: the walk on Windows, 2026-11-19). If the field ends up with other text (it refused
     * or changed what was written), [cursor] stays within it, and the next key goes on from its end.
     */
    private fun put(next: String, at: Int) {
        request.write(next)
        known = next
        cursorAt = at
    }

    private fun place(at: Int) {
        cursorAt = at
    }

    /**
     * Moves the chosen key through the controller's grid (§4.4): left and right wrap within a row, the action column its
     * last stop; up and down go to the nearest key of the next row, and stop at the top and the bottom. Up from the top
     * row goes into the completions where a search has any.
     */
    private fun move(rows: Int, columns: Int): Outcome {
        val next = grid.neighbour(chosen, columns, rows)
        if (next == null) return if (rows < 0 && enterCompletions(nearestCompletion())) Outcome.HANDLED else Outcome.EDGE
        chosen = next
        return Outcome.HANDLED
    }

    /** The completion above the chosen key, by where each sits across the row. */
    private fun nearestCompletion(): Int {
        val here = grid.place(chosen) ?: return 0
        return ((here.centerX / grid.width) * completions.size).toInt().coerceIn(0, (completions.size - 1).coerceAtLeast(0))
    }

    private fun clamp() {
        if (chosen !in grid) chosen = grid.rows.getOrNull(1)?.firstOrNull()?.key ?: grid.keys.first().key
    }

    enum class Outcome { HANDLED, EDGE, DONE, HIDE }

    companion object {
        /** Where focus starts: the first letter, or a PIN's 1. */
        private fun initialKey(kind: InputKind): KeyboardKey = KeyboardLayouts.controller(kind, KeyboardPage.LETTERS).let { grid ->
            (if (kind == InputKind.PIN) grid.rows[0] else grid.rows[1]).first().key
        }

        /** The controller's rows for a field of [kind] on [page] (the grid of [KeyboardLayouts.controller]). */
        fun layout(kind: InputKind, page: KeyboardPage): List<List<KeyboardKey>> =
            KeyboardLayouts.controller(kind, page).rows.map { row -> row.map { it.key } }
    }
}
