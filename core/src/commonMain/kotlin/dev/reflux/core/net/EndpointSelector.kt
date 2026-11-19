package dev.reflux.core.net

import kotlin.concurrent.Volatile

/**
 * Which of a server's addresses to use (B-003): one server, one account, reached at home through its LAN address and
 * away through another (a Tailscale address, a public name). Pure and deterministic; the adapter that talks to the
 * server asks it which address to try and tells it what happened.
 *
 * - **Preference.** The addresses in [preferenceOrder]: the home network first, then overlay networks (Tailscale),
 *   then everything else, and within each kind the order they were added.
 * - **Sticky.** A request goes first to the address that last answered, so a viewer away from home never waits for the
 *   home address to time out on every request; the others are tried in preference order only when it does not answer.
 * - **Failing over** is for not answering at all (no route, refused, timed out). An HTTP answer, even an error, came
 *   from the server: the same server would answer the same at its other address.
 * - **Recovery.** An availability check ([probeOrder]) tries the more preferred addresses first again, at most every
 *   [recheckAfterMs], so a viewer who comes home is back on the LAN address with the next check, not the next restart.
 *
 * Thread-safe enough for its purpose: concurrent requests may race to record an outcome, which at worst costs one
 * extra attempt; the state is never torn.
 */
class EndpointSelector(
    addresses: List<String>,
    private val clock: () -> Long,
    private val recheckAfterMs: Long = RECHECK_AFTER_MS,
) {
    /** Every address, most preferred first. Never empty. */
    val addresses: List<String> = preferenceOrder(addresses).also { require(it.isNotEmpty()) { "a server has an address" } }

    private class State(val current: String, val lastSuccess: Map<String, Long>, val lastFailure: Map<String, Long>, val lastRecheck: Long)

    @Volatile
    private var state = State(this.addresses.first(), emptyMap(), emptyMap(), Long.MIN_VALUE / 2)

    /** The address a request goes to first: the one that last answered, the most preferred until one has. */
    val current: String get() = state.current

    /** The order to try for an ordinary request: the current address, then the others by preference. */
    fun requestOrder(): List<String> = listOf(state.current) + addresses.filter { it != state.current }

    /**
     * The order for an availability check: by preference, so that a more preferred address that answers again is
     * taken back; but while the current one is not the most preferred and was checked recently, it alone goes first.
     */
    fun probeOrder(): List<String> {
        val s = state
        if (s.current == addresses.first()) return addresses
        if (clock() - s.lastRecheck < recheckAfterMs) return requestOrder()
        state = State(s.current, s.lastSuccess, s.lastFailure, clock())
        return addresses
    }

    /** [address] answered: it is the one used now. */
    fun succeeded(address: String) {
        if (address !in addresses) return
        val s = state
        state = State(address, s.lastSuccess + (address to clock()), s.lastFailure, s.lastRecheck)
    }

    /** [address] did not answer at all. */
    fun failed(address: String) {
        if (address !in addresses) return
        val s = state
        state = State(s.current, s.lastSuccess, s.lastFailure + (address to clock()), s.lastRecheck)
    }

    /** What is known of each address, most preferred first, for showing it. */
    fun health(): List<EndpointHealth> = state.let { s ->
        addresses.map { EndpointHealth(it, it == s.current, s.lastSuccess[it], s.lastFailure[it]) }
    }

    companion object {
        const val RECHECK_AFTER_MS: Long = 60_000

        /** How near an address is: 0 the home network, 1 an overlay network (Tailscale), 2 anything else. */
        fun rank(address: String): Int {
            val host = address.substringAfter("://").substringBefore('/').let { authority ->
                if (authority.startsWith("[")) authority.substringAfter('[').substringBefore(']') else authority.substringAfter('@').substringBeforeLast(':')
            }.lowercase()
            val octets = host.split('.').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 4 && host.count { c -> c == '.' } == 3 }
            if (octets != null) {
                return when {
                    octets[0] == 10 || octets[0] == 127 || (octets[0] == 192 && octets[1] == 168) || (octets[0] == 172 && octets[1] in 16..31) ||
                        (octets[0] == 169 && octets[1] == 254) -> 0
                    octets[0] == 100 && octets[1] in 64..127 -> 1
                    else -> 2
                }
            }
            return when {
                host == "localhost" || host == "::1" || host.startsWith("fe80") || host.endsWith(".local") || host.endsWith(".lan") ||
                    host.endsWith(".home") || host.endsWith(".home.arpa") || host.endsWith(".internal") || ('.' !in host && ':' !in host) -> 0
                host.endsWith(".ts.net") || host.startsWith("fd7a:115c:a1e0") -> 1
                host.startsWith("fd") || host.startsWith("fc") -> 0
                else -> 2
            }
        }

        /** [addresses] without repeats (compared as servers compare them), nearest kind first, otherwise as given. */
        fun preferenceOrder(addresses: List<String>): List<String> =
            addresses.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy(::same).withIndex().sortedWith(compareBy({ rank(it.value) }, { it.index })).map { it.value }

        /** The form addresses are compared in: no trailing slash, no case. */
        fun same(address: String): String = address.trim().trimEnd('/').lowercase()
    }
}

/** What is known of one of a server's addresses. Times are the library clock's; null when it never happened. */
data class EndpointHealth(val address: String, val inUse: Boolean, val lastSuccessAt: Long?, val lastFailureAt: Long?)
