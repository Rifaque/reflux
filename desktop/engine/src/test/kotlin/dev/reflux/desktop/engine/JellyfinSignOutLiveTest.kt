package dev.reflux.desktop.engine

import dev.reflux.core.net.HttpRequest
import dev.reflux.core.source.ConnectionKind
import dev.reflux.core.source.ConnectionRequest
import dev.reflux.library.ConfigSeal
import dev.reflux.library.ConnectResult
import dev.reflux.library.SignOut
import dev.reflux.library.net.JdkHttpFetcher
import dev.reflux.sources.jellyfin.JellyfinCredentials
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Removing a Jellyfin server ends Reflux's session there, and only that one, against a real server.
 *
 * Runs when a server is named, as [JellyfinLiveTest] does ([LiveJellyfin]). It signs the test user in twice:
 * once as a Reflux installation of its own (a fresh data directory, so a device of its own) and once as
 * another client. It changes no watch state and leaves no session behind. Nothing it reads from the server
 * (tokens, session ids) is printed: assertions name what they check, not values.
 */
class JellyfinSignOutLiveTest {
    private val http = JdkHttpFetcher(userAgent = "Reflux live test")

    private fun authorization(deviceId: String, token: String? = null) =
        "MediaBrowser Client=\"Reflux live test\", Device=\"live test\", DeviceId=\"$deviceId\", Version=\"1\"" +
            token?.let { ", Token=\"$it\"" }.orEmpty()

    private suspend fun status(url: String, path: String, deviceId: String, token: String, method: String = "GET"): Int =
        http.send(HttpRequest("$url/$path", method, mapOf("Authorization" to authorization(deviceId, token)))).status

    /** The device ids of the sessions the server lists for [token]'s user, as that user may see them. */
    private suspend fun sessionDevices(url: String, deviceId: String, token: String): List<String> {
        val result = http.send(HttpRequest("$url/Sessions", headers = mapOf("Authorization" to authorization(deviceId, token))))
        assertEquals(200, result.status, "the server lists sessions")
        return (Json.parseToJsonElement(result.text()) as JsonArray).mapNotNull { ((it as JsonObject)["DeviceId"] as? JsonPrimitive)?.contentOrNull }
    }

    /** Signs in as another client of the same user (a browser, a phone app), as such a client would. */
    private suspend fun otherClient(url: String, user: String, password: String, deviceId: String): String {
        val body = """{"Username":${JsonPrimitive(user)},"Pw":${JsonPrimitive(password)}}""".encodeToByteArray()
        val result = http.send(
            HttpRequest("$url/Users/AuthenticateByName", "POST", mapOf("Authorization" to authorization(deviceId), "Content-Type" to "application/json"), body),
        )
        assertEquals(200, result.status, "the other client signs in")
        return ((Json.parseToJsonElement(result.text()) as JsonObject)["AccessToken"] as JsonPrimitive).content
    }

    @Test
    fun removingTheServerEndsThisSessionAndNoOther() = runBlocking {
        val live = LiveJellyfin.settings()
        assumeTrue("no Jellyfin server named (see LiveJellyfin)", live != null)
        live!!
        val url = live.url.trimEnd('/')
        val data = Files.createTempDirectory("reflux-signout-live")
        val otherDevice = "reflux-live-other-client-${java.util.UUID.randomUUID()}"
        val other = otherClient(url, live.user, live.password, otherDevice)
        try {
            // The system's own credential store, as the application uses it.
            val environment = DesktopEnvironment(data)
            val request = ConnectionRequest(live.url, live.user, live.password)
            val connected = assertIs<ConnectResult.Connected>(environment.connections.connect(ConnectionKind.SERVER, request))
            val record = environment.library.source(connected.sourceId)!!
            val first = JellyfinCredentials.decode(environment.connections.open(record))!!
            assertEquals(200, status(url, "Users/Me", environment.deviceId, first.accessToken), "Reflux's session works")
            assertTrue(environment.deviceId in sessionDevices(url, otherDevice, other), "the server lists Reflux's session")

            val signOut = environment.removeSource(connected.sourceId)
            assertNull(environment.library.source(connected.sourceId), "removed here")
            assertNull(environment.registry.resolve(connected.sourceId))
            // Only asked of a real store: without one, opening hands back what it is given. Never compared by value,
            // which would print a live token in the failure.
            val seal = DesktopSecrets.seal(data)
            if (seal !== ConfigSeal.None) assertTrue(seal.open(connected.sourceId, record.config).isEmpty(), "and its token is gone from the credential store")
            assertEquals(SignOut.ENDED, signOut.await(), "the server confirmed the end")

            // What the server says now, asked directly.
            assertEquals(401, status(url, "Users/Me", environment.deviceId, first.accessToken), "the removed token no longer works")
            assertFalse(environment.deviceId in sessionDevices(url, otherDevice, other), "the server no longer lists Reflux's session")
            assertEquals(200, status(url, "Users/Me", otherDevice, other), "the other client is still signed in")
            assertTrue(otherDevice in sessionDevices(url, otherDevice, other), "and still listed")

            // Adding it again is a new session.
            val again = assertIs<ConnectResult.Connected>(environment.connections.connect(ConnectionKind.SERVER, request))
            val second = JellyfinCredentials.decode(environment.connections.open(environment.library.source(again.sourceId)!!))!!
            assertNotEquals(first.accessToken, second.accessToken, "a fresh token")
            assertEquals(200, status(url, "Users/Me", environment.deviceId, second.accessToken), "which works")
            assertEquals(401, status(url, "Users/Me", environment.deviceId, first.accessToken), "while the old one stays ended")

            // Leaving nothing behind.
            assertEquals(SignOut.ENDED, environment.removeSource(again.sourceId).await())
            assertEquals(401, status(url, "Users/Me", environment.deviceId, second.accessToken))
            val log = data.resolve("reflux.log")
            if (Files.exists(log)) {
                val text = Files.readString(log)
                assertFalse(first.accessToken in text || second.accessToken in text || other in text, "no token in the log")
            }
        } finally {
            status(url, "Sessions/Logout", otherDevice, other, method = "POST")
        }
    }
}
