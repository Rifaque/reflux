package dev.reflux.sources.webdav

import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.net.HttpResult
import dev.reflux.core.source.Connection
import dev.reflux.core.source.ConnectionProblem
import dev.reflux.core.source.ConnectionRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebDavConnectorTest {
    private val multistatus = """<?xml version="1.0"?><D:multistatus xmlns:D="DAV:"><D:response><D:href>/dav/</D:href><D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response></D:multistatus>"""

    /** A share at nas.local/dav that wants alex / secret. */
    private val share = HttpFetcher { request ->
        if (!request.url.startsWith("https://nas.local/dav")) error("unreachable")
        val auth = request.headers["Authorization"]
        when {
            request.url.startsWith("https://nas.local/dav/private") -> HttpResult(403, ByteArray(0))
            auth == null -> HttpResult(401, ByteArray(0))
            auth != "Basic YWxleDpzZWNyZXQ=" -> HttpResult(401, ByteArray(0))
            else -> HttpResult(207, multistatus.encodeToByteArray())
        }
    }

    private suspend fun connect(address: String, user: String? = "alex", password: String? = "secret") =
        WebDavConnector(share).connect(ConnectionRequest(address, user, password))

    @Test
    fun onlyWebAddressesAreAWebShare() {
        val connector = WebDavConnector(share)
        assertTrue(connector.accepts("https://nas.local/dav"))
        assertTrue(connector.accepts("davs://nas.local/dav"))
        assertFalse(connector.accepts("\\\\nas\\Movies"))
        assertFalse(connector.accepts("nas/Movies"))
        assertEquals("https://nas.local/My%20Media", WebDavConnector.normalize("davs://nas.local/My Media/"))
        assertNull(WebDavConnector.normalize("https://alex:secret@nas.local/dav"), "credentials have their own fields")
    }

    @Test
    fun connectsAndToldApartWhenItCannot() = runTest {
        val connected = assertIs<Connection.Established>(connect("https://nas.local/dav/"))
        assertEquals(ConnectionRequest("https://nas.local/dav", "alex"), WebDavConnector(share).describe(connected.config))
        assertEquals(ConnectionProblem.SIGN_IN_REQUIRED, (connect("https://nas.local/dav", null, null) as Connection.Failed).problem)
        assertEquals(ConnectionProblem.AUTHENTICATION, (connect("https://nas.local/dav", password = "nope") as Connection.Failed).problem)
        assertEquals(ConnectionProblem.PERMISSION_DENIED, (connect("https://nas.local/dav/private") as Connection.Failed).problem)
        assertEquals(ConnectionProblem.UNREACHABLE, (connect("https://elsewhere.local/dav") as Connection.Failed).problem)
        assertEquals(ConnectionProblem.INVALID_ADDRESS, (connect("ftp://nas.local/dav") as Connection.Failed).problem)
        assertEquals(ConnectionProblem.NOT_FOUND, WebDavConnector.problemOf(404, signedIn = true))
        assertEquals(ConnectionProblem.NOT_FOUND, WebDavConnector.problemOf(200, signedIn = true), "a web page is not a share")
    }
}
