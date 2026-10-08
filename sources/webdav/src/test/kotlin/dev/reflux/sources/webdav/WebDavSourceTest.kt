package dev.reflux.sources.webdav

import dev.reflux.core.model.Availability
import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.net.HttpRequest
import dev.reflux.core.net.HttpResult
import dev.reflux.core.source.SourceLocality
import dev.reflux.core.source.SourceUnavailableException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Multistatus responses in the styles of nginx/Apache (`D:`) and Nextcloud (`d:` plus extra namespaces). */
class WebDavSourceTest {
    private val requests = mutableListOf<HttpRequest>()

    private fun response(href: String, collection: Boolean, size: Long? = null, modified: String? = null, prefix: String = "D") = """
        <$prefix:response><$prefix:href>$href</$prefix:href><$prefix:propstat><$prefix:prop>
        <$prefix:resourcetype>${if (collection) "<$prefix:collection/>" else ""}</$prefix:resourcetype>
        ${size?.let { "<$prefix:getcontentlength>$it</$prefix:getcontentlength>" } ?: ""}
        ${modified?.let { "<$prefix:getlastmodified>$it</$prefix:getlastmodified>" } ?: ""}
        </$prefix:prop><$prefix:status>HTTP/1.1 200 OK</$prefix:status></$prefix:propstat></$prefix:response>
    """.trimIndent()

    private fun multistatus(vararg responses: String, prefix: String = "D") =
        """<?xml version="1.0"?><$prefix:multistatus xmlns:$prefix="DAV:" xmlns:oc="http://owncloud.org/ns">${responses.joinToString("")}</$prefix:multistatus>"""

    private val listings = mapOf(
        "/dav/media/" to multistatus(
            response("/dav/media/", true),
            response("/dav/media/Movies/", true),
            response("/dav/media/.snapshots/", true),
            response("/dav/media/notes.txt", false, 10),
        ),
        "/dav/media/Movies/" to multistatus(
            response("/dav/media/Movies/", true, prefix = "d"),
            response("/dav/media/Movies/Heat%20(1995).mkv", false, 4_000_000_000, "Tue, 15 Nov 1994 08:12:31 GMT", prefix = "d"),
            response("https://nas.local/dav/media/Movies/Am%C3%A9lie%20(2001).mkv", false, 2_000, prefix = "d"),
            response("/dav/media/Movies/poster.jpg", false, 5, prefix = "d"),
            prefix = "d",
        ),
    )

    private val http = HttpFetcher { request ->
        requests += request
        val path = java.net.URI(request.url).path
        if (request.headers["Authorization"] != "Basic YWxleDpzM2NyM3Q=") return@HttpFetcher HttpResult(401, ByteArray(0))
        val body = listings[path] ?: return@HttpFetcher HttpResult(404, ByteArray(0))
        val depth = request.headers["Depth"]
        HttpResult(207, (if (depth == "0") multistatus(response(path, true)) else body).encodeToByteArray())
    }

    private val source = WebDavSource(WebDavConfig("https://nas.local/dav/media", "alex", "s3cr3t"), http)

    @Test
    fun listsMediaRecursivelyAndReadOnly() = runTest {
        val files = source.files().toList()
        assertEquals(listOf("Movies/Amélie (2001).mkv", "Movies/Heat (1995).mkv", "Movies/poster.jpg"), files.map { it.path })
        assertEquals(4_000_000_000, files[1].sizeBytes)
        assertEquals(784_887_151_000, files[1].modifiedAtEpochMs)
        assertTrue(requests.all { it.method == "PROPFIND" })
        assertTrue(requests.none { "snapshots" in it.url }, "hidden folders are not walked")
    }

    @Test
    fun playbackUsesEncodedUrlsWithCredentials() = runTest {
        val target = source.playbackTarget("Movies/Amélie (2001).mkv")
        assertEquals("https://nas.local/dav/media/Movies/Am%C3%A9lie%20(2001).mkv", target.uri)
        assertEquals("Basic YWxleDpzM2NyM3Q=", target.headers["Authorization"])
    }

    @Test
    fun availabilityAndAuthentication() = runTest {
        assertEquals(Availability.AVAILABLE, source.availability())
        val wrong = WebDavSource(WebDavConfig("https://nas.local/dav/media", "alex", "nope"), http)
        assertEquals(Availability.UNAVAILABLE, wrong.availability())
        assertFailsWith<SourceUnavailableException> { wrong.files().toList() }
    }

    @Test
    fun configRoundTripAndLocality() {
        val config = WebDavConfig("https://nas.local/dav", "alex", "pw=with=equals")
        assertEquals(config, WebDavConfig.decode(config.encode()))
        assertEquals(SourceLocality.LOCAL_NETWORK, source.descriptor.locality)
        assertEquals(SourceLocality.REMOTE, WebDavSource.localityOf("cloud.example.com"))
    }
}
