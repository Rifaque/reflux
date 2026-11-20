package dev.reflux.ui.state

import dev.reflux.core.input.SemanticAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** F-P4's layouts as data (§4, §5, §6) and the session's focus mapping across presentations (§9). */
class KeyboardLayoutsTest {
    private fun all(): List<KeyboardLayout> = InputKind.entries.flatMap { kind ->
        KeyboardPage.entries.flatMap { page -> listOf(KeyboardLayouts.controller(kind, page), KeyboardLayouts.pointer(kind, page)) }
    }

    @Test
    fun noKeysOverlap_everyKeyIsInsideTheLayout_andNoKeyIsTwice() {
        for (layout in all()) {
            assertEquals(layout.keys.size, layout.keys.map { it.key }.toSet().size, "a key once: ${layout.keys.map { it.key }}")
            for (a in layout.keys) {
                assertTrue(a.x >= 0f && a.y >= 0f && a.x + a.width <= layout.width + 1e-3f, "inside: $a")
                for (b in layout.keys) if (a !== b) {
                    val apart = a.x + a.width <= b.x + 1e-3f || b.x + b.width <= a.x + 1e-3f || a.y + a.height <= b.y + 1e-3f || b.y + b.height <= a.y + 1e-3f
                    assertTrue(apart, "$a overlaps $b")
                }
            }
        }
    }

    @Test
    fun theControllerGridIsColumnAlignedQwertyWithAnActionColumn() {
        val grid = KeyboardLayouts.controller(InputKind.SEARCH, KeyboardPage.LETTERS)
        assertEquals("1234567890", grid.rows[0].take(10).joinToString("") { (it.key as KeyboardKey.Type).text })
        assertEquals("qwertyuiop", grid.rows[1].take(10).joinToString("") { (it.key as KeyboardKey.Type).text })
        // Aligned: q above a above z, column for column.
        assertEquals(grid.place(KeyboardKey.Type("q"))!!.x, grid.place(KeyboardKey.Type("a"))!!.x)
        assertEquals(grid.place(KeyboardKey.Type("w"))!!.x, grid.place(KeyboardKey.Type("x"))!!.x)
        // The action column: Erase, Space, Done at the right of the first three rows.
        assertEquals(listOf(KeyboardKey.Erase, KeyboardKey.Space, KeyboardKey.Done), (0..2).map { grid.rows[it].last().key })
        assertEquals(listOf(KeyboardKey.Shift, KeyboardKey.Page, KeyboardKey.CursorBack, KeyboardKey.CursorForward, KeyboardKey.Clear), grid.rows[4].map { it.key })
    }

    @Test
    fun leftAndRightWrapWithinARow_upAndDownKeepTheColumnAndStopAtTheEdges() {
        val grid = KeyboardLayouts.controller(InputKind.SEARCH, KeyboardPage.LETTERS)
        assertEquals(KeyboardKey.Space, grid.neighbour(KeyboardKey.Type("p"), 1, 0), "right of p is the action column")
        assertEquals(KeyboardKey.Type("q"), grid.neighbour(KeyboardKey.Space, 1, 0), "and right of that wraps to the row's start")
        assertEquals(KeyboardKey.Space, grid.neighbour(KeyboardKey.Type("q"), -1, 0), "left wraps to the row's end")
        assertEquals(KeyboardKey.Type("a"), grid.neighbour(KeyboardKey.Type("q"), 0, 1))
        assertEquals(KeyboardKey.Type("e"), grid.neighbour(KeyboardKey.Type("d"), 0, -1))
        assertNull(grid.neighbour(KeyboardKey.Type("1"), 0, -1), "the top is a boundary")
        assertNull(grid.neighbour(KeyboardKey.Clear, 0, 1), "and so is the bottom")
    }

    @Test
    fun theDeskKeyboardIsStaggeredAnsiWithItsSpecialKeys() {
        val desk = KeyboardLayouts.pointer(InputKind.SEARCH, KeyboardPage.LETTERS)
        assertEquals(15f, desk.width)
        val q = desk.place(KeyboardKey.Type("q"))!!
        val a = desk.place(KeyboardKey.Type("a"))!!
        val z = desk.place(KeyboardKey.Type("z"))!!
        assertEquals(listOf(1.5f, 1.75f, 2.25f), listOf(q.x, a.x, z.x), "the stagger of a physical board")
        for (key in listOf(KeyboardKey.Caps, KeyboardKey.Done, KeyboardKey.Shift, KeyboardKey.ShiftRight, KeyboardKey.Hide, KeyboardKey.Erase)) assertNotNull(desk.place(key), "$key")
        assertTrue(desk.place(KeyboardKey.Space)!!.width >= 6f, "a wide space bar")
        assertNull(desk.note, "US is no fallback note when it is the system's own")
    }

