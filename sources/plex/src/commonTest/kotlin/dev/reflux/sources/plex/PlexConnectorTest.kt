package dev.reflux.sources.plex

import dev.reflux.core.source.Connection
import dev.reflux.core.source.ConnectionKind
import dev.reflux.core.source.ConnectionProblem
import dev.reflux.core.source.ConnectionRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Connecting a Plex server: by account, by token, or with nothing where the server lets the network in. */
class PlexConnectorTest {
    private val plex = FakePlex()
    private val connector = PlexConnector(plex.http, "dev-1", "Reflux on test")

    private suspend fun connect(address: String = "192.168.1.20", user: String? = null, password: String? = null) =
        connector.connect(ConnectionRequest(address, user, password))

    @Test
    fun itIsAServerNamedPlexThatMayNeedNoSignIn() {
        assertEquals("plex", connector.type)
        assertEquals(ConnectionKind.SERVER, connector.kind)
        assertEquals("Plex", connector.product)
        assertTrue(connector.anonymous, "a server that lets the network in is added without an account")
    }

    @Test
    fun anAccountSignsInAtPlexTvOnceAndOnlyTheTokenIsKept() = runTest {
        val connection = assertIs<Connection.Established>(connect(user = "alex@example.com", password = "secret"))
        assertFalse("secret" in connection.config, "the password is not kept")
        val credentials = PlexCredentials.decode(connection.config)!!
        assertEquals("srv-token", credentials.token)
        assertEquals("machine-1", credentials.machineId)
        assertEquals("alex", credentials.account)
        assertEquals("http://192.168.1.20:32400", credentials.serverUrl)
        assertEquals("Living Room", connection.source.descriptor.displayName)
        assertEquals(ConnectionRequest("http://192.168.1.20:32400", "alex"), connector.describe(connection.config))
        val signIn = plex.requests.first { "users/signin" in it.url }
        assertEquals("dev-1", signIn.headers["X-Plex-Client-Identifier"])
        assertEquals("Reflux", signIn.headers["X-Plex-Product"])
        assertFalse(plex.requests.any { "secret" in it.url }, "the password never travels in an address")
    }

    @Test
    fun aServerSharedWithTheAccountTakesTheTokenPlexTvListsForIt() = runTest {
        plex.accountToken = "account-token"
        plex.sharedToken = "srv-token"
        val connection = assertIs<Connection.Established>(connect(user = "alex@example.com", password = "secret"))
        assertEquals("srv-token", PlexCredentials.decode(connection.config)!!.token)
    }

    @Test
    fun aTokenCanBeGivenAsThePasswordAndNothingIsNeededWhereTheServerTrustsTheNetwork() = runTest {
        val byToken = assertIs<Connection.Established>(connect(password = "srv-token"))
        assertEquals("srv-token", PlexCredentials.decode(byToken.config)!!.token)
        assertNull(PlexCredentials.decode(byToken.config)!!.account)
        assertFalse(plex.requests.any { it.url.startsWith("https://plex.tv") }, "plex.tv is not needed for a token")
        plex.serverToken = null
        val open = assertIs<Connection.Established>(connect())
        assertNull(PlexCredentials.decode(open.config)!!.token)
        assertEquals(byToken.source.descriptor.id, open.source.descriptor.id, "the same server without an account is the same source")
    }

    @Test
    fun everyFailureIsAProblemTheViewerCanActOn() = runTest {
        assertEquals(ConnectionProblem.SIGN_IN_REQUIRED, assertIs<Connection.Failed>(connect()).problem, "a server that wants a sign-in, given none")
        assertEquals(ConnectionProblem.AUTHENTICATION, assertIs<Connection.Failed>(connect(user = "alex@example.com", password = "wrong")).problem)
        assertEquals(ConnectionProblem.AUTHENTICATION, assertIs<Connection.Failed>(connect(password = "not-a-token")).problem)
        assertEquals(ConnectionProblem.INVALID_ADDRESS, assertIs<Connection.Failed>(connect(address = "http://bad host")).problem)
        plex.plexTvOnline = false
        assertEquals(ConnectionProblem.UNREACHABLE, assertIs<Connection.Failed>(connect(user = "alex@example.com", password = "secret")).problem)
        plex.online = false
        assertEquals(ConnectionProblem.UNREACHABLE, assertIs<Connection.Failed>(connect(password = "srv-token")).problem)
    }

    @Test
    fun somethingThatIsNotAPlexServerIsSaidToBeNotFound() = runTest {
        val other = PlexConnector({ dev.reflux.core.net.HttpResult(404, ByteArray(0)) }, "dev-1")
        assertEquals(ConnectionProblem.NOT_FOUND, assertIs<Connection.Failed>(other.connect(ConnectionRequest("192.168.1.30:8096"))).problem)
    }

    @Test
    fun aContainersGeneratedHostNameIsNotAName() {
        assertEquals("Plex", PlexConnector.nameOf("2aad07e0258e"))
        assertEquals("Living Room", PlexConnector.nameOf(" Living Room "))
        assertEquals("Plex", PlexConnector.nameOf(""))
    }
}
