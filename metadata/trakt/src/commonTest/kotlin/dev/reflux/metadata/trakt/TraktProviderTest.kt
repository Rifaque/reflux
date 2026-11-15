package dev.reflux.metadata.trakt

import dev.reflux.core.metadata.CreditRole
import dev.reflux.core.metadata.MatchResult
import dev.reflux.core.metadata.MetadataKind
import dev.reflux.core.metadata.MetadataMatcher
import dev.reflux.core.metadata.MetadataQuery
import dev.reflux.core.metadata.MetadataUnavailableException
import dev.reflux.core.metadata.ProviderRef
import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.CalendarDate
import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.net.HttpResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Responses follow the shapes of Trakt's API contracts (github.com/trakt/trakt-api, projects/api/src/contracts, read
 * 2026-11-15), trimmed to the fields Reflux reads. No test touches the network or needs a real Client ID.
 */
class TraktProviderTest {
    private val clientId = "test-client-id-not-real"
    private val requests = mutableListOf<Pair<String, Map<String, String>>>()
    private var routes: Map<String, () -> HttpResult> = emptyMap()

    private val http = HttpFetcher { request ->
        requests += request.url to request.headers
        val path = request.url.removePrefix("https://api.trakt.tv/").substringBefore('?')
        routes[path]?.invoke() ?: HttpResult(404, ByteArray(0))
    }

    private fun ok(json: String, headers: Map<String, String> = emptyMap()) = { HttpResult(200, json.encodeToByteArray(), headers) }

    /** A provider whose rate limiter keeps the test's virtual time, so waits are measured, not slept. */
    private fun TestScope.trakt(
        fetcher: HttpFetcher = http,
        limit: Int = TraktRateLimiter.DEFAULT_LIMIT,
        periodMs: Long = TraktRateLimiter.DEFAULT_PERIOD_MS,
    ): Pair<TraktProvider, TraktRateLimiter> {
        val limiter = TraktRateLimiter(limit, periodMs, clock = { EPOCH + testScheduler.currentTime }, random = Random(7))
        return TraktProvider(clientId, fetcher, limiter = limiter) to limiter
    }

    private fun url(index: Int = 0) = requests[index].first

    @Test
    fun searchesMoviesWithTheDocumentedHeaders() = runTest {
        routes = mapOf(
            "search/movie" to ok(
                """[{"type":"movie","score":1000,"movie":{"title":"Interstellar","original_title":"Interstellar","year":2014,
                "overview":"Space.","votes":51234,"ids":{"trakt":102156,"slug":"interstellar-2014","imdb":"tt0816692","tmdb":157336},
                "images":{"poster":["media.trakt.tv/images/movies/000/102/156/posters/medium/abc.jpg.webp"],"fanart":[]}}},
                {"type":"show","score":10,"show":{"title":"Not a film","ids":{"trakt":1}}},
                {"type":"movie","score":5,"movie":{"title":"Broken","year":null,"ids":{"trakt":null}}}]""",
            ),
        )
        val (trakt) = trakt()
        val results = trakt.search(MetadataQuery(MetadataKind.MOVIE, "Interstellar", 2014, language = "en-US"))
        val first = results.single()
        assertEquals(ProviderRef("trakt", MetadataKind.MOVIE, "102156"), first.ref)
        assertEquals(2014, first.year)
        assertEquals(51234.0, first.popularity)
        assertEquals("https://media.trakt.tv/images/movies/000/102/156/posters/medium/abc.jpg.webp", first.posterUrl)

        val (url, headers) = requests.single()
        assertTrue("query=Interstellar" in url)
        assertTrue("extended=full%2Cimages" in url)
        assertEquals(clientId, headers["trakt-api-key"])
        assertEquals("2", headers["trakt-api-version"])
        assertEquals("application/json", headers["Accept"])
        assertEquals("application/json", headers["Content-Type"])
        assertNull(headers["Authorization"], "no OAuth: looking up a title needs no user")
    }

