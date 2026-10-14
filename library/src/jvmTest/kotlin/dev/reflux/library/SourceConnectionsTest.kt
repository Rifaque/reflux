package dev.reflux.library

import dev.reflux.core.identify.ParsedKind
import dev.reflux.core.identify.ParsedMedia
import dev.reflux.core.model.Availability
import dev.reflux.core.model.StableIds
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.source.CatalogEntry
import dev.reflux.core.source.CatalogSource
import dev.reflux.core.source.CatalogVersion
import dev.reflux.core.source.Connection
import dev.reflux.core.source.ConnectionKind
import dev.reflux.core.source.ConnectionProblem
import dev.reflux.core.source.ConnectionRequest
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.core.source.SourceCapability
import dev.reflux.core.source.SourceConnector
import dev.reflux.core.source.SourceDescriptor
import dev.reflux.core.source.SourceLocality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A server at an address, with one film, that lets in whoever knows its password. */
private class Shelf(val address: String, val token: String) : CatalogSource {
    override val descriptor = SourceDescriptor(StableIds.sourceId("shelf", address), "shelf", "Shelf at $address", SourceLocality.LOCAL_NETWORK, setOf(SourceCapability.PROVIDES_CATALOG))
    override suspend fun availability() = Availability.AVAILABLE
    override suspend fun playbackTarget(path: String) = PlaybackTarget("shelf://$address/$path")
    override fun catalog(): Flow<CatalogEntry> = listOf(
        CatalogEntry(ParsedMedia(ParsedKind.MOVIE, "Heat", 1995), listOf(CatalogVersion("heat", 1, 1, StreamInfo(Container.MATROSKA)))),
        CatalogEntry(ParsedMedia(ParsedKind.EPISODE, "Severance", 2022, season = 1, episode = 1), listOf(CatalogVersion("sev1", 1, 1, StreamInfo(Container.MATROSKA)))),
        CatalogEntry(ParsedMedia(ParsedKind.EPISODE, "Severance", 2022, season = 1, episode = 2), listOf(CatalogVersion("sev2", 1, 1, StreamInfo(Container.MATROSKA)))),
    ).asFlow()
}

private object ShelfConnector : SourceConnector {
    override val type = "shelf"
    override val kind = ConnectionKind.SERVER
    override val product = "Shelf"
    override val addressExample = "shelf://home"
    override val anonymous = false
    var attempts = 0
    override fun accepts(address: String) = address.startsWith("shelf://")
    override suspend fun connect(request: ConnectionRequest): Connection {
        attempts++
        if (request.password != "secret") return Connection.Failed(ConnectionProblem.AUTHENTICATION, "rejected")
        val token = "token-$attempts"
        return Connection.Established(Shelf(request.address, token), "address=${request.address}\ntoken=$token")
    }
    override fun describe(config: String) = ConnectionRequest(config.lines().first().substringAfter('='), null)
}

class SourceConnectionsTest {
    private val library = Library(testDatabase()) { 1_790_000_000_000L }
    private val registry = SourceRegistry(mapOf("shelf" to { record -> Shelf(record.config.lines().first().substringAfter('='), "") }))
    private val reports = mutableListOf<String>()
    private val connections = SourceConnections(library, registry, listOf(ShelfConnector)) { problem, _ -> reports += problem }

    @Test
    fun anAddressBecomesASourceThatIsKeptAndCanBeScanned() = runTest {
        val connected = assertIs<ConnectResult.Connected>(connections.connect(ConnectionKind.SERVER, ConnectionRequest("shelf://home", "alex", "secret")))
        assertEquals("Shelf at shelf://home", connected.name)
        val record = assertNotNull(library.source(connected.sourceId))
        assertTrue("secret" !in record.config, "the password is not what is kept")
        assertNotNull(registry.resolve(connected.sourceId))
        library.scan(registry.resolve(connected.sourceId)!!)
        assertEquals(SourceContents(movies = 1, shows = 1), library.sourceContents(connected.sourceId))
        val info = assertNotNull(connections.describe(record))
        assertEquals(ConnectionKind.SERVER, info.kind)
        assertEquals("shelf://home", info.request?.address)
    }

    @Test
    fun aFailureAddsNothingAndSaysWhy() = runTest {
        assertEquals(ConnectResult.Failed(ConnectionProblem.AUTHENTICATION), connections.connect(ConnectionKind.SERVER, ConnectionRequest("shelf://home", "alex", "guess")))
        assertEquals(ConnectResult.Failed(ConnectionProblem.INVALID_ADDRESS), connections.connect(ConnectionKind.SERVER, ConnectionRequest("ftp://home", "alex", "secret")))
        assertEquals(ConnectResult.Failed(ConnectionProblem.INVALID_ADDRESS), connections.connect(ConnectionKind.SHARE, ConnectionRequest("shelf://home")), "no share connector here")
        assertTrue(library.sources().isEmpty())
        assertTrue(reports.any { "AUTHENTICATION" in it })
    }

    @Test
    fun signingInAgainKeepsTheSourceItsNameAndItsLibrary() = runTest {
        val first = connections.connect(ConnectionKind.SERVER, ConnectionRequest("shelf://home", "alex", "secret")) as ConnectResult.Connected
        library.scan(registry.resolve(first.sourceId)!!)
        library.queries.renameSource("Living room", first.sourceId.value)
        val again = assertIs<ConnectResult.Connected>(connections.connect(ConnectionKind.SERVER, ConnectionRequest("shelf://home", "alex", "secret"), replacing = first.sourceId))
        assertEquals(first.sourceId, again.sourceId)
        assertTrue(again.updated)
        assertEquals("Living room", again.name)
        assertTrue(library.source(first.sourceId)!!.config.endsWith("token-${ShelfConnector.attempts}"), "the new sign-in is what is kept")
        assertEquals(1, library.sources().size)
        assertEquals(1, library.sourceContents(first.sourceId).movies)
    }

    @Test
    fun aChangedAddressReplacesTheSource() = runTest {
        val first = connections.connect(ConnectionKind.SERVER, ConnectionRequest("shelf://old", "alex", "secret")) as ConnectResult.Connected
        val moved = assertIs<ConnectResult.Connected>(connections.connect(ConnectionKind.SERVER, ConnectionRequest("shelf://new", "alex", "secret"), replacing = first.sourceId))
        assertTrue(moved.sourceId != first.sourceId)
        assertNull(library.source(first.sourceId))
        assertNull(registry.resolve(first.sourceId))
        assertEquals(listOf(moved.sourceId), library.sources().map { it.id })
    }
}
