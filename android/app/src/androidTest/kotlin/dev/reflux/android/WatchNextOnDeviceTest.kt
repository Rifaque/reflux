package dev.reflux.android

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.TvContractCompat.PreviewPrograms
import androidx.tvprovider.media.tv.TvContractCompat.WatchNextPrograms
import dev.reflux.android.engine.AndroidEnvironment
import dev.reflux.android.launcher.WatchNextLink
import dev.reflux.android.launcher.WatchNextPublisher
import dev.reflux.core.model.ArtworkKind
import dev.reflux.library.ArtworkCache
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reflux's programs on the launcher's Watch Next row, on a real Android TV provider: added from the
 * library's watch state, updated in place, and removed when finished. Runs where the device has a TV
 * provider (televisions) and skips itself elsewhere; on Fire OS, which has the provider but does not show
 * the row, it checks that nothing is written there.
 *
 * It runs as the application, so while it runs it replaces the installed application's own programs; it
 * deletes them at the end and the application puts its own back the next time it runs.
 */
@SuppressLint("RestrictedApi") // the provider's column names, declared on types the library marks as its own
@RunWith(AndroidJUnit4::class)
class WatchNextOnDeviceTest {
    private val world = DeviceWorld("watch-next")
    /** The application's own artwork cache, which the launcher reads through the application's provider. */
    private val artwork = ArtworkCache(File(context.filesDir, AndroidEnvironment.ARTWORK_DIRECTORY).toPath(), world.environment.http)
    private val publisher = WatchNextPublisher(context, world.environment.library, artwork) { problem, cause -> throw AssertionError(problem, cause) }
    private val lent = mutableListOf<File>()

    private data class Row(
        val id: Long, val title: String?, val episodeTitle: String?, val season: String?, val episode: String?, val watchNextType: Int,
        val type: Int, val position: Int, val duration: Int, val engaged: Long, val poster: String?, val aspect: Int, val key: String?, val intent: String?,
    )

    private val fireOs = context.packageManager.hasSystemFeature(WatchNextPublisher.FIRE_TV_FEATURE)

    /** Whether the device has the TV provider at all, whether or not Reflux writes to it. */
    private val tvProvider = context.contentResolver.acquireContentProviderClient(TvContractCompat.AUTHORITY)?.also { it.close() } != null

    @Before
    fun onlyWithATvProvider() {
        assumeTrue("this device has no TV provider", tvProvider)
        clear()
    }

    @After
    fun cleanUp() {
        if (tvProvider) clear()
        lent.forEach(File::delete)
        world.close()
    }

    private fun clear() {
        context.contentResolver.delete(WatchNextPrograms.CONTENT_URI, null, null)
        context.getSharedPreferences("watch-next-dismissed", 0).edit().clear().commit()
    }

    private fun rows(): List<Row> {
        val columns = arrayOf(
            WatchNextPrograms._ID, WatchNextPrograms.COLUMN_TITLE, WatchNextPrograms.COLUMN_EPISODE_TITLE, WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
            WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER, WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE, WatchNextPrograms.COLUMN_TYPE,
            WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS, WatchNextPrograms.COLUMN_DURATION_MILLIS, WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
            WatchNextPrograms.COLUMN_POSTER_ART_URI, WatchNextPrograms.COLUMN_POSTER_ART_ASPECT_RATIO, WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID, WatchNextPrograms.COLUMN_INTENT_URI,
        )
        return context.contentResolver.query(WatchNextPrograms.CONTENT_URI, columns, null, null, null)!!.use { c ->
            buildList {
                while (c.moveToNext()) add(Row(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getInt(5), c.getInt(6), c.getInt(7), c.getInt(8), c.getLong(9), c.getString(10), c.getInt(11), c.getString(12), c.getString(13)))
            }
        }
    }

