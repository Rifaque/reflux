package dev.reflux.playback.mpv

import dev.reflux.core.model.MediaId
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DisplayCapabilities
import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.PlaybackController
import dev.reflux.core.playback.PlaybackFailure
import dev.reflux.core.playback.PlaybackRequest
import dev.reflux.core.playback.PlayerState
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.playback.SubtitleAttachment
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.playback.TrackPreferences
import dev.reflux.core.playback.TrackType
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.WatchReporter
import dev.reflux.core.source.PlaybackTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the real libmpv engine headlessly. Skipped where libmpv or FFmpeg is not installed, except where
 * the environment promises them (`REFLUX_REQUIRE_ENGINE_TESTS=true`, as in CI): there their absence fails.
 */
class MpvEngineTest {
    private lateinit var directory: Path
    private val capabilities = DesktopCapabilities.of(DisplayCapabilities(1920, 1080))

    @BeforeTest
    fun setUp() {
        needs("libmpv not installed", MpvPlayer.isAvailable)
        needs("ffmpeg not installed", TestMedia.ffmpegAvailable)
        directory = Files.createTempDirectory("reflux-mpv")
    }

    private fun needs(what: String, present: Boolean) {
        if (System.getenv("REFLUX_REQUIRE_ENGINE_TESTS") == "true") assertTrue(present, what) else assumeTrue(what, present)
    }

    @AfterTest
    fun tearDown() {
        if (::directory.isInitialized) directory.toFile().deleteRecursively()
    }

    private fun headless() = MpvPlayer.create(capabilities, mapOf("vo" to "null", "ao" to "null"))!!

    private fun MpvPlayer.await(timeoutMs: Long = 10_000, predicate: (PlayerState) -> Boolean): PlayerState =
        runBlocking { withTimeout(timeoutMs) { state.first(predicate) } }

    @Test
    fun probeReadsRealStreamInformation() {
        val file = TestMedia.multiTrackMkv(directory)
        val info = MpvProbe.createOrNull()!!.probe(PlaybackTarget(file.toUri().toString()))
        assertNotNull(info)
        assertEquals(Container.MATROSKA, info.container)
        assertEquals(VideoCodec.H264, info.video?.codec)
        assertEquals(320, info.video?.width)
        assertEquals(240, info.video?.height)
        assertEquals(8, info.video?.bitDepth)
        assertEquals(DynamicRange.SDR, info.video?.dynamicRange)
        assertEquals(listOf(AudioCodec.AAC, AudioCodec.AAC), info.audio.map { it.codec })
        assertEquals(listOf("eng", "jpn"), info.audio.map { it.language })
        assertEquals(6, info.audio[1].channels)
        assertEquals(SubtitleFormat.SRT, info.subtitles.single().format)
        assertTrue(info.durationMs!! in 2_900..3_200, "duration ${info.durationMs}")
    }

    @Test
    fun probeDetectsHdr() {
        val file = TestMedia.hdrHevcMp4(directory)
        val info = MpvProbe.createOrNull()!!.probe(PlaybackTarget(file.toUri().toString()))!!
        assertEquals(Container.MP4, info.container)
        assertEquals(VideoCodec.HEVC, info.video?.codec)
        assertEquals(DynamicRange.HDR10, info.video?.dynamicRange)
        assertEquals(10, info.video?.bitDepth)
    }

    @Test
    fun probeOfMissingFileIsNull() {
        assertNull(MpvProbe.createOrNull()!!.probe(PlaybackTarget(directory.resolve("missing.mkv").toUri().toString()), 3_000))
    }

    @Test
    fun playsToTheEndWithTracksAndExternalSubtitles() {
        val file = TestMedia.multiTrackMkv(directory)
        val external = directory.resolve("Sample (2020).fr.srt")
        Files.writeString(external, "1\n00:00:00,000 --> 00:00:01,000\nBonjour\n")
        headless().use { player ->
            player.load(
                PlaybackRequest(
                    target = PlaybackTarget(file.toUri().toString()),
                    title = "Sample",
                    subtitles = listOf(SubtitleAttachment(PlaybackTarget(external.toUri().toString()), "fr")),
                ),
            )
            val loaded = player.await { it.status == PlayerStatus.PLAYING && it.tracks.isNotEmpty() }
            assertEquals(2, loaded.audioTracks.size)
            assertEquals(2, loaded.subtitleTracks.size)
            assertTrue(loaded.subtitleTracks.any { it.external })
            assertEquals(TrackType.VIDEO, loaded.tracks.first().type)
            val withChapters = player.await { it.chapters.size == 2 }
            assertEquals(listOf("Intro", "Chapter 1"), withChapters.chapters.map { it.title })
            assertEquals(1_000L, withChapters.chapters[1].startMs)

            val ended = player.await { it.status == PlayerStatus.ENDED }
            assertTrue(ended.positionMs >= 2_900)
        }
    }

