package dev.reflux.desktop

import dev.reflux.core.playback.PlayerStatus
import dev.reflux.desktop.engine.DesktopEnvironment
import dev.reflux.ui.state.PlayerPresentation
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The desktop player's controls follow the window while it is dragged: the same running window, the same playback, and the
 * controls' size recomputed from the room the window has now (design/PlayerChrome.kt). The question is not whether the
 * player can be drawn at different sizes (`PlayerChromeLayoutTest` does that) but whether the window that is already open
 * reacts when its size changes, with a real engine rendering into it.
 *
 * Opens a window, so it runs only on request: `./gradlew :desktop:app:test -Preflux.windowTests=true`.
 */
class PlayerChromeResizeTest {
    private val root: Path = Files.createTempDirectory("reflux-chrome")
    private val media: Path = root.resolve("Media").createDirectories()
    private val film: Path = media.resolve("Night Drive (2021)/Night.Drive.2021.720p.mkv")

    private lateinit var shell: Shell

    private fun note(line: String) = println("TRIAL $line")

    /** The pause button's height on screen in pixels: it is the one control whose size is a tier's own. */
    private fun pauseHeight(): Float = shell.boundsOf("Pause").height

    private fun resize(width: Int, height: Int) {
        shell.ui { shell.window.setSize(width, height) }
        shell.await("the window to be $width x $height") { shell.window.width == width && shell.window.height == height }
    }

    /**
     * Takes the bottom-right corner of the window with the real mouse (the operating system's own resize loop) and drags it
     * across the tiers and back. The button stays down while the controls are read, and what is expected is what the window's
     * size is at that moment, not what the pointer was told to do: the question is whether the controls follow the window.
     */
    private fun realMouseDrag(session: dev.reflux.ui.state.PlaybackSession) {
        val screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.maxByOrNull { it.defaultConfiguration.bounds.width }!!.defaultConfiguration.bounds
        resize(1000 + 14, 640 + 37)
        shell.ui { shell.window.setLocation(screen.x + 20, screen.y + 20) }
        shell.hoverSurface(0.5f, 0.5f)
        shell.awaitShown("Pause")
        val robot = java.awt.Robot()
        val origin = shell.ui { shell.window.location }
        robot.mouseMove(origin.x + 1014 - 3, origin.y + 677 - 3)
        robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
        try {
            fun contentDp() = shell.ui { shell.overlay?.size }!!
            fun dragToWidth(widthDp: Int, heightDp: Int) {
                var guard = 0
                while (guard++ < 400) {
                    val size = contentDp()
                    val dx = when { size.width < widthDp -> 12; size.width > widthDp + 6 -> -12; else -> 0 }
                    val dy = when { size.height < heightDp -> 12; size.height > heightDp + 6 -> -12; else -> 0 }
                    if (dx == 0 && dy == 0) break
                    val at = java.awt.MouseInfo.getPointerInfo().location
                    robot.mouseMove(at.x + dx, at.y + dy)
                    Thread.sleep(12)
                }
            }
            fun sample(what: String) {
                // Still pressed. The controls are measured against the window as it is now.
                Thread.sleep(700)
                shell.hoverSurface(0.5f, 0.5f)
                shell.awaitShown("Pause")
                Thread.sleep(300)
                val window = contentDp()
                val expectedTier = dev.reflux.design.PlayerChrome.tier(window.width.toFloat(), window.height.toFloat(), true, true)
                val expected = dev.reflux.design.PlayerChrome.spec(expectedTier).playButtonDp
                val measured = pauseHeight() / shell.ui { shell.window.graphicsConfiguration.defaultTransform.scaleX }.toFloat()
                note("mouse drag, button down ($what): window ${window.width}x${window.height} dp is $expectedTier, the pause button is ${"%.0f".format(measured)} dp (expected $expected)")
                shell.capture("drag-$what")
                assertEquals(expected, measured, 2f, "while the button is down ($what), at ${window.width}x${window.height} dp")
            }
            dragToWidth(1300, 780)
            sample("standard")
            dragToWidth(1800, 1000)
            sample("large")
            dragToWidth(1300, 780)
            sample("standard-again")
            dragToWidth(1000, 640)
            sample("compact-again")
            dragToWidth(1700, 960)
            sample("large-before-release")
            robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
            sample("large-after-release")
        } finally {
            robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
        }
        assertSame(session, shell.ui { shell.app.playback }, "the same session across the mouse drag")
    }

