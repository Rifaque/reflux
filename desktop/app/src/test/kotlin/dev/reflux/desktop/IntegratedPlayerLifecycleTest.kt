package dev.reflux.desktop

import dev.reflux.desktop.engine.DesktopEnvironment
import dev.reflux.core.playback.PlayerStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.awt.event.KeyEvent
import java.io.File
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-084, opt-in (Linux, a real display; `REFLUX_LIFECYCLE_OUT` a directory): the integrated picture's lifecycle through the
 * real shell, [ROUNDS] playbacks of one file, each paused, resumed, sought, taken to fullscreen and back and closed; one of
 * them closed while the window is on a workspace no monitor shows (`REFLUX_LIFECYCLE_HIDDEN_WORKSPACE`, Hyprland), where no
 * draw comes and the release has to make one. Every render context made is released once, with OpenGL current, nothing fails
 * and the process's memory levels off. Runs with the toolkit's own vsync, as Reflux does.
 */
class IntegratedPlayerLifecycleTest {
    private val out: File? = System.getenv("REFLUX_LIFECYCLE_OUT")?.let(::File)
    private val hidden: String? = System.getenv("REFLUX_LIFECYCLE_HIDDEN_WORKSPACE")

    private fun rssMb(): Long = File("/proc/self/status").readLines().first { it.startsWith("VmRSS:") }.split(Regex("\\s+"))[1].toLong() / 1024

    /** Resident memory by what is mapped: GPU buffers (/dev/dri), memfd, malloc's (anonymous and [heap]), libraries, other. */
    private fun kindsMb(): Map<String, Long> {
        val totals = linkedMapOf("dri" to 0L, "memfd" to 0L, "anon" to 0L, "lib" to 0L, "other" to 0L)
        var kind = "other"
        File("/proc/self/smaps").forEachLine { line ->
            val head = Regex("^[0-9a-f]+-[0-9a-f]+ \\S+ \\S+ \\S+ \\S+\\s*(.*)$").find(line)
            if (head != null) {
                val name = head.groupValues[1]
                kind = when {
                    name.startsWith("/dev/dri") -> "dri"
                    name.startsWith("/memfd") -> "memfd"
                    name.isEmpty() || name == "[heap]" -> "anon"
                    name.endsWith(".so") || ".so." in name -> "lib"
                    else -> "other"
                }
            } else if (line.startsWith("Rss:")) totals[kind] = totals[kind]!! + line.split(Regex("\\s+"))[1].toLong()
        }
        return totals.mapValues { it.value / 1024 }
    }

    private fun hyprctl(command: String) {
        val pid = ProcessHandle.current().pid()
        ProcessBuilder("hyprctl", "dispatch", command.replace("PID", "pid:$pid")).redirectErrorStream(true).start().waitFor()
    }

    @Test
    fun playsAgainAndAgainAndReleasesEveryTime() {
        assumeTrue("lifecycle walk not requested", windowTestsRequested && out != null && System.getProperty("os.name").startsWith("Linux"))
        out!!.mkdirs()
        val root = Files.createTempDirectory("reflux-lifecycle")
        val movie = root.resolve("Media").createDirectories().resolve("Test Card (2020)/Test.Card.2020.mkv")
        SampleMedia.video(movie, seconds = 60, size = "1920x1080", rate = 24)
        val environment = DesktopEnvironment(root.resolve("data"))
        environment.addLocalFolder(root.resolve("Media"))
        runBlocking { environment.pipeline.refresh() }
        val shell = Shell(environment)
        val memory = mutableListOf<Long>()
        val heap = mutableListOf<Long>()
        val kinds = mutableListOf<Map<String, Long>>()
        val log = File(out, "diagnostics.log")
        try {
            shell.awaitShown("Recently Added")
            val home = currentWorkspace()
            repeat(ROUNDS) { round ->
                shell.click("Home")
                shell.awaitShown("Recently Added")
                shell.click("Test Card, 2020")
                shell.awaitShown("Version")
                shell.click("Play")
                shell.await("round $round playing") { shell.app.playback?.state?.value?.let { it.status == PlayerStatus.PLAYING && it.positionMs > 1_000 } == true }
                shell.key(KeyEvent.VK_SPACE)
                shell.await("round $round paused") { shell.app.playback?.state?.value?.status == PlayerStatus.PAUSED }
                shell.key(KeyEvent.VK_SPACE)
                shell.await("round $round resumed") { shell.app.playback?.state?.value?.status == PlayerStatus.PLAYING }
                shell.key(KeyEvent.VK_RIGHT)
                if (System.getenv("REFLUX_LIFECYCLE_NO_FULLSCREEN") == null) {
                    shell.key(KeyEvent.VK_F)
                    Thread.sleep(800)
                    shell.key(KeyEvent.VK_F)
                }
                Thread.sleep(800)
                if (round == ROUNDS / 2 && hidden != null && home != null) {
                    // Closed where nothing is drawn: the release has to bring a draw about itself.
                    hyprctl("hl.dsp.window.move({ workspace = \"$hidden\", follow = false, window = \"PID\" })")
                    Thread.sleep(1_000)
                    runCatching { shell.app.playback?.close() }
                    shell.await("round $round closed while hidden") { shell.app.playback == null }
                    hyprctl("hl.dsp.window.move({ workspace = \"$home\", follow = false, window = \"PID\" })")
                } else {
                    runCatching { shell.app.playback?.close() }
                    shell.await("round $round closed") { shell.app.playback == null }
                }
                Thread.sleep(1_000)
                System.gc()
                memory += rssMb()
                kinds += kindsMb()
                heap += (Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() } / (1024 * 1024))
            }
        } finally {
            shell.close()
            runCatching { environment.diagnostics.file.toFile().copyTo(log, overwrite = true) }
            root.toFile().deleteRecursively()
        }
        val lines = log.readLines()
        val made = lines.count { "render context made" in it }
        val released = lines.count { "${LinuxGlVideo.RENDERER}: released" in it }
        File(out, "report.txt").writeText("rounds $ROUNDS made $made released $released forced ${lines.count { "forced draw" in it }} rss after each: $memory heap after each: $heap\n" + kinds.joinToString("\n") { it.toString() } + "\n")
        assertEquals(ROUNDS, lines.count { "Video renderer: ${LinuxGlVideo.RENDERER}" in it }, "every playback drew in Reflux's scene")
        assertEquals(made, released, "every render context made was released")
        assertTrue(lines.none { "${LinuxGlVideo.RENDERER} failed" in it || "without OpenGL current" in it }, "nothing failed or was freed off its context")
        // After the first rounds have warmed the engine and the driver, more rounds do not keep taking memory.
        val settled = memory.drop(2)
        assertTrue(settled.max() - settled.min() < 150, "memory levels off: $memory MB")
    }

    private fun currentWorkspace(): String? = runCatching {
        val clients = ProcessBuilder("hyprctl", "clients", "-j").start().inputStream.bufferedReader().readText()
        ProcessBuilder("python3", "-I", "-c", "import json,sys\nfor c in json.loads(sys.argv[1]):\n  if c['pid']==int(sys.argv[2]): print(c['workspace']['id']); break",
            clients, "${ProcessHandle.current().pid()}").start().inputStream.bufferedReader().readText().trim().ifEmpty { null }
    }.getOrNull()

    private companion object {
        val ROUNDS = System.getenv("REFLUX_LIFECYCLE_ROUNDS")?.toIntOrNull() ?: 8
    }
}
