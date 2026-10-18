package dev.reflux.android

import dev.reflux.android.engine.WebDavTransport
import dev.reflux.core.net.HttpRequest
import dev.reflux.core.source.Connection
import dev.reflux.core.source.ConnectionProblem
import dev.reflux.core.source.ConnectionRequest
import dev.reflux.core.source.SourceUnavailableException
import dev.reflux.sources.webdav.WebDavConfig
import dev.reflux.sources.webdav.WebDavConnector
import dev.reflux.sources.webdav.WebDavSource
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Collections
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Android transport for web shares against a real socket: what goes over the wire, and that the shared
 * WebDAV adapter gets the same answers from it as from the desktop's client.
 */
class WebDavTransportTest {
    private val servers = mutableListOf<FakeShare>()
    private val transport = WebDavTransport(userAgent = "Reflux/test")

    @AfterTest
    fun stop() = servers.forEach(FakeShare::close)

    private fun share(handler: (Received) -> Reply) = FakeShare(handler).also { servers += it }

    /** A share at /dav/ with a film in Movies/, for alex / s3cr3t. */
    private fun library() = share { request ->
        when {
            request.headers["authorization"] != "Basic YWxleDpzM2NyM3Q=" -> Reply(401)
            request.method != "PROPFIND" -> Reply(405)
            request.path == "/dav/" && request.headers["depth"] == "0" -> Reply(207, multistatus(folder("/dav/")))
            request.path == "/dav/" -> Reply(207, multistatus(folder("/dav/"), folder("/dav/Movies/"), file("/dav/notes.txt")))
            request.path == "/dav/Movies/" -> Reply(207, multistatus(folder("/dav/Movies/"), file("/dav/Movies/Heat%20%281995%29.mkv")))
            else -> Reply(404)
        }
    }

    @Test
    fun aListingGoesOutAsPropfindWithItsHeadersAndBody() = runBlocking<Unit> {
        val server = share { Reply(207, multistatus(folder("/dav/"))) }
        val body = "<d:propfind xmlns:d=\"DAV:\"/>".encodeToByteArray()
        val result = transport.send(
            HttpRequest(
                server.url("/dav/"), "PROPFIND",
                mapOf("Depth" to "1", "Content-Type" to "application/xml; charset=utf-8", "Authorization" to "Basic abc"), body,
            ),
        )
        assertEquals(207, result.status)
        assertTrue("multistatus" in result.text())
        val received = server.received.single()
        assertEquals("PROPFIND", received.method)
        assertEquals("/dav/", received.path)
        assertEquals("1", received.headers["depth"])
        assertEquals("application/xml; charset=utf-8", received.headers["content-type"])
        assertEquals("Basic abc", received.headers["authorization"])
        assertEquals("Reflux/test", received.headers["user-agent"])
        assertEquals(body.decodeToString(), received.body.decodeToString())
    }

    @Test
    fun theSharedAdapterConnectsListsAndPlaysThroughIt() = runBlocking<Unit> {
        val server = library()
        val connected = WebDavConnector(transport).connect(ConnectionRequest(server.url("/dav"), "alex", "s3cr3t"))
        val source = assertIs<Connection.Established>(connected).source as WebDavSource
        assertEquals(listOf("Movies/Heat (1995).mkv"), source.files().toList().map { it.path })
        assertEquals(server.url("/dav/Movies/Heat%20(1995).mkv"), source.playbackTarget("Movies/Heat (1995).mkv").uri)
        assertTrue(server.received.all { it.method == "PROPFIND" })
    }

    @Test
    fun everyFailureIsToldApartAsOnTheDesktop() = runBlocking<Unit> {
        val server = library()
        suspend fun problem(address: String, user: String? = "alex", password: String? = "s3cr3t") =
            (WebDavConnector(transport).connect(ConnectionRequest(address, user, password)) as? Connection.Failed)?.problem
        assertEquals(ConnectionProblem.AUTHENTICATION, problem(server.url("/dav"), password = "nope"))
        assertEquals(ConnectionProblem.SIGN_IN_REQUIRED, problem(server.url("/dav"), null, null))
        assertEquals(ConnectionProblem.NOT_FOUND, problem(server.url("/elsewhere")))
        assertEquals(ConnectionProblem.SERVER_ERROR, problem(share { Reply(500) }.url("/dav")))
        assertEquals(ConnectionProblem.PERMISSION_DENIED, problem(share { Reply(403) }.url("/dav")))
        val closed = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
        assertEquals(ConnectionProblem.UNREACHABLE, problem("http://127.0.0.1:$closed/dav"))
    }

    @Test
    fun aBrokenListingIsAShareThatCannotBeRead() = runBlocking<Unit> {
        val server = share { Reply(207, "<D:multistatus xmlns:D=\"DAV:\"><D:response>") }
        assertFailsWith<SourceUnavailableException> { WebDavSource(WebDavConfig(server.url("/dav")), transport).files().toList() }
    }

