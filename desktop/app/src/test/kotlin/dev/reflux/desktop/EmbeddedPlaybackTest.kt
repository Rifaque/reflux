package dev.reflux.desktop

import dev.reflux.core.input.Key
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.desktop.engine.DesktopEnvironment
import dev.reflux.playback.mpv.MpvPlayer
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.awt.Component
import java.awt.Container
import java.awt.GraphicsEnvironment
import java.awt.Robot
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlin.io.path.createDirectories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs the real desktop shell and plays a real file in its window: libmpv renders into the native video
 * surface and Reflux's controls are layered above it.
 *
 * It opens a window, so it only runs on request: `./gradlew :desktop:app:test -Preflux.windowTests=true`
 * on a machine with a display, libmpv, and FFmpeg. `-Preflux.screenshots=<dir>` also saves what was shown.
 */
class EmbeddedPlaybackTest {
    private val screenshots: File? = System.getProperty("reflux.screenshots")?.takeIf { it.isNotBlank() }?.let(::File)

    @Test
    fun playsInsideTheWindowWithControlsAboveTheVideo() {
        assumeTrue("window tests not requested", System.getProperty("reflux.windowTests") == "true")
        assumeTrue("no display", !GraphicsEnvironment.isHeadless())
        assumeTrue("libmpv not installed", MpvPlayer.isAvailable)
        assumeTrue("ffmpeg not installed", ffmpegAvailable())

        val directory = Files.createTempDirectory("reflux-desktop")
        val media = directory.resolve("Media").createDirectories()
        sampleMovie(media.resolve("Night Drive (2021)").createDirectories().resolve("Night.Drive.2021.720p.mkv"))
        val environment = DesktopEnvironment(directory.resolve("data"))
        environment.addLocalFolder(media)
        runBlocking { environment.pipeline.refresh() }
        val movie = environment.library.movies().single().item.id

        val started = CompletableFuture<RunningReflux>()
        val shell = thread(name = "reflux-shell") { runReflux(environment, exitProcess = false) { started.complete(it) } }
        val running = started.get(60, TimeUnit.SECONDS)
        val app = running.app
        try {
            ui { running.window.isAlwaysOnTop = true }
            val windowed = running.window.bounds
            await("the library to appear") { app.revision > 0 }
            Thread.sleep(1_500)
            capture(running, "window-home")

            ui { app.play(movie) }
            await("playback to start") { app.playback?.state?.value?.let { it.status == PlayerStatus.PLAYING && it.positionMs > 1_000 } == true }
            val session = assertNotNull(app.playback)
            assertEquals("Night Drive", session.title)
            assertEquals(listOf("eng", "jpn"), session.state.value.audioTracks.map { it.language })

            // The controls live in a transparent window that covers the video surface exactly and never takes focus.
            val surface = assertNotNull(find(running.window) { it.name == VideoSurface.NAME && it.isShowing })
            val overlay = running.window.ownedWindows.single { it.isShowing }
            assertEquals(surface.locationOnScreen, overlay.locationOnScreen)
            assertEquals(surface.size, overlay.size)
            assertFalse(overlay.focusableWindowState)
            val playing = capture(running, "window-playing")
            assertTrue(colors(playing) > 8, "the video is visible inside the window")

            // Keys reach the player through the one semantic input path, whatever has focus.
            ui { app.onKey(Key.SPACE) }
            await("pause") { session.state.value.status == PlayerStatus.PAUSED }
            ui { app.onKey(Key.ARROW_RIGHT) }
            await("seek") { session.state.value.positionMs > 10_000 }
            capture(running, "window-paused")

            // Fullscreen keeps the same surface and engine.
            ui { app.onKey(Key.F) }
            await("fullscreen", state = {
                "window=${running.window.bounds} insets=${running.window.insets} screen=${running.window.graphicsConfiguration.bounds} " +
                    "surface=${surface.size} overlay=${overlay.bounds}"
            }) {
                // No frame, the whole screen, and the same surface still under the overlay.
                app.platform.fullscreen && running.window.bounds == running.window.graphicsConfiguration.bounds &&
                    running.window.insets.top == 0 && surface.width >= running.window.width && overlay.size == surface.size
            }
            ui { app.onKey(Key.SPACE) }
            val resumedAt = session.state.value.positionMs
            await("playback to continue in fullscreen") { session.state.value.positionMs > resumedAt + 800 }
            capture(running, "window-fullscreen")

            ui { app.onKey(Key.ESCAPE) }
            await("the player to close") { app.playback == null && find(running.window) { it.name == VideoSurface.NAME } == null }
            assertFalse(app.platform.fullscreen, "leaving the player restores the window")
            await("the window to be restored") { running.window.bounds == windowed }
            capture(running, "window-after")
            // Positions in the first minute are not worth resuming, so only the visit itself is recorded.
            await("the visit to be recorded") { environment.library.watchState(movie)?.durationMs in 39_000L..41_000L }
            assertNull(app.notice)
        } finally {
            ui { running.exit() }
            shell.join(15_000)
            directory.toFile().deleteRecursively()
        }
    }

