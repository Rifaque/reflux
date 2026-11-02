package dev.reflux.android

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.reflux.android.playback.AndroidCapabilities
import dev.reflux.android.playback.AndroidMediaProber
import dev.reflux.android.playback.Media3Player
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaLocation
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.SourceId
import dev.reflux.core.model.StreamInfoOrigin
import dev.reflux.core.model.VersionId
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.PlanOutcome
import dev.reflux.core.playback.PlaybackAssessor
import dev.reflux.core.playback.PlaybackComparison
import dev.reflux.core.playback.PlaybackPlanner
import dev.reflux.core.playback.PlaybackRequest
import dev.reflux.core.playback.PlaybackRequirements
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.playback.TrackPreferences
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.source.PlaybackTarget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals

/**
 * The formats the real library lacks, through the whole local path on this device: what the prober reads, what the
 * assessor and planner say, and what Media3 then does (docs/PLAYBACK.md, section 16). A local file has no server, so
 * a plan this device cannot play is `CANNOT_PLAY` or plays without sound; that is the answer, and the engine is asked
 * anyway, to see whether the plan was too strict or too generous. Logged with the tag `RefluxExecution`.
 */
@RunWith(AndroidJUnit4::class)
class FormatFixtureOnDeviceTest {
    private data class Facts(val codec: VideoCodec, val depth: Int, val height: Int, val range: DynamicRange, val audio: AudioCodec)

    private val fixtures = mapOf(
        "av1-1080p-8bit.mkv" to Facts(VideoCodec.AV1, 8, 1080, DynamicRange.SDR, AudioCodec.AAC),
        "av1-1080p-10bit.mkv" to Facts(VideoCodec.AV1, 10, 1080, DynamicRange.SDR, AudioCodec.AAC),
        "av1-2160p-10bit.mkv" to Facts(VideoCodec.AV1, 10, 2160, DynamicRange.SDR, AudioCodec.AAC),
        "hevc-2160p-hdr10.mkv" to Facts(VideoCodec.HEVC, 10, 2160, DynamicRange.HDR10, AudioCodec.AAC),
        "hevc-2160p-hlg.mkv" to Facts(VideoCodec.HEVC, 10, 2160, DynamicRange.HLG, AudioCodec.AAC),
        "truehd.mkv" to Facts(VideoCodec.H264, 8, 180, DynamicRange.SDR, AudioCodec.TRUEHD),
        "vp9-1080p.webm" to Facts(VideoCodec.VP9, 8, 1080, DynamicRange.SDR, AudioCodec.OPUS),
    )

    @Test
    fun eachMissingFormatIsReadPlannedAndPlayedAsPlanned() = runBlocking {
        val device = AndroidCapabilities.of(context)
        val prober = AndroidMediaProber(context)
        val problems = ArrayList<String>()
        for ((name, expected) in fixtures) {
            val file = File(context.cacheDir, name)
            InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { input -> file.outputStream().use(input::copyTo) }
            val target = PlaybackTarget(android.net.Uri.fromFile(file).toString())
            val stream = prober.probe(target) ?: error("$name: no probe")
            val video = stream.video
            val facts = Facts(video?.codec ?: VideoCodec.UNKNOWN, video?.bitDepth ?: 0, video?.height ?: 0, video?.dynamicRange ?: DynamicRange.SDR, stream.audio.firstOrNull()?.codec ?: AudioCodec.UNKNOWN)
            // The platform's extractor drops a track it cannot read (TrueHD), and on older systems leaves out a profile, so
            // depth is unknown (VP9 on Fire OS 8): those are unknowns, and the engine says the rest at playback.
            val audioUnread = expected.audio == AudioCodec.TRUEHD && stream.audio.isEmpty()
            val sameFacts = facts.copy(depth = if (video?.bitDepth == null) expected.depth else facts.depth, audio = if (audioUnread) expected.audio else facts.audio) == expected
            if (!sameFacts) problems += "$name: read as $facts"
            val version = MediaVersion(VersionId(name), MediaId(name), MediaLocation(SourceId("local"), name), file.length(), 0, stream, StreamInfoOrigin.PROBE)
            val assessment = PlaybackAssessor.assess(stream, device)
            val plan = PlaybackPlanner.plan(PlaybackRequirements(version, TrackPreferences(listOf("en"))), device)
            val player = Media3Player(context, device)
            val actual = try {
                player.load(PlaybackRequest(target, name))
                val state = withTimeoutOrNull(45_000) { player.state.first { (it.status == PlayerStatus.PLAYING && it.positionMs > 200) || it.status == PlayerStatus.ERROR } } ?: player.state.value
                val playing = state.status == PlayerStatus.PLAYING
                when {
                    plan.outcome != PlanOutcome.CANNOT_PLAY && !playing -> problems += "$name: planned ${plan.outcome}, the engine did not play: ${state.failure} ${state.error}"
                    plan.outcome == PlanOutcome.PLAYS && plan.audio != dev.reflux.core.playback.AudioPlan.NONE && state.audioUnavailable -> problems += "$name: planned with sound, played silent"
                    audioUnread && !(state.audioUnavailable && state.unsupportedAudioCodecs.isNotEmpty()) -> problems += "$name: a track the probe could not see was not reported at playback"
                    playing && plan.outcome != PlanOutcome.CANNOT_PLAY -> PlaybackComparison.of(plan, null, state).let { if (it.mismatches.isNotEmpty()) problems += "$name: ${it.summary()}" }
                }
                "${state.status} decoder=${state.videoDecoder} silent=${state.audioUnavailable} unsupportedAudio=${state.unsupportedAudioCodecs} failure=${state.failure}"
            } finally {
                player.close()
            }
            Log.i(
                "RefluxExecution",
                "FIXTURE $name: read ${facts.codec}/${facts.depth} ${video?.profile} ${video?.width}x${video?.height} ${facts.range} + ${facts.audio}; assessed ${assessment.issues}; " +
                    "planned ${plan.outcome} ${plan.delivery} video ${plan.video} audio ${plan.audio} range ${plan.range} reasons ${plan.reasons} unverified ${plan.unverified}; actual $actual",
            )
        }
        assertEquals(emptyList(), problems)
    }
}
