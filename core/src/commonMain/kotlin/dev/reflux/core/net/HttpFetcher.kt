package dev.reflux.core.net

/** Result of an HTTP GET. */
class HttpResult(val status: Int, val body: ByteArray, val headers: Map<String, String> = emptyMap()) {
    val ok: Boolean get() = status in 200..299
    fun text(): String = body.decodeToString()
}

/**
 * Minimal HTTP access for providers and caches, implemented per platform.
 * Throws on transport failures (offline, DNS, TLS); HTTP error statuses are returned as results.
 */
fun interface HttpFetcher {
    suspend fun get(url: String, headers: Map<String, String>): HttpResult
}
