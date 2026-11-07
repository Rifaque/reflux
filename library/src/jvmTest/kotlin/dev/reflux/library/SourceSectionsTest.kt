package dev.reflux.library

import dev.reflux.core.identify.ParsedKind
import dev.reflux.core.identify.ParsedMedia
import dev.reflux.core.model.Availability
import dev.reflux.core.model.SourceId
import dev.reflux.core.model.StableIds
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoStream
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
import dev.reflux.core.source.SourceSignInRequiredException
import dev.reflux.core.source.SourceUnavailableException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** What the shelves server holds, shared by every adapter made for it (a restart makes a new one). */
private object ShelvesServer {
    var sections = listOf(SourceSection("films", "Movies", SectionKind.MOVIES), SourceSection("kids", "Kids", SectionKind.MOVIES), SourceSection("tv", "TV", SectionKind.SHOWS))
    var refusing = false
    var away = false

    fun film(title: String, section: String, height: Int = 1080) = CatalogEntry(
        ParsedMedia(ParsedKind.MOVIE, title, 2000),
        listOf(CatalogVersion("$section/${title.lowercase()}", 1, 1, StreamInfo(Container.MATROSKA, VideoStream(VideoCodec.H264, height = height)))),
        section = section,
    )

    fun episode(show: String, number: Int) = CatalogEntry(
        ParsedMedia(ParsedKind.EPISODE, show, 2008, season = 1, episode = number),
        listOf(CatalogVersion("tv/${show.lowercase()}/$number", 1, 1, StreamInfo(Container.MATROSKA))),
        section = "tv",
    )

    var entries = listOf(film("Heat", "films"), film("Paddington", "kids"), episode("Bluey", 1), episode("Bluey", 2))
}

/** A server with libraries, whose configuration is `token|excluded,ids` (libraries left out before M3). */
private class Shelves(config: String) : CatalogSource, SectionedSource, AutoCloseable {
    val token = config.substringBefore('|')
    var closed = false
    override val descriptor = SourceDescriptor(StableIds.sourceId("shelves", "home"), "shelves", "Shelves", SourceLocality.LOCAL_NETWORK, setOf(SourceCapability.PROVIDES_CATALOG))
    override suspend fun availability() = if (ShelvesServer.away) Availability.UNAVAILABLE else Availability.AVAILABLE
    override suspend fun playbackTarget(path: String) = PlaybackTarget("shelves://$path")
    override suspend fun sections() = check().let { ShelvesServer.sections }

    override fun catalog(): Flow<CatalogEntry> = flow {
        check()
        ShelvesServer.entries.filter { entry -> ShelvesServer.sections.any { it.id == entry.section } }.forEach { emit(it) }
    }

    private fun check() {
        if (ShelvesServer.away) throw SourceUnavailableException(descriptor.id)
        if (ShelvesServer.refusing) throw SourceSignInRequiredException(descriptor.id)
    }

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
    override fun excludedSections(config: String) = config.substringAfter('|', "").split(',').filter { it.isNotBlank() }.toSet()
    override fun withoutExcludedSections(config: String) = config.takeIf { '|' in it }?.substringBefore('|')
}

/** A share with no libraries: the source's own switch is all there is. */
private class Attic(name: String, private val entries: List<CatalogEntry>) : CatalogSource {
    override val descriptor = SourceDescriptor(StableIds.sourceId("share", name), "share", name, SourceLocality.LOCAL_NETWORK, setOf(SourceCapability.PROVIDES_CATALOG))
    override suspend fun availability() = Availability.AVAILABLE
    override suspend fun playbackTarget(path: String) = PlaybackTarget("share://$path")
    override fun catalog(): Flow<CatalogEntry> = flow { entries.forEach { emit(it.copy(section = null)) } }
}

