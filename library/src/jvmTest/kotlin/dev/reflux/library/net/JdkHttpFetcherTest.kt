package dev.reflux.library.net

import com.sun.net.httpserver.HttpServer
import dev.reflux.core.net.HttpRequest
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JdkHttpFetcherTest {
    private val servers = mutableListOf<HttpServer>()

    /** What the last request to each server carried as its `Authorization` header, by server. */
    private val seen = mutableMapOf<HttpServer, String?>()

    @AfterTest
    fun stop() = servers.forEach { it.stop(0) }

    /** 127.0.0.1 and localhost are different hosts to a client, so one server stands for "another host". */
    private fun server(handler: (com.sun.net.httpserver.HttpExchange) -> Unit): HttpServer =
        HttpServer.create(InetSocketAddress(0), 0).also { server ->
            server.createContext("/") { exchange ->
                seen[server] = exchange.requestHeaders.getFirst("Authorization")
                handler(exchange)
                exchange.close()
            }
            server.start()
            servers += server
        }

    private fun answer(status: Int, location: String? = null): (com.sun.net.httpserver.HttpExchange) -> Unit = { exchange ->
        location?.let { exchange.responseHeaders.add("Location", it) }
        exchange.sendResponseHeaders(status, -1)
    }

    @Test
    fun `a redirect to another host does not carry the request's credentials there`() = runBlocking {
        val elsewhere = server(answer(200))
        val origin = server(answer(302, "http://localhost:${elsewhere.address.port}/landing"))

        val result = JdkHttpFetcher().send(
            HttpRequest("http://127.0.0.1:${origin.address.port}/start", headers = mapOf("Authorization" to "MediaBrowser Token=\"secret\"")),
        )

        assertEquals(200, result.status)
        assertEquals("MediaBrowser Token=\"secret\"", seen[origin])
        assertNull(seen[elsewhere], "the other host must not receive the Authorization header")
    }

    @Test
    fun `a redirect on the same host keeps the request's credentials`() = runBlocking {
        lateinit var origin: HttpServer
        origin = server { exchange ->
            if (exchange.requestURI.path == "/start") answer(302, "/landing")(exchange) else answer(200)(exchange)
        }

        val result = JdkHttpFetcher().send(
            HttpRequest("http://127.0.0.1:${origin.address.port}/start", headers = mapOf("Authorization" to "MediaBrowser Token=\"secret\"")),
        )

        assertEquals(200, result.status)
        assertEquals("MediaBrowser Token=\"secret\"", seen[origin])
    }
}
