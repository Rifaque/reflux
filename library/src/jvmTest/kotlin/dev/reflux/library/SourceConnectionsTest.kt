package dev.reflux.library

import dev.reflux.core.identify.ParsedKind
import dev.reflux.core.identify.ParsedMedia
import dev.reflux.core.model.Availability
import dev.reflux.core.model.SourceId
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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    /** How the shelf answers being signed out of, by token. */
    var signOutAnswer: suspend (String) -> Boolean = { true }

    /** The tokens it was asked to end, in order. */
    val signedOut = mutableListOf<String>()

    override suspend fun signOut(config: String, keeping: String?): Boolean {
        val token = config.lines().last().substringAfter('=')
        if (keeping?.lines()?.last()?.substringAfter('=') == token) return false
        signedOut += token
        return signOutAnswer(token)
    }
}

/** A folder: a source that is not connected by address, so there is nothing to sign out of. */
private class Folder : CatalogSource {
    override val descriptor = SourceDescriptor(StableIds.sourceId("folder", "/media"), "folder", "Media", SourceLocality.DEVICE, setOf(SourceCapability.PROVIDES_CATALOG))
    override suspend fun availability() = Availability.AVAILABLE
    override suspend fun playbackTarget(path: String) = PlaybackTarget("file:///media/$path")
    override fun catalog(): Flow<CatalogEntry> = emptyList<CatalogEntry>().asFlow()
}

class SourceConnectionsTest {
    private val library = Library(testDatabase()) { 1_790_000_000_000L }
    private val registry = SourceRegistry(mapOf("shelf" to { record -> Shelf(record.config.lines().first().substringAfter('='), "") }))
    private val reports = mutableListOf<String>()
    private val connections = SourceConnections(library, registry, listOf(ShelfConnector), report = { problem, _ -> reports += problem })

    init {
        ShelfConnector.signOutAnswer = { true }
        ShelfConnector.signedOut.clear()
    }

    /** Connections whose sign-outs run on the test's clock, so that waiting for a server takes no real time. */
    private fun TestScope.timed(seal: ConfigSeal = ConfigSeal.None) = SourceConnections(
        library, registry, listOf(ShelfConnector), report = { problem, cause -> reports += problem + cause?.message?.let { ": $it" }.orEmpty() },
        seal = seal, signOutContext = StandardTestDispatcher(testScheduler),
    )

    private suspend fun SourceConnections.signedIn(address: String = "shelf://home") =
        (connect(ConnectionKind.SERVER, ConnectionRequest(address, "alex", "secret")) as ConnectResult.Connected).sourceId

    private val lastToken get() = "token-${ShelfConnector.attempts}"

    /** Secure storage in memory, to show what it still holds. */
    private class MemorySeal : ConfigSeal {
        val kept = mutableMapOf<String, String>()
        override fun seal(id: SourceId, config: String) = "sealed:${id.value}".also { kept[id.value] = config }
        override fun open(id: SourceId, stored: String) = kept[id.value].orEmpty()
        override fun sealed(stored: String) = stored.startsWith("sealed:")
        override fun forget(id: SourceId) {
            kept.remove(id.value)
        }
    }

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

    @Test
    fun removingAServerEndsItsSessionWithTheTokenItWasKeptWith() = runTest {
        val seal = MemorySeal()
        val connections = timed(seal)
        val id = connections.signedIn()
        val token = lastToken

        val signOut = connections.remove(id)
        assertNull(library.source(id), "removed at once")
        assertNull(registry.resolve(id))
        assertTrue(seal.kept.isEmpty(), "and what was kept for it is forgotten")
        assertEquals(SignOut.ENDED, signOut.await())
        assertEquals(listOf(token), ShelfConnector.signedOut, "only its own session")
        assertTrue(reports.isEmpty())
    }

    @Test
    fun aServerThatCannotBeReachedIsStillRemovedAndTheSignOutIsNotClaimed() = runTest {
        val connections = timed()
        val id = connections.signedIn()
        val token = lastToken
        ShelfConnector.signOutAnswer = { error("no route to host") }

        val signOut = connections.remove(id)
        assertNull(library.source(id))
        assertEquals(SignOut.UNCONFIRMED, signOut.await())
        val report = reports.single()
        assertTrue("not confirmed" in report)
        assertFalse(token in report, "no token in the log")
    }

