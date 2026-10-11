package dev.reflux.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.core.input.InputModality
import dev.reflux.core.input.InputSignal
import dev.reflux.core.model.Availability
import dev.reflux.core.playback.PlaybackFailure
import dev.reflux.core.source.MediaSource
import dev.reflux.ui.state.Destination
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Renders the real screens over a real library and checks what the user would see.
 *
 * Run with `-Preflux.screenshots=<dir>` to also save each screen as a PNG for visual review.
 */
@OptIn(ExperimentalTestApi::class)
class ScreensTest {
    @Test
    fun firstRunAsksForAFolderAndNothingElse() = screen(TestWorld().also { it.library.removeSource(it.source.descriptor.id) }) {
        awaitText("Point Reflux at your media")
        onNodeWithText("Choose a folder").assertIsDisplayed()
        save("first-run")
    }

    @Test
    fun homeIsUsefulWithoutConfiguration() = screen(
        populated().also { world ->
            world.library.stopped(world.movie("Heat").item.id, 62 * 60_000L, 170 * 60_000L)
            world.library.setFavorite(world.movie("Arrival").item.id, true)
            val episodes = world.episodes("Breaking Bad")
            world.clock += 60_000
            world.library.setWatched(episodes[0].item.id, true)
            world.clock += 60_000
            world.library.progress(episodes[1].item.id, 12 * 60_000L, 47 * 60_000L)
        },
    ) {
        awaitText("Continue Watching")
        onNodeWithText("Resume").assertIsDisplayed()
        save("home")
        // Rows further down are composed as they scroll into view.
        val page = onAllNodes(hasScrollToNodeAction()).onFirst()
        page.performScrollToNode(hasText("Recently Added"))
        page.performScrollToNode(hasText("Favorites"))
        save("home-rows")
    }

    @Test
    fun homeLooksFinishedBeforeAnyArtworkExists() = screen(populated(metadata = false), FakePlatform(artwork = false)) {
        awaitText("Recently Added")
        onAllNodesWithText("Heat").fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
        save("home-without-artwork")
    }

    @Test
    fun movieDetailsExplainTheVersionThatWillPlay() = screen(populated()) { app ->
        val world = app.library
        app.navigator.open(Destination.Details(world.movies().first { it.item.title == "Heat" }.item.id))
        awaitText("Versions")
        onNodeWithText("Best here").assertExists()
        onNodeWithText("Directed by Michael Mann").assertExists()
        onNodeWithText("Play").assertExists()
        save("details-movie")
    }

    @Test
    fun showDetailsContinueWhereTheUserLeftOff() = screen(
        populated().also { world ->
            val episodes = world.episodes("Breaking Bad")
            world.library.setWatched(episodes[0].item.id, true)
            world.clock += 60_000
            world.library.progress(episodes[1].item.id, 12 * 60_000L, 47 * 60_000L)
        },
    ) { app ->
        app.navigator.open(Destination.Details(app.library.shows().first { it.item.title == "Breaking Bad" }.item.id))
        awaitText("Continue S1 · E2")
        onNodeWithText("S1 · E2 · Cat's in the Bag...").assertExists()
        onNodeWithText("Season 2").performClick()
        awaitText("S2 · E1 · Seven Thirty-Seven")
        onNodeWithText("Season 1").performClick()
        awaitText("S1 · E1 · Pilot")
        save("details-show")
    }

    @Test
    fun searchShowsHowTheQueryWasUnderstood() = screen(populated()) { app ->
        app.navigator.show(Destination.Search)
        awaitText("Try")
        onNode(hasSetTextAction()).performTextInput("4k science fiction")
        awaitText("Science Fiction")
        awaitText("Blade Runner 2049")
        assertEquals(0, onAllNodesWithText("Heat").fetchSemanticsNodes().size)
        save("search")
    }

    @Test
    fun sourcesPanelListsSourcesAndOffersToAddOne() = screen(populated()) { app ->
        awaitText("Recently Added")
        onNodeWithContentDescription("Media sources").performClick()
        awaitText("Add folder")
        onNodeWithText("Media").assertExists()
        save("sources")
    }

    @Test
    fun controllerInputMorphsTheLayoutInsteadOfSwitchingModes() = screen(populated()) { app ->
        awaitText("Recently Added")
        assertEquals(InputModality.POINTER, app.input.modality)
        app.onSignal(InputSignal.GamepadButtonPress(GamepadButton.DPAD_DOWN, ControllerFamily.PLAYSTATION))
        waitForIdle()
        assertEquals(InputModality.DIRECTIONAL, app.input.modality)
        // The same screen, the same content: only density and focus visuals change.
        onNodeWithText("Recently Added").assertExists()
        assertEquals(Destination.Home, app.navigator.current)
        save("home-controller")
    }

