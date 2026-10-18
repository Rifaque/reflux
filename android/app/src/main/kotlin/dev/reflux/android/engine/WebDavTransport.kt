package dev.reflux.android.engine

import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.net.HttpRequest
import dev.reflux.core.net.HttpResult
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * HTTP for web shares (WebDAV) on Android. A share is listed with `PROPFIND`, and Android's
 * `HttpURLConnection` refuses to send any method but plain HTTP's, so shares go through OkHttp here; the
 * rest of the engine keeps [AndroidFetcher]. Only the methods that read a share are sent, so this cannot
 * become a way to change one.
 *
 * Redirects are followed here rather than by OkHttp, under the desktop's rules: the method and body are kept
 * (a listing stays a listing), a secure address is never left for a plain one, and the share's credentials
 * do not go to an address of another origin.
 */
class WebDavTransport(
    private val userAgent: String,
    private val client: OkHttpClient = defaultClient(),
    private val maxResponseBytes: Long = MAX_RESPONSE_BYTES,
) : HttpFetcher {
    override suspend fun send(request: HttpRequest): HttpResult {
        val method = request.method.uppercase()
        require(method in READ_METHODS) { "a share is only read; $method is not sent" }
        var url = request.url.toHttpUrl()
        var headers = request.headers
        repeat(MAX_REDIRECTS + 1) {
            val response = execute(build(url, method, headers, request.body))
            val next = response.status.takeIf { it in REDIRECTS }?.let { response.headers.location()?.let(url::resolve) }
            if (next == null || (url.isHttps && !next.isHttps)) return response
            if (!sameOrigin(url, next)) headers = headers.filterKeys { !it.equals("Authorization", ignoreCase = true) }
            url = next
        }
        throw IOException("too many redirects")
    }

    private fun build(url: HttpUrl, method: String, headers: Map<String, String>, body: ByteArray?): Request =
        Request.Builder().url(url).header("User-Agent", userAgent)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            // No media type of its own: the request's Content-Type header is the one sent.
            .method(method, body?.toRequestBody(null))
            .build()

    private suspend fun execute(request: Request): HttpResult {
        val call = client.newCall(request)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)

                override fun onResponse(call: Call, response: Response) {
                    runCatching { response.use(::read) }.fold({ continuation.resume(it) }, { continuation.resumeWithException(it) })
                }
            })
        }
    }

    /** The answer, held in memory: a listing is small, and one that is not is refused rather than read. */
    private fun read(response: Response): HttpResult {
        val body = response.body
        val source = body.source()
        if (body.contentLength() > maxResponseBytes || source.request(maxResponseBytes + 1)) {
            throw IOException("answer larger than $maxResponseBytes bytes")
        }
        val names = response.headers.names()
        return HttpResult(response.code, source.buffer.readByteArray(), names.associateWith { response.headers[it].orEmpty() })
    }

    companion object {
        /** What reading a share needs: listing (`PROPFIND`), and fetching or asking about a file. */
        val READ_METHODS: Set<String> = setOf("PROPFIND", "GET", "HEAD", "OPTIONS")

        /** As many as the desktop's HTTP client follows. */
        const val MAX_REDIRECTS = 5

        /** Far more than a listing of a folder with ten thousand files. */
        const val MAX_RESPONSE_BYTES = 32L * 1024 * 1024

        private val REDIRECTS = setOf(301, 302, 303, 307, 308)

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .build()

        private fun sameOrigin(a: HttpUrl, b: HttpUrl) = a.scheme == b.scheme && a.host == b.host && a.port == b.port

        private fun Map<String, String>.location(): String? = entries.firstOrNull { it.key.equals("Location", ignoreCase = true) }?.value
    }
}