    @Test
    fun theDeskKeyboardFollowsTheSystemLayout_andEveryKeyTypesWhatItShows() {
        // French AZERTY, as Windows reads it: the first rows' letters and the digits' row of symbols.
        val azerty = SystemKeymap(
            "AZERTY",
            listOf(
                listOf("²", "&1", "é2", "\"3", "'4", "(5", "-6", "è7", "_8", "ç9", "à0", ")°", "=+").map { SystemKey(it.take(1), it.drop(1).ifEmpty { it.take(1) }) },
                listOf("aA", "zZ", "eE", "rR", "tT", "yY", "uU", "iI", "oO", "pP", "^¨", "$£", "*µ").map { SystemKey(it.take(1), it.drop(1)) },
                listOf("qQ", "sS", "dD", "fF", "gG", "hH", "jJ", "kK", "lL", "mM", "ù%").map { SystemKey(it.take(1), it.drop(1)) },
                listOf("wW", "xX", "cC", "vV", "bB", "nN", ",?", ";.", ":/", "!§").map { SystemKey(it.take(1), it.drop(1)) },
            ),
            iso = SystemKey("<", ">"),
        )
        val desk = KeyboardLayouts.pointer(InputKind.SEARCH, KeyboardPage.LETTERS, azerty)
        val firstLetter = desk.rows.first { row -> row.any { it.key == KeyboardKey.Type("z") } }.first { it.key is KeyboardKey.Type }
        assertEquals("a", (firstLetter.key as KeyboardKey.Type).text, "the top letter row starts A, Z")
        assertNotNull(desk.place(KeyboardKey.Type("<")), "the ISO key beside Shift")
        var text = ""
        val session = KeyboardSession(KeyboardRequest(this, "Search", InputKind.SEARCH, { text }, { text = it }, {}), KeyboardMode.POINTER, azerty)
        // Shift then the digit row's first key types 1, as AZERTY does.
        session.click(KeyboardKey.Shift)
        session.click(desk.keys.first { (it.key as? KeyboardKey.Type)?.text == "&" }.key)
        session.click(KeyboardKey.Type("a"))
        assertEquals("1a", text)
    }

    @Test
    fun switchingPresentationsKeepsEverything_andFocusFollowsTheKey() {
        var text = ""
        val session = KeyboardSession(KeyboardRequest(this, "Search", InputKind.SEARCH, { text }, { text = it }, {}))
        repeat(3) { session.handle(SemanticAction.NAVIGATE_RIGHT) }
        assertEquals(KeyboardKey.Type("r"), session.chosen)
        session.handle(SemanticAction.SELECT)
        session.handle(SemanticAction.TEXT_SHIFT)
        session.show(KeyboardMode.POINTER)
        assertEquals("r", text)
        assertEquals(ShiftState.ONCE, session.shift, "Shift kept across the switch")
        // A click types the key under it, as drawn.
        session.click(KeyboardKey.Type("e"))
        assertEquals("rE", text)
        session.show(KeyboardMode.CONTROLLER)
        assertEquals(KeyboardKey.Type("e"), session.chosen, "focus goes to the key last clicked, not to the first key")
        assertEquals(2, session.cursor)
        // Into the pointer and back without a click: the controller's own key.
        session.handle(SemanticAction.NAVIGATE_DOWN)
        val before = session.chosen
        session.show(KeyboardMode.POINTER)
        session.show(KeyboardMode.CONTROLLER)
        assertEquals(before, session.chosen)
    }

    @Test
    fun aKeyOnlyTheDeskHasMapsToTheNearestGridKey() {
        var text = ""
        val session = KeyboardSession(KeyboardRequest(this, "Search", InputKind.SEARCH, { text }, { text = it }, {}), KeyboardMode.POINTER)
        session.hovering(KeyboardKey.Type("["))
        session.show(KeyboardMode.CONTROLLER)
        assertTrue(session.chosen in KeyboardLayouts.controller(InputKind.SEARCH, KeyboardPage.LETTERS), "a key the grid has: ${session.chosen}")
        assertEquals(KeyboardSession.Outcome.HIDE, session.click(KeyboardKey.Hide))
    }

    @Test
    fun completionsNeverTakeFocus_UpReachesThem_andChoosingFillsTheQueryAndKeepsTyping() {
        var text = "Bla"
        val session = KeyboardSession(KeyboardRequest(this, "Search", InputKind.SEARCH, { text }, { text = it }, {}))
        session.offer(listOf("Blade Runner", "Blade Runner 2049"))
        assertNull(session.completion, "offered, not focused")
        session.handle(SemanticAction.NAVIGATE_UP)
        session.handle(SemanticAction.NAVIGATE_UP)
        assertNotNull(session.completion, "up from the top row reaches them")
        session.handle(SemanticAction.NAVIGATE_RIGHT)
        assertEquals(KeyboardSession.Outcome.HANDLED, session.handle(SemanticAction.SELECT))
        assertEquals("Blade Runner 2049", text)
        assertEquals(text.length, session.cursor)
        session.handle(SemanticAction.NAVIGATE_DOWN)
        assertNull(session.completion)
        // The completions emptying while focused gives focus back to the keys.
        session.handle(SemanticAction.NAVIGATE_UP)
        session.handle(SemanticAction.NAVIGATE_UP)
        session.offer(emptyList())
        assertNull(session.completion)
    }
}
