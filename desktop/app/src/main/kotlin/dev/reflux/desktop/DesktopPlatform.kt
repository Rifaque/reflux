package dev.reflux.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.reflux.core.model.SourceId
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.Player
import dev.reflux.desktop.engine.DesktopEnvironment
import dev.reflux.ui.state.ArtworkLoader
import dev.reflux.ui.state.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Window
import java.util.Locale

/** The desktop shell's side of the experience layer: Skia artwork, libmpv in a native surface, AWT windows. */
internal class DesktopPlatform(private val environment: DesktopEnvironment) : Platform {
    val video = VideoSurface()

    /** The main window, once it exists; dialogs are opened relative to it. */
    var window: Window? = null

    override val artwork: ArtworkLoader = DesktopArtworkLoader(environment.artwork, environment.registry::resolve)

    override val device: DeviceCapabilities by lazy { environment.deviceCapabilities() }

    override val preferredLanguages: List<String> =
        listOf(Locale.getDefault().language).filter { it.isNotBlank() }.ifEmpty { listOf("en") }

    override val canPlay: Boolean get() = environment.canPlay

    override var fullscreen: Boolean by mutableStateOf(false)
        private set

    override fun requestFullscreen(fullscreen: Boolean) {
        this.fullscreen = fullscreen
    }

    override suspend fun createPlayer(): Player? {
        val surface = video.show() ?: return null
        val player = withContext(Dispatchers.IO) { environment.createPlayer(EMBEDDED_OPTIONS + ("wid" to surface.toString())) }
        if (player == null) video.hide()
        return player
    }

    override suspend fun releasePlayer(player: Player) {
        // The engine leaves the surface before the surface goes away.
        withContext(Dispatchers.IO) { player.close() }
        video.hide()
    }

    override suspend fun addMediaFolder(): SourceId? {
        val folder = FolderPicker.choose(window) ?: return null
        return withContext(Dispatchers.IO) { environment.addLocalFolder(folder).descriptor.id }
    }

    override suspend fun removeSource(id: SourceId) = withContext(Dispatchers.IO) { environment.removeSource(id) }

    private companion object {
        /** Reflux draws its own controls and handles all input, so mpv's are switched off. */
        val EMBEDDED_OPTIONS = mapOf(
            "input-cursor" to "no",
            "cursor-autohide" to "no",
            "osd-level" to "0",
        )
    }
}
