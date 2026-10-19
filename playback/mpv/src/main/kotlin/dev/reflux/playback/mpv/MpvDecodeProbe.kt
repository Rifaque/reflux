package dev.reflux.playback.mpv

import com.sun.jna.Pointer
import dev.reflux.core.playback.VideoCodec
import java.nio.file.Files
import java.nio.file.Path

/**
 * Asks this machine's playback engine which codecs its hardware decodes, by decoding a few frames of tiny
 * bundled samples (a still grey picture per codec, bit depth, and size, a few kilobytes each) with hardware
 * decoding asked for and seeing what the engine used (`hwdec-current`). Nothing is shown and nothing played
 * aloud: there is no video or audio output, so hardware decoding is asked for with a copy back to memory,
 * which needs no window, and only through the decoders playback's own video output can use
 * ([PLAYBACK_HWDEC_COPY]): asked more widely, the engine finds decoders playback never gets to use.
 *
 * Each codec and depth is tried at 3840x2160, then at 1920x1080 if that was decoded in software. A sample that
 * cannot be opened at all leaves its question unknown rather than answered. Blocking; run it once, off the
 * interface's thread. It takes about a second.
 */
class MpvDecodeProbe private constructor(private val mpv: LibMpv) {
    /** One sample: what it is, and where its file is. */
    data class Sample(val key: DecodeKey, val width: Int, val height: Int, val path: Path)

    fun run(samples: List<Sample> = bundled(), hwdec: String = PLAYBACK_HWDEC_COPY, timeoutMs: Long = 3_000): DecodeEvidence {
        val handle = mpv.mpv_create() ?: return DecodeEvidence.UNKNOWN
        try {
            mapOf(
                "config" to "no", "terminal" to "no", "vo" to "null", "ao" to "null", "audio" to "no", "pause" to "yes",
                "idle" to "yes", "keep-open" to "yes", "sub-auto" to "no", "audio-file-auto" to "no", "ytdl" to "no",
                "load-scripts" to "no", "hwdec" to hwdec,
            ).forEach { (name, value) -> mpv.mpv_set_option_string(handle, name, value) }
            if (mpv.mpv_initialize(handle) < 0) return DecodeEvidence.UNKNOWN
            val software = DecodeEvidence.softwareCodecs(mpv.getString(handle, "decoder-list"))
            val hardware = mutableMapOf<DecodeKey, HardwareDecode>()
            for ((key, sizes) in samples.groupBy { it.key }) {
                for (sample in sizes.sortedByDescending { it.width.toLong() * it.height }) {
                    val used = decode(handle, sample.path, timeoutMs) ?: break // could not be opened: unknown
                    if (used) {
                        hardware[key] = HardwareDecode.Yes(sample.width, sample.height)
                        break
                    }
                    hardware[key] = HardwareDecode.No
                }
            }
            return DecodeEvidence(software, hardware)
        } finally {
            mpv.mpv_terminate_destroy(handle)
        }
    }

    /** Whether [path]'s first frame was decoded in hardware; null when it was not decoded at all. */
    private fun decode(handle: Pointer, path: Path, timeoutMs: Long): Boolean? {
        mpv.mpv_command(handle, arrayOf("loadfile", path.toString(), "replace", null))
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        // The previous sample's end is reported first; only what follows this one's start is about it.
        var started = false
        var loaded = false
        while (System.nanoTime() < deadline) {
            val event = MpvEvent(mpv.mpv_wait_event(handle, ((deadline - System.nanoTime()) / 1e9).coerceAtLeast(0.0)))
            when (event.eventId) {
                LibMpv.EVENT_START_FILE -> started = true
                LibMpv.EVENT_FILE_LOADED -> loaded = started
                // The first frame is decoded: the decoder in use is settled.
                LibMpv.EVENT_VIDEO_RECONFIG -> if (loaded) return hardwareInUse(mpv.getString(handle, "hwdec-current"))
                LibMpv.EVENT_END_FILE -> if (started && !loaded) return null
                LibMpv.EVENT_SHUTDOWN -> return null
            }
        }
        return null
    }

    companion object {
        fun createOrNull(): MpvDecodeProbe? = LibMpv.loadOrNull()?.let(::MpvDecodeProbe)

        /**
         * The copy-back forms of the hardware decoders Reflux's playback can use. On Windows the video output
         * is Direct3D 11, which takes Direct3D 11 decoding only, on the same graphics adapter (asked for any
         * decoder, a laptop's discrete GPU answers through NVDEC, which that output cannot use). Elsewhere the
         * output is OpenGL or Vulkan, which take VA-API, NVDEC, and the others in mpv's safe list.
         */
        val PLAYBACK_HWDEC_COPY: String = if (System.getProperty("os.name").startsWith("Windows")) "d3d11va-copy" else "auto-copy-safe"

        /** mpv's name for the hardware decoder in use, or `no` (or nothing) for software. */
        internal fun hardwareInUse(current: String?): Boolean = !current.isNullOrBlank() && current != "no"

        private val SAMPLES = listOf(
            Triple(VideoCodec.H264, "h264", "mkv"), Triple(VideoCodec.HEVC, "hevc", "mkv"),
            Triple(VideoCodec.VP9, "vp9", "webm"), Triple(VideoCodec.AV1, "av1", "mkv"),
        )

        /** The bundled samples, copied out to a temporary folder the engine can open (deleted on exit). */
        fun bundled(): List<Sample> {
            val folder = Files.createTempDirectory("reflux-decode-probe").also { it.toFile().deleteOnExit() }
            return SAMPLES.flatMap { (codec, name, extension) ->
                listOf(8, 10).flatMap { depth ->
                    listOf(3840 to 2160, 1920 to 1080).mapNotNull { (width, height) ->
                        val file = "$name-$depth-$height.$extension"
                        val stream = MpvDecodeProbe::class.java.getResourceAsStream("/decode-probe/$file") ?: return@mapNotNull null
                        val target = folder.resolve(file)
                        stream.use { Files.copy(it, target) }
                        target.toFile().deleteOnExit()
                        Sample(DecodeKey(codec, depth), width, height, target)
                    }
                }
            }
        }
    }
}
