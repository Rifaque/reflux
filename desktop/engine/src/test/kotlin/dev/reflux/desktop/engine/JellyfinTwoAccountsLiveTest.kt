package dev.reflux.desktop.engine

import dev.reflux.core.net.HttpRequest
import dev.reflux.core.source.Connection
import dev.reflux.core.source.ConnectionRequest
import dev.reflux.library.net.JdkHttpFetcher
import dev.reflux.sources.jellyfin.JellyfinConnector
import dev.reflux.sources.jellyfin.JellyfinCredentials
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Two accounts on one real Jellyfin server, from one Reflux installation (M8; docs/WATCH_STATE.md, "Profiles").
 *
 * Observes, and does not assume, what the server does when two accounts sign in from one device identity (how every
 * account signed in before M8), and checks what per-account identities guarantee: both accounts stay signed in, and
 * finding "this device's session" by its identity (how the playback truth layer reads the server) never resolves the
 * other account's session.
 *
 * It creates a second user and deletes it again, so it runs only against a throwaway server whose administrator is
 * named: `REFLUX_JELLYFIN_ADMIN_URL`, `REFLUX_JELLYFIN_ADMIN_USER`, `REFLUX_JELLYFIN_ADMIN_PASSWORD`
 * (`tools/ci/jellyfin-test-server.sh` starts one, administrator `reflux` / `reflux-test`). Never a server of the
 * user's own. What it observed is written to `build/live/jellyfin-two-accounts.txt` for VERIFICATION.md, without a
 * token or a password in it.
 */
class JellyfinTwoAccountsLiveTest {
    private val http = JdkHttpFetcher(userAgent = "Reflux live test")
    private val url = System.getenv("REFLUX_JELLYFIN_ADMIN_URL")?.trimEnd('/')
    private val admin = System.getenv("REFLUX_JELLYFIN_ADMIN_USER")
    private val adminPassword = System.getenv("REFLUX_JELLYFIN_ADMIN_PASSWORD")

    private fun authorization(deviceId: String, token: String? = null) =
        "MediaBrowser Client=\"Reflux live test\", Device=\"live test\", DeviceId=\"$deviceId\", Version=\"1\"" + token?.let { ", Token=\"$it\"" }.orEmpty()

    private suspend fun send(path: String, deviceId: String, token: String?, method: String = "GET", body: String? = null) =
        http.send(HttpRequest("$url/$path", method, mapOf("Authorization" to authorization(deviceId, token), "Content-Type" to "application/json"), body?.encodeToByteArray()))

    private suspend fun signIn(user: String, password: String, deviceId: String): String {
        val result = send("Users/AuthenticateByName", deviceId, null, "POST", """{"Username":${JsonPrimitive(user)},"Pw":${JsonPrimitive(password)}}""")
        assertEquals(200, result.status, "$user signs in")
        return ((Json.parseToJsonElement(result.text()) as JsonObject)["AccessToken"] as JsonPrimitive).content
    }

    private suspend fun works(token: String, deviceId: String) = send("Users/Me", deviceId, token).status == 200

    /** The users of the sessions the server lists under [deviceId], as [token]'s user sees them: what "this device's session" resolves to. */
    private suspend fun sessionUsers(deviceId: String, asDevice: String, token: String): List<String> {
        val result = send("Sessions", asDevice, token)
        if (result.status != 200) return listOf("(sessions not readable: ${result.status})")
        return (Json.parseToJsonElement(result.text()) as JsonArray).map { it as JsonObject }
            .filter { (it["DeviceId"] as? JsonPrimitive)?.contentOrNull == deviceId }
            .map { (it["UserName"] as? JsonPrimitive)?.contentOrNull ?: "?" }
    }

    private suspend fun playing(itemId: String, deviceId: String, token: String) =
        send("Sessions/Playing", deviceId, token, "POST", """{"ItemId":"$itemId","PositionTicks":0,"CanSeek":true,"PlayMethod":"DirectPlay"}""").status

