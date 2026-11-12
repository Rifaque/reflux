package dev.reflux.sources.jellyfin

import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.profile.AccountDevices
import kotlin.test.assertNotEquals
import dev.reflux.core.net.HttpRequest
import dev.reflux.core.net.HttpResult
import dev.reflux.core.source.Connection
import dev.reflux.core.source.ConnectionProblem
import dev.reflux.core.source.ConnectionRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
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
    fun eachAccountSignsInAsItsOwnDeviceNeverTheInstallations() = runTest {
        val alex = JellyfinCredentials.decode(assertIs<Connection.Established>(connect("jf.local:8096")).config)!!
        val again = JellyfinCredentials.decode(assertIs<Connection.Established>(connect("http://jf.local:8096/", user = "ALEX")).config)!!
        val deviceIds = requests.map { Regex("DeviceId=\"([^\"]+)\"").find(it.headers["Authorization"].orEmpty())?.groupValues?.get(1) }
        assertEquals(AccountDevices.id("device-1", "jellyfin", "http://jf.local:8096", "alex"), alex.deviceId)
        assertEquals(alex.deviceId, again.deviceId, "the same account again is the same device, so the server's device list does not grow")
        assertTrue(deviceIds.all { it == alex.deviceId }, "every request of the sign-in is that device: $deviceIds")
        assertNotEquals("device-1", alex.deviceId, "never the installation's bare identity, which every account would share")
        assertNotEquals(alex.deviceId, AccountDevices.id("device-1", "jellyfin", "http://jf.local:8096", "sam"))
        assertEquals("u1", JellyfinConnector(server(), "device-1").account(alex.encode()), "a Jellyfin connection is one user's")
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

    private val signedIn = JellyfinCredentials("http://jf.local:8096", "srv1", "u1", "tok-1", "device-1", "Alex").encode()

    /** The server's answer to signing out, and nothing else. */
    private fun signOutAnswer(answer: (HttpRequest) -> HttpResult) = HttpFetcher { request ->
        requests += request
        if (request.url.substringBefore('?').endsWith("/Sessions/Logout")) answer(request) else error("unexpected ${request.method} ${request.url}")
    }

    @Test
    fun signingOutEndsThisDevicesSessionWithItsOwnTokenAndNothingElse() = runTest {
        val connector = JellyfinConnector(signOutAnswer { HttpResult(204, ByteArray(0)) }, "device-1", "Living room TV")
        assertTrue(connector.signOut(signedIn))
        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals("http://jf.local:8096/Sessions/Logout", request.url, "the server that issued the token, with nothing in the address")
        val authorization = request.headers.getValue("Authorization")
        assertTrue("Token=\"tok-1\"" in authorization, "the session is the one its own token names")
        assertTrue("DeviceId=\"device-1\"" in authorization)
        assertTrue(request.body == null || request.body!!.isEmpty(), "no body: no user, no session id, no password")
        assertFalse(request.headers.keys.any { it.equals("X-Emby-Token", ignoreCase = true) })
    }

    @Test
    fun aTokenTheServerNoLongerAcceptsIsAlreadySignedOut() = runTest {
        // Expired, revoked by an administrator, or replaced by a newer sign-in from this device.
        assertTrue(JellyfinConnector(signOutAnswer { HttpResult(401, ByteArray(0)) }, "device-1").signOut(signedIn))
    }

    @Test
    fun aSignOutThatIsNotConfirmedSaysSo() = runTest {
        val unreachable = HttpFetcher { error("no route to host") }
        assertEquals(ConnectionProblem.UNREACHABLE, JellyfinConnector.problemOf(assertFailsWith<JellyfinException> { JellyfinConnector(unreachable, "device-1").signOut(signedIn) }))
        val failing = assertFailsWith<JellyfinException> { JellyfinConnector(signOutAnswer { HttpResult(500, ByteArray(0)) }, "device-1").signOut(signedIn) }
        assertEquals(500, failing.status)
        // Something that is not Jellyfin answered (a captive portal, a proxy's page): that ended nothing.
        val page = signOutAnswer { HttpResult(200, "<html>Welcome</html>".encodeToByteArray()) }
        assertFailsWith<JellyfinException> { JellyfinConnector(page, "device-1").signOut(signedIn) }
        listOf(failing.message, failing.cause?.message).forEach { assertFalse(it.orEmpty().contains("tok-1"), "no token in what is reported") }
    }

    @Test
    fun aConnectionThatStaysInUseIsNotSignedOut() = runTest {
        val connector = JellyfinConnector(signOutAnswer { HttpResult(204, ByteArray(0)) }, "device-1")
        // A server may hand this device the same token again on signing in again; ending it would end the new one.
        assertFalse(connector.signOut(signedIn, keeping = signedIn.replace("Alex", "alex")))
        assertTrue(requests.isEmpty())
        assertTrue(connector.signOut(signedIn, keeping = signedIn.replace("tok-1", "tok-2")))
        assertTrue("Token=\"tok-1\"" in requests.single().headers.getValue("Authorization"), "the replaced token, never the new one")
    }

    @Test
    fun somethingThatIsNotAJellyfinConnectionHasNothingToSignOut() = runTest {
        assertFalse(JellyfinConnector(signOutAnswer { error("not asked") }, "device-1").signOut("garbage"))
        assertNull(requests.firstOrNull())
    }
}
