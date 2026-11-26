package dev.reflux.library

import dev.reflux.sources.local.LocalFolderSource
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * B-006's repeatable fixture (docs/verification/VERIFICATION.md, "Phase 1"): a real library scanned from a folder of ordinary names,
 * 1,000 films and 200 shows of 50 episodes (11,000 files), and how long local search takes on it. Only with
 * `-Preflux.perf=true`; numbers go to the console and to `build/perf/search.txt`. No network, no metadata provider:
 * this is local search alone, the part Reflux controls end to end.
 */
class SearchPerformanceTest {
    @Test
    fun localSearchOnALargeLibrary() {
        assumeTrue("-Preflux.perf=true runs it", System.getProperty("reflux.perf") == "true")
        val root = Files.createTempDirectory("reflux-perf")
        val words = listOf("Night", "River", "Glass", "Silent", "Echo", "Winter", "Paper", "Iron", "Golden", "Hollow", "Crimson", "Northern", "Last", "Hidden", "Broken", "Velvet")
        fun name(i: Int) = "${words[i % words.size]} ${words[(i / words.size) % words.size]} ${i / (words.size * words.size) + 1}"
        repeat(1_000) { i ->
            val title = name(i)
            val year = 1960 + i % 60
            stub(root.resolve("Movies/$title ($year)/${title.replace(' ', '.')}.$year.1080p.BluRay.x264.mkv"))
        }
        repeat(200) { s ->
            val show = "Show ${name(s + 3_000)}"
            repeat(50) { e ->
                val season = e / 10 + 1
                val episode = e % 10 + 1
                stub(root.resolve("Shows/$show/Season %02d/${show.replace(' ', '.')}.S%02dE%02d.1080p.WEB-DL.mkv".format(season, season, episode)))
            }
        }
        val library = Library(LibraryDatabase.open(root.resolve("library.db"))) { System.currentTimeMillis() }
        val source = LocalFolderSource(root.resolve("Movies").parent)
        library.addSource(source, root.toString())
        val scanMs = timed { runBlocking { library.scan(source) } }
        val movies = library.movies().size
        val shows = library.shows().size
        assertTrue(movies >= 990 && shows >= 195, "the library is what was made: $movies films, $shows shows")

        val report = StringBuilder()
        fun line(text: String) { println(text); report.append(text).append('\n') }
        line("library: $movies films, $shows shows, ${library.queries.presentPlayables(null, null).executeAsList().size} playables; scan ${scanMs} ms (cold, no probe, no metadata)")
        // Where a title search's time goes: reading every row it searches, then everything else (documents, matching, entries).
        for ((label, read) in listOf(
            "  rows: films" to { library.queries.presentMovies(null, null).executeAsList() },
            "  rows: shows" to { library.queries.presentShows(null, null).executeAsList() },
            "  rows: every playable (for episodes)" to { library.queries.presentPlayables(null, null).executeAsList() },
            "  rows: resolved metadata of all" to { library.resolvedMetadata(library.queries.presentPlayables(null, null).executeAsList().map { it.id }, 500) },
        )) {
            repeat(3) { read() }
            val samples = (1..15).map { timed { read() } }.sorted()
            line("$label: median ${samples[samples.size / 2]} ms")
        }
        val cases = listOf(
            "title, a prefix" to { library.search("silent") },
            "title, two words" to { library.search("night river") },
            "title, a miss" to { library.search("zzzz") },
            "title, an episode's show" to { library.search("show echo") },
            "smart, structured" to { library.smartSearch("movies from the 90s").results },
            "smart, falls back to title" to { library.smartSearch("glass").results },
        )
        for ((label, query) in cases) {
            repeat(3) { query() } // warm
            val samples = (1..25).map { timed { query() } }.sorted()
            line("$label: median ${samples[samples.size / 2]} ms, p95 ${samples[(samples.size * 95 / 100).coerceAtMost(samples.size - 1)]} ms, max ${samples.last()} ms (25 runs)")
        }
        val out = Path.of("build/perf").also { Files.createDirectories(it) }.resolve("search.txt")
        Files.writeString(out, report.toString())
        root.toFile().deleteRecursively()
    }

    private fun stub(path: Path) {
        Files.createDirectories(path.parent)
        Files.write(path, ByteArray(16))
    }

    private fun timed(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }
}
