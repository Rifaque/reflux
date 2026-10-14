package dev.reflux.library

import dev.reflux.core.model.SourceId
import dev.reflux.core.source.Connection
import dev.reflux.core.source.ConnectionKind
import dev.reflux.core.source.ConnectionProblem
import dev.reflux.core.source.ConnectionRequest
import dev.reflux.core.source.SectionedSource
import dev.reflux.core.source.SourceConnector
import dev.reflux.core.source.SourceSection
import kotlinx.coroutines.CancellationException

/** What came of connecting a server or a share. */
sealed interface ConnectResult {
    /** It is a source now. [updated] when it already was one and was only signed in again or changed. */
    data class Connected(val sourceId: SourceId, val name: String, val updated: Boolean) : ConnectResult

    data class Failed(val problem: ConnectionProblem) : ConnectResult
}

/** A source as its connection describes it: what kind of thing it is, and where (never with a password). */
data class SourceConnectionInfo(val kind: ConnectionKind, val product: String?, val request: ConnectionRequest?)

/**
 * Keeps what a connection needs (tokens, passwords) out of the library database, where the platform can:
 * on Android, encrypted with a key in the system's keystore; on a desktop, in the system's own credential
 * store, with only a reference left in the database. [open] also accepts what was stored before sealing
 * existed, and [ConfigSeal.sealStored] moves that into the seal. Adapters only ever see the opened form, and
 * nothing in the Media Core sees either.
 *
 * Failing to seal throws: a connection is then not kept at all, rather than kept in the open.
 */
interface ConfigSeal {
    /** Keeps [config], the connection of source [id], and returns what the database stores in its place. */
    fun seal(id: SourceId, config: String): String

    /** The connection [stored] stands for. Something that can no longer be opened opens as nothing. */
    fun open(id: SourceId, stored: String): String

    /** Whether [stored] is already sealed, as opposed to kept from before sealing existed. */
    fun sealed(stored: String): Boolean

    /** Lets go of whatever was kept for [id], when the source is removed. */
    fun forget(id: SourceId) {}

    /** Stored as it is; used where the platform has nothing better (see docs/RELEASE.md). */
    object None : ConfigSeal {
        override fun seal(id: SourceId, config: String) = config
        override fun open(id: SourceId, stored: String) = stored
        override fun sealed(stored: String) = true
    }
}

/** The parts of a source made of parts, and which of them are left out. */
data class SectionChoice(val sections: List<SourceSection>, val excluded: Set<String>) {
    /** What is left out and still exists; a part that went away no longer counts. */
    val excludedPresent: Set<String> get() = excluded.intersect(sections.map { it.id }.toSet())
}

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
    private val seal: ConfigSeal = ConfigSeal.None,
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
        connector(record.type)?.let { SourceConnectionInfo(it.kind, it.product, runCatching { it.describe(seal.open(record.id, record.config)) }.getOrNull()) }

    /**
     * Moves what was stored before sealing existed into the seal, so no secret stays in the open. Run once at
     * start. A connection the seal will not take is left as it was, working, and the problem reported: it is
     * never thrown away.
     */
    fun sealStored() {
        library.sources().filter { connector(it.type) != null && !seal.sealed(it.config) }.forEach { record ->
            val stored = try {
                seal.seal(record.id, record.config)
            } catch (e: Exception) {
                report("The connection of a ${record.type} source could not be moved to secure storage; it is kept as it was", e)
                return@forEach
            }
            library.queries.updateSourceConnection(record.displayName, record.locality.name, stored, record.id.value)
        }
    }

    /** What [record] connects with, opened. For the platform's source factories. */
    fun open(record: SourceRecord): String = seal.open(record.id, record.config)

    /** Lets go of what was kept for a source that is being removed. */
    fun forget(id: SourceId) {
        runCatching { seal.forget(id) }.onFailure { report("Secure storage did not let go of a removed source's connection", it) }
    }

    /** Whether [id] is made of parts the user can choose between (a server's libraries). */
    fun sectioned(id: SourceId): Boolean = registry.resolve(id) is SectionedSource

    /** How many parts of [id] are left out, as last chosen; read without asking the source. */
    fun excludedCount(id: SourceId): Int = (registry.resolve(id) as? SectionedSource)?.excludedSections?.size ?: 0

    /**
     * What [id] is made of, asked of the source now, and what is left out. Null when it is not made of parts.
     * Throws [dev.reflux.core.source.SourceUnavailableException] when it cannot be reached.
     */
    suspend fun sections(id: SourceId): SectionChoice? {
        val source = registry.resolve(id) as? SectionedSource ?: return null
        return SectionChoice(source.sections(), source.excludedSections)
    }

    /**
     * Leaves [excluded] out of [id] from now on. The connection stays; the next scan makes the library match,
     * so what was left out leaves it (its watch history stays with its works, as when any file goes away).
     * Leaving everything out is refused: removing the source is how that is said.
     */
    fun setExcludedSections(id: SourceId, excluded: Set<String>, all: List<SourceSection>): Boolean {
        require(all.any { it.id !in excluded }) { "at least one part of a source is included" }
        val record = library.source(id) ?: return false
        val connector = connector(record.type) ?: return false
        val config = connector.withExcludedSections(seal.open(id, record.config), excluded) ?: return false
        val stored = seal.seal(id, config)
        library.queries.updateSourceConnection(record.displayName, record.locality.name, stored, id.value)
        registry.reload(record.copy(config = stored))
        return true
    }

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
        // Signing in again keeps what the user chose to leave out.
        val kept = (registry.resolve(id) as? SectionedSource)?.excludedSections.orEmpty()
        val config = kept.takeIf { it.isNotEmpty() }?.let { connector.withExcludedSections(established.config, it) } ?: established.config
        // Kept securely or not at all: a connection is never written down in the open because the seal failed.
        val stored = try {
            seal.seal(id, config)
        } catch (e: Exception) {
            report("Secure storage refused the connection of a ${connector.type} source", e)
            (source as? AutoCloseable)?.let { runCatching { it.close() } }
            return ConnectResult.Failed(ConnectionProblem.UNKNOWN)
        }
        if (kept.isNotEmpty() && config != established.config) {
            registry.reload(SourceRecord(id, connector.type, name, source.descriptor.locality, stored, dev.reflux.core.model.Availability.UNKNOWN, null, null))
        } else {
            registry.register(source)
        }
        if (existing != null) {
            library.queries.updateSourceConnection(name, source.descriptor.locality.name, stored, id.value)
        } else {
            library.addSource(source, stored)
            if (name != source.descriptor.displayName) library.queries.renameSource(name, id.value)
        }
        if (replacing != null && replacing != id) {
            library.removeSource(replacing)
            registry.unregister(replacing)
            forget(replacing)
        }
        return ConnectResult.Connected(id, name, updated = existing != null || previous != null)
    }
}
