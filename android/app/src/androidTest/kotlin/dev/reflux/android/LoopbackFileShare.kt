package dev.reflux.android

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.concurrent.thread

/**
 * A WebDAV share on the device's loopback that serves real files, with byte ranges, so that the device's media framework
 * streams them over HTTP as it would from a server on the network. No sign-in. [files] maps a path under the share
 * ("Share Film (2023)/Share.Film.2023.mkv") to its bytes. Only reading methods are answered; every method seen is kept.
 */
internal class LoopbackFileShare(files: Map<String, ByteArray>) : AutoCloseable {
    private val socket = ServerSocket(0, 32, InetAddress.getByName("127.0.0.1")) // Android's loopback address is ::1
    val url = "http://127.0.0.1:${socket.localPort}/dav"
    val methods: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
    private val files: Map<String, ByteArray> = files.mapKeys { "/dav/" + it.key.trimStart('/') }
    private val folders: Set<String> = buildSet {
        add("/dav/")
        for (path in this@LoopbackFileShare.files.keys) {
            var at = path.lastIndexOf('/')
            while (at > 0) {
                add(path.substring(0, at + 1))
                at = path.lastIndexOf('/', at - 1)
            }
        }
    }.filter { it.startsWith("/dav/") }.toSet()

    init {
        thread(isDaemon = true, name = "loopback-share") {
            while (!socket.isClosed) {
                val connection = runCatching { socket.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { runCatching { connection.use { serve(it.getInputStream(), it.getOutputStream()) } } }
            }
        }
    }

    private fun serve(input: InputStream, output: java.io.OutputStream) {
        val (method, rawPath) = line(input).split(' ').let { it[0] to it[1] }
        val headers = generateSequence { line(input).takeIf(String::isNotEmpty) }
            .associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
        repeat(headers["content-length"]?.toInt() ?: 0) { input.read() }
        methods += method
        val path = URLDecoder.decode(rawPath.substringBefore('?').replace("+", "%2B"), "UTF-8")
        val folder = if (path.endsWith("/")) path else "$path/"
        when {
            method == "PROPFIND" && folder in folders -> {
                val entries = mutableListOf(entry(folder, null))
                if (headers["depth"] != "0") {
                    folders.filter { it != folder && it.startsWith(folder) && it.removePrefix(folder).trimEnd('/').none { c -> c == '/' } }
                        .forEach { entries += entry(it, null) }
                    files.filter { it.key.startsWith(folder) && '/' !in it.key.removePrefix(folder) }
                        .forEach { entries += entry(it.key, it.value.size) }
                }
                respond(output, 207, "Multi-Status", """<?xml version="1.0"?><D:multistatus xmlns:D="DAV:">${entries.joinToString("")}</D:multistatus>""".encodeToByteArray(), "application/xml")
            }
            method == "PROPFIND" && path in files -> respond(output, 207, "Multi-Status", """<?xml version="1.0"?><D:multistatus xmlns:D="DAV:">${entry(path, files.getValue(path).size)}</D:multistatus>""".encodeToByteArray(), "application/xml")
            (method == "GET" || method == "HEAD") && path in files -> {
                val bytes = files.getValue(path)
                val range = headers["range"]?.removePrefix("bytes=")?.split('-')
                val type = if (path.endsWith(".mkv")) "video/x-matroska" else "video/mp4"
                if (range != null) {
                    val start = range[0].toLong()
                    val end = range.getOrNull(1)?.takeIf(String::isNotEmpty)?.toLong()?.coerceAtMost(bytes.size - 1L) ?: (bytes.size - 1L)
                    val part = bytes.copyOfRange(start.toInt(), end.toInt() + 1)
                    respond(output, 206, "Partial Content", part, type, method == "HEAD", "Content-Range: bytes $start-$end/${bytes.size}\r\n")
                } else {
                    respond(output, 200, "OK", bytes, type, method == "HEAD")
                }
            }
            else -> respond(output, 404, "Not Found", ByteArray(0), "text/plain")
        }
    }

    private fun entry(path: String, size: Int?): String {
        val href = path.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        val kind = if (size == null) "<D:collection/>" else ""
        val length = size?.let { "<D:getcontentlength>$it</D:getcontentlength>" }.orEmpty()
        return "<D:response><D:href>$href</D:href><D:propstat><D:prop><D:resourcetype>$kind</D:resourcetype>$length" +
            "<D:getlastmodified>Sun, 18 Oct 2026 14:58:12 GMT</D:getlastmodified></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>"
    }

    private fun respond(output: java.io.OutputStream, status: Int, reason: String, body: ByteArray, type: String, head: Boolean = false, extra: String = "") {
        output.write(("HTTP/1.1 $status $reason\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nAccept-Ranges: bytes\r\n$extra" +
            "Connection: close\r\n\r\n").encodeToByteArray())
        if (!head) output.write(body)
        output.flush()
    }

    override fun close() = socket.close()

    private fun line(input: InputStream): String {
        val out = ByteArrayOutputStream()
        while (true) {
            val byte = input.read()
            if (byte == -1 || byte == '\n'.code) break
            if (byte != '\r'.code) out.write(byte)
        }
        return out.toString("UTF-8")
    }
}
