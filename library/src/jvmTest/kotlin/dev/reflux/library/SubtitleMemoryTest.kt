package dev.reflux.library

import dev.reflux.core.model.Episode
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.Movie
import dev.reflux.core.model.SourceId
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.AudioStream
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.DisplayCapabilities
import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.playback.SubtitleCapabilities
import dev.reflux.core.playback.SubtitleChoice
import dev.reflux.core.playback.SubtitleDirective
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.playback.SubtitleMode
import dev.reflux.core.playback.SubtitlePreference
import dev.reflux.core.playback.SubtitleRenderer
import dev.reflux.core.playback.SubtitleStream
import dev.reflux.core.playback.Support
import dev.reflux.core.playback.TrackPreferences
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoDecoderSupport
import dev.reflux.core.playback.VideoStream
import dev.reflux.sources.local.LocalFolderSource
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

/**
 * A viewer's subtitle choice for a work is theirs (docs/planning/M10-PLAYER-SPEC.md, section 9; docs/WATCH_STATE.md,
 * "Profiles"): kept per profile in `profile_setting`, as a description of the track, found again in whichever version or
 * episode is played next, and never read for or written to another profile.
 */
class SubtitleMemoryTest {
    private val root: Path = Files.createTempDirectory("reflux-subtitle-memory")
    private var clock = 1_790_000_000_000L
    private val library = Library(testDatabase()) { clock }
    private val source = LocalFolderSource(root)
    private val sources = { id: SourceId -> source.takeIf { it.descriptor.id == id } }
    private val drawable = SubtitleCapabilities(mapOf(SubtitleFormat.SRT to SubtitleRenderer(Support.SUPPORTED, Support.SUPPORTED)))
    private val device = DeviceCapabilities(
        videoDecoders = listOf(VideoDecoderSupport(VideoCodec.H264, 3840, 2160, 10, hardware = true)),
        audioDecoders = AudioCodec.entries.toSet(),
        display = DisplayCapabilities(3840, 2160, setOf(DynamicRange.SDR)),
        containers = Container.entries.toSet(),
        subtitleFormats = SubtitleFormat.entries.toSet(),
        subtitles = drawable,
    )
    private val english = TrackPreferences(listOf("en"), SubtitleMode.OFF)

