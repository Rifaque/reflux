package dev.reflux.android

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.reflux.android.playback.AndroidCapabilities
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.AudioStream
import dev.reflux.core.playback.Capabilities
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.playback.Support
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoProfile
import dev.reflux.core.playback.VideoStream
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What this device says it can do, read through the real platform and Media3, in the shared model. Holds what must
 * be true anywhere and logs the rest (tag `RefluxCapabilities`) for docs/PLAYBACK.md: no device is named in a rule.
 */
@RunWith(AndroidJUnit4::class)
class CapabilityReportOnDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun report(line: String) {
        Log.i("RefluxCapabilities", line)
    }

    @Test
    fun theDeviceDescribesItself() {
        val device = AndroidCapabilities.of(context)
        assertTrue(device.videoDecoders.any { it.codec == VideoCodec.H264 }, "every Android device decodes H.264")
        assertTrue(device.videoDecoders.any { it.profiles != null }, "decoders declare profiles")
        assertTrue(device.videoDecoders.any { !it.frameRates.isNullOrEmpty() }, "and frame rates")
        assertTrue(AudioCodec.AAC in device.audioDecoders)

        for (decoder in device.videoDecoders.sortedBy { it.codec.name }) {
            report("video ${decoder.codec} hardware=${decoder.hardware} ${decoder.maxWidth}x${decoder.maxHeight} depth=${decoder.maxBitDepth} profiles=${decoder.profiles} rates=${decoder.frameRates?.joinToString { "${it.height}p${it.maxFrameRate.toInt()}" }}")
        }
        report("dolby vision profiles ${device.dolbyVisionProfiles?.sorted()}")
        report("audio decoders ${device.audioDecoders.sortedBy { it.name }} limits ${device.audioDecoderLimits}")
        report("output channels ${device.maxOutputChannels} known=${device.maxOutputChannelsKnown} passthrough ${device.audioPassthrough} known=${device.audioPassthroughKnown} atmos ${device.atmosPassthrough}")
        report("display ${device.display}")

        // The reference file's class, in the same words on every device.
        val reference = VideoStream(VideoCodec.HEVC, 1920, 1080, 10, frameRate = 23.976, profile = VideoProfile.MAIN_10, level = 120)
        report("reference video ${Capabilities.video(reference, device)}")
        report("reference audio ${Capabilities.audio(AudioStream(AudioCodec.EAC3, channels = 6, atmos = true, sampleRate = 48_000), device)}")
        report("hi10p ${Capabilities.video(VideoStream(VideoCodec.H264, 1920, 1080, 10, frameRate = 23.976, profile = VideoProfile.HIGH_10, level = 41), device)}")
        report("dts ${Capabilities.audio(AudioStream(AudioCodec.DTS, channels = 6, sampleRate = 48_000), device)}")
        assertEquals(Support.SUPPORTED, Capabilities.video(VideoStream(VideoCodec.H264, 1280, 720, 8, frameRate = 30.0, profile = VideoProfile.HIGH, level = 31), device).decode)
        assertEquals(Support.SUPPORTED, Capabilities.subtitle(SubtitleFormat.SRT, external = true, device).render)
    }
}
