package dev.reflux.desktop

import androidx.compose.runtime.tooling.ComposeToolingApi
import androidx.compose.ui.awt.ComposeDialog
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import dev.reflux.desktop.engine.DesktopEnvironment
import dev.reflux.desktop.gamepad.GamepadReader
import dev.reflux.playback.mpv.MpvPlayer
import dev.reflux.ui.state.AppState
import java.awt.Component
import java.awt.Container
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Robot
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

/** Whether tests that open real windows were asked for (`-Preflux.windowTests=true`) and can run here. */
internal val windowTestsRequested: Boolean
    get() = System.getProperty("reflux.windowTests") == "true" && !GraphicsEnvironment.isHeadless()

internal val isWindows: Boolean = System.getProperty("os.name").startsWith("Windows")

/**
 * The real desktop shell, running in its real window, for end-to-end tests.
 *
 * Tests find controls the way assistive technology does, through the window's semantics, and act on them
 * with pointer and key events delivered to the window's own event queue. That exercises the toolkit's and
 * Reflux's whole input path without moving the system pointer, so the tests can run on a machine someone
 * is using.
 */
@OptIn(ComposeToolingApi::class)
internal class Shell(environment: DesktopEnvironment, gamepads: GamepadReader? = null) : AutoCloseable {
    private val started = CompletableFuture<RunningReflux>()
    private val thread = thread(name = "reflux-shell") { runReflux(environment, exitProcess = false, gamepads = gamepads) { started.complete(it) } }
    private val running: RunningReflux = started.get(60, TimeUnit.SECONDS)
    private val screenshots: File? = System.getProperty("reflux.screenshots")?.takeIf { it.isNotBlank() }?.let(::File)

    val app: AppState get() = running.app
    val window: ComposeWindow get() = running.window

    /** The player's controls window, while something plays. */
    val overlay: ComposeDialog? get() = window.ownedWindows.filterIsInstance<ComposeDialog>().firstOrNull { it.isShowing }

    val exited: Boolean get() = !thread.isAlive

    init {
        ui {
            window.isAlwaysOnTop = true
            window.toFront()
        }
    }

