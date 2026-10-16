package dev.reflux.desktop

import androidx.compose.ui.graphics.ImageBitmap
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.SourceId
import dev.reflux.core.playback.DisplayCapabilities
import dev.reflux.core.playback.Player
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.source.FileEnumeratingSource
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.library.Library
import dev.reflux.library.LibraryDatabase
import dev.reflux.library.LibraryPipeline
import dev.reflux.library.SourceRegistry
import dev.reflux.playback.mpv.DesktopCapabilities
import dev.reflux.playback.mpv.MpvPlayer
import dev.reflux.sources.local.LocalFolderSource
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.ArtworkLoader
import dev.reflux.ui.state.FolderChoice
import dev.reflux.ui.state.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assume.assumeTrue
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * The real desktop engine against a source that stops sending without closing its connection, as the live
 * run did with a forwarder (DECISIONS.md, 2026-10-16). The shared session's thirty seconds run on the test
 * scheduler; the engine and the connection are real. Skipped where libmpv or FFmpeg is missing, except
 * where the environment promises them (`REFLUX_REQUIRE_ENGINE_TESTS=true`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SilentSourceTest {
    private lateinit var directory: Path
    private lateinit var server: SilentServer
    private val players = mutableListOf<Player>()

    @BeforeTest
    fun setUp() {
        needs("libmpv not installed", MpvPlayer.isAvailable)
        needs("ffmpeg not installed", ffmpegAvailable)
        directory = Files.createTempDirectory("reflux-silent")
    }

    private fun needs(what: String, present: Boolean) {
        if (System.getenv("REFLUX_REQUIRE_ENGINE_TESTS") == "true") assertTrue(present, what) else assumeTrue(what, present)
    }

    @AfterTest
    fun tearDown() {
        if (::server.isInitialized) server.close()
        players.forEach { runCatching { it.close() } }
        if (::directory.isInitialized) directory.toFile().deleteRecursively()
    }

    @Test
    fun aHeldOpenSilentConnectionReachesTheStatedFailureAndBlocksNeitherTryAgainNorLeaving() = runTest(timeout = 3.minutes) {
        val world = world()
        stall(world)

        // The engine waits on a connection that stays open and sends nothing; the viewer's thirty seconds pass.
        advanceTimeBy(29_000)
        runCurrent()
        assertNull(world.app.playback?.error, "waiting is still only waiting")
        advanceTimeBy(1_000)
        runCurrent()
        val error = assertNotNull(world.app.playback?.error)
        assertTrue(error.canRetry)
        assertTrue("stopped sending" in error.message)

        // Try again while the source is still silent: the hung read does not hold up the new plan. The engine
        // drops the stalled connection, opens a new one, plays what it is sent, and is waiting again.
        val session = assertNotNull(world.app.playback)
        val connections = server.connections
        val asked = server.ranges.size
        session.retry()
        runCurrent()
        assertNull(session.error)
        assertEquals(PlayerStatus.LOADING, world.player.state.value.status, "the new plan is loading")
        awaitReal("a new connection for the new plan") { server.connections > connections }
        awaitReal("the new plan to ask for the position kept, not the beginning") { server.ranges.drop(asked).any { it > 0 } }
        awaitReal("the engine to play what it is sent and wait again", 30_000) { world.player.state.value.status == PlayerStatus.BUFFERING }
        assertNull(session.error)

        // And leaving: the engine shuts down promptly although its connection is held open.
        val release = session.close()
        awaitReal("the engine to be released", 10_000) { release.isCompleted }
        assertNull(world.app.playback)
        val kept = assertNotNull(world.library.watchState(world.movie)).positionMs
        assertTrue(kept >= RESUME_MS, "the position reached is kept: $kept")
    }

    @Test
    fun dataReturningOnTheHeldConnectionClearsTheFailureAndPlaybackGoesOn() = runTest(timeout = 3.minutes) {
        val world = world()
        stall(world)
        advanceTimeBy(30_000)
        runCurrent()
        assertNotNull(world.app.playback?.error)

        val requests = server.requests
        server.resume()
        awaitReal("playback to go on by itself", 20_000) { world.player.state.value.status == PlayerStatus.PLAYING }
        assertNull(world.app.playback?.error, "the message made way")
        assertEquals(requests, server.requests, "on the connection that was held, without trying again")
        world.app.playback?.close()?.let { release -> awaitReal("the engine to be released", 10_000) { release.isCompleted } }
    }

    private class World(val app: AppState, val library: Library, val movie: MediaId, val players: List<Player>) {
        val player: Player get() = players.last()
    }

    /**
     * A 90 s clip served over HTTP up to about 76 s and then silent, with the viewer's progress at 70 s.
     * Playback resumes there, plays what is sent, and waits.
     */
    private suspend fun TestScope.world(): World {
        val media = directory.resolve("Media").createDirectories()
        val clip = media.resolve("Clip (2020).mp4")
        run(
            "ffmpeg", "-y", "-loglevel", "error",
            "-f", "lavfi", "-i", "testsrc=duration=90:size=160x120:rate=24",
            "-f", "lavfi", "-i", "sine=frequency=440:duration=90",
            "-c:v", "libx264", "-pix_fmt", "yuv420p", "-g", "24", "-c:a", "aac", "-movflags", "+faststart",
            clip.toString(),
        )
        server = SilentServer(clip, cutoff = Files.size(clip) * 76 / 90)
        val library = Library(LibraryDatabase.open(directory.resolve("data/library.db"))) { System.currentTimeMillis() }
        val source = ServedFolder(LocalFolderSource(media, "Media"), server.url)
        val registry = SourceRegistry(emptyMap()).apply { register(source) }
        library.addSource(source, media.toString())
        val pipeline = LibraryPipeline(library, registry)
        withContext(Dispatchers.IO) { pipeline.refresh() }
        val movie = library.movies().single().item.id
        library.stopped(movie, RESUME_MS, 90_000, null)

        val platform = HeadlessPlatform()
        val app = AppState(library, pipeline, registry::resolve, platform, backgroundScope, StandardTestDispatcher(testScheduler))
        return World(app, library, movie, platform.created)
    }

    /** Plays the clip from where the viewer was until what was sent runs out. */
    private fun TestScope.stall(world: World) {
        world.app.play(world.movie)
        awaitReal("playback to begin", 20_000) { world.app.playback != null && world.players.isNotEmpty() && world.player.state.value.status == PlayerStatus.PLAYING }
        assertTrue(world.player.state.value.positionMs >= RESUME_MS - 1_000, "resumed at the position kept")
        awaitReal("the data sent to run out", 30_000) { world.player.state.value.status == PlayerStatus.BUFFERING }
        // It stays out: this is a stall, not a seek.
        Thread.sleep(2_000)
        runCurrent()
        assertEquals(PlayerStatus.BUFFERING, world.player.state.value.status)
    }

    /** Waits in real time for the engine, letting the session's coroutines run, without moving its clock. */
    private fun TestScope.awaitReal(what: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            runCurrent()
            if (condition()) return
            check(System.currentTimeMillis() < deadline) { "timed out waiting for $what (engine: ${players.lastOrNull()?.state?.value})" }
            Thread.sleep(20)
        }
    }

    private inner class HeadlessPlatform : Platform {
        val created = mutableListOf<Player>()
        override val artwork = object : ArtworkLoader {
            override fun cached(locator: ArtworkLocator, widthPx: Int): ImageBitmap? = null
            override suspend fun load(locator: ArtworkLocator, widthPx: Int): ImageBitmap? = null
        }
        override val device = DesktopCapabilities.of(DisplayCapabilities(1920, 1080))
        override val preferredLanguages = listOf("en")
        override val canPlay = true
        override suspend fun createPlayer(): Player? =
            MpvPlayer.create(device, mapOf("vo" to "null", "ao" to "null"))?.also { created += it; players += it }
        override suspend fun releasePlayer(player: Player) = withContext(Dispatchers.IO) { player.close() }
        override suspend fun addMediaFolder(): FolderChoice = FolderChoice.Cancelled
        override suspend fun removeSource(id: SourceId) = Unit
        override val fullscreen = false
        override fun requestFullscreen(fullscreen: Boolean) = Unit
    }

    /** The folder's files, played from [base] over HTTP instead of from the disk. */
    private class ServedFolder(private val local: LocalFolderSource, private val base: String) : FileEnumeratingSource by local {
        override suspend fun playbackTarget(path: String) = PlaybackTarget("$base/media.mp4")
    }

    /**
     * Serves one file with byte ranges, every response stopping at [cutoff] and then holding its connection
     * open, sending nothing, until [resume] or [close]: a server or link that went silent.
     */
    private class SilentServer(private val file: Path, @Volatile private var cutoff: Long) : AutoCloseable {
        private val socket = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        private val size = Files.size(file)
        private val lock = Object()
        private val open = mutableListOf<Socket>()
        @Volatile private var closed = false
        @Volatile var connections = 0
            private set
        /** Where each request asked to start, in bytes. */
        val ranges = java.util.concurrent.CopyOnWriteArrayList<Long>()
        val requests: Int get() = ranges.size
        val url = "http://127.0.0.1:${socket.localPort}"

        init {
            Thread({
                while (!closed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    connections++
                    synchronized(open) { open += client }
                    Thread({ runCatching { serve(client) } }, "silent-server-client").apply { isDaemon = true }.start()
                }
            }, "silent-server").apply { isDaemon = true }.start()
        }

        fun resume() = synchronized(lock) {
            cutoff = size
            lock.notifyAll()
        }

        private fun serve(client: Socket) = client.use {
            val input = client.getInputStream()
            while (!closed) {
                val head = readHead(input) ?: return
                val from = Regex("""(?i)range:\s*bytes=(\d+)-""").find(head)?.groupValues?.get(1)?.toLong() ?: 0L
                ranges += from
                val output = client.getOutputStream()
                val status = if (from > 0) "206 Partial Content" else "200 OK"
                val headers = buildString {
                    append("HTTP/1.1 $status\r\nContent-Type: video/mp4\r\nAccept-Ranges: bytes\r\nContent-Length: ${size - from}\r\n")
                    if (from > 0) append("Content-Range: bytes $from-${size - 1}/$size\r\n")
                    append("\r\n")
                }
                output.write(headers.toByteArray())
                Files.newByteChannel(file).use { channel ->
                    channel.position(from)
                    val buffer = java.nio.ByteBuffer.allocate(64 * 1024)
                    var position = from
                    while (position < size) {
                        synchronized(lock) { while (position >= cutoff && !closed) lock.wait(200) }
                        if (closed) return
                        buffer.clear().limit(minOf(buffer.capacity().toLong(), cutoff - position).toInt())
                        val read = channel.read(buffer)
                        if (read <= 0) return
                        output.write(buffer.array(), 0, read)
                        position += read
                    }
                    output.flush()
                }
            }
        }

        private fun readHead(input: InputStream): String? {
            val head = StringBuilder()
            while (!head.endsWith("\r\n\r\n")) {
                val next = input.read()
                if (next < 0) return null
                head.append(next.toChar())
            }
            return head.toString()
        }

        override fun close() {
            closed = true
            synchronized(lock) { lock.notifyAll() }
            runCatching { socket.close() }
            synchronized(open) { open.forEach { runCatching { it.close() } } }
        }
    }

    private companion object {
        const val RESUME_MS = 70_000L

        val ffmpegAvailable: Boolean by lazy {
            runCatching { ProcessBuilder("ffmpeg", "-version").start().waitFor(10, TimeUnit.SECONDS) }.getOrDefault(false)
        }

        fun run(vararg command: String) {
            val process = ProcessBuilder(*command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0) { "ffmpeg failed: $output" }
        }
    }
}
