package dev.reflux.desktop

import dev.reflux.core.playback.PlayerStatus
import dev.reflux.desktop.engine.DesktopEnvironment
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.test.Test

/**
 * An investigation walk on a Hyprland session (CACHY ledger): plays a real file in the real shell and, at each state,
 * records what the compositor knows of Reflux's windows (`hyprctl clients -j`) and what it actually shows (`grim -T`).
 * Only on request: `-Preflux.windowTests=true REFLUX_HYPRLAND_WALK=<output dir>`; never moves the system pointer. On a machine
 * someone is using, keep its windows off their screen (a runtime rule, e.g. `hyprctl eval 'hl.window_rule({ match = { class =
 * "^(reflux)$" }, workspace = "9 silent" })'`) and its sample tone out of their ears (`PULSE_SERVER=unix:/nonexistent`), or
 * run it in a nested Hyprland (`WAYLAND_DISPLAY`, `DISPLAY` and `HYPRLAND_INSTANCE_SIGNATURE` of that one, and
 * REFLUX_WALK_OUTPUT=<its output>, which adds the compositor's whole picture). docs/planning/CACHYOS-FINAL-AUDIT-LEDGER.md, CACHY-002.
 */
class HyprlandWalkTest {
    private val out: File? = System.getenv("REFLUX_HYPRLAND_WALK")?.let(::File)

    private fun sh(vararg command: String): String =
        ProcessBuilder(*command).redirectErrorStream(true).start().let { p -> p.inputStream.bufferedReader().readText().also { p.waitFor() } }

    private fun record(step: String) {
        val dir = out ?: return
        Thread.sleep(1_200)
        val pid = ProcessHandle.current().pid()
        val clients = sh("hyprctl", "clients", "-j")
        File(dir, "$step.clients.json").writeText(clients)
        // Inside a nested Hyprland (REFLUX_WALK_OUTPUT names its output) the compositor's whole picture is ours to capture.
        System.getenv("REFLUX_WALK_OUTPUT")?.let { sh("grim", "-o", it, File(dir, "$step.composite.png").path) }
        // On a desktop someone uses (REFLUX_WALK_REGION=1): what the compositor shows of Reflux's own window and nothing else, and
        // only while the monitor still shows the workspace it is on; otherwise nothing is captured (a skipped mark instead). A
        // window on a hidden workspace gets no frame callbacks, so its own buffer (below) can be stale.
        if (System.getenv("REFLUX_WALK_REGION") == "1") {
            val region = sh("python3", "-I", "-c", WINDOW_REGION, File(dir, "$step.clients.json").path, "$pid", sh("hyprctl", "monitors", "-j")).trim()
            if (region.isNotEmpty()) sh("grim", "-g", region, File(dir, "$step.region.png").path) else File(dir, "$step.region-skipped").writeText("not on a shown workspace")
        }
        // Every toplevel of this process, each on its own: the main window and, while playing, the controls window.
        val ids = sh("python3", "-I", "-c", "import json,sys\nfor c in json.load(open(sys.argv[1])):\n  if c['pid']==int(sys.argv[2]): print(c['stableId'])", File(dir, "$step.clients.json").path, "$pid")
        ids.lines().filter { it.isNotBlank() }.forEachIndexed { i, id ->
            val file = File(dir, "$step.toplevel$i.png").path
            // grim names a toplevel by Hyprland's stable id; which spelling it takes has varied, so both are tried.
            sh("grim", "-T", id.trim(), file)
            if (!File(file).exists()) sh("grim", "-T", "0x" + id.trim(), file)
        }
    }

    private companion object {
        /** The region (`x,y wxh`) of this process's player window, when the workspace it is on is the one its monitor shows. */
        const val WINDOW_REGION = """
import json,sys
clients=json.load(open(sys.argv[1])); pid=int(sys.argv[2]); monitors=json.loads(sys.argv[3])
shown={m['activeWorkspace']['id'] for m in monitors}
mine=[c for c in clients if c['pid']==pid and c['title']=='Reflux']
if mine and mine[0]['workspace']['id'] in shown:
    c=mine[0]; print(f"{c['at'][0]},{c['at'][1]} {c['size'][0]}x{c['size'][1]}")
"""
    }

    private fun window(dispatch: String) {
        val pid = ProcessHandle.current().pid()
        sh("hyprctl", "dispatch", dispatch.replace("PID", "pid:$pid"))
    }

