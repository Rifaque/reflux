package dev.reflux.sources.smb

import dev.reflux.core.model.Availability
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs against a real SMB server when `REFLUX_SMB_TEST` is set to `host:port/share` (credentials in
 * `REFLUX_SMB_TEST_USER` / `REFLUX_SMB_TEST_PASSWORD`). The share must contain `Movies/Heat (1995).mkv`.
 */
class SmbSourceLiveTest {
    @Test
    fun listsAndStreamsFromARealShare() = runBlocking {
        val target = System.getenv("REFLUX_SMB_TEST")
        assumeTrue("REFLUX_SMB_TEST not set", target != null)
        val host = target!!.substringBefore(':')
        val port = target.substringAfter(':').substringBefore('/').toInt()
        val share = target.substringAfter('/')
        LocalStreamServer().use { streams ->
            SmbSource(SmbShareConfig(host, share, "", System.getenv("REFLUX_SMB_TEST_USER"), System.getenv("REFLUX_SMB_TEST_PASSWORD"), port = port), streams).use { source ->
                assertEquals(Availability.AVAILABLE, source.availability())
                val files = source.files().toList()
                val heat = files.single { it.path == "Movies/Heat (1995)/Heat (1995).mkv" }
                assertTrue(heat.sizeBytes > 0)
                val url = source.playbackTarget(heat.path).uri
                val connection = URI(url).toURL().openConnection()
                connection.setRequestProperty("Range", "bytes=0-3")
                val magic = connection.getInputStream().readBytes()
                // Matroska files start with the EBML magic number.
                assertEquals(listOf(0x1A, 0x45, 0xDF, 0xA3), magic.map { it.toInt() and 0xff })
            }
        }
    }

    /**
     * A connection kept between uses goes stale when something on the way drops it while idle (seen on a Fire TV
     * through Docker's port forwarding, every 30 s): it still says it is connected, and the next request on it
     * never gets an answer. That is not the share being away; a fresh connection is tried before saying so.
     */
    @Test
    fun aConnectionThatWentStaleIsReplacedNotTakenForTheShareBeingAway() = runBlocking {
        val target = System.getenv("REFLUX_SMB_TEST")
        assumeTrue("REFLUX_SMB_TEST not set", target != null)
        val host = target!!.substringBefore(':')
        val port = target.substringAfter(':').substringBefore('/').toInt()
        val share = target.substringAfter('/')
        Relay(host, port).use { relay ->
            LocalStreamServer().use { streams ->
                val config = SmbShareConfig("127.0.0.1", share, "", System.getenv("REFLUX_SMB_TEST_USER"), System.getenv("REFLUX_SMB_TEST_PASSWORD"), port = relay.port)
                SmbSource(config, streams).use { source ->
                    assertEquals(Availability.AVAILABLE, source.availability())
                    val heat = source.files().toList().single { it.path == "Movies/Heat (1995)/Heat (1995).mkv" }

                    relay.stallOpenConnections()
                    assertEquals(Availability.AVAILABLE, source.availability(), "a stale connection is replaced, not reported as the share being away")
                    assertTrue(relay.accepted >= 2, "a second connection was made")

                    relay.stallOpenConnections()
                    val connection = URI(source.playbackTarget(heat.path).uri).toURL().openConnection()
                    connection.setRequestProperty("Range", "bytes=0-3")
                    assertEquals(listOf(0x1A, 0x45, 0xDF, 0xA3), connection.getInputStream().readBytes().map { it.toInt() and 0xff }, "a file still opens")
                }
            }
        }
    }

    /** Forwards connections to [host]:[port]; [stallOpenConnections] makes the ones open now go silent without closing. */
    private class Relay(private val host: String, private val targetPort: Int) : Closeable {
        private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        val port: Int get() = server.localPort
        private val open = CopyOnWriteArrayList<AtomicBoolean>()

        @Volatile
        var accepted = 0
            private set

        init {
            thread(isDaemon = true) {
                while (!server.isClosed) {
                    val client = runCatching { server.accept() }.getOrNull() ?: break
                    accepted++
                    val upstream = Socket(host, targetPort)
                    val stalled = AtomicBoolean(false).also { open += it }
                    fun pump(from: Socket, to: Socket) = thread(isDaemon = true) {
                        val buffer = ByteArray(64 * 1024)
                        runCatching {
                            while (true) {
                                val read = from.getInputStream().read(buffer)
                                if (read < 0) break
                                // A stalled connection takes what is sent and answers nothing, like a dropped NAT entry.
                                if (!stalled.get()) to.getOutputStream().write(buffer, 0, read)
                            }
                        }
                    }
                    pump(client, upstream)
                    pump(upstream, client)
                }
            }
        }

        fun stallOpenConnections() = open.forEach { it.set(true) }

        override fun close() = server.close()
    }
}
