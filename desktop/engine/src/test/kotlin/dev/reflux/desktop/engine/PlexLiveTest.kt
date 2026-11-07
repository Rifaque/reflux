package dev.reflux.desktop.engine

import dev.reflux.core.model.Availability
import dev.reflux.core.model.Episode
import dev.reflux.core.net.HttpRequest
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DeliveryMode
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.DisplayCapabilities
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoDecoderSupport
import dev.reflux.core.source.Connection
import dev.reflux.core.source.ConnectionRequest
import dev.reflux.core.source.SectionKind
import dev.reflux.library.Library
import dev.reflux.library.LibraryDatabase
import dev.reflux.library.LibraryScope
import dev.reflux.library.inclusion
import dev.reflux.library.net.JdkHttpFetcher
import dev.reflux.library.planPlayback
import dev.reflux.library.setLibraryIncluded
import dev.reflux.library.setSourceIncluded
import dev.reflux.sources.plex.PlexConnector
import dev.reflux.sources.plex.PlexSource
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A real Plex Media Server, end to end through the connector, the adapter, and the library: connecting, its
 * libraries, its films and episodes with their streams and subtitles, the file itself, Direct Play, watch state
 * both ways, and inclusion.
 *
 * Runs when `REFLUX_PLEX_TEST` names a server that lets this machine in without signing in, with a film library
 * holding "Plex Test Film" (2020, a minute, with an English .srt beside it) and "Plex Long Film" (2021, six minutes), a second film library holding "Plex Kids Film",
 * and a show library holding "Plex Test Series" S01E01 and S01E02. It changes the watch state of "Plex Test Film" and
 * leaves it unwatched. VERIFICATION.md says how such a server was made in Docker.
 */
class PlexLiveTest {
    private val address = System.getenv("REFLUX_PLEX_TEST")

    private val device = DeviceCapabilities(
        videoDecoders = listOf(VideoDecoderSupport(VideoCodec.H264, 3840, 2160, 8, hardware = true), VideoDecoderSupport(VideoCodec.HEVC, 3840, 2160, 10, hardware = true)),
        audioDecoders = AudioCodec.entries.toSet(),
        display = DisplayCapabilities(3840, 2160),
        containers = Container.entries.toSet(),
        subtitleFormats = SubtitleFormat.entries.toSet(),
    )

