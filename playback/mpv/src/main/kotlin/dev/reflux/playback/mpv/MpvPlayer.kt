package dev.reflux.playback.mpv

import com.sun.jna.Pointer
import dev.reflux.core.net.Redaction
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.PlaybackFailure
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
import kotlin.math.roundToInt
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
    private val log: ((String) -> Unit)?,
    private val onDecode: ((DecodeObservation) -> Unit)?,
) : Player {
    private val mutableState = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = mutableState.asStateFlow()

    @Volatile private var closed = false

    /** Whether the current file's decoding has been reported (once per file, at its first frame). */
    private var decodeReported = false
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
        setProperty("http-header-fields", headerFields(request.target.headers))
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

    // sub-pos is where the bottom of a subtitle sits, in percent of the window's height from the top.
    override fun setSubtitleClearance(fraction: Float) = setProperty("sub-pos", subtitlePosition(fraction).toString())

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

    private fun setProperty(name: String, value: String) = command("set", name, value)

    /**
     * Queues a command for the engine and returns at once; the engine runs them in order and their effect
     * shows in [state]. Nothing here waits for the engine: callers are usually the UI thread, and an engine
     * that is busy or stuck (opening a network file, starting its video output) must not take the
     * interface with it.
     */
    private fun command(vararg args: String) {
        if (!closed) mpv.mpv_command_async(handle, 0, arrayOf(*args, null))
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
                LibMpv.EVENT_START_FILE -> {
                    decodeReported = false
                    mutableState.update { it.copy(status = PlayerStatus.LOADING, failure = null, error = null) }
                }
                // Read tracks synchronously so external subtitles and status become visible together, and with
                // them the tracks mpv chose on opening the file (its default subtitle, say): a selection
                // published later would let a decision made from the tracks be taken against no selection.
                LibMpv.EVENT_FILE_LOADED -> {
                    val tracks = MpvTracks.playerTracks(mpv.getString(handle, "track-list"))
                    val audio = trackId(mpv.getString(handle, "aid"))
                    val subtitle = trackId(mpv.getString(handle, "sid"))
                    mutableState.update {
                        it.copy(status = runningStatus(), tracks = tracks, selectedAudioId = audio, selectedSubtitleId = subtitle)
                    }
                }
                LibMpv.EVENT_PLAYBACK_RESTART -> {
                    mutableState.update { it.copy(status = runningStatus()) }
                    if (!decodeReported && onDecode != null) reportDecode(onDecode)
                }
                LibMpv.EVENT_END_FILE -> event.data?.let { onEndFile(MpvEventEndFile(it)) }
                LibMpv.EVENT_PROPERTY_CHANGE -> event.data?.let { onProperty(event.replyUserdata.toInt(), MpvEventProperty(it)) }
                LibMpv.EVENT_LOG_MESSAGE -> event.data?.let { data ->
                    val message = MpvEventLogMessage(data)
                    log?.invoke(Redaction.redact("[${message.level}][${message.prefix}] ${message.text.orEmpty().trimEnd()}"))
                }
            }
        }
    }

    /** What decoded this file's first frame, for what is known about this machine ([DecodeEvidence.with]). */
    private fun reportDecode(onDecode: (DecodeObservation) -> Unit) {
        val observation = DecodeEvidence.observation(
            hwdecCurrent = mpv.getString(handle, "hwdec-current"),
            codec = mpv.getString(handle, "video-format"),
            width = mpv.getString(handle, "video-dec-params/w")?.toIntOrNull(),
            height = mpv.getString(handle, "video-dec-params/h")?.toIntOrNull(),
            hwPixelFormat = mpv.getString(handle, "video-dec-params/hw-pixelformat"),
            pixelFormat = mpv.getString(handle, "video-dec-params/pixelformat"),
        ) ?: return
        decodeReported = true
        runCatching { onDecode(observation) }
    }

    private fun onEndFile(end: MpvEventEndFile) {
        mutableState.update {
            when (end.reason) {
                // mpv reports a stream that stops arriving (its server gone mid-playback, a cut-off file) as the end
                // of the file. Only an end at the file's running time is one; an earlier end is the media becoming
                // unreachable, and the position reached stays rather than becoming "watched to the end".
                LibMpv.END_FILE_REASON_EOF -> if (endedEarly(it)) {
                    it.copy(status = PlayerStatus.ERROR, failure = PlaybackFailure.UNREACHABLE, error = "the stream ended before the end of the media")
                } else {
                    it.copy(status = PlayerStatus.ENDED, positionMs = it.durationMs ?: it.positionMs)
                }
                LibMpv.END_FILE_REASON_ERROR ->
                    it.copy(status = PlayerStatus.ERROR, failure = failureOf(end.error), error = mpv.mpv_error_string(end.error))
                // A stop caused by loading the next file is followed by START_FILE; plain stops go idle.
                else -> it.copy(status = PlayerStatus.IDLE)
            }
        }
    }

    private fun onProperty(index: Int, event: MpvEventProperty) {
        val property = Property.entries.getOrNull(index) ?: return
        val data = event.data
        // A property without a value is the engine letting go of a file. What was known about that file
        // stays: the duration in particular is what makes its ending count as watched, and the engine
        // withdraws it at the same moment it reports the ending. The next load starts from a clean state.
        if (event.format == LibMpv.FORMAT_NONE || data == null) return
        mutableState.update { state ->
            // Once a file has ended, its final position and duration are the record of that ending.
            if (state.status == PlayerStatus.ENDED && (property == Property.TIME_POS || property == Property.DURATION)) return@update state
            when (property) {
                Property.TIME_POS -> state.copy(positionMs = (data.getDouble(0) * 1000).roundToLong().coerceAtLeast(0))
                Property.DURATION -> state.copy(durationMs = (data.getDouble(0) * 1000).roundToLong())
                Property.SPEED -> state.copy(speed = data.getDouble(0))
                Property.VOLUME -> state.copy(volume = data.getDouble(0).roundToLong().toInt())
                Property.MUTE -> state.copy(muted = data.getInt(0) != 0)
                Property.TRACK_LIST -> state.copy(tracks = MpvTracks.playerTracks(data.getPointer(0)?.getString(0, "UTF-8")))
                Property.CHAPTERS -> state.copy(chapters = MpvTracks.chapters(data.getPointer(0)?.getString(0, "UTF-8")))
                Property.AID -> state.copy(selectedAudioId = trackId(data.getPointer(0)?.getString(0, "UTF-8")))
                Property.SID -> state.copy(selectedSubtitleId = trackId(data.getPointer(0)?.getString(0, "UTF-8")))
                Property.PAUSE, Property.PAUSED_FOR_CACHE, Property.SEEKING ->
                    if (state.status in RUNNING_STATES) state.copy(status = runningStatus()) else state
            }
        }
    }

    private fun trackId(value: String?): String? = value?.takeIf { it != "no" && it != "auto" }

    /** Playing, paused, or buffering, from mpv's current flags. */
    private fun runningStatus(): PlayerStatus = when {
        flag("pause") -> PlayerStatus.PAUSED
        flag("paused-for-cache") || flag("seeking") -> PlayerStatus.BUFFERING
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
         * @param log receives the engine's own verbose log, line by line, with credentials removed. The engine's
         *   `log-file` is not used for it: what it writes cannot be redacted, and it records every option it is
         *   given, the HTTP headers of a server's stream with its access token among them.
         * @param onDecode told, once per file, how its video was decoded (from the engine's event thread).
         */
        fun create(
            capabilities: DeviceCapabilities,
            options: Map<String, String> = emptyMap(),
            log: ((String) -> Unit)? = null,
            onDecode: ((DecodeObservation) -> Unit)? = null,
        ): MpvPlayer? {
            val mpv = LibMpv.loadOrNull() ?: return null
            val handle = mpv.mpv_create() ?: return null
            (defaultOptions + options - "log-file").forEach { (name, value) -> mpv.mpv_set_option_string(handle, name, value) }
            if (mpv.mpv_initialize(handle) < 0) {
                mpv.mpv_terminate_destroy(handle)
                return null
            }
            if (log != null) mpv.mpv_request_log_messages(handle, "debug")
            return MpvPlayer(mpv, handle, capabilities, log, onDecode)
        }

        /** How far short of its running time a file may end and still have played to its end. */
        internal const val END_TOLERANCE_MS: Long = 15_000

        internal fun endedEarly(state: PlayerState): Boolean =
            state.durationMs.let { it != null && state.positionMs < it - END_TOLERANCE_MS }

        internal fun failureOf(error: Int): PlaybackFailure = when (error) {
            LibMpv.ERROR_LOADING_FAILED -> PlaybackFailure.UNREACHABLE
            LibMpv.ERROR_UNKNOWN_FORMAT, LibMpv.ERROR_NOTHING_TO_PLAY, LibMpv.ERROR_UNSUPPORTED -> PlaybackFailure.UNSUPPORTED
            LibMpv.ERROR_AO_INIT_FAILED, LibMpv.ERROR_VO_INIT_FAILED -> PlaybackFailure.OUTPUT
            else -> PlaybackFailure.UNKNOWN
        }

        /**
         * [headers] as the value of mpv's `http-header-fields`, a comma-separated list: commas within a value
         * are escaped, or the value is cut there and sent in pieces (a Jellyfin token never left the engine).
         */
        internal fun headerFields(headers: Map<String, String>): String =
            headers.entries.joinToString(",") { "${it.key}: ${it.value}".replace("\\", "\\\\").replace(",", "\\,") }

        /** mpv takes plain paths for local files and URLs for everything else. */
        internal fun mpvPath(uri: String): String =
            if (uri.startsWith("file:")) Path.of(URI(uri)).toString() else uri

        /** The `sub-pos` that keeps subtitles above the bottom [clearance] of the window (100 is mpv's own place). */
        internal fun subtitlePosition(clearance: Float): Int = 100 - (clearance.coerceIn(0f, 0.6f) * 100).roundToInt()
    }
}
