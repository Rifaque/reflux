package dev.reflux.ui.state

import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.core.input.InputContext
import dev.reflux.core.input.InputMapping
import dev.reflux.core.input.Key
import dev.reflux.core.input.SemanticAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Reflux keyboard's rules without a screen (B-023): layouts, choosing keys by where they are, editing, Shift. */
class KeyboardSessionTest {
    private class Field(var text: String = "", val filter: (String) -> String = { it }) {
        var done = 0
        fun request(kind: InputKind) = KeyboardRequest(this, "Field", kind, { text }, { text = filter(it) }, { done++ })
    }

    private fun KeyboardSession.typeKey(text: String) {
        val (r, c) = rows.withIndex().firstNotNullOf { (r, row) -> row.indexOfFirst { it is KeyboardKey.Type && it.text == text }.takeIf { it >= 0 }?.let { r to it } }
        choose(r, c)
        press(selected)
    }

    /** A field that hands its new text back only when it is composed again, as a screen's field does (Search's). */
    private class LaggingField(var shown: String = "") {
        var written: String? = null
        fun request() = KeyboardRequest(this, "Search", InputKind.SEARCH, { shown }, { written = it }, {})
        fun compose() { written?.let { shown = it }; written = null }
    }

    @Test
    fun theCursorStaysWhereItWasPutWhenTheFieldCatchesUpLate() {
        // The walk on Windows (2026-11-19): ABD, the left bumper once, C, then E went after D instead of after C.
        val field = LaggingField()
        val session = KeyboardSession(field.request())
        for (letter in listOf("a", "b", "d")) { session.typeKey(letter); field.compose(); assertEquals(field.shown.length, session.cursor) }
        assertEquals("abd", field.shown)
        session.handle(SemanticAction.CURSOR_BACK)
        assertEquals(2, session.cursor, "between b and d")
        session.typeKey("c")
        field.compose()
        assertEquals("abcd", field.shown)
        assertEquals(3, session.cursor, "still after c once it has")
        session.typeKey("e")
        field.compose()
        assertEquals("abced", field.shown, "E goes where the cursor is")
        // An edit made elsewhere (a physical keyboard) puts the cursor at the end.
        field.shown = "zz"
        assertEquals(2, session.cursor)
    }

    @Test
    fun typesWhereTheCursorIsAndErasesBeforeIt() {
        val field = Field("rflux")
        val session = KeyboardSession(field.request(InputKind.SEARCH))
        assertEquals(5, session.cursor, "the cursor starts at the end")
        repeat(4) { session.handle(SemanticAction.CURSOR_BACK) }
        session.typeKey("e")
        assertEquals("reflux", field.text)
        assertEquals(2, session.cursor)
        session.handle(SemanticAction.TEXT_ERASE)
        assertEquals("rflux", field.text)
        assertEquals(1, session.cursor)
        session.handle(SemanticAction.CURSOR_FORWARD)
        session.handle(SemanticAction.TEXT_SPACE)
        assertEquals("rf lux", field.text)
        assertEquals(SessionOutcome.EDGE, outcome(session) { repeat(10) { session.handle(SemanticAction.CURSOR_FORWARD) }; session.handle(SemanticAction.CURSOR_FORWARD) })
        session.press(KeyboardKey.Clear)
        assertEquals("", field.text)
        assertEquals(SessionOutcome.EDGE, outcome(session) { session.handle(SemanticAction.TEXT_ERASE) }, "nothing to erase is an edge, not a change")
    }

    private enum class SessionOutcome { HANDLED, EDGE, DONE }

    private fun outcome(session: KeyboardSession, block: () -> KeyboardSession.Outcome): SessionOutcome = SessionOutcome.valueOf(block().name)

    @Test
    fun aFieldThatFiltersIsWhatIsShown() {
        // A PIN takes four digits: the fifth is not typed, and the cursor stays at the end of what the field kept.
        val field = Field { typed -> typed.filter(Char::isDigit).take(4) }
        val session = KeyboardSession(field.request(InputKind.PIN))
        listOf("1", "2", "3", "4", "5").forEach { session.typeKey(it) }
        assertEquals("1234", field.text)
        assertEquals(4, session.cursor)
        assertTrue(session.secret)
    }

    @Test
    fun aPinIsANumberPadWithItsOwnErasingAndDone() {
        val rows = KeyboardSession.layout(InputKind.PIN, KeyboardPage.LETTERS)
        assertEquals(listOf("123", "456", "789"), rows.take(3).map { row -> row.joinToString("") { (it as KeyboardKey.Type).text } })
        assertEquals(listOf(KeyboardKey.Clear, KeyboardKey.Type("0"), KeyboardKey.Erase), rows[3])
        assertEquals(listOf(KeyboardKey.Done), rows[4])
        val session = KeyboardSession(Field().request(InputKind.PIN))
        assertEquals(KeyboardSession.Outcome.EDGE, session.handle(SemanticAction.TEXT_SPACE), "a PIN has no space")
    }

    @Test
    fun anAddressKeepsWhatAddressesNeedOnItsFirstPage() {
        val keys = KeyboardSession.layout(InputKind.ADDRESS, KeyboardPage.LETTERS).flatten().filterIsInstance<KeyboardKey.Type>().map { it.text }
        assertTrue(listOf(":", "/", ".", "-", "http://").all { it in keys }, "$keys")
        val field = Field()
        val session = KeyboardSession(field.request(InputKind.ADDRESS))
        session.typeKey("http://")
        "nas".forEach { session.typeKey(it.toString()) }
        session.typeKey(":")
        "8096".forEach { session.typeKey(it.toString()) }
        assertEquals("http://nas:8096", field.text)
    }

