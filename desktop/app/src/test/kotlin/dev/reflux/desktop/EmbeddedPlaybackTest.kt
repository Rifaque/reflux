package dev.reflux.desktop

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import dev.reflux.core.model.MediaId
import dev.reflux.core.playback.PlayerState
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.desktop.engine.DesktopEnvironment
import dev.reflux.ui.state.Destination
import dev.reflux.ui.state.PlayerPresentation
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs the real desktop shell and plays real files in its window: libmpv renders into the native video
 * surface, Reflux's controls are layered above it, and a mouse operates them.
 *
 * It opens a window, so it only runs on request: `./gradlew :desktop:app:test -Preflux.windowTests=true`
 * on a machine with a display, libmpv, and FFmpeg. `-Preflux.screenshots=<dir>` also saves what was shown.
 */
class EmbeddedPlaybackTest {
    private val root: Path = Files.createTempDirectory("reflux-desktop")
    private val media: Path = root.resolve("Media").createDirectories()
    private val movieFile: Path = media.resolve("Night Drive (2021)/Night.Drive.2021.720p.mkv")
    private val episodeFiles = (1..2).map { media.resolve("Harbour Lights/Season 1/Harbour.Lights.S01E0$it.mkv") }

    private lateinit var environment: DesktopEnvironment
    private lateinit var shell: Shell

    private val state: PlayerState get() = shell.ui { shell.app.playback?.state?.value } ?: PlayerState()
    private val position: Long get() = state.positionMs

    private fun awaitPlayer(what: String, condition: (PlayerState) -> Boolean) =
        shell.await(what, state = { "Player: ${shell.app.playback?.state?.value}" }) { shell.app.playback?.state?.value?.let(condition) == true }

    @Test
    fun playsInsideTheWindowAndAnswersToTheMouse() {
        assumeTrue("window tests not requested", windowTestsRequested)
        assumeTrue("libmpv and ffmpeg are needed", needs("libmpv and ffmpeg", SampleMedia.available))
        SampleMedia.video(movieFile, seconds = 150)
        SampleMedia.video(episodeFiles[0], seconds = 14)
        Files.copy(episodeFiles[0], episodeFiles[1])

        environment = DesktopEnvironment(root.resolve("data"))
        environment.addLocalFolder(media)
        runBlocking { environment.pipeline.refresh() }
        val movie = environment.library.movies().single().item.id
        val episodes = environment.library.showDetail(environment.library.shows().single().item.id)!!.seasons.single().episodes.map { it.item.id }

        shell = Shell(environment)
        try {
            shell.awaitShown("Recently Added")
            // This test checks the screen itself, which a locked or covered desktop does not show.
            if (isWindows) assertTrue(WindowsHitTest.receivesPointer(shell.window), "the desktop is locked or something covers the Reflux window: this test needs it on screen")
            val windowed = shell.ui { shell.window.bounds }
            shell.capture("window-home")

            startsFromDetailsWithVideoUnderTheControls()
            controlsAnswerToTheMouse()
            fullscreenKeepsPlaying(windowed)
            positionIsRememberedAndResumed(movie)
            episodesFollowEachOther(episodes)
            theSurfaceFollowsTheWindowAndSurvivesARestart(episodes[0])
            aMissingFileIsExplained(movie)
            closingTheWindowSavesWhatWasPlaying(episodes[0])
        } finally {
            shell.close()
            root.toFile().deleteRecursively()
        }
    }

    private fun startsFromDetailsWithVideoUnderTheControls() {
        shell.click("Night Drive, 2021")
        shell.awaitShown("Version")
        shell.awaitGone("Recently Added")
        shell.click("Play")
        awaitPlayer("playback to start") { it.status == PlayerStatus.PLAYING && it.positionMs > 1_000 }
        val session = assertNotNull(shell.ui { shell.app.playback })
        assertEquals("Night Drive", session.title)
        assertEquals(listOf("eng", "jpn"), state.audioTracks.map { it.language })
        assertEquals(listOf("Intro", "Chapter 1", "Chapter 2"), state.chapters.map { it.title })

        // The controls live in a transparent window that covers the video surface exactly and never takes focus.
        val surface = assertNotNull(shell.find { it.name == VideoSurface.NAME && it.isShowing })
        val overlay = assertNotNull(shell.overlay)
        shell.ui {
            assertEquals(surface.locationOnScreen, overlay.locationOnScreen)
            assertEquals(surface.size, overlay.size)
            assertFalse(overlay.focusableWindowState)
        }
        shell.capture("window-playing")?.let { assertTrue(colors(it) > 8, "the video is visible inside the window") }
        // The system itself sends pointer input over the video to the controls, not through them.
        if (isWindows) assertTrue(WindowsHitTest.receivesPointer(overlay), "the controls window is what the pointer hits")
    }

