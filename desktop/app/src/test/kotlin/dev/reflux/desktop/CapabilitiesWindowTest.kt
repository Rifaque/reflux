package dev.reflux.desktop

import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.versions.SelectionCriterion
import dev.reflux.desktop.engine.DesktopEnvironment
import dev.reflux.desktop.engine.DisplayReader
import dev.reflux.playback.mpv.DecodeKey
import dev.reflux.playback.mpv.DecodeObservation
import dev.reflux.playback.mpv.HardwareDecode
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.awt.GraphicsEnvironment
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What the real desktop says it can show and decode, in its real window: the display is the one the window
 * is on, read again when the window moves; the engine's decoders are what the probe and a real playback
 * showed on this machine; and a film's page says what that means for its two copies, as it changes.
 *
 * It opens a window, so it only runs on request (`-Preflux.windowTests=true`), with libmpv and FFmpeg.
 * What the hardware answers depends on the machine; where the test relies on an answer it says so.
 */
class CapabilitiesWindowTest {
    private val root: Path = Files.createTempDirectory("reflux-capabilities")
    private val media: Path = root.resolve("Media").createDirectories()

    /** One film in two copies of the same picture: H.264 at 10 bits (the larger file) and HEVC at 10 bits. */
    private fun encode(output: Path, codec: String, bitrate: String) {
        Files.createDirectories(output.parent)
        val process = ProcessBuilder(
            "ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi", "-i", "testsrc2=duration=20:size=1920x1080:rate=24",
            "-c:v", codec, "-preset", "ultrafast", "-b:v", bitrate, "-pix_fmt", "yuv420p10le", output.toString(),
        ).redirectErrorStream(true).start()
        val log = process.inputStream.bufferedReader().readText()
        check(process.waitFor(180, TimeUnit.SECONDS) && process.exitValue() == 0) { "ffmpeg failed: $log" }
    }

    @Test
    fun capabilitiesAreWhatThisMachineShowsAndTheFilmsPageFollowsThem() {
        assumeTrue("window tests not requested", windowTestsRequested)
        assumeTrue("libmpv and ffmpeg are needed", needs("libmpv and ffmpeg", SampleMedia.available))
        encode(media.resolve("Night Drive (2021)/Night.Drive.2021.1080p.H264.10bit.mkv"), "libx264", "6M")
        encode(media.resolve("Night Drive (2021)/Night.Drive.2021.1080p.HEVC.10bit.mkv"), "libx265", "2M")

        val environment = DesktopEnvironment(root.resolve("data"))
        environment.addLocalFolder(media)
        runBlocking { environment.pipeline.refresh() }
        val film = environment.library.movies().single().item.id
        assertEquals(2, environment.library.versions(film).size, "one film, two copies")

        val shell = Shell(environment)
        try {
            shell.awaitShown("Recently Added")

            // The display: the one the window is on, read for the window, not the primary one at start.
            val expected = assertNotNull(DisplayReader.forThisSystem().read(shell.window), "this machine's display can be read")
            shell.await("the window's display to be read", state = { "${environment.capabilities.currentDisplay} vs $expected" }) {
                environment.capabilities.currentDisplay == expected
            }
            assertTrue(expected.width != null && expected.width!! > 0)
            assertEquals(expected.width, shell.ui { shell.app.platform.device }.display.maxWidth, "what Best Version is told")
            println("Display for the window: $expected; screens: ${GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.size}")

            // Moving the window reads its display again, off the interface's thread; on the same screen nothing changes.
            val before = environment.capabilities.current()
            shell.ui { shell.window.setLocation(shell.window.x + 40, shell.window.y + 30) }
            Thread.sleep(800)
            assertEquals(before, environment.capabilities.current(), "the same display, the same capabilities")
            movesToAnotherScreen(shell, environment)

            // Decoding: the probe runs once, a few seconds after start.
            shell.await("the decode probe to finish", timeoutMs = 30_000) { environment.capabilities.currentDecode.hardware.isNotEmpty() }
            val probed = environment.capabilities.currentDecode
            println("Probed: ${probed.summary()}")
            val avc = probed.hardware[DecodeKey(VideoCodec.H264, 10)]
            val hevc = probed.hardware[DecodeKey(VideoCodec.HEVC, 10)]

            shell.click("Night Drive, 2021")
            shell.awaitShown("Versions")
            val selection = shell.ui { environment.library.selectVersion(film, shell.app.platform.device) }
            if (avc == HardwareDecode.No && hevc is HardwareDecode.Yes) {
                // This machine (2026-10-19: Intel graphics through Direct3D 11) decodes HEVC 10-bit in hardware and H.264 10-bit not.
                assertTrue(selection.best!!.version.location.path.contains("HEVC"), "the copy the hardware decodes is recommended")
                assertEquals(SelectionCriterion.FEWER_COMPROMISES, selection.decidedBy)
                shell.awaitShown("Decoded without hardware acceleration", exact = false)
                shell.capture("capabilities-probed")

                // Something new is learned while the page is open: the page follows, without being reopened.
                environment.capabilities.decoded(DecodeObservation(VideoCodec.HEVC, 10, 1920, 1080, hardware = false))
                shell.await("the page to follow") {
                    val now = environment.library.selectVersion(film, shell.app.platform.device)
                    now.decidedBy != SelectionCriterion.FEWER_COMPROMISES && now.best!!.version.location.path.contains("H264")
                }

                // A real playback is the better witness: playing the HEVC copy shows the hardware at work again.
                val hevcVersion = environment.library.versions(film).single { it.version.location.path.contains("HEVC") }.version.id
                shell.ui { shell.app.play(film, hevcVersion) }
                shell.await("the HEVC copy to play", state = { "${shell.app.playback?.state?.value}" }) {
                    shell.app.playback?.state?.value?.let { it.status == PlayerStatus.PLAYING && it.positionMs > 1_000 } == true
                }
                shell.await("the playback's decoding to be recorded") { environment.capabilities.currentDecode.hardware[DecodeKey(VideoCodec.HEVC, 10)] is HardwareDecode.Yes }
                println("After playing the HEVC copy: ${environment.capabilities.currentDecode.summary()}")
                shell.key(java.awt.event.KeyEvent.VK_ESCAPE)
                shell.await("the player to close") { shell.app.playback == null }
            } else {
                println("This machine answered H.264 10-bit $avc and HEVC 10-bit $hevc: the page's comparison was not checked here")
            }
        } finally {
            shell.close()
            root.toFile().deleteRecursively()
        }
    }

    /** Where there is a second screen, the window goes there and its display is read as that one's. */
    private fun movesToAnotherScreen(shell: Shell, environment: DesktopEnvironment) {
        val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
        if (screens.size < 2) {
            println("Only one screen: moving between screens was not tested here")
            return
        }
        val current = shell.ui { shell.window.graphicsConfiguration.device }
        val other = screens.first { it != current }
        val home = shell.ui { shell.window.location }
        shell.ui { other.defaultConfiguration.bounds.let { shell.window.setLocation(it.x + 50, it.y + 50) } }
        shell.await("the other screen's display to be read") {
            environment.capabilities.currentDisplay.width == other.displayMode.width && environment.capabilities.currentDisplay.height == other.displayMode.height
        }
        shell.ui { shell.window.location = home }
    }
}
