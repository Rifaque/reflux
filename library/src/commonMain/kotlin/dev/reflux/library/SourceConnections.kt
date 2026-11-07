package dev.reflux.library

import dev.reflux.core.model.SourceId
import dev.reflux.core.source.Connection
import dev.reflux.core.source.ConnectionKind
import dev.reflux.core.source.ConnectionProblem
import dev.reflux.core.source.ConnectionRequest
import dev.reflux.core.source.SectionedSource
import dev.reflux.core.source.SourceConnector

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.CoroutineContext

/** What came of connecting a server or a share. */
sealed interface ConnectResult {
    /** It is a source now. [updated] when it already was one and was only signed in again or changed. */
    data class Connected(val sourceId: SourceId, val name: String, val updated: Boolean) : ConnectResult

    data class Failed(val problem: ConnectionProblem) : ConnectResult
}

/** What came of asking the other side to end a connection that was removed or replaced. */
enum class SignOut {
    /** The other side ended it, or already no longer accepted it. */
    ENDED,

    /**
     * Not confirmed: the other side was not reached in time, refused, or answered something unexpected. The
     * connection is forgotten here all the same; the other side may go on listing this device until it
     * expires the session itself.
     */
    UNCONFIRMED,

    /** There was nothing to end: a folder, or a share, which keeps no session. */
    NONE,
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
    /** Where signing out runs: on its own, so that nothing waits for a server that does not answer. */
    signOutContext: CoroutineContext = Dispatchers.Default,
    /** How long a sign-out is given before it is left unconfirmed. */
    private val signOutWithinMs: Long = SIGN_OUT_WITHIN_MS,
) {
    private val signOuts = CoroutineScope(SupervisorJob() + signOutContext)

    /** The kinds of thing that can be connected here, in the order they are offered. */
    val kinds: List<ConnectionKind> get() = ConnectionKind.entries.filter { kind -> connectors.any { it.kind == kind } }

    fun connector(type: String): SourceConnector? = connectors.firstOrNull { it.type == type }

    /** The connector of [kind] that handles [address], or null when none does. */
    fun route(kind: ConnectionKind, address: String): SourceConnector? = connectors.firstOrNull { it.kind == kind && it.accepts(address) }

    /** The products of [kind] that can be connected here, each its own choice ("Jellyfin", "Plex"), in order. */
    fun products(kind: ConnectionKind): List<SourceConnector> = connectors.filter { it.kind == kind && it.product != null }

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

    /**
     * Moves libraries a connection used to leave unread into the library's inclusion (docs/NAVIGATION.md): they
     * become excluded libraries, so the viewer's choice holds, and the connection reads every library again, so
     * each can be browsed. Run once at start, after [sealStored]; a connection that cannot be rewritten is left
     * working as it was and the problem reported.
     */
    fun migrateExcludedSections() {
        for (record in library.sources()) {
            val connector = connector(record.type) ?: continue
            val config = runCatching { seal.open(record.id, record.config) }.getOrNull() ?: continue
            val excluded = connector.excludedSections(config).takeIf { it.isNotEmpty() } ?: continue
            val rewritten = connector.withoutExcludedSections(config) ?: continue
            val stored = try {
                seal.seal(record.id, rewritten)
            } catch (e: Exception) {
                report("A ${record.type} source's left-out libraries could not be moved to the library's choices", e)
                continue
            }
            library.excludeLibraries(record.id, excluded)
            library.queries.updateSourceConnection(record.displayName, record.locality.name, stored, record.id.value)
            registry.reload(record.copy(config = stored))
        }
    }

    /** Lets go of what was kept for a source that is being removed. */
    private fun forget(id: SourceId) {
        runCatching { seal.forget(id) }.onFailure { report("Secure storage did not let go of a removed source's connection", it) }
    }

    /**
     * Removes [id] and what Reflux derived from it, and lets go of its connection; watch history stays with
     * its works. That is done when this returns, whatever the network does. What the connection signed in to
     * on the other side (a server's session) is then ended, best-effort and once, without holding the removal
     * up: the result says whether that was confirmed. A failure is reported, never thrown.
     */
    fun remove(id: SourceId): Deferred<SignOut> {
        val departing = departing(id)
        library.removeSource(id)
        registry.unregister(id)
        forget(id)
        return signOut(departing, keeping = null)
    }

    /** A connection on its way out, opened while it can still be: the record and the seal are about to go. */
    private class Departing(val connector: SourceConnector, val config: String)

    private fun departing(id: SourceId): Departing? {
        val record = library.source(id) ?: return null
        val connector = connector(record.type) ?: return null
        val config = runCatching { seal.open(id, record.config) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        return Departing(connector, config)
    }

    private fun signOut(departing: Departing?, keeping: String?): Deferred<SignOut> {
        departing ?: return CompletableDeferred(SignOut.NONE)
        val type = departing.connector.type
        return signOuts.async {
            // The attempt runs apart, so a fetcher that cannot be interrupted only keeps itself waiting.
            val attempt = signOuts.async { runCatching { departing.connector.signOut(departing.config, keeping) } }
            val outcome = withTimeoutOrNull(signOutWithinMs) { attempt.await() }
            when {
                outcome == null -> {
                    attempt.cancel()
                    report("Signing out of a $type connection Reflux let go of: no answer in time, not confirmed", null)
                    SignOut.UNCONFIRMED
                }
                outcome.isFailure -> {
                    report("Signing out of a $type connection Reflux let go of was not confirmed", outcome.exceptionOrNull())
                    SignOut.UNCONFIRMED
                }
                outcome.getOrThrow() -> SignOut.ENDED
                else -> SignOut.NONE
            }
        }
    }

    /** Whether [id] is made of libraries (a server's). */
    fun sectioned(id: SourceId): Boolean = registry.resolve(id) is SectionedSource

    /**
     * Connects to what [request] names and makes it a source. With [replacing], the connection replaces
     * that source's: signing in again, or a changed address. Watch history belongs to works and survives
     * either way. Scanning is the caller's to start.
     */
    suspend fun connect(kind: ConnectionKind, request: ConnectionRequest, replacing: SourceId? = null, type: String? = null): ConnectResult =
        connecting(kind, request, replacing, type).first

    /**
     * [connect], and the sign-out of the connection it replaced: signing in again ends the session it
     * replaces, as removing does (best-effort, never holding the new connection up). [SignOut.NONE] when
     * nothing was replaced or it failed.
     */
    suspend fun connecting(
        kind: ConnectionKind,
        request: ConnectionRequest,
        replacing: SourceId? = null,
        /** The product the viewer chose ([products]); otherwise the first connector of [kind] that takes the address. */
        type: String? = null,
    ): Pair<ConnectResult, Deferred<SignOut>> {
        val previous = replacing?.let(library::source)
        val none: Deferred<SignOut> = CompletableDeferred(SignOut.NONE)
        val connector = if (type != null) {
            connector(type)?.takeIf { it.accepts(request.address) }
        } else {
            previous?.let { connector(it.type) }?.takeIf { it.accepts(request.address) } ?: route(kind, request.address)
        } ?: return ConnectResult.Failed(ConnectionProblem.INVALID_ADDRESS) to none
        val connection = try {
            connector.connect(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            report("Connecting a ${connector.type} source failed unexpectedly", e)
            return ConnectResult.Failed(ConnectionProblem.UNKNOWN) to none
        }
        val established = when (connection) {
            is Connection.Failed -> {
                report("Connecting a ${connector.type} source: ${connection.problem}" + connection.detail?.let { " ($it)" }.orEmpty(), null)
                return ConnectResult.Failed(connection.problem) to none
            }
            is Connection.Established -> connection
        }
        val source = established.source
        val id = source.descriptor.id
        val existing = library.source(id)
        // What this replaces, opened before the seal is given the new connection in its place.
        val replaced = (previous ?: existing)?.let { departing(it.id) }
        // A source the user already knows keeps the name it has; only how to reach it changes.
        val name = existing?.displayName ?: previous?.takeIf { it.type == source.descriptor.type }?.displayName ?: source.descriptor.displayName
        // Signing in again keeps the source's identity, and with it what the viewer included (the library's own state).
        val config = established.config
        // Kept securely or not at all: a connection is never written down in the open because the seal failed.
        val stored = try {
            seal.seal(id, config)
        } catch (e: Exception) {
            report("Secure storage refused the connection of a ${connector.type} source", e)
            (source as? AutoCloseable)?.let { runCatching { it.close() } }
            // Not kept, so the session just opened would only linger on the server.
            signOut(Departing(connector, established.config), keeping = null)
            return ConnectResult.Failed(ConnectionProblem.UNKNOWN) to none
        }
        registry.register(source)
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
        return ConnectResult.Connected(id, name, updated = existing != null || previous != null) to signOut(replaced, keeping = config)
    }

    companion object {
        /** Long enough for a server on the network to answer, short enough that nothing lingers on one that cannot. */
        const val SIGN_OUT_WITHIN_MS: Long = 5_000
    }
}
