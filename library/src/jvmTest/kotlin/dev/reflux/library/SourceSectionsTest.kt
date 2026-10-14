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
import dev.reflux.core.source.ConnectionRequest
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.core.source.SectionKind
import dev.reflux.core.source.SectionedSource
import dev.reflux.core.source.SourceCapability
import dev.reflux.core.source.SourceConnector
import dev.reflux.core.source.SourceDescriptor
import dev.reflux.core.source.SourceLocality
import dev.reflux.core.source.SourceSection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A server with two libraries, whose configuration is `token|excluded,ids`. */
private class Shelves(config: String) : CatalogSource, SectionedSource, AutoCloseable {
    val token = config.substringBefore('|')
    override val excludedSections: Set<String> = config.substringAfter('|', "").split(',').filter { it.isNotBlank() }.toSet()
    var closed = false
    override val descriptor = SourceDescriptor(StableIds.sourceId("shelves", "home"), "shelves", "Shelves", SourceLocality.LOCAL_NETWORK, setOf(SourceCapability.PROVIDES_CATALOG))
    override suspend fun availability() = Availability.AVAILABLE
    override suspend fun playbackTarget(path: String) = PlaybackTarget("shelves://$path")
    override suspend fun sections() = listOf(SourceSection("films", "Movies", SectionKind.MOVIES), SourceSection("kids", "Kids", SectionKind.MOVIES))

    private fun film(title: String) = CatalogEntry(ParsedMedia(ParsedKind.MOVIE, title, 2000), listOf(CatalogVersion(title.lowercase(), 1, 1, StreamInfo(Container.MATROSKA))))

    override fun catalog(): Flow<CatalogEntry> =
        (listOf(film("Heat")).takeIf { "films" !in excludedSections }.orEmpty() + listOf(film("Paddington")).takeIf { "kids" !in excludedSections }.orEmpty()).asFlow()

    override fun close() {
        closed = true
    }
}

private object ShelvesConnector : SourceConnector {
    override val type = "shelves"
    override val kind = ConnectionKind.SERVER
    override val product = "Shelves"
    override val addressExample = "shelves://home"
    override val anonymous = false
    var tokens = 0
    override fun accepts(address: String) = true
    override suspend fun connect(request: ConnectionRequest): Connection = "token-${++tokens}".let { Connection.Established(Shelves(it), it) }
    override fun describe(config: String) = ConnectionRequest("shelves://home", "alex")
    override fun withExcludedSections(config: String, excluded: Set<String>) = config.substringBefore('|') + "|" + excluded.sorted().joinToString(",")
}

/** Reverses the text: enough to show that what is stored is not what the adapter reads. */
private object MirrorSeal : ConfigSeal {
    override fun seal(id: dev.reflux.core.model.SourceId, config: String) = "sealed:" + config.reversed()
    override fun open(id: dev.reflux.core.model.SourceId, stored: String) = open(stored)
    fun open(stored: String) = if (stored.startsWith("sealed:")) stored.removePrefix("sealed:").reversed() else stored
    override fun sealed(stored: String) = stored.startsWith("sealed:")
}

class SourceSectionsTest {
    private val library = Library(testDatabase()) { 1_790_000_000_000L }
    private val registry = SourceRegistry(mapOf("shelves" to { record -> Shelves(MirrorSeal.open(record.config)) }))
    private val connections = SourceConnections(library, registry, listOf(ShelvesConnector), seal = MirrorSeal)

    private suspend fun connect() = (connections.connect(ConnectionKind.SERVER, ConnectionRequest("shelves://home", "alex", "pw")) as ConnectResult.Connected).sourceId

    private suspend fun scan(id: dev.reflux.core.model.SourceId) = library.scan(registry.resolve(id)!!)

    private fun titles() = library.movies().map { it.item.title }.sorted()

