package dev.reflux.desktop

import dev.reflux.core.model.MediaId
import dev.reflux.desktop.engine.DesktopEnvironment
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

/**
 * Times Quick View in the real shell on a copy of a real library (CACHY ledger, BUG B): from asking for it to its
 * pane being shown, and how often the library said it changed meanwhile. Only on request:
 * `-Preflux.windowTests=true` with REFLUX_QUICKVIEW_HOME=<a copy of a data directory> and REFLUX_QUICKVIEW_ITEMS=<id,id>.
 */
class QuickViewWalkTest {
    @Test
    fun walk() {
        val home = System.getenv("REFLUX_QUICKVIEW_HOME")
        assumeTrue("walk not requested", windowTestsRequested && home != null)
        val copy = Files.createTempDirectory("reflux-qv")
        Path.of(home!!).toFile().copyRecursively(copy.toFile())
        val ids = System.getenv("REFLUX_QUICKVIEW_ITEMS").split(',').map(::MediaId)
        val shell = Shell(DesktopEnvironment(copy))
        try {
            shell.awaitShown("Home", exact = false)
            for (round in listOf("cold", "warm", "warm2")) {
                for (id in ids) {
                    val before = shell.ui { shell.app.revision }
                    val t0 = System.nanoTime()
                    shell.ui { shell.app.openQuickView(id) }
                    shell.await("Quick View", timeoutMs = 60_000) { shell.shows("Add to favorites") || shell.shows("Remove from favorites") }
                    val ms = (System.nanoTime() - t0) / 1_000_000
                    val after = shell.ui { shell.app.revision }
                    println("QV $round ${id.value} shown after ${ms}ms; library changes meanwhile: ${after - before}")
                    shell.ui { shell.app.closeQuickView() }
                    shell.await("Quick View closed") { shell.ui { shell.app.quickView == null } }
                    Thread.sleep(500)
                }
            }
        } finally {
            shell.close()
            copy.toFile().deleteRecursively()
        }
    }
}
