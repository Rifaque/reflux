package dev.reflux.sources.plex

import dev.reflux.core.profile.AccountDevices
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
        // The account signs in as a device of its own, derived from this installation's identity (M8), and keeps it.
        val identity = AccountDevices.id("dev-1", "plex", "http://192.168.1.20:32400", "alex@example.com")
        assertEquals(identity, signIn.headers["X-Plex-Client-Identifier"])
        assertEquals(identity, credentials.deviceId)
        assertTrue(plex.requests.all { it.headers["X-Plex-Client-Identifier"] == identity }, "every request of this sign-in")
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
        assertTrue(byToken.source.descriptor.id != open.source.descriptor.id, "a token is someone's account: it is never the source everyone shares without signing in")
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

    @Test
    fun twoAccountsNeverShareAClientIdentityAndAServerWithoutSignInHasItsOwn() = runTest {
        fun identity(connection: Connection) = PlexCredentials.decode(assertIs<Connection.Established>(connection).config)!!.deviceId
        val alex = identity(connect(user = "alex@example.com", password = "secret"))
        val token = identity(connect(password = "srv-token"))
        plex.serverToken = null
        val open = identity(connect())
        // Another plex.tv account signing in from here is another device, by the same rule.
        val sam = AccountDevices.id("dev-1", "plex", "http://192.168.1.20:32400", "sam@example.com")
        assertEquals(4, setOf(alex, sam, token, open).size, "each connection is its own device to Plex")
        assertTrue(listOf(alex, sam, token, open).none { it == "dev-1" }, "and none is the installation's bare identity")
        assertEquals(alex, identity(connect(user = "alex@example.com", password = "secret")), "the same account again is the same device")
        // Two different tokens are two accounts, and two devices; the token itself is never in the identity.
        plex.serverToken = null
        val otherToken = identity(connect(password = "another-token"))
        assertTrue(otherToken != token, "two tokens never share an identity")
        assertTrue("srv-token" !in token && "another-token" !in otherToken)
        assertEquals("token", connector.account(PlexCredentials.decode(PlexCredentials("http://x", "m", "t", "d").encode())!!.encode()))
        assertNull(connector.account(PlexCredentials("http://x", "m", null, "d").encode()), "a server open without signing in is nobody's account")
    }

    @Test
    fun twoTokensOnOneServerAreTwoSourcesAndNeverCollide() = runTest {
        plex.serverToken = null // the server takes any token here: what matters is which source each becomes
        fun established(connection: Connection) = assertIs<Connection.Established>(connection)
        val first = established(connect(password = "token-of-alex"))
        val second = established(connect(password = "token-of-sam"))
        val open = established(connect())
        val ids = listOf(first, second, open).map { it.source.descriptor.id }
        assertEquals(3, ids.toSet().size, "two tokens and the server open to the network are three sources: $ids")
        assertEquals(first.source.descriptor.id, established(connect(password = "token-of-alex")).source.descriptor.id, "the same token again is the same source")
        // The identity survives being stored and read back, which is how the registry recreates the source.
        assertEquals(first.source.descriptor.id, PlexSource.idOf(PlexCredentials.decode(first.config)!!))
        assertEquals(second.source.descriptor.id, PlexSource.idOf(PlexCredentials.decode(second.config)!!))
        // No token, and nothing a token could be read from, in an identifier.
        for (id in ids) assertTrue("token-of" !in id.value && "alex" !in id.value && "sam" !in id.value, id.value)
        assertTrue(PlexCredentials.decode(first.config)!!.deviceId != PlexCredentials.decode(second.config)!!.deviceId, "and each is its own device to the server")
    }

    @Test
    fun aTokenConnectionStoredBeforeKeepsTheIdentityItHas() {
        // A connection from before M8 has no marker: it is still the source its library rows name.
        val stored = "server=http://192.168.1.20:32400\nmachine=machine-1\ntoken=old-token\ndevice=dev-1"
        val legacy = PlexCredentials.decode(stored)!!
        assertEquals(PlexSource.idOf(PlexCredentials("http://192.168.1.20:32400", "machine-1", null, "dev-1")), PlexSource.idOf(legacy))
        assertEquals(stored, legacy.encode(), "and it is stored as it was")
    }
}

