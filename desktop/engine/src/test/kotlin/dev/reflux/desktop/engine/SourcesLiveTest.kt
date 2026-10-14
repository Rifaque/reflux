package dev.reflux.desktop.engine

import dev.reflux.core.model.Availability
import dev.reflux.core.offline.DownloadState
import dev.reflux.core.source.ConnectionKind
import dev.reflux.core.source.ConnectionProblem
import dev.reflux.core.source.ConnectionRequest
import dev.reflux.library.ConnectResult
import dev.reflux.library.DownloadsSource
import dev.reflux.library.FileDownloadStore
import dev.reflux.library.HttpDownloadTransport
import dev.reflux.library.Offline
import dev.reflux.library.downloads
import dev.reflux.library.planPlayback
import dev.reflux.library.userSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Network shares through the same path the application takes (connectors, the library, the pipeline,
 * playback planning, Smart Offline), against real SMB and WebDAV servers. Runs when `REFLUX_SHARES_TEST`
 * names the host that `tools/ci/share-test-servers.sh` started them on.
 *
 * Disconnecting is something done to the servers, so the test runs in phases over one data directory
 * (`REFLUX_SHARES_DATA`): `REFLUX_SHARES_PHASE=connect` with the servers up, `away` after
 * `docker stop reflux-smb-test reflux-webdav-test`, and `back` after starting them again.
 */
class SourcesLiveTest {
    private val host: String? = System.getenv("REFLUX_SHARES_TEST")
    private val phase = System.getenv("REFLUX_SHARES_PHASE") ?: "connect"
    private val data: Path by lazy { System.getenv("REFLUX_SHARES_DATA")?.let(Path::of) ?: Files.createTempDirectory("reflux-shares") }
    private val smb get() = "\\\\$host:1445\\Movies"
    private val webDav get() = "http://$host:8098/"
    private val user = "reflux"
    private val password = "reflux-test"

    private fun environment(): Pair<DesktopEnvironment, FileDownloadStore> {
        val environment = DesktopEnvironment(data)
        val store = FileDownloadStore(data.resolve("downloads").toFile())
        // As the Android shell does: copies are a source of Reflux's own, recreated at every start.
        if (environment.library.source(DownloadsSource.ID) != null) environment.registry.register(DownloadsSource(store))
        return environment to store
    }

    private fun DesktopEnvironment.film() = library.movies().filter { it.item.title == "Night Drive" }

    @Test
    fun sharesWorkThroughTheApplicationsOwnPath() = runBlocking {
        assumeTrue("REFLUX_SHARES_TEST not set", host != null)
        val (environment, store) = environment()
        when (phase) {
            "connect" -> connect(environment, store)
            "away" -> away(environment)
            "back" -> back(environment)
            else -> error("unknown phase $phase")
        }
    }

