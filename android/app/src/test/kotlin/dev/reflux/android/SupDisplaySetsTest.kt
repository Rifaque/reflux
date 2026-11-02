package dev.reflux.android

import dev.reflux.android.playback.SupDisplaySets
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SupDisplaySetsTest {
    private fun header(type: Int, size: Int, pts: Long) = byteArrayOf(
        'P'.code.toByte(), 'G'.code.toByte(),
        (pts ushr 24).toByte(), (pts ushr 16).toByte(), (pts ushr 8).toByte(), pts.toByte(),
        0, 0, 0, 0,
        type.toByte(), (size ushr 8).toByte(), size.toByte(),
    )

    @Test
    fun aPresentationIsItsSegmentsWithoutTheFileFraming() {
        val sets = SupDisplaySets()
        assertNull(sets.add(header(0x16, 2, 900_000), byteArrayOf(1, 2)), "a composition alone is not a presentation")
        val set = sets.add(header(0x80, 0, 900_000), byteArrayOf())!!
        assertEquals(10_000_000L, set.timeUs, "900,000 ticks of 90 kHz are 10 s")
        assertContentEquals(byteArrayOf(0x16, 0, 2, 1, 2, 0x80.toByte(), 0, 0), set.segments, "type, size, data: what Media3 parses in Matroska")
    }

    @Test
    fun theNextPresentationStartsAfresh() {
        val sets = SupDisplaySets()
        sets.add(header(0x16, 1, 90_000), byteArrayOf(7))
        sets.add(header(0x80, 0, 90_000), byteArrayOf())
        sets.add(header(0x16, 1, 180_000), byteArrayOf(8))
        val second = sets.add(header(0x80, 0, 180_000), byteArrayOf())!!
        assertEquals(2_000_000L, second.timeUs)
        assertEquals(8, second.segments[3].toInt())
    }

    @Test
    fun aSeekDropsAHalfReadPresentation() {
        val sets = SupDisplaySets()
        sets.add(header(0x16, 1, 90_000), byteArrayOf(7))
        sets.reset()
        val set = sets.add(header(0x80, 0, 450_000), byteArrayOf())!!
        assertEquals(5_000_000L, set.timeUs)
        assertEquals(3, set.segments.size)
    }

    @Test
    fun timesUseAllThirtyTwoBits() {
        assertEquals(0xFFFFFFFFL * 1_000 / 90, SupDisplaySets.presentationTimeUs(header(0x80, 0, 0xFFFFFFFFL)))
        assertEquals(0x1234, SupDisplaySets.segmentLength(header(0x15, 0x1234, 0)))
    }

    @Test
    fun anythingElseIsRefused() {
        assertFailsWith<IllegalArgumentException> { SupDisplaySets().add(ByteArray(13), byteArrayOf()) }
    }
}
