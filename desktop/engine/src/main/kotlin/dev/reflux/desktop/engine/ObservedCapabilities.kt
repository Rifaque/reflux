package dev.reflux.desktop.engine

import com.sun.jna.Native
import com.sun.jna.Pointer
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.DisplayCapabilities
import dev.reflux.playback.mpv.DecodeEvidence
import dev.reflux.playback.mpv.DecodeObservation
import dev.reflux.playback.mpv.DesktopCapabilities
import dev.reflux.playback.mpv.ObservedAudioOutput
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Window

/** The display playback goes to, as last read: its size, and whether it shows HDR (null when not said). */
data class ObservedDisplay(val width: Int?, val height: Int?, val hdr: HdrState?) {
    val capabilities: DisplayCapabilities get() = DisplayCapabilities(width, height)

    companion object {
        /** Nothing is known about it: no window yet, and no screen to ask (a headless run). */
        val UNKNOWN = ObservedDisplay(null, null, null)
    }
}

/**
 * Where a display reading comes from. On Windows, from the system's own description of the monitor the window
 * is on (mode and HDR state); elsewhere, from the toolkit's description of the screen the window is on, which
 * says nothing of HDR. Reads are cheap (well under a millisecond) but not free: they happen when the window
 * moves to another screen, is activated, or the screens change, never during drawing.
 */
fun interface DisplayReader {
    /** The display [window] is on, or with no window the toolkit's default screen; null when there is none to read. */
    fun read(window: Window?): ObservedDisplay?

    companion object {
        fun forThisSystem(os: String = System.getProperty("os.name")): DisplayReader {
            val toolkit = DisplayReader(::toolkitDisplay)
            if (!os.startsWith("Windows")) return toolkit
            val windows = runCatching { WindowsDisplays() }.getOrNull() ?: return toolkit
            return DisplayReader { window ->
                val handle: Pointer? = window?.takeIf { it.isDisplayable }?.let { runCatching { Native.getWindowPointer(it) }.getOrNull() }
                val monitor = runCatching { if (handle != null) windows.monitorOf(handle) else windows.primary().takeIf { window == null } }.getOrNull()
                monitor?.let { ObservedDisplay(it.width, it.height, it.hdr) } ?: toolkit.read(window)
            }
        }

        /** The screen the toolkit puts [window] on (where most of it is), in its real pixels. */
        private fun toolkitDisplay(window: Window?): ObservedDisplay? {
            if (GraphicsEnvironment.isHeadless()) return null
            val device: GraphicsDevice = window?.graphicsConfiguration?.device ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
            val mode = device.displayMode ?: return null
            if (mode.width <= 0 || mode.height <= 0) return null
            return ObservedDisplay(mode.width, mode.height, hdr = null)
        }
    }
}

/**
 * What this desktop can show and decode, as observed: the display the window is on, and the engine's decoders
 * as the start-up probe and real playbacks showed them. Best Version reads [current] at each decision; it is
 * rebuilt only when something observed changes, and [onChange] is then told (from whichever thread observed it).
 */
class ObservedCapabilities(
    private val reader: DisplayReader = DisplayReader.forThisSystem(),
    private val report: (String) -> Unit = {},
) {
    @Volatile private var display: ObservedDisplay = ObservedDisplay.UNKNOWN
    @Volatile private var decode: DecodeEvidence = DecodeEvidence.UNKNOWN
    @Volatile private var audioOutput: ObservedAudioOutput? = null
    @Volatile private var snapshot: DeviceCapabilities = build()

    /** Told when [current] changed. */
    @Volatile var onChange: (() -> Unit)? = null

    fun current(): DeviceCapabilities = snapshot

    /** Whether the start-up probe has answered (whatever it found). */
    @Volatile var probeFinished: Boolean = false
        private set

    val currentDisplay: ObservedDisplay get() = display
    val currentDecode: DecodeEvidence get() = decode

    /** Reads the display [window] is on (or the default screen without one). Returns whether anything changed. */
    fun observeDisplay(window: Window?): Boolean {
        val read = runCatching { reader.read(window) }.getOrNull() ?: ObservedDisplay.UNKNOWN
        return update(read, decode) { "Display for playback: ${describe(read)}" }
    }

    /** What the probe found at start. */
    fun probed(evidence: DecodeEvidence, elapsedMs: Long): Boolean =
        update(display, evidence) { "Hardware decoding ($elapsedMs ms to find out): ${evidence.summary()}" }.also { probeFinished = true }

    /** What a real playback showed. */
    fun decoded(observation: DecodeObservation): Boolean {
        val next = decode.with(observation)
        return update(display, next) { "A playback changed what is known of decoding: ${next.summary()}" }
    }

    /** How many channels a real playback sent out, of how many it had. */
    @Synchronized
    fun audioOutput(source: Int, output: Int): Boolean {
        val next = audioOutput?.with(source, output) ?: ObservedAudioOutput.first(source, output)
        if (next == audioOutput) return false
        audioOutput = next
        snapshot = build()
        report("A playback changed what is known of the audio output: ${next.channels} channels${if (next.exact) ", its limit" else " at least"}")
        onChange?.invoke()
        return true
    }

    @Synchronized
    private fun update(nextDisplay: ObservedDisplay, nextDecode: DecodeEvidence, describe: () -> String): Boolean {
        if (nextDisplay == display && nextDecode == decode) return false
        display = nextDisplay
        decode = nextDecode
        snapshot = build()
        report(describe())
        onChange?.invoke()
        return true
    }

    private fun build(): DeviceCapabilities =
        DesktopCapabilities.of(display.capabilities, decode, displayShowsHdr = display.hdr == HdrState.ON, audioOutput = audioOutput)

    private fun describe(display: ObservedDisplay): String =
        (if (display.width != null) "${display.width}x${display.height}" else "size unknown") + ", HDR " + when (display.hdr) {
            HdrState.ON -> if (DesktopCapabilities.ENGINE_OUTPUTS_HDR) "on" else "on (played as SDR: the engine does not output HDR)"
            HdrState.OFF -> "off"
            HdrState.UNSUPPORTED -> "not supported"
            null -> "not reported"
        }
}