    private suspend fun connect(environment: DesktopEnvironment, store: FileDownloadStore) {
        val connections = environment.connections
        // What a user can get wrong is told apart.
        assertEquals(ConnectResult.Failed(ConnectionProblem.AUTHENTICATION), connections.connect(ConnectionKind.SHARE, ConnectionRequest(smb, user, "wrong")))
        assertEquals(ConnectResult.Failed(ConnectionProblem.NOT_FOUND), connections.connect(ConnectionKind.SHARE, ConnectionRequest("\\\\$host:1445\\Nothing", user, password)))
        assertEquals(ConnectResult.Failed(ConnectionProblem.SIGN_IN_REQUIRED), connections.connect(ConnectionKind.SHARE, ConnectionRequest(webDav)))
        assertEquals(ConnectResult.Failed(ConnectionProblem.AUTHENTICATION), connections.connect(ConnectionKind.SHARE, ConnectionRequest(webDav, user, "wrong")))
        assertTrue(environment.library.userSources().isEmpty(), "nothing is added by a failed attempt")

        val share = assertIs(connections.connect(ConnectionKind.SHARE, ConnectionRequest(smb, user, password)))
        val web = assertIs(connections.connect(ConnectionKind.SHARE, ConnectionRequest(webDav, user, password)))
        environment.pipeline.refresh()
        assertEquals(Availability.AVAILABLE, environment.library.source(share.sourceId)?.availability)
        assertEquals(Availability.AVAILABLE, environment.library.source(web.sourceId)?.availability)

        // The same film on two shares is one film with two versions.
        val film = environment.film().single()
        val versions = environment.library.versions(film.item.id)
        assertEquals(setOf(share.sourceId, web.sourceId), versions.map { it.version.location.sourceId }.toSet())

        // Each version is something the engine can open, and does: its running time is the file's.
        for (version in versions) {
            val plan = assertNotNull(environment.library.planPlayback(film.item.id, environment.deviceCapabilities(), environment::source, version.version.id))
            environment.prober?.let { probe ->
                val stream = assertNotNull(probe.probe(plan.request.target), "the engine opens the version on ${version.version.location.sourceId}")
                assertTrue((stream.durationMs ?: 0) in 2_390_000..2_410_000, "the engine reads the film from ${version.version.location.sourceId}: ${stream.durationMs}")
            }
        }

        // A copy of the share's version, made the way Smart Offline makes one.
        environment.library.setPreferredVersion(film.item.id, versions.single { it.version.location.sourceId == share.sourceId }.version.id)
        val scope = CoroutineScope(Dispatchers.IO)
        val offline = Offline(environment.library, store, HttpDownloadTransport("Reflux test"), environment::source, { environment.deviceCapabilities() }, listOf("en"), environment.registry::register, Dispatchers.IO)
        offline.start(scope)
        val plan = offline.planFor(listOf(film.item.id))
        assertEquals(share.sourceId, plan.downloads.single().version.location.sourceId, "the pinned version is the one kept")
        offline.enqueue(plan)
        withTimeout(120_000) {
            while (environment.library.downloads().singleOrNull()?.state != DownloadState.COMPLETED) delay(250)
        }
        scope.cancel()
        assertEquals(3, environment.library.versions(film.item.id).size, "the copy is one more version of the same film")
        println("Shares connected, scanned, probed, and a copy kept in $data")
    }

    private suspend fun away(environment: DesktopEnvironment) {
        environment.pipeline.refresh()
        val shares = environment.library.userSources()
        assertEquals(2, shares.size, "sources that are away stay")
        assertTrue(shares.all { it.availability == Availability.UNAVAILABLE }, "and say they are not connected: ${shares.map { it.availability }}")
        val film = environment.film().single()
        assertEquals(3, environment.library.versions(film.item.id).size, "the library still knows every version")
        // What plays now is the copy on this device.
        val plan = assertNotNull(environment.library.planPlayback(film.item.id, environment.deviceCapabilities(), environment::source))
        assertEquals(DownloadsSource.ID, plan.version.version.location.sourceId)
        assertTrue(plan.request.target.uri.startsWith("file:"))
        println("With the shares away the film plays from ${plan.request.target.uri}")
    }

    private suspend fun back(environment: DesktopEnvironment) {
        environment.pipeline.refresh()
        val shares = environment.library.userSources()
        assertEquals(2, shares.size, "reconnecting adds nothing")
        assertTrue(shares.all { it.availability == Availability.AVAILABLE })
        assertEquals(1, environment.film().size, "and the film is still one film")
        assertEquals(3, environment.library.versions(environment.film().single().item.id).size)
        // Connecting the same share again is the same source, not a second one.
        val again = assertIs(environment.connections.connect(ConnectionKind.SHARE, ConnectionRequest(smb, user, password)))
        assertTrue(again.updated)
        assertEquals(2, environment.library.userSources().size)
        println("Shares back, nothing duplicated")
    }

    private fun assertIs(result: ConnectResult): ConnectResult.Connected =
        result as? ConnectResult.Connected ?: throw AssertionError("not connected: $result")
}
