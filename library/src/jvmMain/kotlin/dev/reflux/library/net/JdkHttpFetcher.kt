package dev.reflux.library.net

import dev.reflux.core.net.HttpFetcher
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
    override suspend fun get(url: String, headers: Map<String, String>): HttpResult {
        val request = HttpRequest.newBuilder(URI(url))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", userAgent)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .GET()
            .build()
        val response = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).await()
        val responseHeaders = response.headers().map().mapValues { it.value.firstOrNull().orEmpty() }
        return HttpResult(response.statusCode(), response.body(), responseHeaders)
    }
}
