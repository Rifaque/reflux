package dev.reflux.desktop.engine

import dev.reflux.core.model.Availability
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.SourceId
import dev.reflux.core.net.HttpRequest
import dev.reflux.core.source.CatalogSource
import dev.reflux.core.source.SourceUserState
import dev.reflux.core.source.WatchStateSyncSource
import dev.reflux.library.Library
import dev.reflux.library.LibraryDatabase
import dev.reflux.library.SyncingWatchReporter
import dev.reflux.library.net.JdkHttpFetcher
import dev.reflux.library.setWatchedEverywhere
import dev.reflux.sources.jellyfin.JellyfinSource
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The watch-state exchange with a real Jellyfin server, end to end through the library.
 *
 * Runs when a server is named: `REFLUX_JELLYFIN_TEST` (its URL), `REFLUX_JELLYFIN_TEST_USER`, and
 * `REFLUX_JELLYFIN_TEST_PASSWORD`, in the environment or in `~/.reflux-live/jellyfin.env` ([LiveJellyfin]).
 * The user's library must hold a movie of twenty minutes or more (servers do not keep a position in anything
 * short). The test rewrites that user's watch state for that movie and leaves it unplayed: point it at a
 * server and a user that are there for testing.
 * `tools/ci/jellyfin-test-server.sh` starts such a server in Docker.
 */
class JellyfinLiveTest {
    /** Counts what Reflux tells the server, to tell "told once" from "told every time". */
    private class Observed(private val server: JellyfinSource) : CatalogSource by server, WatchStateSyncSource {
        val told = mutableListOf<String>()
        override val descriptor get() = server.descriptor
        override suspend fun availability() = server.availability()
        override suspend fun playbackTarget(path: String) = server.playbackTarget(path)

        override suspend fun reportProgress(path: String, positionMs: Long, paused: Boolean) {
            server.reportProgress(path, positionMs, paused)
        }

        override suspend fun reportStopped(path: String, positionMs: Long) {
            told += "position ${positionMs / 1000}"
            server.reportStopped(path, positionMs)
        }

        override suspend fun setPlayed(path: String, played: Boolean) {
            told += "played $played"
            server.setPlayed(path, played)
        }
    }

