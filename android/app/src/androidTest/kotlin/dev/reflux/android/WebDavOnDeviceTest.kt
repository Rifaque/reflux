package dev.reflux.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.reflux.android.engine.AndroidEnvironment
import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.net.HttpResult
import dev.reflux.core.source.ConnectionKind
import dev.reflux.core.source.ConnectionProblem
import dev.reflux.core.source.ConnectionRequest
import dev.reflux.core.source.SourceUnavailableException
import dev.reflux.library.ConnectResult
import dev.reflux.library.ScanReport
import dev.reflux.sources.webdav.WebDavConfig
import dev.reflux.sources.webdav.WebDavSource
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ProtocolException
import java.net.ServerSocket
import java.net.URL
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Web shares (WebDAV) added, scanned, and removed on the device, through the application's own wiring. */
@RunWith(AndroidJUnit4::class)
class WebDavOnDeviceTest {
    private fun environment() = AndroidEnvironment(
        context,
        dataDirectory = File(context.filesDir, "reflux-test-webdav-${System.nanoTime()}").also { it.mkdirs() },
        databaseName = null,
    )

    /** Why web shares have their own transport: what was seen on the Fire TV, on every Android device. */
    @Test
    fun androidsOwnHttpConnectionRefusesTheListingMethod() {
        for (url in listOf("http://127.0.0.1/", "https://127.0.0.1/")) {
            val connection = URL(url).openConnection() as HttpURLConnection
            assertFailsWith<ProtocolException>(url) { connection.requestMethod = "PROPFIND" }
        }
    }

    /** A share's listing read by Android's own XML parser, which is not the desktop's. */
    @Test
    fun aListingIsReadByAndroidsParserAndADoctypeIsRefused() = runBlocking<Unit> {
        val listing = """<?xml version="1.0" encoding="utf-8"?><D:multistatus xmlns:D="DAV:" xmlns:lp1="DAV:">""" +
            "<D:response><D:href>/dav/</D:href><D:propstat><D:prop><lp1:resourcetype><D:collection/></lp1:resourcetype></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>" +
            "<D:response><D:href>/dav/Am%c3%a9lie%20(2001).mkv</D:href><D:propstat><D:prop><lp1:resourcetype/><lp1:getcontentlength>7</lp1:getcontentlength>" +
            "<lp1:getlastmodified>Sun, 18 Oct 2026 14:58:12 GMT</lp1:getlastmodified></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response></D:multistatus>"
        val share = WebDavSource(WebDavConfig("http://nas.local/dav"), HttpFetcher { HttpResult(207, listing.encodeToByteArray()) })
        val file = share.files().toList().single()
        assertEquals("Amélie (2001).mkv" to 7L, file.path to file.sizeBytes)
        assertEquals(1_792_335_492_000, file.modifiedAtEpochMs)

        val entities = """<?xml version="1.0"?><!DOCTYPE m [<!ENTITY e "x">]><D:multistatus xmlns:D="DAV:"><D:response><D:href>/dav/&e;.mkv</D:href></D:response></D:multistatus>"""
        val hostile = WebDavSource(WebDavConfig("http://nas.local/dav"), HttpFetcher { HttpResult(207, entities.encodeToByteArray()) })
        assertFailsWith<SourceUnavailableException> { hostile.files().toList() }
    }

    @Test
    fun aShareIsAddedScannedAndRemoved() = runBlocking<Unit> {
        val share = LoopbackShare()
        try {
            val environment = environment()
            val request = ConnectionRequest(share.url, "alex", "s3cr3t")
            val wrong = environment.connections.connect(ConnectionKind.SHARE, request.copy(password = "nope"))
            assertEquals(ConnectResult.Failed(ConnectionProblem.AUTHENTICATION), wrong)

            val connected = assertIs<ConnectResult.Connected>(environment.connections.connect(ConnectionKind.SHARE, request))
            assertEquals(ScanReport.Status.COMPLETED, environment.pipeline.refresh(connected.sourceId).single().status)
            assertEquals(listOf("Night Drive"), environment.library.movies().map { it.item.title })
            assertTrue(share.methods.all { it == "PROPFIND" || it == "GET" || it == "HEAD" }, "nothing that changes a share: ${share.methods}")

            environment.removeSource(connected.sourceId)
            assertNull(environment.library.source(connected.sourceId))
            assertEquals(emptyList(), environment.library.movies())
        } finally {
            share.close()
        }
    }