    @Test
    fun playerShowsTitleTransportAndTrackControls() {
        val platform = FakePlatform()
        screen(populated(), platform) { app ->
            awaitText("Recently Added")
            app.play(app.library.shows().first { it.item.title == "Breaking Bad" }.item.id)
            awaitText("S1 · E1 · Pilot")
            // Controls stay up while paused; while playing they fade away.
            platform.players.single().apply { playTo(95_000) }.pause()
            waitForIdle()
            onNodeWithContentDescription("Play").assertExists()
            onNodeWithContentDescription("Next episode").assertExists()
            onNodeWithText("Skip intro").assertExists()
            onNodeWithContentDescription("Subtitles").performClick()
            awaitText("Off")
            onNodeWithText("English").assertExists()
            save("player")
        }
    }

    @Test
    fun aFolderWithoutMediaSaysSoAndOffersAnother() = screen(TestWorld().also { runBlocking { it.pipeline.refresh() } }) {
        awaitText("Nothing to watch here yet")
        onNodeWithText("Add another folder").assertIsDisplayed()
        save("empty-folder")
    }

    @Test
    fun aLibraryWhoseOnlySourceIsAwayStaysBrowsable() = screen(
        populated().also { world ->
            world.registry.register(object : MediaSource by world.source {
                override suspend fun availability() = Availability.UNAVAILABLE
            })
            runBlocking { world.library.refreshAvailability(world.registry.resolve(world.source.descriptor.id)!!) }
        },
    ) { app ->
        awaitText("Recently Added")
        onAllNodesWithText("Offline").fetchSemanticsNodes().let { assertTrue(it.isNotEmpty(), "cards say they cannot be played right now") }
        save("home-offline")
        app.navigator.open(Destination.Details(app.library.movies().first { it.item.title == "Heat" }.item.id))
        awaitText("Versions")
        save("details-offline")
    }

    @Test
    fun theMovieGridFillsTheWindow() = screen(populated()) { app ->
        app.navigator.show(Destination.Movies)
        awaitText("10 titles")
        save("movies")
    }

    @Test
    fun searchSuggestsAndAdmitsWhenNothingMatches() = screen(populated()) { app ->
        app.navigator.show(Destination.Search)
        awaitText("Try")
        save("search-empty")
        onNode(hasSetTextAction()).performTextInput("zzzz")
        awaitText("Nothing in your library matches “zzzz”.")
        save("search-no-results")
    }

    @Test
    fun aWorkThatLeftTheLibrarySaysSo() {
        val world = populated()
        screen(world) { app ->
            val heat = world.movie("Heat").item.id
            app.navigator.open(Destination.Details(heat))
            awaitText("Versions")
            world.library.versions(heat).forEach { Files.delete(world.source.resolve(it.version.location.path)) }
            runOnIdle { app.refresh() }
            awaitText("This is no longer in your library")
            save("details-gone")
            onNodeWithText("Back").performClick()
            awaitText("Recently Added")
        }
    }

    @Test
    fun noticesFloatAboveThePage() = screen(populated()) { app ->
        awaitText("Recently Added")
        // Time is stepped by hand, so the notice is looked at while it is up rather than on its way out.
        mainClock.autoAdvance = false
        val message = "Reflux can't open “Backup Drive”. Check that it is connected and that you are allowed to read it."
        runOnUiThread { app.notify(message) }
        mainClock.advanceTimeBy(1_000)
        onNodeWithText(message).assertIsDisplayed()
        save("notice")
        mainClock.advanceTimeBy(8_000)
        onNodeWithText(message).assertDoesNotExist()
    }

    @Test
    fun thePlayerExplainsWhatComesNextAndWhatWentWrong() {
        val platform = FakePlatform()
        screen(populated(), platform) { app ->
            awaitText("Recently Added")
            runOnIdle { app.play(app.library.shows().first { it.item.title == "Breaking Bad" }.item.id) }
            awaitText("S1 · E1 · Pilot")
            val player = platform.players.single()
            player.apply { playTo(26 * 60_000L) }.pause()
            onNodeWithContentDescription("Chapters").performClick()
            awaitText("Part Two")
            save("player-chapters")

            player.end()
            awaitText("Play now")
            mainClock.autoAdvance = false
            save("player-up-next")
            mainClock.autoAdvance = true
            onNodeWithText("Cancel").performClick()

            player.fail("unrecognized file format", PlaybackFailure.UNSUPPORTED)
            awaitText("PLAYBACK STOPPED")
            onNodeWithText("This file isn't in a format Reflux can play.").assertExists()
            save("player-error")
        }
    }
}
