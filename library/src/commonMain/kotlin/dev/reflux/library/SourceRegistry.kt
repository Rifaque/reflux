package dev.reflux.library

import dev.reflux.core.model.SourceId
import dev.reflux.core.source.MediaSource
import kotlin.concurrent.Volatile

/**
 * Live source adapters, recreated from persisted [SourceRecord]s by per-type factories supplied by the
 * platform shell (e.g. `local` → a folder adapter on desktop, a Storage Access Framework adapter on Android).
 */
class SourceRegistry(private val factories: Map<String, (SourceRecord) -> MediaSource?>) {
    @Volatile
    private var sources: Map<SourceId, MediaSource> = emptyMap()

    /** Recreates adapters for [records]; records of unknown types are skipped (the library keeps their data). */
    fun load(records: List<SourceRecord>) {
        sources = records.mapNotNull { record -> factories[record.type]?.invoke(record)?.let { record.id to it } }.toMap()
    }

    fun register(source: MediaSource) {
        sources = sources + (source.descriptor.id to source)
    }

    fun unregister(id: SourceId) {
        sources = sources - id
    }

    fun resolve(id: SourceId): MediaSource? = sources[id]

    fun all(): List<MediaSource> = sources.values.toList()
}
