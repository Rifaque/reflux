package dev.reflux.library

import dev.reflux.core.identify.IdentityOverride
import dev.reflux.core.identify.ParsedKind
import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.Availability
import dev.reflux.core.model.Episode
import dev.reflux.core.model.MediaLocation
import dev.reflux.core.model.Movie
import dev.reflux.core.model.Show
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.DisplayCapabilities
import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoDecoderSupport
import dev.reflux.sources.local.LocalFolderSource
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.moveTo
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryTest {
    private val root: Path = Files.createTempDirectory("reflux-library")
    private var clock = 1_790_000_000_000L // 2026-09
    private val library = Library(LibraryDatabase.inMemory()) { clock }
    private val source = LocalFolderSource(root, "Media")

    init {
        library.addSource(source, root.toString())
    }

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
    }

    private fun file(relative: String, size: Int = 10): Path =
        root.resolve(relative).also { it.parent.createDirectories(); it.writeBytes(ByteArray(size)) }

    private val sampleLibrary = listOf(
        "Movies/Interstellar (2014)/Interstellar (2014).mkv",
        "Movies/Interstellar (2014)/poster.jpg",
        "Movies/Interstellar (2014)/Interstellar (2014).en.srt",
        "Movies/The.Dark.Knight.2008.2160p.UHD.BluRay.x265.HDR.TrueHD.7.1-GRP.mkv",
        "Movies/The Dark Knight (2008).mp4",
        "Movies/Heat (1995)/Heat (1995).mkv",
        "Movies/Heat (1995)/Extras/Making Of.mkv",
        "TV/Breaking Bad/poster.jpg",
        "TV/Breaking Bad/Season 1/Breaking.Bad.S01E01.Pilot.720p.mkv",
        "TV/Breaking Bad/Season 1/Breaking.Bad.S01E02.720p.mkv",
        "TV/Breaking Bad/Season 2/Breaking.Bad.S02E01.720p.mkv",
        "TV/Breaking Bad/season02-poster.jpg",
    )

    @Test
    fun folderToVirtualLibrary() = runTest {
        sampleLibrary.forEach { file(it) }
        val report = library.scan(source)

        assertEquals(ScanReport.Status.COMPLETED, report.status)
        assertEquals(7, report.added)
        assertEquals(mapOf(ParsedKind.EXTRA to 1), report.skipped)

        val movies = library.movies()
        assertEquals(listOf("The Dark Knight", "Heat", "Interstellar"), movies.map { it.item.title })
        assertTrue(movies.all { it.availability == Availability.AVAILABLE })

        val darkKnight = movies.first { it.item.title == "The Dark Knight" }
        val versions = library.versions(darkKnight.item.id)
        assertEquals(2, versions.size)
        assertEquals(setOf(2160, null), versions.map { it.version.stream.video?.height }.toSet())

        val interstellar = movies.first { it.item.title == "Interstellar" }
        assertEquals(
            ArtworkLocator.SourceFile(MediaLocation(source.descriptor.id, "Movies/Interstellar (2014)/poster.jpg")),
            interstellar.artwork[ArtworkKind.POSTER],
        )
        val subtitle = library.versions(interstellar.item.id).single().version.externalSubtitles.single()
        assertEquals("en", subtitle.language)
        assertEquals(SubtitleFormat.SRT, subtitle.format)

        val show = library.shows().single()
        assertEquals("Breaking Bad", show.item.title)
        assertNotNull(show.artwork[ArtworkKind.POSTER])
        val detail = library.showDetail(show.item.id)!!
        assertEquals(listOf(1, 2), detail.seasons.map { it.season.number })
        assertEquals(listOf("Pilot", "Episode 2"), detail.seasons[0].episodes.map { it.item.title })
        assertNotNull(detail.seasons[1].entry.artwork[ArtworkKind.POSTER])
    }

    @Test
    fun rescanningIsIdempotent() = runTest {
        sampleLibrary.forEach { file(it) }
        library.scan(source)
        val before = library.movies() to library.shows()
        val report = library.scan(source)
        assertEquals(0, report.added)
        assertEquals(0, report.updated)
        assertEquals(0, report.removed)
        assertEquals(7, report.unchanged)
        assertEquals(before, library.movies() to library.shows())
    }

    @Test
    fun disconnectedSourceKeepsTheLibrary() = runTest {
        sampleLibrary.forEach { file(it) }
        library.scan(source)
        val movie = library.movies().first()
        library.reportStopped(movie.item.id, 30 * 60_000L, 120 * 60_000L)

        // The drive disappears.
        val unplugged = root.resolveSibling(root.fileName.toString() + "-unplugged")
        root.moveTo(unplugged)
        try {
            assertEquals(ScanReport.Status.UNAVAILABLE, library.scan(source).status)
            val movies = library.movies()
            assertEquals(3, movies.size)
            assertTrue(movies.all { it.availability == Availability.UNAVAILABLE })
            assertEquals(30 * 60_000L, library.watchState(movie.item.id)?.positionMs)
            assertEquals(1, library.search("interstellar").size)
            assertNull(library.selectVersion(movie.item.id, desktop).best)
        } finally {
            unplugged.moveTo(root)
        }
        assertEquals(ScanReport.Status.COMPLETED, library.scan(source).status)
        assertTrue(library.movies().all { it.availability == Availability.AVAILABLE })
    }

    @Test
    fun emptyMountPointIsNotTreatedAsDeletion() = runTest {
        sampleLibrary.forEach { file(it) }
        library.scan(source)
        // An unmounted share often leaves an empty mount-point directory behind.
        root.toFile().listFiles()!!.forEach { it.deleteRecursively() }
        assertEquals(ScanReport.Status.EMPTY_KEPT, library.scan(source).status)
        assertEquals(3, library.movies().size)
        assertEquals(Availability.UNAVAILABLE, library.source(source.descriptor.id)?.availability)
    }

    @Test
    fun deletedFilesLeaveTheLibraryButWatchHistoryStays() = runTest {
        sampleLibrary.forEach { file(it) }
        library.scan(source)
        val heat = library.movies().single { it.item.title == "Heat" }
        library.setWatched(heat.item.id, true)

        root.resolve("Movies/Heat (1995)/Heat (1995).mkv").deleteExisting()
        val report = library.scan(source)
        assertEquals(1, report.removed)
        assertTrue(library.movies().none { it.item.title == "Heat" })

        // The file comes back (e.g. restored from backup): its history is still there.
        file("Movies/Heat (1995)/Heat (1995).mkv")
        library.scan(source)
        assertEquals(true, library.watchState(heat.item.id)?.completed)
    }

    @Test
    fun changedFilesAreUpdated() = runTest {
        sampleLibrary.forEach { file(it) }
        library.scan(source)
        file("Movies/Heat (1995)/Heat (1995).mkv", size = 99)
        val report = library.scan(source)
        assertEquals(1, report.updated)
        val heat = library.movies().single { it.item.title == "Heat" }
        assertEquals(99, library.versions(heat.item.id).single().version.sizeBytes)
    }

    @Test
    fun yearlessFileJoinsKnownMovie() = runTest {
        file("Movies/Arrival (2016).mkv")
        file("Downloads/arrival.1080p.mkv")
        library.scan(source)
        val movie = library.movies().single()
        assertEquals(2016, (movie.item as Movie).year)
        assertEquals(2, library.versions(movie.item.id).size)
    }

    @Test
    fun identifyCorrectionPersistsAndCarriesHistory() = runTest {
        file("Movies/movie.mkv")
        library.scan(source)
        val wrong = library.movies().single()
        assertEquals(1, library.lowConfidence().size)
        library.reportStopped(wrong.item.id, 10 * 60_000L, 100 * 60_000L)

        val location = MediaLocation(source.descriptor.id, "Movies/movie.mkv")
        library.identify(location, IdentityOverride(ParsedKind.MOVIE, "Primer", 2004))
        val corrected = library.movies().single()
        assertEquals("Primer", corrected.item.title)
        assertEquals(10 * 60_000L, corrected.watchState?.positionMs)
        assertTrue(library.lowConfidence().isEmpty())

        // The correction survives rescans and never touches the file.
        library.scan(source)
        assertEquals("Primer", library.movies().single().item.title)
        assertTrue(Files.exists(root.resolve("Movies/movie.mkv")))
    }

    @Test
    fun identifyCanTurnAMovieIntoAnEpisode() = runTest {
        file("Clips/pilot.mkv")
        library.scan(source)
        library.identify(
            MediaLocation(source.descriptor.id, "Clips/pilot.mkv"),
            IdentityOverride(ParsedKind.EPISODE, "Firefly", 2002, season = 1, episode = 1),
        )
        assertTrue(library.movies().isEmpty())
        val show = library.shows().single()
        assertEquals("Firefly", show.item.title)
        assertEquals(1, (library.showDetail(show.item.id)!!.seasons.single().episodes.single().item as Episode).episodeNumber)
    }

    @Test
    fun continueWatchingAndNextUp() = runTest {
        sampleLibrary.forEach { file(it) }
        library.scan(source)
        val heat = library.movies().single { it.item.title == "Heat" }
        val show = library.shows().single()
        val episodes = library.showDetail(show.item.id)!!.seasons.flatMap { it.episodes }

        clock += 1000
        library.reportStopped(heat.item.id, 40 * 60_000L, 170 * 60_000L)
        clock += 1000
        library.reportStopped(episodes[0].item.id, 46 * 60_000L, 47 * 60_000L) // finished the pilot

        val continueWatching = library.continueWatching()
        assertEquals(listOf(episodes[1].item.id, heat.item.id), continueWatching.map { it.item.id })
        assertEquals<Any?>(episodes[1].item, library.showDetail(show.item.id)!!.nextUp)
    }

    @Test
    fun markingAShowWatchedMarksItsEpisodes() = runTest {
        sampleLibrary.forEach { file(it) }
        library.scan(source)
        val show = library.shows().single()
        library.setWatched(show.item.id, true)
        val episodes = library.showDetail(show.item.id)!!.seasons.flatMap { it.episodes }
        assertTrue(episodes.all { it.watchState?.completed == true })
        assertNull(library.showDetail(show.item.id)!!.nextUp)
    }

    @Test
    fun bestVersionUsesDeviceCapabilities() = runTest {
        sampleLibrary.forEach { file(it) }
        library.scan(source)
        val darkKnight = library.movies().single { it.item.title == "The Dark Knight" }
        val selection = library.selectVersion(darkKnight.item.id, desktop)
        assertEquals(Container.MATROSKA, selection.best?.version?.stream?.container)

        val other = library.versions(darkKnight.item.id).single { it.version.id != selection.best?.version?.id }
        library.setPreferredVersion(darkKnight.item.id, other.version.id)
        assertEquals(other.version.id, library.selectVersion(darkKnight.item.id, desktop).best?.version?.id)
    }

    @Test
    fun searchFindsMoviesShowsAndEpisodes() = runTest {
        sampleLibrary.forEach { file(it) }
        library.scan(source)
        assertEquals("The Dark Knight", library.search("dark kni").single().item.title)
        assertIs<Show>(library.search("breaking").first().item)
        assertEquals("Pilot", library.search("pilot").single().item.title)
    }

    @Test
    fun favoritesAndRecentlyAdded() = runTest {
        file("Movies/Heat (1995).mkv")
        library.scan(source)
        clock += 60_000
        file("TV/Lost/Season 1/Lost.S01E01.mkv")
        library.scan(source)
        assertEquals(listOf("Lost", "Heat"), library.recentlyAdded().map { it.item.title })

        val heat = library.movies().single()
        library.setFavorite(heat.item.id, true)
        assertEquals(listOf(heat.item.id), library.favorites().map { it.item.id })
        assertTrue(library.movies().single().favorite)
    }

    @Test
    fun removingASourceKeepsWatchHistoryOnly() = runTest {
        sampleLibrary.forEach { file(it) }
        library.scan(source)
        val heat = library.movies().single { it.item.title == "Heat" }
        library.reportStopped(heat.item.id, 20 * 60_000L, 170 * 60_000L)
        library.removeSource(source.descriptor.id)
        assertTrue(library.movies().isEmpty())
        assertTrue(library.sources().isEmpty())
        assertEquals(20 * 60_000L, library.watchState(heat.item.id)?.positionMs)
    }

    private val desktop = DeviceCapabilities(
        videoDecoders = listOf(
            VideoDecoderSupport(VideoCodec.H264, 4096, 2304, 8, hardware = true),
            VideoDecoderSupport(VideoCodec.HEVC, 4096, 2304, 10, hardware = true),
        ),
        audioDecoders = AudioCodec.entries.toSet(),
        maxOutputChannels = 2,
        display = DisplayCapabilities(3840, 2160, setOf(DynamicRange.SDR, DynamicRange.HDR10)),
        containers = Container.entries.toSet(),
        subtitleFormats = SubtitleFormat.entries.toSet(),
    )

}
