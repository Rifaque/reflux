package dev.reflux.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import dev.reflux.core.input.SemanticAction
import dev.reflux.design.Palette
import dev.reflux.desktop.engine.DesktopEnvironment
import dev.reflux.desktop.gamepad.GamepadInput
import dev.reflux.desktop.gamepad.GamepadReader
import dev.reflux.ui.RefluxApp
import dev.reflux.ui.player.MiniPlayerSurface
import dev.reflux.ui.player.PlayerOverlay
import dev.reflux.ui.player.VideoStage
import dev.reflux.ui.player.rememberVideoPlacement
import dev.reflux.ui.state.PlayerPresentation
import dev.reflux.ui.state.VideoFrame
import dev.reflux.ui.state.AppState
import dev.reflux.ui.theme.RefluxColors
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.AWTEvent
import java.awt.Dimension
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import javax.swing.SwingUtilities

fun main() {
    val environment = try {
        DesktopEnvironment()
    } catch (e: dev.reflux.library.NewerLibraryException) {
        // A library from a newer Reflux is left exactly as it is; the person is told why this one will not open it.
        refuseNewerLibrary(e)
        return
    }
    runReflux(environment)
}

/** Says, in a window of the system's own, that this library needs a newer Reflux, and where it is; then Reflux ends. */
internal fun refuseNewerLibrary(e: dev.reflux.library.NewerLibraryException) {
    val message = newerLibraryMessage(e)
    System.err.println(message)
    if (!java.awt.GraphicsEnvironment.isHeadless()) {
        runCatching { javax.swing.JOptionPane.showMessageDialog(null, message, "Reflux", javax.swing.JOptionPane.WARNING_MESSAGE) }
    }
}

/** What the person is told: what happened, that nothing was changed, and what to do. */
internal fun newerLibraryMessage(e: dev.reflux.library.NewerLibraryException): String =
    "This library was created by a newer version of Reflux, and this version can't safely open it.\n\n" +
        "Nothing in it was changed. Install the latest Reflux to use it again.\n\n" +
        "Library: ${e.file}\n(library format ${e.found}; this version of Reflux understands up to ${e.supported})"

/** A running desktop shell, for end-to-end tests that drive the real window. */
internal class RunningReflux(val app: AppState, val window: ComposeWindow, val exit: () -> Unit)

/**
 * Runs the desktop shell on [environment] until its window is closed.
 *
 * @param exitProcess false keeps the JVM alive afterwards (tests).
 * @param gamepads where controllers are read from; null on platforms without a reader.
 * @param onStarted called on the UI thread once the window exists.
 */