    @Test
    fun searchesShows() = runTest {
        routes = mapOf(
            "search/show" to ok(
                """[{"type":"show","score":900,"show":{"title":"Breaking Bad","year":2008,"votes":80000,
                "ids":{"trakt":1388,"slug":"breaking-bad","tvdb":81189,"imdb":"tt0903747","tmdb":1396}}}]""",
            ),
        )
        val (trakt) = trakt()
        val show = trakt.search(MetadataQuery(MetadataKind.SHOW, "Breaking Bad", null, language = "en-US")).single()
        assertEquals(ProviderRef("trakt", MetadataKind.SHOW, "1388"), show.ref)
        assertNull(show.posterUrl)
        assertTrue(url().startsWith("https://api.trakt.tv/search/show?"), "a show search never asks for films")
    }

    @Test
    fun aWrongYearInTheFileNameDoesNotHideTheWork() = runTest {
        routes = mapOf("search/show" to ok("""[{"type":"show","show":{"title":"Breaking Bad","year":2008,"ids":{"trakt":1388}}}]"""))
        val (trakt) = trakt()
        val query = MetadataQuery(MetadataKind.SHOW, "Breaking Bad", 2009, language = "en-US")
        val candidates = trakt.search(query)
        assertFalse("year" in url(), "the year is weighed by the matcher, never used to filter the search")
        assertIs<MatchResult.Accepted>(MetadataMatcher.match(query, candidates))
        assertEquals(1, requests.size)
    }

    @Test
    fun searchSyntaxIsEscaped() {
        assertEquals("Mission\\: Impossible", TraktProvider.escapeQuery("Mission: Impossible"))
        assertEquals("Fast \\&& Furious \\(2009\\)", TraktProvider.escapeQuery("Fast && Furious (2009)"))
        assertEquals("Am%C3%A9lie%20%26%20co", TraktProvider.encode("Amélie & co"))
    }

    @Test
    fun findsWorksByTheirIdsInOtherCatalogs() = runTest {
        routes = mapOf(
            "search/imdb/tt0133093" to ok("""[{"type":"movie","score":null,"movie":{"title":"The Matrix","year":1999,"ids":{"trakt":481}}}]"""),
            "search/tmdb/1396" to ok("""[{"type":"show","score":null,"show":{"title":"Breaking Bad","ids":{"trakt":1388}}}]"""),
        )
        val (trakt) = trakt()
        assertEquals(ProviderRef("trakt", MetadataKind.MOVIE, "481"), trakt.findByExternalId(MetadataKind.MOVIE, "imdb", "tt0133093"))
        assertTrue("type=movie" in url())
        assertEquals(ProviderRef("trakt", MetadataKind.SHOW, "1388"), trakt.findByExternalId(MetadataKind.SHOW, "tmdb", "1396"))
        // An ID of the other kind is no match.
        assertNull(trakt.findByExternalId(MetadataKind.SHOW, "imdb", "tt0133093"))
        val asked = requests.size
        assertNull(trakt.findByExternalId(MetadataKind.MOVIE, "tvdb", "81189"), "Trakt keys TVDB IDs to shows only")
        assertNull(trakt.findByExternalId(MetadataKind.MOVIE, "anidb", "1"))
        assertEquals(asked, requests.size, "nothing is asked that Trakt cannot answer")
    }

