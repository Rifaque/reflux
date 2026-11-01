package dev.reflux.desktop.engine

import dev.reflux.core.model.MediaId
import dev.reflux.core.net.HttpRequest
import dev.reflux.core.net.Redaction
import dev.reflux.core.playback.DisplayCapabilities
import dev.reflux.core.playback.PlanOutcome
import dev.reflux.core.playback.AudioPlan
import dev.reflux.core.playback.PlaybackComparison
import dev.reflux.core.playback.PlaybackController
import dev.reflux.core.playback.PlaybackSurvey
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.playback.SubtitleDirective
import dev.reflux.core.playback.SubtitlePlan
import dev.reflux.core.playback.TrackPreferences
import dev.reflux.core.playback.WatchReporter
import dev.reflux.library.Library
import dev.reflux.library.LibraryDatabase
import dev.reflux.library.net.JdkHttpFetcher
import dev.reflux.library.planPlayback
import dev.reflux.playback.mpv.DesktopCapabilities
import dev.reflux.playback.mpv.MpvPlayer
import dev.reflux.sources.jellyfin.JellyfinSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The real media matrix on the desktop engine: every kind of media on a real server, planned for this client and
 * played once (docs/PLAYBACK.md, section 15). It reads the server's files and changes none of them. On request, with
 * `REFLUX_MATRIX=1` and `--rerun`: it takes minutes, and its conversions are real work for the server.
 */
class MediaMatrixLiveTest {
    private val http = JdkHttpFetcher()
    private val viewer = TrackPreferences(listOf("en"))
    private val device = DesktopCapabilities.of(DisplayCapabilities(1920, 1080))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private object Silent : WatchReporter {
        override fun progress(itemId: MediaId, positionMs: Long, durationMs: Long?) = Unit
        override fun stopped(itemId: MediaId, positionMs: Long, durationMs: Long?) = Unit
    }

    @Test
    fun everyKindOfMediaPlaysAsPlanned() = runBlocking {
        assumeTrue("not asked for", System.getenv("REFLUX_MATRIX") == "1")
        val live = LiveJellyfin.settings()
        assumeTrue("no live server, or no libmpv", live != null && MpvPlayer.isAvailable)
        val server = JellyfinSource(JellyfinSource.connect(live!!.url, live.user, live.password, http, "reflux-matrix-test", "Reflux matrix"), http, "Live test")
        val directory = Files.createTempDirectory("reflux-matrix").also { it.toFile().deleteOnExit() }
        val library = Library(LibraryDatabase.open(directory.resolve("library.db"))) { System.currentTimeMillis() }
        library.addSource(server, "live")
        library.scan(server)
        val episodes = library.shows().flatMap { show -> library.showDetail(show.item.id)?.seasons.orEmpty().flatMap { it.episodes } }
        val versions = (library.movies() + episodes).flatMap { library.versions(it.item.id) }.map { it.version }
            .filter { v -> System.getenv("REFLUX_MATRIX_TITLE").let { it == null || library.item(v.itemId)?.title == it } }

        val cases = PlaybackSurvey.of(versions, viewer, server.descriptor.capabilities, device)
        println("MATRIX ${versions.size} versions, ${cases.size} cases")
        cases.forEach { println("MATRIX case ${it.summary()}") }

        val failed = ArrayList<String>()
        val only = System.getenv("REFLUX_MATRIX_ONLY")
        for (case in PlaybackSurvey.walk(cases).filter { only == null || only in it.media }) {
            val version = case.example
            val path = version.location.path
            val stream = server.playbackTarget(path)
            if (!http.send(HttpRequest(stream.uri, headers = stream.headers + ("Range" to "bytes=0-1023"))).ok) {
                failed += "NOT SERVED ${case.media}"
                continue
            }
            val outcome = try {
                val plan = library.planPlayback(version.itemId, device, { server.takeIf { s -> s.descriptor.id == it } }, versionId = version.id, resume = false, preferences = viewer, subtitle = case.subtitle)
                    ?: error("no plan")
                MpvPlayer.create(device, mapOf("vo" to "null", "ao" to "null"))!!.use { player ->
                    val started = System.nanoTime()
                    PlaybackController(player, version.itemId, Silent, viewer).start(scope, plan.request)
                    val first = withTimeout(90_000) { player.state.first { (it.status == PlayerStatus.PLAYING && it.positionMs > 200) || it.status == PlayerStatus.ERROR } }
                    check(first.status == PlayerStatus.PLAYING) { "failed: ${first.failure} ${first.error}" }
                    val firstFrameMs = (System.nanoTime() - started) / 1_000_000
                    val target = minOf(120_000L, (first.durationMs ?: 240_000L) / 2)
                    val seekFrom = System.nanoTime()
                    player.seekTo(target)
                    withTimeout(90_000) { player.state.first { it.positionMs in (target + 300)..(target + 8_000) && it.status == PlayerStatus.PLAYING } }
                    val seekMs = (System.nanoTime() - seekFrom) / 1_000_000
                    server.reportProgress(path, 0, paused = false)
                    if (plan.request.tracks?.subtitle is SubtitleDirective.Attached) withTimeout(150_000) { player.state.first { it.selectedSubtitleId != null } }
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
                }
            } catch (failure: Exception) {
                "PROBLEM ${failure::class.simpleName}: ${Redaction.redact(failure.message.orEmpty())}"
            } finally {
                server.reportStopped(path, 0)
                server.release(path)
            }
            println("MATRIX walk ${case.media} [${library.item(version.itemId)?.title}]: $outcome")
            if (outcome.startsWith("PROBLEM")) failed += "${case.media}: $outcome"
        }
        assertEquals(emptyList(), failed)
    }
}
