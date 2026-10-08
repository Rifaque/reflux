package dev.reflux.sources.smb

import dev.reflux.core.model.Availability
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.net.URI
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
}