    private fun controlsAnswerToTheMouse() {
        shell.click("Skip intro")
        awaitPlayer("the intro to be skipped") { it.positionMs >= 8_000 }

        // The video is the biggest button.
        shell.clickSurface(0.5f, 0.4f)
        awaitPlayer("a click on the video to pause") { it.status == PlayerStatus.PAUSED }

        shell.drag("Timeline", from = 0.1f, to = 0.6f)
        awaitPlayer("the scrubber to seek") { it.positionMs in 84_000..96_000 }
        shell.capture("window-paused")

        shell.click("Chapters")
        shell.click("Chapter 1")
        awaitPlayer("the chapter to be played") { it.positionMs in 7_500..9_500 }
        assertNull(shell.ui { shell.app.playback?.menu })

        val japanese = state.audioTracks.single { it.language == "jpn" }.id
        shell.click("Audio")
        shell.capture("window-menu")
        shell.click("Japanese", exact = false)
        awaitPlayer("the audio track to change") { it.selectedAudioId == japanese }

        shell.click("Subtitles")
        shell.click("English", exact = false)
        awaitPlayer("subtitles to turn on") { it.selectedSubtitleId != null }
        shell.click("Subtitles")
        shell.click("Off")
        awaitPlayer("subtitles to turn off") { it.selectedSubtitleId == null }

        shell.click("Playback speed")
        shell.click("1.5×")
        awaitPlayer("the speed to change") { it.speed == 1.5 }
        shell.click("Playback speed")
        shell.click("Normal")
        awaitPlayer("the speed to return to normal") { it.speed == 1.0 }

        shell.wheel("Minimize player", 1)
        awaitPlayer("the wheel to turn the volume down") { it.volume == 95 }
        shell.click("Mute")
        awaitPlayer("mute") { it.muted }
        shell.click("Unmute")
        awaitPlayer("unmute") { !it.muted }

        shell.click("Play")
        awaitPlayer("playback to resume") { it.status == PlayerStatus.PLAYING }
    }

    private fun fullscreenKeepsPlaying(windowed: java.awt.Rectangle) {
        val window = shell.window
        val surface = assertNotNull(shell.find { it.name == VideoSurface.NAME && it.isShowing })
        shell.hoverSurface(0.5f, 0.5f)
        shell.click("Fullscreen")
        shell.await("fullscreen", state = {
            "window=${window.bounds} insets=${window.insets} screen=${window.graphicsConfiguration.bounds} surface=${surface.size} overlay=${shell.overlay?.bounds}"
        }) {
            // The whole screen, and the same surface still under the controls. On Windows the frame is gone
            // too; on X11 the window manager keeps it off screen and the content fills the display.
            val screen = window.graphicsConfiguration.bounds
            val framed = if (isWindows) window.bounds == screen && window.insets.top == 0 else surface.locationOnScreen == screen.location
            shell.app.platform.fullscreen && framed && surface.width >= screen.width && surface.height >= screen.height &&
                shell.overlay?.size == surface.size
        }
        val before = position
        awaitPlayer("playback to continue in fullscreen") { it.status == PlayerStatus.PLAYING && it.positionMs > before + 800 }
        shell.capture("window-fullscreen")

        // The keyboard reaches the player whichever window has focus.
        shell.key(KeyEvent.VK_SPACE)
        awaitPlayer("Space to pause") { it.status == PlayerStatus.PAUSED }
        shell.click("Exit fullscreen")
        shell.await("the window to be restored", state = { "window=${window.bounds} before=$windowed" }) {
            !shell.app.platform.fullscreen && restored(window.bounds, windowed)
        }
    }

