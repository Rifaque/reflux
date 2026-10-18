package dev.reflux.ui.state

import kotlin.test.Test
import kotlin.test.assertEquals

class IdentifyQueryTest {
    private fun parse(text: String) = IdentifyQuery.parse(text, latestYear = 2027)

    @Test
    fun aTrailingYearIsTheYearAndTheRestIsTheTitle() {
        assertEquals("Dune" to 1984, parse("Dune 1984"))
        assertEquals("Dune" to 1984, parse("  Dune (1984) "))
        assertEquals("Dune" to 1984, parse("Dune [1984]"))
        assertEquals("The Thing" to 1982, parse("The Thing.1982"))
        // The page starts from the work's own name and year, which reads back the same way.
        assertEquals("Heat" to 1995, parse(IdentifyQuery.of("Heat", 1995)))
        assertEquals("Heat (1995)", IdentifyQuery.of("Heat", 1995))
        assertEquals("Heat", IdentifyQuery.of("Heat", null))
    }

    @Test
    fun aNumberThatCannotBeAYearIsPartOfTheTitle() {
        assertEquals("Blade Runner 2049" to null, parse("Blade Runner 2049"))
        assertEquals("1917" to null, parse("1917"))
        assertEquals("2001: A Space Odyssey" to null, parse("2001: A Space Odyssey"))
        assertEquals("Fahrenheit 0451" to null, parse("Fahrenheit 0451"))
        assertEquals("Ocean's Eleven" to null, parse("Ocean's Eleven"))
        assertEquals("" to null, parse("   "))
    }
}
