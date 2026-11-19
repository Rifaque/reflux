package dev.reflux.library

import dev.reflux.core.source.ConnectionKind
import dev.reflux.core.source.ConnectionRequest
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * B-006: title search keeps what it searches between searches, so this is what proves it is never stale: whatever
 * changes the library, a description, a source, or what a profile sees is in the very next search.
 */
class SearchFreshnessTest {
    private val root: Path = Files.createTempDirectory("reflux-search")
    private val clock = 1_790_000_000_000L
    private val library = Library(testDatabase()) { clock }
    private val folder = LocalFolderSource(root)
    private val provider = FakeMetadataProvider()
    private val registry = AccountConnector.registry
    private val connections = SourceConnections(library, registry, listOf(AccountConnector))

    @BeforeTest
    fun reset() {
        AccountServers.reset()
        library.addSource(folder, root.toString())
    }

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
    }

    private fun file(relative: String) = root.resolve(relative).also { it.parent.createDirectories(); it.writeBytes(ByteArray(8)) }
    private fun titles(query: String) = library.search(query).map { it.item.title }

    @Test
    fun aNewFileAGoneFileAndADescriptionAreInTheNextSearch() = runTest {
        file("Movies/Heat (1995)/Heat.1995.1080p.mkv")
        library.scan(folder)
        assertEquals(listOf("Heat"), titles("heat"))
        assertEquals(emptyList(), titles("alien"), "kept between searches")
        // A file added: found at once.
        file("Movies/Alien (1979)/Alien.1979.1080p.mkv")
        library.scan(folder)
        assertEquals(listOf("Alien"), titles("alien"))
        // A description: Amélie is found by its original title once the provider says it.
        provider.movie("194", "Amélie", 2001, original = "Le Fabuleux Destin d'Amélie Poulain")
        file("Movies/amelie.2001.1080p.mkv")
        library.scan(folder)
        assertEquals(emptyList(), titles("fabuleux destin"))
        library.refreshMetadata(provider, "en-US")
        assertEquals(listOf("Amélie"), titles("fabuleux destin"))
        // A file gone: not found any more.
        Files.delete(root.resolve("Movies/Alien (1979)/Alien.1979.1080p.mkv"))
        library.scan(folder)
        assertEquals(emptyList(), titles("alien"))
        // The source gone: nothing of it.
        library.removeSource(folder.descriptor.id)
        assertEquals(emptyList(), titles("heat"))
    }

    @Test
    fun anotherProfilesAccountIsNeverSearchedAndASwitchIsInTheNextSearch() = runTest {
        AccountServers.movie("alex", "primer", "Primer", 2004)
        val main = library.activeProfile()
        assertIs<ConnectResult.Connected>(connections.connect(ConnectionKind.SERVER, ConnectionRequest("acct://home", "alex", "pw")))
        library.sources().forEach { record -> registry.resolve(record.id)?.let { library.scan(it) } }
        assertEquals(listOf("Primer"), titles("primer"))
        val sam = library.createProfile("Sam")
        library.switchProfile(sam)
        assertTrue(titles("primer").isEmpty(), "Alex's account is not Sam's to search")
        library.switchProfile(main)
        assertEquals(listOf("Primer"), titles("primer"))
    }

    @Test
    fun aSourceAddedLaterIsSearched() = runTest {
        file("Movies/Heat (1995)/Heat.1995.1080p.mkv")
        library.scan(folder)
        assertEquals(listOf("Heat"), titles("heat"))
        val other = Files.createTempDirectory("reflux-search-2")
        try {
            other.resolve("Ran (1985)").createDirectories()
            other.resolve("Ran (1985)/Ran.1985.mkv").writeBytes(ByteArray(8))
            val second = LocalFolderSource(other)
            library.addSource(second, other.toString())
            library.scan(second)
            assertEquals(listOf("Ran"), titles("ran"))
        } finally {
            other.toFile().deleteRecursively()
        }
    }
}