    private fun positionIsRememberedAndResumed(movie: MediaId) {
        shell.drag("Timeline", from = 0.2f, to = 0.7f)
        awaitPlayer("the seek") { it.positionMs in 99_000..111_000 }
        val left = position
        shell.click("Stop playing")
        shell.await("the player to close") { shell.app.playback == null && shell.find { it.name == VideoSurface.NAME } == null }
        shell.await("the window to be as it was", state = { "window=${shell.window.bounds}" }) { !shell.app.platform.fullscreen }
        shell.await("the position to be saved") { environment.library.watchState(movie)?.positionMs in (left - 1_500)..(left + 1_500) }
        assertNull(shell.ui { shell.app.notice })
        shell.capture("window-after")

        // The details page now offers to resume, and resuming starts where the user left.
        shell.click("Resume")
        awaitPlayer("playback to resume near where it stopped") { it.status == PlayerStatus.PLAYING && it.positionMs >= left - 3_000 }
        assertTrue(position < left + 15_000)
        // Escape steps the player aside into the dock (the desktop has one) and the playback goes on; it does not end it.
        shell.key(KeyEvent.VK_ESCAPE)
        shell.await("Escape to put the player in the dock") { shell.app.playback?.presentation == PlayerPresentation.MINI }
        assertTrue(shell.ui { shell.app.navigator.current } is Destination.Details, "Escape left the page under the player where it was")
        shell.click("Stop playing")
        shell.await("the dock's stop to end playback") { shell.app.playback == null }
    }

    private fun episodesFollowEachOther(episodes: List<MediaId>) {
        shell.click("Back")
        shell.awaitShown("Recently Added")
        shell.click("Harbour Lights")
        shell.click("Play S1 · E1")
        awaitPlayer("the first episode to start") { it.status == PlayerStatus.PLAYING }
        assertEquals("S1 · E1", shell.ui { shell.app.playback?.subtitle }?.take(7))
        shell.hoverSurface(0.5f, 0.5f)
        shell.awaitShown("Next episode")

        // At its end the next episode is offered, and one click starts it.
        shell.drag("Timeline", from = 0.3f, to = 0.9f)
        shell.awaitShown("Play now")
        shell.capture("window-up-next")
        shell.click("Play now")
        shell.await("the second episode to start") { shell.app.playback?.subtitle?.startsWith("S1 · E2") == true }
        awaitPlayer("the second episode to play") { it.status == PlayerStatus.PLAYING && it.positionMs > 500 }
        shell.await("the first episode to be watched") { environment.library.watchState(episodes[0])?.completed == true }

        // The last episode has nothing after it: the player closes by itself.
        shell.hoverSurface(0.5f, 0.5f)
        shell.drag("Timeline", from = 0.3f, to = 0.9f)
        shell.await("the player to close after the last episode") { shell.app.playback == null }
        shell.await("the last episode to be watched") { environment.library.watchState(episodes[1])?.completed == true }
    }

    private fun theSurfaceFollowsTheWindowAndSurvivesARestart(episode: MediaId) {
        shell.ui { shell.app.play(episode) }
        awaitPlayer("playback to start") { it.status == PlayerStatus.PLAYING }
        fun covered(): Boolean {
            val surface = shell.find { it.name == VideoSurface.NAME && it.isShowing } ?: return false
            val overlay = shell.overlay ?: return false
            return surface.locationOnScreen == overlay.locationOnScreen && surface.size == overlay.size
        }
        shell.await("the controls to cover the video") { covered() }

        // Resizing the window resizes the video and keeps the controls exactly over it.
        val before = shell.ui { shell.window.size }
        shell.ui { shell.window.setSize(before.width - 220, before.height - 140) }
        shell.await("the controls to follow the smaller window") {
            covered() && shell.overlay!!.width < before.width - 150
        }
        shell.ui { shell.window.size = before }
        shell.await("the controls to follow the window back") { covered() && shell.overlay!!.width > before.width - 60 }

        // Closing and playing again in the same breath: the second playback waits for the first engine to
        // let go, then gets a surface of its own with the controls over it.
        shell.ui {
            shell.app.playback?.close()
            shell.app.play(episode)
        }
        shell.await("the second playback to start") { shell.app.playback?.state?.value?.let { it.status == PlayerStatus.PLAYING && it.positionMs > 300 } == true }
        shell.await("the controls to cover the new video") { covered() }
        // Escape puts it in the dock (still playing, one surface); stopping from there lets go of the surface.
        shell.key(KeyEvent.VK_ESCAPE)
        shell.await("the player to be in the dock") { shell.app.playback?.presentation == PlayerPresentation.MINI }
        assertEquals(null, shell.ui { shell.app.playback?.error }, "the dock holds a playing picture")
        shell.click("Stop playing")
        shell.await("the player to close") { shell.app.playback == null && shell.find { it.name == VideoSurface.NAME } == null }
    }

