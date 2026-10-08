package dev.reflux.playback.mpv

import com.sun.jna.Pointer
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.source.PlaybackTarget
import kotlin.math.roundToLong

/**
 * Reads real stream information (codecs, resolution, HDR, tracks, duration) with libmpv, without
 * rendering or audio output. Replaces file-name hints with probed facts.
 */
class MpvProbe private constructor(private val mpv: LibMpv) {

    /** Probes [target]; returns null if the file cannot be opened. Blocking; call from a background thread. */
    fun probe(target: PlaybackTarget, timeoutMs: Long = 10_000): StreamInfo? {
        val handle = mpv.mpv_create() ?: return null
        try {
            mapOf(
                "config" to "no", "terminal" to "no", "vo" to "null", "ao" to "null", "pause" to "yes",
                "idle" to "yes", "sub-auto" to "no", "audio-file-auto" to "no", "ytdl" to "no",
                "hwdec" to "no", "load-scripts" to "no",
            ).forEach { (name, value) -> mpv.mpv_set_option_string(handle, name, value) }
            if (mpv.mpv_initialize(handle) < 0) return null
            target.headers.takeIf { it.isNotEmpty() }?.let { headers ->
                mpv.mpv_set_property_string(handle, "http-header-fields", headers.entries.joinToString(",") { "${it.key}: ${it.value}" })
            }
            mpv.mpv_command(handle, arrayOf("loadfile", MpvPlayer.mpvPath(target.uri), "replace", null))
            if (!awaitLoaded(handle, timeoutMs)) return null
            // Colour information is known once the first frame is decoded; wait briefly for it.
            awaitEvent(handle, LibMpv.EVENT_VIDEO_RECONFIG, VIDEO_PARAMS_TIMEOUT_MS)
            return read(handle)
        } finally {
            mpv.mpv_terminate_destroy(handle)
        }
    }

    private fun awaitLoaded(handle: Pointer, timeoutMs: Long): Boolean {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            val event = MpvEvent(mpv.mpv_wait_event(handle, remainingSeconds(deadline)))
            when (event.eventId) {
                LibMpv.EVENT_FILE_LOADED -> return true
                LibMpv.EVENT_END_FILE, LibMpv.EVENT_SHUTDOWN -> return false
            }
        }
        return false
    }

    private fun awaitEvent(handle: Pointer, eventId: Int, timeoutMs: Long) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            val event = MpvEvent(mpv.mpv_wait_event(handle, remainingSeconds(deadline)))
            if (event.eventId == eventId || event.eventId == LibMpv.EVENT_END_FILE) return
        }
    }

    private fun remainingSeconds(deadline: Long): Double = ((deadline - System.nanoTime()) / 1e9).coerceAtLeast(0.0)

    private fun read(handle: Pointer): StreamInfo {
        val tracks = MpvTracks.parse(mpv.getString(handle, "track-list"))
        val videoTrack = tracks.firstOrNull { MpvTracks.typeOf(it) == "video" && !MpvTracks.isAlbumArt(it) }
        val gamma = mpv.getString(handle, "video-params/gamma")
        val pixelFormat = mpv.getString(handle, "video-params/pixelformat").orEmpty()
        val video = videoTrack?.let(MpvTracks::video)?.let { base ->
            val range = when {
                base.dolbyVisionProfile != null -> DynamicRange.DOLBY_VISION
                gamma == "pq" -> DynamicRange.HDR10
                gamma == "hlg" -> DynamicRange.HLG
                else -> DynamicRange.SDR
            }
            base.copy(
                dynamicRange = range,
                bitDepth = bitDepthOf(pixelFormat) ?: if (range != DynamicRange.SDR) 10 else null,
            )
        }
        return StreamInfo(
            container = containerOf(mpv.getString(handle, "file-format")),
            video = video,
            audio = tracks.filter { MpvTracks.typeOf(it) == "audio" }.map(MpvTracks::audio),
            subtitles = tracks.filter { MpvTracks.typeOf(it) == "sub" && !MpvTracks.isExternal(it) }
                .map(MpvTracks::subtitle),
            durationMs = mpv.getString(handle, "duration")?.toDoubleOrNull()?.let { (it * 1000).roundToLong() },
            bitrateBps = null,
        )
    }

    companion object {
        private const val VIDEO_PARAMS_TIMEOUT_MS = 2_000L

        fun createOrNull(): MpvProbe? = LibMpv.loadOrNull()?.let(::MpvProbe)

        internal fun bitDepthOf(pixelFormat: String): Int? =
            Regex("""p(\d{2})(?:le|be)?$""").find(pixelFormat)?.groupValues?.get(1)?.toInt()
                ?: if (pixelFormat.isNotEmpty()) 8 else null

        /** Maps mpv/FFmpeg demuxer names (`matroska,webm`, `mov,mp4,m4a,...`) to containers. */
        internal fun containerOf(format: String?): Container {
            val names = format.orEmpty().lowercase().split(',')
            return when {
                "matroska" in names || "mkv" in names -> Container.MATROSKA
                "mp4" in names || "mov" in names -> Container.MP4
                "avi" in names -> Container.AVI
                "mpegts" in names -> Container.MPEG_TS
                "mpeg" in names -> Container.MPEG_PS
                "webm" in names -> Container.WEBM
                "asf" in names -> Container.WMV
                "flv" in names -> Container.FLV
                "ogg" in names -> Container.OGG
                else -> Container.UNKNOWN
            }
        }
    }
}
