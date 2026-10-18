package dev.reflux.sources.webdav

import dev.reflux.core.model.Availability
import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.net.HttpRequest
import dev.reflux.core.net.HttpResult
import dev.reflux.core.source.SourceFile
import dev.reflux.core.source.SourceUnavailableException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** What a share is asked, and what is made of its answers: the cases a transport on any platform must give the same result for. */
class WebDavListingTest {
    private val requests = mutableListOf<HttpRequest>()

    /** Each folder's listing by its path; a missing folder is a 404. */
    private val folders = mutableMapOf<String, String>()

    /** An answer other than a listing, by path. */
    private val answers = mutableMapOf<String, HttpResult>()

    private val http = HttpFetcher { request ->
        requests += request
        val path = URI(request.url).rawPath
        answers[path]?.let { return@HttpFetcher it }
        if (request.headers["Authorization"] != "Basic YWxleDpzM2NyM3Q=") return@HttpFetcher HttpResult(401, ByteArray(0))
        val body = folders[path] ?: return@HttpFetcher HttpResult(404, ByteArray(0))
        HttpResult(207, (if (request.headers["Depth"] == "0") listing(path, entry(path, folder = true)) else body).encodeToByteArray())
    }

    private fun entry(href: String, folder: Boolean = false, size: Long = 1) =
        "<D:response><D:href>$href</D:href><D:propstat><D:prop><D:resourcetype>${if (folder) "<D:collection/>" else ""}</D:resourcetype>" +
            "<D:getcontentlength>$size</D:getcontentlength></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>"

    private fun listing(self: String, vararg entries: String) =
        """<?xml version="1.0"?><D:multistatus xmlns:D="DAV:">${entry(self, folder = true)}${entries.joinToString("")}</D:multistatus>"""

    private fun share(url: String = "http://nas.local/dav") = WebDavSource(WebDavConfig(url, "alex", "s3cr3t"), http)

    @Test
    fun aListingIsOnePropfindWithItsDepthBodyAndCredentials() = runTest {
        folders["/dav/"] = listing("/dav/")
        share().files().toList()
        val request = requests.single()
        assertEquals("PROPFIND", request.method)
        assertEquals("http://nas.local/dav/", request.url)
        assertEquals("1", request.headers["Depth"])
        assertEquals("application/xml; charset=utf-8", request.headers["Content-Type"])
        assertEquals("Basic YWxleDpzM2NyM3Q=", request.headers["Authorization"])
        val body = request.body!!.decodeToString()
        assertTrue("<d:propfind" in body && "resourcetype" in body && "getcontentlength" in body && "getlastmodified" in body, body)

        requests.clear()
        share().availability()
        assertEquals("0", requests.single().headers["Depth"], "asking whether a share is there lists nothing")
    }

    @Test
    fun anEmptyShareHasNoFilesAndIsStillThere() = runTest {
        folders["/dav/"] = listing("/dav/")
        assertEquals(emptyList(), share().files().toList())
        assertEquals(Availability.AVAILABLE, share().availability())
    }

    @Test
    fun nestedFoldersAreWalkedAndOnlyMediaIsKept() = runTest {
        folders["/dav/"] = listing("/dav/", entry("/dav/Shows/", folder = true), entry("/dav/readme.txt"), entry("/dav/Film.mkv", size = 42))
        folders["/dav/Shows/"] = listing("/dav/Shows/", entry("/dav/Shows/Signal/", folder = true))
        folders["/dav/Shows/Signal/"] = listing("/dav/Shows/Signal/", entry("/dav/Shows/Signal/Season%201/", folder = true))
        folders["/dav/Shows/Signal/Season%201/"] = listing("/dav/Shows/Signal/Season%201/", entry("/dav/Shows/Signal/Season%201/Signal%20S01E01.mkv"))
        val files = share().files().toList()
        assertEquals(listOf("Film.mkv", "Shows/Signal/Season 1/Signal S01E01.mkv"), files.map { it.path })
        assertEquals(42, files.first().sizeBytes)
        assertTrue(requests.all { it.method == "PROPFIND" }, "nothing but listings is sent")
    }

    @Test
    fun namesAreDecodedOnceAndEncodedAgainForPlayback() = runTest {
        folders["/dav/"] = listing(
            "/dav/",
            entry("/dav/Am%C3%A9lie%20%282001%29.mkv"),
            entry("/dav/100%25%20Wolf%20%23%201+2.mkv"),
        )
        val source = share()
        val paths = source.files().toList().map { it.path }
        assertEquals(listOf("100% Wolf # 1+2.mkv", "Amélie (2001).mkv"), paths)
        assertEquals("http://nas.local/dav/100%25%20Wolf%20%23%201+2.mkv", source.playbackTarget(paths[0]).uri)
        assertEquals("http://nas.local/dav/Am%C3%A9lie%20(2001).mkv", source.playbackTarget(paths[1]).uri)
    }

