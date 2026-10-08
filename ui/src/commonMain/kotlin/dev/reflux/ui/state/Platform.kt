package dev.reflux.ui.state

import androidx.compose.ui.graphics.ImageBitmap
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.SourceId
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.Player

/** Decodes artwork for display. Implemented by the platform shell on top of its disk cache and image decoder. */
interface ArtworkLoader {
    /** An already decoded image, for drawing without a placeholder frame. Must not block. */
    fun cached(locator: ArtworkLocator, widthPx: Int): ImageBitmap?

    /** Loads and decodes [locator] at roughly [widthPx] wide, or returns null when it cannot be obtained now. */
    suspend fun load(locator: ArtworkLocator, widthPx: Int): ImageBitmap?
}

/**
 * What the experience layer needs from a platform shell. Everything here is a platform capability; the
 * experience layer checks it instead of assuming it (docs/ARCHITECTURE.md).
 */
interface Platform {
    val artwork: ArtworkLoader

    /** What this device and its playback engine can play; drives Best Version. */
    val device: DeviceCapabilities

    /** Preferred audio and subtitle languages as ISO 639 codes, most preferred first (from the system locale). */
    val preferredLanguages: List<String>

    /** Whether this is a television: 10-foot type scale and overscan-safe margins. */
    val television: Boolean get() = false

    /** Whether a playback engine is installed. Browsing never depends on it. */
    val canPlay: Boolean

    /**
     * Creates a playback engine attached to the shell's video surface and shows that surface, or returns
     * null when no engine is available.
     */
    suspend fun createPlayer(): Player?

    /** Closes a player from [createPlayer] and hides the video surface. */
    suspend fun releasePlayer(player: Player)

    /** Lets the user choose a media folder and registers it as a read-only source. Null when cancelled. */
    suspend fun addMediaFolder(): SourceId?

    /** Removes a source and what Reflux derived from it. The user's files are never touched. */
    suspend fun removeSource(id: SourceId)

    /** Whether the window is fullscreen, as observable state. */
    val fullscreen: Boolean

    fun requestFullscreen(fullscreen: Boolean)
}
