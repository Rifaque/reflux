package dev.reflux.library

import dev.reflux.core.metadata.ProviderCollection
import dev.reflux.core.metadata.RemoteArtwork
import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.CalendarDate
import dev.reflux.sources.local.LocalFolderSource
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmartLibraryTest {
    private val root: Path = Files.createTempDirectory("reflux-smartlib")
    private var clock = 1_790_000_000_000L // 2026-09-21
    private val library = Library(LibraryDatabase.inMemory()) { clock }
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

    private fun franchiseMovie(id: String, title: String, year: Int) {
        val ref = provider.movie(id, title, year)
        provider.works[ref] = provider.works.getValue(ref).copy(
            collection = ProviderCollection("tmdb:collection:263", "The Dark Knight Collection", "https://img/c.jpg"),
            artwork = listOf(RemoteArtwork(ArtworkKind.BACKDROP, "https://img/$id-backdrop.jpg")),
            genres = listOf("Action", "Crime"),
        )
    }

    @Test
    fun franchisesAppearWithTwoPresentMovies() = runTest {
        franchiseMovie("272", "Batman Begins", 2005)
        franchiseMovie("155", "The Dark Knight", 2008)
        franchiseMovie("49026", "The Dark Knight Rises", 2012)
        file("The Dark Knight (2008).mkv")
        library.scan(source)
        library.refreshMetadata(provider, "en-US")
        assertTrue(library.franchises().isEmpty(), "a single movie is not a collection")

        file("Batman Begins (2005).mkv")
        library.scan(source)
        library.refreshMetadata(provider, "en-US")
        val franchise = library.franchises().single()
        assertEquals("The Dark Knight Collection", franchise.name)
        assertEquals(2, franchise.presentCount)
        assertEquals(listOf("Batman Begins", "The Dark Knight"), library.collectionEntries(franchise).map { it.item.title })
        val darkKnight = library.movies().single { it.item.title == "The Dark Knight" }
        assertEquals(franchise.id, library.franchiseOf(darkKnight.item.id)?.id)
    }

    @Test
    fun manualAndSmartCollections() = runTest {
        file("Heat (1995).mkv")
        file("Alien (1979).mkv")
        library.scan(source)
        val heat = library.movies().single { it.item.title == "Heat" }

        val picks = library.createCollection("Friday picks")
        library.addToCollection(picks.id, heat.item.id)
        library.addToCollection(picks.id, heat.item.id)
        assertEquals(listOf("Heat"), library.collectionEntries(picks).map { it.item.title })

        val unwatched = library.createCollection("Unwatched", smartQuery = "movies I haven't watched")
        assertEquals(2, library.collectionEntries(unwatched).size)
        library.setWatched(heat.item.id, true)
        assertEquals(listOf("Alien"), library.collectionEntries(unwatched).map { it.item.title })

        assertEquals(listOf("Friday picks", "Unwatched"), library.userCollections().map { it.name })
        library.deleteCollection(picks.id)
        assertEquals(listOf("Unwatched"), library.userCollections().map { it.name })
    }

    @Test
    fun homeFeedForAFreshLibrary() = runTest {
        franchiseMovie("272", "Batman Begins", 2005)
        franchiseMovie("155", "The Dark Knight", 2008)
        file("Batman Begins (2005).mkv")
        file("The Dark Knight (2008).mkv")
        file("TV/Lost/Season 1/Lost.S01E01.mkv")
        library.scan(source)
        library.refreshMetadata(provider, "en-US")

        val home = library.home()
        assertEquals(listOf("recent", "franchise:tmdb:collection:263", "shows", "movies"), home.rows.map { it.id })
        assertNotNull(home.featured?.artwork?.get(ArtworkKind.BACKDROP))

        val batman = library.movies().first()
        library.stopped(batman.item.id, 20 * 60_000L, 120 * 60_000L)
        assertEquals("continue", library.home().rows.first().id)
    }

    @Test
    fun emptyLibraryHasAnEmptyHome() {
        val home = library.home()
        assertTrue(home.rows.isEmpty())
        assertNull(home.featured)
    }

    @Test
    fun diagnostics() = runTest {
        provider.show("1396", "Breaking Bad", 2008, mapOf(1 to 7))
        provider.movie("a", "Solaris", 1972, popularity = 40.0)
        provider.movie("b", "Solaris", 2002, popularity = 50.0)
        file("TV/Breaking Bad/Season 1/Breaking.Bad.S01E01.mkv")
        file("TV/Breaking Bad/Season 1/Breaking.Bad.S01E02-E03.mkv")
        file("TV/Breaking Bad/Season 1/Breaking.Bad.S01E06.mkv")
        file("Solaris.mkv")
        file("Heat (1995)/Heat.1995.1080p.mkv")
        file("Heat (1995)/Heat.1995.2160p.mkv")
        file("movie.mkv")
        library.scan(source)
        library.refreshMetadata(provider, "en-US")

        val report = library.diagnostics()
        val missing = report.missingEpisodes.single()
        assertEquals("Breaking Bad", missing.showTitle)
        assertEquals(listOf(4, 5, 7), missing.missing)
        assertTrue(report.unmatched.any { it.item.title == "Solaris" })
        assertEquals(listOf("Heat"), report.multipleVersions.map { it.item.title })
        assertEquals("movie.mkv", report.lowConfidence.single().version.location.fileName)
        assertTrue(report.missingArtwork.any { it.item.title == "Heat" })
        assertTrue(report.unavailableSources.isEmpty())
    }

    @Test
    fun calendarConversion() {
        assertEquals(CalendarDate(1970, 1, 1), Library.dateOf(0))
        assertEquals(CalendarDate(2000, 2, 29), Library.dateOf(951_782_400_000L))
        assertEquals(CalendarDate(2026, 9, 21), Library.dateOf(1_790_000_000_000L))
        assertEquals(CalendarDate(1969, 12, 31), Library.dateOf(-1))
    }

}
