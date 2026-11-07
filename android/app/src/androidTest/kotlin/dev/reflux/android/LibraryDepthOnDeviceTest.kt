package dev.reflux.android

import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.reflux.core.metadata.Authority
import dev.reflux.core.metadata.MetadataField
import dev.reflux.core.model.ArtworkKind
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * Library depth (M5) on a real Android runtime, over the installed application's own library (connected by
 * `JellyfinExecutionOnDeviceTest.prepareTheInstalledApplication`): what its server says its works are, their
 * stills and season posters fetched through Android's HTTP stack, decoded at the size asked for, and kept. Reads only;
 * prints counts and timings, never a title. Skipped where the library has no shows.
 */
@RunWith(AndroidJUnit4::class)
class LibraryDepthOnDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun report(line: String) {
        Log.i("RefluxExecution", line)
    }

    @Test
    fun theInstalledLibraryIsDescribedAndPictured() = runBlocking<Unit> {
        val environment = (context.applicationContext as RefluxApplication).environment
        val library = environment.library
        val shows = library.shows()
        assumeTrue("the installed library has no shows", shows.isNotEmpty())
        val movies = library.movies()
        val started = SystemClock.elapsedRealtime()
        val details = shows.mapNotNull { library.showDetail(it.item.id) }
        val read = SystemClock.elapsedRealtime() - started
        val seasons = details.flatMap { it.seasons }
        val episodes = seasons.flatMap { it.episodes }
        val described = movies.count { it.metadata?.provenance?.get(MetadataField.OVERVIEW)?.authority == Authority.SOURCE }
        report(
            "depth: movies ${movies.size} (server overview $described), shows ${shows.size}, seasons ${seasons.size} " +
                "(poster ${seasons.count { it.entry.artwork[ArtworkKind.POSTER] != null }}, overview ${seasons.count { it.entry.metadata?.overview != null }}), " +
                "episodes ${episodes.size} (still ${episodes.count { it.artwork[ArtworkKind.THUMBNAIL] != null }}, overview ${episodes.count { it.metadata?.overview != null }}); " +
                "every show's page read in $read ms",
        )
        assertTrue(episodes.count { it.artwork[ArtworkKind.THUMBNAIL] != null } * 2 >= episodes.size, "most episodes have a still")

        val stills = episodes.take(4).mapNotNull { it.artwork[ArtworkKind.THUMBNAIL] } + seasons.take(2).mapNotNull { it.entry.artwork[ArtworkKind.POSTER] }
        val fetched = SystemClock.elapsedRealtime()
        val files = stills.map { environment.artwork.get(it, environment.registry::resolve) }
        val widths = files.filterNotNull().map { file ->
            BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(file.toString(), it) }.outWidth
        }
        report("artwork: ${stills.size} pictures, ${files.count { it != null }} fetched in ${SystemClock.elapsedRealtime() - fetched} ms, widths $widths, cache ${environment.artwork.usage().files} files")
        assertTrue(widths.isNotEmpty() && widths.all { it in 1..1920 }, "decoded, at the size asked for: $widths")
        assertTrue(stills.all { environment.artwork.cached(it) != null || files[stills.indexOf(it)] == null }, "kept for offline")
    }
}
