package dev.reflux.library

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
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "Identify": telling Reflux what a work is when it guessed wrong or could not tell, and taking that back.
 * What the user said is Reflux's own state; files are never touched.
 */
class IdentifyTest {
    private val root: Path = Files.createTempDirectory("reflux-identify")
    private val otherRoot: Path = Files.createTempDirectory("reflux-identify-other")
    private var clock = 1_790_000_000_000L
    private val library = Library(testDatabase()) { clock }
    private val source = LocalFolderSource(root, "Media")
    private val provider = FakeMetadataProvider()

    init {
        library.addSource(source, root.toString())
    }

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
        otherRoot.toFile().deleteRecursively()
    }

    private fun file(relative: String, under: Path = root) = under.resolve(relative).also { it.parent.createDirectories(); it.writeBytes(ByteArray(8)) }

    @Test
    fun anAmbiguousFilmIsSetRightAndKeepsEverythingTheUserGaveIt() = runTest {
        val old = provider.movie("841", "Dune", 1984)
        val new = provider.movie("438631", "Dune", 2021, popularity = 90.0)
        file("Films/Dune.mkv")
        file("Films/Dune.2160p.HDR.mkv")
        library.scan(source)
        val guessed = library.movies().single()
        assertEquals(2, library.versions(guessed.item.id).size)
        library.stopped(guessed.item.id, 50 * 60_000L, 137 * 60_000L)
        library.setFavorite(guessed.item.id, true)
        val pinned = library.versions(guessed.item.id).first { "2160p" in it.version.location.path }.version.id
        library.pinVersion(guessed.item.id, pinned)
        assertEquals(false, library.identificationStatus(guessed.item.id)?.corrected)

        // Both films are offered, with what tells them apart.
        val candidates = library.searchMetadata(guessed.item.id, provider, "en-US", title = "Dune")
        assertEquals(setOf(old, new), candidates.map { it.candidate.ref }.toSet())
        val newId = library.identifyAs(guessed.item.id, candidates.single { it.candidate.ref == old }.candidate, provider, "en-US")

        val identified = library.movies().single()
        assertEquals(newId, identified.item.id)
        assertEquals(1984, (identified.item as Movie).year)
        assertEquals(old, identified.metadata?.ref)
        assertEquals(50 * 60_000L, identified.watchState?.positionMs, "the place follows the work")
        assertTrue(identified.favorite, "a favorite stays a favorite")
        assertEquals(setOf(pinned), library.pinnedVersions(newId), "the version Play always uses is still that one")
        assertEquals(2, library.versions(newId).size, "both copies are versions of the work chosen")
        val status = assertNotNull(library.identificationStatus(newId))
        assertTrue(status.corrected)
        assertFalse(status.weak)
        assertEquals(old, status.match)

        // It survives rescans, and nothing is left behind under the old identity.
        library.scan(source)
        assertEquals(newId, library.movies().single().item.id)
        assertNull(library.entry(guessed.item.id))
        assertTrue(Files.exists(root.resolve("Films/Dune.mkv")))
    }

    @Test
    fun undoingGivesTheWorkBackToItsFileNamesWithItsHistory() = runTest {
        val primer = provider.movie("14337", "Primer", 2004)
        file("Movies/movie.mkv")
        library.scan(source)
        val unknown = library.movies().single()
        library.setFavorite(unknown.item.id, true)
        val candidate = library.searchMetadata(unknown.item.id, provider, "en-US", title = "Primer").first { it.candidate.ref == primer }
        val identified = library.identifyAs(unknown.item.id, candidate.candidate, provider, "en-US")
        library.stopped(identified, 20 * 60_000L, 77 * 60_000L)

        val back = assertNotNull(library.undoIdentification(identified))
        assertEquals(unknown.item.id, back, "the work is what its file name says again")
        val entry = library.movies().single()
        assertEquals(20 * 60_000L, entry.watchState?.positionMs)
        assertTrue(entry.favorite)
        assertNull(entry.metadata?.ref?.takeIf { it == primer }, "the chosen entry is forgotten")
        assertEquals(false, library.identificationStatus(back)?.corrected)
        // Undone for good: a rescan does not bring the correction back.
        library.scan(source)
        assertEquals(unknown.item.id, library.movies().single().item.id)
    }

    @Test
    fun episodesWithoutAShowNameAreAShowOfTheirFolder() = runTest {
        file("S01E01.mkv")
        file("Season 1/S01E02.mkv")
        file("TV/S01E01.mkv")
        file("Videos/Season 2/S02E01.mkv")
        val other = LocalFolderSource(otherRoot, "Media")
        library.addSource(other, otherRoot.toString())
        file("S01E01.mkv", under = otherRoot)
        library.scan(source)
        library.scan(other)

        // One show per folder; at a source's top, the source's. Two sources of the same name stay two shows.
        val shows = library.shows()
        assertEquals(listOf("Media", "Media", "TV", "Videos"), shows.map { it.item.title }.sorted())
        val top = shows.single { show -> library.showDetail(show.item.id)!!.seasons.flatMap { it.episodes }.size == 2 }
        assertEquals(listOf(1, 2), library.showDetail(top.item.id)!!.seasons.flatMap { it.episodes }.map { (it.item as Episode).episodeNumber })
        // None of them is a confident identification: each is offered to Identify.
        assertTrue(shows.all { library.identificationStatus(it.item.id)?.weak == true })
        assertEquals(5, library.lowConfidence().size)

        // Rescanning keeps the same shows.
        library.scan(source)
        assertEquals(shows.map { it.item.id }.toSet(), (library.shows()).map { it.item.id }.toSet())
    }

    @Test
    fun identifyingOneFolderLeavesEveryOtherFolderAlone() = runTest {
        val firefly = provider.show("1437", "Firefly", 2002, mapOf(1 to 2))
        file("TV/S01E01.mkv")
        file("TV/S01E02.mkv")
        file("Videos/S01E01.mkv")
        library.scan(source)
        val tv = library.shows().single { it.item.title == "TV" }
        val videos = library.shows().single { it.item.title == "Videos" }
        val pilot = library.showDetail(tv.item.id)!!.seasons.single().episodes.first().item.id
        library.stopped(pilot, 5 * 60_000L, 44 * 60_000L)

        val candidate = library.searchMetadata(tv.item.id, provider, "en-US", title = "Firefly").first { it.candidate.ref == firefly }
        val identified = library.identifyAs(tv.item.id, candidate.candidate, provider, "en-US")

        assertEquals(listOf("Firefly", "Videos"), library.shows().map { it.item.title }.sorted())
        assertEquals(videos.item.id, library.shows().single { it.item.title == "Videos" }.item.id, "the other folder is untouched")
        val episodes = library.showDetail(identified)!!.seasons.single().episodes
        assertEquals(2, episodes.size)
        assertEquals(5 * 60_000L, episodes.first().watchState?.positionMs, "the episode's place follows it")
        library.scan(source)
        assertEquals(listOf("Firefly", "Videos"), library.shows().map { it.item.title }.sorted())

        // Undone, the folder is its own show again, the place still with its episode.
        val back = assertNotNull(library.undoIdentification(identified))
        assertNotEquals(identified, back)
        assertEquals(tv.item.id, back)
        assertEquals(5 * 60_000L, library.showDetail(back)!!.seasons.single().episodes.first().watchState?.positionMs)
        assertEquals(listOf("TV", "Videos"), library.shows().map { it.item.title }.sorted())
    }
}