    @Test
    fun aRealPlexServerIsASourceLikeAnyOther() = runBlocking {
        assumeTrue("no Plex server named in REFLUX_PLEX_TEST", address != null)
        val http = JdkHttpFetcher()
        val connection = assertIs<Connection.Established>(PlexConnector(http, "reflux-live-test", "Reflux live test").connect(ConnectionRequest(address!!)))
        val source = connection.source as PlexSource
        assertEquals(Availability.AVAILABLE, source.availability())
        println("Plex: connected to \"${source.descriptor.displayName}\" without signing in")
        assertEquals("Plex", source.descriptor.displayName, "a container's generated host name is not a name")

        val library = Library(LibraryDatabase.open(Files.createTempDirectory("reflux-plex-live").resolve("library.db"))) { System.currentTimeMillis() }
        library.addSource(source, connection.config)
        library.scan(source)
        val id = source.descriptor.id

        // Its libraries, as the server has them.
        val libraries = library.inclusion(id)!!.libraries
        println("Plex: libraries ${libraries.map { "${it.name} (${it.kind})" }}")
        assertEquals(setOf("Films", "Kids", "TV"), libraries.map { it.name }.toSet())
        assertEquals(SectionKind.SHOWS, libraries.single { it.name == "TV" }.kind)

        // Films and episodes, identified by the server, with a trailing year read as the year.
        val movies = library.movies().associateBy { it.item.title }
        println("Plex: films ${movies.keys}")
        assertEquals(setOf("Plex Test Film", "Plex Long Film", "Plex Kids Film"), movies.keys)
        val show = library.shows().single()
        assertEquals("Plex Test Series", show.item.title)
        val episodes = library.showDetail(show.item.id)!!.seasons.single().episodes.map { it.item as Episode }
        assertEquals(listOf(1, 2), episodes.map { it.episodeNumber })

        // Its streams and its subtitle file, as the server read them.
        val film = movies.getValue("Plex Test Film").item.id
        val version = library.versions(film).single().version
        println("Plex: stream ${version.stream.container} ${version.stream.video?.codec} ${version.stream.video?.width}x${version.stream.video?.height} audio ${version.stream.audio.map { it.codec.name + ":" + it.language }} subtitles ${version.externalSubtitles.map { it.format.name + ":" + it.language }}")
        assertEquals(Container.MATROSKA, version.stream.container)
        assertEquals(VideoCodec.H264, version.stream.video?.codec)
        assertEquals(640, version.stream.video?.width)
        assertEquals(AudioCodec.AAC, version.stream.audio.single().codec)
        assertTrue(version.externalSubtitles.any { it.format == SubtitleFormat.SRT }, "the .srt beside it: ${version.externalSubtitles}")

        // The file itself, from the server, as a player and a download open it.
        val target = source.playbackTarget(version.location.path)
        val bytes = http.send(HttpRequest(target.uri, headers = target.headers + ("Range" to "bytes=0-3")))
        println("Plex: file answered ${bytes.status} with ${bytes.body.size} bytes")
        assertTrue(bytes.ok && bytes.body.take(4) == listOf(0x1A, 0x45, 0xDF, 0xA3).map { it.toByte() }, "a Matroska file's first bytes")
        val subtitle = version.externalSubtitles.first { it.format == SubtitleFormat.SRT }
        val subtitleTarget = source.playbackTarget(subtitle.location.path)
        assertTrue("Hello from Plex" in http.send(HttpRequest(subtitleTarget.uri, headers = subtitleTarget.headers)).text())

        // Played as it is: the planner's Direct Play, through the same path as every source.
        val plan = library.planPlayback(film, device, { if (it == id) source else null })!!
        println("Plex: planned ${plan.planned?.delivery} from ${plan.request.target.uri.substringAfter("32400")}")
        assertEquals(DeliveryMode.DIRECT_PLAY, plan.planned?.delivery)

        // Watch state both ways: told to the server, and read back by a scan. Plex keeps no position in anything
        // as short as a minute, so the position goes through the six-minute film.
        source.setPlayed(version.location.path, true)
        library.scan(source)
        assertEquals(true, library.watchState(film)?.completed, "the server's played mark is adopted")
        source.setPlayed(version.location.path, false)
        val long = movies.getValue("Plex Long Film").item.id
        val longVersion = library.versions(long).single().version
        source.setPlayed(longVersion.location.path, false)
        source.reportStopped(longVersion.location.path, 180_000)
        library.scan(source)
        println("Plex: the long film after a stop at 3 min told to the server: ${library.watchState(long)?.positionMs} ms, completed ${library.watchState(long)?.completed}")
        assertEquals(180_000L, library.watchState(long)?.positionMs, "the server's position is adopted")
        source.setPlayed(longVersion.location.path, false)

        // Inclusion: a library out, then the whole source, still browsable on its own.
        val kids = libraries.single { it.name == "Kids" }.id
        library.setLibraryIncluded(id, kids, false)
        assertEquals(setOf("Plex Test Film", "Plex Long Film"), library.movies().map { it.item.title }.toSet())
        assertEquals(setOf("Plex Kids Film"), library.movies(LibraryScope.OfSource(id, kids)).map { it.item.title }.toSet())
        library.setSourceIncluded(id, false)
        assertTrue(library.movies().isEmpty() && library.shows().isEmpty())
        assertEquals(3, library.movies(LibraryScope.OfSource(id)).size)
        assertEquals(1, library.shows(LibraryScope.OfSource(id)).size)
        library.setSourceIncluded(id, true)
        assertEquals(setOf("Plex Test Film", "Plex Long Film"), library.movies().map { it.item.title }.toSet(), "the library's own switch is kept")
        println("Plex: inclusion of the source and of a library behaved as specified")
    }
}