    @Test
    fun theControlsFollowTheWindowWhileItIsResizedAndPlaybackNeverNoticesIt() {
        assumeTrue("window tests not requested", windowTestsRequested)
        assumeTrue("libmpv and ffmpeg are needed", needs("libmpv and ffmpeg", SampleMedia.available))
        SampleMedia.video(film, seconds = 150)
        val environment = DesktopEnvironment(root.resolve("data"))
        environment.addLocalFolder(media)
        runBlocking { environment.pipeline.refresh() }
        shell = Shell(environment)
        try {
            shell.awaitShown("Recently Added")
            shell.click("Night Drive, 2021")
            shell.awaitShown("Version")
            shell.click("Play")
            shell.await("playback to start", state = { "${shell.app.playback?.state?.value}" }) {
                shell.app.playback?.state?.value?.let { it.status == PlayerStatus.PLAYING && it.positionMs > 1_000 } == true
            }
            val session = assertNotNull(shell.ui { shell.app.playback })
            assertEquals(PlayerPresentation.FULL, session.presentation)
            val scale = shell.ui { shell.window.graphicsConfiguration.defaultTransform.scaleX }.toFloat()
            // The window's content in dp is its size in pixels over the scale; the tiers are in dp.
            fun controlsAt(widthDp: Int, heightDp: Int): Float {
                resize(widthDp + 14, heightDp + 37)
                shell.hoverSurface(0.5f, 0.5f)
                shell.awaitShown("Pause")
                // Let the new size reach the controls window and be drawn.
                Thread.sleep(500)
                shell.hoverSurface(0.5f, 0.55f)
                shell.awaitShown("Pause")
                return pauseHeight() / scale
            }
            val engine = shell.ui { shell.app.playback }
            val steps = listOf(
                1000 to 640 to 44f, // compact: today's 44 dp play button
                1300 to 780 to 48f, // standard
                1800 to 1000 to 68f, // large
                1300 to 780 to 48f, // and back down: standard
                1000 to 640 to 44f, // compact
            )
            val seen = mutableListOf<Float>()
            for ((size, expected) in steps) {
                val (w, h) = size
                val measured = controlsAt(w, h)
                note("resize: ${w}x${h} dp, the pause button is ${"%.0f".format(measured)} dp (expected $expected)")
                seen += measured
                assertEquals(expected, measured, 2f, "the controls at ${w}x${h} dp")
                assertSame(engine, shell.ui { shell.app.playback }, "the same session across the resize")
                assertTrue(shell.ui { shell.app.playback?.state?.value?.status } == PlayerStatus.PLAYING, "playback goes on")
            }
            // A drag is not a jump: dozens of small resizes a second, with nothing waited for between them.
            fun drag(fromW: Int, toW: Int, fromH: Int, toH: Int, steps: Int = 60) {
                for (i in 0..steps) {
                    val w = fromW + (toW - fromW) * i / steps
                    val h = fromH + (toH - fromH) * i / steps
                    shell.ui { shell.window.setSize(w + 14, h + 37) }
                    Thread.sleep(16)
                }
            }
            // And the real thing: the window's corner taken with the mouse, which is the operating system's own resize loop,
            // sampled while the button is still down, before anything is released.
            realMouseDrag(session)
            drag(1000, 1800, 640, 1000)
            shell.hoverSurface(0.5f, 0.5f)
            shell.awaitShown("Pause")
            Thread.sleep(400)
            note("drag up: the pause button is ${"%.0f".format(pauseHeight() / scale)} dp (expected 68)")
            assertEquals(68f, pauseHeight() / scale, 2f, "after dragging up to 1800x1000 dp")
            drag(1800, 1000, 1000, 640)
            shell.hoverSurface(0.5f, 0.55f)
            shell.awaitShown("Pause")
            Thread.sleep(400)
            note("drag down: the pause button is ${"%.0f".format(pauseHeight() / scale)} dp (expected 44)")
            assertEquals(44f, pauseHeight() / scale, 2f, "after dragging back down to 1000x640 dp")
            assertSame(engine, shell.ui { shell.app.playback }, "the same session across the drags")
        } finally {
            shell.close()
            root.toFile().deleteRecursively()
        }
    }
}
