package dev.reflux.library

import dev.reflux.core.metadata.MetadataProvider
import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.Availability
import dev.reflux.core.model.SourceId
import dev.reflux.core.playback.MediaProber
import dev.reflux.core.source.CatalogSource
import dev.reflux.core.source.ChangeNotifyingSource
import dev.reflux.core.source.FileEnumeratingSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the pipeline is doing, for a quiet progress indicator in the UI. */
sealed interface PipelineActivity {
    data object Idle : PipelineActivity
    data class Scanning(val sourceName: String) : PipelineActivity
    data object Probing : PipelineActivity
    data object FetchingMetadata : PipelineActivity
    data object FetchingArtwork : PipelineActivity
}

/**
 * Keeps the library current with no user involvement: scan → probe → metadata → artwork, triggered on demand,
 * by source change notifications, and when an offline source comes back.
 *
 * Every stage is optional and degrades gracefully: without a prober, hints are kept; without a metadata
 * provider or network, the library is still fully browsable and playable.
 */
class LibraryPipeline(
    private val library: Library,
    private val registry: SourceRegistry,
    private val prober: MediaProber? = null,
    private val metadata: MetadataProvider? = null,
    private val language: String = "en-US",
    private val prefetchArtwork: (suspend (List<ArtworkLocator>) -> Unit)? = null,
) {
    private val mutex = Mutex()
    private val mutableActivity = MutableStateFlow<PipelineActivity>(PipelineActivity.Idle)
    val activity: StateFlow<PipelineActivity> = mutableActivity.asStateFlow()

    /** Scans [sourceId] (or every source) and enriches the library. Concurrent calls run one after another. */
    suspend fun refresh(sourceId: SourceId? = null): List<ScanReport> = mutex.withLock {
        try {
            val ids = sourceId?.let(::listOf) ?: library.sources().map { it.id }
            val reports = ids.mapNotNull { id ->
                val source = registry.resolve(id)?.takeIf { it is FileEnumeratingSource || it is CatalogSource } ?: return@mapNotNull null
                mutableActivity.value = PipelineActivity.Scanning(source.descriptor.displayName)
                library.scan(source)
            }
            enrich()
            reports
        } finally {
            mutableActivity.value = PipelineActivity.Idle
        }
    }

    private suspend fun enrich() {
        prober?.let { prober ->
            mutableActivity.value = PipelineActivity.Probing
            while (library.probePending(registry::resolve, prober, limit = BATCH) == BATCH) Unit
        }
        metadata?.let { provider ->
            mutableActivity.value = PipelineActivity.FetchingMetadata
            do {
                val report = library.refreshMetadata(provider, language, limit = BATCH)
            } while (!report.offline && report.matched + report.ambiguous + report.unmatched == BATCH)
        }
        // Works that turned out to share a provider ID become one work with several versions.
        library.unifyWorks()
        prefetchArtwork?.let { prefetch ->
            mutableActivity.value = PipelineActivity.FetchingArtwork
            val kinds = setOf(ArtworkKind.POSTER, ArtworkKind.BACKDROP, ArtworkKind.LOGO)
            prefetch((library.movies() + library.shows()).flatMap { entry -> entry.artwork.filterKeys { it in kinds }.values })
        }
    }

    /**
     * Rescans sources when they report changes (debounced so a copy in progress triggers one scan), and
     * re-checks availability periodically so a reconnected drive or share is picked up automatically.
     */
    @OptIn(FlowPreview::class)
    fun watch(scope: CoroutineScope, debounceMs: Long = 2_000, availabilityIntervalMs: Long = 30_000): Job = scope.launch {
        for (source in registry.all().filterIsInstance<ChangeNotifyingSource>()) {
            launch {
                try {
                    source.changes().debounce(debounceMs).collect { refresh(source.descriptor.id) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Watching failed (e.g. watch limits); availability checks below still catch reconnects.
                }
            }
        }
        launch {
            while (isActive) {
                for (source in registry.all()) {
                    val before = library.source(source.descriptor.id)?.availability
                    val now = library.refreshAvailability(source)
                    if (now == Availability.AVAILABLE && before != Availability.AVAILABLE) refresh(source.descriptor.id)
                }
                delay(availabilityIntervalMs)
            }
        }
    }

    private companion object {
        const val BATCH = 50
    }
}
