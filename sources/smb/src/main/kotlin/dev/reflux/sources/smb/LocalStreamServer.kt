package dev.reflux.sources.smb

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Random-access reading of one remote file. */
interface RangeReader {
    val length: Long

    /** Reads up to [buffer].size bytes at [offset]; returns the number read, or -1 at the end. */
    fun read(offset: Long, buffer: ByteArray, count: Int): Int
}

/**
 * A loopback-only HTTP/1.1 server that streams files from sources players cannot open directly (SMB).
 *
 * Each file gets an unguessable URL token, so other local processes cannot enumerate the share. Supports
 * `GET`/`HEAD` and single byte ranges, which is what seeking in mpv and Media3 needs. Uses only plain sockets so
 * it runs on Android as well as desktop JVMs.
 */
class LocalStreamServer : Closeable {
    private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    private val executor = Executors.newCachedThreadPool { runnable -> Thread(runnable, "reflux-stream").apply { isDaemon = true } }
    private val streams = ConcurrentHashMap<String, () -> RangeReader>()
    private val tokens = ConcurrentHashMap<String, String>()
    private val random = SecureRandom()

    val port: Int get() = server.localPort

    init {
        executor.execute(::acceptLoop)
    }

    /** Registers a stream under a stable [key] and returns its URL. [open] is called per request. */
    fun register(key: String, fileName: String, open: () -> RangeReader): String {
        val token = tokens.computeIfAbsent(key) {
            ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        }
        streams[token] = open
        val safeName = fileName.map { if (it.isLetterOrDigit() || it in "._-") it else '_' }.joinToString("")
        return "http://127.0.0.1:$port/$token/$safeName"
    }

    override fun close() {
        server.close()
        executor.shutdownNow()
    }

    private fun acceptLoop() {
        while (!server.isClosed) {
            val socket = try {
                server.accept()
            } catch (_: SocketException) {
                return
            }
            executor.execute { socket.use(::serve) }
        }
    }

    private fun serve(socket: Socket) {
        val input = BufferedInputStream(socket.getInputStream())
        val output = socket.getOutputStream()
        try {
            while (true) {
                val requestLine = readLine(input) ?: return
                val headers = generateSequence { readLine(input)?.takeIf { it.isNotEmpty() } }
                    .associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
                val parts = requestLine.split(' ')
                if (parts.size < 2 || parts[0] !in setOf("GET", "HEAD")) return respond(output, 405, "Method Not Allowed")
                val token = parts[1].trimStart('/').substringBefore('/')
                val open = streams[token] ?: return respond(output, 404, "Not Found")
                val reader = try {
                    open()
                } catch (_: IOException) {
                    return respond(output, 502, "Bad Gateway")
                }
                (reader as? Closeable).use { _ -> send(output, reader, headers["range"], head = parts[0] == "HEAD") }
                if (headers["connection"].equals("close", ignoreCase = true)) return
            }
        } catch (_: IOException) {
            // client went away (seeking closes connections)
        }
    }

    private fun send(output: OutputStream, reader: RangeReader, range: String?, head: Boolean) {
        val length = reader.length
        val (start, end) = parseRange(range, length) ?: return respond(output, 416, "Range Not Satisfiable", "Content-Range: bytes */$length\r\n")
        val partial = range != null
        val count = end - start + 1
        val header = buildString {
            append(if (partial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
            append("Content-Type: application/octet-stream\r\n")
            append("Accept-Ranges: bytes\r\n")
            append("Content-Length: ").append(count).append("\r\n")
            if (partial) append("Content-Range: bytes ").append(start).append('-').append(end).append('/').append(length).append("\r\n")
            append("\r\n")
        }
        output.write(header.toByteArray(Charsets.US_ASCII))
        if (!head) {
            val buffer = ByteArray(256 * 1024)
            var position = start
            while (position <= end) {
                val read = reader.read(position, buffer, minOf(buffer.size.toLong(), end - position + 1).toInt())
                if (read <= 0) break
                output.write(buffer, 0, read)
                position += read
            }
        }
        output.flush()
    }

    private fun respond(output: OutputStream, status: Int, reason: String, extra: String = "") {
        output.write("HTTP/1.1 $status $reason\r\n${extra}Content-Length: 0\r\n\r\n".toByteArray(Charsets.US_ASCII))
        output.flush()
    }

    private fun readLine(input: BufferedInputStream): String? {
        val line = StringBuilder()
        while (true) {
            val c = input.read()
            if (c == -1) return if (line.isEmpty()) null else line.toString()
            if (c == '\n'.code) return line.toString().trimEnd('\r')
            line.append(c.toChar())
            if (line.length > 8192) throw IOException("header line too long")
        }
    }

    companion object {
        /** Parses a single `bytes=a-b`, `bytes=a-`, or `bytes=-n` range; null when unsatisfiable. */
        internal fun parseRange(range: String?, length: Long): Pair<Long, Long>? {
            if (length == 0L) return if (range == null) 0L to -1L else null
            if (range == null) return 0L to length - 1
            val spec = range.removePrefix("bytes=").substringBefore(',').trim()
            val first = spec.substringBefore('-')
            val last = spec.substringAfter('-')
            val (start, end) = when {
                first.isEmpty() -> (length - (last.toLongOrNull() ?: return null)).coerceAtLeast(0) to length - 1
                else -> (first.toLongOrNull() ?: return null) to (last.toLongOrNull()?.coerceAtMost(length - 1) ?: (length - 1))
            }
            return if (start > end || start >= length) null else start to end
        }
    }
}
