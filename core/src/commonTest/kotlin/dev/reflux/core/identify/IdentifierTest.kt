package dev.reflux.core.identify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class IdentifierTest {
    private val parser = MediaPathParser(maxYear = 2026)

    private fun identify(path: String, identifier: Identifier = Identifier()) =
        identifier.identify(parser.parse(path)!!)!!

    @Test
    fun differentlyNamedFilesOfTheSameMovieShareIdentity() {
        val a = identify("Movies/The Dark Knight (2008)/The Dark Knight (2008).mkv")
        val b = identify("Downloads/The.Dark.Knight.2008.2160p.UHD.BluRay.x265-GRP.mkv")
        assertEquals(a.playable.id, b.playable.id)
    }

    @Test
    fun editionsAreVersionsOfTheSameMovie() {
        val theatrical = identify("Blade Runner (1982).mkv")
        val finalCut = identify("Blade Runner (1982) Final Cut.mkv")
        assertEquals(theatrical.playable.id, finalCut.playable.id)
    }

    @Test
    fun remakesAreDifferentWorks() {
        assertNotEquals(identify("Dune (1984).mkv").playable.id, identify("Dune (2021).mkv").playable.id)
    }

    @Test
    fun yearlessFileJoinsTheOnlyKnownYear() {
        val identifier = Identifier { _, key -> if (key == "dune") setOf(2021) else emptySet() }
        val yearless = identify("Dune.mkv", identifier)
        assertEquals(identify("Dune (2021).mkv").playable.id, yearless.playable.id)
    }

    @Test
    fun yearlessFileStaysSeparateWhenAmbiguous() {
        val identifier = Identifier { _, key -> if (key == "dune") setOf(1984, 2021) else emptySet() }
        val yearless = identify("Dune.mkv", identifier)
        val movie = assertIs<Identification.OfMovie>(yearless).movie
        assertNull(movie.year)
    }

    @Test
    fun episodesShareShowAndSeason() {
        val e1 = assertIs<Identification.OfEpisode>(identify("TV/Lost/Season 1/Lost.S01E01.mkv"))
        val e2 = assertIs<Identification.OfEpisode>(identify("Lost.S01E02.720p.HDTV.mkv"))
        assertEquals(e1.show.id, e2.show.id)
        assertEquals(e1.season.id, e2.season.id)
        assertNotEquals(e1.episode.id, e2.episode.id)
        assertEquals(2, e2.episode.episodeNumber)
    }

    @Test
    fun sameEpisodeInDifferentQualitiesSharesIdentity() {
        val a = identify("TV/Lost/Season 1/Lost - S01E01 - Pilot (1).mkv")
        val b = identify("Lost.S01E01.1080p.BluRay.mkv")
        assertEquals(a.playable.id, b.playable.id)
    }

    @Test
    fun overridesReplaceIdentityButKeepTechnicalFacts() {
        val parsed = parser.parse("Movies/Unknown.Thing.2160p.HDR.mkv")!!
        val corrected = IdentityOverride(ParsedKind.MOVIE, "Arrival", 2016).applyTo(parsed)
        val identification = Identifier().identify(corrected)!!
        assertEquals(identify("Arrival (2016).mkv").playable.id, identification.playable.id)
        assertEquals(2160, corrected.stream.video?.height)
        assertEquals(ConfidenceLevel.HIGH, corrected.confidence.level)
    }

    @Test
    fun yearHintsAreCollectedPerKindAndTitle() {
        val hints = Identifier.yearHintsOf(
            listOf("Dune (2021).mkv", "Dune (1984).mkv", "Doctor Who (2005)/Season 1/S01E01.mkv").map { parser.parse(it)!! },
        )
        assertEquals(setOf(1984, 2021), hints[ParsedKind.MOVIE to "dune"])
        assertEquals(setOf(2005), hints[ParsedKind.EPISODE to "doctor who"])
    }
}
