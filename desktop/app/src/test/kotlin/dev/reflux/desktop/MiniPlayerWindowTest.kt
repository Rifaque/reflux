package dev.reflux.desktop

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import dev.reflux.core.model.MediaId
import dev.reflux.core.playback.PlayerState
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.desktop.engine.DesktopEnvironment
import dev.reflux.ui.state.PlaybackSession
import dev.reflux.ui.state.PlayerPresentation
import dev.reflux.ui.state.VideoFrame
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.awt.Component
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import java.awt.Rectangle
import java.awt.event.KeyEvent
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.SwingUtilities
import kotlin.io.path.createDirectories
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The desktop mini-player, in the real shell with a real engine: one playback session, shown across the whole window
 * or in the navigation's dock while the viewer browses, with libmpv rendering into the same native child window
 * throughout (docs/DECISIONS.md, 2026-11-12, "The desktop mini-player"). This is the feasibility trial that opened M9,
 * kept as the regression test of what it found.
 *
 * It opens a window, so it only runs on request: `./gradlew :desktop:app:test -Preflux.windowTests=true` on a machine
 * with a display, libmpv, and FFmpeg. What it measured is printed as lines that start with `TRIAL`.
 */
class MiniPlayerWindowTest {
    private val root: Path = Files.createTempDirectory("reflux-mini")
    private val media: Path = root.resolve("Media").createDirectories()
    private val first: Path = media.resolve("Night Drive (2021)/Night.Drive.2021.720p.mkv")
    private val second: Path = media.resolve("Harbour Lights (2019)/Harbour.Lights.2019.720p.mkv")

    private lateinit var environment: DesktopEnvironment
    private lateinit var shell: Shell

    private val state: PlayerState get() = shell.ui { shell.app.playback?.state?.value } ?: PlayerState()
    private val position: Long get() = state.positionMs

    private fun awaitPlayer(what: String, condition: (PlayerState) -> Boolean) =
        shell.await(what, state = { "Player: ${shell.app.playback?.state?.value}, ${describe()}" }) { shell.app.playback?.state?.value?.let(condition) == true }

    private fun note(line: String) = println("TRIAL $line")

    @Test
    fun onePlaybackMovesBetweenTheWholeWindowAndTheDock() {
        assumeTrue("window tests not requested", windowTestsRequested)
        assumeTrue("libmpv and ffmpeg are needed", needs("libmpv and ffmpeg", SampleMedia.available))
        SampleMedia.video(first, seconds = 150)
        second.parent.createDirectories()
        Files.copy(first, second)

        environment = DesktopEnvironment(root.resolve("data"))
        environment.addLocalFolder(media)
        runBlocking { environment.pipeline.refresh() }
        val movies = environment.library.movies().associate { it.item.title to it.item.id }
        val nightDrive = movies.getValue("Night Drive")
        val harbour = movies.getValue("Harbour Lights")

        shell = Shell(environment)
        try {
            shell.awaitShown("Recently Added")
            note("environment: ${System.getProperty("os.name")} ${System.getProperty("os.version")}, session ${System.getenv("XDG_SESSION_TYPE") ?: "n/a"}, screens ${screens().map { "${it.width}x${it.height}@${it.x},${it.y}" }}")
            val session = startsAcrossTheWholeWindow()
            val surface = stepsAsideIntoTheDock(session)
            theShellStaysTheViewers(session)
            nothingTakesFocusOrStaysOnTop(session)
            theDockOperatesTheSameSession(session)
            whatCoversThePageHidesThePicture(session)
            theDockFollowsTheWindow(session)
            anotherDisplay(session)
            comesBackToTheWholePlayer(session, surface)
            leavesFullscreenForTheDock(session, surface)
            anotherTitleReplacesTheSession(session, nightDrive, harbour)
            stoppingFromTheDockRecordsThePlace(harbour)
        } finally {
            shell.close()
            root.toFile().deleteRecursively()
        }
    }

    private fun startsAcrossTheWholeWindow(): PlaybackSession {
        shell.click("Night Drive, 2021")
        shell.awaitShown("Version")
        shell.click("Play")
        awaitPlayer("playback to start") { it.status == PlayerStatus.PLAYING && it.positionMs > 1_000 }
        val session = assertNotNull(shell.ui { shell.app.playback })
        assertEquals(PlayerPresentation.FULL, session.presentation)
        shell.await("the picture to be the whole window", state = ::describe) { fillsTheWindow() && covered() }
        return session
    }