    @Test
    fun walk() {
        assumeTrue("walk not requested", windowTestsRequested && out != null)
        out!!.mkdirs()
        val root: Path = Files.createTempDirectory("reflux-walk")
        val media = root.resolve("Media").createDirectories()
        val movie = media.resolve("Test Card (2020)/Test.Card.2020.mkv")
        SampleMedia.video(movie, seconds = 120, size = "1920x1080", rate = 24)
        // A dark scene, to see the controls' scrim over a picture with little light in it.
        val night = media.resolve("Night Scene (2020)/Night.Scene.2020.mkv").also { it.parent.createDirectories() }
        ProcessBuilder("ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi", "-i", "testsrc2=duration=60:size=1920x1080:rate=24,colorlevels=romax=0.12:gomax=0.12:bomax=0.12",
            "-f", "lavfi", "-i", "sine=frequency=220:duration=60", "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", "-c:a", "aac", night.toString())
            .inheritIO().start().waitFor()
        val environment = DesktopEnvironment(root.resolve("data"))
        environment.addLocalFolder(media)
        runBlocking { environment.pipeline.refresh() }
        val shell = Shell(environment)
        try {
            shell.awaitShown("Recently Added")
            // The workspace the player starts on, to come back to after the move.
            val home = sh("python3", "-I", "-c", "import json,sys\nfor c in json.loads(sys.argv[1]):\n  if c['pid']==int(sys.argv[2]): print(c['workspace']['id']); break",
                sh("hyprctl", "clients", "-j"), "${ProcessHandle.current().pid()}").trim()
            record("01-home")
            shell.click("Test Card, 2020")
            shell.awaitShown("Version")
            shell.click("Play")
            shell.await("playback") { shell.app.playback?.state?.value?.let { it.status == PlayerStatus.PLAYING && it.positionMs > 1_000 } == true }
            shell.hoverSurface(0.5f, 0.5f)
            record("02-playing-tiled")
            // Left alone, the controls fade and the picture is whole.
            runCatching { shell.await("the controls to fade", timeoutMs = 15_000) { !shell.shows("Timeline", exact = false) } }
            record("02b-controls-hidden")
            shell.hoverSurface(0.5f, 0.5f)
            window("hl.dsp.window.float({ action = \"toggle\", window = \"PID\" })")
            record("03-floating")
            window("hl.dsp.window.resize({ x = 1100, y = 700, window = \"PID\" })")
            shell.hoverSurface(0.5f, 0.5f)
            record("04-floating-resized")
            shell.key(java.awt.event.KeyEvent.VK_SPACE)
            record("05-paused")
            shell.key(java.awt.event.KeyEvent.VK_RIGHT)
            record("06-seeked")
            window("hl.dsp.window.float({ action = \"toggle\", window = \"PID\" })")
            shell.key(java.awt.event.KeyEvent.VK_F)
            record("07-fullscreen")
            shell.key(java.awt.event.KeyEvent.VK_F)
            record("08-windowed-again")
            window("hl.dsp.window.move({ workspace = \"8\", follow = false, window = \"PID\" })")
            record("09-other-workspace")
            window("hl.dsp.window.move({ workspace = \"$home\", follow = false, window = \"PID\" })")
            record("09b-back-on-its-workspace")
            shell.key(java.awt.event.KeyEvent.VK_ESCAPE)
            record("10-mini-player")
            // Later frames in the dock, to see the picture kept inside its rounded corners as it plays.
            Thread.sleep(1_500)
            record("10b-mini-player-later")
            runCatching { shell.app.playback?.close() }
            shell.await("the test card closed") { shell.app.playback == null }
            shell.click("Home")
            shell.awaitShown("Recently Added")
            shell.click("Night Scene, 2020")
            shell.awaitShown("Version")
            shell.click("Play")
            shell.await("the dark scene playing") { shell.app.playback?.state?.value?.let { it.status == PlayerStatus.PLAYING && it.positionMs > 1_000 } == true }
            shell.hoverSurface(0.5f, 0.5f)
            record("11-dark-controls")
            shell.click("Playback speed", exact = false)
            record("12-menu-open")
            shell.key(java.awt.event.KeyEvent.VK_ESCAPE)
        } finally {
            shell.close()
            // The diagnostics log says which renderer drew the picture (linux-mpv-gl-integrated or linux-x11-shape-fallback).
            runCatching { environment.diagnostics.file.toFile().copyTo(File(out, "diagnostics.log"), overwrite = true) }
            root.toFile().deleteRecursively()
        }
    }
}