    private val matrix = mapOf(
        "movies/481" to ok(
            """{"title":"The Matrix","original_title":"The Matrix","year":1999,"tagline":"Welcome to the Real World.",
            "overview":"Neo.","released":"1999-03-31","runtime":136,"rating":8.4,"votes":60000,"certification":"R",
            "genres":["action","science-fiction"],
            "ids":{"trakt":481,"slug":"the-matrix-1999","imdb":"tt0133093","tmdb":603},
            "images":{"poster":["media.trakt.tv/images/movies/000/000/481/posters/medium/p.jpg.webp"],
                      "fanart":["media.trakt.tv/images/movies/000/000/481/fanarts/medium/f.jpg.webp"],
                      "logo":["media.trakt.tv/images/movies/000/000/481/logos/medium/l.png.webp"],
                      "clearart":[],"banner":[],"thumb":[]}}""",
        ),
        "movies/481/people" to ok(
            """{"cast":[{"character":"Neo","characters":["Neo"],"person":{"name":"Keanu Reeves","ids":{"trakt":1}},
                        "images":{"headshot":["media.trakt.tv/images/people/000/000/001/headshots/medium/k.jpg.webp"]}},
                       {"characters":["Trinity"],"person":{"name":"Carrie-Anne Moss","ids":{"trakt":2}}}],
               "crew":{"directing":[{"jobs":["Director"],"person":{"name":"Lana Wachowski","ids":{"trakt":3}}},
                                    {"jobs":["Assistant Director"],"person":{"name":"Someone Else","ids":{"trakt":4}}}],
                       "camera":[{"jobs":["Director of Photography"],"person":{"name":"Bill Pope","ids":{"trakt":5}}}]}}""",
        ),
        "movies/481/studios" to ok("""[{"name":"Warner Bros. Pictures","country":"us","ids":{"trakt":1}},{"name":"Village Roadshow","ids":{"trakt":2}},{"name":"Groucho","ids":{"trakt":3}}]"""),
        "genres/movies" to ok("""[{"name":"Action","slug":"action"},{"name":"Science Fiction","slug":"science-fiction"}]"""),
    )

    @Test
    fun movieDetailsCreditsArtworkAndIds() = runTest {
        routes = matrix
        val (trakt) = trakt()
        val movie = assertNotNull(trakt.details(ProviderRef("trakt", MetadataKind.MOVIE, "481"), "en-US"))
        assertEquals("The Matrix", movie.title)
        assertEquals("Welcome to the Real World.", movie.tagline)
        assertEquals(listOf("Action", "Science Fiction"), movie.genres)
        assertEquals(136, movie.runtimeMinutes)
        assertEquals(CalendarDate(1999, 3, 31), movie.releaseDate)
        assertEquals(8.4, movie.rating)
        assertEquals("R", movie.contentRating)
        assertEquals(listOf("Warner Bros. Pictures", "Village Roadshow"), movie.studios)
        assertNull(movie.collection, "Trakt has no franchises; none is invented")

        // Credits: the director, then the cast in billing order; nobody else from the crew.
        assertEquals(listOf("Lana Wachowski", "Keanu Reeves", "Carrie-Anne Moss"), movie.credits.map { it.name })
        assertEquals(listOf(CreditRole.DIRECTOR, CreditRole.ACTOR, CreditRole.ACTOR), movie.credits.map { it.role })
        assertEquals(listOf(null, "Neo", "Trinity"), movie.credits.map { it.character })
        assertEquals("https://media.trakt.tv/images/people/000/000/001/headshots/medium/k.jpg.webp", movie.credits[1].profileUrl)

        // Artwork: Trakt's own URLs, nothing made up about language or score.
        assertEquals(
            mapOf(
                ArtworkKind.POSTER to "https://media.trakt.tv/images/movies/000/000/481/posters/medium/p.jpg.webp",
                ArtworkKind.BACKDROP to "https://media.trakt.tv/images/movies/000/000/481/fanarts/medium/f.jpg.webp",
                ArtworkKind.LOGO to "https://media.trakt.tv/images/movies/000/000/481/logos/medium/l.png.webp",
            ),
            movie.artwork.associate { it.kind to it.url },
        )
        assertTrue(movie.artwork.all { it.language == null && it.score == 0.0 })

        // The other catalogs' IDs are kept, beside Trakt's own.
        assertEquals(mapOf("trakt" to "481", "imdb" to "tt0133093", "tmdb" to "603"), movie.externalIds)

        assertTrue("extended=full%2Cimages" in requests.first { "/movies/481?" in it.first }.first)
        assertTrue(requests.none { "/translations/" in it.first }, "English needs no translation")
    }