/** Reverses the text: enough to show that what is stored is not what the adapter reads. */
private object MirrorSeal : ConfigSeal {
    override fun seal(id: SourceId, config: String) = "sealed:" + config.reversed()
    override fun open(id: SourceId, stored: String) = open(stored)
    fun open(stored: String) = if (stored.startsWith("sealed:")) stored.removePrefix("sealed:").reversed() else stored
    override fun sealed(stored: String) = stored.startsWith("sealed:")
}

/** Sources and their libraries in the unified library: inclusion, browsing a source, and their independence (docs/NAVIGATION.md). */
class SourceSectionsTest {
    private val library = Library(testDatabase()) { 1_790_000_000_000L }
    private val registry = SourceRegistry(mapOf("shelves" to { record -> Shelves(MirrorSeal.open(record.config)) }))
    private val connections = SourceConnections(library, registry, listOf(ShelvesConnector), seal = MirrorSeal)

    init {
        ShelvesServer.sections = listOf(SourceSection("films", "Movies", SectionKind.MOVIES), SourceSection("kids", "Kids", SectionKind.MOVIES), SourceSection("tv", "TV", SectionKind.SHOWS))
        ShelvesServer.entries = listOf(ShelvesServer.film("Heat", "films"), ShelvesServer.film("Paddington", "kids"), ShelvesServer.episode("Bluey", 1), ShelvesServer.episode("Bluey", 2))
        ShelvesServer.refusing = false
        ShelvesServer.away = false
    }

    private suspend fun connect() = (connections.connect(ConnectionKind.SERVER, ConnectionRequest("shelves://home", "alex", "pw")) as ConnectResult.Connected).sourceId

    private suspend fun scan(id: SourceId) = library.scan(registry.resolve(id)!!)

    private fun titles(scope: LibraryScope = LibraryScope.Unified) = (library.movies(scope) + library.shows(scope)).map { it.item.title }.sorted()

    @Test
    fun everyLibraryIsReadAndRememberedSoItCanBeShownWhileTheServerIsAway() = runTest {
        val id = connect()
        scan(id)
        assertEquals(listOf("Bluey", "Heat", "Paddington"), titles())
        val inclusion = assertNotNull(library.inclusion(id))
        assertTrue(inclusion.included)
        assertEquals(listOf("films", "kids", "tv"), inclusion.libraries.map { it.id })
        assertEquals(listOf("Movies", "Kids", "TV"), inclusion.libraries.map { it.name })
        assertTrue(inclusion.libraries.all { it.included && it.effective })
        assertEquals(SourceContents(1, 0), inclusion.libraries.first { it.id == "kids" }.contents)
        assertEquals(SourceContents(0, 1), inclusion.libraries.first { it.id == "tv" }.contents)
        ShelvesServer.away = true
        scan(id)
        assertEquals(3, library.inclusion(id)!!.libraries.size, "a server that is away keeps its libraries as last read")
    }

    @Test
    fun aLibraryOffLeavesTheUnifiedLibraryAndStaysBrowsableWithItsHistory() = runTest {
        val id = connect()
        scan(id)
        val paddington = library.movies().single { it.item.title == "Paddington" }
        library.stopped(paddington.item.id, 20 * 60_000L, 90 * 60_000L, library.versions(paddington.item.id).single().version)

        library.setLibraryIncluded(id, "kids", false)
        assertEquals(listOf("Bluey", "Heat"), titles(), "what is left out leaves the unified library at once, without a scan")
        assertEquals(listOf("Bluey", "Heat", "Paddington"), titles(LibraryScope.OfSource(id)), "and is still on the source's own page")
        assertEquals(listOf("Paddington"), titles(LibraryScope.OfSource(id, "kids")), "and on its library's")
        assertTrue(library.continueWatching().none { it.item.title == "Paddington" }, "Continue Watching is part of the unified library")
        assertTrue(library.search("Paddington").isEmpty(), "and so is Search")
        assertEquals(1, library.versions(paddington.item.id, LibraryScope.OfSource(id)).size, "its version plays from the source's page")
        assertTrue(library.versions(paddington.item.id).isEmpty())
        assertEquals(1, library.sources().size, "the connection stays")
        assertEquals(Availability.AVAILABLE, library.source(id)!!.availability, "leaving a library out is not a source going away")

        scan(id)
        assertEquals(listOf("Bluey", "Heat"), titles(), "a scan keeps the choice")
        library.setLibraryIncluded(id, "kids", true)
        assertEquals(listOf("Bluey", "Heat", "Paddington"), titles())
        assertEquals(20 * 60_000L, library.watchState(paddington.item.id)?.positionMs, "and comes back with where the viewer was")
    }

