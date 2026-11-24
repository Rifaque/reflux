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

    /** The controllers' motors, where the platform's reader can drive them (Windows XInput; F-002). Set once controllers are read. */
    var rumble: dev.reflux.ui.state.ControllerRumble? = null

    override val controllerRumble: dev.reflux.ui.state.ControllerRumble? get() = rumble

    /** Present on X11 only; created before any engine so that it sees the toolkit's own error handler. */
    private val x11Errors = X11ErrorGuard.create().also { guard ->
        if (guard == null && X11ErrorGuard.needed) report("Xlib could not be loaded: an X11 error after playback may end Reflux")
    }

    /** The main window, once it exists; dialogs are opened relative to it. */
    var window: Window? = null

    /** Windows and Linux offer the Reflux keyboard's desk picture to a pointer, in the system's own layout (F-P4, D3). */
    override val deskKeyboard: Boolean get() = true

    override fun keyboardLayout(): dev.reflux.ui.state.SystemKeymap? = SystemKeyboardLayout.read()

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

    /**
     * The video surface is a native child of the main window, and a child window can be anywhere in its parent: in
     * the mini-player's dock as well as across all of it (DECISIONS.md, 2026-11-12; verified on Windows and X11).
     */
    override val docksVideo: Boolean get() = true

    /** A desk may have only a mouse: Search offers the Reflux keyboard with a button (B-051). */
    override val pointerKeyboard: Boolean get() = true

    override val build: BuildIdentity = readBuildIdentity()

    override val notices: NoticeTarget = NoticeTarget.DESKTOP

    override val publisherMark: androidx.compose.ui.graphics.ImageBitmap? by lazy {
        runCatching {
            DesktopPlatform::class.java.getResourceAsStream("/rsx-develops-wordmark.png")!!.use { org.jetbrains.skia.Image.makeFromEncoded(it.readBytes()) }
                .toComposeImageBitmap()
        }.getOrNull()
    }

    override val connections: SourceConnections get() = environment.connections
    override val pinKey: dev.reflux.library.PinKey get() = environment.pinKey

    override val offline: Offline get() = environment.offline

    override val playbackSetupHint: String =
        if (System.getProperty("os.name").startsWith("Windows")) {
            "Playback needs libmpv (libmpv-2.dll), which this build does not include yet."
        } else {
            LinuxPlaybackSetup.hint()
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

    /**
     * The Linux picture drawn by Compose through libmpv's render API (B-084), made once the window is known; null elsewhere.
     * [glPlaying] says whether the engine now playing draws that way (else the native video window does, as the fallback).
     */
    val glVideo: LinuxGlVideo?
        get() = madeGlVideo ?: if (LINUX) window?.let { LinuxGlVideo(it, environment.diagnostics::record) }?.also { madeGlVideo = it } else null

    private var madeGlVideo: LinuxGlVideo? = null

    var glPlaying: Boolean by mutableStateOf(false)
        private set

    override suspend fun createPlayer(): Player? {
        // Linux: the engine renders into the window's own scene where Skiko draws it with OpenGL; otherwise its own window.
        val gl = glVideo
        if (LINUX && gl == null) environment.diagnostics.record("Video renderer: ${LinuxGlVideo.FALLBACK} (the window was not known yet)")
        if (LINUX && gl != null && !gl.available) environment.diagnostics.record("Video renderer: ${LinuxGlVideo.FALLBACK} (${gl.unavailableReason()})")
        if (LINUX && gl?.available == true && System.getenv("REFLUX_NATIVE_VIDEO_WINDOW") == null) {
            val info = window?.let(SkikoGl::renderInfo)
            val hwdec = System.getenv("REFLUX_RENDER_HWDEC") ?: renderedHwdec(info)
            val player = withContext(Dispatchers.IO) { environment.createPlayer(RENDERED_OPTIONS + ("hwdec" to hwdec)) }
            val engine = player as? dev.reflux.playback.mpv.MpvPlayer
            // The picture's canvas is composed while glPlaying, and the render context is made in its first draw, before the
            // engine is given anything to play (LinuxGlVideo.attach).
            if (engine != null) glPlaying = true
            if (engine != null && gl.attach(engine)) {
                environment.diagnostics.record("Video renderer: ${LinuxGlVideo.RENDERER} (libmpv render API into Skiko ${SkikoGl.version}'s OpenGL context, vo=libmpv, hwdec=$hwdec)")
                return engine
            }
            glPlaying = false
            withContext(Dispatchers.IO) { player?.close() }
            report("Video renderer: ${LinuxGlVideo.FALLBACK} (the integrated picture could not be set up" + (dev.reflux.playback.mpv.MpvPlayer.lastFailure?.let { ": $it" } ?: "") + ")")
        }
        val surface = video.show() ?: run {
            report("The video surface was not created in time")
            return null
        }
        x11Errors?.hold()
        // On X11 the engine keeps its video output until it is destroyed, instead of dropping it whenever
        // playback stops: every teardown is a moment in which an X error would end the process.
        val options = embeddedOptions(surface, linux = LINUX, guarded = x11Errors != null)
        val player = try {
            withContext(Dispatchers.IO) { environment.createPlayer(options) }
        } catch (e: Throwable) {
            x11Errors?.release()
            video.hide()
            throw e
        }
        if (player == null) {
            report("libmpv was found but could not be initialized" + (dev.reflux.playback.mpv.MpvPlayer.lastFailure?.let { ": $it" } ?: ""))
            x11Errors?.release()
            video.hide()
        }
        return player
    }

    override suspend fun releasePlayer(player: Player) {
        if (glPlaying) {
            // The render context goes first, with OpenGL current, as libmpv requires; then the engine.
            try {
                glVideo?.release()
            } finally {
                // The engine is closed whatever happened to the release.
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                    player.close()
                    // The engine decoded into frame pools in system memory (copy-back decoding), on threads of its own; glibc
                    // keeps what they freed in its per-thread arenas. Handed back now, so playback after playback does not
                    // add up (measured 2026-11-24, CACHY-011).
                    GlibcTrim.trim()
                }
                glPlaying = false
            }
            return
        }
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

    internal companion object {
        private val LINUX = System.getProperty("os.name").startsWith("Linux")

        /** Reflux draws its own controls and handles all input, so mpv's are switched off. */
        private val EMBEDDED_OPTIONS = mapOf(
            "input-cursor" to "no",
            "cursor-autohide" to "no",
            "osd-level" to "0",
        )

        /**
         * What the engine is given to draw into [surface], Reflux's own native window.
         *
         * On Linux that window is always an X11 one: the toolkit is X11, and on a Wayland session it runs through XWayland. Left
         * to choose, mpv's GPU output tries Wayland first when the session has one. A Wayland surface cannot be put inside an X11
         * window, so mpv made a surface of its own above Reflux's windows: it covered the player's controls window (only the
         * parts above the picture showed, and the pointer never reached the controls) and sat out of place (B-058, the Ubuntu
         * 26.04 GNOME walk, 2026-11-20). So the GPU output is held to X11 contexts, EGL first.
         *
         * @param guarded the X11 error guard is in place: the engine then keeps its video output until it is destroyed.
         */
        fun embeddedOptions(surface: Long, linux: Boolean, guarded: Boolean): Map<String, String> = buildMap {
            putAll(EMBEDDED_OPTIONS)
            put("wid", surface.toString())
            if (linux) put("gpu-context", X11_CONTEXTS)
            if (guarded) put("force-window", "yes")
        }

        /**
         * The engine for the Linux picture drawn in Reflux's scene (LinuxGlVideo): no window of its own, its frames rendered
         * through libmpv's render API, hardware decoding where the machine has it.
         */
        val RENDERED_OPTIONS: Map<String, String> = EMBEDDED_OPTIONS + mapOf("vo" to "libmpv")

        /**
         * The decoding asked for with the integrated picture, by the OpenGL Skiko draws with ([info], its render info). Skiko
         * draws with GLX, and mpv's zero-copy hand-over of VA-API frames needs EGL, so on Mesa (Intel, AMD) only copying
         * decoders work there; mpv's own order would take nvdec-copy first, waking a second (NVIDIA) GPU to decode frames that
         * are then copied across to the one that draws. Decoding on the GPU that draws (vaapi-copy) costs least and leaves the
         * other asleep. On NVIDIA's OpenGL, CUDA's interop works with GLX: auto-safe finds nvdec without a copy.
         * Measured 2026-11-24 (CACHY-011, docs/planning/CACHYOS-FINAL-AUDIT-LEDGER.md).
         */
        internal fun renderedHwdec(info: String?): String =
            if (info?.contains("NVIDIA", ignoreCase = true) == true) "auto-safe" else "vaapi-copy,auto-copy-safe"

        /** mpv's X11 GPU contexts, in order: EGL, then GLX, then Vulkan. */
        const val X11_CONTEXTS = "x11egl,x11,x11vk"
    }
}

/** What this build recorded about its commit (see `generateBuildIdentity`); the version alone where it recorded nothing. */
internal fun readBuildIdentity(): BuildIdentity =
    BuildIdentity.parse(DesktopPlatform::class.java.getResource("/dev/reflux/desktop/build-identity.properties")?.readText())