    private fun aMissingFileIsExplained(movie: MediaId) {
        shell.ui { shell.app.navigator.open(Destination.Details(movie)) }
        shell.awaitShown("Resume")
        Files.delete(movieFile)
        shell.click("Resume")
        shell.awaitShown("Playback stopped")
        assertTrue(shell.shows("moved or deleted", exact = false), "the failure is explained in the user's terms")
        assertTrue(shell.shows("Details:", exact = false), "with the engine's own words for diagnostics")
        shell.capture("window-playback-error")
        shell.click("Back to library")
        shell.await("the player to close") { shell.app.playback == null }

        // The library catches up with the disk, and the open page says what happened.
        shell.await("the movie to leave the library") { environment.library.movies().isEmpty() }
        shell.awaitShown("This is no longer in your library")
        assertTrue(Files.readString(environment.diagnostics.file).contains("UNREACHABLE"), "the failure was written down")
        // Back leads to where the user was before: the show they had just finished.
        shell.click("Back")
        shell.awaitShown("Season 1")
    }

    private fun closingTheWindowSavesWhatWasPlaying(episode: MediaId) {
        val lastPlayed = environment.library.watchState(episode)?.lastPlayedAtEpochMs ?: 0
        shell.ui { shell.app.play(episode) }
        awaitPlayer("playback to start") { it.status == PlayerStatus.PLAYING && it.positionMs > 1_500 }
        shell.requestClose()
        shell.awaitExit()
        assertTrue((environment.library.watchState(episode)?.lastPlayedAtEpochMs ?: 0) > lastPlayed, "what was playing was recorded before the window went away")
    }

    /**
     * Whether the window is back where it was. Windows restores it exactly; an X11 window manager puts the
     * frame back itself and may place it a few pixels off.
     */
    private fun restored(now: java.awt.Rectangle, before: java.awt.Rectangle): Boolean =
        if (isWindows) now == before else now.size == before.size && now.location.distance(before.location) <= 48.0

    /** The number of clearly different colors in the middle of [image]: a flat surface has one. */
    private fun colors(image: BufferedImage): Int {
        val seen = HashSet<Int>()
        for (x in image.width / 4 until image.width * 3 / 4 step 16) {
            for (y in image.height / 4 until image.height * 3 / 4 step 16) seen += image.getRGB(x, y) and 0xF0F0F0
        }
        return seen.size
    }
}

/** Asks Windows which window the pointer would reach at a point, without moving the pointer there. */
private object WindowsHitTest {
    @Suppress("FunctionName")
    private interface User32 : StdCallLibrary {
        fun WindowFromPoint(point: Long): Pointer?
        fun GetAncestor(window: Pointer, flags: Int): Pointer?
        fun GetWindowRect(window: Pointer, rectangle: IntArray): Boolean
    }

    private val user32: User32 by lazy { Native.load("user32", User32::class.java) }
    private const val GA_ROOT = 2

    /** Whether pointer input over [window] (a third of the way down its middle) is delivered to it rather than to another window. */
    fun receivesPointer(window: java.awt.Window): Boolean {
        val handle = Native.getWindowPointer(window)
        val rectangle = IntArray(4)
        if (!user32.GetWindowRect(handle, rectangle)) return false
        val x = (rectangle[0] + rectangle[2]) / 2
        val y = rectangle[1] + (rectangle[3] - rectangle[1]) / 3
        // A POINT passed by value is its two 32-bit halves in one register.
        val hit = user32.WindowFromPoint((y.toLong() shl 32) or (x.toLong() and 0xFFFFFFFFL)) ?: return false
        return user32.GetAncestor(hit, GA_ROOT) == handle
    }
}
