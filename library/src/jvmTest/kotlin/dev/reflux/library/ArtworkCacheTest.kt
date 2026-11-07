package dev.reflux.library

import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.MediaLocation
import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.net.HttpResult
import dev.reflux.sources.local.LocalFolderSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.createDirectories
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArtworkCacheTest {
    private val directory: Path = Files.createTempDirectory("reflux-cache")
    private val media: Path = Files.createTempDirectory("reflux-media")
    private var online = true
    private val downloads = mutableListOf<String>()
    private val http = HttpFetcher { request ->
        val url = request.url
        if (!online) error("offline")
        downloads += url
        if (url.endsWith("missing.jpg")) HttpResult(404, ByteArray(0)) else HttpResult(200, url.encodeToByteArray())
    }

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
        media.toFile().deleteRecursively()
    }

    @Test
    fun remoteImagesAreDownloadedOnceAndServedOffline() = runTest {
        val cache = ArtworkCache(directory.resolve("art"), http)
        val poster = ArtworkLocator.Remote("https://img/poster.jpg")
        val file = assertNotNull(cache.get(poster) { null })
        assertContentEquals("https://img/poster.jpg".encodeToByteArray(), file.readBytes())

        online = false
        assertEquals(file, cache.get(poster) { null })
        assertEquals(1, downloads.size)
        assertNull(cache.get(ArtworkLocator.Remote("https://img/other.jpg")) { null })
        assertNull(cache.get(ArtworkLocator.Remote("https://img/missing.jpg")) { null })
    }

    @Test
    fun aCacheWhoseDirectoryWasTakenAwayStartsAgain() = runTest {
        val cache = ArtworkCache(directory.resolve("art"), http)
        val poster = ArtworkLocator.Remote("https://img/poster.jpg")
        assertNotNull(cache.get(poster) { null })
        // Seen on Android: a system short of space clears directories behind a running app.
        directory.resolve("art").toFile().deleteRecursively()
        cache.trim()
        assertEquals(1, cache.prefetch(listOf(poster), { null }))
        assertNotNull(cache.cached(poster))
    }

    @Test
    fun sourceImagesAreCopiedSoTheyOutliveTheDrive() = runTest {
        val source = LocalFolderSource(media)
        media.resolve("Heat (1995)").createDirectories()
        media.resolve("Heat (1995)/poster.jpg").writeBytes(byteArrayOf(1, 2, 3))
        val cache = ArtworkCache(directory.resolve("art"), http)
        val locator = ArtworkLocator.SourceFile(MediaLocation(source.descriptor.id, "Heat (1995)/poster.jpg"))
        assertNotNull(cache.get(locator) { source })

        media.toFile().deleteRecursively()
        assertContentEquals(byteArrayOf(1, 2, 3), assertNotNull(cache.cached(locator)).readBytes())
    }

    @Test
    fun leastRecentlyUsedFilesAreEvicted() = runTest {
        val cache = ArtworkCache(directory.resolve("art"), http, maxBytes = 60)
        val urls = (1..4).map { "https://img/image-$it.jpg" } // 22 bytes each
        urls.forEachIndexed { index, url ->
            val file = cache.get(ArtworkLocator.Remote(url)) { null }!!
            Files.setLastModifiedTime(file, FileTime.fromMillis(1_000L * (index + 1)))
        }
        cache.trim()
        assertNull(cache.cached(ArtworkLocator.Remote(urls[0])))
        assertNull(cache.cached(ArtworkLocator.Remote(urls[1])))
        assertNotNull(cache.cached(ArtworkLocator.Remote(urls[3])))
    }

    @Test
    fun prefetchCountsAvailableImages() = runTest {
        val cache = ArtworkCache(directory.resolve("art"), http)
        val count = cache.prefetch(
            listOf(ArtworkLocator.Remote("https://img/a.jpg"), ArtworkLocator.Remote("https://img/missing.jpg"), ArtworkLocator.Remote("https://img/a.jpg")),
            sources = { null },
        )
        assertEquals(1, count)
    }

    @Test
    fun theSamePictureAskedForAtOnceIsFetchedOnce() = runTest {
        val slow = HttpFetcher { request ->
            kotlinx.coroutines.delay(100)
            downloads += request.url
            HttpResult(200, request.url.encodeToByteArray())
        }
        val cache = ArtworkCache(directory.resolve("art"), slow)
        val still = ArtworkLocator.Remote("https://img/still.jpg")
        val files = coroutineScope { (1..12).map { async { cache.get(still) { null } } }.awaitAll() }
        assertEquals(1, downloads.size)
        assertEquals(1, cache.fetches.get())
        assertEquals(1, files.filterNotNull().distinct().size)
    }

    @Test
    fun aPictureThatFailedIsNotAskedForAgainAtOnce() = runTest {
        var time = 0L
        val cache = ArtworkCache(directory.resolve("art"), http, clock = { time })
        val missing = ArtworkLocator.Remote("https://img/missing.jpg")
        assertNull(cache.get(missing) { null })
        assertNull(cache.get(missing) { null })
        assertEquals(1, downloads.size, "a grid scrolled back and forth asks the server once")
        time += 11 * 60_000
        assertNull(cache.get(missing) { null })
        assertEquals(2, downloads.size)
    }

    @Test
    fun withOnlineLookupsOffOnlyWhatIsKeptIsShown() = runTest {
        var allowed = true
        val cache = ArtworkCache(directory.resolve("art"), http, remoteAllowed = { allowed })
        val kept = ArtworkLocator.Remote("https://img/kept.jpg")
        assertNotNull(cache.get(kept) { null })
        allowed = false
        assertNotNull(cache.get(kept) { null }, "kept before, shown now")
        assertNull(cache.get(ArtworkLocator.Remote("https://img/new.jpg")) { null })
        assertEquals(1, downloads.size, "nothing new was asked of the provider")
        // A picture in the user's own source is fetched as ever.
        media.resolve("poster.jpg").writeBytes(byteArrayOf(1, 2, 3))
        val source = LocalFolderSource(media)
        assertNotNull(cache.get(ArtworkLocator.SourceFile(MediaLocation(source.descriptor.id, "poster.jpg"))) { source })
    }

    @Test
    fun whatTheCacheHoldsIsAccountedFor() = runTest {
        val cache = ArtworkCache(directory.resolve("art"), http)
        assertEquals(ArtworkCache.Usage(0, 0), cache.usage())
        cache.get(ArtworkLocator.Remote("https://img/a.jpg")) { null }
        cache.get(ArtworkLocator.Remote("https://img/b.jpg")) { null }
        assertEquals(2, cache.usage().files)
        assertEquals("https://img/a.jpg".length + "https://img/b.jpg".length.toLong(), cache.usage().bytes)
        assertTrue(Files.list(media).use { it.count() } == 0L, "nothing is ever written beside media")
    }
}
