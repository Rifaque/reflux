package dev.reflux.ui.state

import dev.reflux.core.model.MediaId
import dev.reflux.library.Library
import dev.reflux.library.LibraryDatabase
import dev.reflux.ui.testDevice
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import org.junit.Assume.assumeTrue

/**
 * Measures what Quick View reads, on a real library copy (CACHY-003): opt-in only, with REFLUX_TIMING_LIBRARY=<a copy of
 * library.db> and REFLUX_TIMING_ITEMS=<id,id>. It copies the file again before opening it, so even the copy is left as it was.
 */
class QuickViewTimingTest {
    @Test
    fun measure() {
        val path = System.getenv("REFLUX_TIMING_LIBRARY")
        assumeTrue("measurement not requested", path != null)
        val ids = System.getenv("REFLUX_TIMING_ITEMS").orEmpty().split(',').filter { it.isNotBlank() }
        val copy = Files.createTempDirectory("timing").resolve("library.db")
        Files.copy(Path.of(path!!), copy)
        val library = Library(LibraryDatabase.open(copy)) { System.currentTimeMillis() }
        for (id in ids) {
            repeat(5) { run ->
                val t0 = System.nanoTime()
                val details = library.detailsModel(MediaId(id), testDevice)
                val t1 = System.nanoTime()
                val quick = library.quickViewModel(MediaId(id), testDevice)
                val t2 = System.nanoTime()
                val same = quick != null && details != null && quick.facts == details.facts && quick.playLabel == details.playLabel && quick.playId == details.playId
                println("TIMING $id run=$run details=${(t1 - t0) / 1_000_000}ms quick=${(t2 - t1) / 1_000_000}ms episodes=${details?.seasons?.sumOf { it.episodes.size }} same=$same")
            }
        }
    }

}
