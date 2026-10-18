package dev.reflux.library

import dev.reflux.core.identify.ParsedKind
import dev.reflux.core.identify.ParsedMedia
import dev.reflux.core.model.Availability
import dev.reflux.core.model.Episode
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.StableIds
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.AudioStream
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.DisplayCapabilities
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoDecoderSupport
import dev.reflux.core.playback.VideoStream
import dev.reflux.core.source.CatalogEntry
import dev.reflux.core.source.CatalogSource
import dev.reflux.core.source.CatalogVersion
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.core.source.SourceCapability
import dev.reflux.core.source.SourceDescriptor
import dev.reflux.core.source.SourceLocality
import dev.reflux.core.source.SourceUnavailableException
import dev.reflux.core.versions.SelectionCriterion
import dev.reflux.core.watch.Handoff
import dev.reflux.core.watch.TimelineMatch
import dev.reflux.sources.local.LocalFolderSource
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MINUTE = 60_000L

private fun stream(minutes: Long, height: Int = 1080, codec: VideoCodec = VideoCodec.H264, bitDepth: Int = 8) = StreamInfo(
    container = Container.MATROSKA,
    video = VideoStream(codec, height * 16 / 9, height, bitDepth),
    audio = listOf(AudioStream(AudioCodec.AC3, 6, "eng")),
    durationMs = minutes * MINUTE,
)

/** A server that has already identified its episodes, and can go away. */
private class EpisodeServer(name: String) : CatalogSource {
    val entries = mutableListOf<CatalogEntry>()
    var reachable = true
    override val descriptor = SourceDescriptor(StableIds.sourceId("server", name), "server", name, SourceLocality.LOCAL_NETWORK, setOf(SourceCapability.PROVIDES_CATALOG))
    override suspend fun availability() = if (reachable) Availability.AVAILABLE else Availability.UNAVAILABLE
    override suspend fun playbackTarget(path: String) = PlaybackTarget("https://server/$path")
    override fun catalog() = if (reachable) entries.toList().asFlow() else flow { throw SourceUnavailableException(descriptor.id) }

    fun episode(show: String, season: Int, episode: Int, vararg versions: CatalogVersion) {
        entries += CatalogEntry(ParsedMedia(ParsedKind.EPISODE, show, null, season = season, episode = episode), versions.toList())
    }
}

/**
 * An episode's versions are a film's: listed, ranked by Best Version, and pinned for the episode, with the
 * same rules for copies, cuts, and sources that are away (DECISIONS.md, 2026-10-18, "An episode's versions").
 */
class EpisodeVersionsTest {
    private val root: Path = Files.createTempDirectory("reflux-episode-versions")
    private var clock = 1_790_000_000_000L
    private val library = Library(testDatabase()) { clock }
    private val local = LocalFolderSource(root, "Shows")
    private val server = EpisodeServer("Home Server")
    private val device = DeviceCapabilities(
        videoDecoders = listOf(VideoDecoderSupport(VideoCodec.H264, 3840, 2160, 8, hardware = true), VideoDecoderSupport(VideoCodec.HEVC, 3840, 2160, 10, hardware = true)),
        audioDecoders = AudioCodec.entries.toSet(),
        display = DisplayCapabilities(3840, 2160),
        containers = Container.entries.toSet(),
        subtitleFormats = SubtitleFormat.entries.toSet(),
    )