    @Test
    fun everyImageUrlIsTraktsOwnAndAbsoluteForTheArtworkCache() = runTest {
        routes = matrix
        val (trakt) = trakt()
        val movie = assertNotNull(trakt.details(ProviderRef("trakt", MetadataKind.MOVIE, "481"), "en-US"))
        val urls = movie.artwork.map { it.url } + movie.credits.mapNotNull { it.profileUrl }
        // The library hands each URL to its ArtworkCache, which downloads it once; the interface draws only cached
        // files. So each must be a complete https URL on Trakt's image host, never another image service's.
        assertTrue(urls.isNotEmpty())
        assertTrue(urls.all { it.startsWith("https://media.trakt.tv/images/") }, "$urls")
    }

    @Test
    fun genreNamesAreReadOnce() = runTest {
        routes = matrix
        val (trakt) = trakt()
        val ref = ProviderRef("trakt", MetadataKind.MOVIE, "481")
        trakt.details(ref, "en-US")
        trakt.details(ref, "en-US")
        assertEquals(1, requests.count { it.first.contains("/genres/movies") })
        assertEquals("Science Fiction", TraktProvider.readableSlug("science-fiction"))
    }

    @Test
    fun anotherLanguageReadsItsTranslation() = runTest {
        routes = matrix + (
            "movies/481/translations/de" to ok(
                """[{"title":"Matrix (AT)","overview":"Neo (AT).","tagline":null,"language":"de","country":"at"},
                {"title":"Matrix","overview":"Neo, auf Deutsch.","tagline":"","language":"de","country":"de"}]""",
            )
        )
        val (trakt) = trakt()
        val movie = assertNotNull(trakt.details(ProviderRef("trakt", MetadataKind.MOVIE, "481"), "de-DE"))
        assertEquals("Matrix", movie.title)
        assertEquals("Neo, auf Deutsch.", movie.overview)
        assertEquals("Welcome to the Real World.", movie.tagline, "an empty translation keeps the original")
        assertEquals("The Matrix", movie.originalTitle)
    }

    @Test
    fun aTranslationSplitOverEntriesIsPutTogether() = runTest {
        // As Trakt answered for a film in German (2026-11-16): the title in an entry with no country, the overview in
        // Germany's.
        routes = matrix + (
            "movies/481/translations/de" to ok(
                """[{"title":"Matrix","overview":null,"tagline":null,"language":"de","country":null},
                {"title":null,"overview":"Neo, auf Deutsch.","tagline":null,"language":"de","country":"de"}]""",
            )
        )
        val (trakt) = trakt()
        val movie = assertNotNull(trakt.details(ProviderRef("trakt", MetadataKind.MOVIE, "481"), "de-DE"))
        assertEquals("Matrix", movie.title)
        assertEquals("Neo, auf Deutsch.", movie.overview)
        assertEquals("Welcome to the Real World.", movie.tagline)
    }

    @Test
    fun aSeasonTraktDoesNotHaveIsNothing() = runTest {
        // Trakt answers 204 No Content for a season number a show does not have (seen 2026-11-16).
        routes = breakingBad + ("shows/1388/seasons/99/info" to { HttpResult(204, ByteArray(0)) })
        val (trakt) = trakt()
        assertNull(trakt.season(ProviderRef("trakt", MetadataKind.SHOW, "1388"), 99, "en-US"))
    }

