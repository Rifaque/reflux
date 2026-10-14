package dev.reflux.sources.jellyfin

import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.net.HttpRequest
import dev.reflux.core.net.HttpResult
import dev.reflux.core.source.Connection
import dev.reflux.core.source.ConnectionProblem
import dev.reflux.core.source.ConnectionRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Signing in from what a user types, and every way that can go wrong, told apart. */
class JellyfinConnectorTest {
    private val requests = mutableListOf<HttpRequest>()

    /** A server at jf.local:8096 whose only account is alex / secret. */
    private fun server(answer: (HttpRequest) -> HttpResult? = { null }) = HttpFetcher { request ->
        requests += request
        answer(request)?.let { return@HttpFetcher it }
        if (!request.url.startsWith("http://jf.local:8096/")) error("no route to host")
        when (request.url.removePrefix("http://jf.local:8096/").substringBefore('?')) {
            "System/Info/Public" -> HttpResult(200, """{"ServerName":"Living Room","Version":"10.10.3","Id":"srv1"}""".encodeToByteArray())
            "Users/AuthenticateByName" ->
                if (request.body!!.decodeToString().contains("\"Pw\":\"secret\"")) {
                    HttpResult(200, """{"AccessToken":"tok","ServerId":"srv1","User":{"Id":"u1","Name":"Alex"}}""".encodeToByteArray())
                } else {
                    HttpResult(401, ByteArray(0))
                }
            else -> HttpResult(404, ByteArray(0))
        }
    }

    private suspend fun connect(address: String, user: String? = "alex", password: String? = "secret", http: HttpFetcher = server()) =
        JellyfinConnector(http, "device-1").connect(ConnectionRequest(address, user, password))

    @Test
    fun signsInWithTheAddressAsTypedAndKeepsNoPassword() = runTest {
        val connection = assertIs<Connection.Established>(connect("jf.local:8096/"))
        assertEquals("Living Room", connection.source.descriptor.displayName, "named as the server names itself")
        assertFalse("secret" in connection.config, "the password is used to sign in and then forgotten")
        val credentials = JellyfinCredentials.decode(connection.config)!!
        assertEquals("tok", credentials.accessToken)
        assertEquals(ConnectionRequest("http://jf.local:8096", "Alex"), JellyfinConnector(server(), "d").describe(connection.config))
    }

    @Test
    fun eachFailureSaysWhatTheUserCanDoAboutIt() = runTest {
        assertEquals(ConnectionProblem.INVALID_ADDRESS, (connect("http://jf local") as Connection.Failed).problem)
        assertEquals(ConnectionProblem.INVALID_ADDRESS, (connect("http://user:pw@jf.local:8096") as Connection.Failed).problem, "credentials never go in an address")
        assertEquals(ConnectionProblem.SIGN_IN_REQUIRED, (connect("jf.local:8096", user = " ") as Connection.Failed).problem)
        assertEquals(ConnectionProblem.UNREACHABLE, (connect("nas.local:8096") as Connection.Failed).problem)
        assertEquals(ConnectionProblem.AUTHENTICATION, (connect("jf.local:8096", password = "wrong") as Connection.Failed).problem)
        // Something answers at the address, but it is not Jellyfin: a router's page, another service.
        val webPage = server { if ("System/Info" in it.url) HttpResult(200, "<html>Router</html>".encodeToByteArray()) else null }
        assertEquals(ConnectionProblem.NOT_FOUND, (connect("jf.local:8096", http = webPage) as Connection.Failed).problem)
        val elsewhere = server { HttpResult(404, ByteArray(0)) }
        assertEquals(ConnectionProblem.NOT_FOUND, (connect("jf.local:8096", http = elsewhere) as Connection.Failed).problem)
        val starting = server { HttpResult(503, ByteArray(0)) }
        assertEquals(ConnectionProblem.SERVER_ERROR, (connect("jf.local:8096", http = starting) as Connection.Failed).problem)
    }

    @Test
    fun aServerThatWasNeverNamedIsCalledJellyfin() {
        assertEquals("Living Room", JellyfinConnector.nameOf("Living Room"))
        assertEquals("Jellyfin", JellyfinConnector.nameOf("4f2331b42400"), "a container's generated host name is not a name")
        assertEquals("Jellyfin", JellyfinConnector.nameOf(null))
        assertEquals("nas", JellyfinConnector.nameOf("nas"))
    }

    @Test
    fun aFailureCarriesNoCredentialsForTheLog() = runTest {
        val failed = assertIs<Connection.Failed>(connect("jf.local:8096", password = "hunter2"))
        assertTrue(failed.detail.orEmpty().let { "hunter2" !in it && "tok" !in it })
    }
}