    @Test
    fun aRedirectKeepsTheListingAndItsCredentialsOnTheSameShare() = runBlocking<Unit> {
        val server = share { request ->
            if (request.path == "/dav") Reply(301, headers = mapOf("Location" to "/dav/")) else Reply(207, multistatus(folder("/dav/")))
        }
        val result = transport.send(HttpRequest(server.url("/dav"), "PROPFIND", mapOf("Authorization" to "Basic abc", "Depth" to "0"), "<x/>".encodeToByteArray()))
        assertEquals(207, result.status)
        val (first, second) = server.received
        assertEquals("/dav" to "/dav/", first.path to second.path)
        assertEquals("PROPFIND", second.method)
        assertEquals("<x/>", second.body.decodeToString())
        assertEquals("Basic abc", second.headers["authorization"])
    }

    @Test
    fun credentialsAreNotCarriedToAnotherOrigin() = runBlocking<Unit> {
        val elsewhere = share { Reply(207, multistatus(folder("/dav/"))) }
        val server = share { Reply(307, headers = mapOf("Location" to elsewhere.url("/dav/"))) }
        transport.send(HttpRequest(server.url("/dav/"), "PROPFIND", mapOf("Authorization" to "Basic abc", "Depth" to "0")))
        assertEquals("Basic abc", server.received.single().headers["authorization"])
        assertNull(elsewhere.received.single().headers["authorization"])
    }

    @Test
    fun endlessRedirectsEnd() = runBlocking<Unit> {
        val server = share { Reply(302, headers = mapOf("Location" to "/again")) }
        assertFailsWith<IOException> { transport.send(HttpRequest(server.url("/dav/"), "PROPFIND")) }
        assertEquals(WebDavTransport.MAX_REDIRECTS + 1, server.received.size)
    }

    @Test
    fun nothingThatWouldChangeAShareIsSent() = runBlocking<Unit> {
        val server = share { Reply(201) }
        for (method in listOf("PUT", "DELETE", "MOVE", "COPY", "MKCOL", "PROPPATCH", "LOCK", "POST")) {
            assertFailsWith<IllegalArgumentException>(method) { transport.send(HttpRequest(server.url("/dav/x"), method, body = ByteArray(1))) }
        }
        assertTrue(server.received.isEmpty())
    }

    @Test
    fun anAnswerTooLargeToBeAListingIsRefused() = runBlocking<Unit> {
        val small = WebDavTransport(userAgent = "Reflux/test", maxResponseBytes = 1024)
        val server = share { Reply(207, "x".repeat(4096)) }
        assertFailsWith<IOException> { small.send(HttpRequest(server.url("/dav/"), "PROPFIND")) }
        assertEquals(1024, small.send(HttpRequest(share { Reply(207, "y".repeat(1024)) }.url("/"), "PROPFIND")).body.size)
    }

    @Test
    fun aSecureShareIsNotLeftForAPlainAddress() {
        // Not followed: the redirect is handed back, which the connector reports as no share there.
        assertFalse(WebDavTransport.defaultClient().followRedirects, "redirects are the transport's own, under its rules")
    }

    private fun folder(href: String) =
        "<D:response><D:href>$href</D:href><D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>"

    private fun file(href: String) =
        "<D:response><D:href>$href</D:href><D:propstat><D:prop><D:resourcetype/><D:getcontentlength>7</D:getcontentlength></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>"

    private fun multistatus(vararg responses: String) = """<?xml version="1.0"?><D:multistatus xmlns:D="DAV:">${responses.joinToString("")}</D:multistatus>"""
}

class Received(val method: String, val path: String, val headers: Map<String, String>, val body: ByteArray)

class Reply(val status: Int, val body: String = "", val headers: Map<String, String> = emptyMap())

/** An HTTP/1.1 server on loopback that answers each request from [handler], one connection per request. */
class FakeShare(private val handler: (Received) -> Reply) : AutoCloseable {
    private val socket = ServerSocket(0, 16, InetAddress.getLoopbackAddress())
    val received: MutableList<Received> = Collections.synchronizedList(mutableListOf())

    fun url(path: String) = "http://127.0.0.1:${socket.localPort}$path"

    init {
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                val connection = runCatching { socket.accept() }.getOrNull() ?: break
                connection.use {
                    val input = BufferedInputStream(it.getInputStream())
                    val (method, path) = line(input).split(' ').let { parts -> parts[0] to parts[1] }
                    val headers = generateSequence { line(input).takeIf(String::isNotEmpty) }
                        .associate { header -> header.substringBefore(':').trim().lowercase() to header.substringAfter(':').trim() }
                    val body = ByteArray(headers["content-length"]?.toInt() ?: 0).also { bytes -> input.readNBytesCompat(bytes) }
                    val request = Received(method, path, headers, body)
                    received += request
                    val reply = handler(request)
                    val content = reply.body.encodeToByteArray()
                    val head = buildString {
                        append("HTTP/1.1 ${reply.status} Status\r\n")
                        reply.headers.forEach { (name, value) -> append("$name: $value\r\n") }
                        append("Content-Length: ${content.size}\r\nConnection: close\r\n\r\n")
                    }
                    it.getOutputStream().apply { write(head.encodeToByteArray()); write(content); flush() }
                }
            }
        }
    }

    override fun close() = socket.close()

    private fun line(input: InputStream): String {
        val out = ByteArrayOutputStream()
        while (true) {
            val byte = input.read()
            if (byte == -1 || byte == '\n'.code) break
            if (byte != '\r'.code) out.write(byte)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun InputStream.readNBytesCompat(into: ByteArray) {
        var read = 0
        while (read < into.size) {
            val n = read(into, read, into.size - read)
            if (n < 0) break
            read += n
        }
    }
}
