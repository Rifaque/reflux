package dev.reflux.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toComposeImageBitmap
import dev.reflux.core.BuildIdentity
import dev.reflux.core.model.SourceId
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.Player
import dev.reflux.desktop.engine.DesktopEnvironment
import dev.reflux.desktop.engine.FolderAddition
import dev.reflux.library.Offline
import dev.reflux.library.SourceConnections
import dev.reflux.ui.legal.NoticeTarget
import dev.reflux.ui.state.AccessibilityPreferences
import dev.reflux.ui.state.ArtworkLoader
import dev.reflux.ui.state.FolderChoice
import dev.reflux.ui.state.Platform
import dev.reflux.ui.state.SupportReportOutlet
import dev.reflux.core.diagnostics.SupportReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Window
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

/** The desktop shell's side of the experience layer: Skia artwork, libmpv in a native surface, AWT windows. */
internal class DesktopPlatform(private val environment: DesktopEnvironment) : Platform {
    val video = VideoSurface()

    /** Present on X11 only; created before any engine so that it sees the toolkit's own error handler. */
    private val x11Errors = X11ErrorGuard.create().also { guard ->
        if (guard == null && X11ErrorGuard.needed) report("Xlib could not be loaded: an X11 error after playback may end Reflux")
    }

    /** The main window, once it exists; dialogs are opened relative to it. */
    var window: Window? = null

    override val artwork: ArtworkLoader = DesktopArtworkLoader(environment.artwork, environment.registry::resolve)

    /** As observed now: the display the window is on, and what the engine was seen to decode ([DisplayWatcher]). */
    override val device: DeviceCapabilities get() = environment.deviceCapabilities()

    override val preferredLanguages: List<String> =
        listOf(Locale.getDefault().language).filter { it.isNotBlank() }.ifEmpty { listOf("en") }

    /** Read once at start and whenever the window comes back to the front: a setting changed meanwhile applies. */
    override var accessibility: AccessibilityPreferences by mutableStateOf(AccessibilityPreferences())
        private set

    private val readingSettings = AtomicBoolean(false)

    /** Reads the system's settings on a thread of their own; on Linux that runs a process. */
    fun refreshAccessibility() {
        if (!readingSettings.compareAndSet(false, true)) return
        thread(name = "reflux-accessibility-settings", isDaemon = true) {
            val read = runCatching { DesktopAccessibility.read() }.getOrDefault(accessibility)
            readingSettings.set(false)
            SwingUtilities.invokeLater { accessibility = read }
        }
    }

    override val canPlay: Boolean get() = environment.canPlay

    override val build: BuildIdentity = readBuildIdentity()

    override val notices: NoticeTarget = NoticeTarget.DESKTOP

    override val publisherMark: androidx.compose.ui.graphics.ImageBitmap? by lazy {
        runCatching {
            DesktopPlatform::class.java.getResourceAsStream("/rsx-develops-wordmark.png")!!.use { org.jetbrains.skia.Image.makeFromEncoded(it.readBytes()) }
                .toComposeImageBitmap()
        }.getOrNull()
    }

    override val connections: SourceConnections get() = environment.connections

    override val offline: Offline get() = environment.offline

    override val playbackSetupHint: String =
        if (System.getProperty("os.name").startsWith("Windows")) {
            "Playback needs libmpv (libmpv-2.dll), which this build does not include yet."
        } else {
            "Install libmpv with your package manager (libmpv2 on Debian and Ubuntu, mpv-libs on Fedora), then restart Reflux."
        }

    override fun report(problem: String, cause: Throwable?) = environment.diagnostics.record(problem, cause)

    override val platformFacts: List<SupportReport.Fact> get() = listOf(
        SupportReport.Fact("Platform", "${System.getProperty("os.name")} ${System.getProperty("os.version")}"),
        SupportReport.Fact("Architecture", System.getProperty("os.arch")),
        SupportReport.Fact("Form factor", "desktop"),
        SupportReport.Fact("Java runtime", "${System.getProperty("java.vendor")} ${System.getProperty("java.runtime.version")}"),
        SupportReport.Fact("Playback engine", if (environment.canPlay) "libmpv" else "none: libmpv was not found"),
    )

