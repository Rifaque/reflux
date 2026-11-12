package dev.reflux.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.core.input.InputModality
import dev.reflux.core.input.InputSignal
import dev.reflux.core.input.Key
import dev.reflux.core.input.SemanticAction
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.design.AdaptiveLayout
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.BrowsedFolder
import dev.reflux.ui.state.Destination
import dev.reflux.ui.state.FolderBrowser
import dev.reflux.ui.state.FolderChoice
import dev.reflux.ui.state.FolderListing
import dev.reflux.ui.state.PlayerMenu
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A television: 960 by 540 whatever its resolution, a remote, and nothing else to operate it with. */
internal const val TV_WIDTH = 960
internal const val TV_HEIGHT = 540
internal const val TV_DENSITY = 2f

@OptIn(ExperimentalTestApi::class)
internal fun television(
    world: TestWorld = populated(),
    platform: FakePlatform = FakePlatform(television = true),
    around: @Composable (@Composable () -> Unit) -> Unit = { it() },
    block: DesktopComposeUiTest.(AppState) -> Unit,
) = screen(world, platform, TV_WIDTH, TV_HEIGHT, density = TV_DENSITY, around = around, block = block)

/**
 * A press on the remote, the way the Android shell delivers one: what a key means is decided first, and a
 * key that means nothing to Reflux is the toolkit's, which activates what has focus. Back does not come as
 * a key at all: the system hands it over as the action it is.
 */
@OptIn(ExperimentalTestApi::class)
internal fun DesktopComposeUiTest.remote(app: AppState, key: Key, times: Int = 1) {
    repeat(times) {
        runOnIdle {
            when {
                key == Key.BACK -> {
                    app.onSignal(InputSignal.KeyPress(key))
                    app.dispatch(SemanticAction.BACK)
                }
                !app.onKey(key) && key == Key.DPAD_CENTER -> app.dispatch(SemanticAction.SELECT)
            }
        }
        waitForIdle()
    }
}

/** A storage with a few folders in it, and a record of what was asked of it. */
private class FakeFolders(private val world: TestWorld) : FolderBrowser {
    var allowed = true
    val added = mutableListOf<String>()
    private val tree = mapOf(
        "storage" to FolderListing(listOf(BrowsedFolder("storage/Download", "Download"), BrowsedFolder("storage/Movies", "Movies")), videos = 0),
        "storage/Movies" to FolderListing(listOf(BrowsedFolder("storage/Movies/Heat (1995)", "Heat (1995)")), videos = 2),
    )

    override suspend fun roots(): List<BrowsedFolder>? = if (allowed) listOf(BrowsedFolder("storage", "Internal storage"), BrowsedFolder("usb", "USB drive")) else null
    override suspend fun list(folder: BrowsedFolder): FolderListing = tree[folder.id] ?: FolderListing(emptyList(), 0)
    override suspend fun add(folder: BrowsedFolder): FolderChoice {
        added += folder.id
        world.file("Heat (1995)/Heat.1995.1080p.mkv")
        return FolderChoice.Added(world.source.descriptor.id)
    }
}

/**
 * The same experience on a television: laid out for the far side of a room and operated with a remote
 * alone, through the same keys, actions, and focus as everywhere else.
 */
@OptIn(ExperimentalTestApi::class)
class TelevisionTest {
    /** Bounds in the canvas the layout sees, not in the pixels it is drawn with. */
    private fun Rect.onCanvas() = Rect(left / TV_DENSITY, top / TV_DENSITY, right / TV_DENSITY, bottom / TV_DENSITY)
    private fun DesktopComposeUiTest.focused(): Rect = onNode(isFocused()).fetchSemanticsNode().boundsInRoot.onCanvas()
    private fun DesktopComposeUiTest.boundsOf(text: String): Rect = onNodeWithText(text).fetchSemanticsNode().boundsInRoot.onCanvas()
    private fun DesktopComposeUiTest.shows(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    private fun DesktopComposeUiTest.has(description: String) = onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
    private fun DesktopComposeUiTest.focusIsOn(matcher: SemanticsMatcher) = onAllNodes(isFocused() and matcher).fetchSemanticsNodes().isNotEmpty()
    private fun DesktopComposeUiTest.awaitFocusOn(what: String, matcher: SemanticsMatcher = hasText(what, substring = true)) =
        await("focus on $what", { runCatching { onNode(isFocused()).fetchSemanticsNode().config.toString() }.getOrNull() }) { focusIsOn(matcher) }

    /** Where a page's content starts: there is no bar above it, the navigation is a rail on the left. */
    private val pageTop = 36f
    private val railRight = 90f
    // The rail widens while it has focus, so it is told by what has focus: a destination.
    private fun DesktopComposeUiTest.inRail() = runCatching { focused().left < railRight && onAllNodes(isFocused() and SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Tab)).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)

