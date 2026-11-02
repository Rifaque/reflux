package dev.reflux.android

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.reflux.android.engine.AndroidFetcher
import dev.reflux.android.playback.AndroidCapabilities
import dev.reflux.android.playback.Media3Player
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.net.HttpRequest
import dev.reflux.core.net.Redaction
import dev.reflux.core.playback.AudioChoice
import dev.reflux.core.playback.AudioPlan
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.PlanOutcome
import dev.reflux.core.playback.PlaybackComparison
import dev.reflux.core.playback.PlaybackController
import dev.reflux.core.playback.PlaybackSurvey
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.playback.SubtitleChoice
import dev.reflux.core.playback.SubtitleDirective
import dev.reflux.core.playback.SubtitlePlan
import dev.reflux.core.playback.TrackPreferences
import dev.reflux.core.playback.WatchReporter
import dev.reflux.library.Library
import dev.reflux.library.planPlayback
import dev.reflux.sources.jellyfin.JellyfinSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * The real media matrix on this device's real engine and real capabilities: every kind of media on a real server,
 * planned and played once (docs/PLAYBACK.md, section 15). It uses a private library, and leaves the application's
 * own alone. On request: `RUN_ARGS="-e refluxMatrix true"` through `tools/live/run-on-device.py`. It reads the
 * server's files and changes none of them.
 *
 * There is no surface here, so a picture is not seen. What a subtitle delivers is: the cues the engine hands over.
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

    private inner class Walk(val library: Library, val server: JellyfinSource, val http: AndroidFetcher, val device: DeviceCapabilities, val world: DeviceWorld) {
        val failed = ArrayList<String>()

        /** Plays [version] as planned, once, and reports what happened. */
        suspend fun play(label: String, version: MediaVersion, subtitle: SubtitleChoice = SubtitleChoice.Off, audio: AudioChoice = AudioChoice.Automatic) {
            val path = version.location.path
            val stream = server.playbackTarget(path)
            // One lost request is a result for this case, not the end of the walk.
            val served = runCatching { http.send(HttpRequest(stream.uri, headers = stream.headers + ("Range" to "bytes=0-1023"))).ok }
            if (served.getOrNull() != true) {
                report("walk $label: PROBLEM not served (${served.exceptionOrNull()?.let { it::class.simpleName } ?: "the server refused"})")
                failed += "NOT SERVED $label"
                return
            }
            val player = Media3Player(context, device)
            val outcome = try {
                val plan = library.planPlayback(version.itemId, device, world.sources, versionId = version.id, resume = false, preferences = viewer, audio = audio, subtitle = subtitle)
                    ?: error("no plan")
                val started = System.nanoTime()
                PlaybackController(player, version.itemId, Nothing, viewer).start(scope, plan.request)
                val first = player.await("$label to start", 90_000) { (it.status == PlayerStatus.PLAYING && it.positionMs > 300) || it.status == PlayerStatus.ERROR }
                check(first.status == PlayerStatus.PLAYING) { "failed: ${first.failure} ${first.error}" }
                val firstFrameMs = (System.nanoTime() - started) / 1_000_000
                val target = minOf(120_000L, (first.durationMs ?: 240_000L) / 2)
                val seekFrom = System.nanoTime()
                val refused = first.seekable == false
                if (!refused) {
                    player.seekTo(target)
                    player.await("$label to seek", 90_000) { it.positionMs in (target + 300)..(target + 8_000) && it.status == PlayerStatus.PLAYING }
                }
                val seekMs = (System.nanoTime() - seekFrom) / 1_000_000
                server.reportProgress(path, 0, paused = false)
                if (plan.request.tracks?.subtitle is SubtitleDirective.Attached) player.await("the subtitle", 150_000) { it.selectedSubtitleId != null }
                delay(800)
                val state = player.state.value
                val planned = plan.planned!!
                val compared = PlaybackComparison.of(planned, server.playbackEvidence(path), state)
                val cues = if (planned.subtitles == SubtitlePlan.NONE || planned.subtitles == SubtitlePlan.BURN_IN) "" else {
                    val shown = withTimeoutOrNull(45_000) { player.cues.first { it.isNotEmpty() } }
                    val cue = shown?.first()
                    " cues=" + when {
                        cue == null -> "none in 45 s"
                        cue.bitmap != null -> "bitmap ${cue.bitmap!!.width}x${cue.bitmap!!.height}"
                        else -> "text ${cue.text?.length ?: 0} chars"
                    }
                }
                val now = player.state.value
                val track = now.audioTracks.firstOrNull { it.id == now.selectedAudioId }
                val problems = buildList {
                    if (compared.mismatches.isNotEmpty()) add("mismatch ${compared.mismatches}")
                    if (planned.outcome == PlanOutcome.PLAYS && planned.audio != AudioPlan.NONE && state.audioUnavailable) add("silent ${state.unsupportedAudioCodecs}")
                    if (planned.subtitles !in setOf(SubtitlePlan.NONE, SubtitlePlan.BURN_IN) && state.selectedSubtitleId == null) add("subtitle not selected")
                }
                "${if (problems.isEmpty()) "ok" else "PROBLEM $problems"} first=${firstFrameMs}ms ${if (refused) "seek=engine-unseekable" else "seek=${seekMs}ms"} " +
                    "audio=${track?.codec}/${track?.channels}/${track?.language}$cues reasons=${planned.reasons} ${compared.summary()}"
            } catch (failure: Throwable) {
                "PROBLEM ${failure::class.simpleName}: ${Redaction.redact(failure.message.orEmpty()).take(700)}"
            } finally {
                player.close()
                server.reportStopped(path, 0)
                server.release(path)
            }
            report("walk $label [${library.item(version.itemId)?.title}]: $outcome")
            if (outcome.startsWith("PROBLEM")) failed += "$label: ${outcome.take(160)}"
        }
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
            if (arguments.getString("refluxMatrixPlanOnly") == "true") return@runBlocking

            val walk = Walk(library, server, http, device, world)
            val only = arguments.getString("refluxMatrixOnly")
            // One subtitle of one title, chosen by the viewer: `-e refluxMatrixTitle <title> -e refluxMatrixSubtitle <index>`.
            arguments.getString("refluxMatrixSubtitle")?.toIntOrNull()?.let { index ->
                versions.forEach { walk.play("subtitle $index ${it.stream.subtitles.getOrNull(index)?.let { s -> "${s.format}/${s.language}" }}", it, SubtitleChoice.Explicit(index)) }
                assertEquals(emptyList(), walk.failed)
                return@runBlocking
            }
            for (case in PlaybackSurvey.walk(cases).filter { only == null || only in it.media }) walk.play(case.media, case.example, case.subtitle)
            if (only == null) {
                for ((label, version) in PlaybackSurvey.edges(versions)) walk.play("edge $label", version)
                // The second language of a file with two, chosen by the viewer.
                versions.firstOrNull { v -> v.stream.audio.mapNotNull { it.language }.distinct().size > 1 }?.let { version ->
                    val other = version.stream.audio.indexOfFirst { !it.language.orEmpty().startsWith("en") }
                    walk.play("explicit audio ${version.stream.audio[other].language}", version, audio = AudioChoice.Explicit(other))
                }
                // A work with more than one version: which one Play takes.
                library.movies().plus(episodes).filter { library.versions(it.item.id).size > 1 }.forEach { entry ->
                    val plan = library.planPlayback(entry.item.id, device, world.sources, resume = false, preferences = viewer)
                    report("best ${entry.item.title}: ${plan?.selection?.ranked?.map { "${it.version.stream.video?.codec}/${it.version.stream.video?.bitDepth} ${it.version.stream.video?.height}p ${it.version.stream.audio.firstOrNull()?.codec} playable=${it.playable}" }} chose ${plan?.version?.version?.stream?.video?.codec} ${plan?.planned?.delivery}")
                    plan?.version?.version?.location?.path?.let { server.release(it) }
                }
            }
            assertEquals(emptyList(), walk.failed)
        } finally {
            scope.cancel()
            world.close()
        }
    }
}
