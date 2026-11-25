package dev.reflux.desktop

import dev.reflux.core.playback.PlayerStatus
import dev.reflux.desktop.engine.DesktopEnvironment
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-084, the context-replacement edge (CachyOS final session, 2026-11-25): Skiko replaces its OpenGL context while a film
 * plays (a new redrawer, as Skiko makes when drawing fails: `RedrawerManager.findNextWorkingRenderApi`, asked here for the
 * same API again). The old context is destroyed first; the render context and the texture made in it are let go with no
 * context current, made again in the new one, and the picture comes back: libmpv renders frames again in the new context,
 * playback never stops, and nothing is logged as failed. Linux with Skiko on OpenGL only (the integrated picture); skipped
 * on the fallback (hosted CI).
 */
class IntegratedContextReplacementTest {
    @Test
    fun thePictureSurvivesANewOpenGLContext() {
        assumeTrue("window tests not requested", windowTestsRequested && System.getProperty("os.name").startsWith("Linux"))
        val root = Files.createTempDirectory("reflux-replace")
        val movie = root.resolve("Media").createDirectories().resolve("Test Card (2020)/Test.Card.2020.mkv")
        SampleMedia.video(movie, seconds = 60, size = "1280x720", rate = 24)
        val environment = DesktopEnvironment(root.resolve("data"))
        environment.addLocalFolder(root.resolve("Media"))
        runBlocking { environment.pipeline.refresh() }
        val shell = Shell(environment)
        try {
            shell.awaitShown("Recently Added")
            shell.click("Test Card, 2020")
            shell.awaitShown("Version")
            shell.click("Play")
            shell.await("playing") { shell.app.playback?.state?.value?.let { it.status == PlayerStatus.PLAYING && it.positionMs > 1_500 } == true }
            assumeTrue("the integrated picture is not in use here (no OpenGL): nothing to replace", shell.integratedFrames != null)
            shell.await("frames rendered before") { (shell.integratedFrames ?: 0) > 24 }
            val before = shell.ui { SkikoGl.directContext(shell.window) }
            // Skiko's own recovery path: the same API again, through a new redrawer and a new OpenGL context.
            shell.ui { replaceRedrawer(shell.window) }
            shell.await("a new OpenGL context") { SkikoGl.directContext(shell.window).let { it != null && it !== before } }
            shell.await("the picture made again in the new context", timeoutMs = 10_000) { "selected again" in log(environment) }
            val after = shell.integratedFrames ?: 0
            shell.await("frames rendered in the new context", timeoutMs = 10_000) { (shell.integratedFrames ?: 0) > after + 24 }
            val position = shell.app.playback!!.state.value.positionMs
            shell.await("playback goes on") { shell.app.playback?.state?.value?.let { it.status == PlayerStatus.PLAYING && it.positionMs > position + 1_000 } == true }
            runCatching { shell.app.playback?.close() }
            shell.await("closed") { shell.app.playback == null }
        } finally {
            shell.close()
        }
        val lines = log(environment).lines()
        root.toFile().deleteRecursively()
        assertTrue(lines.none { "${LinuxGlVideo.RENDERER} failed" in it }, "nothing failed:\n" + lines.filter { LinuxGlVideo.RENDERER in it }.joinToString("\n"))
        assertEquals(2, lines.count { "render context made" in it }, "made once per context")
        assertEquals(1, lines.count { "${LinuxGlVideo.RENDERER}: released" in it }, "released once, at the end")
    }

    /** `SkiaLayer.redrawerManager.findNextWorkingRenderApi(true)`: Skiko 0.150.1's own way to a new redrawer of the same API. */
    private fun replaceRedrawer(window: java.awt.Component) {
        val layer = SkikoGl.layer(window) ?: error("no Skia layer")
        val field = generateSequence<Class<*>>(layer.javaClass) { it.superclass }.firstNotNullOf { type -> type.declaredFields.firstOrNull { it.name == "redrawerManager" } }
        field.isAccessible = true
        val manager = field.get(layer)
        manager.javaClass.getMethod("findNextWorkingRenderApi", Boolean::class.javaPrimitiveType).invoke(manager, true)
    }

    private fun log(environment: DesktopEnvironment): String = runCatching { environment.diagnostics.file.toFile().readText() }.getOrDefault("")
}