    @Test
    fun startPositionPauseSeekAndTrackSelection() {
        val file = TestMedia.multiTrackMkv(directory)
        headless().use { player ->
            player.load(PlaybackRequest(PlaybackTarget(file.toUri().toString()), "Sample", startPositionMs = 1_500))
            val started = player.await { it.status == PlayerStatus.PLAYING && it.durationMs != null }
            assertTrue(started.positionMs >= 1_400, "started at ${started.positionMs}")

            player.pause()
            player.await { it.status == PlayerStatus.PAUSED }
            player.seekTo(500)
            player.await { it.status == PlayerStatus.PAUSED && it.positionMs in 400..700 }

            val japanese = player.state.value.audioTracks.single { it.language == "jpn" }
            player.selectAudio(japanese.id)
            player.await { it.selectedAudioId == japanese.id }
            player.selectSubtitle(null)
            player.await { it.selectedSubtitleId == null }
            player.setSpeed(1.5)
            player.await { it.speed == 1.5 }
            // Subtitles can be lifted clear of the player's controls and put back.
            assertEquals(100, MpvPlayer.subtitlePosition(0f))
            assertEquals(82, MpvPlayer.subtitlePosition(0.18f))
            player.setSubtitleClearance(0.18f)
            player.setSubtitleClearance(0f)
            player.setSpeed(1.0)
            player.await { it.speed == 1.0 && it.failure == null }

            player.stop()
            player.await { it.status == PlayerStatus.IDLE }
        }
    }

    @Test
    fun controllerAppliesDefaultTracksAndReportsCompletion() {
        val file = TestMedia.multiTrackMkv(directory)
        val reports = mutableListOf<String>()
        val reporter = object : WatchReporter {
            override fun progress(itemId: MediaId, positionMs: Long, durationMs: Long?) { synchronized(reports) { reports += "progress" } }
            override fun stopped(itemId: MediaId, positionMs: Long, durationMs: Long?) { synchronized(reports) { reports += "stopped:$positionMs" } }
        }
        headless().use { player ->
            runBlocking {
                val controller = PlaybackController(player, MediaId("m"), reporter, TrackPreferences(listOf("ja")))
                controller.start(this, PlaybackRequest(PlaybackTarget(file.toUri().toString()), "Sample"))
                val japanese = withTimeout(10_000) {
                    player.state.first { s -> s.selectedAudioId != null && s.audioTracks.any { it.id == s.selectedAudioId && it.language == "jpn" } }
                }
                // Japanese audio is understood, the only subtitle is English and not forced: subtitles off.
                assertNull(japanese.selectedSubtitleId)
                withTimeout(10_000) { player.state.first { it.status == PlayerStatus.ENDED } }
                withTimeout(5_000) { while (synchronized(reports) { reports.none { it.startsWith("stopped") } }) delay(20) }
                controller.stop()
            }
        }
        assertEquals(1, reports.count { it.startsWith("stopped") })
        assertTrue(reports.first().startsWith("progress"))
    }

    @Test
    fun aLoadedFileSaysWhichTracksArePlayingWithItsTracks() {
        // mpv picks a file's default subtitle as it opens it. The tracks must not be shown without that choice,
        // or whoever decides from them (the controller) decides against a selection that is not there yet.
        val file = TestMedia.defaultSubtitleMkv(directory)
        headless().use { player ->
            player.load(PlaybackRequest(PlaybackTarget(file.toUri().toString()), "Sample"))
            val loaded = player.await { it.tracks.isNotEmpty() && it.status != PlayerStatus.LOADING }
            assertEquals(loaded.subtitleTracks.single().id, loaded.selectedSubtitleId)
            assertEquals(loaded.audioTracks.single().id, loaded.selectedAudioId)
        }
    }

    @Test
    fun aDefaultSubtitleIsTurnedOffWhenTheAudioIsUnderstood() {
        // Found playing a real Jellyfin film: its only subtitle, marked default but not forced, stayed on.
        val file = TestMedia.defaultSubtitleMkv(directory)
        val reporter = object : WatchReporter {
            override fun progress(itemId: MediaId, positionMs: Long, durationMs: Long?) {}
            override fun stopped(itemId: MediaId, positionMs: Long, durationMs: Long?) {}
        }
        headless().use { player ->
            runBlocking {
                val controller = PlaybackController(player, MediaId("m"), reporter, TrackPreferences(listOf("en")))
                controller.start(this, PlaybackRequest(PlaybackTarget(file.toUri().toString()), "Sample"))
                withTimeout(10_000) { player.state.first { it.status == PlayerStatus.PLAYING && it.selectedAudioId != null } }
                delay(1_000)
                assertNull(player.state.value.selectedSubtitleId)
                controller.stop()
            }
        }
    }

