package dev.reflux.sources.plex

import dev.reflux.core.metadata.CreditRole
import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.CalendarDate
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What a Plex Media Server says its items are about, read with the catalog. */
class PlexDescriptionTest {
    private val plex = FakePlex()
    private val source = PlexSource(PlexCredentials("http://192.168.1.20:32400", "machine-1", "srv-token", "dev-1", "alex"), plex.http, "Living Room")

    @Test
    fun aFilmIsDescribedAsTheServerHasIt() = runTest {
        val heat = source.catalog().toList().first { it.identity.title == "Heat" }.description!!
        assertEquals("About Heat.", heat.overview)
        assertEquals("Heat, the tagline", heat.tagline)
        assertEquals(listOf("Crime", "Drama"), heat.genres)
        assertEquals(100, heat.runtimeMinutes)
        assertEquals(CalendarDate(1995, 12, 15), heat.releaseDate)
        assertEquals(7.9, heat.rating, "the audience's rating, not the critics' score")
        assertEquals("R", heat.contentRating)
        assertEquals(listOf("A Studio"), heat.studios)
        assertEquals(listOf("Michael Mann", "Al Pacino", "Val Kilmer"), heat.credits.map { it.name })
        assertEquals(CreditRole.DIRECTOR, heat.credits.first().role)
        assertEquals("Vincent Hanna", heat.credits[1].character)
        assertEquals("https://metadata-static.plex.tv/p.jpg", heat.credits[1].profileUrl, "Plex's own address, as given")
        assertEquals("images/library/people/2/thumb", heat.credits[2].profileUrl, "a path on the server")
    }

    @Test
    fun anEpisodeCarriesItsShowAndItsSeason() = runTest {
        val entry = source.catalog().toList().first { it.identity.episode == 1 }
        assertEquals("Episode 1 happens.", entry.description!!.overview)
        assertEquals(45, entry.description!!.runtimeMinutes)
        val show = entry.showDescription!!
        assertEquals("Work and life, severed.", show.overview)
        assertEquals(listOf("Apple TV+"), show.studios)
        assertEquals(8.7, show.rating)
        val season = entry.season!!
        assertEquals("Season 1", season.title)
        assertEquals("Mark leads a team.", season.overview)
        assertEquals(mapOf(ArtworkKind.POSTER to "images/library/metadata/210/thumb/1"), season.artwork)
        assertNull(source.catalog().toList().first { it.identity.title == "Heat" }.season)
    }
}