    @Test
    fun watchStateRoundTripsWithARealServer() = runBlocking {
        val live = LiveJellyfin.settings()
        assumeTrue("no live Jellyfin server named (environment or ~/.reflux-live/jellyfin.env)", live != null)
        val (url, user, password) = Triple(live!!.url, live.user, live.password)
        val http = JdkHttpFetcher()
        val jellyfin = JellyfinSource(JellyfinSource.connect(url, user, password, http, deviceId = "reflux-live-test"), http, "Live test")
        // The same user on another device, as any Jellyfin client presents itself.
        val elsewhere = JellyfinSource(JellyfinSource.connect(url, user, password, http, "reflux-live-test-elsewhere", "Another client"), http)
        val server = Observed(jellyfin)
        assertEquals(Availability.AVAILABLE, server.availability())

        val movie = server.catalog().toList().firstOrNull { entry -> entry.versions.any { (it.stream.durationMs ?: 0) >= 20 * MINUTE } }
        val path = assertNotNull(movie, "the test user's library needs a movie of twenty minutes or more").versions.first().path
        val running = movie.versions.first().stream.durationMs!!
        suspend fun onServer(): SourceUserState =
            assertNotNull(server.catalog().toList().single { entry -> entry.versions.any { it.path == path } }.userState)
        // The server hands out the file itself, to Reflux's own player.
        val stream = server.playbackTarget(path)
        val head = http.send(HttpRequest(stream.uri, headers = stream.headers + ("Range" to "bytes=0-3")))
        assertTrue(head.ok && head.body.size >= 4, "the direct stream answers (HTTP ${head.status})")

        jellyfin.setPlayed(path, false)
        assertFalse(onServer().played)
        assertEquals(0, onServer().positionMs)

        val directory = Files.createTempDirectory("reflux-jellyfin-live").also { it.toFile().deleteOnExit() }
        val library = Library(LibraryDatabase.open(directory.resolve("library.db"))) { System.currentTimeMillis() }
        val sources = { id: SourceId -> server.takeIf { it.descriptor.id == id } }
        library.addSource(server, "live")

        // 1. The server's existing state is read and compared: nothing has been watched anywhere.
        library.scan(server)
        val copy: MediaVersion = library.movies().flatMap { library.versions(it.item.id) }.single { it.version.location.path == path }.version
        val item: MediaId = copy.itemId
        assertNull(library.watchState(item))
        assertTrue(server.told.isEmpty())

        // 2. Progress made in Reflux on another copy of the same cut reaches the server at the next scan,
        //    outside any playback session on the server.
        val first = seconds(running * 2 / 5)
        delay(20)
        library.stopped(item, first, running, null)
        library.scan(server)
        assertEquals(listOf("position ${first / 1000}"), server.told)
        assertClose(first, onServer().positionMs, "the server took a position sent outside a playback session")
        assertFalse(onServer().played)

        // 3. With nothing left to settle, nothing more is sent and nothing changes.
        library.scan(server)
        assertEquals(1, server.told.size)
        assertEquals(first, library.watchState(item)?.positionMs)

        // 4. The server's value is now the stale one: Reflux moved on, and a scan does not bring the old
        //    position back.
        val second = seconds(running * 3 / 5)
        delay(20)
        library.stopped(item, second, running, null)
        assertClose(first, onServer().positionMs, "the server still says what it said")
        library.scan(server)
        assertEquals(second, library.watchState(item)?.positionMs)
        assertClose(second, onServer().positionMs, "and is brought up to date instead")

        // 5. News from the server: the user watches further on another client.
        val third = seconds(running * 7 / 10)
        elsewhere.reportProgress(path, third - 5_000, paused = false)
        elsewhere.reportStopped(path, third)
        assertClose(third, onServer().positionMs, "what the other client reported")
        val before = server.told.size
        library.scan(server)
        val adopted = assertNotNull(library.watchState(item))
        assertClose(third, adopted.positionMs, "Reflux adopts it")
        assertEquals(copy.id, adopted.versionId, "as a position on the server's copy")
        assertEquals(before, server.told.size, "and has nothing to tell the server")

        // 6. Playing the server's copy in Reflux reports to the server as it goes.
        val fourth = seconds(running * 4 / 5)
        coroutineScope {
            val reporter = SyncingWatchReporter(library, copy, server, this)
            reporter.progress(item, fourth - 10_000, running)
            reporter.stopped(item, fourth, running)
        }
        assertClose(fourth, onServer().positionMs, "the position at the end of a session")
        library.scan(server)
        assertEquals(fourth, library.watchState(item)?.positionMs)

        // 7. Watched and unwatched travel both ways; un-marking makes the server forget its position.
        assertEquals(0, library.setWatchedEverywhere(item, true, sources))
        assertTrue(onServer().played)
        library.scan(server)
        assertTrue(library.watchState(item)!!.completed)
        assertEquals(0, library.setWatchedEverywhere(item, false, sources))
        assertFalse(onServer().played)
        assertEquals(0, onServer().positionMs)
        library.scan(server)
        assertFalse(library.watchState(item)!!.completed)

        // 8. Finished on another client: the server's "played" is news and is adopted.
        elsewhere.setPlayed(path, true)
        library.scan(server)
        assertTrue(library.watchState(item)!!.completed)

        jellyfin.setPlayed(path, false)
    }

    private fun seconds(positionMs: Long): Long = positionMs / 1000 * 1000

    private fun assertClose(expected: Long, actual: Long, what: String) =
        assertTrue(abs(expected - actual) < 1_500, "$what: expected about ${expected / 1000} s, the server says ${actual / 1000} s")

    private companion object {
        const val MINUTE = 60_000L
    }
}