    init {
        library.addSource(local, root.toString())
        library.addSource(server, "https://server")
    }

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
    }

    private fun file(relative: String) = root.resolve(relative).also { it.parent.createDirectories(); it.writeBytes(ByteArray(8)) }

    private suspend fun scanBoth() {
        library.scan(local)
        library.scan(server)
    }

    private fun show() = library.shows().single { it.item.title == "Severance" }.item.id

    private fun episode(season: Int, number: Int): MediaId =
        library.showDetail(show())!!.seasons.flatMap { it.episodes }.map { it.item as Episode }.single { it.seasonNumber == season && it.episodeNumber == number }.id

    /** Severance S01E01 in three copies (a 1080p file here, a 4K stream, and a 720p stream), S01E02 in one. */
    private suspend fun severance() {
        file("Severance/Season 1/Severance.S01E01.1080p.mkv")
        file("Severance/Season 1/Severance.S01E02.1080p.mkv")
        server.episode("Severance", 1, 1, CatalogVersion("items/s1e1-4k", 9, 1, stream(57, 2160, VideoCodec.HEVC, 10)), CatalogVersion("items/s1e1-720", 3, 1, stream(57, 720)))
        scanBoth()
    }

    @Test
    fun anEpisodesVersionsAreListedFromEverySourceAndRanked() = runTest {
        severance()
        val pilot = episode(1, 1)
        val versions = library.versions(pilot)
        assertEquals(3, versions.size, "one episode, three copies across two sources")
        assertEquals(setOf(local.descriptor.id, server.descriptor.id), versions.map { it.version.location.sourceId }.toSet())

        val selection = library.selectVersion(pilot, device)
        // Best Version, unchanged: the 4K copy for a 4K display, then by quality.
        assertEquals("items/s1e1-4k", selection.best?.version?.location?.path)
        assertEquals(SelectionCriterion.RESOLUTION, selection.decidedBy)
        assertEquals(listOf(2160, 1080, 720), selection.ranked.map { it.version.stream.video?.height })
        // The same order every time.
        assertEquals(selection.ranked.map { it.version.id }, library.selectVersion(pilot, device).ranked.map { it.version.id })

        // The show's page knows which episodes have something to choose between, and how much.
        assertEquals(mapOf(pilot to 3, episode(1, 2) to 1), library.episodeVersionChoices(show()))
    }

    @Test
    fun anEpisodesPinIsItsOwnAndOutlivesARescan() = runTest {
        severance()
        val pilot = episode(1, 1)
        val second = episode(1, 2)
        val local1080 = library.versions(pilot).single { it.version.location.sourceId == local.descriptor.id }.version.id

        library.pinVersion(pilot, local1080)
        val pinned = library.selectVersion(pilot, device)
        assertEquals(local1080, pinned.best?.version?.id)
        assertEquals(SelectionCriterion.USER_PREFERENCE, pinned.decidedBy)
        assertEquals(local1080, assertNotNull(library.planPlayback(pilot, device, { id -> if (id == server.descriptor.id) server else if (id == local.descriptor.id) local else null })).version.version.id, "playback planning honors it")
        assertEquals(emptySet(), library.pinnedVersions(second), "a pin is for one episode, not its show or season")
        assertEquals(emptySet(), library.pinnedVersions(show()))

        clock += MINUTE
        scanBoth()
        assertEquals(setOf(local1080), library.pinnedVersions(episode(1, 1)), "kept across a rescan")
        assertEquals(local1080, library.selectVersion(episode(1, 1), device).best?.version?.id)

        library.pinVersion(pilot, null)
        assertEquals(emptySet(), library.pinnedVersions(pilot))
        assertEquals("items/s1e1-4k", library.selectVersion(pilot, device).best?.version?.location?.path, "Best Version chooses again")
    }

    @Test
    fun aPinnedVersionWhoseSourceIsAwayIsSetAsideButKept() = runTest {
        severance()
        val pilot = episode(1, 1)
        val uhd = library.versions(pilot).single { it.version.location.path == "items/s1e1-4k" }.version.id
        library.pinVersion(pilot, library.versions(pilot).single { it.version.location.path == "items/s1e1-720" }.version.id)
        val sd = library.pinnedVersions(pilot).single()

        server.reachable = false
        library.refreshAvailability(server)
        clock += MINUTE
        library.scan(server)
        val away = library.selectVersion(pilot, device)
        // Falling back by the ranking rules: the copy that can be reached plays.
        assertEquals(local.descriptor.id, away.best?.version?.location?.sourceId)
        assertEquals(SelectionCriterion.AVAILABILITY, away.decidedBy)
        assertEquals(setOf(sd), library.pinnedVersions(pilot), "the choice is not rewritten")
        assertTrue(away.ranked.single { it.version.id == sd }.candidate.availability == Availability.UNAVAILABLE)
        assertTrue(away.ranked.any { it.version.id == uhd }, "versions of a source that is away stay listed")

        server.reachable = true
        library.refreshAvailability(server)
        library.scan(server)
        assertEquals(sd, library.selectVersion(pilot, device).best?.version?.id, "back, it is played again")
    }

    @Test
    fun anotherCutOfAnEpisodeIsNotTheSameTimeline() = runTest {
        file("Severance/Season 1/Severance.S01E01.1080p.mkv")
        server.episode(
            "Severance", 1, 1,
            CatalogVersion("items/s1e1", 9, 1, stream(57, 2160, VideoCodec.HEVC, 10)),
            CatalogVersion("items/s1e1-extended", 9, 1, stream(71, 2160, VideoCodec.HEVC, 10), edition = "Extended"),
        )
        scanBoth()
        val pilot = episode(1, 1)
        val versions = library.versions(pilot).associateBy { it.version.location.path }
        val ordinary = versions.getValue("items/s1e1").version
        val extended = versions.getValue("items/s1e1-extended").version

        // A position in one cut is not the same moment in the other.
        library.stopped(pilot, 30 * MINUTE, 57 * MINUTE, ordinary)
        val state = library.watchState(pilot)
        assertEquals(TimelineMatch.DIFFERENT, Handoff.relation(state, extended))
        // Resuming keeps the cut being watched, even over the pin.
        library.pinVersion(pilot, extended.id)
        val resuming = library.selectVersion(pilot, device)
        assertEquals(ordinary.id, resuming.best?.version?.id)
        assertEquals(extended.id, resuming.ranked.last().version.id, "the other cut ranks below every copy of this one")
        assertEquals(setOf(extended.id), library.pinnedVersions(pilot))
        // Starting over plays the pinned cut.
        assertEquals(extended.id, library.selectVersion(pilot, device, resuming = false).best?.version?.id)
    }

    @Test
    fun withOneVersionThereIsNothingToChoose() = runTest {
        file("Severance/Season 1/Severance.S01E02.1080p.mkv")
        library.scan(local)
        val second = episode(1, 2)
        assertEquals(1, library.versionChoices(second, library.versions(second).map { it.version.id }))
        assertEquals(mapOf(second to 1), library.episodeVersionChoices(show()))
        assertNull(library.selectVersion(second, device).decidedBy)
    }
}
