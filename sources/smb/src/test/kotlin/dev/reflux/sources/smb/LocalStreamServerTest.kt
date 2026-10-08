package dev.reflux.sources.smb

import java.net.HttpURLConnection
import java.net.URI
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalStreamServerTest {
    private val server = LocalStreamServer()
    private val data = ByteArray(1_000_000) { (it % 251).toByte() }

    private fun reader() = object : RangeReader {
        override val length = data.size.toLong()
        override fun read(offset: Long, buffer: ByteArray, count: Int): Int {
            if (offset >= data.size) return -1
            val n = minOf(count, data.size - offset.toInt())
            data.copyInto(buffer, 0, offset.toInt(), offset.toInt() + n)
            return n
        }
    }

    @AfterTest
    fun close() = server.close()

    private fun get(url: String, range: String? = null, method: String = "GET"): Pair<HttpURLConnection, ByteArray> {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = method
        range?.let { connection.setRequestProperty("Range", it) }
        val body = if (connection.responseCode < 400) connection.inputStream.readBytes() else ByteArray(0)
        return connection to body
    }

    @Test
    fun servesWholeFilesAndRanges() {
        val url = server.register("share/Movies/Heat (1995).mkv", "Heat (1995).mkv") { reader() }
        assertTrue(url.startsWith("http://127.0.0.1:${server.port}/"))
        assertTrue(url.endsWith("/Heat__1995_.mkv"))

        val (whole, body) = get(url)
        assertEquals(200, whole.responseCode)
        assertContentEquals(data, body)

        val (partial, slice) = get(url, "bytes=1000-1999")
        assertEquals(206, partial.responseCode)
        assertEquals("bytes 1000-1999/1000000", partial.getHeaderField("Content-Range"))
        assertContentEquals(data.copyOfRange(1000, 2000), slice)

        val (tail, end) = get(url, "bytes=-10")
        assertEquals(206, tail.responseCode)
        assertContentEquals(data.copyOfRange(data.size - 10, data.size), end)

        val (head, empty) = get(url, method = "HEAD")
        assertEquals("1000000", head.getHeaderField("Content-Length"))
        assertEquals(0, empty.size)
    }

    @Test
    fun rejectsUnknownTokensAndBadRanges() {
        val url = server.register("a", "a.mkv") { reader() }
        assertEquals(404, get("http://127.0.0.1:${server.port}/0123456789abcdef/a.mkv").first.responseCode)
        assertEquals(416, get(url, "bytes=5000000-").first.responseCode)
        assertEquals(url, server.register("a", "a.mkv") { reader() }, "stable URL per key")
    }

    @Test
    fun rangeParsing() {
        assertEquals(0L to 99L, LocalStreamServer.parseRange(null, 100))
        assertEquals(10L to 99L, LocalStreamServer.parseRange("bytes=10-", 100))
        assertEquals(10L to 99L, LocalStreamServer.parseRange("bytes=10-500", 100))
        assertEquals(90L to 99L, LocalStreamServer.parseRange("bytes=-10", 100))
        assertNull(LocalStreamServer.parseRange("bytes=100-", 100))
        assertNull(LocalStreamServer.parseRange("bytes=x-y", 100))
    }
}
