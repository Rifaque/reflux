package dev.reflux.library

import dev.reflux.core.metadata.MetadataCandidate
import dev.reflux.core.metadata.MetadataProvider
import dev.reflux.core.metadata.MetadataUnavailableException
import dev.reflux.core.metadata.ProviderAttribution
import dev.reflux.core.metadata.ScoredCandidate
import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.Availability
import dev.reflux.core.model.MediaId
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
    metadata: MetadataProvider? = null,
    private val language: String = "en-US",
    private val prefetchArtwork: (suspend (List<ArtworkLocator>) -> Unit)? = null,
) {
    /** The provider in the build, behind its gate (coalescing, spacing, backing off); null without one. */
    private val provider: ProviderGate? = metadata?.let { it as? ProviderGate ?: ProviderGate(it) }

    /**
     * The provider while the viewer allows Reflux to look things up online ([Library.onlineMetadata]); null
     * otherwise, when what is cached and what sources say is all there is.
     */
    private val metadata: MetadataProvider? get() = provider?.takeIf { library.onlineMetadata }
    private val mutex = Mutex()
    private val mutableActivity = MutableStateFlow<PipelineActivity>(PipelineActivity.Idle)
    val activity: StateFlow<PipelineActivity> = mutableActivity.asStateFlow()

    private val mutableMetadataUnreachable = MutableStateFlow(false)

    /**
     * Whether the last attempt to fetch titles and artwork could not reach the provider. The library stays
     * complete without it (file-name identification); the next refresh picks up where this one stopped.
     */
    val metadataUnreachable: StateFlow<Boolean> = mutableMetadataUnreachable.asStateFlow()

    /** How the metadata provider in use asks to be credited (the About page); null without one. */
    val metadataAttribution: ProviderAttribution? get() = provider?.attribution

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
            var report: MetadataReport
            do {
                report = library.refreshMetadata(provider, language, limit = BATCH)
                mutableMetadataUnreachable.value = report.offline
            } while (!report.offline && report.matched + report.ambiguous + report.unmatched == BATCH)
            // A few of the oldest matches are read again each pass, so nothing kept is older than the
            // provider's terms allow, without ever re-reading the whole library at once.
            if (!report.offline && library.refreshStaleMetadata(provider, language, limit = STALE_BATCH) == null) {
                mutableMetadataUnreachable.value = true
            }
        }
        // Works that turned out to share a provider ID become one work with several versions.
        library.unifyWorks()
        prefetchArtwork?.let { prefetch ->
            mutableActivity.value = PipelineActivity.FetchingArtwork
            val kinds = setOf(ArtworkKind.POSTER, ArtworkKind.BACKDROP, ArtworkKind.LOGO)
            // Posters, backdrops, and logos of every movie and show; of episodes, only the stills of those the
            // viewer is about to watch (Continue Watching). The rest are fetched as they come into view.
            val stills = library.continueWatching().mapNotNull { it.artwork[ArtworkKind.THUMBNAIL] }
            prefetch((library.movies() + library.shows()).flatMap { entry -> entry.artwork.filterKeys { it in kinds }.values } + stills)
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

    /**
     * Whether works can be identified against a metadata provider here: the build supplied its credential, and
     * the viewer allows online lookups.
     */
    val canIdentify: Boolean get() = metadata != null

    /** Whether the build has a metadata provider at all, whatever the viewer chose. */
    val hasProvider: Boolean get() = provider != null

    /**
     * Reads [itemId]'s details and artwork again (a movie, a show, or a season: [Library.refreshDetails]), then
     * fetches its pictures. Only when the viewer asks; nothing happens without a provider or with lookups off.
     */
    suspend fun refreshDetails(itemId: MediaId): MetadataRefresh? {
        val provider = metadata ?: return null
        this.provider?.reset()
        val result = mutex.withLock { library.refreshDetails(itemId, provider, language) }
        mutableMetadataUnreachable.value = result == MetadataRefresh.UNREACHABLE
        if (result == MetadataRefresh.REFRESHED) prefetchArtwork?.let { prefetch -> library.entry(itemId)?.let { prefetch(it.artwork.values.toList()) } }
        return result
    }

    /**
     * What [itemId] could be, best first, for a title the user typed (and a year, if they gave one).
     * Throws [MetadataUnavailableException] when the provider cannot be reached; returns nothing without one.
     */
    suspend fun identifyCandidates(itemId: MediaId, title: String, year: Int?): List<ScoredCandidate> {
        val provider = metadata ?: return emptyList()
        return library.searchMetadata(itemId, provider, language, title, year)
    }

    /**
     * Makes [itemId] the work [candidate] is, from now on and across rescans, with its titles and artwork, and
     * returns the work's ID afterwards. Runs between scans, never during one.
     */
    suspend fun identify(itemId: MediaId, candidate: MetadataCandidate): MediaId {
        val provider = metadata ?: return itemId
        val identified = mutex.withLock { library.identifyAs(itemId, candidate, provider, language) }
        prefetchArtwork?.let { prefetch -> library.entry(identified)?.let { entry -> prefetch(entry.artwork.values.toList()) } }
        return identified
    }

    /** Gives [itemId] back to automatic identification ([Library.undoIdentification]); the work it is now, if any. */
    suspend fun undoIdentification(itemId: MediaId): MediaId? = mutex.withLock { library.undoIdentification(itemId) }

    private companion object {
        const val BATCH = 50
        const val STALE_BATCH = 10
    }
}
