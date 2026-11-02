package dev.reflux.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.reflux.android.playback.AndroidCapabilities
import dev.reflux.android.playback.Media3Player
import dev.reflux.core.playback.PlaybackRequest
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.playback.SubtitleAttachment
import dev.reflux.core.source.PlaybackTarget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A PGS file beside the media, as a server extracts one: a presentation is drawn when its time comes and cleared by
 * the next. The file is made here, segment by segment, so the test needs no real film.
 */
@RunWith(AndroidJUnit4::class)
class PgsAttachmentOnDeviceTest {
    private fun segment(out: ByteArrayOutputStream, timeMs: Long, type: Int, data: ByteArray) {
        val ticks = timeMs * 90
        out.write(byteArrayOf('P'.code.toByte(), 'G'.code.toByte(), (ticks ushr 24).toByte(), (ticks ushr 16).toByte(), (ticks ushr 8).toByte(), ticks.toByte(), 0, 0, 0, 0))
        out.write(byteArrayOf(type.toByte(), (data.size ushr 8).toByte(), data.size.toByte()))
        out.write(data)
    }

    private fun shorts(vararg values: Int) = values.flatMap { listOf((it ushr 8).toByte(), it.toByte()) }.toByteArray()

    /** A 4 by 4 white square at 2 s, gone at 4 s, on a 320 by 180 plane. */
    private fun supFile(): File {
        val out = ByteArrayOutputStream()
        // Composition: plane size, then frame rate, number, state, palette update, palette, one object, its window and crop, its place.
        segment(out, 2_000, 0x16, shorts(320, 180) + byteArrayOf(0x10, 0, 1, 0x80.toByte(), 0, 0, 1, 0, 1, 0, 0) + shorts(10, 20))
        segment(out, 2_000, 0x14, byteArrayOf(0, 0, 1, 235.toByte(), 128.toByte(), 128.toByte(), 255.toByte()))
        val row = byteArrayOf(1, 1, 1, 1, 0, 0)
        val rle = row + row + row + row
        segment(out, 2_000, 0x15, shorts(1) + byteArrayOf(0, 0xC0.toByte()) + byteArrayOf(0, 0, (rle.size + 4).toByte()) + shorts(4, 4) + rle)
        segment(out, 2_000, 0x80, byteArrayOf())
        // The next presentation has no object: the square goes.
        segment(out, 4_000, 0x16, shorts(320, 180) + byteArrayOf(0x10, 0, 2, 0, 0, 0, 0))
        segment(out, 4_000, 0x80, byteArrayOf())
        return File(context.cacheDir, "square.sup").also { it.writeBytes(out.toByteArray()) }
    }

    @Test
    fun aPgsFileBesideTheMediaIsDrawnAndCleared() = runBlocking {
        val clip = File(context.cacheDir, "clip.mp4")
        InstrumentationRegistry.getInstrumentation().context.assets.open("clip.mp4").use { input -> clip.outputStream().use(input::copyTo) }
        val sup = supFile()
        val player = Media3Player(context, AndroidCapabilities.of(context))
        try {
            player.load(
                PlaybackRequest(
                    PlaybackTarget(android.net.Uri.fromFile(clip).toString()), "PGS beside",
                    subtitles = listOf(SubtitleAttachment(PlaybackTarget(android.net.Uri.fromFile(sup).toString()), "en")),
                ),
            )
            val ready = player.await("the clip to play", 30_000) { it.status == PlayerStatus.PLAYING && it.subtitleTracks.isNotEmpty() }
            val track = ready.subtitleTracks.single()
            assertTrue(track.external, "$track")
            assertEquals("en", track.language)
            player.selectSubtitle(track.id)
            val shown = withTimeout(15_000) { player.cues.first { it.isNotEmpty() } }
            val position = player.state.value.positionMs
            val bitmap = shown.single().bitmap!!
            assertEquals(4 to 4, bitmap.width to bitmap.height)
            assertTrue(position in 1_500..3_500, "drawn at its time: $position ms")
            withTimeout(15_000) { player.cues.first { it.isEmpty() } }
            assertTrue(player.state.value.positionMs in 3_500..5_500, "and cleared at the next: ${player.state.value.positionMs} ms")
            assertEquals(PlayerStatus.PLAYING, player.state.value.status, "the picture did not wait for it")
        } finally {
            player.close()
        }
    }
}
