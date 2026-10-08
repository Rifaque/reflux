package dev.reflux.metadata.tmdb

import dev.reflux.core.metadata.CreditRole
import dev.reflux.core.metadata.MetadataKind
import dev.reflux.core.metadata.MetadataQuery
import dev.reflux.core.metadata.MetadataUnavailableException
import dev.reflux.core.metadata.ProviderRef
import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.CalendarDate
import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.net.HttpResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Responses follow the documented TMDB v3 shapes, trimmed to the fields Reflux reads. */
class TmdbProviderTest {
    private val requests = mutableListOf<Pair<String, Map<String, String>>>()
    private var routes: Map<String, () -> HttpResult> = emptyMap()

    private val http = HttpFetcher { url, headers ->
        requests += url to headers
        val path = url.removePrefix("https://api.themoviedb.org/3/").substringBefore('?')
        routes[path]?.invoke() ?: HttpResult(404, "{}".encodeToByteArray())
    }

    private fun ok(json: String) = { HttpResult(200, json.encodeToByteArray()) }

    private val token = "eyJhbGciOiJIUzI1NiJ9.payload.signature"
    private val tmdb = TmdbProvider(token, http)

    @Test
    fun searchesMoviesWithYearAndBearerToken() = runTest {
        routes = mapOf(
            "search/movie" to ok(
                """{"page":1,"results":[{"id":157336,"title":"Interstellar","original_title":"Interstellar",
                "release_date":"2014-11-05","overview":"Space.","popularity":120.5,"poster_path":"/p.jpg"},
                {"id":1,"title":"Broken","release_date":"","popularity":null}],"total_results":2}""",
            ),
        )
        val results = tmdb.search(MetadataQuery(MetadataKind.MOVIE, "Interstellar", 2014, language = "en-US"))
        assertEquals(ProviderRef("tmdb", MetadataKind.MOVIE, "157336"), results.first().ref)
        assertEquals(2014, results.first().year)
        assertEquals("https://image.tmdb.org/t/p/w780/p.jpg", results.first().posterUrl)
        assertNull(results[1].year)

        val (url, headers) = requests.single()
        assertTrue("primary_release_year=2014" in url)
        assertTrue("query=Interstellar" in url)
        assertEquals("Bearer $token", headers["Authorization"])
    }

    @Test
    fun searchRetriesWithoutAWrongYear() = runTest {
        var calls = 0
        routes = mapOf(
            "search/tv" to {
                calls++
                if (calls == 1) HttpResult(200, """{"results":[]}""".encodeToByteArray())
                else HttpResult(200, """{"results":[{"id":1396,"name":"Breaking Bad","first_air_date":"2008-01-20"}]}""".encodeToByteArray())
            },
        )
        val results = tmdb.search(MetadataQuery(MetadataKind.SHOW, "Breaking Bad", 2009, language = "en-US"))
        assertEquals("1396", results.single().ref.id)
        assertTrue("first_air_date_year=2009" in requests[0].first)
        assertTrue("first_air_date_year" !in requests[1].first)
    }

    @Test
    fun v3KeysUseTheQueryParameter() = runTest {
        routes = mapOf("search/movie" to ok("""{"results":[]}"""))
        TmdbProvider("abc123", http).search(MetadataQuery(MetadataKind.MOVIE, "Heat", null, language = "en"))
        assertTrue("api_key=abc123" in requests.single().first)
        assertNull(requests.single().second["Authorization"])
    }

    @Test
    fun findsByImdbId() = runTest {
        routes = mapOf("find/tt0133093" to ok("""{"movie_results":[{"id":603}],"tv_results":[]}"""))
        assertEquals(ProviderRef("tmdb", MetadataKind.MOVIE, "603"), tmdb.findByExternalId(MetadataKind.MOVIE, "imdb", "tt0133093"))
        assertTrue("external_source=imdb_id" in requests.single().first)
        assertNull(tmdb.findByExternalId(MetadataKind.SHOW, "imdb", "tt0133093"))
    }

