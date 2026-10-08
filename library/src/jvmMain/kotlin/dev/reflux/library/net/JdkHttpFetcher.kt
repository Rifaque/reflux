package dev.reflux.library.net

import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.net.HttpRequest as RefluxRequest
import dev.reflux.core.net.HttpResult
import kotlinx.coroutines.future.await
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** [HttpFetcher] on the JDK HTTP client (HTTP/2, redirects, system proxy settings). */
class JdkHttpFetcher(
    private val userAgent: String = "Reflux",
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .build(),
) : HttpFetcher {
    override suspend fun send(request: RefluxRequest): HttpResult {
        val body = request.body?.let(HttpRequest.BodyPublishers::ofByteArray) ?: HttpRequest.BodyPublishers.noBody()
        val jdkRequest = HttpRequest.newBuilder(URI(request.url))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", userAgent)
            .apply { request.headers.forEach { (name, value) -> header(name, value) } }
            .method(request.method, body)
            .build()
        val response = client.sendAsync(jdkRequest, HttpResponse.BodyHandlers.ofByteArray()).await()
        val responseHeaders = response.headers().map().mapValues { it.value.firstOrNull().orEmpty() }
        return HttpResult(response.statusCode(), response.body(), responseHeaders)
    }
}