    @Test
    fun theSourceSwitchExcludesEveryLibraryAndKeepsEachLibrarysOwnChoice() = runTest {
        val id = connect()
        scan(id)
        library.setLibraryIncluded(id, "kids", false)
        library.setSourceIncluded(id, false)
        assertEquals(emptyList(), titles(), "a source off contributes nothing")
        val off = library.inclusion(id)!!
        assertFalse(off.included)
        assertFalse(off.contributes)
        assertEquals(mapOf("films" to true, "kids" to false, "tv" to true), off.libraries.associate { it.id to it.included }, "each library keeps its switch")
        assertTrue(off.libraries.none { it.effective })
        assertEquals(listOf("Bluey", "Heat", "Paddington"), titles(LibraryScope.OfSource(id)), "an excluded source is browsed as it is")
        assertEquals(Availability.AVAILABLE, library.source(id)!!.availability)
        assertNotNull(registry.resolve(id), "nothing is disconnected")

        library.setSourceIncluded(id, true)
        assertEquals(listOf("Bluey", "Heat"), titles(), "on again restores exactly what was chosen")
    }

    @Test
    fun aSourceOnWithEveryLibraryOffSaysSoAndContributesNothing() = runTest {
        val id = connect()
        scan(id)
        listOf("films", "kids", "tv").forEach { library.setLibraryIncluded(id, it, false) }
        val inclusion = library.inclusion(id)!!
        assertTrue(inclusion.included)
        assertTrue(inclusion.noLibraryIncluded)
        assertFalse(inclusion.contributes)
        assertEquals(emptyList(), titles())
    }

    @Test
    fun aNewLibraryIsIncludedAndOneThatGoesKeepsItsChoiceForWhenItComesBack() = runTest {
        val id = connect()
        scan(id)
        library.setLibraryIncluded(id, "kids", false)
        ShelvesServer.sections = ShelvesServer.sections.filter { it.id != "kids" } + SourceSection("docs", "Documentaries", SectionKind.MOVIES)
        ShelvesServer.entries = ShelvesServer.entries + ShelvesServer.film("Free Solo", "docs")
        scan(id)
        assertEquals(listOf("films", "tv", "docs"), library.inclusion(id)!!.libraries.map { it.id })
        assertEquals(listOf("Bluey", "Free Solo", "Heat"), titles(), "a library found later is included")
        assertEquals(listOf("Bluey", "Free Solo", "Heat"), titles(LibraryScope.OfSource(id)), "a library that went away leaves with its works")

        ShelvesServer.sections = ShelvesServer.sections + SourceSection("kids", "Kids", SectionKind.MOVIES)
        scan(id)
        assertFalse(library.inclusion(id)!!.libraries.first { it.id == "kids" }.included, "back with the choice that was made")
        assertEquals(listOf("Bluey", "Free Solo", "Heat"), titles())
    }

