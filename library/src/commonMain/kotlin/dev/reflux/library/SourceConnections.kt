package dev.reflux.library

import dev.reflux.core.model.SourceId
import dev.reflux.core.source.Connection
import dev.reflux.core.source.ConnectionKind
import dev.reflux.core.source.ConnectionProblem
import dev.reflux.core.source.ConnectionRequest
import dev.reflux.core.source.SourceConnector
import kotlinx.coroutines.CancellationException

/** What came of connecting a server or a share. */
sealed interface ConnectResult {
    /** It is a source now. [updated] when it already was one and was only signed in again or changed. */
    data class Connected(val sourceId: SourceId, val name: String, val updated: Boolean) : ConnectResult

    data class Failed(val problem: ConnectionProblem) : ConnectResult
}

/** A source as its connection describes it: what kind of thing it is, and where (never with a password). */
data class SourceConnectionInfo(val kind: ConnectionKind, val product: String?, val request: ConnectionRequest?)

/** How many movies and shows a source holds. */
data class SourceContents(val movies: Int, val shows: Int)

fun Library.sourceContents(id: SourceId): SourceContents =
    queries.sourceContents(id.value).executeAsOne().let { SourceContents(it.movies.toInt(), it.shows.toInt()) }

/** The library's clock, for saying how long ago something happened. */
fun Library.currentTime(): Long = now()

/**
 * Adding sources that are reached by an address: servers and network shares. The platform supplies the
 * connectors it has; this decides which one an address is for, and turns a connection into a source the
 * library keeps and the registry can use. Nothing here knows any adapter.
 */
class SourceConnections(
    private val library: Library,
    private val registry: SourceRegistry,
    val connectors: List<SourceConnector>,
    private val report: (String, Throwable?) -> Unit = { _, _ -> },
) {
    /** The kinds of thing that can be connected here, in the order they are offered. */
    val kinds: List<ConnectionKind> get() = ConnectionKind.entries.filter { kind -> connectors.any { it.kind == kind } }

    fun connector(type: String): SourceConnector? = connectors.firstOrNull { it.type == type }

    /** The connector of [kind] that handles [address], or null when none does. */
    fun route(kind: ConnectionKind, address: String): SourceConnector? = connectors.firstOrNull { it.kind == kind && it.accepts(address) }

    /** Addresses that show what can be typed for [kind]. */
    fun examples(kind: ConnectionKind): List<String> = connectors.filter { it.kind == kind }.map { it.addressExample }

    /** Null for sources that are not connected by address (folders, downloads). */
    fun describe(record: SourceRecord): SourceConnectionInfo? =
        connector(record.type)?.let { SourceConnectionInfo(it.kind, it.product, runCatching { it.describe(record.config) }.getOrNull()) }

    /**
     * Connects to what [request] names and makes it a source. With [replacing], the connection replaces
     * that source's: signing in again, or a changed address. Watch history belongs to works and survives
     * either way. Scanning is the caller's to start.
     */
    suspend fun connect(kind: ConnectionKind, request: ConnectionRequest, replacing: SourceId? = null): ConnectResult {
        val previous = replacing?.let(library::source)
        val connector = previous?.let { connector(it.type) }?.takeIf { it.accepts(request.address) } ?: route(kind, request.address)
            ?: return ConnectResult.Failed(ConnectionProblem.INVALID_ADDRESS)
        val connection = try {
            connector.connect(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            report("Connecting a ${connector.type} source failed unexpectedly", e)
            return ConnectResult.Failed(ConnectionProblem.UNKNOWN)
        }
        val established = when (connection) {
            is Connection.Failed -> {
                report("Connecting a ${connector.type} source: ${connection.problem}" + connection.detail?.let { " ($it)" }.orEmpty(), null)
                return ConnectResult.Failed(connection.problem)
            }
            is Connection.Established -> connection
        }
        val source = established.source
        val id = source.descriptor.id
        val existing = library.source(id)
        // A source the user already knows keeps the name it has; only how to reach it changes.
        val name = existing?.displayName ?: previous?.takeIf { it.type == source.descriptor.type }?.displayName ?: source.descriptor.displayName
        if (existing != null) {
            library.queries.updateSourceConnection(name, source.descriptor.locality.name, established.config, id.value)
        } else {
            library.addSource(source, established.config)
            if (name != source.descriptor.displayName) library.queries.renameSource(name, id.value)
        }
        registry.register(source)
        if (replacing != null && replacing != id) {
            library.removeSource(replacing)
            registry.unregister(replacing)
        }
        return ConnectResult.Connected(id, name, updated = existing != null || previous != null)
    }
}
