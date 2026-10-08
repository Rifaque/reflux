package dev.reflux.library

import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.MediaLocation
import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.net.HttpResult
import dev.reflux.sources.local.LocalFolderSource
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

class ArtworkCacheTest {
    private val directory: Path = Files.createTempDirectory("reflux-cache")
    private val media: Path = Files.createTempDirectory("reflux-media")
    private var online = true
    private val downloads = mutableListOf<String>()
    private val http = HttpFetcher { url, _ ->
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
}