    @Test
    fun theSameWorkOnTwoIncludedSourcesIsOneWorkAndEachSourceShowsItsOwnCopy() = runTest {
        val id = connect()
        scan(id)
        val share = Attic("Attic", listOf(ShelvesServer.film("Heat", "films", height = 2160)))
        library.addSource(share, "attic")
        library.scan(share)
        assertEquals(1, library.movies().count { it.item.title == "Heat" }, "one work, not two entries")
        val heat = library.movies().single { it.item.title == "Heat" }.item.id
        assertEquals(2, library.versions(heat).size)
        assertEquals(listOf(id), library.versions(heat, LibraryScope.OfSource(id)).map { it.version.location.sourceId }, "the server's page has the server's copy")
        val device = dev.reflux.core.playback.DeviceCapabilities(
            videoDecoders = listOf(dev.reflux.core.playback.VideoDecoderSupport(VideoCodec.H264, 3840, 2160, 8, hardware = true)),
            audioDecoders = dev.reflux.core.playback.AudioCodec.entries.toSet(),
            display = dev.reflux.core.playback.DisplayCapabilities(3840, 2160),
            containers = Container.entries.toSet(),
            subtitleFormats = dev.reflux.core.playback.SubtitleFormat.entries.toSet(),
        )
        assertEquals(share.descriptor.id, library.selectVersion(heat, device).best?.version?.location?.sourceId, "unified Best Version takes the better copy")
        assertEquals(id, library.selectVersion(heat, device, scope = LibraryScope.OfSource(id)).best?.version?.location?.sourceId, "the source's page plays its own")

        library.setSourceIncluded(share.descriptor.id, false)
        assertEquals(listOf(id), library.versions(heat).map { it.version.location.sourceId }, "an excluded source's copy leaves the unified work")
        assertEquals(1, library.movies().count { it.item.title == "Heat" })
    }

    @Test
    fun aShowOnASourcePageHasOnlyThatSourcesEpisodes() = runTest {
        val id = connect()
        scan(id)
        val share = Attic("Attic", listOf(ShelvesServer.episode("Bluey", 3)))
        library.addSource(share, "attic")
        library.scan(share)
        val bluey = library.shows().single().item.id
        assertEquals(listOf(1, 2, 3), library.showDetail(bluey)!!.seasons.single().episodes.map { (it.item as dev.reflux.core.model.Episode).episodeNumber })
        assertEquals(listOf(1, 2), library.showDetail(bluey, LibraryScope.OfSource(id))!!.seasons.single().episodes.map { (it.item as dev.reflux.core.model.Episode).episodeNumber })
        assertEquals(listOf(3), library.showDetail(bluey, LibraryScope.OfSource(share.descriptor.id))!!.seasons.single().episodes.map { (it.item as dev.reflux.core.model.Episode).episodeNumber })
        library.setLibraryIncluded(id, "tv", false)
        assertEquals(listOf(3), library.showDetail(bluey)!!.seasons.single().episodes.map { (it.item as dev.reflux.core.model.Episode).episodeNumber }, "the unified show is made of what is included")
    }

    @Test
    fun availabilityAndSigningInChangeNothingAboutInclusion() = runTest {
        val id = connect()
        scan(id)
        library.setLibraryIncluded(id, "kids", false)
        ShelvesServer.away = true
        scan(id)
        assertEquals(Availability.UNAVAILABLE, library.source(id)!!.availability)
        assertTrue(library.source(id)!!.included, "a source that is away stays included")
        assertEquals(listOf("Bluey", "Heat"), titles(), "and its works stay in the library")
        ShelvesServer.away = false
        ShelvesServer.refusing = true
        scan(id)
        val refused = library.source(id)!!
        assertTrue(refused.signInRequired)
        assertEquals(Availability.UNAVAILABLE, refused.availability, "nothing can be read from it until then")
        assertTrue(refused.included)
        assertFalse(library.inclusion(id)!!.libraries.first { it.id == "kids" }.included)
        assertEquals(listOf("Bluey", "Heat"), titles())
        ShelvesServer.refusing = false
        scan(id)
        assertFalse(library.source(id)!!.signInRequired, "a scan it accepts clears it")
    }

