@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.reflux.android

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.reflux.android.engine.AndroidFetcher
import dev.reflux.android.playback.AndroidCapabilities
import dev.reflux.android.playback.Media3Player
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.playback.AudioPlan
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.PlaybackPlanner
import dev.reflux.core.playback.PlaybackRequest
import dev.reflux.core.playback.PlaybackRequirements
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.playback.SubtitleChoice
import dev.reflux.core.playback.TrackPreferences
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.sources.jellyfin.JellyfinSource
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What Media3 itself says about seeking in real files, by the same calls [dev.reflux.android.playback.Media3Player]
 * makes, with nothing of Reflux between them. On request (`-e refluxSeekProbe true`, optionally `-e refluxSeekFile
 * <path>` for a file on the device). It reads, and changes nothing on the server.
 */
@RunWith(AndroidJUnit4::class)
class SeekabilityOnDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun report(line: String) {
        Log.i("RefluxExecution", "SEEK $line")
    }

    private fun liveSettings(): Map<String, String> {
        val pipe = instrumentation.uiAutomation.executeShellCommand("cat /data/local/tmp/reflux-live.env")
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(pipe).bufferedReader().readLines()
            .filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
    }

    private fun <T> onMain(action: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(action) }
        return result!!.getOrThrow()
    }

    private fun engine(uri: String, headers: Map<String, String>, start: Long?): ExoPlayer = onMain {
        val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setDefaultRequestProperties(headers)
        val source = DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)).createMediaSource(MediaItem.fromUri(Uri.parse(uri)))
        val player = ExoPlayer.Builder(context).build()
        player.volume = 0f
        player.addListener(object : Player.Listener {
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                report("  discontinuity reason=$reason ${oldPosition.positionMs} -> ${newPosition.positionMs}")
            }
        })
        if (start != null) player.setMediaSource(source, start) else player.setMediaSource(source)
        player.playWhenReady = true
        player.prepare()
        player
    }

    private fun ExoPlayer.describe(): String {
        val window = currentTimeline.takeIf { !it.isEmpty }?.getWindow(currentMediaItemIndex, Timeline.Window())
        return "state=$playbackState seekable=$isCurrentMediaItemSeekable window.isSeekable=${window?.isSeekable} dynamic=${window?.isDynamic} " +
            "window.durationMs=${window?.durationMs?.takeIf { it != C.TIME_UNSET }} duration=${duration.takeIf { it != C.TIME_UNSET }} " +
            "command=${isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)} position=$currentPosition error=${playerError?.errorCodeName}"
    }

    private fun awaitReady(player: ExoPlayer, what: String) {
        repeat(600) {
            if (onMain { player.playbackState == Player.STATE_READY || player.playerError != null }) return
            Thread.sleep(100)
        }
        report("  $what never became ready: ${onMain { player.describe() }}")
    }

    private fun sample(player: ExoPlayer, label: String) {
        var at = 0L
        for (wait in listOf(200L, 800L, 2_000L, 3_000L, 6_000L)) {
            Thread.sleep(wait)
            at += wait
            report("  $label +${at}ms position=${onMain { player.currentPosition }} state=${onMain { player.playbackState }}")
        }
    }

    private fun probe(name: String, uri: String, headers: Map<String, String>) {
        report("== $name")
        val played = engine(uri, headers, null)
        try {
            awaitReady(played, name)
            Thread.sleep(1_500)
            report("  after start: ${onMain { played.describe() }}")
            onMain { played.seekTo(120_000) }
            report("  seek issued, at once: ${onMain { played.currentPosition }}")
            sample(played, "seek")
            report("  after seek: ${onMain { played.describe() }}")
        } finally {
            onMain { played.release() }
        }
        val resumed = engine(uri, headers, 120_000)
        try {
            report("  resume at 120000, before ready: ${onMain { resumed.currentPosition }}")
            awaitReady(resumed, "$name resumed")
            report("  resumed: ${onMain { resumed.describe() }}")
            sample(resumed, "resume")
        } finally {
            onMain { resumed.release() }
        }
    }

    private fun asset(name: String): String {
        val file = java.io.File(context.cacheDir, name)
        instrumentation.context.assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
        return Uri.fromFile(file).toString()
    }

    private fun play(name: String, start: Long? = null): Media3Player =
        Media3Player(context, AndroidCapabilities.of(context)).also { it.load(PlaybackRequest(PlaybackTarget(asset(name)), name, startPositionMs = start)) }

    /** The shape of 3 real files, made from a generated clip: Media3 does not follow one SeekHead to another. */
    @Test
    fun aChainedSeekHeadIsReportedAndASeekDoesNotLoseThePlace() {
        val player = play("chained-seekhead.mkv")
        try {
            player.await("the clip to play", 30_000) { it.status == PlayerStatus.PLAYING && it.positionMs > 1_500 }
            assertEquals(false, player.state.value.seekable, "the engine's own answer; if this turns true, Media3 has learned to follow the chain")
            assertTrue(player.state.value.durationMs!! in 59_000L..61_000L, "it does know how long the file is")
            val before = player.state.value.positionMs
            player.seekTo(30_000)
            Thread.sleep(1_000)
            assertTrue(player.state.value.positionMs >= before, "not back to the beginning: ${player.state.value.positionMs} after $before")
        } finally {
            player.close()
        }
    }

    @Test
    fun theSameMediaWithAPlainSeekHeadSeeksAndResumes() {
        val player = play("seekable.mkv")
        try {
            player.await("the clip to play", 30_000) { it.status == PlayerStatus.PLAYING && it.positionMs > 500 }
            assertEquals(true, player.state.value.seekable)
            player.seekTo(30_000)
            player.await("the seek", 30_000) { it.positionMs in 30_000L..36_000L }
        } finally {
            player.close()
        }
        val resumed = play("seekable.mkv", 30_000)
        try {
            resumed.await("the place", 30_000) { it.status == PlayerStatus.PLAYING && it.positionMs in 30_000L..36_000L }
        } finally {
            resumed.close()
        }
    }

    @Test
    fun whatMedia3SaysAboutSeeking() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("not asked for", arguments.getString("refluxSeekProbe") == "true")
        arguments.getString("refluxSeekFile")?.let { probe("file on this device", Uri.fromFile(java.io.File(it)).toString(), emptyMap()) }
        val live = liveSettings()
        val url = live["REFLUX_JELLYFIN_TEST"]
        assumeTrue("no live server named", !url.isNullOrBlank())
        val http = AndroidFetcher(context, "Reflux test")
        val server = JellyfinSource(JellyfinSource.connect(url!!, live.getValue("REFLUX_JELLYFIN_TEST_USER"), live["REFLUX_JELLYFIN_TEST_PASSWORD"].orEmpty(), http, "reflux-seek-probe"), http, "Live")
        val world = DeviceWorld("seek")
        try {
            world.add(server, "live")
            val library = world.environment.library
            library.scan(server)
            val show = library.shows().first { it.item.title == "Moon Knight" }
            val episodes = library.showDetail(show.item.id)!!.seasons.flatMap { it.episodes }
            val versions: List<Pair<String, MediaVersion>> = episodes.map { it.item.title to library.versions(it.item.id).first().version } +
                library.movies().first { it.item.title == "Doctor Strange" }.let { it.item.title to library.versions(it.item.id).first().version }
            for ((title, version) in versions) {
                val target = server.playbackTarget(version.location.path)
                probe("static stream, $title", target.uri, target.headers)
                server.reportStopped(version.location.path, 0)
                server.release(version.location.path)
            }
            // The same file with its container delivered by the server: what a remux would give it.
            val (title, version) = versions.first()
            val device = AndroidCapabilities.of(context)
            val narrowed = device.copy(containers = device.containers - Container.MATROSKA)
            val plan = PlaybackPlanner.plan(PlaybackRequirements(version, TrackPreferences(listOf("en")), server.descriptor.capabilities, subtitle = SubtitleChoice.Off), narrowed)
            report("remux plan: ${plan.delivery} video=${plan.video} audio=${plan.audio} kept=${plan.audio != AudioPlan.NONE}")
            val delivery = server.negotiate(version, plan, narrowed)
            if (delivery != null) probe("server remux, $title", delivery.target.uri, delivery.target.headers)
            server.reportStopped(version.location.path, 0)
            server.release(version.location.path)
        } finally {
            world.close()
        }
    }
}
