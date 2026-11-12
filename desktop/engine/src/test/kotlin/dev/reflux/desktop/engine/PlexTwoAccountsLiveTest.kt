package dev.reflux.desktop.engine

import dev.reflux.core.net.HttpRequest
import dev.reflux.core.source.ConnectionRequest
import dev.reflux.library.net.JdkHttpFetcher
import dev.reflux.core.source.ConnectionKind
import dev.reflux.library.ConnectResult
import dev.reflux.library.activeProfile
import dev.reflux.library.createProfile
import dev.reflux.library.endpoints
import dev.reflux.library.switchProfile
import dev.reflux.library.userSources
import kotlin.test.assertEquals
import dev.reflux.sources.plex.PlexCredentials
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Two accounts on one real Plex Media Server, from one Reflux installation (M8; docs/WATCH_STATE.md, "Profiles"): the
 * Plex half of [JellyfinTwoAccountsLiveTest]. It observes what the server does when two accounts use one client
 * identity (how every account signed in before M8), and checks that per-account identities keep both signed in and
 * that "this device's session" never resolves to the other account's. The second half signs in through the application's own connections as two profiles, so it exercises the source identity a token gets (`PlexSource.idOf`), not the connector alone.
 *
 * Needs two plex.tv accounts (or their tokens) that can reach the server, which only the owner can provide:
 * `REFLUX_PLEX_URL`, `REFLUX_PLEX_TOKEN_A`, `REFLUX_PLEX_TOKEN_B`, in the environment or in `~/.reflux-live/plex.env`.
 * Without them it is skipped, and VERIFICATION.md says the live run is owner-pending; the per-account identity is
 * covered meanwhile by `PlexConnectorTest`. What it observed goes to `build/live/plex-two-accounts.txt`, without tokens.
 */
class PlexTwoAccountsLiveTest {
    private val http = JdkHttpFetcher(userAgent = "Reflux live test")
    private val settings: Map<String, String> = buildMap {
        val file = Path.of(System.getProperty("user.home"), ".reflux-live", "plex.env")
        if (Files.exists(file)) Files.readAllLines(file).filter { '=' in it && !it.startsWith("#") }.forEach { put(it.substringBefore('=').trim(), it.substringAfter('=').trim()) }
        listOf("REFLUX_PLEX_URL", "REFLUX_PLEX_TOKEN_A", "REFLUX_PLEX_TOKEN_B").forEach { key -> System.getenv(key)?.let { put(key, it) } }
    }

    private suspend fun get(url: String, path: String, client: String, token: String) =
        http.send(HttpRequest("${url.trimEnd('/')}/$path", headers = mapOf("X-Plex-Client-Identifier" to client, "X-Plex-Token" to token, "Accept" to "application/json", "X-Plex-Product" to "Reflux")))

    private suspend fun works(url: String, client: String, token: String) = get(url, "library/sections", client, token).status == 200

    /** The accounts of the sessions the server lists for player [client], as [token]'s account sees them. */
    private suspend fun sessionUsers(url: String, client: String, token: String): List<String> {
        val text = get(url, "status/sessions", client, token).text()
        return Regex("\"machineIdentifier\"\\s*:\\s*\"${Regex.escape(client)}\"").findAll(text).map { match ->
            Regex("\"title\"\\s*:\\s*\"([^\"]*)\"").findAll(text.substring(0, match.range.first)).lastOrNull()?.groupValues?.get(1) ?: "?"
        }.toList()
    }

    @Test
    fun twoAccountsFromOneInstallationNeverShareASession() = runBlocking {
        val url = settings["REFLUX_PLEX_URL"]
        val a = settings["REFLUX_PLEX_TOKEN_A"]
        val b = settings["REFLUX_PLEX_TOKEN_B"]
        assumeTrue("owner-pending: no Plex server and two account tokens named (REFLUX_PLEX_*)", url != null && a != null && b != null)
        val observed = mutableListOf<String>()
        try {
            // 1. One client identity for both accounts, as before M8.
            val shared = "reflux-live-shared-${UUID.randomUUID()}"
            observed += "shared identity: A works ${works(url!!, shared, a!!)}, then B works ${works(url, shared, b!!)}, then A still works ${works(url, shared, a)}"
            observed += "shared identity: sessions for the identity as A: ${sessionUsers(url, shared, a)}; as B: ${sessionUsers(url, shared, b)}"
            // 2. As Reflux itself signs in since M8: two profiles of one installation, each connecting its own token
            // through the application's own connections (never the connector alone), on a real data directory.
            val environment = DesktopEnvironment(Files.createTempDirectory("reflux-plex-two-accounts"))
            val library = environment.library
            val alex = library.activeProfile()
            val connectedA = assertIs<ConnectResult.Connected>(environment.connections.connect(ConnectionKind.SERVER, ConnectionRequest(url, null, a)))
            val sam = library.createProfile("Sam")
            library.switchProfile(sam)
            val connectedB = assertIs<ConnectResult.Connected>(environment.connections.connect(ConnectionKind.SERVER, ConnectionRequest(url, null, b)), "the second token is its own account, not the first's in another profile")
            try {
                assertNotEquals(connectedA.sourceId, connectedB.sourceId, "two tokens are two sources")
                assertEquals(alex, library.source(connectedA.sourceId)?.profileId)
                assertEquals(sam, library.source(connectedB.sourceId)?.profileId)
                assertEquals(1, library.endpoints().size, "one server address, shared")
                val first = PlexCredentials.decode(environment.connections.open(library.source(connectedA.sourceId)!!))!!
                val second = PlexCredentials.decode(environment.connections.open(library.source(connectedB.sourceId)!!))!!
                assertNotEquals(first.deviceId, second.deviceId, "each account is its own device to Plex")
                assertTrue(a !in connectedA.sourceId.value && b !in connectedB.sourceId.value, "no token in an identifier")
                assertTrue(works(url, first.deviceId, a) && works(url, second.deviceId, b), "both accounts are signed in at once")
                // Each profile sees its own account and never the other's.
                assertEquals(listOf(connectedB.sourceId), library.userSources().map { it.id })
                library.switchProfile(alex)
                assertEquals(listOf(connectedA.sourceId), library.userSources().map { it.id })
                val asA = sessionUsers(url, first.deviceId, a)
                val bUnderA = sessionUsers(url, second.deviceId, a)
                val asB = sessionUsers(url, second.deviceId, b)
                observed += "per-account identities, through the application: two sources, two profiles, one endpoint; A's identity resolves to $asA; B's identity as A sees it: $bUnderA; as B: $asB"
                assertTrue(asA.isEmpty() || asB.none { it in asA }, "B's identity never resolves to A's session")
            } finally {
                environment.removeSource(connectedA.sourceId).await()
                library.switchProfile(sam)
                environment.removeSource(connectedB.sourceId).await()
            }
        } finally {
            val out = Path.of("build", "live", "plex-two-accounts.txt")
            Files.createDirectories(out.parent)
            Files.writeString(out, observed.joinToString("\n") { line -> listOfNotNull(a, b).fold(line) { text, token -> text.replace(token, "***") } } + "\n")
        }
    }
}
