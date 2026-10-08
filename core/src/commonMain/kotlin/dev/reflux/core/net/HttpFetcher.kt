package dev.reflux.core.net

/** An HTTP request. Bodies are raw bytes; callers set `Content-Type` themselves. */
class HttpRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
)

/** Result of an HTTP request. */
class HttpResult(val status: Int, val body: ByteArray, val headers: Map<String, String> = emptyMap()) {
    val ok: Boolean get() = status in 200..299
    fun text(): String = body.decodeToString()
}

/**
 * Minimal HTTP access for providers, sources, and caches, implemented per platform.
 * Throws on transport failures (offline, DNS, TLS); HTTP error statuses are returned as results.
 */
fun interface HttpFetcher {
    suspend fun send(request: HttpRequest): HttpResult
}

suspend fun HttpFetcher.get(url: String, headers: Map<String, String> = emptyMap()): HttpResult =
    send(HttpRequest(url, headers = headers))

suspend fun HttpFetcher.postJson(url: String, json: String, headers: Map<String, String> = emptyMap()): HttpResult =
    send(HttpRequest(url, "POST", headers + ("Content-Type" to "application/json"), json.encodeToByteArray()))