    /** The end of the diagnostics log (and of the one it rotated into, when the current one is short). */
    override suspend fun recentProblems(): List<String> = withContext(Dispatchers.IO) {
        val file = environment.diagnostics.file
        listOf(file.resolveSibling("${file.fileName}.1"), file).flatMap { runCatching { java.nio.file.Files.readAllLines(it) }.getOrDefault(emptyList()) }
    }

    /** Saved where downloads go, which every desktop user knows how to find. */
    override val supportReports = SupportReportOutlet { fileName, text ->
        withContext(Dispatchers.IO) {
            val home = java.nio.file.Path.of(System.getProperty("user.home"))
            val folder = home.resolve("Downloads").takeIf { java.nio.file.Files.isDirectory(it) } ?: home
            java.nio.file.Files.writeString(folder.resolve(fileName), text)
            if (folder == home) "Saved in your home folder as $fileName." else "Saved in your Downloads folder as $fileName."
        }
    }

    override fun openLink(url: String): Boolean = runCatching {
        val desktop = java.awt.Desktop.getDesktop().takeIf { java.awt.Desktop.isDesktopSupported() && it.isSupported(java.awt.Desktop.Action.BROWSE) } ?: return false
        desktop.browse(java.net.URI(url))
        true
    }.getOrDefault(false)

    override var fullscreen: Boolean by mutableStateOf(false)
        private set

    override fun requestFullscreen(fullscreen: Boolean) {
        this.fullscreen = fullscreen
    }

    override suspend fun createPlayer(): Player? {
        val surface = video.show() ?: run {
            report("The video surface was not created in time")
            return null
        }
        x11Errors?.hold()
        // On X11 the engine keeps its video output until it is destroyed, instead of dropping it whenever
        // playback stops: every teardown is a moment in which an X error would end the process.
        val options = EMBEDDED_OPTIONS + ("wid" to surface.toString()) + if (x11Errors != null) mapOf("force-window" to "yes") else emptyMap()
        val player = try {
            withContext(Dispatchers.IO) { environment.createPlayer(options) }
        } catch (e: Throwable) {
            x11Errors?.release()
            video.hide()
            throw e
        }
        if (player == null) {
            report("libmpv was found but could not be initialized")
            x11Errors?.release()
            video.hide()
        }
        return player
    }

    override suspend fun releasePlayer(player: Player) {
        // The engine leaves the surface before the surface goes away.
        try {
            withContext(Dispatchers.IO) { x11Errors?.closely { player.close() } ?: player.close() }
        } finally {
            x11Errors?.release()
            video.hide()
        }
    }

    override suspend fun addMediaFolder(): FolderChoice {
        val folder = FolderPicker.choose(window) ?: return FolderChoice.Cancelled
        val name = folder.fileName?.toString() ?: folder.toString()
        return when (val added = withContext(Dispatchers.IO) { environment.addLocalFolder(folder) }) {
            is FolderAddition.Added -> FolderChoice.Added(added.source.descriptor.id)
            is FolderAddition.AlreadyCovered -> FolderChoice.AlreadyInLibrary(added.by.id, added.by.displayName)
            is FolderAddition.Unreadable -> FolderChoice.Unreadable(name)
            is FolderAddition.NotAFolder -> FolderChoice.NotAFolder(name)
        }
    }

    override suspend fun removeSource(id: SourceId) {
        // The server's sign-out goes on by itself; the removal is done here.
        withContext(Dispatchers.IO) { environment.removeSource(id) }
    }

    private companion object {
        /** Reflux draws its own controls and handles all input, so mpv's are switched off. */
        val EMBEDDED_OPTIONS = mapOf(
            "input-cursor" to "no",
            "cursor-autohide" to "no",
            "osd-level" to "0",
        )
    }
}

/** What this build recorded about its commit (see `generateBuildIdentity`); the version alone where it recorded nothing. */
internal fun readBuildIdentity(): BuildIdentity =
    BuildIdentity.parse(DesktopPlatform::class.java.getResource("/dev/reflux/desktop/build-identity.properties")?.readText())
