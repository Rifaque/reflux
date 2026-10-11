package dev.reflux.desktop.engine

import dev.reflux.core.model.Availability
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopEnvironmentTest {
    private val root: Path = Files.createTempDirectory("reflux-engine").also { it.toFile().deleteOnExit() }
    private val data: Path = root.resolve("data")

    private fun media(relative: String): Path =
        root.resolve(relative).also { it.parent.createDirectories() }.also { it.writeBytes(ByteArray(8)) }

    private suspend fun DesktopEnvironment.scan(addition: FolderAddition) =
        library.scan(assertIs<FolderAddition.Added>(addition).source)

    @Test
    fun aChosenFolderIsRememberedAcrossLaunches() = runTest {
        media("Media/Heat (1995)/Heat.1995.1080p.mkv")
        val first = DesktopEnvironment(data)
        first.scan(first.addLocalFolder(root.resolve("Media")))
        assertEquals("Heat", first.library.movies().single().item.title)

        // The next launch finds the source and its library without asking again.
        val second = DesktopEnvironment(data)
        val source = second.library.sources().single()
        assertEquals("Media", source.displayName)
        assertNotNull(second.source(source.id), "the adapter is recreated from what was persisted")
        assertEquals(Availability.AVAILABLE, second.source(source.id)?.availability())
        assertEquals("Heat", second.library.movies().single().item.title)
    }

    @Test
    fun theSameFilesNeverBecomeTwoSources() = runTest {
        media("Media/Movies/Heat (1995)/Heat.1995.1080p.mkv")
        val environment = DesktopEnvironment(data)
        val movies = assertIs<FolderAddition.Added>(environment.addLocalFolder(root.resolve("Media/Movies")))

        val again = assertIs<FolderAddition.AlreadyCovered>(environment.addLocalFolder(root.resolve("Media/Movies")))
        assertEquals(movies.source.descriptor.id, again.by.id)
        val spelledDifferently = root.resolve("Media/Movies/../Movies/.")
        assertIs<FolderAddition.AlreadyCovered>(environment.addLocalFolder(spelledDifferently))
        val inside = assertIs<FolderAddition.AlreadyCovered>(environment.addLocalFolder(root.resolve("Media/Movies/Heat (1995)")))
        assertEquals("Movies", inside.by.displayName)
        assertEquals(1, environment.library.sources().size)
    }

    @Test
    fun aWiderFolderTakesOverTheFoldersInsideItAndWatchHistorySurvives() = runTest {
        media("Media/Movies/Heat (1995)/Heat.1995.1080p.mkv")
        media("Media/Shows/Severance/Season 1/Severance.S01E01.mkv")
        val environment = DesktopEnvironment(data)
        environment.scan(environment.addLocalFolder(root.resolve("Media/Movies")))
        val heat = environment.library.movies().single().item.id
        environment.library.setWatched(heat, true)

        val wider = assertIs<FolderAddition.Added>(environment.addLocalFolder(root.resolve("Media")))
        assertEquals(listOf("Movies"), wider.replaced.map { it.displayName })
        environment.library.scan(wider.source)

        assertEquals(listOf("Media"), environment.library.sources().map { it.displayName })
        val movie = environment.library.movies().single()
        assertEquals(heat, movie.item.id, "the work is the same work, wherever its file is reached from")
        assertTrue(movie.watchState?.completed == true)
        assertEquals(1, environment.library.versions(heat).size, "one file is one version")
        assertEquals(1, environment.library.shows().size)
    }

    @Test
    fun somethingThatIsNotAFolderIsRefused() {
        val environment = DesktopEnvironment(data)
        assertIs<FolderAddition.NotAFolder>(environment.addLocalFolder(media("Media/Heat.1995.mkv")))
        assertIs<FolderAddition.NotAFolder>(environment.addLocalFolder(root.resolve("Missing")))
        assertTrue(environment.library.sources().isEmpty())
    }

    @Test
    fun aFolderThatCannotBeReadIsRefused() {
        val folder = root.resolve("Private").createDirectories()
        val restricted = runCatching { Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("---------")) }.isSuccess
        assumeTrue("needs POSIX permissions and a user they apply to", restricted && !Files.isReadable(folder))
        try {
            val environment = DesktopEnvironment(data)
            assertIs<FolderAddition.Unreadable>(environment.addLocalFolder(folder))
            assertTrue(environment.library.sources().isEmpty())
        } finally {
            Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("rwx------"))
        }
    }

    @Test
    fun problemsAreWrittenDownWithTheirCause() {
        val log = DiagnosticLog(data.resolve("reflux.log"), maxBytes = 400)
        log.record("Refreshing the library failed", IllegalStateException("the share dropped the connection"))
        val written = Files.readString(log.file)
        assertTrue("Refreshing the library failed" in written && "the share dropped the connection" in written && "IllegalStateException" in written)

        // The log cannot grow without bound: a full one is set aside, and one generation is kept.
        log.record("Playback failed")
        assertFalse("the share dropped" in Files.readString(log.file))
        assertTrue("the share dropped" in Files.readString(log.file.resolveSibling("reflux.log.1")))
        assertEquals(data.resolve("reflux.log"), DesktopEnvironment(data).diagnostics.file, "the log lives next to the library")
    }
}
