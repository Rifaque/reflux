package dev.reflux.sources.plex

import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.net.HttpRequest
import dev.reflux.core.net.HttpResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plex's video preview thumbnails (B-030): a part's BIF index at `/library/parts/{id}/indexes/sd`, read once a version.
 * Recorded format only: no Plex server with generated previews was available (VERIFICATION.md, "Phase 3").
 */
class PlexTrickplayTest {
    private val requests = mutableListOf<HttpRequest>()
    private var index: ByteArray? = null

    private fun le(value: Long) = byteArrayOf(value.toByte(), (value shr 8).toByte(), (value shr 16).toByte(), (value shr 24).toByte())

    private fun jpeg(width: Int, height: Int, marker: Byte) = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(),
        0xFF.toByte(), 0xC0.toByte(), 0, 11, 8, (height shr 8).toByte(), height.toByte(), (width shr 8).toByte(), width.toByte(), 3, marker, 0,
    )

    private val pictures = listOf(jpeg(320, 136, 1), jpeg(320, 136, 2), jpeg(320, 136, 3))

    private fun bif(): ByteArray {
        val header = ByteArray(64)
        byteArrayOf(0x89.toByte(), 0x42, 0x49, 0x46, 0x0d, 0x0a, 0x1a, 0x0a).copyInto(header)
        le(pictures.size.toLong()).copyInto(header, 12)
        le(2_000).copyInto(header, 16)
        var offset = 64L + (pictures.size + 1) * 8L
        var table = ByteArray(0)
        pictures.forEachIndexed { i, picture ->
            table += le(i.toLong()) + le(offset)
            offset += picture.size
        }
        table += le(0xFFFFFFFFL) + le(offset)
        return header + table + pictures.fold(ByteArray(0)) { all, p -> all + p }
    }

    private val http = HttpFetcher { request ->
        requests += request
        val path = request.url.removePrefix("http://192.168.1.20:32400/").substringBefore('?')
        if (path == "library/parts/501/indexes/sd") index?.let { HttpResult(200, it) } ?: HttpResult(404, ByteArray(0)) else HttpResult(404, ByteArray(0))
    }

    private val source = PlexSource(PlexCredentials("http://192.168.1.20:32400", "machine-1", "srv-token", "dev-1", "alex"), http)
    private val path = "items/201/library/parts/501/1700000000/file.mkv"

    @Test
    fun aPartsIndexGivesItsPicturesOneASheet() = runTest {
        index = bif()
        val manifest = assertNotNull(source.trickplay(path))
        assertEquals(2_000, manifest.intervalMs)
        assertEquals(3, manifest.count)
        assertEquals(320 to 136, manifest.frameWidth to manifest.frameHeight, "the picture's own size, read from it")
        assertEquals(1 to 1, manifest.columns to manifest.rows)
        assertContentEquals(pictures[1], source.trickplaySheet(path, manifest, 1))
        assertNull(source.trickplaySheet(path, manifest, 3))
        assertEquals(1, requests.size, "the index is read once")
        assertTrue(requests.single().headers["X-Plex-Token"] == "srv-token", "with the token in a header, never in the address")
        assertTrue("srv-token" !in requests.single().url)
    }

    @Test
    fun aPartWithoutAnIndexHasNoPreviews() = runTest {
        assertNull(source.trickplay(path))
        assertNull(source.trickplay("images/library/metadata/201/thumb/1"), "not a version")
        index = byteArrayOf(1, 2, 3)
        assertNull(PlexSource(PlexCredentials("http://192.168.1.20:32400", "machine-1", "srv-token", "dev-1", "alex"), http).trickplay(path), "not a BIF")
    }
}
