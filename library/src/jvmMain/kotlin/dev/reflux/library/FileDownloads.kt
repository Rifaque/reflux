package dev.reflux.library

import dev.reflux.core.offline.DownloadFailure
import dev.reflux.core.offline.StorageSnapshot
import dev.reflux.core.source.PlaybackTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Downloads kept in a directory that belongs to Reflux alone (the application's private storage on
 * Android, its data directory on a desktop). Nothing outside it is ever touched, and a path that would
 * leave it is refused.
 */
class FileDownloadStore(directory: File) : DownloadStore {
    private val root: File = directory.absoluteFile

    private fun file(path: String): File {
        val resolved = File(root, path).canonicalFile
        require(resolved.path.startsWith(root.canonicalPath + File.separator)) { "path leaves the downloads directory: $path" }
        return resolved
    }

    override fun available(): Boolean = (root.isDirectory || root.mkdirs()) && root.canWrite()

    override fun uri(path: String): String = file(path).toURI().toString().replace("file:/", "file:///").replace("file:////", "file:///")

    override fun length(path: String): Long? = file(path).takeIf { it.isFile }?.length()

    override fun open(path: String, offset: Long): DownloadSink {
        val target = file(path)
        target.parentFile?.mkdirs()
        val access = RandomAccessFile(target, "rw")
        access.setLength(offset.coerceAtMost(access.length()))
        access.seek(access.length())
        return object : DownloadSink {
            override fun write(bytes: ByteArray, length: Int) = access.write(bytes, 0, length)
            override fun close() = access.close()
        }
    }

    override fun move(from: String, to: String) {
        val target = file(to)
        target.delete()
        if (!file(from).renameTo(target)) throw IOException("could not finish the download at $to")
    }

    override fun delete(path: String) {
        file(path).delete()
    }

    override fun storage(): StorageSnapshot {
        root.mkdirs()
        return StorageSnapshot(freeBytes = root.usableSpace, totalBytes = root.totalSpace.takeIf { it > 0 })
    }
}

/**
 * Fetches files over HTTP, continuing part-way with a range request where the server allows it, and
 * straight from a `file:` address for sources that are files already (tests, mounted shares).
 */
class HttpDownloadTransport(private val userAgent: String) : DownloadTransport {
    override suspend fun fetch(target: PlaybackTarget, offset: Long, receiver: DownloadReceiver) = withContext(Dispatchers.IO) {
        if (target.uri.startsWith("file:")) return@withContext fromFile(File(URI(target.uri)), offset, receiver)
        val connection = try {
            URL(target.uri).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw DownloadException(DownloadFailure.NOT_ALLOWED, "not something that can be fetched", e)
        }
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", userAgent)
            // A copy has to be the file itself, not a compressed transfer of it whose length means something else.
            connection.setRequestProperty("Accept-Encoding", "identity")
            target.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
            val status = try {
                connection.responseCode
            } catch (e: IOException) {
                throw DownloadException(DownloadFailure.SOURCE_UNAVAILABLE, "the source did not answer", e)
            }
            when (status) {
                HttpURLConnection.HTTP_PARTIAL -> receiver.started(offset, totalOf(connection.getHeaderField("Content-Range")) ?: connection.contentLengthLong.takeIf { it >= 0 }?.plus(offset))
                HttpURLConnection.HTTP_OK -> receiver.started(0, connection.contentLengthLong.takeIf { it >= 0 })
                // Asked to continue past the end: the part that is there is the whole file.
                REQUESTED_RANGE_NOT_SATISFIABLE -> return@withContext receiver.started(offset, offset)
                HttpURLConnection.HTTP_NOT_FOUND, HttpURLConnection.HTTP_GONE -> throw DownloadException(DownloadFailure.MEDIA_GONE, "HTTP $status")
                HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN -> throw DownloadException(DownloadFailure.NOT_ALLOWED, "HTTP $status")
                else -> throw DownloadException(DownloadFailure.SOURCE_UNAVAILABLE, "HTTP $status")
            }
            try {
                connection.inputStream.use { pump(it, receiver) }
            } catch (e: IOException) {
                throw DownloadException(DownloadFailure.INTERRUPTED, "the connection broke", e)
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun fromFile(file: File, offset: Long, receiver: DownloadReceiver) {
        val length = try {
            if (!file.isFile) throw FileNotFoundException(file.path)
            file.length()
        } catch (e: IOException) {
            throw DownloadException(DownloadFailure.MEDIA_GONE, "the file is not there", e)
        }
        val from = offset.coerceAtMost(length)
        receiver.started(from, length)
        try {
            file.inputStream().use { input ->
                input.skip(from)
                pump(input, receiver)
            }
        } catch (e: IOException) {
            throw DownloadException(DownloadFailure.INTERRUPTED, "reading the file failed", e)
        }
    }

    private suspend fun pump(input: InputStream, receiver: DownloadReceiver) {
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return
            if (read > 0) receiver.bytes(buffer, read)
        }
    }

    /** The whole size from "bytes 100-199/200". */
    private fun totalOf(contentRange: String?): Long? = contentRange?.substringAfterLast('/', "")?.toLongOrNull()

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 30_000
        const val BUFFER_BYTES = 64 * 1024
        const val REQUESTED_RANGE_NOT_SATISFIABLE = 416
    }
}