    /** The first question of the trial: the engine's own native window, moved and resized under it while it plays. */
    private fun stepsAsideIntoTheDock(session: PlaybackSession): Component {
        val surface = surface()
        shell.hoverSurface(0.5f, 0.5f)
        shell.awaitShown("Mini player")
        // Watched as it goes: every time the toolkit moves or resizes the surface on its way is one step of the journey.
        val steps = java.util.Collections.synchronizedList(ArrayList<Long>())
        val journey = object : java.awt.event.HierarchyBoundsAdapter() {
            override fun ancestorMoved(event: java.awt.event.HierarchyEvent) { steps += System.nanoTime() }
            override fun ancestorResized(event: java.awt.event.HierarchyEvent) { steps += System.nanoTime() }
        }
        shell.ui { surface.addHierarchyBoundsListener(journey) }
        shell.click("Mini player")
        shell.await("the picture to be in the dock", state = ::describe) { docked() && covered() }
        shell.ui { surface.removeHierarchyBoundsListener(journey) }
        val times = steps.toList()
        if (times.size > 1) {
            val gaps = times.zipWithNext { earlier, later -> (later - earlier) / 1_000_000.0 }
            note("collapse: the surface was moved ${times.size} times over ${"%.0f".format((times.last() - times.first()) / 1_000_000.0)} ms; longest gap between two moves ${"%.0f".format(gaps.max())} ms, median ${"%.0f".format(gaps.sorted()[gaps.size / 2])} ms")
        } else {
            note("collapse: the surface was moved ${times.size} time(s): it went to the dock at once")
        }

        assertSame(session, shell.ui { shell.app.playback }, "the same session")
        assertSame(surface, surface(), "the same native surface: the engine was not given another")
        assertEquals(PlayerPresentation.MINI, session.presentation)
        val dock = shell.ui { surfaceInWindow() }
        val whole = shell.ui { wholeWindow() }
        assertTrue(dock.width < whole.width / 2 && dock.height < whole.height / 2, "the picture is small: $dock in $whole")
        assertTrue(dock.x > whole.width / 2 && dock.y > whole.height / 2, "and in the bottom corner: $dock in $whole")

        val before = position
        awaitPlayer("playback to go on in the dock") { it.status == PlayerStatus.PLAYING && it.positionMs > before + 1_500 }
        // The picture is on screen there, and the page is on screen around it.
        shell.capture("mini-docked")?.let { image ->
            val (inDock, onPage) = shell.ui {
                val scale = scale()
                val content = shell.window.contentPane
                val origin = SwingUtilities.convertPoint(content, 0, 0, shell.window)
                fun colors(area: Rectangle): Int {
                    val seen = HashSet<Int>()
                    for (x in area.x + 8 until area.x + area.width - 8 step 6) for (y in area.y + 8 until area.y + area.height - 8 step 6) {
                        val px = origin.x + (x / scale).toInt()
                        val py = origin.y + (y / scale).toInt()
                        if (px in 0 until image.width && py in 0 until image.height) seen += image.getRGB(px, py) and 0xF0F0F0
                    }
                    return seen.size
                }
                colors(dock) to colors(Rectangle(whole.width / 4, whole.height / 8, whole.width / 3, whole.height / 3))
            }
            note("screen: $inDock colors inside the dock's picture, $onPage on the page beside it")
            assertTrue(inDock > 8, "the video is visible in the dock ($inDock colors)")
            assertTrue(onPage > 2, "the page is visible around it ($onPage colors)")
        }
        return surface
    }

    /** The page is the viewer's while this plays: pointer and keyboard go to it, and the picture's own window to the picture. */
    private fun theShellStaysTheViewers(session: PlaybackSession) {
        shell.click("Back")
        shell.awaitShown("Recently Added")
        shell.click("Movies")
        shell.awaitShown("Harbour Lights, 2019")
        assertSame(session, shell.ui { shell.app.playback })
        // Escape is Back for the page, not the end of what is playing beside it.
        shell.click("Harbour Lights, 2019")
        shell.awaitShown("Version")
        shell.key(KeyEvent.VK_ESCAPE)
        shell.awaitShown("Harbour Lights, 2019")
        assertSame(session, shell.ui { shell.app.playback }, "Escape went back a page and left the playback alone")
        val before = position
        awaitPlayer("playback to go on while browsing") { it.status == PlayerStatus.PLAYING && it.positionMs > before + 800 }

        if (isWindows) {
            val overlay = assertNotNull(shell.overlay)
            val overPicture = shell.ui { HitTest.rootAt(overlay, 0.5, 0.5) }
            val overPage = shell.ui { HitTest.rootAt(shell.window, 0.5, 0.33) }
            assertEquals(Native.getWindowPointer(overlay), overPicture, "over the picture, the pointer reaches the mini-player's own surface")
            assertEquals(Native.getWindowPointer(shell.window), overPage, "over the page, the pointer reaches the page")
            note("pointer: the system sends input over the picture to the overlay window and input over the page to the main window")
        }
    }

