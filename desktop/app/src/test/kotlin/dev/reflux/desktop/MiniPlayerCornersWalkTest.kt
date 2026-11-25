package dev.reflux.desktop

import dev.reflux.core.playback.PlayerStatus
import dev.reflux.desktop.engine.DesktopEnvironment
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.awt.event.KeyEvent
import java.io.File
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.test.Test

/**
 * The mini-player's rounded video on a real Linux desktop (CACHY-011, the owner's final CachyOS session, 2026-11-25), on request
 * only: `-Preflux.windowTests=true REFLUX_CORNERS_WALK=<dir>` on Hyprland. Plays a bright test card, then captures **Reflux's own
 * toplevel only** (`grim -T`, never the screen) in the full player, as fast as grim allows through the move into the dock, and in
 * the dock; and writes where the picture was laid out at each capture (`<n>.txt`: the window's pixels, the corner radius), for the
 * pixel check made afterwards (the video's own pixels outside the rounded corners, not a border drawn over them).
 */
class MiniPlayerCornersWalkTest {
    private val out: File? = System.getenv("REFLUX_CORNERS_WALK")?.let(::File)

    private fun sh(vararg command: String): String =
        ProcessBuilder(*command).redirectErrorStream(true).start().let { p -> p.inputStream.bufferedReader().readText().also { p.waitFor() } }

    @Test
    fun corners() {
        assumeTrue("corners walk not requested", windowTestsRequested && out != null)
        out!!.mkdirs()
        val root = Files.createTempDirectory("reflux-corners")
        val movie = root.resolve("Media").createDirectories().resolve("Test Card (2020)/Test.Card.2020.mkv")
        SampleMedia.video(movie, seconds = 120, size = "1920x1080", rate = 24)
        val environment = DesktopEnvironment(root.resolve("data"))
        environment.addLocalFolder(root.resolve("Media"))
        runBlocking { environment.pipeline.refresh() }
        val shell = Shell(environment)
        val pid = ProcessHandle.current().pid()
        var n = 0
        fun capture(step: String) {
            val id = sh("python3", "-I", "-c", "import json,sys\nfor c in json.loads(sys.argv[1]):\n  if c['pid']==int(sys.argv[2]) and c['title']=='Reflux': print(c['stableId']); break",
                sh("hyprctl", "clients", "-j"), "$pid").trim()
            if (id.isEmpty()) return
            val name = "%02d-%s".format(n++, step)
            val frame = shell.ui { shell.app.videoFrame }
            // The integrated picture says where it is drawn; the fallback's surface is where the dock (or the whole window) is.
            val placed = shell.integratedPicture ?: (frame as? dev.reflux.ui.state.VideoFrame.Docked)?.bounds
                ?: shell.ui { androidx.compose.ui.geometry.Rect(0f, 0f, shell.window.contentPane.width.toFloat(), shell.window.contentPane.height.toFloat()) }
            val scale = shell.ui { shell.window.graphicsConfiguration.defaultTransform.scaleX }
            val file = File(out, "$name.png").path
            sh("grim", "-T", id, file)
            if (!File(file).exists()) sh("grim", "-T", "0x$id", file)
            File(out, "$name.txt").writeText("placed=$placed frame=$frame scale=$scale window=${shell.ui { shell.window.width }}x${shell.ui { shell.window.height }}\n")
        }
        try {
            shell.awaitShown("Recently Added")
            shell.click("Test Card, 2020")
            shell.awaitShown("Version")
            shell.click("Play")
            shell.await("playback") { shell.app.playback?.state?.value?.let { it.status == PlayerStatus.PLAYING && it.positionMs > 1_000 } == true }
            runCatching { shell.await("the controls to fade", timeoutMs = 15_000) { !shell.shows("Timeline", exact = false) } }
            capture("full")
            shell.key(KeyEvent.VK_ESCAPE)
            val until = System.currentTimeMillis() + 1_200
            while (System.currentTimeMillis() < until) capture("docking")
            Thread.sleep(1_500)
            capture("docked")
            Thread.sleep(1_500)
            capture("docked-later")
            shell.clickSurface(0.5f, 0.5f)
            shell.await("the whole player again") { shell.app.playback?.presentation == dev.reflux.ui.state.PlayerPresentation.FULL }
            Thread.sleep(1_200)
            capture("full-again")
            runCatching { shell.app.playback?.close() }
        } finally {
            shell.close()
            root.toFile().deleteRecursively()
        }
    }
}