    private val breakingBad = mapOf(
        "shows/1388" to ok(
            """{"title":"Breaking Bad","year":2008,"overview":"Walter.","first_aired":"2008-01-21T02:00:00.000Z",
            "last_aired":"2013-09-30T02:00:00.000Z","airs":{"day":"Sunday","time":"21:00","timezone":"America/New_York"},
            "runtime":47,"certification":"TV-MA","network":"AMC","rating":9.3,"votes":90000,"genres":["drama"],
            "ids":{"trakt":1388,"slug":"breaking-bad","tvdb":81189,"imdb":"tt0903747","tmdb":1396},
            "images":{"poster":["media.trakt.tv/images/shows/000/001/388/posters/medium/p.jpg.webp"]}}""",
        ),
        "shows/1388/people" to ok(
            """{"cast":[{"characters":["Walter White"],"episode_count":62,"person":{"name":"Bryan Cranston","ids":{"trakt":10}}}],
               "crew":{"created by":[{"jobs":["Creator"],"person":{"name":"Vince Gilligan","ids":{"trakt":11}}}],
                       "writing":[{"jobs":["Writer"],"person":{"name":"Vince Gilligan","ids":{"trakt":11}}}]}}""",
        ),
        "genres/shows" to ok("""[{"name":"Drama","slug":"drama"}]"""),
        "shows/1388/seasons/1/info" to ok(
            """{"number":1,"title":"Season 1","overview":"It begins.","first_aired":"2008-01-21T02:00:00.000Z",
            "ids":{"trakt":3950},"images":{"poster":["media.trakt.tv/images/seasons/000/003/950/posters/medium/s1.jpg.webp"],"thumb":[]}}""",
        ),
        "shows/1388/seasons/1" to ok(
            """[{"season":1,"number":1,"title":"Pilot","overview":"Diagnosis.","first_aired":"2008-01-21T02:00:00.000Z","runtime":58,
                "rating":8.3,"votes":12000,"ids":{"trakt":73482},"images":{"screenshot":["media.trakt.tv/images/episodes/000/073/482/screenshots/medium/e1.jpg.webp"]}},
               {"season":1,"number":2,"title":"Cat's in the Bag...","first_aired":null,"rating":0,"votes":0,"ids":{"trakt":73483}},
               {"season":1,"title":"No number","ids":{"trakt":1}}]""",
        ),
    )

    @Test
    fun showDetails() = runTest {
        routes = breakingBad
        val (trakt) = trakt()
        val show = assertNotNull(trakt.details(ProviderRef("trakt", MetadataKind.SHOW, "1388"), "en-US"))
        assertEquals("Breaking Bad", show.title)
        // Air times are UTC instants; the dates are the broadcast's own, in New York.
        assertEquals(CalendarDate(2008, 1, 20), show.releaseDate)
        assertEquals(CalendarDate(2013, 9, 29), show.endDate)
        assertEquals(47, show.runtimeMinutes)
        assertEquals("TV-MA", show.contentRating)
        assertEquals(listOf("AMC"), show.studios)
        assertEquals(listOf("Drama"), show.genres)
        assertEquals(listOf("Vince Gilligan", "Bryan Cranston"), show.credits.map { it.name })
        assertEquals(CreditRole.CREATOR, show.credits.first().role)
        assertEquals("Walter White", show.credits[1].character)
        assertEquals(mapOf("trakt" to "1388", "imdb" to "tt0903747", "tmdb" to "1396", "tvdb" to "81189"), show.externalIds)
    }

    @Test
    fun seasonsAndEpisodesWithLocalAirDates() = runTest {
        routes = breakingBad
        val (trakt) = trakt()
        val ref = ProviderRef("trakt", MetadataKind.SHOW, "1388")
        val season = assertNotNull(trakt.season(ref, 1, "en-US"))
        assertEquals("Season 1", season.title)
        assertEquals("https://media.trakt.tv/images/seasons/000/003/950/posters/medium/s1.jpg.webp", season.posterUrl)
        assertEquals(CalendarDate(2008, 1, 20), season.airDate)
        assertEquals(listOf(1, 2), season.episodes.map { it.episodeNumber })
        val pilot = season.episodes.first()
        assertEquals("Pilot", pilot.title)
        assertEquals(CalendarDate(2008, 1, 20), pilot.airDate, "the date a daily show's file names, not UTC's")
        assertEquals(58, pilot.runtimeMinutes)
        assertEquals(8.3, pilot.rating)
        assertEquals("https://media.trakt.tv/images/episodes/000/073/482/screenshots/medium/e1.jpg.webp", pilot.stillUrl)
        assertNull(season.episodes[1].rating, "no votes, no rating")
        assertNull(season.episodes[1].airDate)
        // The show's time zone was read once, for every season after it.
        trakt.season(ref, 1, "en-US")
        assertEquals(1, requests.count { it.first.startsWith("https://api.trakt.tv/shows/1388?") })
        assertNull(trakt.season(ref, 9, "en-US"))
    }