    fun <T> ui(action: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) return action()
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait { result = runCatching(action) }
        return result!!.getOrThrow()
    }

    fun await(what: String, timeoutMs: Long = 20_000, state: () -> String = { "" }, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (ui(condition)) return
            Thread.sleep(80)
        }
        error("Timed out waiting for $what. ${ui(state)} On screen: ${ui { texts() }.take(40)}")
    }

    // Finding things ---------------------------------------------------------------------------------

    private class Located(val surface: Window, val bounds: Rect)

    private fun surfaces(): List<Pair<Window, Collection<SemanticsOwner>>> =
        listOfNotNull(overlay?.let { it to it.semanticsOwners }, window to window.semanticsOwners)

    private fun SemanticsNode.says(text: String, exact: Boolean): Boolean {
        val labels = config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        return labels.any { if (exact) it == text else it.contains(text) }
    }

    /** Must run on the UI thread. The player's controls are searched first: they are what is in front. */
    private fun locate(text: String, exact: Boolean): Located? {
        for ((surface, owners) in surfaces()) {
            val node = owners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
                .firstOrNull { it.says(text, exact) && !it.boundsInWindow.isEmpty } ?: continue
            return Located(surface, node.boundsInWindow)
        }
        return null
    }

    private fun texts(): List<String> = surfaces().flatMap { (_, owners) ->
        owners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }.flatMap { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        }
    }

    /** Whether a control or text named [text] is on screen, in the browsing window or the player. */
    fun shows(text: String, exact: Boolean = true): Boolean = ui { locate(text, exact) != null }

    fun awaitShown(text: String, exact: Boolean = true) = await("\"$text\" to be shown") { locate(text, exact) != null }

    fun awaitGone(text: String) = await("\"$text\" to go away") { locate(text, exact = true) == null }

    /** Where [text] is, in the coordinates of the surface that shows it. */
    fun boundsOf(text: String, exact: Boolean = true): Rect = ui { locate(text, exact)?.bounds ?: error("\"$text\" is not on screen: ${texts().take(40)}") }

    // Acting on them ---------------------------------------------------------------------------------

    private fun content(surface: Window): Container = (surface as? ComposeWindow)?.contentPane ?: (surface as ComposeDialog).contentPane

    /** Delivers a mouse event at ([x], [y]) in the pixel coordinates of [surface]'s content. Must run on the UI thread. */
    private fun mouse(surface: Window, id: Int, x: Float, y: Float, button: Int = MouseEvent.NOBUTTON, down: Boolean = false, wheel: Int = 0) {
        val scale = surface.graphicsConfiguration.defaultTransform.scaleX
        val pane = content(surface)
        val point = Point((x / scale).toInt(), (y / scale).toInt())
        val target = SwingUtilities.getDeepestComponentAt(pane, point.x, point.y) ?: pane
        val local = SwingUtilities.convertPoint(pane, point, target)
        val modifiers = if (down) InputEvent.BUTTON1_DOWN_MASK else 0
        val now = System.currentTimeMillis()
        val event = if (id == MouseEvent.MOUSE_WHEEL) {
            MouseWheelEvent(target, id, now, modifiers, local.x, local.y, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, wheel)
        } else {
            MouseEvent(target, id, now, modifiers, local.x, local.y, if (button == MouseEvent.NOBUTTON) 0 else 1, false, button)
        }
        target.dispatchEvent(event)
    }

    /** One click, delivered in one piece so that nothing else in the event queue can come between its parts. */
    private fun clickAt(surface: Window, x: Float, y: Float) = ui {
        mouse(surface, MouseEvent.MOUSE_MOVED, x, y)
        mouse(surface, MouseEvent.MOUSE_PRESSED, x, y, MouseEvent.BUTTON1, down = true)
        mouse(surface, MouseEvent.MOUSE_RELEASED, x, y, MouseEvent.BUTTON1)
    }

    /**
     * Clicks [target], a control that only exists while the pointer is over [text] (or [belowDp] under it).
     * The machine's real pointer may move meanwhile and end the hover, so this hovers again until the click lands.
     */
    fun clickRevealedBy(text: String, belowDp: Float, target: String) {
        repeat(25) {
            hover(text, belowDp = belowDp)
            Thread.sleep(350)
            val clicked = ui {
                val located = locate(target, exact = true) ?: return@ui false
                clickAt(located.surface, located.bounds.center.x, located.bounds.center.y)
                true
            }
            if (clicked) return
        }
        error("\"$target\" did not appear while hovering \"$text\"")
    }

    /** Moves the pointer onto [text], or [belowDp] under its middle, without pressing anything. */
    fun hover(text: String, exact: Boolean = true, belowDp: Float = 0f) {
        awaitShown(text, exact)
        ui {
            locate(text, exact)?.let {
                val scale = it.surface.graphicsConfiguration.defaultTransform.scaleX.toFloat()
                mouse(it.surface, MouseEvent.MOUSE_MOVED, it.bounds.center.x, it.bounds.center.y + belowDp * scale)
            }
        }
    }

    /** Moves the pointer over the frontmost surface (the video, or the page), which brings hidden player controls back. */
    fun hoverSurface(fractionX: Float, fractionY: Float) {
        val surface = overlay ?: window
        ui {
            val pane = content(surface)
            val scale = surface.graphicsConfiguration.defaultTransform.scaleX.toFloat()
            mouse(surface, MouseEvent.MOUSE_MOVED, pane.width * scale * fractionX - 12f, pane.height * scale * fractionY)
            mouse(surface, MouseEvent.MOUSE_MOVED, pane.width * scale * fractionX, pane.height * scale * fractionY)
        }
    }

    /**
     * Where [text] is once it has stopped moving. Like a person, the tests act on what has settled, not on
     * a screen that is still sliding in or one that is on its way out.
     */
    private fun settled(text: String, exact: Boolean): Located {
        awaitShown(text, exact)
        val deadline = System.currentTimeMillis() + 10_000
        var previous: Located? = null
        while (System.currentTimeMillis() < deadline) {
            val current = ui { locate(text, exact) }
            if (current != null && previous != null && current.surface === previous.surface && current.bounds == previous.bounds) return current
            previous = current
            Thread.sleep(120)
        }
        error("\"$text\" never came to rest")
    }

    /** Clicks the control or text named [text], once it is on screen and at rest. */
    fun click(text: String, exact: Boolean = true) {
        val located = settled(text, exact)
        clickAt(located.surface, located.bounds.center.x, located.bounds.center.y)
    }

    /** Clicks a point of the frontmost surface given as fractions of its size: the video, or the page. */
    fun clickSurface(fractionX: Float, fractionY: Float, times: Int = 1) {
        val surface = overlay ?: window
        val (width, height) = ui { content(surface).let { it.width to it.height } }
        val scale = surface.graphicsConfiguration.defaultTransform.scaleX.toFloat()
        repeat(times) { clickAt(surface, width * scale * fractionX, height * scale * fractionY) }
    }

    /** Presses on [text] at [from] of its width, drags to [to], and releases. */
    fun drag(text: String, from: Float, to: Float) {
        val located = settled(text, exact = true)
        val y = located.bounds.center.y
        fun x(fraction: Float) = located.bounds.left + located.bounds.width * fraction
        ui { mouse(located.surface, MouseEvent.MOUSE_MOVED, x(from), y) }
        ui { mouse(located.surface, MouseEvent.MOUSE_PRESSED, x(from), y, MouseEvent.BUTTON1, down = true) }
        for (step in 1..6) ui { mouse(located.surface, MouseEvent.MOUSE_DRAGGED, x(from + (to - from) * step / 6f), y, down = true) }
        ui { mouse(located.surface, MouseEvent.MOUSE_RELEASED, x(to), y, MouseEvent.BUTTON1) }
    }

    /** Turns the wheel by [notches] (positive scrolls down) with the pointer over [text]. */
    fun wheel(text: String, notches: Int) {
        awaitShown(text)
        ui { locate(text, exact = true)?.let { mouse(it.surface, MouseEvent.MOUSE_WHEEL, it.bounds.center.x, it.bounds.center.y, wheel = notches) } }
    }

    /** Presses a key the way the toolkit delivers one, whichever of the shell's windows has focus. */
    fun key(code: Int) = ui {
        window.dispatchEvent(KeyEvent(window, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED))
        window.dispatchEvent(KeyEvent(window, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED))
    }

    /** Asks the window to close, as its close button does. */
    fun requestClose() = SwingUtilities.invokeLater { window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING)) }

    fun find(matches: (Component) -> Boolean): Component? = ui { find(window, matches) }

    private fun find(root: Component, matches: (Component) -> Boolean): Component? {
        if (matches(root)) return root
        return (root as? Container)?.components?.firstNotNullOfOrNull { find(it, matches) }
    }

    /**
     * Captures what the window shows on screen, saving it when screenshots were requested.
     *
     * Reading the screen is dependable on Windows. On X11 with a compositing manager it occasionally fails
     * in a way that ends the process, so there it is only done when screenshots were asked for, and null is
     * returned otherwise.
     */
    fun capture(name: String): BufferedImage? {
        if (!isWindows && screenshots == null) return null
        Thread.sleep(600) // let the compositor present the frame
        val visible = ui { window.bounds.intersection(window.graphicsConfiguration.bounds) }
        val image = Robot().createScreenCapture(visible)
        screenshots?.let {
            it.mkdirs()
            ImageIO.write(image, "png", File(it, "$name.png"))
        }
        return image
    }

    fun awaitExit(timeoutMs: Long = 20_000) {
        thread.join(timeoutMs)
        check(exited) { "The shell did not exit" }
    }

    override fun close() {
        if (!exited) runCatching { ui { running.exit() } }
        thread.join(15_000)
    }
}