    @Test
    fun choosingMovesToTheNearestKeyAboveAndBelow() {
        val session = KeyboardSession(Field().request(InputKind.SEARCH))
        // Starts on the first letter row's first key.
        assertEquals(KeyboardKey.Type("q"), session.selected)
        repeat(4) { session.handle(SemanticAction.NAVIGATE_RIGHT) }
        assertEquals(KeyboardKey.Type("t"), session.selected)
        session.handle(SemanticAction.NAVIGATE_DOWN)
        assertEquals(KeyboardKey.Type("g"), session.selected)
        session.handle(SemanticAction.NAVIGATE_DOWN)
        session.handle(SemanticAction.NAVIGATE_DOWN)
        // The bottom row's keys are wider (F-P4's grid): below "b" is the cursor-back key, nearest in x.
        assertEquals(KeyboardKey.CursorBack, session.selected)
        assertEquals(KeyboardSession.Outcome.EDGE, session.handle(SemanticAction.NAVIGATE_DOWN), "nothing below the last row")
        session.handle(SemanticAction.NAVIGATE_UP)
        session.handle(SemanticAction.NAVIGATE_UP)
        session.handle(SemanticAction.NAVIGATE_UP)
        session.handle(SemanticAction.NAVIGATE_UP)
        assertEquals(0, session.row)
        assertEquals(KeyboardSession.Outcome.EDGE, session.handle(SemanticAction.NAVIGATE_UP))
        // F-P4 §4.4 (owner-approved): left and right wrap within the row, the action column its last stop.
        repeat(5) { session.handle(SemanticAction.NAVIGATE_LEFT) }
        assertEquals(0, session.row, "wrapping stays in the row")
        assertEquals(KeyboardKey.Erase, KeyboardSession(Field().request(InputKind.SEARCH)).let { s -> s.handle(SemanticAction.NAVIGATE_UP); s.handle(SemanticAction.NAVIGATE_LEFT); s.selected })
    }

    @Test
    fun shiftIsOnceThenLockedThenOffAndANameStartsWithACapital() {
        val name = Field()
        val session = KeyboardSession(name.request(InputKind.NAME))
        assertEquals(ShiftState.ONCE, session.shift, "a name's first letter is a capital")
        "sam".forEach { session.typeKey(it.toString()) }
        assertEquals("Sam", name.text)
        session.handle(SemanticAction.TEXT_SHIFT)
        session.handle(SemanticAction.TEXT_SHIFT)
        assertEquals(ShiftState.LOCKED, session.shift)
        "tv".forEach { session.typeKey(it.toString()) }
        assertEquals("SamTV", name.text)
        session.handle(SemanticAction.TEXT_SHIFT)
        assertEquals(ShiftState.OFF, session.shift)
        assertEquals(ShiftState.OFF, KeyboardSession(Field().request(InputKind.SEARCH)).shift, "a search starts in lower case")
    }

    @Test
    fun symbolsAreAPageAwayAndDoneFinishes() {
        val field = Field("a")
        val session = KeyboardSession(field.request(InputKind.SECRET))
        session.handle(SemanticAction.TEXT_SYMBOLS)
        assertEquals(KeyboardPage.SYMBOLS, session.page)
        session.typeKey("#")
        assertEquals("a#", field.text)
        session.handle(SemanticAction.TEXT_SYMBOLS)
        assertEquals(KeyboardPage.LETTERS, session.page)
        assertEquals(KeyboardSession.Outcome.DONE, session.handle(SemanticAction.TEXT_DONE))
        // Done is in the action column (F-P4 §4.1).
        session.choose(KeyboardKey.Done)
        assertEquals(KeyboardKey.Done, session.selected)
        assertEquals(KeyboardSession.Outcome.DONE, session.press(session.selected))
    }

    @Test
    fun aPhysicalKeyboardTypingMovesTheCursorToTheEnd() {
        val field = Field("abc")
        val session = KeyboardSession(field.request(InputKind.SEARCH))
        session.handle(SemanticAction.CURSOR_BACK)
        assertEquals(2, session.cursor)
        field.text = "abcd"
        assertEquals(4, session.cursor)
    }

    @Test
    fun whileTypingTheButtonsAreTheKeyboardsAndNothingIsAShortcut() {
        val xbox = ControllerFamily.XBOX
        fun button(b: GamepadButton) = InputMapping.map(b, xbox, InputContext.TEXT_ENTRY)
        assertEquals(SemanticAction.SELECT, button(GamepadButton.FACE_SOUTH))
        assertEquals(SemanticAction.BACK, button(GamepadButton.FACE_EAST))
        assertEquals(SemanticAction.TEXT_ERASE, button(GamepadButton.FACE_WEST))
        assertEquals(SemanticAction.TEXT_SPACE, button(GamepadButton.FACE_NORTH))
        assertEquals(SemanticAction.CURSOR_BACK, button(GamepadButton.SHOULDER_LEFT))
        assertEquals(SemanticAction.CURSOR_FORWARD, button(GamepadButton.SHOULDER_RIGHT))
        assertEquals(SemanticAction.TEXT_DONE, button(GamepadButton.START))
        // A keyboard's letters, Backspace, and the destination shortcuts are not the keyboard's: they reach the field.
        assertNull(InputMapping.map(Key.M, InputContext.TEXT_ENTRY))
        assertNull(InputMapping.map(Key.BACKSPACE, InputContext.TEXT_ENTRY))
        assertNull(InputMapping.map(Key.SHORTCUT_1, InputContext.TEXT_ENTRY))
        assertEquals(SemanticAction.BACK, InputMapping.map(Key.BACK, InputContext.TEXT_ENTRY))
        assertEquals(SemanticAction.SELECT, InputMapping.map(Key.DPAD_CENTER, InputContext.TEXT_ENTRY))
    }
}
