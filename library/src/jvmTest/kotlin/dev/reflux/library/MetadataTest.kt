package dev.reflux.library

import dev.reflux.core.metadata.RemoteArtwork
import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.CalendarDate
import dev.reflux.core.model.Episode
import dev.reflux.core.model.Movie
import dev.reflux.sources.local.LocalFolderSource
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MetadataTest {
    private val root: Path = Files.createTempDirectory("reflux-metadata")
    private var clock = 1_790_000_000_000L
    private val library = Library(testDatabase()) { clock }
    private val source = LocalFolderSource(root)
    private val provider = FakeMetadataProvider()

    init {
        library.addSource(source, root.toString())
    }

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
    }

    private fun file(relative: String) = root.resolve(relative).also { it.parent.createDirectories(); it.writeBytes(ByteArray(8)) }

    @Test
    fun matchedMoviesGetTitlesOverviewAndArtwork() = runTest {
        provider.movie(
            "194", "Amélie", 2001, original = "Le Fabuleux Destin d'Amélie Poulain",
            artwork = arrayOf(
                RemoteArtwork(ArtworkKind.POSTER, "https://img/fr.jpg", "fr", 9.0),
                RemoteArtwork(ArtworkKind.POSTER, "https://img/en.jpg", "en", 5.0),
                RemoteArtwork(ArtworkKind.BACKDROP, "https://img/text.jpg", "en", 9.0),
                RemoteArtwork(ArtworkKind.BACKDROP, "https://img/clean.jpg", null, 1.0),
            ),
        )
        file("Movies/amelie.2001.1080p.mkv")
        library.scan(source)
        val report = library.refreshMetadata(provider, "en-US")
        assertEquals(1, report.matched)

        val entry = library.movies().single()
        assertEquals("Amélie", entry.item.title)
        assertEquals("About Amélie", entry.metadata?.overview)
        assertEquals(ArtworkLocator.Remote("https://img/en.jpg"), entry.artwork[ArtworkKind.POSTER])
        assertEquals(ArtworkLocator.Remote("https://img/clean.jpg"), entry.artwork[ArtworkKind.BACKDROP])
        assertEquals("Amélie", library.search("fabuleux destin").single().item.title)
    }

    @Test
    fun localArtworkWinsOverProviderArtwork() = runTest {
        provider.movie("1", "Heat", 1995, artwork = arrayOf(RemoteArtwork(ArtworkKind.POSTER, "https://img/heat.jpg")))
        file("Heat (1995)/Heat (1995).mkv")
        file("Heat (1995)/poster.jpg")
        library.scan(source)
        library.refreshMetadata(provider, "en-US")
        assertIs<ArtworkLocator.SourceFile>(library.movies().single().artwork[ArtworkKind.POSTER])
    }

    @Test
    fun ambiguousWorksWaitForTheRetryWindow() = runTest {
        provider.movie("a", "Solaris", 1972, popularity = 40.0)
        provider.movie("b", "Solaris", 2002, popularity = 50.0)
        file("Solaris.mkv")
        library.scan(source)
        assertEquals(1, library.refreshMetadata(provider, "en-US").ambiguous)
        assertNull(library.movies().single().metadata)

        provider.calls.clear()
        library.refreshMetadata(provider, "en-US")
        assertTrue(provider.calls.isEmpty(), "not retried immediately: ${provider.calls}")

        clock += 8 * 24 * 3_600_000L
        library.refreshMetadata(provider, "en-US")
        assertEquals(listOf("search:Solaris"), provider.calls)
    }

    @Test
    fun offlineProviderStopsAndRetriesLater() = runTest {
        provider.movie("1", "Heat", 1995)
        file("Heat (1995).mkv")
        library.scan(source)
        provider.offline = true
        val report = library.refreshMetadata(provider, "en-US")
        assertTrue(report.offline)
        assertEquals("Heat", library.movies().single().item.title)

        provider.offline = false
        assertEquals(1, library.refreshMetadata(provider, "en-US").matched)
    }

    @Test
    fun embeddedIdsSkipSearch() = runTest {
        provider.movie("157336", "Interstellar", 2014)
        file("Movies/Interstellar (2014) {tmdb-157336}/Interstellar.mkv")
        library.scan(source)
        library.refreshMetadata(provider, "en-US")
        assertEquals(listOf("details:157336"), provider.calls)

        val imdb = provider.movie("603", "The Matrix", 1999)
        provider.externalIds["imdb" to "tt0133093"] = imdb
        file("Movies/The Matrix (1999) [imdbid-tt0133093]/The Matrix.mkv")
        library.scan(source)
        provider.calls.clear()
        library.refreshMetadata(provider, "en-US")
        assertEquals(listOf("find:imdb:tt0133093", "details:603"), provider.calls)
    }

    @Test
    fun showsGetSeasonAndEpisodeMetadata() = runTest {
        provider.show("1396", "Breaking Bad", 2008, mapOf(1 to 7, 2 to 13))
        file("TV/Breaking Bad/Season 1/Breaking.Bad.S01E01.mkv")
        file("TV/Breaking Bad/Season 1/Breaking.Bad.S01E02.mkv")
        library.scan(source)
        val report = library.refreshMetadata(provider, "en-US")
        assertEquals(1, report.matched)

        val show = library.shows().single()
        val detail = library.showDetail(show.item.id)!!
        assertEquals(listOf("Breaking Bad S1E1", "Breaking Bad S1E2"), detail.seasons.single().episodes.map { it.item.title })
        assertEquals(ArtworkLocator.Remote("https://img/1396/s1.jpg"), detail.seasons.single().entry.artwork[ArtworkKind.POSTER])
        assertEquals(ArtworkLocator.Remote("https://img/1396/1/1.jpg"), detail.seasons.single().episodes.first().artwork[ArtworkKind.THUMBNAIL])

        // An episode added later is filled in without re-matching the show.
        file("TV/Breaking Bad/Season 2/Breaking.Bad.S02E05.mkv")
        library.scan(source)
        provider.calls.clear()
        assertEquals(1, library.refreshMetadata(provider, "en-US").episodes)
        assertTrue(provider.calls.none { it.startsWith("search") })
        val season2 = library.showDetail(show.item.id)!!.seasons.single { it.season.number == 2 }
        assertEquals("Breaking Bad S2E5", season2.episodes.single().item.title)
    }

    @Test
    fun absoluteNumberedEpisodesMapAcrossSeasons() = runTest {
        provider.show("95479", "Jujutsu Kaisen", 2020, mapOf(1 to 24, 2 to 23))
        file("Anime/[SubsPlease] Jujutsu Kaisen - 26 (1080p) [ABCDEF12].mkv")
        library.scan(source)
        library.refreshMetadata(provider, "en-US")
        val episode = library.showDetail(library.shows().single().item.id)!!.seasons.single().episodes.single()
        assertEquals("Jujutsu Kaisen S2E2", episode.item.title)
        assertEquals(26, (episode.item as Episode).absoluteNumber)
    }

    @Test
    fun datedEpisodesMatchByAirDate() = runTest {
        val ref = provider.show("60694", "Last Week Tonight with John Oliver", 2014, emptyMap())
        provider.seasons[ref to 11] = dev.reflux.core.metadata.SeasonMetadata(
            11,
            episodes = listOf(
                dev.reflux.core.metadata.EpisodeMetadata(11, 3, "Tariffs", airDate = CalendarDate(2024, 3, 3)),
                dev.reflux.core.metadata.EpisodeMetadata(11, 4, "Elections", airDate = CalendarDate(2024, 3, 10)),
            ),
        )
        file("TV/Last Week Tonight with John Oliver/Last.Week.Tonight.With.John.Oliver.2024.03.10.1080p.mkv")
        library.scan(source)
        library.refreshMetadata(provider, "en-US")
        val episode = library.showDetail(library.shows().single().item.id)!!.seasons.single().episodes.single()
        assertEquals("Elections", episode.item.title)
    }

    @Test
    fun identifyAsPinsTheChoiceAndSurvivesRescans() = runTest {
        val primer = provider.movie("14337", "Primer", 2004)
        provider.movie("1", "Prime", 2005)
        file("Movies/movie.mkv")
        library.scan(source)
        val unknown = library.movies().single()
        library.stopped(unknown.item.id, 10 * 60_000L, 77 * 60_000L)

        val candidates = library.searchMetadata(unknown.item.id, provider, "en-US", title = "Primer")
        assertEquals("Primer", candidates.first().candidate.title)
        val newId = library.identifyAs(unknown.item.id, candidates.first().candidate, provider, "en-US")

        val identified = library.movies().single()
        assertEquals(newId, identified.item.id)
        assertEquals("Primer", identified.item.title)
        assertEquals(2004, (identified.item as Movie).year)
        assertEquals(primer, identified.metadata?.ref)
        assertEquals(10 * 60_000L, identified.watchState?.positionMs)

        library.scan(source)
        assertEquals("Primer", library.movies().single().item.title)
        assertTrue(Files.exists(root.resolve("Movies/movie.mkv")))
    }

    @Test
    fun metadataStaysWhileTheSourceIsOffline() = runTest {
        provider.movie("1", "Heat", 1995)
        file("Heat (1995).mkv")
        library.scan(source)
        library.refreshMetadata(provider, "en-US")
        root.toFile().listFiles()!!.forEach { it.deleteRecursively() }
        library.scan(source) // empty mount point: kept
        assertEquals("About Heat", library.movies().single().metadata?.overview)
    }
}