    @Test
    fun aTelevisionStartsWithFocusAndSaysNothingAboutItsRemote() = television { app ->
        awaitText("Recently Added")
        // No pointer is ever assumed: the screen opens with its main action in focus.
        assertEquals(InputModality.DIRECTIONAL, app.input.modality)
        assertEquals(ControllerFamily.TV_REMOTE, app.input.controllerFamily)
        awaitFocusOn("Play")
        // A remote's keys say what they do, so the screen does not.
        assertFalse(prompted("Select") || prompted("OK") || prompted("Sources"))
        // Everything is inside the overscan-safe area, and the navigation is a rail on the left of the page.
        val safe = AdaptiveLayout.profile(TV_WIDTH.toFloat(), TV_HEIGHT.toFloat(), true, InputModality.DIRECTIONAL).safeMarginDp
        val play = focused()
        // What has focus is lifted a little towards the viewer; that much it may lean over the margin.
        assertTrue(play.left >= safe - 4f, "inside the safe area: $play")
        val movies = onNode(destination("Movies")).fetchSemanticsNode().boundsInRoot.onCanvas()
        assertTrue(movies.right < railRight && movies.left > 8f && play.left > movies.right, "the rail is beside the page: $movies, $play")
        save("tv-home")

        // The arrows are the remote's: they never turn the television into a keyboard.
        remote(app, Key.ARROW_DOWN)
        assertEquals(InputModality.DIRECTIONAL, app.input.modality)
        assertEquals(ControllerFamily.TV_REMOTE, app.input.controllerFamily)

        // A controller is named in its own terms, as anywhere, and a remote takes the prompts away again.
        runOnIdle { app.onGamepadButton(GamepadButton.DPAD_UP, ControllerFamily.PLAYSTATION) }
        await("the controller's prompts") { prompted("Cross") }
        remote(app, Key.ARROW_DOWN)
        await("the prompts to go") { !prompted("Cross") }
    }

    @Test
    fun theRemoteMovesARowAtATimeAndComesBackToWhereItWas() = television { app ->
        awaitText("Recently Added")
        awaitFocusOn("Play")
        remote(app, Key.ARROW_DOWN)
        // The row with focus sits at the top of the page, its title showing, with the next one in view.
        await("the first row to settle", { boundsOf("Recently Added") }) { abs(boundsOf("Recently Added").top - pageTop) < 14f }
        val first = focused()
        assertTrue(first.top > boundsOf("Recently Added").bottom - 4f && first.bottom < TV_HEIGHT, "the whole card is on screen: $first")
        assertTrue(boundsOf("Ready to Watch").top < TV_HEIGHT - 60f, "the next row is in view")
        save("tv-home-row")

        remote(app, Key.ARROW_DOWN)
        await("the second row to settle") { abs(boundsOf("Ready to Watch").top - pageTop) < 14f }
        assertTrue(focused().top > boundsOf("Ready to Watch").bottom - 4f)
        assertFalse(shows("Recently Added") && boundsOf("Recently Added").bottom > pageTop, "what went up is not left half showing")

        remote(app, Key.ARROW_UP)
        await("the first row to return") { abs(boundsOf("Recently Added").top - pageTop) < 14f }
        remote(app, Key.ARROW_RIGHT, 3)
        val fourth = focused()
        assertTrue(fourth.left > first.left + 300f && abs(fourth.top - first.top) < 6f, "right moves along the row: $first then $fourth")

        // OK opens it; the page starts on its main action.
        remote(app, Key.DPAD_CENTER)
        await("details") { app.navigator.current is Destination.Details }
        awaitFocusOn("Play")
        assertTrue(focused().bottom < TV_HEIGHT - 24f, "the main action is on screen without scrolling")
        // The remote's Back is the way back; an arrow for it on screen would only be one more stop on the way up.
        assertFalse(has("Back"), "no Back on screen on a television")
        save("tv-details")
        remote(app, Key.ARROW_DOWN, 2)
        save("tv-details-episodes")

        // Back returns to the card it was opened from, not to the top of the page.
        remote(app, Key.BACK)
        await("home") { app.navigator.current == Destination.Home }
        await("focus to return", { runCatching { focused() }.getOrNull() }) { runCatching { abs(focused().left - fourth.left) < 6f && abs(focused().top - fourth.top) < 14f }.getOrDefault(false) }
        save("tv-home-returned")

        // Up from the first row is the featured title, and left from there the navigation, on Home.
        remote(app, Key.ARROW_UP)
        await("the hero") { focusIsOn(hasText("Play", substring = true)) || focusIsOn(hasText("Details")) }
        // Left along the hero's buttons, then into the rail, which lands on Home.
        repeat(3) { if (!inRail()) remote(app, Key.ARROW_LEFT) }
        await("the navigation", { runCatching { onNode(isFocused()).fetchSemanticsNode().let { it.config.toString() + " " + it.boundsInRoot } }.getOrNull() }) { inRail() && focusIsOn(destination("Home")) }
        save("tv-navigation")
        // And right from the navigation is the page again.
        remote(app, Key.ARROW_RIGHT)
        await("the page", { runCatching { focused() }.getOrNull() }) { runCatching { focused().left > railRight }.getOrDefault(false) }
    }