internal fun runReflux(
    environment: DesktopEnvironment,
    exitProcess: Boolean = true,
    gamepads: GamepadReader? = GamepadInput.platformReader(),
    onStarted: (RunningReflux) -> Unit = {},
) {
    val platform = DesktopPlatform(environment)
    // Which build wrote what follows (docs/VERSIONING.md).
    environment.diagnostics.record("Reflux ${platform.build.label} started")
    application(exitProcessOnExit = exitProcess) {
        // The last line of defence: a failure nobody handled is written down instead of ending the app.
        val scope = rememberCoroutineScope { CoroutineExceptionHandler { _, error -> platform.report("An unexpected error was not handled", error) } }
        val app = remember {
            AppState(environment.library, environment.pipeline, environment.registry::resolve, platform, scope).also { state ->
                state.start()
                // Copies are made for as long as Reflux runs, whatever screen is open.
                environment.startOffline(scope)
                // What is observed of the display and decoding changes what Best Version says; pages read it again.
                environment.capabilities.onChange = { SwingUtilities.invokeLater { state.invalidate() } }
                scope.launch(Dispatchers.IO) {
                    // Once the library's first refresh has had the machine to itself.
                    delay(DECODE_PROBE_DELAY_MS)
                    environment.probeDecoding()
                }
            }
        }
        val windowState = rememberWindowState(size = DpSize(1360.dp, 860.dp), position = WindowPosition.Aligned(Alignment.Center))

        // What was playing is saved, and its engine gone, before the process goes away.
        val leave: () -> Unit = {
            scope.launch {
                app.finishPlayback()
                exitApplication()
            }
        }

        Window(
            onCloseRequest = {
                leave()
            },
            state = windowState,
            title = "Reflux",
            icon = AppIcon,
        ) {
            // Fullscreen is experience-layer state; the shell only carries it out.
            val fullscreen = remember(window) { Fullscreen(window, windowState) }
            LaunchedEffect(platform.fullscreen) { fullscreen.set(platform.fullscreen) }
            DisposableEffect(window) {
                platform.window = window
                window.minimumSize = Dimension(900, 600)
                window.background = java.awt.Color(Palette.background.value.toInt(), true)
                darkTitleBar(window)
                // One input path for the whole app: keys reach Reflux whichever component has focus,
                // including the native video surface.
                // A held key is told from a key pressed again, and a let-go key is known (the held seek is one seek).
                val repeats = KeyRepeats(
                    schedule = { delay, action ->
                        val timer = javax.swing.Timer(delay.toInt()) { action() }.apply { isRepeats = false; start() }
                        timer::stop
                    },
                    onReleased = app::onKeyReleased,
                )
                val keys = KeyEventDispatcher { event ->
                    when (event.id) {
                        KeyEvent.KEY_PRESSED -> event.toRefluxKey()?.let { key -> app.onKey(key, repeat = repeats.pressed(key)) } == true
                        KeyEvent.KEY_RELEASED -> {
                            event.toRefluxKey()?.let(repeats::released)
                            false
                        }
                        else -> false
                    }
                }
                val focus = KeyboardFocusManager.getCurrentKeyboardFocusManager()
                focus.addKeyEventDispatcher(keys)
                // The back button of a mouse is Back, like Escape.
                val mouse = AWTEventListener { event ->
                    if (event is MouseEvent && event.isBackButtonPress()) app.dispatch(SemanticAction.BACK)
                }
                Toolkit.getDefaultToolkit().addAWTEventListener(mouse, AWTEvent.MOUSE_EVENT_MASK)
                // Controllers are read system-wide, so they only act while a Reflux window is the active one.
                val controllers = gamepads?.let { reader ->
                    GamepadInput(reader, active = { focus.activeWindow != null }) { button, family ->
                        SwingUtilities.invokeLater { app.onGamepadButton(button, family) }
                    }
                }
                val displays = DisplayWatcher(window, environment.capabilities)
                // A setting changed in the system's own window is seen when Reflux comes back to the front.
                platform.refreshAccessibility()
                val settings = object : WindowAdapter() {
                    override fun windowGainedFocus(event: WindowEvent) = platform.refreshAccessibility()
                }
                window.addWindowFocusListener(settings)
                onStarted(RunningReflux(app, window, leave))
                onDispose {
                    window.removeWindowFocusListener(settings)
                    displays.close()
                    controllers?.close()
                    Toolkit.getDefaultToolkit().removeAWTEventListener(mouse)
                    focus.removeKeyEventDispatcher(keys)
                    platform.window = null
                }
            }

            // Where the one video surface is: the whole window under the full player, or in the mini-player's dock
            // beside the page (docs/PLAYBACK.md, "One session, several presentations").
            // The surface is a native window, which is put where it belongs in one move rather than made to travel.
            val placement = rememberVideoPlacement(app, travels = false)
            Box(Modifier.fillMaxSize().background(RefluxColors.background)) {
                RefluxApp(app)
                platform.video.canvas?.let { canvas ->
                    VideoStage(placement, Modifier.fillMaxSize()) {
                        key(canvas) { SwingPanel(
                            factory = { canvas },
                            modifier = Modifier.fillMaxSize(),
                            // The toolkit's holder paints a light panel colour in the corners the picture's region cuts away
                            // (SurfaceCorners); there it paints what the dock's glass is instead.
                            update = { (it.parent as? javax.swing.JComponent)?.apply { isOpaque = true; background = DockGlass } },
                        ) }
                    }
                }
            }

            // The controls window lives exactly as long as the video surface, not as long as the session:
            // it must not be torn down while the engine is being torn down (see X11ErrorGuard), and the
            // surface only goes away once the engine has.
            platform.video.canvas?.let { canvas ->
                // The picture's corners follow the dock's: a region on the surface's window, and a rim over it (SurfaceCorners).
                val corners = remember(canvas) { SurfaceCorners(canvas, window) }
                DisposableEffect(corners) { onDispose { corners.close() } }
                // Read here, in the composition, so that a change of the radius recomposes this; an effect does not observe what it reads.
                val radius = placement.pictureRadius.value
                SideEffect { corners.radiusDp = radius }
                VideoOverlay(window, canvas, shown = app.videoFrame != VideoFrame.Hidden) {
                    val session = app.playback ?: return@VideoOverlay
                    // An almost invisible fill keeps the whole overlay window hit-testable; fully transparent
                    // pixels would let pointer input fall through to the video surface.
                    Box(Modifier.fillMaxSize().background(Color(0x01000000))) {
                        // The full player's controls belong over a picture that is the whole window and at rest. In
                        // the dock, and on the way there and back, the picture is only the way back to the player.
                        if (session.presentation == PlayerPresentation.FULL && placement.full) {
                            PlayerOverlay(app, session, hiddenCursor = HiddenCursor)
                        } else {
                            MiniPlayerSurface(app, session, Modifier.pictureRim(placement.pictureRadius))
                        }
                    }
                }
            }
        }
    }
}

