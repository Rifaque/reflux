package dev.reflux.desktop

import dev.reflux.desktop.engine.DesktopEnvironment
import dev.reflux.playback.mpv.MpvPlayer
import dev.reflux.ui.state.PlaybackSession
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.test.Test

/**
 * B-084, opt-in: plays each file of `REFLUX_PERF_MEDIA` through the real shell for [SECONDS] seconds and writes what decoded and
 * drew it, and what it cost, to `REFLUX_PERF_OUT` (a directory): the engine's own counters (`hwdec-current`, `current-vo`,
 * dropped frames, A/V sync), this process's CPU (the engine runs in it), its resident memory, the NVIDIA GPU's use as nvidia-smi
 * reports it and the Intel GPU's busy share from its RC6 residency. Both GPUs' figures include the rest of the desktop; an idle
 * sample is taken first to compare with. Run it once as it is (the integrated picture) and once with
 * `REFLUX_NATIVE_VIDEO_WINDOW=1` (the native window), on the same workspace. Not a pass/fail test: a measurement.
 */
class IntegratedPlayerPerfTest {
    private val media: File? = System.getenv("REFLUX_PERF_MEDIA")?.let(::File)
    private val out: File? = System.getenv("REFLUX_PERF_OUT")?.let(::File)
    private val os = ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean

    private class Sample(val cpuNs: Long, val wallNs: Long, val rc6Ms: Long?)

    private fun sample() = Sample(os.processCpuTime, System.nanoTime(), rc6())

    private fun rc6(): Long? = runCatching { File("/sys/class/drm/card1/gt/gt0/rc6_residency_ms").readText().trim().toLong() }.getOrNull()

    private fun rssMb(): Long = File("/proc/self/status").readLines().first { it.startsWith("VmRSS:") }.split(Regex("\\s+"))[1].toLong() / 1024

    /** nvidia-smi sampled once a second while [block] runs: mean GPU %, decoder %, power W. */
    private fun <T> withNvidia(block: () -> T): Pair<T, String> {
        val process = runCatching {
            ProcessBuilder("nvidia-smi", "--query-gpu=utilization.gpu,utilization.decoder,power.draw", "--format=csv,noheader,nounits", "-lms", "1000")
                .redirectErrorStream(true).start()
        }.getOrNull()
        val lines = mutableListOf<String>()
        val reader = process?.let { p -> Thread { p.inputStream.bufferedReader().forEachLine { synchronized(lines) { lines += it } } }.apply { start() } }
        val result = block()
        process?.destroy()
        reader?.join(2_000)
        val rows = synchronized(lines) { lines.mapNotNull { l -> l.split(",").map { it.trim().toDoubleOrNull() }.takeIf { it.size == 3 && it.all { v -> v != null } } } }
        val summary = if (rows.isEmpty()) "nvidia: -" else "nvidia gpu ${"%.0f".format(rows.map { it[0]!! }.average())}% dec ${"%.0f".format(rows.map { it[1]!! }.average())}% ${"%.1f".format(rows.map { it[2]!! }.average())} W"
        return result to summary
    }

    private fun cost(a: Sample, b: Sample): String {
        val wall = (b.wallNs - a.wallNs).toDouble()
        val cpu = (b.cpuNs - a.cpuNs) / wall * 100
        val intel = if (a.rc6Ms != null && b.rc6Ms != null) "intel busy ${"%.0f".format(100 - (b.rc6Ms - a.rc6Ms) / (wall / 1e6) * 100)}%" else "intel: -"
        return "cpu ${"%.0f".format(cpu)}% of one core; $intel"
    }

    private fun engine(session: PlaybackSession): MpvPlayer? =
        runCatching { PlaybackSession::class.java.getDeclaredField("player").apply { isAccessible = true }.get(session) as? MpvPlayer }.getOrNull()

