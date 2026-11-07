package dev.reflux.sources.plex

import dev.reflux.core.identify.ParsedKind
import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.Availability
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.source.SectionKind
import dev.reflux.core.source.SourceCapability
import dev.reflux.core.source.SourceLocality
import dev.reflux.core.source.SourceSection
import dev.reflux.core.source.SourceSignInRequiredException
import dev.reflux.core.source.SourceUnavailableException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A Plex Media Server as a source: its libraries, its catalog, its files, and its watch state. */
class PlexSourceTest {
    private val plex = FakePlex()
    private val credentials = PlexCredentials("http://192.168.1.20:32400", "machine-1", "srv-token", "dev-1", "alex")
    private val source = PlexSource(credentials, plex.http, "Living Room")

    @Test
    fun itIsACatalogWithLibrariesThatPlaysAndKeepsFilesAndKeepsWatchState() {
        assertEquals("plex", source.descriptor.type)
        assertEquals(SourceLocality.LOCAL_NETWORK, source.descriptor.locality)
        assertEquals(
            setOf(SourceCapability.PROVIDES_CATALOG, SourceCapability.WATCH_STATE_SYNC, SourceCapability.DOWNLOAD),
            source.descriptor.capabilities,
            "no server conversion is claimed: Plex plays its files as they are",
        )
        assertEquals(PlexSource(credentials.copy(serverUrl = "http://plex.local:32400"), plex.http).descriptor.id, source.descriptor.id, "the same server and account is one source, however it is reached")
        assertFalse(PlexSource(credentials.copy(account = "sam"), plex.http).descriptor.id == source.descriptor.id, "another account is another source")
    }

    @Test
    fun onlyFilmAndShowLibrariesAreItsLibraries() = runTest {
        assertEquals(listOf(SourceSection("1", "Films", SectionKind.MOVIES), SourceSection("2", "TV Shows", SectionKind.SHOWS)), source.sections())
    }

    @Test
    fun theCatalogIsEveryFilmAndEpisodeWithTheLibraryItIsIn() = runTest {
        val entries = source.catalog().toList()
        assertEquals(listOf("Heat", "Arrival", "Severance", "Severance"), entries.map { it.identity.title })
        assertEquals(listOf("1", "1", "2", "2"), entries.map { it.section })
        assertFalse(entries.any { it.identity.title == "Two Parts" }, "a film in two files is not one version yet")
        // Pages are followed to the end, and each page's items are read whole.
        assertTrue(plex.calls.any { "X-Plex-Container-Start=2" in it }, "the second page of films was asked for: ${plex.calls}")
    }

    @Test
    fun aFilmComesWithItsStreamsSubtitlesArtworkAndIdentifiers() = runTest {
        val heat = source.catalog().toList().first { it.identity.title == "Heat" }
        assertEquals(ParsedKind.MOVIE, heat.identity.kind)
        assertEquals(1995, heat.identity.year)
        assertEquals(mapOf("imdb" to "tt101", "tmdb" to "101"), heat.identity.externalIds)
        val version = heat.versions.single()
        assertEquals("items/101/library/parts/501/1700000000/file.mkv", version.path)
        assertEquals(4_000_000_000, version.sizeBytes)
        assertEquals(Container.MATROSKA, version.stream.container)
        assertEquals(VideoCodec.H264, version.stream.video?.codec)
        assertEquals(1080, version.stream.video?.height)
        assertEquals(8_000_000, version.stream.bitrateBps, "Plex counts kilobits")
        assertEquals(listOf(AudioCodec.AC3, AudioCodec.AAC), version.stream.audio.map { it.codec })
        assertEquals(listOf("eng", "fra"), version.stream.audio.map { it.language })
        assertTrue(version.stream.audio.first().default)
        assertEquals(listOf(SubtitleFormat.SRT), version.stream.subtitles.map { it.format }, "the embedded subtitle")
        assertTrue(version.stream.subtitles.single().forced)
        assertEquals(listOf("items/101/library/streams/91"), version.subtitles.map { it.path }, "the separate subtitle file")
        assertEquals("spa", version.subtitles.single().language)
        assertEquals("images/library/metadata/101/thumb/1700000000", heat.artwork[ArtworkKind.POSTER])
        assertEquals("images/library/metadata/101/art/1700000000", heat.artwork[ArtworkKind.BACKDROP])
    }

    @Test
    fun anEpisodeBelongsToItsShowWithTheShowsIdentifiersAndArtwork() = runTest {
        val first = source.catalog().toList().first { it.identity.episode == 1 }
        assertEquals(ParsedKind.EPISODE, first.identity.kind)
        assertEquals("Severance", first.identity.title)
        assertEquals(2022, first.identity.year)
        assertEquals(1, first.identity.season)
        assertEquals("Episode 1", first.identity.episodeTitle)
        assertEquals(mapOf("tvdb" to "371980", "tmdb" to "95396"), first.identity.externalIds)
        assertEquals("images/library/metadata/200/thumb/1", first.showArtwork[ArtworkKind.POSTER])
        assertEquals("images/library/metadata/201/thumb/1", first.artwork[ArtworkKind.THUMBNAIL])
        val video = first.versions.single().stream.video!!
        assertEquals(VideoCodec.HEVC, video.codec)
        assertEquals(DynamicRange.HDR10, video.dynamicRange)
        assertEquals(10, video.bitDepth)
        assertTrue(first.versions.single().stream.audio.single().atmos)
    }