    @Test
    fun anAbsentRecordIsNothingNotAFailure() = runTest {
        val (trakt) = trakt()
        assertNull(trakt.details(ProviderRef("trakt", MetadataKind.MOVIE, "999999"), "en-US"))
        assertNull(trakt.details(ProviderRef("trakt", MetadataKind.SHOW, "999999"), "en-US"))
        assertEquals(emptyList(), trakt.search(MetadataQuery(MetadataKind.MOVIE, "x", null, language = "en")))
    }

    @Test
    fun aShortRetryAfterIsWaitedOut() = runTest {
        var calls = 0
        routes = mapOf(
            "search/movie" to {
                calls++
                if (calls == 1) HttpResult(429, ByteArray(0), mapOf("retry-after" to "2")) else HttpResult(200, "[]".encodeToByteArray())
            },
        )
        val (trakt) = trakt()
        val started = testScheduler.currentTime
        trakt.search(MetadataQuery(MetadataKind.MOVIE, "x", null, language = "en"))
        assertEquals(2, calls)
        assertTrue(testScheduler.currentTime - started >= 2_000, "the second request waited Trakt's two seconds")
    }

    @Test
    fun aLongRetryAfterFailsAtOnceAndNothingIsAskedUntilThen() = runTest {
        routes = mapOf("search/movie" to { HttpResult(429, ByteArray(0), mapOf("Retry-After" to "120")) })
        val (trakt) = trakt()
        val query = MetadataQuery(MetadataKind.MOVIE, "x", null, language = "en")
        val refused = assertFailsWith<MetadataUnavailableException> { trakt.search(query) }
        assertEquals(120_000L, refused.retryAfterMs)
        assertEquals(1, requests.size)
        val again = assertFailsWith<MetadataUnavailableException> { trakt.search(query) }
        assertEquals(1, requests.size, "no request while Trakt asked to wait")
        assertTrue(again.retryAfterMs!! in 1..120_000L)
        advanceTimeBy(121_000)
        routes = mapOf("search/movie" to ok("[]"))
        trakt.search(query)
        assertEquals(2, requests.size)
    }

    @Test
    fun aRateLimitWithoutRetryAfterBacksOffBoundedWithJitter() = runTest {
        routes = mapOf("search/movie" to { HttpResult(429, ByteArray(0)) })
        val (trakt) = trakt()
        val started = testScheduler.currentTime
        val refused = assertFailsWith<MetadataUnavailableException> { trakt.search(MetadataQuery(MetadataKind.MOVIE, "x", null, language = "en")) }
        // Three tries at most, never a spin: 1-2 s, then 2-4 s, then the third answer is passed on with its wait.
        assertEquals(3, requests.size)
        val waited = testScheduler.currentTime - started
        assertTrue(waited in 3_000..6_000, "waited $waited ms")
        assertTrue(refused.retryAfterMs!! in 4_000..8_000, "told to wait ${refused.retryAfterMs} ms")

        val limiter = TraktRateLimiter(clock = { 0 }, random = Random(1))
        val waits = (1..12).map { limiter.rateLimited(emptyMap()) }
        assertTrue(waits.all { it in 1..TraktRateLimiter.MAX_BACKOFF_MS }, "$waits")
        assertTrue(waits.last() >= TraktRateLimiter.MAX_BACKOFF_MS / 2, "the backoff grows to its ceiling and stops there")
    }

    @Test
    fun theRateLimitHeaderIsFollowed() = runTest {
        val until = "2026-11-15T00:01:00Z" // a minute after EPOCH
        routes = mapOf(
            "search/movie" to ok(
                "[]",
                mapOf("X-Ratelimit" to """{"name":"UNAUTHED_API_GET_LIMIT","period":300,"limit":1000,"remaining":0,"until":"$until"}"""),
            ),
        )
        val (trakt, limiter) = trakt()
        val query = MetadataQuery(MetadataKind.MOVIE, "x", null, language = "en")
        trakt.search(query)
        val state = limiter.state()
        assertEquals(1000, state.limit)
        assertEquals(300_000L, state.periodMs)
        assertTrue(state.pausedForMs in 59_000..60_000, "paused until the window ends: ${state.pausedForMs}")
        // A minute is longer than a request should wait in place: the caller is told, and nothing is sent.
        val refused = assertFailsWith<MetadataUnavailableException> { trakt.search(query) }
        assertTrue(refused.retryAfterMs!! in 59_000..60_000)
        assertEquals(1, requests.size)
        advanceTimeBy(61_000)
        trakt.search(query)
        assertEquals(2, requests.size)
    }

