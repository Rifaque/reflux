package dev.reflux.playback.mpv

import com.sun.jna.Pointer
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.PlaybackRequest
import dev.reflux.core.playback.Player
import dev.reflux.core.playback.PlayerState
import dev.reflux.core.playback.PlayerStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.URI
import java.nio.file.Path
import kotlin.math.roundToLong

/**
 * Desktop playback engine backed by libmpv.
 *
 * mpv supplies hardware decoding, libplacebo tone mapping, broad format support, and libass subtitles.
 * Reflux drives it purely through the [Player] contract; user mpv configuration is deliberately not loaded so
 * the default experience is the same on every machine.
 *
 * @param options extra mpv options, e.g. `wid` for embedding into a native window.
 */
class MpvPlayer private constructor(
    private val mpv: LibMpv,
    private val handle: Pointer,
    override val capabilities: DeviceCapabilities,
) : Player {
    private val mutableState = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = mutableState.asStateFlow()

    @Volatile private var closed = false
    private val eventThread = Thread(::eventLoop, "mpv-events").apply { isDaemon = true }

    init {
        Property.entries.forEach { mpv.mpv_observe_property(handle, it.ordinal.toLong(), it.mpvName, it.format) }
        eventThread.start()
    }

    override fun load(request: PlaybackRequest) {
        mutableState.value = PlayerState(status = PlayerStatus.LOADING, volume = state.value.volume, muted = state.value.muted)
        // `start` and `sub-files` are per-load options; reset them on every load.
        setProperty("start", request.startPositionMs?.let { "+" + (it / 1000.0) } ?: "none")
        command("change-list", "sub-files", "clr", "")
        request.subtitles.forEach { command("change-list", "sub-files", "append", mpvPath(it.target.uri)) }
        setProperty("http-header-fields", request.target.headers.entries.joinToString(",") { "${it.key}: ${it.value}" })
        setProperty("force-media-title", request.title)
        setProperty("pause", "no")
        command("loadfile", mpvPath(request.target.uri), "replace")
    }

    override fun play() = setProperty("pause", "no")
    override fun pause() = setProperty("pause", "yes")
    override fun seekTo(positionMs: Long) = command("seek", (positionMs / 1000.0).toString(), "absolute+exact")
    override fun setSpeed(speed: Double) = setProperty("speed", speed.toString())
    override fun setVolume(volume: Int) = setProperty("volume", volume.coerceIn(0, 100).toString())
    override fun setMuted(muted: Boolean) = setProperty("mute", if (muted) "yes" else "no")
    override fun selectAudio(trackId: String) = setProperty("aid", trackId)
    override fun selectSubtitle(trackId: String?) = setProperty("sid", trackId ?: "no")

    override fun stop() {
        command("stop")
    }

    override fun close() {
        if (closed) return
        closed = true
        mpv.mpv_wakeup(handle)
        eventThread.join(2_000)
        mpv.mpv_terminate_destroy(handle)
    }

    private fun setProperty(name: String, value: String) {
        if (!closed) mpv.mpv_set_property_string(handle, name, value)
    }

    private fun command(vararg args: String) {
        if (!closed) mpv.mpv_command(handle, arrayOf(*args, null))
    }

    private fun eventLoop() {
        while (!closed) {
            val event = MpvEvent(mpv.mpv_wait_event(handle, -1.0))
            when (event.eventId) {
                LibMpv.EVENT_SHUTDOWN -> {
                    // The user closed mpv's own window (standalone use) or the player is being destroyed.
                    mutableState.update { if (it.status == PlayerStatus.ENDED) it else it.copy(status = PlayerStatus.IDLE) }
                    return
                }
                LibMpv.EVENT_START_FILE -> mutableState.update { it.copy(status = PlayerStatus.LOADING, error = null) }
                // Read tracks synchronously so external subtitles and status become visible together.
                LibMpv.EVENT_FILE_LOADED -> {
                    val tracks = MpvTracks.playerTracks(mpv.getString(handle, "track-list"))
                    mutableState.update { it.copy(status = runningStatus(), tracks = tracks) }
                }
                LibMpv.EVENT_PLAYBACK_RESTART -> mutableState.update { it.copy(status = runningStatus()) }
                LibMpv.EVENT_END_FILE -> event.data?.let { onEndFile(MpvEventEndFile(it)) }
                LibMpv.EVENT_PROPERTY_CHANGE -> event.data?.let { onProperty(event.replyUserdata.toInt(), MpvEventProperty(it)) }
            }
        }
    }

    private fun onEndFile(end: MpvEventEndFile) {
        mutableState.update {
            when (end.reason) {
                LibMpv.END_FILE_REASON_EOF -> it.copy(status = PlayerStatus.ENDED, positionMs = it.durationMs ?: it.positionMs)
                LibMpv.END_FILE_REASON_ERROR -> it.copy(status = PlayerStatus.ERROR, error = mpv.mpv_error_string(end.error))
                // A stop caused by loading the next file is followed by START_FILE; plain stops go idle.
                else -> it.copy(status = PlayerStatus.IDLE)
            }
        }
    }

    private fun onProperty(index: Int, event: MpvEventProperty) {
        val property = Property.entries.getOrNull(index) ?: return
        val data = event.data
        if (event.format == LibMpv.FORMAT_NONE || data == null) {
            if (property == Property.DURATION) mutableState.update { it.copy(durationMs = null) }
            return
        }
        mutableState.update { state ->
            when (property) {
                Property.TIME_POS -> state.copy(positionMs = (data.getDouble(0) * 1000).roundToLong().coerceAtLeast(0))
                Property.DURATION -> state.copy(durationMs = (data.getDouble(0) * 1000).roundToLong())
                Property.SPEED -> state.copy(speed = data.getDouble(0))
                Property.VOLUME -> state.copy(volume = data.getDouble(0).roundToLong().toInt())
                Property.MUTE -> state.copy(muted = data.getInt(0) != 0)
                Property.TRACK_LIST -> state.copy(tracks = MpvTracks.playerTracks(data.getPointer(0)?.getString(0, "UTF-8")))
                Property.CHAPTERS -> state.copy(chapters = MpvTracks.chapters(data.getPointer(0)?.getString(0, "UTF-8")))
                Property.AID -> state.copy(selectedAudioId = trackId(data))
                Property.SID -> state.copy(selectedSubtitleId = trackId(data))
                Property.PAUSE, Property.PAUSED_FOR_CACHE, Property.SEEKING ->
                    if (state.status in RUNNING_STATES) state.copy(status = runningStatus()) else state
            }
        }
    }

    private fun trackId(data: Pointer): String? =
        data.getPointer(0)?.getString(0, "UTF-8")?.takeIf { it != "no" && it != "auto" }

    /** Playing, paused, or buffering, from mpv's current flags. */
    private fun runningStatus(): PlayerStatus = when {
        flag("paused-for-cache") || flag("seeking") -> PlayerStatus.BUFFERING
        flag("pause") -> PlayerStatus.PAUSED
        else -> PlayerStatus.PLAYING
    }

    private fun flag(name: String): Boolean = mpv.getString(handle, name) == "yes"

    private enum class Property(val mpvName: String, val format: Int) {
        TIME_POS("time-pos", LibMpv.FORMAT_DOUBLE),
        DURATION("duration", LibMpv.FORMAT_DOUBLE),
        SPEED("speed", LibMpv.FORMAT_DOUBLE),
        VOLUME("volume", LibMpv.FORMAT_DOUBLE),
        MUTE("mute", LibMpv.FORMAT_FLAG),
        PAUSE("pause", LibMpv.FORMAT_FLAG),
        PAUSED_FOR_CACHE("paused-for-cache", LibMpv.FORMAT_FLAG),
        SEEKING("seeking", LibMpv.FORMAT_FLAG),
        TRACK_LIST("track-list", LibMpv.FORMAT_STRING),
        CHAPTERS("chapter-list", LibMpv.FORMAT_STRING),
        AID("aid", LibMpv.FORMAT_STRING),
        SID("sid", LibMpv.FORMAT_STRING),
    }

    companion object {
        private val RUNNING_STATES = setOf(PlayerStatus.PLAYING, PlayerStatus.PAUSED, PlayerStatus.BUFFERING)

        /** Options that make libmpv behave as an embedded engine with Reflux's defaults. */
        val defaultOptions: Map<String, String> = linkedMapOf(
            "config" to "no",
            "terminal" to "no",
            "idle" to "yes",
            "keep-open" to "no",
            "input-default-bindings" to "no",
            "input-vo-keyboard" to "no",
            "osc" to "no",
            "ytdl" to "no",
            "sub-auto" to "no",
            "audio-file-auto" to "no",
            "hwdec" to "auto-safe",
            // gpu-next first; plain outputs keep playback working without usable GPU drivers (VMs, remote desktops).
            "vo" to if (System.getProperty("os.name").startsWith("Windows")) "gpu-next,gpu" else "gpu-next,gpu,xv,x11",
            // A missing or disconnected audio device must not stop video playback.
            "audio-fallback-to-null" to "yes",
        )

        /** Whether libmpv can be loaded on this machine. */
        val isAvailable: Boolean by lazy { LibMpv.loadOrNull() != null }

        /**
         * Creates a player, or returns null if libmpv is missing or fails to initialize.
         *
         * @param options merged over [defaultOptions]; use `vo=null`/`ao=null` for headless use.
         */
        fun create(capabilities: DeviceCapabilities, options: Map<String, String> = emptyMap()): MpvPlayer? {
            val mpv = LibMpv.loadOrNull() ?: return null
            val handle = mpv.mpv_create() ?: return null
            (defaultOptions + options).forEach { (name, value) -> mpv.mpv_set_option_string(handle, name, value) }
            if (mpv.mpv_initialize(handle) < 0) {
                mpv.mpv_terminate_destroy(handle)
                return null
            }
            return MpvPlayer(mpv, handle, capabilities)
        }

        /** mpv takes plain paths for local files and URLs for everything else. */
        internal fun mpvPath(uri: String): String =
            if (uri.startsWith("file:")) Path.of(URI(uri)).toString() else uri
    }
}
