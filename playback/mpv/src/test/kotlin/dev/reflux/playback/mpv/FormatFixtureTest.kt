package dev.reflux.playback.mpv

import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaLocation
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.SourceId
import dev.reflux.core.model.StreamInfoOrigin
import dev.reflux.core.model.VersionId
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DisplayCapabilities
import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.PlanOutcome
import dev.reflux.core.playback.PlaybackAssessor
import dev.reflux.core.playback.PlaybackComparison
import dev.reflux.core.playback.PlaybackPlanner
import dev.reflux.core.playback.PlaybackRequest
import dev.reflux.core.playback.PlaybackRequirements
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.playback.TrackPreferences
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.source.PlaybackTarget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The formats the real library lacks, through the whole local path on the desktop engine: what the probe reads, what
 * the assessor and planner say, and what libmpv then does (docs/PLAYBACK.md, section 16). Needs FFmpeg and libmpv.
 */
class FormatFixtureTest {
    private val expected = mapOf(
        "av1-1080p-8bit.mkv" to Facts(VideoCodec.AV1, 8, 1080, DynamicRange.SDR, AudioCodec.AAC),
        "av1-1080p-10bit.mkv" to Facts(VideoCodec.AV1, 10, 1080, DynamicRange.SDR, AudioCodec.AAC),
        "av1-2160p-10bit.mkv" to Facts(VideoCodec.AV1, 10, 2160, DynamicRange.SDR, AudioCodec.AAC),
        "hevc-2160p-hdr10.mkv" to Facts(VideoCodec.HEVC, 10, 2160, DynamicRange.HDR10, AudioCodec.AAC),
        "hevc-2160p-hlg.mkv" to Facts(VideoCodec.HEVC, 10, 2160, DynamicRange.HLG, AudioCodec.AAC),
        "truehd.mkv" to Facts(VideoCodec.H264, 8, 180, DynamicRange.SDR, AudioCodec.TRUEHD),
        "vp9-1080p.webm" to Facts(VideoCodec.VP9, 8, 1080, DynamicRange.SDR, AudioCodec.OPUS),
    )

    data class Facts(val codec: VideoCodec, val depth: Int, val height: Int, val range: DynamicRange, val audio: AudioCodec)

    @Test
    fun eachMissingFormatIsReadPlannedAndPlayedAsPlanned() = runBlocking {
        assumeTrue("needs FFmpeg and libmpv", TestMedia.ffmpegAvailable && MpvPlayer.isAvailable)
        val directory = Files.createTempDirectory("reflux-fixtures").also { it.toFile().deleteOnExit() }
        val probe = MpvProbe.createOrNull()!!
        val device = DesktopCapabilities.of(DisplayCapabilities(1920, 1080))
        val problems = ArrayList<String>()
        for (fixture in FormatFixtures.all) {
            val file = FormatFixtures.make(fixture, directory)
            val target = PlaybackTarget(file.toUri().toString())
            val stream: StreamInfo = probe.probe(target) ?: error("${fixture.name}: no probe")
            val video = stream.video
            val facts = Facts(video?.codec ?: VideoCodec.UNKNOWN, video?.bitDepth ?: 0, video?.height ?: 0, video?.dynamicRange ?: DynamicRange.SDR, stream.audio.firstOrNull()?.codec ?: AudioCodec.UNKNOWN)
            if (facts != expected.getValue(fixture.name)) problems += "${fixture.name}: read as $facts"
            val version = MediaVersion(VersionId(fixture.name), MediaId(fixture.name), MediaLocation(SourceId("local"), fixture.name), Files.size(file), 0, stream, StreamInfoOrigin.PROBE)
            val assessment = PlaybackAssessor.assess(stream, device)
            val plan = PlaybackPlanner.plan(PlaybackRequirements(version, TrackPreferences(listOf("en"))), device)
            val outcome = MpvPlayer.create(device, mapOf("vo" to "null", "ao" to "null"))!!.use { player ->
                player.load(PlaybackRequest(target, fixture.name))
                val state = withTimeoutOrNull(60_000) { player.state.first { (it.status == PlayerStatus.PLAYING && it.positionMs > 200) || it.status == PlayerStatus.ERROR } }
                    ?: player.state.value
                val compared = PlaybackComparison.of(plan, null, state)
                if (plan.outcome != PlanOutcome.CANNOT_PLAY && state.status != PlayerStatus.PLAYING) problems += "${fixture.name}: planned ${plan.outcome} and the engine did not play: ${state.failure} ${state.error}"
                if (compared.mismatches.isNotEmpty()) problems += "${fixture.name}: ${compared.summary()}"
                "${state.status} decoder=${state.videoDecoder} audio=${state.audioTracks.firstOrNull { it.id == state.selectedAudioId }?.codec}"
            }
            println(
                "FIXTURE ${fixture.name} (${fixture.question}): read ${facts.codec}/${facts.depth} ${video?.profile} L${video?.level} ${video?.width}x${video?.height} ${facts.range} + ${facts.audio}; " +
                    "assessed ${assessment.issues}; planned ${plan.outcome} ${plan.delivery} video ${plan.video} audio ${plan.audio} range ${plan.range} reasons ${plan.reasons} unverified ${plan.unverified}; actual $outcome",
            )
        }
        assertEquals(emptyList(), problems)
        assertTrue(FormatFixtures.all.size == expected.size)
    }
}
