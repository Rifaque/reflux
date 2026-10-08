package dev.reflux.library

import dev.reflux.core.identify.ParsedKind
import dev.reflux.core.identify.ParsedMedia
import dev.reflux.core.model.Availability
import dev.reflux.core.model.StableIds
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.source.CatalogEntry
import dev.reflux.core.source.CatalogSource
import dev.reflux.core.source.CatalogVersion
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.core.source.SourceCapability
import dev.reflux.core.source.SourceDescriptor
import dev.reflux.core.source.SourceLocality
import dev.reflux.sources.local.LocalFolderSource
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class Server(private val entries: List<CatalogEntry>) : CatalogSource {
    override val descriptor = SourceDescriptor(
        StableIds.sourceId("server", "s"), "server", "Server", SourceLocality.LOCAL_NETWORK, setOf(SourceCapability.PROVIDES_CATALOG),
    )
    override suspend fun availability() = Availability.AVAILABLE
    override suspend fun playbackTarget(path: String) = PlaybackTarget("https://server/$path")
    override fun catalog() = entries.asFlow()
}

class UnificationTest {
    private val root: Path = Files.createTempDirectory("reflux-unify")
    private var clock = 1_790_000_000_000L
    private val library = Library(LibraryDatabase.inMemory()) { clock }
    private val local = LocalFolderSource(root)
    private val provider = FakeMetadataProvider()

    init {
        library.addSource(local, root.toString())
    }

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
    }

    private fun file(relative: String) = root.resolve(relative).also { it.parent.createDirectories(); it.writeBytes(ByteArray(8)) }

    private fun version(path: String) = CatalogVersion(path, 1, 1, StreamInfo())

    @Test
    fun differentlyNamedCopiesBecomeOneWork() = runTest {
        provider.movie("11", "Star Wars", 1977)
        file("Movies/Star Wars (1977).mkv")
        library.scan(local)
        val localMovie = library.movies().single()
        library.stopped(localMovie.item.id, 30 * 60_000L, 121 * 60_000L)
        library.setFavorite(localMovie.item.id, true)
        library.refreshMetadata(provider, "en-US")

        clock += 1_000
        val server = Server(
            listOf(
                CatalogEntry(
                    ParsedMedia(ParsedKind.MOVIE, "Star Wars: Episode IV - A New Hope", 1977, externalIds = mapOf("tmdb" to "11")),
                    listOf(version("items/sw/sources/sw")),
                ),
            ),
        )
        library.addSource(server, "s")
        library.scan(server)
        assertEquals(2, library.movies().size, "two names: two works until unified")

        assertEquals(1, library.unifyWorks())
        val movie = library.movies().single()
        assertEquals(localMovie.item.id, movie.item.id, "the oldest work survives")
        assertEquals(2, library.versions(movie.item.id).size)
        assertEquals(30 * 60_000L, movie.watchState?.positionMs)
        assertTrue(movie.favorite)

        // The alias is permanent: rescans keep one work.
        library.scan(server)
        library.scan(local)
        assertEquals(1, library.movies().size)
        assertEquals(0, library.unifyWorks())
    }

    @Test
    fun showsMergeEpisodeByEpisode() = runTest {
        file("TV/The Office (US)/Season 2/The Office (US) - S02E01.mkv")
        library.scan(local)
        val office = library.shows().single()
        val episode = library.showDetail(office.item.id)!!.seasons.single().episodes.single()
        library.setWatched(episode.item.id, true)
        provider.show("2316", "The Office", 2005, mapOf(2 to 22))
        library.refreshMetadata(provider, "en-US")

        clock += 1_000
        val server = Server(
            listOf(
                CatalogEntry(
                    ParsedMedia(ParsedKind.EPISODE, "The Office", 2005, season = 2, episode = 1, episodeTitle = "The Dundies", externalIds = mapOf("tmdb" to "2316")),
                    listOf(version("items/e1/sources/e1")),
                ),
                CatalogEntry(
                    ParsedMedia(ParsedKind.EPISODE, "The Office", 2005, season = 2, episode = 2, externalIds = mapOf("tmdb" to "2316")),
                    listOf(version("items/e2/sources/e2")),
                ),
            ),
        )
        library.addSource(server, "s")
        library.scan(server)
        assertEquals(2, library.shows().size)

        library.unifyWorks()
        val show = library.shows().single()
        val episodes = library.showDetail(show.item.id)!!.seasons.single().episodes
        assertEquals(listOf(1, 2), episodes.map { (it.item as dev.reflux.core.model.Episode).episodeNumber })
        assertEquals(2, library.versions(episodes.first().item.id).size)
        assertEquals(true, episodes.first().watchState?.completed)
    }

    @Test
    fun differentWorksAreNeverMerged() = runTest {
        provider.movie("1", "Heat", 1995)
        provider.movie("2", "Alien", 1979)
        file("Heat (1995).mkv")
        file("Alien (1979).mkv")
        library.scan(local)
        library.refreshMetadata(provider, "en-US")
        assertEquals(0, library.unifyWorks())
        assertEquals(2, library.movies().size)
    }
}
