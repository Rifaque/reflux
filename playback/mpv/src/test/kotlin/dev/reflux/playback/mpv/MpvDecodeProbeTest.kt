package dev.reflux.playback.mpv

import dev.reflux.core.playback.VideoCodec
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The decode probe with the real engine, where libmpv is installed. What the hardware answers depends on the
 * machine, so this checks only that every question gets an answer the engine could give, and prints it.
 */
class MpvDecodeProbeTest {
    private fun needs(what: String, present: Boolean) {
        if (System.getenv("REFLUX_REQUIRE_ENGINE_TESTS") == "true") assertTrue(present, what) else assumeTrue(what, present)
    }

    @Test
    fun everyBundledSampleIsThere() {
        val samples = MpvDecodeProbe.bundled()
        assertEquals(16, samples.size, "four codecs, two depths, two sizes")
        assertTrue(samples.all { Files.size(it.path) in 1..64_000 }, "each a few kilobytes")
    }

    @Test
    fun theEngineAnswersEveryQuestion() {
        needs("libmpv not installed", MpvPlayer.isAvailable)
        val probe = assertNotNull(MpvDecodeProbe.createOrNull())
        val started = System.nanoTime()
        val evidence = probe.run()
        val ms = (System.nanoTime() - started) / 1_000_000
        println("Decode probe ($ms ms): ${evidence.summary()}")
        val software = assertNotNull(evidence.software, "the engine lists its decoders")
        assertTrue(setOf(VideoCodec.H264, VideoCodec.HEVC, VideoCodec.VP9, VideoCodec.AV1).all { it in software }, "software decoders for the probed codecs: $software")
        for (codec in listOf(VideoCodec.H264, VideoCodec.HEVC, VideoCodec.VP9, VideoCodec.AV1)) {
            for (depth in listOf(8, 10)) assertNotNull(evidence.hardware[DecodeKey(codec, depth)], "$codec $depth-bit answered")
        }
    }

    @Test
    fun aSampleThatCannotBeOpenedLeavesItsQuestionUnknown() {
        needs("libmpv not installed", MpvPlayer.isAvailable)
        val missing = Files.createTempDirectory("probe").resolve("nothing.mkv")
        val evidence = assertNotNull(MpvDecodeProbe.createOrNull()).run(listOf(MpvDecodeProbe.Sample(DecodeKey(VideoCodec.HEVC, 10), 3840, 2160, missing)))
        assertNull(evidence.hardware[DecodeKey(VideoCodec.HEVC, 10)])
        assertFalse(evidence.hardware.isNotEmpty())
    }
}