    @Test
    fun theSectionsAndSearchWorkFromTheRemote() = television { app ->
        awaitText("Recently Added")
        awaitFocusOn("Play")
        remote(app, Key.ARROW_LEFT)
        await("the navigation") { inRail() }
        runOnIdle { app.navigator.show(Destination.Movies) }
        awaitText("10 titles")
        // Five titles across beside the rail, and the grid starts on its first.
        awaitFocusOn("Alien", hasContentDescription("Alien", substring = true))
        val posters = onAllNodesWithContentDescription("", substring = true).fetchSemanticsNodes().map { it.boundsInRoot.onCanvas() }.filter { it.height > it.width * 1.2f }
        assertEquals(5, posters.count { it.top < posters.minOf { poster -> poster.top } + 12f })
        save("tv-movies")

        // Search starts in its field; what was typed is understood and shown, and down reaches the results.
        runOnIdle { app.navigator.show(Destination.Search) }
        await("the field") { app.textInputActive }
        save("tv-search")
        runOnIdle { app.searchQuery = "4k movies I haven't watched" }
        awaitText("4K")
        save("tv-search-results")
        remote(app, Key.ARROW_DOWN)
        await("a result") { !app.textInputActive && runCatching { focused().height > 100f }.getOrDefault(false) }
        // Up is the field again, and Back from it goes back: a remote has no key just for letting go of a field.
        remote(app, Key.ARROW_UP)
        await("the field") { app.textInputActive }
        // Back from it goes to the navigation, as from any destination's page, and then Home.
        remote(app, Key.BACK)
        await("the navigation, on Search") { focusIsOn(destination("Search")) }
        remote(app, Key.BACK)
        await("home") { app.navigator.current == Destination.Home }
    }

