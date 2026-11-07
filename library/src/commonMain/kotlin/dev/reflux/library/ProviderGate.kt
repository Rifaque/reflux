package dev.reflux.library

import dev.reflux.core.metadata.MetadataCandidate
import dev.reflux.core.metadata.MetadataKind
import dev.reflux.core.metadata.MetadataProvider
import dev.reflux.core.metadata.MetadataQuery
import dev.reflux.core.metadata.MetadataUnavailableException
import dev.reflux.core.metadata.ProviderAttribution
import dev.reflux.core.metadata.ProviderRef
import dev.reflux.core.metadata.SeasonMetadata
import dev.reflux.core.metadata.WorkMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

/**
 * Stands between Reflux and a metadata provider so that a large library never becomes a storm of requests
 * (docs/METADATA.md, "Requests"):
 *
 * - **Coalescing.** The same question asked while it is being answered waits for that answer, and is asked once.
 * - **A short memory.** An answer is kept for [memoryMs] (the Identify page searching again, a details page
 *   refreshing twice), at most [memorySize] of them. Persisted metadata is the library's; this is only in memory.
 * - **Spacing.** Requests go one at a time, at least [spacingMs] apart.
 * - **Backing off.** After the provider cannot be reached, it is not asked again for a while (from
 *   [initialBackoffMs], doubling up to [maxBackoffMs]); callers are told it is unavailable at once, as they would
 *   be by the provider, and keep what they have.
 *
 * It changes no answer: what the provider says, or that it cannot be reached, is passed on as it is.
 */
class ProviderGate(
    private val provider: MetadataProvider,
    private val spacingMs: Long = 120,
    private val memoryMs: Long = 10 * 60_000L,
    private val memorySize: Int = 256,
    private val initialBackoffMs: Long = 60_000L,
    private val maxBackoffMs: Long = 30 * 60_000L,
    private val clock: () -> Long = monotonicMs(),
) : MetadataProvider {
    override val id: String get() = provider.id
    override val attribution: ProviderAttribution? get() = provider.attribution

    private val lock = Mutex()
    private val inFlight = mutableMapOf<String, CompletableDeferred<Any?>>()
    private val memory = LinkedHashMap<String, Pair<Long, Any?>>()
    private val turn = Mutex()
    private var lastRequestAt = Long.MIN_VALUE
    private var closedUntil = Long.MIN_VALUE
    private var backoff = initialBackoffMs

    /** How many requests reached the provider, for tests and diagnostics. */
    var requests: Int = 0
        private set

    override suspend fun search(query: MetadataQuery): List<MetadataCandidate> =
        ask("search|${query.kind}|${query.title}|${query.year}|${query.language}|${query.externalIds.toSortedMap()}") { provider.search(query) }

    override suspend fun findByExternalId(kind: MetadataKind, source: String, id: String): ProviderRef? =
        ask("find|$kind|$source|$id") { provider.findByExternalId(kind, source, id) }

    override suspend fun details(ref: ProviderRef, language: String): WorkMetadata? =
        ask("details|$ref|$language") { provider.details(ref, language) }

    override suspend fun season(ref: ProviderRef, seasonNumber: Int, language: String): SeasonMetadata? =
        ask("season|$ref|$seasonNumber|$language") { provider.season(ref, seasonNumber, language) }

    /** Forgets what was remembered and lets the provider be asked at once, e.g. when the viewer asks for a refresh. */
    suspend fun reset() = lock.withLock {
        memory.clear()
        closedUntil = Long.MIN_VALUE
        backoff = initialBackoffMs
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> ask(key: String, call: suspend () -> T): T {
        val (waiting, owner) = lock.withLock {
            val now = clock()
            memory[key]?.let { (at, value) -> if (now - at < memoryMs) return value as T else memory.remove(key) }
            if (now < closedUntil) throw MetadataUnavailableException("${provider.id} is resting after failing")
            inFlight[key]?.let { return@withLock it to false }
            CompletableDeferred<Any?>().also { inFlight[key] = it } to true
        }
        if (!owner) return waiting.await() as T
        return try {
            val value = turn.withLock {
                val wait = if (lastRequestAt == Long.MIN_VALUE) 0 else lastRequestAt + spacingMs - clock()
                if (wait > 0) delay(wait)
                lastRequestAt = clock()
                requests++
                call()
            }
            lock.withLock {
                backoff = initialBackoffMs
                memory[key] = clock() to value
                while (memory.size > memorySize) memory.remove(memory.keys.first())
                inFlight.remove(key)
            }
            waiting.complete(value)
            value
        } catch (e: Throwable) {
            lock.withLock {
                if (e is MetadataUnavailableException) {
                    closedUntil = clock() + backoff
                    backoff = (backoff * 2).coerceAtMost(maxBackoffMs)
                }
                inFlight.remove(key)
            }
            // Those waiting are told what the one asking was, cancellation included.
            waiting.completeExceptionally(if (e is CancellationException) MetadataUnavailableException("cancelled", e) else e)
            throw e
        }
    }

    private companion object {
        fun monotonicMs(): () -> Long {
            val start = TimeSource.Monotonic.markNow()
            return { start.elapsedNow().inWholeMilliseconds }
        }
    }
}
