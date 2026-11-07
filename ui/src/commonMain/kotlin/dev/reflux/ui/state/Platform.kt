package dev.reflux.ui.state

import androidx.compose.ui.graphics.ImageBitmap
import dev.reflux.core.BuildIdentity
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.SourceId
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.Player
import dev.reflux.library.Offline
import dev.reflux.library.SourceConnections
import dev.reflux.ui.legal.NoticeTarget

/** Decodes artwork for display. Implemented by the platform shell on top of its disk cache and image decoder. */
interface ArtworkLoader {
    /** An already decoded image, for drawing without a placeholder frame. Must not block. */
    fun cached(locator: ArtworkLocator, widthPx: Int): ImageBitmap?

    /** Loads and decodes [locator] at roughly [widthPx] wide, or returns null when it cannot be obtained now. */
    suspend fun load(locator: ArtworkLocator, widthPx: Int): ImageBitmap?
}

/**
 * What the device's accessibility settings ask of the interface. Each is what the system reports, never a
 * setting of Reflux's own; where a platform does not say, the default stands.
 */
data class AccessibilityPreferences(
    /** A screen reader is running: what a sighted user finds by moving the pointer has to stay in the tree. */
    val screenReader: Boolean = false,
    /** The user asked for less motion: state changes at once rather than travelling there. */
    val reduceMotion: Boolean = false,
    /** Text size on top of what the toolkit already applies (Android applies the system's own; a desktop does not). */
    val textScale: Float = 1f,
    /** The user asked for less transparency (Windows' "Transparency effects" off): material is drawn opaque. */
    val reduceTransparency: Boolean = false,
    /** The user asked for more contrast (high contrast, high-contrast text): opaque material, stronger content and edges. */
    val highContrast: Boolean = false,
)

/** What came of asking the user for a media folder. */
sealed interface FolderChoice {
    /** The user closed the picker without choosing. */
    data object Cancelled : FolderChoice

    /** The folder is now a read-only source. */
    data class Added(val sourceId: SourceId) : FolderChoice

    /** The folder is already in the library, as (part of) the source named [sourceName]. */
    data class AlreadyInLibrary(val sourceId: SourceId, val sourceName: String) : FolderChoice

    /** [name] exists but cannot be read: missing permissions, or a drive or share that is not connected. */
    data class Unreadable(val name: String) : FolderChoice

    /** What was chosen is not a folder on a filesystem (a virtual location, a file). */
    data class NotAFolder(val name: String) : FolderChoice
}

/** What fullscreen is on a platform. */
enum class FullscreenMode {
    /** A window that can fill its screen and come back (a desktop). */
    WINDOW,

    /** The application already fills the screen; fullscreen turns the picture across its long side (a phone or tablet). */
    LANDSCAPE,

    /** Always the whole screen, one way up (a television): there is nothing to offer. */
    NONE,
}

/** A folder as a platform's storage shows it. [id] is the platform's own way of finding it again. */
data class BrowsedFolder(val id: String, val name: String)

/** What is directly inside a folder: the folders to go further into, and how many videos lie in it. */
data class FolderListing(val folders: List<BrowsedFolder>, val videos: Int)

/**
 * The device's storage, for platforms that have no folder picker of their own to offer (televisions). The
 * experience layer draws the picker over it, so choosing a folder works with whatever is in the user's hand.
 */
interface FolderBrowser {
    /** Where browsing starts: the device's storage and each drive connected to it. Null when the user did not let Reflux read them. */
    suspend fun roots(): List<BrowsedFolder>?

    suspend fun list(folder: BrowsedFolder): FolderListing

    /** Registers [folder] as a read-only source. */
    suspend fun add(folder: BrowsedFolder): FolderChoice
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

    /** Whether this is a television: a 10-foot layout inside overscan-safe margins, driven by a remote. */
    val television: Boolean get() = false

    /**
     * Whether loudness is the device's own business (its volume keys, a television's remote). The player
     * then offers no volume of its own, and the keys that would have changed it are free for something else.
     */
    val systemVolume: Boolean get() = false

    /**
     * Keeping copies on the device for watching without their sources, where the platform has it set up;
     * null where it does not, and then nothing about downloads is offered.
     */
    val offline: Offline? get() = null

    /**
     * Connecting servers and network shares by their address, with the kinds this platform has; null where
     * it has none, and then only folders can be added.
     */
    val connections: SourceConnections? get() = null

    /** The storage to choose a media folder from, where the platform has no picker of its own; null where it does. */
    val folderBrowser: FolderBrowser? get() = null

    /** Which build this is: the version, and the commit the shell's build recorded (About, diagnostics). */
    val build: BuildIdentity get() = BuildIdentity()

    /** Which build's third-party notices apply here; null where the shell lists none (a test's). */
    val notices: NoticeTarget? get() = null

    /** What the system's accessibility settings are now, as observable state. */
    val accessibility: AccessibilityPreferences get() = AccessibilityPreferences()

    /**
     * Whether this device should be spared costly visual effects (one the system calls low on memory). Its material
     * is then at most ENHANCED (`design`'s MaterialTiers); nothing else changes.
     */
    val constrainedGraphics: Boolean get() = false

    /** Whether a playback engine is installed. Browsing never depends on it. */
    val canPlay: Boolean

    /** What the user can do about a missing playback engine on this platform, as one sentence; null if nothing. */
    val playbackSetupHint: String? get() = null

    /**
     * Creates a playback engine attached to the shell's video surface and shows that surface, or returns
     * null when no engine is available.
     */
    suspend fun createPlayer(): Player?

    /** Closes a player from [createPlayer] and hides the video surface. */
    suspend fun releasePlayer(player: Player)

    /** Lets the user choose a media folder with the platform's picker and registers it as a read-only source. Not used where there is a [folderBrowser]. */
    suspend fun addMediaFolder(): FolderChoice

    /** Removes a source and what Reflux derived from it. The user's files are never touched. */
    suspend fun removeSource(id: SourceId)

    /** What [requestFullscreen] does here, so the player offers it only where it changes something. */
    val fullscreenMode: FullscreenMode get() = FullscreenMode.WINDOW

    /** Whether the window is fullscreen, as observable state. */
    val fullscreen: Boolean

    fun requestFullscreen(fullscreen: Boolean)

    /**
     * Records a problem for developers and bug reports. What the user is told is decided by the experience
     * layer; this keeps the technical detail that message leaves out.
     */
    fun report(problem: String, cause: Throwable? = null) {}
}