    @Test
    fun theRemoteAloneDrivesThePlayer() {
        val platform = FakePlatform(television = true)
        television(platform = platform) { app ->
            awaitText("Recently Added")
            runOnIdle { app.play(app.library.shows().first { it.item.title == "Breaking Bad" }.item.id) }
            awaitText("S1 · E1 · Pilot")
            val player = platform.players.single()
            val session = assertNotNull(app.playback)
            runOnIdle { player.playTo(95_000) }
            waitForIdle()

            // Nothing here is made to be pointed at: the bar says where playback is and what lies beneath.
            assertFalse(has("Back to library") || has("Volume") || has("Fullscreen") || has("Previous"))
            assertTrue(has("Timeline") && shows("Subtitles") && shows("Episodes"))

            // OK pauses and plays, left and right seek.
            remote(app, Key.DPAD_CENTER)
            assertEquals(PlayerStatus.PAUSED, player.state.value.status)
            awaitText("Paused")
            save("tv-player")
            remote(app, Key.DPAD_CENTER)
            assertEquals(PlayerStatus.PLAYING, player.state.value.status)
            remote(app, Key.ARROW_RIGHT)
            assertEquals(105_000L, player.state.value.positionMs)
            remote(app, Key.ARROW_LEFT)
            assertEquals(95_000L, player.state.value.positionMs)

            // Up takes what is on offer above the controls; loudness is the television's own.
            awaitText("Skip intro")
            remote(app, Key.ARROW_UP)
            assertEquals(150_000L, player.state.value.positionMs)
            assertEquals(100, player.state.value.volume)

            // Down opens everything else, on subtitles; the arrows move through it and OK chooses.
            remote(app, Key.ARROW_DOWN)
            assertEquals(PlayerMenu.SUBTITLES, session.menu)
            awaitText("Spanish · Forced")
            save("tv-player-menu")
            remote(app, Key.ARROW_DOWN)
            remote(app, Key.DPAD_CENTER)
            assertEquals("1", player.state.value.selectedSubtitleId)
            assertNull(session.menu)
            assertEquals(150_000L, player.state.value.positionMs, "moving through a list did not seek")

            // The episode after this one is in the first list, for a remote with no key for it.
            remote(app, Key.ARROW_DOWN)
            remote(app, Key.ARROW_LEFT, 3)
            assertEquals(PlayerMenu.EPISODES, session.menu)
            awaitText("Next")
            save("tv-player-episodes")
            remote(app, Key.DPAD_CENTER)
            await("the next episode") { session.subtitle == "S1 · E2 · Cat's in the Bag..." }

            // Back closes a list first, then leaves.
            remote(app, Key.ARROW_DOWN)
            assertNotNull(session.menu)
            remote(app, Key.BACK)
            assertNull(session.menu)
            assertNotNull(app.playback)
            remote(app, Key.BACK)
            await("the player to close") { app.playback == null }
        }
    }

    @Test
    fun aFolderIsChosenWithTheRemoteAlone() {
        val world = TestWorld()
        val folders = FakeFolders(world)
        television(world, FakePlatform(television = true, folderBrowser = folders)) { app ->
            awaitText("Add another folder")
            awaitFocusOn("Add another folder")

            // Refused: said in words, and nothing opens.
            folders.allowed = false
            remote(app, Key.DPAD_CENTER)
            await("the refusal to be said") { app.notice?.contains("permission") == true }
            assertNull(app.folderPicker)

            // Allowed: the storage, then the folders in it, one step at a time.
            folders.allowed = true
            remote(app, Key.DPAD_CENTER)
            awaitText("Where is your media?")
            awaitFocusOn("Internal storage")
            save("tv-folders-storage")
            remote(app, Key.DPAD_CENTER)
            awaitFocusOn("Use this folder")
            remote(app, Key.ARROW_DOWN, 2)
            awaitFocusOn("Movies")
            // Focus does not leave the picker for the page behind it.
            remote(app, Key.ARROW_DOWN, 3)
            remote(app, Key.ARROW_LEFT)
            awaitFocusOn("Movies")
            remote(app, Key.DPAD_CENTER)
            awaitText("Internal storage  ›  2 videos, 1 folder")
            awaitFocusOn("Use this folder")
            save("tv-folders-folder")

            // Back is one folder up, and out of the picker from the top.
            remote(app, Key.BACK)
            awaitText("Download")
            assertNotNull(app.folderPicker)
            remote(app, Key.ARROW_DOWN, 2)
            remote(app, Key.DPAD_CENTER)
            awaitFocusOn("Use this folder")
            remote(app, Key.DPAD_CENTER)
            await("the folder to be added") { folders.added == listOf("storage/Movies") && app.folderPicker == null }

            // The library builds itself, and focus is on what to do with it.
            awaitText("Recently Added")
            awaitFocusOn("Play")
            save("tv-first-library")

            // The picker can also just be left.
            runOnIdle { app.addFolder() }
            awaitText("Where is your media?")
            remote(app, Key.BACK)
            await("the picker to close") { app.folderPicker == null }
            assertEquals(1, folders.added.size)
        }
    }
}
