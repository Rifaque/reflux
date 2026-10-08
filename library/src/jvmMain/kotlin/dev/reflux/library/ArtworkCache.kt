package dev.reflux.library

import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.SourceId
import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.net.get
import dev.reflux.core.source.MediaSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import kotlin.io.path.isRegularFile

/**
 * A disk cache of artwork images, so posters and backdrops stay visible while offline.
 *
 * Remote images are downloaded; images inside sources (e.g. `poster.jpg` on a USB drive) are copied, so they
 * survive the drive being unplugged. Least recently used files are evicted beyond [maxBytes].
 */
class ArtworkCache(
    private val directory: Path,
    private val http: HttpFetcher,
    private val maxBytes: Long = 2L * 1024 * 1024 * 1024,
) {
    init {
        Files.createDirectories(directory)
    }

    /** The cached file for [locator], if present. Never touches the network or sources. */
    fun cached(locator: ArtworkLocator): Path? =
        fileFor(locator).takeIf { it.isRegularFile() }?.also { touch(it) }

    /**
     * Returns a local file for [locator], fetching it when needed, or null when it cannot be obtained now.
     * Sources are looked up through [sources] for images that live inside them.
     */
    suspend fun get(locator: ArtworkLocator, sources: (SourceId) -> MediaSource?): Path? {
        cached(locator)?.let { return it }
        val target = fileFor(locator)
        return try {
            val bytes = when (locator) {
                is ArtworkLocator.Remote -> http.get(locator.url, emptyMap()).takeIf { it.ok }?.body
                is ArtworkLocator.SourceFile -> {
                    val source = sources(locator.location.sourceId) ?: return null
                    val playable = source.playbackTarget(locator.location.path)
                    if (playable.uri.startsWith("file:")) {
                        withContext(Dispatchers.IO) { Files.readAllBytes(Path.of(URI(playable.uri))) }
                    } else {
                        http.get(playable.uri, playable.headers).takeIf { it.ok }?.body
                    }
                }
            } ?: return null
            withContext(Dispatchers.IO) { write(target, bytes) }
            target
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null // offline, removed, or unreadable: the UI shows its placeholder
        }
    }

    /** Fetches many images with bounded concurrency, then trims the cache. Returns how many are available. */
    suspend fun prefetch(locators: Collection<ArtworkLocator>, sources: (SourceId) -> MediaSource?, concurrency: Int = 4): Int {
        val semaphore = Semaphore(concurrency)
        val available = coroutineScope {
            locators.distinct()
                .map { locator -> async { semaphore.withPermit { get(locator, sources) } } }
                .count { it.await() != null }
        }
        withContext(Dispatchers.IO) { trim() }
        return available
    }

    /** Evicts least recently used files until the cache fits in [maxBytes]. */
    fun trim() {
        val files = Files.list(directory).use { stream -> stream.filter { it.isRegularFile() }.toList() }
            .map { it to Files.readAttributes(it, java.nio.file.attribute.BasicFileAttributes::class.java) }
            .sortedBy { it.second.lastModifiedTime() }
        var total = files.sumOf { it.second.size() }
        for ((file, attributes) in files) {
            if (total <= maxBytes) break
            Files.deleteIfExists(file)
            total -= attributes.size()
        }
    }

    private fun write(target: Path, bytes: ByteArray) {
        val temporary = Files.createTempFile(directory, "download", ".part")
        try {
            Files.write(temporary, bytes)
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun touch(file: Path) {
        runCatching { Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis())) }
    }

    private fun fileFor(locator: ArtworkLocator): Path {
        val key = when (locator) {
            is ArtworkLocator.Remote -> locator.url
            is ArtworkLocator.SourceFile -> "source:${locator.location.sourceId.value}/${locator.location.path}"
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(key.encodeToByteArray())
            .joinToString("") { "%02x".format(it) }.take(32)
        val extension = key.substringAfterLast('.', "").lowercase().takeIf { it in IMAGE_EXTENSIONS } ?: "img"
        return directory.resolve("$digest.$extension")
    }

    private companion object {
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    }
}