    private fun ui(action: () -> Unit) = SwingUtilities.invokeAndWait(action)

    private fun await(what: String, timeoutMs: Long = 20_000, state: () -> String = { "" }, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            var met = false
            ui { met = condition() }
            if (met) return
            Thread.sleep(100)
        }
        var seen = ""
        ui { seen = state() }
        error("Timed out waiting for $what. $seen")
    }

    private fun find(root: Component, matches: (Component) -> Boolean): Component? {
        if (matches(root)) return root
        return (root as? Container)?.components?.firstNotNullOfOrNull { find(it, matches) }
    }

    private fun capture(running: RunningReflux, name: String): BufferedImage {
        Thread.sleep(600) // let the compositor present the frame
        val image = Robot().createScreenCapture(running.window.bounds)
        screenshots?.let {
            it.mkdirs()
            ImageIO.write(image, "png", File(it, "$name.png"))
        }
        return image
    }

    /** The number of clearly different colors in the middle of [image]: a flat surface has one. */
    private fun colors(image: BufferedImage): Int {
        val seen = HashSet<Int>()
        for (x in image.width / 4 until image.width * 3 / 4 step 16) {
            for (y in image.height / 4 until image.height * 3 / 4 step 16) seen += image.getRGB(x, y) and 0xF0F0F0
        }
        return seen.size
    }

    private fun ffmpegAvailable(): Boolean =
        runCatching { ProcessBuilder("ffmpeg", "-version").start().waitFor(10, TimeUnit.SECONDS) }.getOrDefault(false)

    /** 40 s of moving 720p test video with English and Japanese audio, an English subtitle, and chapters. */
    private fun sampleMovie(output: Path) {
        val directory = output.parent
        val subtitle = directory.resolve("embedded.srt")
        Files.writeString(subtitle, "1\n00:00:01,000 --> 00:00:30,000\nReflux subtitle rendering\n")
        val chapters = directory.resolve("chapters.txt")
        Files.writeString(
            chapters,
            ";FFMETADATA1\n[CHAPTER]\nTIMEBASE=1/1000\nSTART=0\nEND=8000\ntitle=Intro\n" +
                "[CHAPTER]\nTIMEBASE=1/1000\nSTART=8000\nEND=40000\ntitle=Chapter 1\n",
        )
        val process = ProcessBuilder(
            "ffmpeg", "-y", "-loglevel", "error",
            "-f", "lavfi", "-i", "testsrc2=duration=40:size=1280x720:rate=30",
            "-f", "lavfi", "-i", "sine=frequency=330:duration=40",
            "-f", "lavfi", "-i", "sine=frequency=550:duration=40",
            "-i", subtitle.toString(),
            "-i", chapters.toString(),
            "-map", "0:v", "-map", "1:a", "-map", "2:a", "-map", "3:s", "-map_chapters", "4",
            "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", "-c:a", "aac", "-c:s", "srt",
            "-metadata:s:a:0", "language=eng", "-metadata:s:a:1", "language=jpn", "-metadata:s:s:0", "language=eng",
            output.toString(),
        ).redirectErrorStream(true).start()
        val log = process.inputStream.bufferedReader().readText()
        check(process.waitFor(120, TimeUnit.SECONDS) && process.exitValue() == 0) { "ffmpeg failed: $log" }
        Files.delete(subtitle)
        Files.delete(chapters)
    }
}
