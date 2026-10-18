package dev.reflux.ui.state

import dev.reflux.core.model.MediaId
import dev.reflux.ui.FakePlatform
import dev.reflux.ui.TestWorld
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A work chosen on the launcher's Watch Next row opens in Reflux and plays from the work's own place. */
@OptIn(ExperimentalCoroutinesApi::class)
class ContinueFromOutsideTest {
    @Test
    fun aFilmContinuesFromWhereTheUserIsOverItsDetails() = runTest {
        val world = TestWorld().apply { populate() }
        val platform = FakePlatform(television = true)
        val app = appState(world, platform)
        val heat = world.movie("Heat").item.id
        world.library.stopped(heat, 40 * 60_000L, 170 * 60_000L)
        app.navigator.show(Destination.Shows)

        app.continueFromOutside(heat)
        runCurrent()

        assertEquals(40 * 60_000L, platform.players.single().requests.single().startPositionMs)
        assertEquals(Destination.Details(heat), app.navigator.current)
        assertEquals(Destination.Home, app.navigator.section, "Back from the player and the page goes Home")
        assertEquals(40 * 60_000L, world.library.watchState(heat)?.positionMs, "opening it changes nothing by itself")
    }

    @Test
    fun theNextEpisodeStartsAtTheBeginning() = runTest {
        val world = TestWorld().apply { populate() }
        val platform = FakePlatform(television = true)
        val app = appState(world, platform)
        val episodes = world.episodes("Breaking Bad")
        world.library.stopped(episodes[0].item.id, 46 * 60_000L, 47 * 60_000L)

        app.continueFromOutside(episodes[1].item.id)
        runCurrent()

        val request = platform.players.single().requests.single()
        assertTrue(request.startPositionMs == null || request.startPositionMs == 0L)
        assertEquals(episodes[1].item.id, app.playback?.entry?.item?.id)
    }

    @Test
    fun whatWasPlayingStopsFirst() = runTest {
        val world = TestWorld().apply { populate() }
        val platform = FakePlatform(television = true)
        val app = appState(world, platform)
        app.play(world.movie("Alien").item.id)
        runCurrent()
        val alien = platform.players.single()

        val heat = world.movie("Heat").item.id
        app.continueFromOutside(heat)
        runCurrent()

        assertTrue(alien in platform.released)
        assertEquals(2, platform.players.size)
        assertEquals(heat, app.playback?.entry?.item?.id)
    }

    @Test
    fun aWorkThatIsNoLongerThereSaysSo() = runTest {
        val world = TestWorld().apply { populate() }
        val platform = FakePlatform(television = true)
        val app = appState(world, platform)

        app.continueFromOutside(MediaId("gone"))
        runCurrent()

        assertTrue(platform.players.isEmpty())
        assertNull(app.playback)
        assertEquals(Destination.Home, app.navigator.current)
        assertEquals(Problems.NOTHING_TO_PLAY, app.notice)
    }
}