    @Test
    fun aLibraryLeftOutLeavesTheLibraryAndComesBackWithItsHistory() = runTest {
        val id = connect()
        scan(id)
        assertEquals(listOf("Heat", "Paddington"), titles())
        val paddington = library.movies().single { it.item.title == "Paddington" }
        library.stopped(paddington.item.id, 20 * 60_000L, 90 * 60_000L, library.versions(paddington.item.id).single().version)

        val choice = assertNotNull(connections.sections(id))
        assertEquals(setOf("films", "kids"), choice.sections.map { it.id }.toSet())
        assertTrue(connections.setExcludedSections(id, setOf("kids"), choice.sections))
        assertEquals(setOf("kids"), (registry.resolve(id) as SectionedSource).excludedSections, "the adapter now reads the new choice")
        scan(id)
        assertEquals(listOf("Heat"), titles(), "what was left out leaves the library")
        assertEquals(1, library.sources().size, "the connection stays")
        assertEquals(Availability.AVAILABLE, library.source(id)!!.availability, "a smaller choice is not a source going away")

        assertTrue(connections.setExcludedSections(id, emptySet(), choice.sections))
        scan(id)
        assertEquals(listOf("Heat", "Paddington"), titles())
        assertEquals(20 * 60_000L, library.watchState(library.movies().single { it.item.title == "Paddington" }.item.id)?.positionMs, "and comes back with where the user was")
    }

    @Test
    fun everythingCannotBeLeftOut() = runTest {
        val id = connect()
        val choice = connections.sections(id)!!
        assertFailsWith<IllegalArgumentException> { connections.setExcludedSections(id, setOf("films", "kids"), choice.sections) }
    }

    @Test
    fun signingInAgainKeepsTheChoiceAndDoesNotAddASecondSource() = runTest {
        val id = connect()
        connections.setExcludedSections(id, setOf("kids"), connections.sections(id)!!.sections)
        val again = connections.connect(ConnectionKind.SERVER, ConnectionRequest("shelves://home", "alex", "pw"), replacing = id) as ConnectResult.Connected
        assertEquals(id, again.sourceId)
        assertEquals(1, library.sources().size)
        val live = registry.resolve(id) as Shelves
        assertEquals(setOf("kids"), live.excludedSections)
        assertEquals("token-${ShelvesConnector.tokens}", live.token, "with the new sign-in")
    }

    @Test
    fun whatIsStoredIsSealedAndAdaptersOnlySeeItOpened() = runTest {
        val id = connect()
        val token = "token-${ShelvesConnector.tokens}"
        val stored = library.source(id)!!.config
        assertTrue(stored.startsWith("sealed:") && token !in stored)
        assertEquals("shelves://home", connections.describe(library.source(id)!!)?.request?.address)
        // A restart recreates the adapter from what was stored.
        val fresh = SourceRegistry(mapOf("shelves" to { record -> Shelves(MirrorSeal.open(record.config)) })).apply { load(library.sources()) }
        assertEquals(token, (fresh.resolve(id) as Shelves).token)
    }

    @Test
    fun whatWasStoredBeforeSealingExistedIsSealedAtStart() = runTest {
        val plain = SourceConnections(library, registry, listOf(ShelvesConnector))
        val id = (plain.connect(ConnectionKind.SERVER, ConnectionRequest("shelves://home", "alex", "pw")) as ConnectResult.Connected).sourceId
        assertFalse(library.source(id)!!.config.startsWith("sealed:"))
        connections.sealStored()
        val stored = library.source(id)!!.config
        assertTrue(stored.startsWith("sealed:"))
        assertEquals("shelves://home", connections.describe(library.source(id)!!)?.request?.address)
        connections.sealStored()
        assertEquals(stored, library.source(id)!!.config, "sealed once, not again")
    }

    @Test
    fun anAdapterThatIsReplacedOrRemovedIsClosed() = runTest {
        val id = connect()
        val first = registry.resolve(id) as Shelves
        connections.setExcludedSections(id, setOf("kids"), connections.sections(id)!!.sections)
        assertTrue(first.closed, "the adapter it replaced lets go of what it held")
        val second = registry.resolve(id) as Shelves
        assertFalse(second.closed)
        registry.unregister(id)
        assertTrue(second.closed)
    }
}