    @Test
    fun measure() {
        assumeTrue("measurement not requested", windowTestsRequested && media != null && out != null)
        out!!.mkdirs()
        val files = media!!.listFiles { f -> f.extension == "mkv" }!!.sortedBy { it.name }
        val root = Files.createTempDirectory("reflux-perf")
        val library = root.resolve("Media").createDirectories()
        // Plain titles: the library reads codec and size words in a file's name as such, not as its title.
        val names = listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel")
        val titles = files.mapIndexed { i, f ->
            val title = names[i]
            val dir = library.resolve("$title (2020)").createDirectories()
            Files.createSymbolicLink(dir.resolve("$title (2020).mkv"), f.toPath())
            title to f.name
        }
        val environment = DesktopEnvironment(root.resolve("data"))
        environment.addLocalFolder(library)
        runBlocking { environment.pipeline.refresh() }
        val report = StringBuilder("renderer requested: ${if (System.getenv("REFLUX_NATIVE_VIDEO_WINDOW") != null) LinuxGlVideo.FALLBACK else LinuxGlVideo.RENDERER}\n")
        val shell = Shell(environment)
        try {
            shell.awaitShown("Recently Added")
            Thread.sleep(3_000)
            val (idle, idleGpu) = withNvidia { sample().let { a -> Thread.sleep(SECONDS * 1_000L); cost(a, sample()) } }
            report.append("idle (library shown): $idle; $idleGpu; rss ${rssMb()} MB\n")
            for ((title, file) in titles) {
                shell.click("Home")
                shell.awaitShown("Recently Added")
                shell.click("$title, 2020")
                shell.awaitShown("Version")
                shell.click("Play")
                shell.await("playback of $title") { shell.app.playback?.state?.value?.let { it.positionMs > 1_000 } == true }
                Thread.sleep(3_000)
                val state = shell.app.playback?.state?.value
                report.append("  state before sampling: ${state?.status} at ${state?.positionMs} ms, failure ${state?.failure}\n")
                val mpv = shell.app.playback?.let(::engine)
                if (mpv == null) report.append("  the session's engine could not be reached\n")
                val p = { name: String -> mpv?.property(name) ?: "-" }
                val drops0 = p("frame-drop-count").toLongOrNull() ?: 0
                val dec0 = p("decoder-frame-drop-count").toLongOrNull() ?: 0
                val rss0 = rssMb()
                val (cost, gpu) = withNvidia { sample().let { a -> Thread.sleep(SECONDS * 1_000L); cost(a, sample()) } }
                report.append(
                    "$file: vo=${p("current-vo")} hwdec-current=${p("hwdec-current")} codec=${p("video-format")} " +
                        "${p("video-params/w")}x${p("video-params/h")} dec-format=${p("video-dec-params/pixelformat")} " +
                        "hw-format=${p("video-dec-params/hw-pixelformat")} gamma=${p("video-params/gamma")} primaries=${p("video-params/primaries")}; " +
                        "fps container ${p("container-fps")} shown ${p("estimated-display-fps")}; " +
                        "dropped(vo) +${(p("frame-drop-count").toLongOrNull() ?: 0) - drops0} dropped(decoder) +${(p("decoder-frame-drop-count").toLongOrNull() ?: 0) - dec0} " +
                        "mistimed ${p("mistimed-frame-count")} delayed ${p("vo-delayed-frame-count")} avsync ${p("avsync")}; " +
                        "$cost; $gpu; rss $rss0 -> ${rssMb()} MB\n",
                )
                runCatching { shell.app.playback?.close() }
                shell.await("playback closed") { shell.app.playback == null }
            }
            report.append("after all: rss ${rssMb()} MB\n")
        } finally {
            shell.close()
            runCatching { environment.diagnostics.file.toFile().copyTo(File(out, "diagnostics.log"), overwrite = true) }
            File(out, "report.txt").writeText(report.toString())
            root.toFile().deleteRecursively()
        }
    }

    private companion object {
        const val SECONDS = 20
    }
}
