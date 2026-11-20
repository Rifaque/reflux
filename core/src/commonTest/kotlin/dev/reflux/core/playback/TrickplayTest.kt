package dev.reflux.core.playback

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Scene previews (B-030): where each picture lies on its sheet, and Plex's BIF index read without trusting it. */
class TrickplayTest {
    // As Jellyfin 12.4.0 described a 40-minute film (VERIFICATION.md, "Phase 3"): 240 pictures every 10 s, 10 by 10.
    private val jellyfin = TrickplayManifest.of("k", intervalMs = 10_000, count = 240, frameWidth = 320, frameHeight = 180, columns = 10, rows = 10)!!

    @Test
    fun aPositionMapsToThePictureTakenAtOrBeforeIt() {
        assertEquals(3, jellyfin.sheets)
        assertEquals(TrickplayFrame(0, 0, 0, 0, 320, 180, 0), jellyfin.frameAt(0))
        assertEquals(TrickplayFrame(0, 0, 0, 0, 320, 180, 0), jellyfin.frameAt(9_999))
        assertEquals(TrickplayFrame(13, 0, 3 * 320, 1 * 180, 320, 180, 130_000), jellyfin.frameAt(135_000))
        // Picture 100 starts the second sheet; 205 is on the third, the sixth of its first row.
        assertEquals(TrickplayFrame(100, 1, 0, 0, 320, 180, 1_000_000), jellyfin.frameAt(1_000_000))
        assertEquals(TrickplayFrame(205, 2, 5 * 320, 0, 320, 180, 2_050_000), jellyfin.frameAt(2_051_000))
    }

    @Test
    fun positionsOutsideTheVideoAreHeldToItsFirstAndLastPictures() {
        assertEquals(0, jellyfin.frameAt(-5_000).index)
        assertEquals(239, jellyfin.frameAt(10_000_000).index)
        assertEquals(239, jellyfin.frame(400).index)
        assertEquals(0, jellyfin.frame(-3).index)
    }

    @Test
    fun aMalformedDescriptionIsNoPreviews() {
        assertNull(TrickplayManifest.of("k", 0, 10, 320, 180))
        assertNull(TrickplayManifest.of("k", 10_000, 0, 320, 180))
        assertNull(TrickplayManifest.of("k", 10_000, 10, null, 180))
        assertNull(TrickplayManifest.of("k", 10_000, 10, 320, 180, columns = 0))
        assertEquals(10, TrickplayManifest.of("k", 10_000, 10, 320, 180)!!.sheets, "one picture a sheet where a source keeps them one by one")
    }

    private fun le(value: Long) = byteArrayOf(value.toByte(), (value shr 8).toByte(), (value shr 16).toByte(), (value shr 24).toByte())

    /** A BIF with [jpegs], each [multiplier] ms apart: the header, the index, then the pictures. */
    private fun bif(jpegs: List<ByteArray>, multiplier: Long = 2_000): ByteArray {
        val header = ByteArray(64)
        byteArrayOf(0x89.toByte(), 0x42, 0x49, 0x46, 0x0d, 0x0a, 0x1a, 0x0a).copyInto(header)
        le(jpegs.size.toLong()).copyInto(header, 12)
        le(multiplier).copyInto(header, 16)
        var offset = 64L + (jpegs.size + 1) * 8L
        val index = ArrayList<Byte>()
        jpegs.forEachIndexed { i, jpeg ->
            index += le(i.toLong()).toList() + le(offset).toList()
            offset += jpeg.size
        }
        index += le(0xFFFFFFFFL).toList() + le(offset).toList()
        return header + index.toByteArray() + jpegs.fold(ByteArray(0)) { all, jpeg -> all + jpeg }
    }

    /** The smallest thing [BifIndex.jpegSize] reads: start of image, then a baseline frame header. */
    private fun jpeg(width: Int, height: Int, marker: Byte = 7) = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(),
        0xFF.toByte(), 0xC0.toByte(), 0, 11, 8, (height shr 8).toByte(), height.toByte(), (width shr 8).toByte(), width.toByte(), 3, marker, 0,
    )

    @Test
    fun aBifGivesEachPictureAndWhenItWasTaken() {
        val pictures = listOf(jpeg(320, 180, 1), jpeg(320, 180, 2), jpeg(320, 180, 3))
        val read = assertNotNull(BifIndex.read(bif(pictures)))
        assertEquals(3, read.count)
        assertEquals(2_000, read.intervalMs)
        assertEquals(listOf(0L, 2_000L, 4_000L), (0 until 3).map(read::timeMs))
        pictures.forEachIndexed { i, picture -> assertContentEquals(picture, read.jpeg(i)) }
        assertEquals(320 to 180, BifIndex.jpegSize(read.jpeg(0)))
    }

    @Test
    fun aMultiplierOfZeroMeansSeconds() {
        val read = assertNotNull(BifIndex.read(bif(listOf(jpeg(10, 10), jpeg(10, 10)), multiplier = 0)))
        assertEquals(1_000, read.timeMs(1))
    }

    @Test
    fun anythingMalformedIsNoPreviews() {
        val good = bif(listOf(jpeg(10, 10), jpeg(10, 10)))
        assertNull(BifIndex.read(ByteArray(10)))
        assertNull(BifIndex.read(good.copyOf().also { it[1] = 0 }), "not the magic")
        assertNull(BifIndex.read(good.copyOf(good.size - 5)), "a picture runs past the end")
        assertNull(BifIndex.read(good.copyOf().also { le(0).copyInto(it, 12) }), "no pictures")
        assertNull(BifIndex.jpegSize(byteArrayOf(1, 2, 3, 4)))
    }
}
