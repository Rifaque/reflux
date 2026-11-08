package dev.reflux.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.reflux.android.playback.Media3Player
import dev.reflux.core.playback.PlaybackRequest
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.playback.SubtitleAttachment
import dev.reflux.core.playback.TrackType
import dev.reflux.core.source.PlaybackTarget
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A subtitle line that is already showing at the place a seek lands is drawn there, not only from the next line's
 * start (seen once on the Phone 2a in M6: a single 0:01–0:30 cue, English chosen mid-film, a seek to 0:12 drew
 * nothing). Both ways the viewer gets there: subtitles chosen before playing, and chosen while playing.
 */
@RunWith(AndroidJUnit4::class)
class SubtitleSeekOnDeviceTest {
    private val worlds = ArrayList<DeviceWorld>()
    private val players = ArrayList<Media3Player>()

    @After
    fun cleanUp() {
        players.forEach(Media3Player::close)
        worlds.forEach(DeviceWorld::close)
    }

    private fun prepared(name: String): Pair<Media3Player, PlaybackRequest> {
        val world = DeviceWorld(name).also { worlds += it }
        val clip = world.clip("clip.mp4")
        // One long line, as in the file the phone played: 0:01 to 0:30.
        val subtitle = File(clip.parentFile, "clip.en.srt").apply { writeText("1\n00:00:01,000 --> 00:00:30,000\nA line that lasts half a minute.\n\n") }
        val player = Media3Player(context, world.environment.capabilities).also { players += it }
        return player to PlaybackRequest(
            PlaybackTarget(world.tree.open(clip.path)), "Clip",
            subtitles = listOf(SubtitleAttachment(PlaybackTarget(world.tree.open(subtitle.path)), "en")),
        )
    }

    /** The cue's text on screen within [ms], or null. */
    private fun Media3Player.cueWithin(ms: Long): String? = runBlocking {
        withTimeoutOrNull(ms) { cues.first { it.isNotEmpty() } }?.joinToString { it.text.toString() }
    }

    private fun Media3Player.chooseEnglish() {
        val track = await("the subtitle track") { state -> state.subtitleTracks.any { it.type == TrackType.SUBTITLE } }.subtitleTracks.first()
        selectSubtitle(track.id)
        await("the subtitle to be selected") { it.selectedSubtitleId == track.id }
    }

    @Test
    fun aLineAlreadyShowingIsDrawnAfterASeekIntoIt() {
        val (player, request) = prepared("seek-chosen-before")
        player.load(request)
        player.await("playback to start") { it.status == PlayerStatus.PLAYING && it.positionMs > 300 }
        player.chooseEnglish()
        assertNotNull(player.cueWithin(5_000), "the line is drawn from its start")
        player.seekTo(40_000)
        player.await("past the line") { it.positionMs > 40_000 }
        assertTrue(player.cues.value.isEmpty(), "nothing is drawn after the line ends")
        player.seekTo(12_000)
        player.await("back inside the line") { it.positionMs in 12_000..20_000 && it.status == PlayerStatus.PLAYING }
        assertNotNull(player.cueWithin(3_000), "a line already showing at 0:12 is drawn there")
    }

    @Test
    fun aLineIsDrawnWhenSubtitlesAreChosenInsideItAndAfterASeek() {
        val (player, request) = prepared("seek-chosen-during")
        player.load(request)
        player.await("playback to start") { it.status == PlayerStatus.PLAYING && it.positionMs > 300 }
        player.seekTo(10_000)
        player.await("inside the line") { it.positionMs in 10_000..20_000 && it.status == PlayerStatus.PLAYING }
        // Chosen mid-line, as on the phone: the line already running is drawn without waiting for another.
        player.chooseEnglish()
        assertNotNull(player.cueWithin(3_000), "a line already running is drawn when subtitles are turned on")
        player.seekTo(20_000)
        player.await("further into the line") { it.positionMs in 20_000..28_000 && it.status == PlayerStatus.PLAYING }
        assertNotNull(player.cueWithin(3_000), "and after a seek inside it")
    }
}
