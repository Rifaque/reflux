package dev.reflux.android

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.reflux.android.engine.AndroidFetcher
import dev.reflux.android.playback.AndroidCapabilities
import dev.reflux.android.playback.Media3Player
import dev.reflux.core.model.MediaId
import dev.reflux.core.net.HttpRequest
import dev.reflux.core.net.Redaction
import dev.reflux.core.playback.AudioPlan
import dev.reflux.core.playback.PlanOutcome
import dev.reflux.core.playback.PlaybackComparison
import dev.reflux.core.playback.PlaybackController
import dev.reflux.core.playback.PlaybackSurvey
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.playback.SubtitleDirective
import dev.reflux.core.playback.SubtitlePlan
import dev.reflux.core.playback.TrackPreferences
import dev.reflux.core.playback.WatchReporter
import dev.reflux.library.planPlayback
import dev.reflux.sources.jellyfin.JellyfinSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * The real media matrix on this device's real engine and real capabilities: every kind of media on a real server,
 * planned and played once (docs/PLAYBACK.md, section 15). It uses a private library, and leaves the application's
 * own alone. On request: `RUN_ARGS="-e refluxMatrix true"` through `tools/live/run-on-device.py`. It reads the
 * server's files and changes none of them.
 */
@RunWith(AndroidJUnit4::class)
class MediaMatrixOnDeviceTest {
    private val viewer = TrackPreferences(listOf("en"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private object Nothing : WatchReporter {
        override fun progress(itemId: MediaId, positionMs: Long, durationMs: Long?) = Unit
        override fun stopped(itemId: MediaId, positionMs: Long, durationMs: Long?) = Unit
    }

    private fun report(line: String) {
        Log.i("RefluxExecution", "MATRIX $line")
    }

    private fun liveSettings(): Map<String, String> {
        val pipe = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cat /data/local/tmp/reflux-live.env")
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(pipe).bufferedReader().readLines()
            .filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
    }

    @Test
    fun everyKindOfMediaPlaysAsPlanned() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("not asked for", arguments.getString("refluxMatrix") == "true")
        val live = liveSettings()
        val url = live["REFLUX_JELLYFIN_TEST"]
        assumeTrue("no live server named", !url.isNullOrBlank())
        val http = AndroidFetcher(context, "Reflux test")
        val server = JellyfinSource(JellyfinSource.connect(url!!, live.getValue("REFLUX_JELLYFIN_TEST_USER"), live["REFLUX_JELLYFIN_TEST_PASSWORD"].orEmpty(), http, "reflux-matrix-device"), http, "Live")
        val world = DeviceWorld("matrix")
        try {
            world.add(server, "live")
            val library = world.environment.library
            library.scan(server)
            val episodes = library.shows().flatMap { show -> library.showDetail(show.item.id)?.seasons.orEmpty().flatMap { it.episodes } }
            val versions = (library.movies() + episodes).flatMap { library.versions(it.item.id) }.map { it.version }
                .filter { v -> arguments.getString("refluxMatrixTitle").let { it == null || library.item(v.itemId)?.title == it } }
            val device = AndroidCapabilities.of(context)

            val cases = PlaybackSurvey.of(versions, viewer, server.descriptor.capabilities, device)
            report("${versions.size} versions, ${cases.size} cases")
            cases.forEach { report("case ${it.summary()}") }

            val failed = ArrayList<String>()
            val only = arguments.getString("refluxMatrixOnly")
            val planOnly = arguments.getString("refluxMatrixPlanOnly") == "true"
            for (case in PlaybackSurvey.walk(cases).filter { !planOnly && (only == null || only in it.media) }) {
                val version = case.example
                val path = version.location.path
                val stream = server.playbackTarget(path)
                if (!http.send(HttpRequest(stream.uri, headers = stream.headers + ("Range" to "bytes=0-1023"))).ok) {
                    failed += "NOT SERVED ${case.media}"
                    continue
                }
                val player = Media3Player(context, device)
                val outcome = try {
                    val plan = library.planPlayback(version.itemId, device, world.sources, versionId = version.id, resume = false, preferences = viewer, subtitle = case.subtitle)
                        ?: error("no plan")
                    val started = System.nanoTime()
                    PlaybackController(player, version.itemId, Nothing, viewer).start(scope, plan.request)
                    val first = player.await("${case.media} to start", 90_000) { (it.status == PlayerStatus.PLAYING && it.positionMs > 300) || it.status == PlayerStatus.ERROR }
                    check(first.status == PlayerStatus.PLAYING) { "failed: ${first.failure} ${first.error}" }
                    val firstFrameMs = (System.nanoTime() - started) / 1_000_000
                    val target = minOf(120_000L, (first.durationMs ?: 240_000L) / 2)
                    val seekFrom = System.nanoTime()
                    player.seekTo(target)
                    player.await("${case.media} to seek", 90_000) { it.positionMs in (target + 300)..(target + 8_000) && it.status == PlayerStatus.PLAYING }
                    val seekMs = (System.nanoTime() - seekFrom) / 1_000_000
                    server.reportProgress(path, 0, paused = false)
                    if (plan.request.tracks?.subtitle is SubtitleDirective.Attached) player.await("the subtitle", 150_000) { it.selectedSubtitleId != null }
                    delay(800)
                    val state = player.state.value
                    val planned = plan.planned!!
                    val compared = PlaybackComparison.of(planned, server.playbackEvidence(path), state)
                    val problems = buildList {
                        if (compared.mismatches.isNotEmpty()) add("mismatch ${compared.mismatches}")
                        if (planned.outcome == PlanOutcome.PLAYS && planned.audio != AudioPlan.NONE && state.audioUnavailable) add("silent ${state.unsupportedAudioCodecs}")
                        if (planned.subtitles !in setOf(SubtitlePlan.NONE, SubtitlePlan.BURN_IN) && state.selectedSubtitleId == null) add("subtitle not selected")
                    }
                    "${if (problems.isEmpty()) "ok" else "PROBLEM $problems"} first=${firstFrameMs}ms seek=${seekMs}ms ${compared.summary()}"
                } catch (failure: Throwable) {
                    "PROBLEM ${failure::class.simpleName}: ${Redaction.redact(failure.message.orEmpty()).take(700)}"
                } finally {
                    player.close()
                    server.reportStopped(path, 0)
                    server.release(path)
                }
                report("walk ${case.media} [${library.item(version.itemId)?.title}]: $outcome")
                if (outcome.startsWith("PROBLEM")) failed += "${case.media}: ${outcome.take(160)}"
            }
            assertEquals(emptyList(), failed)
        } finally {
            scope.cancel()
            world.close()
        }
    }
}
