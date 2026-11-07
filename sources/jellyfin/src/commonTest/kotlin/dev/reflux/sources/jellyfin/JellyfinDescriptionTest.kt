package dev.reflux.sources.jellyfin

import dev.reflux.core.metadata.CreditRole
import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.CalendarDate
import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.net.HttpResult
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a Jellyfin server says its items are about, read with the catalog (Jellyfin 10.9+ shapes, trimmed). */
class JellyfinDescriptionTest {
    private val urls = mutableListOf<String>()
    private var seasonsAnswer: () -> HttpResult = {
        HttpResult(
            200,
            """{"Items":[{"Id":"season1","Name":"Season One","Overview":"Walt begins.","PremiereDate":"2008-01-20T00:00:00.0000000Z",
               "ImageTags":{"Primary":"x"}}]}""".encodeToByteArray(),
        )
    }

    private val movie = """
        {"Id":"m1","Name":"Heat","OriginalTitle":"Heat","Type":"Movie","ProductionYear":1995,"RunTimeTicks":102000000000,
         "PremiereDate":"1995-12-15T00:00:00.0000000Z","Overview":"A cop and a thief.","Taglines":["A Los Angeles crime saga"],
         "Genres":["Crime","Drama"],"CommunityRating":7.9,"OfficialRating":"R","Studios":[{"Name":"Warner Bros."}],
         "People":[{"Name":"Michael Mann","Id":"p0","Type":"Director"},{"Name":"Al Pacino","Id":"p1","Role":"Vincent Hanna","Type":"Actor","PrimaryImageTag":"t"},
                   {"Name":"Robert De Niro","Id":"p2","Role":"Neil McCauley","Type":"Actor"},{"Name":"Producer","Id":"p3","Type":"Producer"}],
         "MediaSources":[{"Id":"s1","Container":"mkv","Size":1,"MediaStreams":[]}]}
    """.trimIndent()

    private val episode = """
        {"Id":"e1","Name":"Pilot","Type":"Episode","SeriesId":"show1","SeriesName":"Breaking Bad","SeasonId":"season1",
         "ParentIndexNumber":1,"IndexNumber":1,"Overview":"A teacher turns.","PremiereDate":"2008-01-20T00:00:00.0000000Z",
         "CommunityRating":8.2,"RunTimeTicks":34800000000,
         "People":[{"Name":"Raymond Cruz","Id":"g1","Role":"Tuco","Type":"GuestStar"}],
         "ImageTags":{"Primary":"t"},"MediaSources":[{"Id":"e1","Container":"mkv","Size":1,"MediaStreams":[]}]}
    """.trimIndent()

    private val http = HttpFetcher { request ->
        urls += request.url
        val path = request.url.removePrefix("https://jf.example.com/").substringBefore('?')
        val query = request.url.substringAfter('?', "")
        when {
            path == "UserViews" -> HttpResult(200, """{"Items":[]}""".encodeToByteArray())
            path == "Items" && "IncludeItemTypes=Series" in query -> HttpResult(
                200,
                """{"Items":[{"Id":"show1","Name":"Breaking Bad","ProductionYear":2008,"Overview":"A chemistry teacher.",
                   "PremiereDate":"2008-01-20T00:00:00.0000000Z","EndDate":"2013-09-29T00:00:00.0000000Z","Genres":["Drama"],
                   "Studios":[{"Name":"AMC"}],"OfficialRating":"TV-MA","ProviderIds":{"Tmdb":"1396"}}]}""".encodeToByteArray(),
            )
            path == "Items" && "IncludeItemTypes=Season" in query -> seasonsAnswer()
            path == "Items" && "StartIndex=0" in query -> HttpResult(200, """{"Items":[$movie,$episode],"TotalRecordCount":2}""".encodeToByteArray())
            else -> HttpResult(200, """{"Items":[],"TotalRecordCount":2}""".encodeToByteArray())
        }
    }

    private val source = JellyfinSource(JellyfinCredentials("https://jf.example.com", "srv1", "u1", "tok", "dev1"), http)

    @Test
    fun aMovieIsDescribedAsTheServerHasIt() = runTest {
        val heat = source.catalog().toList().first().description!!
        assertEquals("Heat", heat.title)
        assertNull(heat.originalTitle, "the same name again is not an original title")
        assertEquals("A cop and a thief.", heat.overview)
        assertEquals("A Los Angeles crime saga", heat.tagline)
        assertEquals(listOf("Crime", "Drama"), heat.genres)
        assertEquals(170, heat.runtimeMinutes)
        assertEquals(CalendarDate(1995, 12, 15), heat.releaseDate)
        assertEquals(7.9, heat.rating)
        assertEquals("R", heat.contentRating)
        assertEquals(listOf("Warner Bros."), heat.studios)
        // Directors first, then the cast; producers are not credits Reflux shows.
        assertEquals(listOf("Michael Mann", "Al Pacino", "Robert De Niro"), heat.credits.map { it.name })
        assertEquals(CreditRole.DIRECTOR, heat.credits.first().role)
        assertEquals("Vincent Hanna", heat.credits[1].character)
        assertEquals("items/p1/images/Primary", heat.credits[1].profileUrl, "a picture the server has, as a path in it")
        assertNull(heat.credits[2].profileUrl)
        assertTrue(urls.first { "StartIndex=0" in it }.contains("People"), "asked for with the catalog, not item by item")
    }

    @Test
    fun anEpisodeCarriesItsShowAndItsSeason() = runTest {
        val entry = source.catalog().toList().last()
        assertEquals("A teacher turns.", entry.description!!.overview)
        assertEquals(58, entry.description!!.runtimeMinutes)
        assertEquals(8.2, entry.description!!.rating)
        assertEquals(listOf("Raymond Cruz"), entry.description!!.credits.map { it.name }, "its guest cast")
        val show = entry.showDescription!!
        assertEquals("A chemistry teacher.", show.overview)
        assertEquals(CalendarDate(2013, 9, 29), show.endDate)
        assertEquals(listOf("AMC"), show.studios)
        val season = entry.season!!
        assertEquals("Season One", season.title)
        assertEquals("Walt begins.", season.overview)
        assertEquals(CalendarDate(2008, 1, 20), season.airDate)
        assertEquals(mapOf(ArtworkKind.POSTER to "items/season1/images/Primary"), season.artwork)
    }

    @Test
    fun aServerThatCannotListSeasonsStillListsItsEpisodes() = runTest {
        seasonsAnswer = { HttpResult(500, "".encodeToByteArray()) }
        val entries = source.catalog().toList()
        assertEquals(2, entries.size)
        assertNull(entries.last().season)
        assertEquals("A teacher turns.", entries.last().description!!.overview)
    }
}
