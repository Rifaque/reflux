package dev.reflux.library

import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.Availability
import dev.reflux.core.model.StableIds
import dev.reflux.core.playback.MediaProber
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.source.ChangeNotifyingSource
import dev.reflux.core.source.FileEnumeratingSource
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.core.source.SourceCapability
import dev.reflux.core.source.SourceDescriptor
import dev.reflux.core.source.SourceFile
import dev.reflux.core.source.SourceLocality
import dev.reflux.core.metadata.RemoteArtwork
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** An in-memory folder-like source. */
private class MemorySource(name: String) : FileEnumeratingSource, ChangeNotifyingSource {
    val files = mutableListOf<SourceFile>()
    var online = true
    var scans = 0
    val signals = MutableSharedFlow<Unit>(extraBufferCapacity = 16)

    override val descriptor = SourceDescriptor(
        StableIds.sourceId("memory", name), "memory", name, SourceLocality.DEVICE,
        setOf(SourceCapability.ENUMERATE_FILES, SourceCapability.CHANGE_NOTIFICATIONS),
    )

    override suspend fun availability() = if (online) Availability.AVAILABLE else Availability.UNAVAILABLE
    override suspend fun playbackTarget(path: String) = PlaybackTarget("memory://$path")
    override fun files(): Flow<SourceFile> {
        scans++
        return files.toList().asFlow()
    }
    override fun changes(): Flow<Unit> = signals

    fun add(path: String) {
        files += SourceFile(path, 100, 1)
    }
}

class LibraryPipelineTest {
    private var clock = 1_790_000_000_000L
    private val library = Library(LibraryDatabase.inMemory()) { clock }
    private val source = MemorySource("Media")
    private val registry = SourceRegistry(emptyMap()).apply { register(source) }
    private val provider = FakeMetadataProvider()
    private val probed = mutableListOf<String>()
    private val prefetched = mutableListOf<ArtworkLocator>()
    private val pipeline = LibraryPipeline(
        library, registry,
        prober = MediaProber { target -> probed += target.uri; StreamInfo(durationMs = 1000) },
        metadata = provider,
        prefetchArtwork = { prefetched += it },
    )

    init {
        library.addSource(source, "memory")
    }

    @Test
    fun refreshRunsTheWholePipeline() = runTest {
        provider.movie("1", "Heat", 1995, artwork = arrayOf(RemoteArtwork(ArtworkKind.POSTER, "https://img/heat.jpg")))
        source.add("Heat (1995).mkv")
        val reports = pipeline.refresh()
        assertEquals(1, reports.single().added)
        assertEquals(listOf("memory://Heat (1995).mkv"), probed)
        assertEquals("About Heat", library.movies().single().metadata?.overview)
        assertEquals(listOf<ArtworkLocator>(ArtworkLocator.Remote("https://img/heat.jpg")), prefetched)
        assertEquals(PipelineActivity.Idle, pipeline.activity.value)
    }

    @Test
    fun changeSignalsAreDebouncedIntoOneScan() = runTest {
        val job = pipeline.watch(backgroundScope, debounceMs = 2_000, availabilityIntervalMs = 3_600_000)
        runCurrent()
        val scansAfterStart = source.scans
        source.add("Heat (1995).mkv")
        repeat(5) {
            source.signals.emit(Unit)
            advanceTimeBy(500)
        }
        advanceTimeBy(2_500)
        runCurrent()
        assertEquals(scansAfterStart + 1, source.scans)
        assertEquals(1, library.movies().size)
        job.cancel()
    }

    @Test
    fun reconnectedSourcesAreRescanned() = runTest {
        source.add("Heat (1995).mkv")
        pipeline.refresh()
        source.online = false
        library.refreshAvailability(source)
        source.add("Alien (1979).mkv")

        val job = pipeline.watch(backgroundScope, availabilityIntervalMs = 10_000)
        runCurrent()
        assertEquals(1, library.movies().size, "offline: nothing changes")
        source.online = true
        advanceTimeBy(10_001)
        runCurrent()
        assertEquals(2, library.movies().size)
        assertTrue(library.movies().all { it.availability == Availability.AVAILABLE })
        job.cancel()
    }

    @Test
    fun worksWithoutOptionalStages() = runTest {
        val minimal = LibraryPipeline(library, registry)
        source.add("Heat (1995).mkv")
        minimal.refresh()
        assertEquals("Heat", library.movies().single().item.title)
    }
}