    @Test
    fun aFilmIsAddedUpdatedInPlaceAndRemovedWhenFinished() = runBlocking {
        assumeTrue("Fire OS: Reflux keeps no programs there", !fireOs)
        world.stub("Heat (1995)/Heat (1995).mkv")
        world.environment.library.scan(world.folder)
        val heat = world.movie("Heat")
        val library = world.environment.library

        library.stopped(heat, 40 * MINUTE, 170 * MINUTE)
        publisher.publish()
        val added = rows().single()
        assertEquals("Heat", added.title)
        assertEquals(WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE, added.watchNextType)
        assertEquals(PreviewPrograms.TYPE_MOVIE, added.type)
        assertEquals((40 * MINUTE).toInt(), added.position)
        assertEquals((170 * MINUTE).toInt(), added.duration)
        assertEquals(heat.value, added.key)

        // Selecting it opens Reflux on this work.
        val intent = Intent.parseUri(assertNotNull(added.intent), Intent.URI_INTENT_SCHEME)
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(heat, WatchNextLink.itemId(intent.dataString))

        publisher.publish()
        assertEquals(listOf(added), rows(), "nothing changed, so nothing was written")

        library.stopped(heat, 80 * MINUTE, 170 * MINUTE)
        publisher.publish()
        val updated = rows().single()
        assertEquals(added.id, updated.id, "the same row, updated in place")
        assertEquals((80 * MINUTE).toInt(), updated.position)

        library.stopped(heat, 169 * MINUTE, 170 * MINUTE)
        publisher.publish()
        assertTrue(rows().isEmpty())
        assertEquals(true, library.watchState(heat)?.completed)
    }

    @Test
    fun aShowOffersItsNextEpisode() = runBlocking {
        assumeTrue("Fire OS: Reflux keeps no programs there", !fireOs)
        world.stub("Caminandes/Season 1/Caminandes.S01E01.mkv")
        world.stub("Caminandes/Season 1/Caminandes.S01E02.mkv")
        val library = world.environment.library
        library.scan(world.folder)
        val episodes = library.showDetail(library.shows().single().item.id)!!.seasons.single().episodes.map { it.item.id }

        library.stopped(episodes[0], 2 * MINUTE + 25_000, 2 * MINUTE + 30_000)
        publisher.publish()
        val next = rows().single()
        assertEquals(episodes[1].value, next.key)
        assertEquals("Caminandes", next.title)
        assertEquals(WatchNextPrograms.WATCH_NEXT_TYPE_NEXT, next.watchNextType)
        assertEquals(PreviewPrograms.TYPE_TV_EPISODE, next.type)
        assertEquals("1", next.season)
        assertEquals("2", next.episode)
        assertEquals(0, next.position)

        library.stopped(episodes[1], 2 * MINUTE + 25_000, 2 * MINUTE + 30_000)
        publisher.publish()
        assertTrue(rows().isEmpty(), "the show is finished")
    }

    @Test
    fun theLauncherCanReadTheCardsPicture() = runBlocking {
        assumeTrue("Fire OS: Reflux keeps no programs there", !fireOs)
        world.stub("Heat (1995)/Heat (1995).mkv")
        File(world.media, "Heat (1995)/poster.jpg").writeBytes(POSTER)
        val library = world.environment.library
        library.scan(world.folder)
        val heat = world.movie("Heat")
        library.stopped(heat, 40 * MINUTE, 170 * MINUTE)

        val locator = assertNotNull(library.entry(heat)?.artwork?.get(ArtworkKind.POSTER))
        publisher.publish()
        assertNull(rows().single().poster, "not cached yet, so not offered: publishing never waits for the network")

        // The pipeline caches the artwork; the next run offers it.
        val cached = assertNotNull(artwork.get(locator, world.environment.registry::resolve)).toFile()
        lent += cached
        publisher.publish()
        val row = rows().single()
        val poster = Uri.parse(assertNotNull(row.poster))
        assertEquals("${context.packageName}.artwork", poster.authority)
        assertEquals(PreviewPrograms.ASPECT_RATIO_MOVIE_POSTER, row.aspect)
        assertTrue(POSTER.contentEquals(context.contentResolver.openInputStream(poster)!!.use { it.readBytes() }))
    }

    @Test
    fun fireOsKeepsNoProgramsInTheTvProvider() = runBlocking {
        assumeTrue("not Fire OS", fireOs)
        assertFalse(publisher.supported)
        world.stub("Heat (1995)/Heat (1995).mkv")
        val library = world.environment.library
        library.scan(world.folder)
        val heat = world.movie("Heat")
        library.stopped(heat, 40 * MINUTE, 170 * MINUTE)

        publisher.publish()
        assertTrue(rows().isEmpty(), "nothing was written to the TV provider")
        // Reflux's own state is untouched: the work is still there to continue, inside Reflux.
        assertEquals(40 * MINUTE, library.watchState(heat)?.positionMs)
        assertEquals(heat, library.continuations(10).single().entry.item.id)
    }

    private companion object {
        /** A tiny JPEG's first bytes; only its bytes travel, nothing decodes it. */
        val POSTER = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4)
    }
}