    @Test
    fun removingForgetsInclusionAndAddingAgainStartsIncludedWithItsHistory() = runTest {
        val id = connect()
        scan(id)
        val heat = library.movies().single { it.item.title == "Heat" }.item.id
        library.setWatched(heat, true)
        library.setSourceIncluded(id, false)
        library.setLibraryIncluded(id, "kids", false)
        connections.remove(id).await()
        assertTrue(library.sources().isEmpty())
        val again = connect()
        assertEquals(id, again, "the same source")
        scan(again)
        assertTrue(library.inclusion(again)!!.included)
        assertTrue(library.inclusion(again)!!.libraries.all { it.included })
        assertEquals(true, library.watchState(heat)?.completed, "what was watched is remembered")
    }

    @Test
    fun signingInAgainKeepsTheChoicesAndDoesNotAddASecondSource() = runTest {
        val id = connect()
        scan(id)
        library.setLibraryIncluded(id, "kids", false)
        val again = connections.connect(ConnectionKind.SERVER, ConnectionRequest("shelves://home", "alex", "pw"), replacing = id) as ConnectResult.Connected
        assertEquals(id, again.sourceId)
        assertEquals(1, library.sources().size)
        assertFalse(library.inclusion(id)!!.libraries.first { it.id == "kids" }.included)
        assertEquals("token-${ShelvesConnector.tokens}", (registry.resolve(id) as Shelves).token, "with the new sign-in")
    }

    @Test
    fun librariesLeftOutBeforeBecomeExcludedLibrariesAndAreReadAgain() = runTest {
        val plain = SourceConnections(library, registry, listOf(ShelvesConnector), seal = MirrorSeal)
        val id = connect()
        // As an earlier version stored it: the connection itself leaving "kids" unread.
        val record = library.source(id)!!
        library.queries.updateSourceConnection(record.displayName, record.locality.name, MirrorSeal.seal(id, "token-old|kids"), id.value)
        registry.load(library.sources())
        plain.migrateExcludedSections()
        assertEquals("token-old", (registry.resolve(id) as Shelves).token, "the connection no longer leaves anything out")
        assertEquals("token-old", MirrorSeal.open(library.source(id)!!.config), "and is stored that way, sealed")
        scan(id)
        assertFalse(library.inclusion(id)!!.libraries.first { it.id == "kids" }.included, "the viewer's choice holds")
        assertEquals(listOf("Bluey", "Heat"), titles())
        assertEquals(listOf("Bluey", "Heat", "Paddington"), titles(LibraryScope.OfSource(id)), "and the library can be browsed")
        plain.migrateExcludedSections()
        assertFalse(library.inclusion(id)!!.libraries.first { it.id == "kids" }.included, "run again, nothing changes")
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
        connections.connect(ConnectionKind.SERVER, ConnectionRequest("shelves://home", "alex", "pw"), replacing = id)
        assertTrue(first.closed, "the adapter it replaced lets go of what it held")
        val second = registry.resolve(id) as Shelves
        assertFalse(second.closed)
        registry.unregister(id)
        assertTrue(second.closed)
    }

    @Test
    fun aServerProductIsChosenByTheViewerWhenThereIsMoreThanOne() = runTest {
        val other = object : SourceConnector by ShelvesConnector {
            override val type = "other"
            override val product = "Other"
            override suspend fun connect(request: ConnectionRequest): Connection = Connection.Failed(dev.reflux.core.source.ConnectionProblem.NOT_FOUND)
        }
        val both = SourceConnections(library, registry, listOf(other, ShelvesConnector), seal = MirrorSeal)
        assertEquals(listOf("Other", "Shelves"), both.products(ConnectionKind.SERVER).map { it.product })
        assertTrue(both.connect(ConnectionKind.SERVER, ConnectionRequest("shelves://home", "alex", "pw")) is ConnectResult.Failed, "without a choice, the first that takes the address")
        assertTrue(both.connect(ConnectionKind.SERVER, ConnectionRequest("shelves://home", "alex", "pw"), type = "shelves") is ConnectResult.Connected)
    }
}