    init {
        library.addSource(source, root.toString())
    }

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
    }

    private fun file(relative: String) = root.resolve(relative).also { it.parent.createDirectories(); it.writeBytes(ByteArray(8)) }

    private fun stream(vararg subtitles: SubtitleStream) = StreamInfo(
        Container.MATROSKA, VideoStream(VideoCodec.H264, 1920, 1080, 8), listOf(AudioStream(AudioCodec.AAC, 2, "en", default = true)), subtitles.toList(),
    )

    private val fr = SubtitleStream(SubtitleFormat.SRT, "fr")
    private val es = SubtitleStream(SubtitleFormat.SRT, "es")
    private val en = SubtitleStream(SubtitleFormat.SRT, "en")

    private suspend fun movie(): Movie {
        file("Heat (1995)/Heat.1995.1080p.mkv")
        library.scan(source)
        return library.movies().single().item as Movie
    }

    private suspend fun show(): List<Episode> {
        file("Severance/Season 1/Severance.S01E01.1080p.mkv")
        file("Severance/Season 1/Severance.S01E02.1080p.mkv")
        library.scan(source)
        return library.showDetail(library.shows().single().item.id)!!.seasons.flatMap { it.episodes }.map { it.item as Episode }
    }

    private fun probe(item: MediaId, info: StreamInfo) = library.recordProbe(library.versions(item).single().version.id, info)

    @Test
    fun whatAViewerChoseForAMovieIsTheChoiceTheNextPlayStartsFrom() = runTest {
        val heat = movie()
        probe(heat.id, stream(en, fr, es))
        assertEquals(SubtitleDirective.Off, library.planPlayback(heat.id, device, sources, preferences = english)?.request?.tracks?.subtitle, "nothing chosen: automatic, which shows none here")

        library.rememberSubtitle(heat.id, SubtitlePreference(false, "es"))
        val plan = assertNotNull(library.planPlayback(heat.id, device, sources, preferences = english))
        assertEquals(SubtitleDirective.Embedded(2), plan.request.tracks?.subtitle, "Spanish, wherever the file numbers it")

        // The viewer's own choice, made now, outranks what they chose before.
        val now = assertNotNull(library.planPlayback(heat.id, device, sources, preferences = english, subtitle = SubtitleChoice.Explicit(1)))
        assertEquals(SubtitleDirective.Embedded(1), now.request.tracks?.subtitle)
    }

    @Test
    fun offIsRemembered() = runTest {
        val heat = movie()
        probe(heat.id, stream(en, fr))
        val french = TrackPreferences(listOf("en"), SubtitleMode.ALWAYS)
        assertNotNull(library.planPlayback(heat.id, device, sources, preferences = french)).request.tracks?.subtitle.let { assert(it != SubtitleDirective.Off) { "always: a subtitle" } }
        library.rememberSubtitle(heat.id, SubtitlePreference.OFF)
        assertEquals(SubtitleDirective.Off, library.planPlayback(heat.id, device, sources, preferences = french)?.request?.tracks?.subtitle)
    }

    @Test
    fun aChoiceMadeInOneEpisodeCarriesThroughTheShow() = runTest {
        val episodes = show()
        probe(episodes[0].id, stream(en, fr))
        probe(episodes[1].id, stream(es, fr, en))
        library.rememberSubtitle(episodes[0].id, SubtitlePreference(false, "fr"))
        assertEquals(SubtitleDirective.Embedded(1), library.planPlayback(episodes[0].id, device, sources, preferences = english)?.request?.tracks?.subtitle)
        assertEquals(SubtitleDirective.Embedded(1), library.planPlayback(episodes[1].id, device, sources, preferences = english)?.request?.tracks?.subtitle, "the next episode numbers its tracks differently")
        assertEquals(SubtitlePreference(false, "fr"), library.subtitlePreference(episodes[1].id))
    }

    @Test
    fun whereTheDescriptionMatchesNothingTheChoiceIsTheAutomaticOneAndTheMemoryStays() = runTest {
        val heat = movie()
        probe(heat.id, stream(en))
        library.rememberSubtitle(heat.id, SubtitlePreference(false, "de"))
        assertEquals(SubtitleDirective.Off, library.planPlayback(heat.id, device, sources, preferences = english)?.request?.tracks?.subtitle)
        assertEquals(SubtitlePreference(false, "de"), library.subtitlePreference(heat.id), "a version with German may come")
    }

    @Test
    fun anotherProfileNeitherReadsNorWritesIt() = runTest {
        val heat = movie()
        probe(heat.id, stream(en, fr, es))
        val main = library.activeProfile()
        library.rememberSubtitle(heat.id, SubtitlePreference(false, "fr"))

        val sam = library.createProfile("Sam")
        library.switchProfile(sam)
        assertNull(library.subtitlePreference(heat.id), "Sam never chose")
        assertEquals(SubtitleDirective.Off, library.planPlayback(heat.id, device, sources, preferences = english)?.request?.tracks?.subtitle, "so Sam's play is automatic, not the first viewer's")
        library.rememberSubtitle(heat.id, SubtitlePreference(false, "es"))
        assertEquals(SubtitlePreference(false, "es"), library.subtitlePreference(heat.id))

        // A playback belongs to the profile that started it: read and written for it, whoever is watching by then.
        assertEquals(SubtitlePreference(false, "fr"), library.subtitlePreference(heat.id, main))
        assertEquals(SubtitleDirective.Embedded(1), library.planPlayback(heat.id, device, sources, preferences = english, profile = main)?.request?.tracks?.subtitle)
        library.rememberSubtitle(heat.id, SubtitlePreference.OFF, main)
        assertEquals(SubtitlePreference(false, "es"), library.subtitlePreference(heat.id), "Sam's is untouched")
    }

    @Test
    fun startingAProfileOverTakesItsChoicesAndOnlyItsOwn() = runTest {
        val heat = movie()
        library.rememberSubtitle(heat.id, SubtitlePreference(false, "fr"))
        val sam = library.createProfile("Sam")
        library.rememberSubtitle(heat.id, SubtitlePreference(false, "es"), sam)
        library.resetProfile(sam)
        assertNull(library.subtitlePreference(heat.id, sam))
        assertEquals(SubtitlePreference(false, "fr"), library.subtitlePreference(heat.id))
        library.deleteProfile(sam)
        assertEquals(SubtitlePreference(false, "fr"), library.subtitlePreference(heat.id))
    }

    @Test
    fun aDamagedValueIsForgottenNotGuessedAt() = runTest {
        val heat = movie()
        library.setProfileSetting(SubtitlePreference.key(heat.id.value), "banana")
        assertNull(library.subtitlePreference(heat.id))
        probe(heat.id, stream(en, fr))
        assertNotNull(library.planPlayback(heat.id, device, sources, preferences = english))
    }
}
