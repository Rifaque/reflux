package dev.reflux.metadata.trakt

import dev.reflux.core.metadata.MetadataUnavailableException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.random.Random

/**
 * Keeps Reflux inside Trakt's limits for one Client ID and address (docs/METADATA.md, "Requests"), on top of the
 * library's provider gate, which already asks one question at a time and never the same one twice.
 *
 * - **A budget.** Requests draw from a bucket of [limit] requests refilled evenly over [periodMs] (Trakt's
 *   unauthenticated GET limit to start with), so a first scan of a large library is paced instead of spending the
 *   whole window in its first seconds and then failing.
 * - **What Trakt says.** Each response's `X-Ratelimit` header (`limit`, `period`, `remaining`, `until`) replaces
 *   the estimate. With nothing remaining, no request is sent before `until`.
 * - **Told to wait.** A 429's `Retry-After`, or a bounded backoff with jitter when it has none, pauses every request.
 * - **Never spinning.** A wait up to [maxWaitMs] is waited out (an Identify search stays a search); a longer one
 *   fails at once with [MetadataUnavailableException.retryAfterMs], so a background refresh stops and the gate
 *   leaves Trakt alone for as long as Trakt asked.
 *
 * [clock] is wall time in milliseconds, because `until` is a moment, not a duration.
 */
class TraktRateLimiter(
    private var limit: Int = DEFAULT_LIMIT,
    private var periodMs: Long = DEFAULT_PERIOD_MS,
    /** The longest wait waited out in place; longer ones are the caller's to come back after. */
    val maxWaitMs: Long = 10_000L,
    private val clock: () -> Long = ::epochMillis,
    private val random: Random = Random.Default,
) {
    private val lock = Mutex()
    private var tokens = limit.toDouble()
    private var refilledAt = Long.MIN_VALUE
    /** Wall time before which nothing is sent; 0 when nothing is paused (never [Long.MIN_VALUE]: it would overflow). */
    private var pausedUntil = 0L
    private var failures = 0

    /** What is known now, for tests and diagnostics. */
    data class State(val limit: Int, val periodMs: Long, val tokens: Double, val pausedForMs: Long)

    suspend fun state(): State = lock.withLock {
        refill(clock())
        State(limit, periodMs, tokens, (pausedUntil - clock()).coerceAtLeast(0))
    }

    /** Waits for a request's turn, or throws [MetadataUnavailableException] when that would take longer than [maxWaitMs]. */
    suspend fun acquire() = lock.withLock {
        var now = clock()
        refill(now)
        val paused = (pausedUntil - now).coerceAtLeast(0)
        val short = if (tokens >= 1) 0L else ((1 - tokens) * periodMs / limit).toLong() + 1
        val wait = maxOf(paused, short)
        if (wait > maxWaitMs) throw MetadataUnavailableException("Trakt asked to wait", retryAfterMs = wait)
        if (wait > 0) {
            delay(wait)
            now = clock()
            refill(now)
        }
        tokens = (tokens - 1).coerceAtLeast(0.0)
    }

    /** Reads `X-Ratelimit` from a response; a response that is not a 429 also ends the run of failures. */
    suspend fun observe(status: Int, headers: Map<String, String>) = lock.withLock {
        if (status != 429) failures = 0
        val header = headers.header("X-Ratelimit") ?: return@withLock
        val json = runCatching { Json.parseToJsonElement(header) as? JsonObject }.getOrNull() ?: return@withLock
        val now = clock()
        refill(now)
        json.int("limit")?.takeIf { it > 0 }?.let { limit = it }
        json.int("period")?.takeIf { it > 0 }?.let { periodMs = it * 1000L }
        json.int("remaining")?.let { remaining ->
            tokens = remaining.coerceIn(0, limit).toDouble()
            if (remaining <= 0) {
                parseInstantMillis(json.string("until"))?.let { pausedUntil = maxOf(pausedUntil, it) }
            }
        }
    }

    /**
     * Records a 429 and returns how long to wait before the next request: `Retry-After` when Trakt sent it, else
     * a backoff doubling from [BASE_BACKOFF_MS] up to [MAX_BACKOFF_MS], with jitter so clients do not return together.
     */
    suspend fun rateLimited(headers: Map<String, String>): Long = lock.withLock {
        failures++
        val stated = headers.header("Retry-After")?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.let { it * 1000 }
        val wait = stated ?: run {
            val ceiling = (BASE_BACKOFF_MS shl (failures - 1).coerceAtMost(10)).coerceAtMost(MAX_BACKOFF_MS)
            ceiling / 2 + random.nextLong(ceiling / 2 + 1)
        }
        tokens = 0.0
        pausedUntil = maxOf(pausedUntil, clock() + wait)
        wait
    }

    /** Pauses every request for [ms], e.g. after Trakt says it is overloaded. */
    suspend fun pause(ms: Long) = lock.withLock {
        pausedUntil = maxOf(pausedUntil, clock() + ms)
    }

    private fun refill(now: Long) {
        if (refilledAt != Long.MIN_VALUE && now > refilledAt) {
            tokens = (tokens + (now - refilledAt).toDouble() * limit / periodMs).coerceAtMost(limit.toDouble())
        }
        refilledAt = now
    }

    companion object {
        /** Trakt's Standard API limit for unauthenticated GET requests: 500 every 5 minutes per Client ID and address. */
        const val DEFAULT_LIMIT: Int = 500
        const val DEFAULT_PERIOD_MS: Long = 5 * 60_000L
        const val BASE_BACKOFF_MS: Long = 2_000L
        const val MAX_BACKOFF_MS: Long = 5 * 60_000L
    }
}

internal fun Map<String, String>.header(name: String): String? = entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
