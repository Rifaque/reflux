package dev.reflux.library

import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.SourceId
import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.net.get
import dev.reflux.core.source.MediaSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries

/**
 * A disk cache of artwork images, so posters and backdrops stay visible while offline (docs/METADATA.md,
 * "Artwork cache").
 *
 * Remote images are downloaded; images inside sources (e.g. `poster.jpg` on a USB drive) are copied, so they
 * survive the drive being unplugged. Each image's file is named by a digest of where it comes from, so the same
 * picture is kept once, and a picture that changes where it comes from is a new file. Least recently used files
 * are evicted beyond [maxBytes]. Nothing is ever written anywhere but [directory]: never beside media.
 *
 * Asked for the same picture while it is on its way, a caller waits for that download rather than starting
 * another; a picture that could not be fetched is not asked for again for [retryAfterMs]. While [remoteAllowed]
 * says no (online lookups turned off), provider images already kept are served and no new ones are fetched;
 * images in the user's own sources are fetched as ever.
 */
class ArtworkCache(
    private val directory: Path,
    private val http: HttpFetcher,
    private val maxBytes: Long = 2L * 1024 * 1024 * 1024,
    private val remoteAllowed: () -> Boolean = { true },
    private val retryAfterMs: Long = 10 * 60_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    init {
        Files.createDirectories(directory)
    }

    private val inFlight = ConcurrentHashMap<Path, CompletableDeferred<Path?>>()
    private val failedAt = ConcurrentHashMap<Path, Long>()

    /** How many downloads or copies were started, for tests and diagnostics. */
    val fetches = AtomicInteger()

    /** What the cache holds now: files and bytes. */
    data class Usage(val files: Int, val bytes: Long)

    fun usage(): Usage {
        if (!Files.isDirectory(directory)) return Usage(0, 0)
        val files = directory.listDirectoryEntries().filter { it.isRegularFile() && !it.fileName.toString().endsWith(".part") }
        return Usage(files.size, files.sumOf { runCatching { Files.size(it) }.getOrDefault(0L) })
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
        if (locator is ArtworkLocator.Remote && !remoteAllowed()) return null
        val target = fileFor(locator)
        // A source's own picture is tried again sooner: the source coming back is the usual reason it failed.
        val retry = if (locator is ArtworkLocator.Remote) retryAfterMs else retryAfterMs / 10
        failedAt[target]?.let { if (clock() - it < retry) return null else failedAt.remove(target) }
        val mine = CompletableDeferred<Path?>()
        val running = inFlight.putIfAbsent(target, mine)
        if (running != null) return running.await()
        return try {
            fetch(locator, target, sources).also { mine.complete(it) }
        } catch (e: Throwable) {
            mine.complete(null)
            throw e
        } finally {
            inFlight.remove(target, mine)
        }
    }

    private suspend fun fetch(locator: ArtworkLocator, target: Path, sources: (SourceId) -> MediaSource?): Path? {
        fetches.incrementAndGet()
        val fetched = try {
            val bytes = when (locator) {
                is ArtworkLocator.Remote -> http.get(locator.url, emptyMap()).takeIf { it.ok }?.body
                is ArtworkLocator.SourceFile -> {
                    val source = sources(locator.location.sourceId) ?: return@fetch null
                    val playable = source.playbackTarget(locator.location.path)
                    if (playable.uri.startsWith("file:")) {
                        withContext(Dispatchers.IO) { Files.readAllBytes(Paths.get(URI(playable.uri))) }
                    } else {
                        http.get(playable.uri, playable.headers).takeIf { it.ok }?.body
                    }
                }
            } ?: return null.also { failedAt[target] = clock() }
            withContext(Dispatchers.IO) { write(target, bytes) }
            target
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null // offline, removed, or unreadable: the UI shows its placeholder
        }
        if (fetched == null) failedAt[target] = clock()
        return fetched
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
        // A cache directory can be taken away while Reflux runs (a system short of space, a user tidying up).
        if (!Files.isDirectory(directory)) return
        val files = directory.listDirectoryEntries().filter { it.isRegularFile() }
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
        Files.createDirectories(directory)
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