/** Generated media for end-to-end tests. Needs FFmpeg. */
internal object SampleMedia {
    val available: Boolean by lazy {
        MpvPlayer.isAvailable && runCatching { ProcessBuilder("ffmpeg", "-version").start().waitFor(10, TimeUnit.SECONDS) }.getOrDefault(false)
    }

    /**
     * Moving test video with English and Japanese audio, an English subtitle, and chapters: an "Intro" for
     * the first 8 seconds, then one chapter per remaining third.
     */
    fun video(output: Path, seconds: Int, size: String = "640x360", rate: Int = 15) {
        val directory = Files.createDirectories(output.parent)
        val subtitle = directory.resolve("embedded.srt")
        Files.writeString(subtitle, "1\n00:00:01,000 --> 00:00:30,000\nReflux subtitle rendering\n")
        val chapters = directory.resolve("chapters.txt")
        val second = 8 + (seconds - 8) / 2
        Files.writeString(
            chapters,
            ";FFMETADATA1\n" + listOf("Intro" to (0 to 8), "Chapter 1" to (8 to second), "Chapter 2" to (second to seconds))
                .joinToString("") { (title, span) -> "[CHAPTER]\nTIMEBASE=1/1\nSTART=${span.first}\nEND=${span.second}\ntitle=$title\n" },
        )
        val process = ProcessBuilder(
            "ffmpeg", "-y", "-loglevel", "error",
            "-f", "lavfi", "-i", "testsrc2=duration=$seconds:size=$size:rate=$rate",
            "-f", "lavfi", "-i", "sine=frequency=330:duration=$seconds",
            "-f", "lavfi", "-i", "sine=frequency=550:duration=$seconds",
            "-i", subtitle.toString(),
            "-i", chapters.toString(),
            "-map", "0:v", "-map", "1:a", "-map", "2:a", "-map", "3:s", "-map_chapters", "4",
            "-c:v", "libx264", "-preset", "ultrafast", "-g", rate.toString(), "-pix_fmt", "yuv420p", "-c:a", "aac", "-c:s", "srt",
            "-metadata:s:a:0", "language=eng", "-metadata:s:a:1", "language=jpn", "-metadata:s:s:0", "language=eng",
            output.toString(),
        ).redirectErrorStream(true).start()
        val log = process.inputStream.bufferedReader().readText()
        check(process.waitFor(180, TimeUnit.SECONDS) && process.exitValue() == 0) { "ffmpeg failed: $log" }
        Files.delete(subtitle)
        Files.delete(chapters)
    }
}
