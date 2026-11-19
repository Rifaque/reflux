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

/** One key of the Reflux keyboard. [weight] is its width against the others in its row. */
sealed class KeyboardKey(val weight: Float) {
    /** Types [text]: a letter (cased by Shift), a digit, a symbol, or a run such as `http://`. */
    data class Type(val text: String, private val width: Float = 1f) : KeyboardKey(width)
    data object Shift : KeyboardKey(1.5f)
    /** Letters and symbols. */
    data object Page : KeyboardKey(1.5f)
    data object Space : KeyboardKey(3f)
    data object Erase : KeyboardKey(1.5f)
    data object Clear : KeyboardKey(1.5f)
    data object CursorBack : KeyboardKey(1f)
    data object CursorForward : KeyboardKey(1f)
    data object Done : KeyboardKey(2f)
}

enum class KeyboardPage { LETTERS, SYMBOLS }

/** Capitals: none, the next letter only, or every letter until Shift again. */
enum class ShiftState { OFF, ONCE, LOCKED }

/**
 * The Reflux keyboard while it is open: its layout for the field's [InputKind], the chosen key, Shift, the page, and
 * the cursor. Pure state, so every rule here is tested without a screen. Directional input moves the chosen key
 * across rows by where keys are (the nearest one above or below), so rows of different keys stay predictable.
 */
class KeyboardSession(val request: KeyboardRequest) {
    val kind: InputKind get() = request.kind
    val label: String get() = request.label

    var page by mutableStateOf(KeyboardPage.LETTERS)
        private set
    var shift by mutableStateOf(if (request.kind == InputKind.NAME && request.read().isEmpty()) ShiftState.ONCE else ShiftState.OFF)
        private set
    var row by mutableIntStateOf(if (request.kind == InputKind.PIN) 0 else 1)
        private set
    var column by mutableIntStateOf(0)
        private set
    private var cursorAt by mutableIntStateOf(request.read().length)

    /** The text was this when the cursor was last placed: if it changes from elsewhere (a physical keyboard), the cursor goes to its end. */
    private var known = request.read()

    val text: String get() = request.read()

    /** Where the cursor is, between 0 and the text's length. */
    val cursor: Int
        get() {
            val now = text
            return if (now != known) now.length else cursorAt.coerceIn(0, now.length)
        }

    /** Whether what is typed is hidden (a password, a PIN). */
    val secret: Boolean get() = kind == InputKind.SECRET || kind == InputKind.PIN

    val rows: List<List<KeyboardKey>> get() = layout(kind, page)

    val selected: KeyboardKey get() = rows[row][column]

    /** What [key] types now, Shift applied. */
    fun typed(key: KeyboardKey.Type): String =
        if (shift != ShiftState.OFF && key.text.length == 1) key.text.uppercase() else key.text

    /**
     * A semantic action while the keyboard is open. Returns [Outcome.DONE] when typing is finished (the Done key, or
     * Start), [Outcome.EDGE] when a direction has nowhere to go, else [Outcome.HANDLED].
     */
    fun handle(action: SemanticAction): Outcome = when (action) {
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

    /** Presses [key], as confirm on it, a touch, or a click does. */
    fun press(key: KeyboardKey): Outcome {
        val now = text
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
            KeyboardKey.Shift -> shift = when (shift) {
                ShiftState.OFF -> ShiftState.ONCE
                ShiftState.ONCE -> ShiftState.LOCKED
                ShiftState.LOCKED -> ShiftState.OFF
            }
            KeyboardKey.Page -> {
                page = if (page == KeyboardPage.LETTERS) KeyboardPage.SYMBOLS else KeyboardPage.LETTERS
                clamp()
            }
            KeyboardKey.Done -> return Outcome.DONE
        }
        return Outcome.HANDLED
    }

    /** Chooses [key] where it is, as a pointer or a finger does by pressing it. */
    fun choose(rowIndex: Int, columnIndex: Int) {
        row = rowIndex.coerceIn(0, rows.lastIndex)
        column = columnIndex.coerceIn(0, rows[row].lastIndex)
    }

    /** Writes [next] to the field, and places the cursor at [at] if the field took it as it was, or at its end if not. */
    private fun put(next: String, at: Int) {
        request.write(next)
        val taken = request.read()
        known = taken
        cursorAt = if (taken == next) at else taken.length
    }

    private fun place(at: Int) {
        known = text
        cursorAt = at
    }

    private fun move(rows: Int, columns: Int): Outcome {
        val layout = this.rows
        if (rows == 0) {
            val next = column + columns
            if (next !in layout[row].indices) return Outcome.EDGE
            column = next
            return Outcome.HANDLED
        }
        val target = row + rows
        if (target !in layout.indices) return Outcome.EDGE
        // The key in the next row whose middle is nearest this one's: keys are chosen by where they are.
        val middle = middleOf(layout[row], column)
        column = layout[target].indices.minBy { kotlin.math.abs(middleOf(layout[target], it) - middle) }
        row = target
        return Outcome.HANDLED
    }

    private fun clamp() {
        row = row.coerceIn(0, rows.lastIndex)
        column = column.coerceIn(0, rows[row].lastIndex)
    }

    enum class Outcome { HANDLED, EDGE, DONE }

    companion object {
        /** Where the middle of key [index] falls across its row, from 0 to 1. */
        internal fun middleOf(row: List<KeyboardKey>, index: Int): Float {
            val total = row.sumOf { it.weight.toDouble() }.toFloat()
            val before = row.take(index).sumOf { it.weight.toDouble() }.toFloat()
            return (before + row[index].weight / 2f) / total
        }

        private fun keys(text: String) = text.map { KeyboardKey.Type(it.toString()) }

        private val DIGITS = keys("1234567890")

        /**
         * The rows for a field of [kind] on [page]. Every layout ends with the keys a remote needs, having no buttons
         * for them: the cursor, a space, erase, clear, and done. A PIN is a number pad; an address keeps `:`, `/`, `.`,
         * and `http://` on its first page.
         */
        fun layout(kind: InputKind, page: KeyboardPage): List<List<KeyboardKey>> {
            if (kind == InputKind.PIN) {
                return listOf(
                    keys("123"),
                    keys("456"),
                    keys("789"),
                    listOf(KeyboardKey.Clear, KeyboardKey.Type("0"), KeyboardKey.Erase),
                    listOf(KeyboardKey.Done),
                )
            }
            val address = kind == InputKind.ADDRESS
            val characters = when (page) {
                KeyboardPage.LETTERS -> listOf(
                    DIGITS,
                    keys("qwertyuiop"),
                    keys(if (address) "asdfghjkl:" else "asdfghjkl'"),
                    keys(if (address) "zxcvbnm./-" else "zxcvbnm,.-"),
                )
                KeyboardPage.SYMBOLS -> listOf(
                    DIGITS,
                    keys("!@#\$%^&*()"),
                    keys("-_=+[]{};:"),
                    keys("'\",./\\?|~`"),
                )
            }
            val middle = if (address && page == KeyboardPage.LETTERS) KeyboardKey.Type("http://", 3f) else KeyboardKey.Space
            return characters + listOf(
                listOf(KeyboardKey.Shift, KeyboardKey.Page, KeyboardKey.CursorBack, middle, KeyboardKey.CursorForward, KeyboardKey.Erase, KeyboardKey.Clear, KeyboardKey.Done),
            )
        }
    }
}