    @Test
    fun aShareAtTheServersRootAndAnAddressWithASlashAreTheSameShare() = runTest {
        folders["/"] = listing("/", entry("/Film.mkv"))
        assertEquals(listOf("Film.mkv"), share("http://nas.local").files().toList().map { it.path })
        assertEquals("http://nas.local/Film.mkv", share("http://nas.local/").playbackTarget("Film.mkv").uri)
        assertEquals(share("http://nas.local/dav").descriptor.id, share("http://nas.local/dav/").descriptor.id)
    }

    @Test
    fun nothingAnAnswerNamesOutsideTheShareIsListedOrAskedFor() = runTest {
        folders["/dav/"] = listing(
            "/dav/",
            entry("/other/Film.mkv"),
            entry("/dav/../etc/Film.mkv"),
            entry("/dav/%2e%2e/Film.mkv"),
            entry("/dav/a%2Fb.mkv"),
            entry("https://elsewhere.example/steal/", folder = true),
            entry("/dav/Kept.mkv"),
        )
        assertEquals(listOf("Kept.mkv"), share().files().toList().map { it.path })
        assertTrue(requests.all { URI(it.url).host == "nas.local" }, "credentials never go to another host: ${requests.map { it.url }}")
        assertFailsWith<IllegalArgumentException> { share().playbackTarget("../etc/passwd") }
    }

    @Test
    fun aServerNamingItselfDifferentlyIsStillAskedAtTheShareAddress() = runTest {
        // A share behind a reverse proxy that reports its own internal address in hrefs.
        folders["/dav/"] = listing("/dav/", entry("http://localhost:8080/dav/Movies/", folder = true))
        folders["/dav/Movies/"] = listing("/dav/Movies/", entry("http://localhost:8080/dav/Movies/Heat.mkv"))
        assertEquals(listOf("Movies/Heat.mkv"), share().files().toList().map { it.path })
        assertEquals(listOf("http://nas.local/dav/", "http://nas.local/dav/Movies/"), requests.map { it.url })
    }

    @Test
    fun aWrongPasswordIsNotAShare() = runTest {
        val wrong = WebDavSource(WebDavConfig("http://nas.local/dav", "alex", "nope"), http)
        folders["/dav/"] = listing("/dav/")
        assertEquals(Availability.UNAVAILABLE, wrong.availability())
        assertFailsWith<SourceUnavailableException> { wrong.files().toList() }
        assertEquals(401, wrong.probe())
    }

    @Test
    fun aBrokenAnswerAtTheRootIsAShareThatIsNotThere() = runTest {
        answers["/dav/"] = HttpResult(207, "<D:multistatus xmlns:D=\"DAV:\"><D:response>".encodeToByteArray())
        assertEquals(Availability.UNAVAILABLE, share().availability())
        assertFailsWith<SourceUnavailableException> { share().files().toList() }
    }

    @Test
    fun aDeclaredDoctypeIsRefused() = runTest {
        // No entity in an answer is ever expanded (XXE).
        answers["/dav/"] = HttpResult(207, """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><D:multistatus xmlns:D="DAV:"/>""".encodeToByteArray())
        assertFailsWith<SourceUnavailableException> { share().files().toList() }
    }

    @Test
    fun aDocumentTypeIsFoundWithoutTheParsersHelp() {
        // Android's parser has no setting to refuse one, so the adapter looks itself.
        assertTrue(WebDavSource.declaresDoctype("<?xml version=\"1.0\"?><!doctype x><x/>".encodeToByteArray()))
        assertTrue(WebDavSource.declaresDoctype("<<!DOCTYPE x><x/>".encodeToByteArray()))
        assertTrue(WebDavSource.declaresDoctype("﻿<!DOCTYPE x><x/>".toByteArray(Charsets.UTF_16LE)))
        assertEquals(false, WebDavSource.declaresDoctype("<D:href>/dav/&lt;!DOCTYPE.mkv</D:href>".encodeToByteArray()))
    }

    @Test
    fun aBrokenAnswerBelowTheRootIsAFolderNotRead() = runTest {
        folders["/dav/"] = listing("/dav/", entry("/dav/Movies/", folder = true), entry("/dav/Film.mkv"))
        answers["/dav/Movies/"] = HttpResult(207, "not xml".encodeToByteArray())
        assertEquals(listOf(SourceFile("Film.mkv", 1, 0), SourceFile.folderNotRead("Movies")), share().files().toList())
    }

    @Test
    fun serverErrorsAndAnUnreachableShareAreNotThere() = runTest {
        answers["/dav/"] = HttpResult(503, ByteArray(0))
        assertEquals(Availability.UNAVAILABLE, share().availability())
        assertFailsWith<SourceUnavailableException> { share().files().toList() }

        val unreachable = WebDavSource(WebDavConfig("http://nas.local/dav"), HttpFetcher { throw java.net.ConnectException("Connection refused") })
        assertEquals(Availability.UNAVAILABLE, unreachable.availability())
        assertFailsWith<SourceUnavailableException> { unreachable.files().toList() }
    }
}