    @Test
    fun aStreamThatStopsBeforeItsEndIsNotWatchedToTheEnd() {
        // Found with a real Jellyfin server cut off mid-episode: mpv reports a stream that stops arriving as the end
        // of the file, and the episode was recorded as watched.
        val file = TestMedia.truncatedMkv(directory)
        val stops = mutableListOf<Long>()
        val reporter = object : WatchReporter {
            override fun progress(itemId: MediaId, positionMs: Long, durationMs: Long?) {}
            override fun stopped(itemId: MediaId, positionMs: Long, durationMs: Long?) { stops += positionMs }
        }
        headless().use { player ->
            runBlocking {
                val controller = PlaybackController(player, MediaId("m"), reporter, TrackPreferences(listOf("en")))
                controller.start(this, PlaybackRequest(PlaybackTarget(file.toUri().toString()), "Sample", startPositionMs = 4_000))
                val end = withTimeout(30_000) { player.state.first { it.status == PlayerStatus.ENDED || it.status == PlayerStatus.ERROR } }
                assertEquals(PlayerStatus.ERROR, end.status)
                assertEquals(PlaybackFailure.UNREACHABLE, end.failure)
                assertTrue(end.positionMs < 20_000, "position ${end.positionMs}")
                controller.stop()
                assertEquals(listOf(end.positionMs), stops)
            }
        }
    }

    @Test
    fun failuresSayWhatWentWrong() {
        // A file that is not there, and a file that is not media: the two failures users actually meet.
        headless().use { player ->
            player.load(PlaybackRequest(PlaybackTarget(directory.resolve("gone.mkv").toUri().toString()), "Gone"))
            val missing = player.await { it.status == PlayerStatus.ERROR }
            assertEquals(PlaybackFailure.UNREACHABLE, missing.failure)
            assertNotNull(missing.error, "the engine's own wording is kept for diagnostics")
        }
        headless().use { player ->
            val notMedia = Files.write(directory.resolve("notes.mkv"), ByteArray(4096) { 7 })
            player.load(PlaybackRequest(PlaybackTarget(notMedia.toUri().toString()), "Notes"))
            assertEquals(PlaybackFailure.UNSUPPORTED, player.await { it.status == PlayerStatus.ERROR }.failure)

            // A failure is not sticky: the same engine plays the next thing asked of it.
            player.load(PlaybackRequest(PlaybackTarget(TestMedia.multiTrackMkv(directory).toUri().toString()), "Fine"))
            val playing = player.await { it.status == PlayerStatus.PLAYING }
            assertNull(playing.failure)
            assertNull(playing.error)
        }
    }

    @Test
    fun engineErrorCodesMapToFailures() {
        assertEquals(PlaybackFailure.UNREACHABLE, MpvPlayer.failureOf(LibMpv.ERROR_LOADING_FAILED))
        assertEquals(PlaybackFailure.UNSUPPORTED, MpvPlayer.failureOf(LibMpv.ERROR_UNKNOWN_FORMAT))
        assertEquals(PlaybackFailure.UNSUPPORTED, MpvPlayer.failureOf(LibMpv.ERROR_NOTHING_TO_PLAY))
        assertEquals(PlaybackFailure.OUTPUT, MpvPlayer.failureOf(LibMpv.ERROR_VO_INIT_FAILED))
        assertEquals(PlaybackFailure.OUTPUT, MpvPlayer.failureOf(LibMpv.ERROR_AO_INIT_FAILED))
        assertEquals(PlaybackFailure.UNKNOWN, MpvPlayer.failureOf(-1))
    }

    @Test
    fun anEndingKeepsItsDurationSoThatItCountsAsWatched() {
        // The engine withdraws a file's duration as it unloads the file, around the moment it reports the
        // ending. Whichever comes first, the ended state must still say how long the file was.
        repeat(5) {
            headless().use { player ->
                player.load(PlaybackRequest(PlaybackTarget(TestMedia.multiTrackMkv(directory).toUri().toString()), "Short"))
                player.await { it.status == PlayerStatus.PLAYING && it.durationMs != null }
                player.seekTo(2_600)
                val ended = player.await { it.status == PlayerStatus.ENDED }
                assertNotNull(ended.durationMs, "the ended state knows the duration")
                assertEquals(ended.durationMs, ended.positionMs)
                Thread.sleep(250) // let the unload notifications arrive
                val later = player.state.value
                assertEquals(PlayerStatus.ENDED, later.status)
                assertEquals(ended.durationMs to ended.positionMs, later.durationMs to later.positionMs, "and nothing that follows takes it away")
            }
        }
    }
}
