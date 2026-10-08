package dev.reflux.core.metadata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MetadataMatcherTest {
    private fun query(title: String, year: Int?, kind: MetadataKind = MetadataKind.MOVIE) =
        MetadataQuery(kind, title, year, language = "en-US")

    private fun candidate(id: String, title: String, year: Int?, popularity: Double = 10.0, original: String? = null) =
        MetadataCandidate(ProviderRef("tmdb", MetadataKind.MOVIE, id), title, original, year, popularity = popularity)

    @Test
    fun exactTitleAndYearIsAccepted() {
        val result = MetadataMatcher.match(query("Interstellar", 2014), listOf(candidate("1", "Interstellar", 2014)))
        assertEquals("1", assertIs<MatchResult.Accepted>(result).candidate.ref.id)
    }

    @Test
    fun yearSeparatesRemakes() {
        val candidates = listOf(candidate("84", "Dune", 1984, 30.0), candidate("21", "Dune", 2021, 100.0))
        assertEquals("84", assertIs<MatchResult.Accepted>(MetadataMatcher.match(query("Dune", 1984), candidates)).candidate.ref.id)
    }

    @Test
    fun withoutYearDecisivePopularityBreaksExactTies() {
        val candidates = listOf(candidate("84", "Dune", 1984, 30.0), candidate("21", "Dune", 2021, 100.0))
        assertEquals("21", assertIs<MatchResult.Accepted>(MetadataMatcher.match(query("Dune", null), candidates)).candidate.ref.id)
        val close = listOf(candidate("a", "Solaris", 1972, 40.0), candidate("b", "Solaris", 2002, 50.0))
        assertIs<MatchResult.Ambiguous>(MetadataMatcher.match(query("Solaris", null), close))
    }

    @Test
    fun releaseYearOffByOneStillMatches() {
        val result = MetadataMatcher.match(query("Parasite", 2020), listOf(candidate("1", "Parasite", 2019)))
        assertIs<MatchResult.Accepted>(result)
    }

    @Test
    fun wrongYearIsNotAccepted() {
        val result = MetadataMatcher.match(query("Heat", 1995), listOf(candidate("1", "Heat", 2013)))
        assertIs<MatchResult.Ambiguous>(result)
    }

    @Test
    fun originalTitlesAndQualifiersMatch() {
        assertIs<MatchResult.Accepted>(
            MetadataMatcher.match(query("Amélie", 2001), listOf(candidate("1", "Amélie", 2001, original = "Le Fabuleux Destin d'Amélie Poulain"))),
        )
        assertIs<MatchResult.Accepted>(
            MetadataMatcher.match(query("Le Fabuleux Destin d'Amelie Poulain", 2001), listOf(candidate("1", "Amélie", 2001, original = "Le Fabuleux Destin d'Amélie Poulain"))),
        )
        assertIs<MatchResult.Accepted>(
            MetadataMatcher.match(query("The Office (US)", 2005, MetadataKind.SHOW), listOf(candidate("1", "The Office", 2005))),
        )
    }

    @Test
    fun unrelatedResultsAreNoMatch() {
        assertEquals(MatchResult.NoMatch, MetadataMatcher.match(query("Primer", 2004), listOf(candidate("1", "Prime Suspect", 1991))))
        assertEquals(MatchResult.NoMatch, MetadataMatcher.match(query("Primer", 2004), emptyList()))
    }

    @Test
    fun similarityIsArticleAndPunctuationInsensitive() {
        assertTrue(MetadataMatcher.titleSimilarity("Office", "The Office") >= 0.95)
        assertEquals(1.0, MetadataMatcher.titleSimilarity("Spider-Man: No Way Home", "Spider Man No Way Home"))
    }

    @Test
    fun providerRefsRoundTrip() {
        val ref = ProviderRef("tmdb", MetadataKind.SHOW, "1396")
        assertEquals(ref, ProviderRef.parse(ref.toString()))
    }
}