    @Test
    fun requestsArePacedWithinTheBudget() = runTest {
        routes = mapOf("search/movie" to ok("[]"))
        // Two requests a second: the third waits for the budget to refill, instead of failing on a 429.
        val (trakt) = trakt(limit = 2, periodMs = 1_000)
        val started = testScheduler.currentTime
        repeat(3) { trakt.search(MetadataQuery(MetadataKind.MOVIE, "x$it", null, language = "en")) }
        val waited = testScheduler.currentTime - started
        assertTrue(waited in 500..600, "waited $waited ms")
    }

    @Test
    fun malformedResponsesNetworkFailuresAndRefusalsAreUnavailable() = runTest {
        val query = MetadataQuery(MetadataKind.MOVIE, "x", null, language = "en")
        routes = mapOf("search/movie" to ok("""{"not": "an array"""))
        assertFailsWith<MetadataUnavailableException> { trakt().first.search(query) }
        routes = mapOf("search/movie" to ok("""{"not":"an array"}"""))
        assertFailsWith<MetadataUnavailableException> { trakt().first.search(query) }

        val offline = trakt(HttpFetcher { throw IllegalStateException("no network") }).first
        assertFailsWith<MetadataUnavailableException> { offline.search(query) }

        for (status in listOf(401, 403)) {
            routes = mapOf("search/movie" to { HttpResult(status, ByteArray(0)) })
            val refused = assertFailsWith<MetadataUnavailableException> { trakt().first.search(query) }
            assertFalse(clientId in refused.message.orEmpty(), "the Client ID is never in a message")
        }
        routes = mapOf("search/movie" to { HttpResult(503, ByteArray(0)) })
        assertEquals(30_000L, assertFailsWith<MetadataUnavailableException> { trakt().first.search(query) }.retryAfterMs)
    }

    @Test
    fun cancellationStaysCancellation() = runTest {
        val cancelled = trakt(HttpFetcher { throw CancellationException("the viewer left") }).first
        assertFailsWith<CancellationException> { cancelled.search(MetadataQuery(MetadataKind.MOVIE, "x", null, language = "en")) }

        // Cancelled while waiting its turn: the job ends, and no request is sent.
        routes = mapOf("search/movie" to { HttpResult(429, ByteArray(0), mapOf("Retry-After" to "5")) })
        val (trakt) = trakt()
        val job = launch { trakt.search(MetadataQuery(MetadataKind.MOVIE, "x", null, language = "en")) }
        runCurrent()
        assertEquals(1, requests.size)
        job.cancel()
        advanceTimeBy(10_000)
        assertTrue(job.isCancelled)
        assertEquals(1, requests.size)
    }

    @Test
    fun withoutAClientIdThereIsNoProvider() {
        assertFailsWith<IllegalArgumentException> { TraktProvider(" ", http) }
        assertNull(TraktProvider.fromClientId(null, http))
        assertNull(TraktProvider.fromClientId("  ", http))
        assertEquals("trakt", TraktProvider.fromClientId(clientId, http)?.id)
    }

    @Test
    fun theAttributionIsTheOneTraktAsksFor() {
        val attribution = TraktProvider(clientId, http).attribution
        assertEquals("Reflux uses Trakt but is not endorsed or certified by Trakt.", attribution.notice)
        assertEquals("Trakt", attribution.name)
        assertEquals("trakt.tv", attribution.site)
        assertNull(attribution.logo, "no Trakt mark is drawn")
    }

    private companion object {
        /** 2026-11-15T00:00:00Z. */
        const val EPOCH = 1_794_700_800_000L
    }
}
