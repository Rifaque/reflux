package dev.reflux.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import dev.reflux.ui.player.PlayerOverlay
import dev.reflux.ui.state.AppState
import dev.reflux.ui.theme.RefluxColors
import kotlinx.coroutines.CoroutineExceptionHandler
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
import java.awt.image.BufferedImage
import javax.swing.SwingUtilities

fun main() = runReflux(DesktopEnvironment())

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
    application(exitProcessOnExit = exitProcess) {
        // The last line of defence: a failure nobody handled is written down instead of ending the app.
        val scope = rememberCoroutineScope { CoroutineExceptionHandler { _, error -> platform.report("An unexpected error was not handled", error) } }
        val app = remember {
            AppState(environment.library, environment.pipeline, environment.registry::resolve, platform, scope).also { it.start() }
        }
        val windowState = rememberWindowState(size = DpSize(1360.dp, 860.dp), position = WindowPosition.Aligned(Alignment.Center))

        Window(
            onCloseRequest = {
                // What was playing is saved before the process goes away.
                scope.launch {
                    app.playback?.close()?.join()
                    exitApplication()
                }
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
                val keys = KeyEventDispatcher { event ->
                    event.id == KeyEvent.KEY_PRESSED && event.toRefluxKey()?.let(app::onKey) == true
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
                onStarted(RunningReflux(app, window, ::exitApplication))
                onDispose {
                    controllers?.close()
                    Toolkit.getDefaultToolkit().removeAWTEventListener(mouse)
                    focus.removeKeyEventDispatcher(keys)
                    platform.window = null
                }
            }

            Box(Modifier.fillMaxSize().background(RefluxColors.background)) {
                RefluxApp(app)
                platform.video.canvas?.let { canvas ->
                    key(canvas) { SwingPanel(factory = { canvas }, modifier = Modifier.fillMaxSize()) }
                }
            }

            val session = app.playback
            val canvas = platform.video.canvas
            if (session != null && canvas != null) {
                VideoOverlay(window, canvas) {
                    // An almost invisible fill keeps the whole overlay window hit-testable; fully transparent
                    // pixels would let pointer input fall through to the video surface.
                    Box(Modifier.fillMaxSize().background(Color(0x01000000))) {
                        PlayerOverlay(app, session, hiddenCursor = HiddenCursor)
                    }
                }
            }
        }
    }
}

private val HiddenCursor = PointerIcon(
    Toolkit.getDefaultToolkit().createCustomCursor(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), Point(0, 0), "hidden"),
)

/** The window and taskbar icon: a play mark on the accent color. */
private object AppIcon : Painter() {
    override val intrinsicSize = Size(256f, 256f)

    override fun DrawScope.onDraw() {
        val unit = size.minDimension
        drawRoundRect(
            Brush.linearGradient(listOf(RefluxColors.accent, Color(0xFF2B4C8C)), Offset.Zero, Offset(unit, unit)),
            cornerRadius = CornerRadius(unit * 0.24f),
        )
        drawPath(
            Path().apply {
                moveTo(unit * 0.38f, unit * 0.28f)
                lineTo(unit * 0.74f, unit * 0.5f)
                lineTo(unit * 0.38f, unit * 0.72f)
                close()
            },
            RefluxColors.onAccent,
        )
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

private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20

@Suppress("FunctionName")
private interface Dwm : Library {
    fun DwmSetWindowAttribute(window: Pointer, attribute: Int, value: IntByReference, size: Int): Int
}
