package dev.reflux.sources.jellyfin

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
 * Jellyfin's trickplay tiles (B-030), in the shape Jellyfin 12.4.0 answered with (VERIFICATION.md, "Phase 3"):
 * `BaseItemDto.Trickplay` per media source and width, and the tiles at `Videos/{item}/Trickplay/{width}/{index}.jpg`.
 */
class JellyfinTrickplayTest {
    private val requests = mutableListOf<HttpRequest>()
    private var online = true
    private var trickplay = """{"s1":{"320":{"Width":320,"Height":180,"TileWidth":10,"TileHeight":10,"ThumbnailCount":240,"Interval":10000,"Bandwidth":6683},
        "640":{"Width":640,"Height":360,"TileWidth":5,"TileHeight":5,"ThumbnailCount":240,"Interval":10000}}}"""
    private val sheet = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)

    private val http = HttpFetcher { request ->
        requests += request
        if (!online) error("offline")
        val path = request.url.removePrefix("https://jf.example.com/").substringBefore('?')
        when {
            path == "Items" -> HttpResult(200, """{"Items":[{"Id":"m1","Trickplay":$trickplay}]}""".encodeToByteArray())
            path.startsWith("Videos/m1/Trickplay/320/") && path.removePrefix("Videos/m1/Trickplay/320/") in setOf("0.jpg", "1.jpg", "2.jpg") -> HttpResult(200, sheet)
            else -> HttpResult(404, ByteArray(0))
        }
    }

    private val source = JellyfinSource(JellyfinCredentials("https://jf.example.com", "srv1", "u1", "tok", "dev1"), http)

    @Test
    fun theWidthNearestThePreviewSizeIsUsedAndDescribedExactly() = runTest {
        val manifest = assertNotNull(source.trickplay("items/m1/sources/s1"))
        assertEquals(10_000, manifest.intervalMs)
        assertEquals(240, manifest.count)
        assertEquals(320 to 180, manifest.frameWidth to manifest.frameHeight)
        assertEquals(10 to 10, manifest.columns to manifest.rows)
        assertEquals(3, manifest.sheets)
        val asked = requests.single()
        assertTrue("ids=m1" in asked.url && "Fields=Trickplay" in asked.url && "userId=u1" in asked.url, asked.url)
        assertTrue(asked.headers.getValue("Authorization").contains("Token=\"tok\""), "asked as the account")
        // Asked once a version: the description is remembered.
        source.trickplay("items/m1/sources/s1")
        assertEquals(1, requests.size)
    }

    @Test
    fun aSheetIsFetchedWithTheAccountsTokenForItsMediaSource() = runTest {
        val manifest = assertNotNull(source.trickplay("items/m1/sources/s1"))
        assertContentEquals(sheet, source.trickplaySheet("items/m1/sources/s1", manifest, 2))
        val asked = requests.last()
        assertTrue(asked.url.startsWith("https://jf.example.com/Videos/m1/Trickplay/320/2.jpg?MediaSourceId=s1"), asked.url)
        assertTrue(asked.headers.getValue("Authorization").contains("Token=\"tok\""))
        assertNull(source.trickplaySheet("items/m1/sources/s1", manifest, 3), "no sheet past the last")
    }

    @Test
    fun aVersionWithoutPreviewsHasNone_andAnUnreachableServerIsAskedAgainLater() = runTest {
        assertNull(source.trickplay("items/m1/sources/other"), "another media source has none")
        assertNull(source.trickplay("videos/m1/s1/subtitles/2/stream.srt"), "not a version")
        trickplay = "{}"
        online = false
        assertNull(source.trickplay("items/m1/sources/s1"))
        online = true
        trickplay = """{"s1":{"320":{"Width":320,"Height":180,"TileWidth":10,"TileHeight":10,"ThumbnailCount":240,"Interval":10000}}}"""
        assertNotNull(source.trickplay("items/m1/sources/s1"), "not remembered as none while the server could not be reached")
    }

    @Test
    fun aMalformedDescriptionIsNoPreviews_andAFailedSheetIsNone() = runTest {
        trickplay = """{"s1":{"320":{"Width":0,"Height":180,"TileWidth":10,"TileHeight":10,"ThumbnailCount":240,"Interval":10000}}}"""
        assertNull(source.trickplay("items/m1/sources/s1"))
        val other = JellyfinSource(JellyfinCredentials("https://jf.example.com", "srv1", "u1", "tok", "dev1"), http)
        trickplay = """{"s1":{"320":{"Width":320,"Height":180,"TileWidth":10,"TileHeight":10,"ThumbnailCount":240,"Interval":10000}}}"""
        val manifest = assertNotNull(other.trickplay("items/m1/sources/s1"))
        online = false
        assertNull(other.trickplaySheet("items/m1/sources/s1", manifest, 0), "offline: no sheet, no failure")
    }
}