/** What the dock's glass is, flat: the page's colour with the surface's tint over it, as FLOATING material lays it (design/Material.kt). */
private val DockGlass: java.awt.Color = run {
    fun mix(shift: Int): Int {
        val page = Palette.background.value.toInt() shr shift and 0xFF
        val tint = Palette.surface.value.toInt() shr shift and 0xFF
        return (page * 44 + tint * 56) / 100
    }
    java.awt.Color(mix(16), mix(8), mix(0))
}

private val HiddenCursor = PointerIcon(
    Toolkit.getDefaultToolkit().createCustomCursor(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), Point(0, 0), "hidden"),
)

/**
 * The window and taskbar icon: the Reflux mark (design/brand) on a graphite tile lit from the top left, like the glass
 * materials. Neutral, like the interface; the same as the packaged icons and the Android launcher icon.
 */
private object AppIcon : Painter() {
    override val intrinsicSize = Size(256f, 256f)

    /** The mark's outline in a 100-unit box, as tools/brand/make-brand-assets.py writes it. */
    private val mark: Path = PathParser().parsePathString(
        AppIcon::class.java.getResourceAsStream("/reflux-mark.path")!!.bufferedReader().use { it.readText() }.trim(),
    ).toPath().apply { fillType = PathFillType.EvenOdd }

    override fun DrawScope.onDraw() {
        val unit = size.minDimension
        drawRoundRect(
            Brush.linearGradient(listOf(Color(0xFF2C2C33), Color(0xFF0D0D10)), Offset.Zero, Offset(unit * 0.35f, unit)),
            cornerRadius = CornerRadius(unit * 0.22f),
        )
        translate(unit * 0.17f, unit * 0.17f) {
            scale(unit * 0.66f / 100f, pivot = Offset.Zero) { drawPath(mark, RefluxColors.contentPrimary) }
        }
    }
}

/** Asks Windows for a dark title bar, so the window frame matches the dark interface. */
private fun darkTitleBar(window: java.awt.Window) {
    if (!System.getProperty("os.name").startsWith("Windows")) return
    runCatching {
        val handle = Native.getWindowPointer(window)
        val enabled = IntByReference(1)
        Native.load("dwmapi", Dwm::class.java).DwmSetWindowAttribute(handle, DWMWA_USE_IMMERSIVE_DARK_MODE, enabled, 4)
    }
}

/** How long after start the engine is asked about hardware decoding (about two seconds of work, once). */
private const val DECODE_PROBE_DELAY_MS = 3_000L

private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20

@Suppress("FunctionName")
private interface Dwm : Library {
    fun DwmSetWindowAttribute(window: Pointer, attribute: Int, value: IntByReference, size: Int): Int
}