    private fun nothingTakesFocusOrStaysOnTop(session: PlaybackSession) {
        val overlay = assertNotNull(shell.overlay)
        shell.ui {
            // An owned window is on top exactly when its owner is (this harness puts its own window there to be seen):
            // the mini-player never asks for more than the Reflux window has.
            assertEquals(shell.window.isAlwaysOnTop, overlay.isAlwaysOnTop, "the mini-player is above the Reflux window and nothing else")
            assertFalse(overlay.focusableWindowState, "and never takes the keyboard from the page")
            assertSame(shell.window, overlay.owner, "it belongs to the Reflux window: it goes where that goes, and has no taskbar entry of its own")
            val active = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
            assertTrue(active == null || active === shell.window, "the active window is the page's, or another application's: $active")
        }
        assertSame(session, shell.ui { shell.app.playback })
        note("windows: overlay owned by the main window, not always-on-top, not focusable; ${shell.ui { shell.window.ownedWindows.count { it.isShowing } }} owned window showing")
    }

    private fun theDockOperatesTheSameSession(session: PlaybackSession) {
        shell.click("Pause")
        awaitPlayer("the dock's button to pause") { it.status == PlayerStatus.PAUSED }
        shell.click("Play")
        awaitPlayer("the dock's button to play") { it.status == PlayerStatus.PLAYING }
        // The keyboard's way back to the player, and back out again.
        shell.key(KeyEvent.VK_I)
        shell.await("I to bring the player back", state = ::describe) { session.presentation == PlayerPresentation.FULL && fillsTheWindow() && covered() }
        shell.awaitShown("Back to library")
        shell.key(KeyEvent.VK_I)
        shell.await("I to put it aside again", state = ::describe) { session.presentation == PlayerPresentation.MINI && docked() && covered() }
        assertSame(session, shell.ui { shell.app.playback })
        assertEquals(PlayerStatus.PLAYING, state.status)
    }

    /** A native surface is above everything Reflux draws in its window, so it is taken out of sight under what covers the page. */
    private fun whatCoversThePageHidesThePicture(session: PlaybackSession) {
        val surface = surface()
        shell.ui { shell.app.offlineOpen = true }
        shell.await("the picture to be out of sight under the sheet", state = ::describe) {
            shell.app.videoFrame == VideoFrame.Hidden && surfaceInWindow().let { it.x + it.width <= 0 } && shell.overlay == null
        }
        val before = position
        awaitPlayer("playback to go on unseen") { it.status == PlayerStatus.PLAYING && it.positionMs > before + 1_200 }
        shell.ui { shell.app.offlineOpen = false }
        shell.await("the picture to be back in the dock", state = ::describe) { docked() && covered() }
        assertSame(surface, surface())
        assertSame(session, shell.ui { shell.app.playback })
        note("covered: out of sight the surface is outside the window and its overlay hidden; playback went on and the same surface came back")
    }

    private fun theDockFollowsTheWindow(session: PlaybackSession) {
        val before = shell.ui { shell.window.bounds }
        shell.ui { shell.window.setSize(before.width - 240, before.height - 160) }
        shell.await("the dock to follow a smaller window", state = ::describe) { shell.window.width < before.width - 150 && docked() && covered() }
        shell.ui { shell.window.bounds = before }
        shell.await("the dock to follow the window back", state = ::describe) { shell.window.width > before.width - 60 && docked() && covered() }
        assertEquals(PlayerStatus.PLAYING, state.status)
        assertSame(session, shell.ui { shell.app.playback })
    }

    /** Another display, where there is one: the window is moved there and back, and the picture and its surface go with it. */
    private fun anotherDisplay(session: PlaybackSession) {
        val home = shell.ui { shell.window.bounds }
        val here = shell.ui { shell.window.graphicsConfiguration.bounds }
        val other = screens().firstOrNull { it != here }
        if (other == null) {
            note("displays: one display here; a move to another was not exercised")
            return
        }
        val width = minOf(home.width, other.width - 120)
        val height = minOf(home.height, other.height - 120)
        shell.ui { shell.window.setBounds(other.x + 60, other.y + 60, width, height) }
        shell.await("the window to be on the other display", state = ::describe) { shell.window.graphicsConfiguration.bounds == other && docked() && covered() }
        val before = position
        awaitPlayer("playback to go on there") { it.status == PlayerStatus.PLAYING && it.positionMs > before + 1_000 }
        val scaleThere = shell.ui { scale() }
        shell.ui { shell.window.bounds = home }
        shell.await("the window to be back", state = ::describe) { shell.window.graphicsConfiguration.bounds == here && docked() && covered() }
        assertSame(session, shell.ui { shell.app.playback })
        note("displays: moved to $other (scale $scaleThere) and back to $here (scale ${shell.ui { scale() }}); the picture stayed in its dock and kept playing")
    }

