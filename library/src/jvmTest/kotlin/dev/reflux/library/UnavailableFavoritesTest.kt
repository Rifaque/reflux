package dev.reflux.library

import dev.reflux.core.model.Availability
import dev.reflux.core.source.ConnectionKind
import dev.reflux.core.source.ConnectionRequest
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * B-033: a favorite whose only source was removed stays the viewer's (local first), is shown as not available, and
 * can be removed without the source back; another profile's favorites are never touched.
 */
class UnavailableFavoritesTest {
    private val clock = 1_790_000_000_000L
    private val library = Library(LibraryDatabase.open(Files.createTempDirectory("reflux-favorites").resolve("library.db"))) { clock }
    private val registry = AccountConnector.registry
    private val connections = SourceConnections(library, registry, listOf(AccountConnector))

    @BeforeTest
    fun reset() = AccountServers.reset()

    private suspend fun connect(account: String) =
        assertIs<ConnectResult.Connected>(connections.connect(ConnectionKind.SERVER, ConnectionRequest("acct://home", account, "pw"))).sourceId

    private suspend fun scanAll() = library.sources().forEach { record -> registry.resolve(record.id)?.let { library.scan(it) } }

    @Test
    fun aFavoriteOfARemovedSourceStaysCanBeRemovedAndComesBackOnlyIfKept() = runTest {
        AccountServers.movie("alex", "heat", "Heat", 1995)
        val main = library.activeProfile()
        val source = connect("alex")
        scanAll()
        val heat = library.movies().single().item.id
        library.setFavorite(heat, true)
        // Another viewer's favorite of the same work, on the shared item.
        val sam = library.createProfile("Sam")
        library.switchProfile(sam)
        library.setFavorite(heat, true)
        library.switchProfile(main)

        // Its only source goes: the favorite stays, and says nothing can play it.
        connections.remove(source).await()
        assertTrue(library.isFavorite(heat))
        val shown = library.favorites().single()
        assertEquals(heat, shown.item.id)
        assertEquals(Availability.UNAVAILABLE, shown.availability)
        assertTrue(library.movies().isEmpty(), "it is not in the library's films")

        // Removed from favorites, without the source.
        library.setFavorite(heat, false)
        assertFalse(library.isFavorite(heat))
        assertTrue(library.favorites().isEmpty())

        // The source again: the film is back, not a favorite, because the viewer said so.
        connect("alex")
        scanAll()
        assertEquals(listOf("Heat"), library.movies().map { it.item.title })
        assertFalse(library.movies().single().favorite)
        // Sam's favorite was never touched.
        library.switchProfile(sam)
        assertTrue(library.isFavorite(heat))
        assertEquals(listOf(heat), library.favorites().map { it.item.id })
    }

    @Test
    fun aFavoriteKeptWhileItsSourceWasAwayIsPlayableAgainWhenItComesBack() = runTest {
        AccountServers.movie("alex", "heat", "Heat", 1995)
        val source = connect("alex")
        scanAll()
        val heat = library.movies().single().item.id
        library.setFavorite(heat, true)
        connections.remove(source).await()
        connect("alex")
        scanAll()
        val back = library.favorites().single()
        assertEquals(heat, back.item.id, "the same work, still a favorite")
        assertTrue(back.availability != Availability.UNAVAILABLE)
    }
}
