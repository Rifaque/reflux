package dev.reflux.desktop.engine

import dev.reflux.core.metadata.Authority
import dev.reflux.core.metadata.MetadataField
import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.source.MediaSource
import dev.reflux.library.ArtworkCache
import dev.reflux.library.Library
import dev.reflux.library.LibraryDatabase
import dev.reflux.library.ScanReport
import dev.reflux.library.net.JdkHttpFetcher
import dev.reflux.sources.jellyfin.JellyfinConnector
import dev.reflux.sources.jellyfin.JellyfinSource
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

/**
 * Library depth (M5) against the developer's real Jellyfin server and its real media: what the server says its
 * films, shows, seasons, and episodes are, their stills and season posters, and their cast, read with the
 * catalog, cached, and kept while the server is away.
 *
 * Read only: every request is a GET (asserted), nothing on the server or in its media folders is changed, and the
 * session this test opens is ended. It prints counts and timings, never a title. Runs where [LiveJellyfin] names a
 * server.
 */
class LibraryDepthLiveTest {
    @Test
    fun aRealServersLibraryIsDescribedPicturedAndKept() = runBlocking {
        val live = LiveJellyfin.settings()
        assumeTrue("no live Jellyfin server named (environment or ~/.reflux-live/jellyfin.env)", live != null)
        val directory = Files.createTempDirectory("reflux-depth-live")
        val methods = mutableSetOf<String>()
        val jdk = JdkHttpFetcher()
        val counting = HttpFetcher { request -> synchronized(methods) { methods += request.method }; jdk.send(request) }
        val credentials = JellyfinSource.connect(live!!.url, live.user, live.password, jdk, deviceId = "reflux-depth-live")
        try {
            val server = JellyfinSource(credentials, counting, "Live")
            val library = Library(LibraryDatabase.open(directory.resolve("library.db"))) { System.currentTimeMillis() }
            library.addSource(server, "live")
            val (report, scanTime) = measureTimedValue { library.scan(server) }
            assertEquals(ScanReport.Status.COMPLETED, report.status)
            // M4 scanned this server in about 3 s; describing it must not make that much slower (docs/VERIFICATION.md).
            assertTrue(scanTime.inWholeSeconds < 15, "scan took $scanTime")
            assertEquals(setOf("GET"), methods, "reading a library changes nothing on the server")

            val movies = library.movies()
            val shows = library.shows()
            val details = shows.mapNotNull { library.showDetail(it.item.id) }
            val seasons = details.flatMap { it.seasons }
            val episodes = seasons.flatMap { it.episodes }
            fun percent(part: Int, whole: Int) = if (whole == 0) "-" else "${part * 100 / whole}%"
            val described = movies.count { it.metadata?.overview != null }
            val sourceSaid = movies.count { it.metadata?.provenance?.get(MetadataField.OVERVIEW)?.authority == Authority.SOURCE }
            val seasonPosters = seasons.count { it.entry.artwork[ArtworkKind.POSTER] != null }
            val seasonStories = seasons.count { it.entry.metadata?.overview != null }
            val stills = episodes.count { it.artwork[ArtworkKind.THUMBNAIL] != null }
            val episodeStories = episodes.count { it.metadata?.overview != null }
            println(
                "LIVE depth: scan ${scanTime.inWholeMilliseconds} ms; movies ${movies.size} (overview ${percent(described, movies.size)}, " +
                    "from the server ${percent(sourceSaid, movies.size)}); " +
                    "shows ${shows.size} (overview ${percent(shows.count { it.metadata?.overview != null }, shows.size)}); " +
                    "seasons ${seasons.size} (poster ${percent(seasonPosters, seasons.size)}, overview ${percent(seasonStories, seasons.size)}); " +
                    "episodes ${episodes.size} (still ${percent(stills, episodes.size)}, overview ${percent(episodeStories, episodes.size)})",
            )
            if (movies.isNotEmpty()) assertTrue(described * 2 >= movies.size, "most films are described by the server")
            if (episodes.isNotEmpty()) assertTrue(stills * 2 >= episodes.size, "most episodes have a still of their own")

            // The pictures a page needs, fetched once each, and shown again with the server away.
            val wanted = (episodes.take(6).mapNotNull { it.artwork[ArtworkKind.THUMBNAIL] } +
                seasons.take(4).mapNotNull { it.entry.artwork[ArtworkKind.POSTER] } +
                movies.take(3).mapNotNull { it.artwork[ArtworkKind.BACKDROP] }).distinct()
            val requests = AtomicInteger()
            val cache = ArtworkCache(directory.resolve("artwork"), HttpFetcher { requests.incrementAndGet(); jdk.send(it) })
            val sources: (dev.reflux.core.model.SourceId) -> MediaSource? = { server }
            val (files, fetchTime) = measureTimedValue { wanted.map { cache.get(it, sources) } }
            val decoded = files.filterNotNull().count { ImageIO.read(it.toFile()) != null }
            val again = measureTimedValue { wanted.map { cache.get(it, sources) } }
            println(
                "LIVE artwork: ${wanted.size} pictures (${wanted.count { it is ArtworkLocator.SourceFile }} from the server), " +
                    "fetched ${files.count { it != null }} in ${fetchTime.inWholeMilliseconds} ms, decoded $decoded, " +
                    "cache ${cache.usage().files} files ${cache.usage().bytes / 1024} KB; again ${again.duration.inWholeMilliseconds} ms with ${requests.get() - files.count { it != null }} more requests",
            )
            assertEquals(files.count { it != null }, requests.get(), "a picture kept is never fetched again")
            assertTrue(Files.list(directory).use { stream -> stream.allMatch { it.fileName.toString() in setOf("library.db", "library.db-wal", "library.db-shm", "artwork") } })

            // The server away: what it said and what was kept still show.
            val away = JellyfinSource(credentials, HttpFetcher { error("the server is away") }, "Live")
            val before = library.movies().map { it.metadata }
            assertEquals(ScanReport.Status.UNAVAILABLE, library.scan(away).status)
            assertEquals(before, library.movies().map { it.metadata }, "nothing is lost while the server is away")
            val offline = ArtworkCache(directory.resolve("artwork"), HttpFetcher { error("offline") })
            assertEquals(files.count { it != null }, wanted.count { offline.cached(it) != null }, "kept pictures show offline")
        } finally {
            JellyfinConnector(jdk, "reflux-depth-live").signOut(credentials.encode(), null)
            directory.toFile().deleteRecursively()
        }
    }
}