    private fun comesBackToTheWholePlayer(session: PlaybackSession, surface: Component) {
        val before = position
        // A click on the picture is the way back.
        shell.clickSurface(0.5f, 0.5f)
        shell.await("a click on the picture to bring the player back", state = ::describe) {
            session.presentation == PlayerPresentation.FULL && fillsTheWindow() && covered()
        }
        shell.awaitShown("Back to library")
        assertSame(session, shell.ui { shell.app.playback })
        assertSame(surface, surface())
        assertTrue(position >= before, "playback carried on from where it was: $before to $position")
        awaitPlayer("playback to go on in the whole window") { it.status == PlayerStatus.PLAYING && it.positionMs > before + 800 }
    }

    private fun leavesFullscreenForTheDock(session: PlaybackSession, surface: Component) {
        val windowed = shell.ui { shell.window.bounds }
        shell.hoverSurface(0.5f, 0.5f)
        shell.click("Fullscreen")
        shell.await("fullscreen", state = ::describe) {
            val screen = shell.window.graphicsConfiguration.bounds
            shell.app.platform.fullscreen && surface().width >= screen.width && surface().height >= screen.height && covered()
        }
        shell.key(KeyEvent.VK_I)
        shell.await("the window to be a window again, with the picture in its dock", state = ::describe) {
            !shell.app.platform.fullscreen && restored(shell.window.bounds, windowed) && docked() && covered()
        }
        assertSame(session, shell.ui { shell.app.playback })
        assertSame(surface, surface())
        val before = position
        awaitPlayer("playback to go on after fullscreen") { it.status == PlayerStatus.PLAYING && it.positionMs > before + 800 }
        note("fullscreen: collapsing from fullscreen restored the window and docked the same surface")
    }

    /** Playing something else is one session giving way to the next, never two. */
    private fun anotherTitleReplacesTheSession(session: PlaybackSession, playing: MediaId, next: MediaId) {
        // A place worth keeping: the first minute of a film is not one (WatchRules).
        shell.ui { session.seekTo(70_000) }
        awaitPlayer("the seek, from the dock") { it.status == PlayerStatus.PLAYING && it.positionMs >= 70_000 }
        val left = position
        shell.click("Harbour Lights, 2019")
        shell.awaitShown("Version")
        shell.click("Play")
        shell.await("the other film to be what plays", state = ::describe) { shell.app.playback?.let { it !== session && it.title == "Harbour Lights" } == true }
        awaitPlayer("the other film to play") { it.status == PlayerStatus.PLAYING && it.positionMs > 500 }
        assertEquals(1, shell.ui { surfaces().size }, "one video surface: the first engine has gone")
        assertEquals(PlayerPresentation.FULL, shell.ui { shell.app.playback?.presentation }, "a playback the viewer just started has the screen")
        // It went on playing while the other film was found and chosen; its place is where it had got to by then.
        shell.await("the first film's place to be recorded", state = { "left at $left, recorded ${environment.library.watchState(playing)}" }) {
            environment.library.watchState(playing)?.positionMs?.let { it >= left - 1_500 && it <= left + 30_000 } == true
        }
        assertNull(environment.library.watchState(next)?.takeIf { it.completed })
    }

    private fun stoppingFromTheDockRecordsThePlace(playing: MediaId) {
        shell.key(KeyEvent.VK_I)
        shell.await("the picture to be in the dock", state = ::describe) { docked() && covered() }
        shell.ui { shell.app.playback?.seekTo(80_000) }
        awaitPlayer("the seek, from the dock") { it.status == PlayerStatus.PLAYING && it.positionMs >= 80_000 }
        val left = position
        shell.click("Stop playing")
        shell.await("the player to be gone") { shell.app.playback == null && surfaces().isEmpty() && shell.overlay == null }
        shell.await("its place to be recorded", state = { "left at $left, recorded ${environment.library.watchState(playing)}" }) {
            environment.library.watchState(playing)?.positionMs?.let { abs(it - left) < 4_000 } == true
        }
        shell.awaitGone("Stop playing")
    }

    // What the test reads off the real window --------------------------------------------------------------------

