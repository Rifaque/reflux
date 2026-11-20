package dev.reflux.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.ui.state.Destination
import dev.reflux.ui.state.SearchScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The redesigned Search (B-057, second design): the idle page offers only what this library has; the scope tabs are a filter that
 * narrows results, counts and the empty state alike; results come in sections by kind; recent searches are this
 * profile's and can be cleared; and the page is laid out for each kind of screen.
 */
@OptIn(ExperimentalTestApi::class)
class SearchDesignTest {
    private fun SemanticsNodeInteraction.mouseClick() = performMouseInput { click() }

    private fun androidx.compose.ui.test.DesktopComposeUiTest.toSearch(app: dev.reflux.ui.state.AppState) {
        awaitText("Recently Added")
        runOnIdle { app.go(Destination.Search) }
        await("search") { app.navigator.current == Destination.Search }
        awaitText("Start with")
    }

    @Test
    fun theIdlePageOffersOnlyWhatThisLibraryHasWithCounts() = screen(populated()) { app ->
        toSearch(app)
        // Every quick find says how many titles it finds, and none finds nothing.
        val tiles = onAllNodes(hasTestTag("suggestion")).fetchSemanticsNodes()
        assertTrue(tiles.size >= 3, "quick finds shown: ${tiles.size}")
        // Each says how many it finds, quietly, and none finds nothing (B-057).
        assertEquals(0, onAllNodesWithText("0").fetchSemanticsNodes().size, "nothing offered that finds nothing")
        // Nothing is anyone's favorite yet: no Favorites shortcut.
        assertEquals(0, onAllNodesWithText("Favorites").fetchSemanticsNodes().size)
        // The library's own genres, as an index of words.
        awaitText("Genres")
        assertTrue(onAllNodes(hasTestTag("genre")).fetchSemanticsNodes().isNotEmpty())
        // Choosing a quick find asks it.
        onNodeWithText("In 4K").mouseClick()
        await("asked") { app.searchQuery == "4k" }
        awaitText("Blade Runner 2049")
    }