    @Test
    fun whatWasWatchedIsReadAsTheServerKeepsIt() = runTest {
        val entries = source.catalog().toList()
        val heat = entries.first { it.identity.title == "Heat" }.userState!!
        assertFalse(heat.played)
        assertEquals(1_200_000, heat.positionMs)
        assertEquals(1_700_001_000_000, heat.lastPlayedAtEpochMs)
        val arrival = entries.first { it.identity.title == "Arrival" }.userState!!
        assertTrue(arrival.played)
        assertEquals(2, arrival.playCount)
        val unwatched = entries.first { it.identity.episode == 2 }.userState!!
        assertFalse(unwatched.played)
        assertEquals(0, unwatched.positionMs)
    }

    @Test
    fun filesAndArtworkAreOpenedFromTheServerWithTheTokenInAHeader() = runTest {
        val film = source.playbackTarget("items/101/library/parts/501/1700000000/file.mkv")
        assertEquals("http://192.168.1.20:32400/library/parts/501/1700000000/file.mkv", film.uri)
        assertEquals(mapOf("X-Plex-Token" to "srv-token"), film.headers)
        assertFalse("srv-token" in film.toString(), "nothing printed carries the token")
        assertEquals(film, source.downloadTarget("items/101/library/parts/501/1700000000/file.mkv"), "a copy is the file itself")
        assertEquals("http://192.168.1.20:32400/library/streams/91", source.playbackTarget("items/101/library/streams/91").uri)
        assertEquals("http://192.168.1.20:32400/library/metadata/101/thumb/1700000000", source.playbackTarget("images/library/metadata/101/thumb/1700000000").uri)
        assertFailsWith<IllegalArgumentException> { source.playbackTarget("videos/1") }
        val open = PlexSource(credentials.copy(token = null), plex.http)
        assertEquals(emptyMap(), open.playbackTarget("items/101/library/parts/501/1/file.mkv").headers, "a server that lets the network in needs no token")
    }

    @Test
    fun progressStoppingAndPlayedAreToldToTheServer() = runTest {
        val path = "items/101/library/parts/501/1700000000/file.mkv"
        source.reportProgress(path, 61_000, paused = false)
        source.reportProgress(path, 62_000, paused = true)
        source.reportStopped(path, 63_000)
        source.setPlayed(path, true)
        source.setPlayed(path, false)
        val told = plex.calls.filter { it.startsWith(":/") }
        assertEquals(5, told.size, "$told")
        assertTrue("key=101" in told[0] && "time=61000" in told[0] && "state=playing" in told[0], told[0])
        assertTrue("state=paused" in told[1], told[1])
        assertTrue(told[2].startsWith(":/progress") && "state=stopped" in told[2] && "time=63000" in told[2], told[2])
        assertTrue(told[3].startsWith(":/scrobble") && "key=101" in told[3], told[3])
        assertTrue(told[4].startsWith(":/unscrobble"), told[4])
        assertEquals(0.90, source.marksPlayedPast)
        assertTrue(plex.requests.filter { ":/" in it.url }.all { it.headers["X-Plex-Token"] == "srv-token" })
    }

    @Test
    fun aServerAwayIsUnavailableAndATokenNoLongerAcceptedNeedsSigningIn() = runTest {
        assertEquals(Availability.AVAILABLE, source.availability())
        plex.serverToken = "rotated"
        assertFailsWith<SourceSignInRequiredException> { source.catalog().toList() }
        assertEquals(Availability.AVAILABLE, source.availability(), "it is there; it no longer lets this token in")
        plex.online = false
        assertEquals(Availability.UNAVAILABLE, source.availability())
        val away = assertFailsWith<SourceUnavailableException> { source.catalog().toList() }
        assertFalse(away is SourceSignInRequiredException)
    }

    @Test
    fun addressesAreCompletedWithPlexsPort() {
        assertEquals("http://192.168.1.20:32400", PlexSource.normalizeUrl("192.168.1.20"))
        assertEquals("http://plex.local:32400", PlexSource.normalizeUrl(" plex.local/ "))
        assertEquals("https://media.example.com:443", PlexSource.normalizeUrl("https://media.example.com:443"))
        assertEquals("http://[fd00::5]:32400", PlexSource.normalizeUrl("http://[fd00::5]"))
        assertEquals(SourceLocality.REMOTE, PlexSource.localityOf("https://media.example.com:443"))
        assertNull(PlexCredentials.decode("server=http://x"))
        assertEquals(credentials, PlexCredentials.decode(credentials.encode()))
        assertFalse("srv-token" in credentials.toString())
    }
}
