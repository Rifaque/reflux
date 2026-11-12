package dev.reflux.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.reflux.library.onlineMetadata
import dev.reflux.ui.state.Destination
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Library depth (M5) as the viewer meets it: what a show is and when it aired, a season's own name and story,
 * episodes pictured by their own stills, an episode's guests, and the two metadata controls (Identify's
 * "Refresh details", and whether Reflux looks things up online).
 */
@OptIn(ExperimentalTestApi::class)
class LibraryDepthUiTest {
    @Test
    fun aShowSaysWhenItAiredAndASeasonWhatItIs() = screen(populated()) { app ->
        app.navigator.open(Destination.Details(app.library.shows().first { it.item.title == "Severance" }.item.id))
        awaitText("The First Season")
        onNodeWithText("Mark leads a team whose memories are split between work and home.").assertExists()
        onNodeWithText("2022  ·  4 episodes").assertExists()
        save("details-show-season")
        // A season whose name is only its number says nothing more than its tab.
        app.navigator.open(Destination.Details(app.library.shows().first { it.item.title == "Breaking Bad" }.item.id))
        awaitText("S1 · E1 · Pilot")
        assertTrue(onAllNodesWithText("5 episodes", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun episodesArePicturedByTheirOwnStills() {
        val platform = FakePlatform()
        screen(populated(), platform) { app ->
            app.navigator.open(Destination.Details(app.library.shows().first { it.item.title == "Breaking Bad" }.item.id))
            awaitText("S1 · E1 · Pilot")
            await("the pilot's still") { platform.artwork.painted("still/Breaking Bad/1/1") != null }
            // With no still of its own, an episode borrows its show's backdrop rather than standing blank.
            app.navigator.open(Destination.Details(app.library.shows().first { it.item.title == "Severance" }.item.id))
            awaitText("S1 · E1 · Good News About Hell")
            await("the show's backdrop for its episodes") { platform.artwork.painted("backdrop/Severance") != null }
            save("details-show-stills")
        }
    }

    @Test
    fun anEpisodesPageSaysWhenItAiredAndWhoIsInIt() = screen(populated()) { app ->
        val pilot = app.library.showDetail(app.library.shows().first { it.item.title == "Breaking Bad" }.item.id)!!.seasons.first().episodes.first()
        app.navigator.open(Destination.Details(pilot.item.id))
        awaitText("Play")
        onAllNodesWithText("Feb 18, 2008", substring = true).onFirst().assertExists()
        onNodeWithText("In which Pilot happens.").assertExists()
        runCatching { onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Guest cast")) }
        awaitText("Guest cast")
        onNodeWithText("Raymond Cruz").assertExists()
        onNodeWithText("Tuco").assertExists()
        save("details-episode")
    }

    @Test
    fun identifyRefreshesWhatAWorkIsMatchedTo() = screen(populated()) { app ->
        app.navigator.open(Destination.Identify(app.library.movies().first { it.item.title == "Heat" }.item.id))
        awaitText("Refresh details")
        // Identify searches as it opens, and its results arrive above the button: a click aimed before they land can
        // land on a result instead, which identifies the work and leaves the page (1 in 25 under load, M7). The
        // button is clicked once the page has stopped moving.
        await("the search to settle") { onAllNodesWithText("Searching…").fetchSemanticsNodes().isEmpty() }
        onNodeWithText("Refresh details").performClick()
        awaitText("Details and artwork refreshed")
    }

    @Test
    fun onlineLookupsCanBeTurnedOffAndBackOn() = screen(populated()) { app ->
        app.navigator.show(Destination.Settings)
        runCatching { onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Online metadata & artwork")) }
        awaitText("Online metadata & artwork")
        assertTrue(app.canIdentify)
        onNodeWithText("Online metadata & artwork").performClick()
        await("lookups to be off") { !app.library.onlineMetadata }
        assertFalse(app.canIdentify, "nothing offers what cannot be done")
        // What was looked up before is all still there.
        assertTrue(app.library.movies().first { it.item.title == "Heat" }.metadata?.overview != null)
        onNodeWithText("Online metadata & artwork").performClick()
        await("lookups to be on") { app.library.onlineMetadata }
        assertTrue(app.canIdentify)
    }

    @Test
    fun withoutAProviderThereIsNoSwitchToTurn() = screen(populated(metadata = false)) { app ->
        app.navigator.show(Destination.Settings)
        runCatching { onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Media Atmosphere")) }
        awaitText("Media Atmosphere")
        assertTrue(onAllNodesWithText("Online metadata & artwork").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aSeasonsStoryFitsATabletAndAPhone() {
        // The Xiaomi Pad 7 on its side (about 1164 by 777 dp) and a phone held upright.
        for ((name, size) in listOf("tablet" to (1164 to 777), "phone" to (412 to 915))) {
            screen(populated(), width = size.first, height = size.second) { app ->
                app.navigator.open(Destination.Details(app.library.shows().first { it.item.title == "Severance" }.item.id))
                awaitText("Severance")
                runCatching { onNode(hasScrollToNodeAction()).performScrollToNode(hasText("The First Season")) }
                awaitText("The First Season")
                onNodeWithText("Mark leads a team whose memories are split between work and home.").assertExists()
                runCatching { onNode(hasScrollToNodeAction()).performScrollToNode(hasText("S1 · E1 · Good News About Hell")) }
                awaitText("S1 · E1 · Good News About Hell")
                save("details-show-season-$name")
            }
        }
    }
}