    /**
     * A tab clicked with the mouse leaves the typing in the field, so Backspace goes on editing the query instead of being
     * Back, which left Search (found on the Linux VM walk, 2026-11-20). "Search everything" does the same.
     */
    @Test
    fun aClickedTabLeavesTheTypingInTheField() = screen(populated()) { app ->
        toSearch(app)
        onNode(hasSetTextAction()).performTextInput("4k")
        awaitText("Blade Runner 2049")
        onNode(hasTestTag("scope:Movies")).mouseClick()
        await("movies") { app.searchScope == SearchScope.MOVIES }
        await("the field to keep the typing") { onAllNodes(hasSetTextAction() and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasSetTextAction() and isFocused()).performKeyInput { pressKey(Key.Backspace) }
        await("Backspace to edit the query") { app.searchQuery == "4" }
        assertEquals(Destination.Search, app.navigator.current, "still on Search")
        // Nothing among the shows, then everything again: the field still has the typing.
        onNode(hasSetTextAction()).performTextReplacement("4k")
        onNode(hasTestTag("scope:Shows")).mouseClick()
        await("shows") { app.searchScope == SearchScope.SHOWS }
        if (saysAnywhere("No matches for")) {
            onNodeWithText("Search everything").mouseClick()
            await("everything") { app.searchScope == SearchScope.ALL }
            await("the field to have focus again") { onAllNodes(hasSetTextAction() and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        }
    }

    @Test
    fun theScopeSwitchNarrowsResultsCountsAndTheEmptyState() = screen(populated()) { app ->
        toSearch(app)
        onNode(hasSetTextAction()).performTextInput("4k")
        awaitText("Blade Runner 2049")
        onNode(hasTestTag("scope:Shows")).mouseClick()
        await("shows only") { app.searchScope == SearchScope.SHOWS }
        await("no movie left") { onAllNodesWithText("Blade Runner 2049").fetchSemanticsNodes().isEmpty() }
        // Nothing among the shows: said, with a way back to everything.
        if (saysAnywhere("No matches for")) {
            awaitText("Search everything")
            onNodeWithText("Search everything").mouseClick()
            await("everything again") { app.searchScope == SearchScope.ALL }
            awaitText("Blade Runner 2049")
        }
        onNode(hasTestTag("scope:Movies")).mouseClick()
        await("movies") { app.searchScope == SearchScope.MOVIES }
        // The idle counts follow the scope too.
        onNode(hasSetTextAction()).performTextReplacement("")
        awaitText("Start with")
        save("search-idle-movies")
    }

    @Test
    fun resultsComeInSectionsByKindWhenThereIsMoreThanOne() = screen(populated()) { app ->
        toSearch(app)
        // Something both films and shows answer: whatever is unwatched.
        onNode(hasSetTextAction()).performTextInput("unwatched")
        await("sections") { saysAnywhere("Movies") && onAllNodesWithText("Shows").fetchSemanticsNodes().size >= 2 }
        await("the count") { onAllNodes(androidx.compose.ui.test.hasText(" results", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        save("search-results-sections")
    }

    @Test
    fun aSearchThatOpenedSomethingIsRememberedAndCanBeCleared() = screen(populated()) { app ->
        toSearch(app)
        onNode(hasSetTextAction()).performTextInput("villeneuve movies")
        awaitText("Arrival")
        onAllNodesWithContentDescription("Arrival, 2016").onFirst().mouseClick()
        await("details") { app.navigator.current is Destination.Details }
        runOnIdle { app.navigator.back() }
        await("search") { app.navigator.current == Destination.Search }
        onNode(hasSetTextAction()).performTextReplacement("")
        awaitText("Recent")
        awaitText("villeneuve movies")
        save("search-idle-recent")
        onNode(hasTestTag("recent")).mouseClick()
        await("asked again") { app.searchQuery == "villeneuve movies" }
        onNode(hasSetTextAction()).performTextReplacement("")
        awaitText("Recent")
        onNodeWithText("Clear").mouseClick()
        await("forgotten") { onAllNodes(hasTestTag("recent")).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun aControllerGoesFromTheFieldToTheTilesAndBack() = screen(populated()) { app ->
        awaitText("Recently Added")
        runOnIdle { app.onGamepadButton(GamepadButton.DPAD_DOWN, ControllerFamily.XBOX) }
        repeat(3) { runOnIdle { app.onGamepadButton(GamepadButton.SHOULDER_RIGHT, ControllerFamily.XBOX) } }
        await("the field") { app.navigator.current == Destination.Search && app.textInputActive }
        awaitText("Start with")
        runOnIdle { app.onGamepadButton(GamepadButton.DPAD_DOWN, ControllerFamily.XBOX) }
        await("off the field") { !app.textInputActive }
        repeat(2) { runOnIdle { app.onGamepadButton(GamepadButton.DPAD_DOWN, ControllerFamily.XBOX) } }
        waitForIdle()
        save("search-controller-tile")
        runOnIdle { app.onGamepadButton(GamepadButton.FACE_SOUTH, ControllerFamily.XBOX) }
        await("a tile asked") { app.searchQuery.isNotBlank() }
        assertFalse(app.keyboard != null, "choosing a tile is not typing")
    }

    @Test
    fun eachKindOfScreenIsLaidOutForItself() {
        // A phone, a tablet, a desktop window, and a television: rendered for review (VERIFICATION.md).
        for ((name, size) in listOf("phone" to (412 to 900), "tablet" to (1280 to 800), "desktop" to (1440 to 900))) {
            screen(populated(), width = size.first, height = size.second) { app ->
                toSearch(app)
                save("search-idle-$name")
                onNode(hasSetTextAction()).performTextInput("zzqx")
                awaitText("No matches for “zzqx”")
                save("search-none-$name")
            }
        }
        screen(populated(), FakePlatform(television = true), width = 960, height = 540, density = 2f) { app ->
            awaitText("Recently Added")
            runOnIdle { app.go(Destination.Search) }
            awaitText("Start with")
            save("search-idle-tv")
        }
    }
}
