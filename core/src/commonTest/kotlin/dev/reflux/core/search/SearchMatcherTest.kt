package dev.reflux.core.search

import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchMatcherTest {
    private val docs = listOf(
        SearchDocument(MediaId("1"), MediaKind.MOVIE, "The Dark Knight", 2008),
        SearchDocument(MediaId("2"), MediaKind.MOVIE, "The Dark Knight Rises", 2012),
        SearchDocument(MediaId("3"), MediaKind.MOVIE, "Amélie", 2001),
        SearchDocument(MediaId("4"), MediaKind.MOVIE, "Dune", 1984),
        SearchDocument(MediaId("5"), MediaKind.MOVIE, "Dune", 2021),
        SearchDocument(MediaId("6"), MediaKind.SHOW, "Dark", 2017),
        SearchDocument(MediaId("7"), MediaKind.EPISODE, "Dune Sea", aliases = listOf("Star Wars")),
        SearchDocument(MediaId("8"), MediaKind.MOVIE, "Spider-Man: Into the Spider-Verse", 2018),
    )

    private fun ids(query: String) = SearchMatcher.search(query, docs).map { it.document.id.value }

    @Test
    fun exactTitleRanksFirst() {
        assertEquals("6", ids("dark").first())
        assertEquals(listOf("6", "1", "2"), ids("dark"))
    }

    @Test
    fun prefixesAndAccents() {
        assertEquals(listOf("3"), ids("ame"))
        assertEquals(listOf("3"), ids("AMELIE"))
        assertEquals(listOf("1", "2"), ids("dark kni"))
    }

    @Test
    fun yearNarrowsResults() {
        assertEquals(listOf("5"), ids("dune 2021"))
        assertEquals(listOf("4", "5", "7"), ids("dune"))
    }

    @Test
    fun punctuationInsensitive() {
        assertEquals(listOf("8"), ids("spider man into"))
    }

    @Test
    fun aliasesMatch() {
        assertEquals(listOf("7"), ids("star wars"))
    }

    @Test
    fun blankQueryFindsNothing() {
        assertTrue(ids("  ").isEmpty())
    }
}