    /**
     * The same against a real WebDAV server, when one is named (`-e refluxWebDav http://10.0.2.2:8098/`, with
     * `-e refluxWebDavUser` and `-e refluxWebDavPassword` if it wants a sign-in, as
     * `tools/ci/share-test-servers.sh` sets up): connected, scanned, every file opened by the device's own
     * media framework over the share for its running time, and removed.
     */
    @Test
    fun aRealShareIsAddedScannedOpenedAndRemoved() = runBlocking<Unit> {
        val arguments = InstrumentationRegistry.getArguments()
        val url = arguments.getString("refluxWebDav").orEmpty()
        assumeTrue("no WebDAV server named (-e refluxWebDav <url>)", url.isNotBlank())
        val environment = environment()
        val result = environment.connections.connect(
            ConnectionKind.SHARE,
            ConnectionRequest(url, arguments.getString("refluxWebDavUser"), arguments.getString("refluxWebDavPassword")),
        )
        val connected = assertIs<ConnectResult.Connected>(result)
        assertEquals(ScanReport.Status.COMPLETED, environment.pipeline.refresh(connected.sourceId).single().status)
        val works = environment.library.movies() + environment.library.shows()
        assertTrue(works.isNotEmpty(), "the share's media is in the library")
        for (movie in environment.library.movies()) {
            val version = environment.library.versions(movie.item.id).first().version
            assertTrue((version.stream.durationMs ?: 0) > 0, "${movie.item.title} was opened over the share")
        }
        environment.removeSource(connected.sourceId)
        assertNull(environment.library.source(connected.sourceId))
    }
}

/** A WebDAV share on the device's loopback with one film, for alex / s3cr3t. */
private class LoopbackShare : AutoCloseable {
    private val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1")) // Android's loopback address is ::1
    val url = "http://127.0.0.1:${socket.localPort}/dav"
    val methods: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

    private fun entry(href: String, folder: Boolean) = "<D:response><D:href>$href</D:href><D:propstat><D:prop>" +
        "<D:resourcetype>${if (folder) "<D:collection/>" else ""}</D:resourcetype><D:getcontentlength>2048</D:getcontentlength>" +
        "</D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>"

    private fun listing(vararg entries: String) = """<?xml version="1.0"?><D:multistatus xmlns:D="DAV:">${entries.joinToString("")}</D:multistatus>"""

    init {
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                val connection = runCatching { socket.accept() }.getOrNull() ?: break
                runCatching {
                    connection.use {
                        val input = it.getInputStream()
                        val (method, path) = line(input).split(' ').let { parts -> parts[0] to parts[1] }
                        val headers = generateSequence { line(input).takeIf(String::isNotEmpty) }
                            .associate { header -> header.substringBefore(':').trim().lowercase() to header.substringAfter(':').trim() }
                        repeat(headers["content-length"]?.toInt() ?: 0) { input.read() }
                        methods += method
                        val (status, body) = when {
                            headers["authorization"] != "Basic YWxleDpzM2NyM3Q=" -> 401 to ""
                            method != "PROPFIND" -> 404 to ""
                            headers["depth"] == "0" -> 207 to listing(entry("/dav/", true))
                            path == "/dav/" -> 207 to listing(entry("/dav/", true), entry("/dav/Movies/", true))
                            path == "/dav/Movies/" -> 207 to listing(entry("/dav/Movies/", true), entry("/dav/Movies/Night%20Drive%20%282020%29.mkv", false))
                            else -> 404 to ""
                        }
                        val content = body.encodeToByteArray()
                        it.getOutputStream().apply {
                            write("HTTP/1.1 $status Status\r\nContent-Length: ${content.size}\r\nConnection: close\r\n\r\n".encodeToByteArray())
                            write(content)
                            flush()
                        }
                    }
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
        return out.toString("UTF-8")
    }
}
