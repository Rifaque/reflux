package dev.reflux.library

import dev.reflux.core.model.SourceId
import dev.reflux.core.source.MediaSource
import kotlin.concurrent.Volatile

/**
 * Live source adapters, recreated from persisted [SourceRecord]s by per-type factories supplied by the
 * platform shell (e.g. `local` → a folder adapter on desktop, a Storage Access Framework adapter on Android).
 *
 * An adapter that is replaced or removed is closed if it holds anything open (a share's connection).
 */
class SourceRegistry(private val factories: Map<String, (SourceRecord) -> MediaSource?>) {
    @Volatile
    private var sources: Map<SourceId, MediaSource> = emptyMap()

    /** Recreates adapters for [records]; records of unknown types are skipped (the library keeps their data). */
    fun load(records: List<SourceRecord>) {
        val previous = sources
        sources = records.mapNotNull { record -> factories[record.type]?.invoke(record)?.let { record.id to it } }.toMap()
        previous.values.filter { old -> sources.values.none { it === old } }.forEach(::close)
    }

    /** Recreates the adapter for [record], after its configuration changed. */
    fun reload(record: SourceRecord) {
        factories[record.type]?.invoke(record)?.let(::register)
    }

    fun register(source: MediaSource) {
        val previous = sources[source.descriptor.id]
        sources = sources + (source.descriptor.id to source)
        if (previous != null && previous !== source) close(previous)
    }

    fun unregister(id: SourceId) {
        val previous = sources[id]
        sources = sources - id
        previous?.let(::close)
    }

    fun resolve(id: SourceId): MediaSource? = sources[id]

    fun all(): List<MediaSource> = sources.values.toList()

    private fun close(source: MediaSource) {
        (source as? AutoCloseable)?.let { runCatching { it.close() } }
    }
}