    @Test
    fun movieDetails() = runTest {
        routes = mapOf(
            "movie/603" to ok(
                """{"id":603,"title":"The Matrix","original_title":"The Matrix","overview":"Neo.","tagline":"Welcome.",
                "genres":[{"id":28,"name":"Action"},{"id":878,"name":"Science Fiction"}],"runtime":136,
                "release_date":"1999-03-30","vote_average":8.2,"imdb_id":"tt0133093",
                "credits":{"cast":[{"name":"Carrie-Anne Moss","character":"Trinity","order":2,"profile_path":null},
                                   {"name":"Keanu Reeves","character":"Neo","order":0,"profile_path":"/k.jpg"}],
                           "crew":[{"name":"Lana Wachowski","job":"Director"},{"name":"Bill Pope","job":"Director of Photography"}]},
                "images":{"posters":[{"file_path":"/en.jpg","iso_639_1":"en","vote_average":5.5}],
                          "backdrops":[{"file_path":"/b.jpg","iso_639_1":null,"vote_average":5.3}],
                          "logos":[{"file_path":"/l.png","iso_639_1":"en","vote_average":5.0}]},
                "release_dates":{"results":[{"iso_3166_1":"DE","release_dates":[{"certification":"16"}]},
                                            {"iso_3166_1":"US","release_dates":[{"certification":""},{"certification":"R"}]}]},
                "external_ids":{"imdb_id":"tt0133093"}}""",
            ),
        )
        val movie = tmdb.details(ProviderRef("tmdb", MetadataKind.MOVIE, "603"), "en-US")!!
        assertEquals("The Matrix", movie.title)
        assertEquals(listOf("Action", "Science Fiction"), movie.genres)
        assertEquals(136, movie.runtimeMinutes)
        assertEquals(CalendarDate(1999, 3, 30), movie.releaseDate)
        assertEquals("R", movie.contentRating)
        assertEquals(listOf("Lana Wachowski", "Keanu Reeves", "Carrie-Anne Moss"), movie.credits.map { it.name })
        assertEquals(CreditRole.DIRECTOR, movie.credits.first().role)
        assertEquals("Neo", movie.credits[1].character)
        assertEquals("https://image.tmdb.org/t/p/w185/k.jpg", movie.credits[1].profileUrl)
        assertEquals(setOf(ArtworkKind.POSTER, ArtworkKind.BACKDROP, ArtworkKind.LOGO), movie.artwork.map { it.kind }.toSet())
        assertEquals("https://image.tmdb.org/t/p/w1280/b.jpg", movie.artwork.single { it.kind == ArtworkKind.BACKDROP }.url)
        assertEquals(mapOf("imdb" to "tt0133093", "tmdb" to "603"), movie.externalIds)
        assertTrue("include_image_language=en%2Cnull" in requests.single().first)
    }

    @Test
    fun showDetailsAndSeasons() = runTest {
        routes = mapOf(
            "tv/1396" to ok(
                """{"id":1396,"name":"Breaking Bad","original_name":"Breaking Bad","episode_run_time":[45,47],
                "first_air_date":"2008-01-20","vote_average":8.9,"created_by":[{"name":"Vince Gilligan"}],
                "credits":{"cast":[{"name":"Bryan Cranston","character":"Walter White","order":0}]},
                "content_ratings":{"results":[{"iso_3166_1":"US","rating":"TV-MA"}]},
                "external_ids":{"imdb_id":"tt0903747","tvdb_id":81189}}""",
            ),
            "tv/1396/season/1" to ok(
                """{"season_number":1,"name":"Season 1","poster_path":"/s1.jpg","episodes":[
                {"episode_number":1,"season_number":1,"name":"Pilot","air_date":"2008-01-20","runtime":58,"still_path":"/e1.jpg"},
                {"episode_number":2,"season_number":1,"name":"Cat's in the Bag...","air_date":"2008-01-27"}]}""",
            ),
        )
        val ref = ProviderRef("tmdb", MetadataKind.SHOW, "1396")
        val show = tmdb.details(ref, "en-US")!!
        assertEquals(45, show.runtimeMinutes)
        assertEquals("TV-MA", show.contentRating)
        assertEquals(CreditRole.CREATOR, show.credits.first().role)
        assertEquals(mapOf("imdb" to "tt0903747", "tvdb" to "81189", "tmdb" to "1396"), show.externalIds)

        val season = tmdb.season(ref, 1, "en-US")!!
        assertEquals("https://image.tmdb.org/t/p/w780/s1.jpg", season.posterUrl)
        assertEquals(listOf("Pilot", "Cat's in the Bag..."), season.episodes.map { it.title })
        assertEquals("https://image.tmdb.org/t/p/w780/e1.jpg", season.episodes.first().stillUrl)
        assertNull(tmdb.season(ref, 9, "en-US"))
    }

    @Test
    fun failuresMapToUnavailable() = runTest {
        routes = mapOf("search/movie" to { HttpResult(401, """{"status_code":7}""".encodeToByteArray()) })
        assertFailsWith<MetadataUnavailableException> { tmdb.search(MetadataQuery(MetadataKind.MOVIE, "x", null, language = "en")) }

        val offline = TmdbProvider(token, HttpFetcher { _, _ -> throw IllegalStateException("no network") })
        assertFailsWith<MetadataUnavailableException> { offline.search(MetadataQuery(MetadataKind.MOVIE, "x", null, language = "en")) }
    }

    @Test
    fun rateLimitIsRetried() = runTest {
        var calls = 0
        routes = mapOf(
            "search/movie" to {
                calls++
                if (calls < 3) HttpResult(429, ByteArray(0), mapOf("retry-after" to "1"))
                else HttpResult(200, """{"results":[]}""".encodeToByteArray())
            },
        )
        tmdb.search(MetadataQuery(MetadataKind.MOVIE, "x", null, language = "en"))
        assertEquals(3, calls)
    }

    @Test
    fun queryEncoding() {
        assertEquals("Am%C3%A9lie%20%26%20co", TmdbProvider.encode("Amélie & co"))
        assertEquals("a-b_c.d~e", TmdbProvider.encode("a-b_c.d~e"))
    }
}
