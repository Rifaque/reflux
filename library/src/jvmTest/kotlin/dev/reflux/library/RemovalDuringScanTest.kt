package dev.reflux.library

import dev.reflux.core.identify.ParsedKind
import dev.reflux.core.identify.ParsedMedia
import dev.reflux.core.model.Availability
import dev.reflux.core.model.StableIds
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.source.CatalogEntry
import dev.reflux.core.source.CatalogSource
import dev.reflux.core.source.CatalogVersion
import dev.reflux.core.source.FileEnumeratingSource
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.core.source.SourceCapability
import dev.reflux.core.source.SourceDescriptor
import dev.reflux.core.source.SourceFile
import dev.reflux.core.source.SourceLocality
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Reading a source takes a while; the user may remove it meanwhile. What the scan read then goes nowhere. */
class RemovalDuringScanTest {
    private val library = Library(testDatabase()) { 1_790_000_000_000L }
    private val reading = CompletableDeferred<Unit>()
    private val release = CompletableDeferred<Unit>()

    private val server = object : CatalogSource {
        override val descriptor = SourceDescriptor(
            StableIds.sourceId("server", "slow"), "server", "Slow Server", SourceLocality.LOCAL_NETWORK, setOf(SourceCapability.PROVIDES_CATALOG),
        )
        override suspend fun availability() = Availability.AVAILABLE
        override suspend fun playbackTarget(path: String) = PlaybackTarget("https://slow.example/$path")
        override fun catalog(): Flow<CatalogEntry> = flow {
            reading.complete(Unit)
            release.await()
            emit(CatalogEntry(ParsedMedia(ParsedKind.MOVIE, "Heat", 1995), listOf(CatalogVersion("items/heat", 1, 1, StreamInfo()))))
        }
    }

    private val share = object : FileEnumeratingSource {
        override val descriptor = SourceDescriptor(
            StableIds.sourceId("share", "slow"), "share", "Slow Share", SourceLocality.LOCAL_NETWORK, setOf(SourceCapability.ENUMERATE_FILES),
        )
        override suspend fun availability() = Availability.AVAILABLE
        override suspend fun playbackTarget(path: String) = PlaybackTarget("smb://slow/$path")
        override fun files(): Flow<SourceFile> = flow {
            reading.complete(Unit)
            release.await()
            emit(SourceFile("Arrival (2016)/Arrival (2016).mkv", 1, 1))
        }
    }

    @Test
    fun aServerRemovedWhileItIsReadLeavesNoWorksBehind() = runTest {
        library.addSource(server, "https://slow.example")
        val scan = async { library.scan(server) }
        reading.await()
        library.removeSource(server.descriptor.id)
        release.complete(Unit)
        assertEquals(ScanReport.Status.REMOVED, scan.await().status)
        assertTrue(library.movies().isEmpty(), "not a work with nothing to play that no scan would ever remove")
        assertTrue(library.sources().none { it.id == server.descriptor.id })
    }

    @Test
    fun aShareRemovedWhileItIsReadLeavesNoWorksBehind() = runTest {
        library.addSource(share, "smb://slow/media")
        val scan = async { library.scan(share) }
        reading.await()
        library.removeSource(share.descriptor.id)
        release.complete(Unit)
        assertEquals(ScanReport.Status.REMOVED, scan.await().status)
        assertTrue(library.movies().isEmpty())
    }
}