    @Test
    fun twoAccountsFromOneInstallationNeverShareASession() = runBlocking {
        assumeTrue("no throwaway Jellyfin server's administrator named (REFLUX_JELLYFIN_ADMIN_*)", url != null && admin != null && adminPassword != null)
        val setup = "reflux-live-setup-${UUID.randomUUID()}"
        val adminToken = signIn(admin!!, adminPassword!!, setup)
        val second = "reflux-second-${UUID.randomUUID().toString().take(8)}"
        val secondPassword = UUID.randomUUID().toString()
        val created = send("Users/New", setup, adminToken, "POST", """{"Name":"$second","Password":"$secondPassword"}""")
        assertEquals(200, created.status, "a second account is made for the test")
        val secondId = ((Json.parseToJsonElement(created.text()) as JsonObject)["Id"] as JsonPrimitive).content
        val items = Json.parseToJsonElement(send("Items?Recursive=true&IncludeItemTypes=Movie", setup, adminToken).text()) as JsonObject
        val movie = (((items["Items"] as JsonArray).first() as JsonObject)["Id"] as JsonPrimitive).content
        val observed = mutableListOf<String>()
        val server = (Json.parseToJsonElement(send("System/Info/Public", setup, null).text()) as JsonObject)["Version"]?.let { (it as JsonPrimitive).content }
        observed += "Jellyfin $server, accounts A (administrator) and B (made for the test)"
        try {
            // 1. One identity for both accounts, as every Reflux installation had before M8.
            val shared = "reflux-live-shared-${UUID.randomUUID()}"
            val a1 = signIn(admin, adminPassword, shared)
            observed += "shared identity: A signed in; A's playback report answered ${playing(movie, shared, a1)}"
            val aSessionsBefore = sessionUsers(shared, setup, adminToken)
            val b1 = signIn(second, secondPassword, shared)
            observed += "shared identity: B signed in from the same identity"
            observed += "shared identity: A's token still works after B's sign-in: ${works(a1, shared)}"
            observed += "shared identity: B's token works: ${works(b1, shared)}"
            observed += "shared identity: sessions listed under the identity before B: $aSessionsBefore; after B: ${sessionUsers(shared, setup, adminToken)}"
            if (works(a1, shared)) observed += "shared identity: \"this device's session\" as A finds it resolves to: ${sessionUsers(shared, shared, a1)}"
            observed += "shared identity: \"this device's session\" as B finds it resolves to: ${sessionUsers(shared, shared, b1)}"
            send("Sessions/Logout", shared, a1, "POST")
            send("Sessions/Logout", shared, b1, "POST")

            // 2. Each account its own identity, as the connector signs in since M8.
            val installation = "reflux-live-${UUID.randomUUID()}"
            val connector = JellyfinConnector(http, installation, "Reflux live test")
            fun credentials(connection: Connection) = JellyfinCredentials.decode(assertIs<Connection.Established>(connection).config)!!
            val a = credentials(connector.connect(ConnectionRequest(url!!, admin, adminPassword)))
            assertEquals(204, playing(movie, a.deviceId, a.accessToken).let { if (it == 200) 204 else it }, "A's playback report is accepted")
            val b = credentials(connector.connect(ConnectionRequest(url, second, secondPassword)))
            assertNotEquals(a.deviceId, b.deviceId, "each account signs in as its own device")
            assertTrue(works(a.accessToken, a.deviceId), "A is still signed in after B signs in")
            assertTrue(works(b.accessToken, b.deviceId), "and B is signed in")
            val asA = sessionUsers(a.deviceId, a.deviceId, a.accessToken)
            assertTrue(asA.isNotEmpty() && asA.all { it == admin }, "A's \"this device's session\" is A's only: $asA")
            val bUnderA = sessionUsers(b.deviceId, a.deviceId, a.accessToken)
            assertTrue(bUnderA.none { it == admin }, "B's identity never resolves to A's session: $bUnderA")
            val asB = sessionUsers(b.deviceId, b.deviceId, b.accessToken)
            assertTrue(asB.all { it == second }, "B's \"this device's session\" is never A's: $asB")
            assertTrue(sessionUsers(a.deviceId, b.deviceId, b.accessToken).none { it == second }, "and A's identity never resolves to B's session")
            observed += "per-account identities: A and B both signed in at once: true; A's session found by A's identity: $asA; by B's identity as A: $bUnderA; B's session by B's identity: $asB"
            assertTrue(connector.signOut(a.encode()))
            assertTrue(connector.signOut(b.encode()))
            observed += "per-account identities: both signed out; A's token after: ${works(a.accessToken, a.deviceId)}, B's: ${works(b.accessToken, b.deviceId)}"
        } finally {
            send("Users/$secondId", setup, adminToken, "DELETE")
            send("Sessions/Logout", setup, adminToken, "POST")
            val out = Path.of("build", "live", "jellyfin-two-accounts.txt")
            Files.createDirectories(out.parent)
            Files.writeString(out, observed.joinToString("\n") { line -> line.replace(secondPassword, "***").replace(adminPassword, "***") } + "\n")
        }
    }
}