    @Test
    fun aServerThatStopsAnsweringMidRequestDoesNotHoldTheRemovalUp() = runTest {
        val connections = timed()
        val id = connections.signedIn()
        ShelfConnector.signOutAnswer = { awaitCancellation() }
        val started = testScheduler.currentTime

        val signOut = connections.remove(id)
        assertNull(library.source(id), "removed before the server is even asked")
        assertTrue(signOut.isActive)
        assertEquals(SignOut.UNCONFIRMED, signOut.await())
        assertEquals(SourceConnections.SIGN_OUT_WITHIN_MS, testScheduler.currentTime - started, "given up after the time it was allowed")
        assertTrue(reports.single().contains("no answer in time"))
    }

    @Test
    fun aServerAlreadyAwayIsRemovedAllTheSame() = runTest {
        val connections = timed()
        val id = connections.signedIn()
        library.queries.setSourceAvailability(Availability.UNAVAILABLE.name, 1L, id.value)
        ShelfConnector.signOutAnswer = { error("connection refused") }
        assertEquals(SignOut.UNCONFIRMED, connections.remove(id).await())
        assertNull(library.source(id))
        assertNull(registry.resolve(id))
    }

    @Test
    fun aSourceThatIsNotSignedInToAnythingHasNothingToSignOut() = runTest {
        val folder = Folder()
        library.addSource(folder, "/media")
        registry.register(folder)
        assertEquals(SignOut.NONE, timed().remove(folder.descriptor.id).await())
        assertNull(library.source(folder.descriptor.id))
        assertTrue(ShelfConnector.signedOut.isEmpty())
    }

    @Test
    fun removingAServerKeepsWhatWasWatchedAndAddingItAgainSignsInAfresh() = runTest {
        val connections = timed()
        val id = connections.signedIn()
        library.scan(registry.resolve(id)!!)
        val heat = library.movies().single().item.id
        library.setWatched(heat, true)
        val episode = library.showDetail(library.shows().single().item.id)!!.seasons.single().episodes.first().item.id
        library.progress(episode, 600_000, 3_600_000, null)
        val first = lastToken

        assertEquals(SignOut.ENDED, connections.remove(id).await())
        assertNull(library.source(id))
        assertEquals(true, library.watchState(heat)?.completed, "watch history belongs to the work, not the source")
        assertEquals(600_000, library.watchState(episode)?.positionMs, "and progress is not reset")

        val again = connections.signedIn()
        assertEquals(id, again, "the same server and account")
        assertTrue(lastToken != first, "a new session, not the ended one")
        assertTrue(library.source(again)!!.config.endsWith(lastToken))
        library.scan(registry.resolve(again)!!)
        assertEquals(true, library.watchState(library.movies().single().item.id)?.completed)
        assertEquals(listOf(first), ShelfConnector.signedOut, "the new session is left alone")
    }

    @Test
    fun signingInAgainEndsTheSessionItReplacesAndNotTheNewOne() = runTest {
        val seal = MemorySeal()
        val connections = timed(seal)
        val id = connections.signedIn()
        val old = lastToken

        val (result, signOut) = connections.connecting(ConnectionKind.SERVER, ConnectionRequest("shelf://home", "alex", "secret"), replacing = id)
        assertIs<ConnectResult.Connected>(result)
        assertEquals(SignOut.ENDED, signOut.await())
        assertEquals(listOf(old), ShelfConnector.signedOut)
        assertTrue(seal.kept.getValue(id.value).endsWith(lastToken), "the new sign-in is what is kept")
    }

    @Test
    fun aFailedSignInEndsNothing() = runTest {
        val connections = timed()
        val id = connections.signedIn()
        val (result, signOut) = connections.connecting(ConnectionKind.SERVER, ConnectionRequest("shelf://home", "alex", "guess"), replacing = id)
        assertIs<ConnectResult.Failed>(result)
        assertEquals(SignOut.NONE, signOut.await())
        assertTrue(ShelfConnector.signedOut.isEmpty(), "the session that still works is kept")
        assertNotNull(library.source(id))
    }

    @Test
    fun removingOneServerLeavesAnotherSignedIn() = runTest {
        val connections = timed()
        val home = connections.signedIn("shelf://home")
        val homeToken = lastToken
        val cabin = connections.signedIn("shelf://cabin")
        assertEquals(SignOut.ENDED, connections.remove(home).await())
        assertEquals(listOf(homeToken), ShelfConnector.signedOut)
        assertNotNull(library.source(cabin))
        assertNotNull(registry.resolve(cabin))
    }
}
