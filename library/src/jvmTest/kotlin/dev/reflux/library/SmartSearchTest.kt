package dev.reflux.library

import dev.reflux.core.metadata.Credit
import dev.reflux.core.metadata.CreditRole
import dev.reflux.core.metadata.WorkMetadata
import dev.reflux.core.model.CalendarDate
import dev.reflux.core.model.MediaKind
import dev.reflux.sources.local.LocalFolderSource
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SmartSearchTest {
    private val root: Path = Files.createTempDirectory("reflux-smart")
    private var clock = 1_790_000_000_000L
    private val library = Library(testDatabase()) { clock }
    private val source = LocalFolderSource(root)
    private val provider = FakeMetadataProvider()

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
    }

    private fun file(relative: String) = root.resolve(relative).also { it.parent.createDirectories(); it.writeBytes(ByteArray(8)) }

    private fun movie(id: String, title: String, year: Int, runtime: Int, genres: List<String>, director: String, vararg cast: String) {
        val ref = provider.movie(id, title, year)
        provider.works[ref] = WorkMetadata(
            ref, title, releaseDate = CalendarDate(year, 6, 1), runtimeMinutes = runtime, genres = genres,
            credits = listOf(Credit(director, CreditRole.DIRECTOR)) + cast.map { Credit(it, CreditRole.ACTOR) },
        )
    }

    @BeforeTest
    fun setUp() = runTest {
        movie("1", "Interstellar", 2014, 169, listOf("Adventure", "Drama", "Science Fiction"), "Christopher Nolan", "Matthew McConaughey")
        movie("2", "Arrival", 2016, 116, listOf("Drama", "Science Fiction"), "Denis Villeneuve", "Amy Adams")
        movie("3", "Blade Runner 2049", 2017, 164, listOf("Science Fiction"), "Denis Villeneuve", "Ryan Gosling")
        movie("4", "La La Land", 2016, 129, listOf("Comedy", "Drama", "Music", "Romance"), "Damien Chazelle", "Ryan Gosling", "Emma Stone")
        movie("5", "Groundhog Day", 1993, 101, listOf("Comedy", "Fantasy", "Romance"), "Harold Ramis", "Bill Murray")
        provider.show("10", "Dark", 2017, mapOf(1 to 10))
        file("Interstellar.2014.2160p.UHD.BluRay.HDR.x265.mkv")
        file("Arrival.2016.2160p.WEB.DV.x265.mkv")
        file("Blade.Runner.2049.2017.1080p.BluRay.x264.mkv")
        file("La.La.Land.2016.1080p.mkv")
        file("Groundhog.Day.1993.720p.mkv")
        file("TV/Dark/Season 1/Dark.S01E01.2160p.mkv")
        file("TV/Dark/Season 1/Dark.S01E02.2160p.mkv")
        library.addSource(source, root.toString())
        library.scan(source)
        library.refreshMetadata(provider, "en-US")
    }

    private fun titles(query: String) = library.smartSearch(query).results.map { it.item.title }

    @Test
    fun roadmapExamples() {
        val interstellar = library.movies().single { it.item.title == "Interstellar" }
        library.setWatched(interstellar.item.id, true)
        assertEquals(listOf("Arrival"), titles("4k movies I haven't watched"))
        assertEquals(listOf("Interstellar"), titles("Nolan movies"))
        assertEquals(listOf("Arrival"), titles("science fiction under 2 hours"))
        assertEquals(listOf("Blade Runner 2049", "La La Land"), titles("movies with Ryan Gosling"))
    }

    @Test
    fun combinedFilters() {
        assertEquals(listOf("Groundhog Day"), titles("90s comedies"))
        assertEquals(listOf("Arrival"), titles("dolby vision"))
        assertEquals(listOf("Arrival", "Interstellar"), titles("hdr movies")) // Dolby Vision is HDR too
        assertEquals(listOf("Arrival", "Blade Runner 2049"), titles("villeneuve films"))
        assertEquals(listOf("Dark"), titles("4k shows"))
        assertEquals(listOf("Blade Runner 2049"), titles("ryan gosling science fiction"))
    }

    @Test
    fun showWatchState() {
        val dark = library.shows().single()
        val episode = library.showDetail(dark.item.id)!!.seasons.single().episodes.first()
        library.setWatched(episode.item.id, true)
        assertEquals(listOf("Dark"), titles("unfinished shows"))
        library.setWatched(dark.item.id, true)
        assertEquals(listOf("Dark"), titles("watched shows"))
    }

    @Test
    fun interpretationIsReturnedAndTitlesStillWork() {
        val result = library.smartSearch("unwatched blade movies")
        assertEquals(setOf(MediaKind.MOVIE), result.query.kinds)
        assertEquals("blade", result.query.text)
        assertEquals(listOf("Blade Runner 2049"), result.results.map { it.item.title })

        val plain = library.smartSearch("groundhog")
        assertFalse(plain.query.structured)
        assertEquals(listOf("Groundhog Day"), plain.results.map { it.item.title })
    }
}
