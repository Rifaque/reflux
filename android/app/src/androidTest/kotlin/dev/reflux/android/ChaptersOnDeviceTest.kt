package dev.reflux.android

import androidx.media3.common.Label
import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.Chapter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.reflux.android.playback.Media3Player
import dev.reflux.core.playback.PlaybackRequest
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.source.PlaybackTarget
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import dev.reflux.core.playback.Chapter as PlayerChapter

/**
 * Chapters on Android are what Media3 reads out of the file (docs/PLAYBACK.md, section 18); Reflux only turns its `Chapter`
 * metadata into the chapters the player's list and timeline use. Reflux parses no file. A film Media3 describes no
 * chapters of has none.
 */
@RunWith(AndroidJUnit4::class)
class ChaptersOnDeviceTest {
    private val worlds = ArrayList<DeviceWorld>()
    private val players = ArrayList<Media3Player>()

    @After
    fun cleanUp() {
        players.forEach(Media3Player::close)
        worlds.forEach(DeviceWorld::close)
    }

    private fun chapter(startMs: Long, title: String?, hidden: Boolean = false): Chapter =
        Chapter.Builder().setStartTimeMs(startMs).setEndTimeMs(startMs + 60_000).setHidden(hidden).apply { title?.let { setTitle(Label("en", it)) } }.build()

    private val somethingElse = object : Metadata.Entry {}

    @Test
    fun chaptersAreInOrderOfTheirStartWithTheTitlesTheFileGaveOneToAStart() {
        val chapters = Media3Player.chaptersOf(
            listOf(chapter(120_000, "The heist"), chapter(0, "Opening"), chapter(300_000, "Aftermath"), chapter(0, "Opening"), somethingElse),
        )
        assertEquals(listOf(PlayerChapter("Opening", 0), PlayerChapter("The heist", 120_000), PlayerChapter("Aftermath", 300_000)), chapters)
    }

    @Test
    fun aChapterWithNoTitleKeepsItsPlaceAndHiddenOnesAreNotOffered() {
        val chapters = Media3Player.chaptersOf(listOf(chapter(0, null), chapter(60_000, "  "), chapter(90_000, "Hidden", hidden = true), chapter(120_000, "Named")))
        assertEquals(listOf(PlayerChapter(null, 0), PlayerChapter(null, 60_000), PlayerChapter("Named", 120_000)), chapters)
    }

    @Test
    fun nothingDescribedIsNothingOffered() {
        assertTrue(Media3Player.chaptersOf(emptyList()).isEmpty())
        assertTrue(Media3Player.chaptersOf(listOf(somethingElse)).isEmpty())
    }

    private fun played(asset: String, name: String): Media3Player {
        val world = DeviceWorld(name).also { worlds += it }
        val file = File(world.media, asset)
        InstrumentationRegistry.getInstrumentation().context.assets.open(asset).use { input -> file.outputStream().use(input::copyTo) }
        val player = Media3Player(context, world.environment.capabilities).also { players += it }
        player.load(PlaybackRequest(PlaybackTarget(world.tree.open(file.path)), asset))
        player.await("playback to start") { it.status == PlayerStatus.PLAYING && it.positionMs > 200 }
        return player
    }

    private val expected = listOf(PlayerChapter("Opening", 0), PlayerChapter("The heist", 1_000), PlayerChapter("Aftermath", 2_000))

    @Test
    fun aMatroskaFilesChaptersReachThePlayersState() {
        assertEquals(expected, played("chapters.mkv", "chapters-mkv").await("the file's chapters") { it.chapters.isNotEmpty() }.chapters)
    }

    @Test
    fun anMp4FilesChaptersReachThePlayersState() {
        assertEquals(expected, played("chapters.mp4", "chapters-mp4").await("the file's chapters") { it.chapters.isNotEmpty() }.chapters)
    }

    @Test
    fun aFilmWithNoChaptersHasNone() {
        val player = played("clip.mp4", "chapters-none")
        Thread.sleep(1_500)
        assertTrue(player.state.value.chapters.isEmpty(), "none are made up: ${player.state.value.chapters}")
    }
}