    private fun screens(): List<Rectangle> = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.bounds }

    private fun surfaces(): List<Component> {
        val found = ArrayList<Component>()
        fun walk(component: Component) {
            if (component.name == VideoSurface.NAME && component.isShowing) found += component
            (component as? java.awt.Container)?.components?.forEach(::walk)
        }
        walk(shell.window)
        return found
    }

    private fun surface(): Component = shell.ui { surfaces().singleOrNull() ?: error("expected one video surface, found ${surfaces().size}") }

    private fun scale(): Double = shell.window.graphicsConfiguration.defaultTransform.scaleX

    /** Where the video surface is in the window's content, in the window's pixels. Must run on the UI thread. */
    private fun surfaceInWindow(): Rectangle {
        val surface = surfaces().singleOrNull() ?: return Rectangle()
        val origin = SwingUtilities.convertPoint(surface, 0, 0, shell.window.contentPane)
        val scale = scale()
        return Rectangle(Math.round(origin.x * scale).toInt(), Math.round(origin.y * scale).toInt(), Math.round(surface.width * scale).toInt(), Math.round(surface.height * scale).toInt())
    }

    private fun wholeWindow(): Rectangle {
        val content = shell.window.contentPane
        val scale = scale()
        return Rectangle(0, 0, Math.round(content.width * scale).toInt(), Math.round(content.height * scale).toInt())
    }

    /** Whether the surface is the whole of the window's content, to the pixel a fractional display scale rounds to. */
    private fun fillsTheWindow(): Boolean {
        val surface = surfaceInWindow()
        val whole = wholeWindow()
        val tolerance = (2 * scale()).toInt() + 2
        return abs(surface.x) <= tolerance && abs(surface.y) <= tolerance && abs(surface.width - whole.width) <= tolerance && abs(surface.height - whole.height) <= tolerance
    }

    /** Whether the surface is where the dock's picture box is, at rest. Must run on the UI thread. */
    private fun docked(): Boolean {
        val frame = shell.app.videoFrame as? VideoFrame.Docked ?: return false
        val surface = surfaceInWindow()
        val tolerance = (2 * scale()).toInt() + 2
        return abs(surface.x - frame.bounds.left) <= tolerance && abs(surface.y - frame.bounds.top) <= tolerance &&
            abs(surface.width - frame.bounds.width) <= tolerance && abs(surface.height - frame.bounds.height) <= tolerance
    }

    /** Whether the overlay window is exactly over the surface. Must run on the UI thread. */
    private fun covered(): Boolean {
        val surface = surfaces().singleOrNull() ?: return false
        val overlay = shell.overlay ?: return false
        return surface.locationOnScreen == overlay.locationOnScreen && surface.size == overlay.size
    }

    private fun describe(): String {
        val surface = surfaces().singleOrNull()
        return "frame=${shell.app.videoFrame} presentation=${shell.app.playback?.presentation} surface=${surfaceInWindow()} whole=${wholeWindow()} " +
            "surfaceOnScreen=${surface?.let { runCatching { it.locationOnScreen }.getOrNull() }} overlay=${shell.overlay?.bounds} window=${shell.window.bounds} fullscreen=${shell.app.platform.fullscreen}"
    }

    private fun restored(now: Rectangle, before: Rectangle): Boolean =
        if (isWindows) now == before else now.size == before.size && now.location.distance(before.location) <= 48.0
}

/** Asks Windows which top-level window the pointer would reach at a point of the screen, without moving the pointer there. */
private object HitTest {
    @Suppress("FunctionName")
    private interface User32 : StdCallLibrary {
        fun WindowFromPoint(point: Long): Pointer?
        fun GetAncestor(window: Pointer, flags: Int): Pointer?
        fun GetWindowRect(window: Pointer, rectangle: IntArray): Boolean
    }

    private val user32: User32 by lazy { Native.load("user32", User32::class.java) }
    private const val GA_ROOT = 2

    /** The top-level window the pointer reaches at a point of [window], given as fractions of its size on screen. */
    fun rootAt(window: java.awt.Window, fractionX: Double, fractionY: Double): Pointer? {
        val rectangle = IntArray(4)
        if (!user32.GetWindowRect(Native.getWindowPointer(window), rectangle)) return null
        val x = (rectangle[0] + (rectangle[2] - rectangle[0]) * fractionX).toLong()
        val y = (rectangle[1] + (rectangle[3] - rectangle[1]) * fractionY).toLong()
        // A POINT passed by value is its two 32-bit halves in one register.
        val hit = user32.WindowFromPoint((y shl 32) or (x and 0xFFFFFFFFL)) ?: return null
        return user32.GetAncestor(hit, GA_ROOT)
    }
}
