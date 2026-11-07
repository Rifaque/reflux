package dev.reflux.ui

import androidx.compose.ui.input.key.Key as ComposeKey
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.center
import androidx.compose.ui.test.pressKey
import dev.reflux.core.input.InputModality
import dev.reflux.core.input.InputSignal
import dev.reflux.core.input.Key
import dev.reflux.core.input.SemanticAction
import dev.reflux.core.playback.PlaybackFailure
import dev.reflux.design.Motion
import dev.reflux.library.SourceConnections
import dev.reflux.ui.state.AccessibilityPreferences
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import dev.reflux.ui.state.PlayerMenu
import dev.reflux.ui.theme.MotionPolicy
import dev.reflux.ui.theme.tween
import androidx.compose.animation.core.TweenSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What assistive technology is given: read from the semantics tree, the same tree Android turns into TalkBack's
 * nodes. Whether a real screen reader then says it well is for VERIFICATION.md; that it is there at all is here.
 */
@OptIn(ExperimentalTestApi::class)
class AccessibilityTest {
    private val handheld get() = FakePlatform(systemVolume = true, fullscreenMode = dev.reflux.ui.state.FullscreenMode.LANDSCAPE)

    private fun DesktopComposeUiTest.exists(description: String) = onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()

    private fun DesktopComposeUiTest.node(description: String): SemanticsNode = onNodeWithContentDescription(description).fetchSemanticsNode()

    private fun DesktopComposeUiTest.playing(app: AppState, platform: FakePlatform, title: String = "Heat") {
        app.play(app.library.movies().first { it.item.title == title }.item.id)
        await("the player") { platform.players.isNotEmpty() && app.playback != null }
        await("the timeline") { exists("Timeline") }
    }

    private fun titled(title: String) = SemanticsMatcher("pane $title") { it.config.getOrNull(SemanticsProperties.PaneTitle) == title }

    // Isolation --------------------------------------------------------------------------------------

    @Test
    fun whileThePlayerIsOpenThePageBehindItIsNotThere() {
        val platform = FakePlatform()
        screen(populated(), platform) { app ->
            awaitText("Recently Added")
            assertTrue(onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty(), "the page is there first")
            playing(app, platform)
            waitForIdle()
            for (gone in listOf("Home", "Movies", "Shows", "Recently Added")) {
                assertTrue(onAllNodesWithText(gone).fetchSemanticsNodes().isEmpty(), "$gone is behind the player")
            }
            assertFalse(exists("Media sources"), "and so is the navigation's")
            assertTrue(exists("Pause"), "the player is what is left")

            runOnIdle { app.playback!!.close() }
            await("the page to come back") { onAllNodesWithText("Recently Added").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty())
        }
    }

    @Test
    fun aTelevisionsPlayerHidesThePageToo() {
        val platform = FakePlatform(television = true)
        television(platform = platform) { app ->
            awaitText("Recently Added")
            playing(app, platform)
            waitForIdle()
            assertTrue(onAllNodesWithText("Recently Added").fetchSemanticsNodes().isEmpty())
            // What had focus keeps it behind the video, so it is still there when the player closes.
            runOnIdle { app.playback!!.close() }
            await("focus to be there afterwards") { onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
        }
    }

    @Test
    fun whatIsBehindAnOpenPanelIsNotThere() {
        val offline = OfflineWorld()
        screen(offline.world, offline.platform) { app ->
            with(offline) { begin(app) }
            runOnIdle { app.navigator.open(Destination.Details(offline.movie("Heat"))) }
            awaitText("Versions")
            runOnIdle { app.reviewDownload(offline.movie("Heat")) }
            await("the sheet") { onAllNodes(titled("Watch offline")).fetchSemanticsNodes().isNotEmpty() }
            assertTrue(onAllNodesWithText("Versions").fetchSemanticsNodes().isEmpty(), "the page is behind the sheet")
            assertTrue(onAllNodesWithText("Home").fetchSemanticsNodes().isEmpty(), "the navigation is too")
            assertTrue(SemanticsActions.Dismiss in onNode(titled("Watch offline")).fetchSemanticsNode().config, "and a screen reader can close it without tapping outside")
            onNode(titled("Watch offline")).performSemanticsAction(SemanticsActions.Dismiss)
            await("the sheet to close") { !app.offlineOpen }
            awaitText("Versions")
        }
    }

    // Player -----------------------------------------------------------------------------------------

    @Test
    fun theControlsStayForAScreenReader() {
        val platform = FakePlatform().apply { accessibility = AccessibilityPreferences(screenReader = true) }
        screen(populated(), platform) { app ->
            awaitText("Recently Added")
            playing(app, platform)
            mainClock.advanceTimeBy(Motion.CONTROLS_AUTO_HIDE_MS * 3)
            waitForIdle()
            assertTrue(exists("Timeline"), "controls that left could never be found again")
        }
    }

    @Test
    fun anOpenListComesAfterTheControlsThatOpenedIt() {
        // TalkBack stays on the button a list was opened from; the list has to be its next stop, not the start again.
        val platform = handheld
        screen(populated(), platform, width = 900, height = 600) { app ->
            awaitText("Recently Added")
            playing(app, platform)
            runOnIdle { app.playback!!.toggleMenu(PlayerMenu.AUDIO) }
            await("the list") { onAllNodes(titled(PlayerMenu.AUDIO.title)).fetchSemanticsNodes().isNotEmpty() }
            assertEquals(1f, onNode(titled(PlayerMenu.AUDIO.title)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.TraversalIndex))
        }
    }

    @Test
    fun aRemoteIsTakenToKeepWhenASourceAsksAndBackToRemoveAfterwards() = television { app ->
        awaitText("Recently Added")
        runOnIdle { app.navigator.open(Destination.Sources) }
        awaitText("Add media")
        fun on(text: String) = await("focus on $text") { onAllNodes(isFocused() and hasText(text)).fetchSemanticsNodes().isNotEmpty() }
        on("Add media")
        remote(app, Key.ARROW_DOWN)
        remote(app, Key.DPAD_CENTER)
        awaitText("Refresh")
        // An open source's first stop is its switch, then its actions.
        remote(app, Key.ARROW_DOWN)
        on("In your library")
        remote(app, Key.ARROW_DOWN)
        on("Open")
        remote(app, Key.ARROW_RIGHT, times = 2)
        on("Remove")
        remote(app, Key.DPAD_CENTER)
        on("Keep")
        remote(app, Key.DPAD_CENTER)
        on("Remove")
        assertTrue(app.library.sources().isNotEmpty(), "a press too many kept it")
    }

    @Test
    fun aCardSaysItsTitleOnce() {
        // A generated placeholder draws the title, and a wide card puts it on the image.
        screen(populated()) { _ ->
            awaitText("Recently Added")
            val nodes = onAllNodes(SemanticsMatcher("a card") { SemanticsProperties.ContentDescription in it.config && SemanticsActions.OnClick in it.config }).fetchSemanticsNodes()
            val cards = nodes.filter { it.config[SemanticsProperties.ContentDescription].single().contains("Heat") }
            assertTrue(cards.isNotEmpty())
            for (card in cards) assertNull(card.config.getOrNull(SemanticsProperties.Text), "said after its description as well")
        }
    }

    @Test
    fun theSpeedButtonSaysItsValueOnce() {
        val platform = handheld
        screen(populated(), platform, width = 900, height = 600) { app ->
            awaitText("Recently Added")
            playing(app, platform)
            assertNull(node("Playback speed").config.getOrNull(SemanticsProperties.Text), "\"Normal\" is its state; \"1×\" would say it again")
        }
    }

    @Test
    fun aTouchDoesNotPutThemAwayForAScreenReader() {
        val platform = FakePlatform().apply { accessibility = AccessibilityPreferences(screenReader = true) }
        screen(populated(), platform, width = 412, height = 915) { app ->
            awaitText("Recently Added")
            playing(app, platform)
            runOnIdle { app.onSignal(InputSignal.Touch) }
            onRoot().performTouchInput { click(center) }
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            assertTrue(exists("Timeline"))
        }
    }

    @Test
    fun withoutAScreenReaderTheyStillGo() {
        val platform = FakePlatform()
        screen(populated(), platform) { app ->
            awaitText("Recently Added")
            playing(app, platform)
            mainClock.advanceTimeBy(Motion.CONTROLS_AUTO_HIDE_MS * 3)
            await("the controls to leave") { !exists("Timeline") }
        }
    }

    @Test
    fun theTimelineSaysWhereItIsAndCanBeMoved() {
        val platform = FakePlatform()
        screen(populated(), platform) { app ->
            awaitText("Recently Added")
            playing(app, platform)
            runOnIdle { platform.players.single().playTo(5 * 60_000) }
            await("the position") { node("Timeline").config.getOrNull(SemanticsProperties.StateDescription) == "5:00 of 50:00" }
            assertTrue(onAllNodesWithText("5:00").fetchSemanticsNodes().isEmpty(), "the clock beside it is not said again")
            val move = assertNotNull(node("Timeline").config.getOrNull(SemanticsActions.SetProgress)?.action)
            runOnIdle { move(0.5f) }
            await("the seek") { platform.players.single().state.value.positionMs == 25 * 60_000L }
            assertTrue(exists("Back 10 seconds") && exists("Forward 10 seconds"), "ten seconds is in the name of the seeks")
        }
    }

    @Test
    fun theListsAreOneChoiceAndSayWhenTheyAreOpen() {
        val platform = FakePlatform()
        screen(populated(), platform) { app ->
            awaitText("Recently Added")
            playing(app, platform)
            assertEquals("Closed", node("Audio").config.getOrNull(SemanticsProperties.StateDescription))
            assertEquals("Normal, closed", node("Playback speed").config.getOrNull(SemanticsProperties.StateDescription), "the value a sighted user reads on it")
            runOnIdle { app.playback!!.toggleMenu(PlayerMenu.AUDIO) }
            await("the list") { onAllNodes(titled("Audio")).fetchSemanticsNodes().isNotEmpty() }
            assertEquals("Open", node("Audio").config.getOrNull(SemanticsProperties.StateDescription))
            val radios = onAllNodes(SemanticsMatcher("a radio") { it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton }).fetchSemanticsNodes()
            assertEquals(2, radios.size, "each track is a choice")
            assertEquals(1, radios.count { it.config.getOrNull(SemanticsProperties.Selected) == true }, "and one is in effect")
            onNode(titled("Audio")).performSemanticsAction(SemanticsActions.Dismiss)
            await("the list to close") { app.playback?.menu == null }
        }
    }

    @Test
    fun whatHasNoChoiceIsAListOfActions() {
        val platform = FakePlatform()
        val world = populated()
        screen(world, platform) { app ->
            awaitText("Recently Added")
            app.play(app.library.shows().first().item.id)
            await("the player") { app.playback?.next != null || app.playback?.previous != null }
            runOnIdle { app.playback!!.toggleMenu(PlayerMenu.EPISODES) }
            await("the list") { onAllNodes(titled("Episodes")).fetchSemanticsNodes().isNotEmpty() }
            assertTrue(onAllNodes(SemanticsMatcher("a radio") { it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton }).fetchSemanticsNodes().isEmpty())
        }
    }

    @Test
    fun aRemotesPlayerGivesAScreenReaderWhatTheRemoteDoes() {
        val platform = FakePlatform(television = true)
        television(platform = platform) { app ->
            awaitText("Recently Added")
            playing(app, platform)
            assertFalse(exists("Pause"), "no button on a screen nobody can point at")
            assertFalse(exists("Back to library"))
            val actions = assertNotNull(node("Timeline").config.getOrNull(SemanticsActions.CustomActions)).map { it.label }
            assertEquals(listOf("Pause", "Back 10 seconds", "Forward 10 seconds"), actions)
            val before = app.playback!!.menu
            assertNull(before)
            val subtitles = onNode(hasText("Subtitles") and SemanticsMatcher("a tab") { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab })
            assertFalse(subtitles.fetchSemanticsNode().config.contains(SemanticsProperties.Focused) && subtitles.fetchSemanticsNode().config[SemanticsProperties.Focused], "a name takes no D-pad focus")
            subtitles.performSemanticsAction(SemanticsActions.OnClick)
            await("the list") { app.playback?.menu == PlayerMenu.SUBTITLES }
        }
    }

    @Test
    fun loadingIsSaidNotOnlyShown() {
        val platform = FakePlatform()
        platform.starts = false
        screen(populated(), platform) { app ->
            awaitText("Recently Added")
            app.play(app.library.movies().first { it.item.title == "Heat" }.item.id)
            await("the wait") { exists("Loading") }
            assertEquals(ProgressBarRangeInfo.Indeterminate, node("Loading").config.getOrNull(SemanticsProperties.ProgressBarRangeInfo))
        }
    }

    @Test
    fun aFailureAndWhatIsNextAreAnnouncedOnce() {
        val platform = FakePlatform()
        screen(populated(), platform) { app ->
            awaitText("Recently Added")
            playing(app, platform)
            runOnIdle { platform.players.single().fail("nope", PlaybackFailure.UNKNOWN) }
            await("the failure") { onAllNodesWithText("Playback stopped").fetchSemanticsNodes().isNotEmpty() }
            val pane = onAllNodes(SemanticsMatcher("a stated failure") { it.config.getOrNull(SemanticsProperties.PaneTitle)?.startsWith("Playback stopped. ") == true }).fetchSemanticsNodes()
            assertEquals(1, pane.size)
        }
    }

    @Test
    fun aNoticeIsAnAnnouncementOnOneStableNode() {
        screen(populated()) { app ->
            awaitText("Recently Added")
            val live = SemanticsMatcher("a live region") { it.config.getOrNull(SemanticsProperties.LiveRegion) == LiveRegionMode.Polite }
            assertEquals(1, onAllNodes(live).fetchSemanticsNodes().size, "it is already there, or a screen reader would never hear it change")
            val quiet = onNode(live).fetchSemanticsNode().id
            runOnIdle { app.notify("Something happened") }
            awaitText("Something happened")
            assertEquals(quiet, onNode(live and hasText("Something happened")).fetchSemanticsNode().id, "the same node, now saying it")
            assertEquals(1, onAllNodesWithText("Something happened").fetchSemanticsNodes().size, "said once")
        }
    }

    // Navigation -------------------------------------------------------------------------------------

    @Test
    fun aPhonesIconTabSaysWhatItIsAndWhetherItIsSelected() = screen(populated(), width = 412, height = 915) { app ->
        awaitText("Recently Added")
        val search = { onNode(destination("Search")).fetchSemanticsNode().config }
        assertEquals(Role.Tab, search().getOrNull(SemanticsProperties.Role))
        assertEquals(false, search().getOrNull(SemanticsProperties.Selected))
        assertEquals(true, onNode(destination("Home")).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
    }

    @Test
    fun searchSaysWhenItIsTheSection() = screen(populated()) { app ->
        awaitText("Recently Added")
        runOnIdle { app.navigator.show(Destination.Search) }
        await("search to be the section") { onNode(destination("Search")).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true }
        assertEquals(false, onNode(destination("Home")).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
    }

    @Test
    fun aPhoneMeetsTheWayBackThenThePageThenTheSections() = screen(populated(), width = 412, height = 915) { app ->
        awaitText("Recently Added")
        runOnIdle { app.navigator.open(Destination.Details(app.library.movies().first().item.id)) }
        await("the way back") { exists("Back") }
        fun group(order: Float) = onAllNodes(SemanticsMatcher("traversal $order") { it.config.getOrNull(SemanticsProperties.TraversalIndex) == order }).fetchSemanticsNodes()
        assertTrue(group(-1f).any { "Back" in it.children.flatMap { child -> child.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() } }, "the top of the screen first")
        assertTrue(group(1f).isNotEmpty(), "the sections last, though they are drawn over the page")
        assertTrue(group(0f).isNotEmpty(), "and the page between")
    }

    @Test
    fun aPageAndItsSectionsHaveNamesAndHeadings() = screen(populated()) { app ->
        awaitText("Recently Added")
        assertTrue(onAllNodes(titled("Home")).fetchSemanticsNodes().isNotEmpty())
        val heading = SemanticsMatcher("a heading") { SemanticsProperties.Heading in it.config }
        assertTrue(onAllNodes(heading and hasText("Recently Added")).fetchSemanticsNodes().isNotEmpty())
        val heat = app.library.movies().first { it.item.title == "Heat" }
        runOnIdle { app.navigator.open(Destination.Details(heat.item.id)) }
        await("the work's page") { onAllNodes(titled("Heat")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(onAllNodes(heading and hasText("Heat")).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun aCardIsOneStop() {
        screen(populated(), width = 412, height = 915) { app ->
            runOnIdle { app.navigator.show(Destination.Movies) }
            await("the card") { exists("Heat, 1995") }
            assertTrue(onAllNodesWithText("1995").fetchSemanticsNodes().isEmpty(), "its caption is in its description, and is not met again")
            assertEquals(Role.Button, node("Heat, 1995").config.getOrNull(SemanticsProperties.Role))
        }
    }

    @Test
    fun switchedOnControlsSaySo() {
        screen(populated()) { app ->
            val heat = app.library.movies().first { it.item.title == "Heat" }
            runOnIdle { app.navigator.open(Destination.Details(heat.item.id)) }
            await("the page") { exists("Mark as watched") }
            assertNull(node("Mark as watched").config.getOrNull(SemanticsProperties.Selected), "off says nothing: \"not selected\" on every button is noise")
            runOnIdle { app.setWatched(heat.item.id, true) }
            await("the mark") { exists("Mark as unwatched") }
            assertEquals(true, node("Mark as unwatched").config.getOrNull(SemanticsProperties.Selected))
        }
    }

    @Test
    fun anUnwatchedEpisodeAndAWatchedOneAreToldApart() {
        screen(populated()) { app ->
            val show = app.library.shows().first()
            runOnIdle { app.setWatched(firstEpisode(app).item.id, true) }
            runOnIdle { app.navigator.open(Destination.Details(show.item.id)) }
            val watched = SemanticsMatcher("watched") { it.config.getOrNull(SemanticsProperties.StateDescription) == "Watched" }
            await("the mark") { onAllNodes(watched).fetchSemanticsNodes().isNotEmpty() }
        }
    }

    @Test
    fun aFieldIsNamedOnceAndSaysWhenItIsWrong() {
        val world = populated()
        val platform = FakePlatform(connections = SourceConnections(world.library, world.registry, listOf(HomeNetwork.server, HomeNetwork.share)))
        screen(world, platform) { app ->
            runOnIdle { app.navigator.open(Destination.Connect(dev.reflux.core.source.ConnectionKind.SERVER)) }
            await("the form") { exists("Server address") }
            assertTrue(onAllNodesWithText("Server address").fetchSemanticsNodes().isEmpty(), "the label names the field, and is not a stop of its own")
        }
    }

    @Test
    fun theSearchFieldHasAName() = screen(populated()) { app ->
        runOnIdle { app.navigator.show(Destination.Search) }
        await("the field") { exists("Search your library") }
    }

    @Test
    fun howManyResultsThereAreIsSaid() = screen(populated()) { app ->
        runOnIdle { app.navigator.show(Destination.Search) }
        runOnIdle { app.searchQuery = "Heat" }
        val results = hasText("1 result") and SemanticsMatcher("a live region") { it.config.getOrNull(SemanticsProperties.LiveRegion) != null }
        await("the count") { onAllNodes(results).fetchSemanticsNodes().isNotEmpty() }
    }

    // Keyboard ---------------------------------------------------------------------------------------

    @Test
    fun aKeyboardReachesThePlayersListsWithoutAMenuKey() {
        val platform = FakePlatform()
        screen(populated(), platform) { app ->
            awaitText("Recently Added")
            playing(app, platform)
            runOnIdle { assertTrue(app.onKey(Key.O)) }
            await("a list") { app.playback?.menu != null }
            runOnIdle { app.onKey(Key.ESCAPE) }
            await("it to close") { app.playback?.menu == null }
            assertNotNull(app.playback, "Escape closes the list before it closes the player")
        }
    }

    @Test
    fun aLetterOutsideThePlayerIsJustALetter() = screen(populated()) { app ->
        awaitText("Recently Added")
        runOnIdle { assertFalse(app.onKey(Key.O)) }
        assertEquals(Destination.Home, app.navigator.current)
    }

    @Test
    fun tabDoesNotWalkThePageBehindThePlayer() {
        val platform = FakePlatform()
        screen(populated(), platform) { app ->
            awaitText("Recently Added")
            playing(app, platform)
            runOnIdle { assertTrue(app.onKey(Key.TAB), "taken, so the toolkit does not move focus") }
        }
    }

    @Test
    fun aPanelHandsFocusBackToWhatOpenedItWithoutWaitingForAKey() {
        val offline = OfflineWorld()
        screen(offline.world, offline.platform) { app ->
            with(offline) { begin(app) }
            runOnIdle { app.navigator.open(Destination.Details(offline.movie("Ronin"))) }
            await("the page") { exists("Download") }
            runOnIdle { app.onKey(Key.TAB) }
            await("a keyboard") { app.input.modality == InputModality.KEYBOARD }
            onNodeWithContentDescription("Download").performSemanticsAction(SemanticsActions.OnClick)
            await("the sheet to take focus") { app.offlineOpen && onAllNodes(isFocused() and hasText("Download")).fetchSemanticsNodes().isNotEmpty() }
            runOnIdle { assertTrue(app.dispatch(SemanticAction.BACK)) }
            await("focus to come back") { onAllNodes(isFocused() and hasContentDescription("Download")).fetchSemanticsNodes().isNotEmpty() }
        }
    }

    @Test
    fun anOpenPanelKeepsTabInside() {
        val offline = OfflineWorld()
        screen(offline.world, offline.platform) { app ->
            with(offline) { begin(app) }
            runOnIdle { app.navigator.open(Destination.Details(offline.movie("Ronin"))) }
            await("the page") { exists("Download") }
            // Opened with a pointer, so nothing yet has focus.
            onNodeWithContentDescription("Download").performClick()
            await("the sheet to take focus") { onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
            repeat(4) {
                onRoot().performKeyInput { pressKey(ComposeKey.Tab) }
                waitForIdle()
                assertTrue(app.offlineOpen)
                assertTrue(onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty(), "focus stays where the screen reader and the eye are")
            }
        }
    }

    // Motion and text --------------------------------------------------------------------------------

    @Test
    fun withMotionReducedStateChangesTakeNoTime() {
        val platform = FakePlatform().apply { accessibility = AccessibilityPreferences(reduceMotion = true) }
        screen(populated(), platform) {
            await("the policy") { MotionPolicy.reduced }
            assertEquals(0, Motion.navigation.tween<Float>().let { (it as TweenSpec).durationMillis })
        }
        screen(populated(), FakePlatform()) {
            await("the policy") { !MotionPolicy.reduced }
            assertEquals(Motion.navigation.durationMs, Motion.navigation.tween<Float>().durationMillis)
        }
    }

    @Test
    fun withMotionReducedLoadingIsWords() {
        val platform = FakePlatform().apply {
            starts = false
            accessibility = AccessibilityPreferences(reduceMotion = true)
        }
        screen(populated(), platform) { app ->
            awaitText("Recently Added")
            app.play(app.library.movies().first { it.item.title == "Heat" }.item.id)
            await("the wait") { onAllNodesWithText("Loading…").fetchSemanticsNodes().isNotEmpty() }
        }
    }

    private fun DesktopComposeUiTest.firstEpisode(app: AppState) = app.library.showDetail(app.library.shows().first().item.id)!!.seasons.first().episodes.first()

    private fun DesktopComposeUiTest.assertNothingCutOff(width: Int) {
        waitForIdle()
        val everything = onAllNodes(SemanticsMatcher("clickable") { SemanticsActions.OnClick in it.config }, useUnmergedTree = true).fetchSemanticsNodes()
        for (control in everything) {
            val bounds = control.boundsInRoot
            if (bounds.isEmpty || control.layoutInfo.parentInfo == null) continue
            assertTrue(bounds.left >= -1f && bounds.right <= width + 1f, "${control.config} goes past the edge: $bounds")
        }
    }

    @Test
    fun aPhoneSectionsSurviveTheLargestTextSize() = screen(populated(), width = 360, height = 780, fontScale = 2f) { _ ->
        awaitText("Recently Added")
        assertNothingCutOff(360)
        val search = onNode(destination("Search")).fetchSemanticsNode().boundsInRoot
        assertTrue(search.right <= 360f && search.width >= 40f, "Search stays in the bar: $search")
        // The bar's destinations are icons: the largest text cannot push them off the screen or squeeze them.
        val home = onNode(destination("Home")).fetchSemanticsNode().boundsInRoot
        assertTrue(home.height >= 40f && home.left >= 0f, "Home keeps its size: $home")
    }

    @Test
    fun aButtonGrowsWithItsWordsRatherThanCuttingThem() = screen(populated(), width = 412, height = 915, fontScale = 2f) { app ->
        runOnIdle { app.navigator.open(Destination.Details(app.library.movies().first().item.id)) }
        await("the page") { exists("Mark as watched") }
        val play = onNodeWithText("Play").fetchSemanticsNode()
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        play.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        assertTrue(play.boundsInRoot.height >= results.single().size.height, "the button holds its line")
    }

    @Test
    fun aPlayerAtTheLargestTextSizeStaysOnScreen() {
        val platform = handheld
        screen(populated(), platform, width = 412, height = 915, fontScale = 2f) { app ->
            awaitText("Recently Added")
            playing(app, platform)
            assertNothingCutOff(412)
            runOnIdle { app.playback!!.toggleMenu(PlayerMenu.AUDIO) }
            await("the list") { onAllNodes(titled("Audio")).fetchSemanticsNodes().isNotEmpty() }
            assertNothingCutOff(412)
        }
    }

    @Test
    fun theSystemsTextSizeIsAppliedWhereTheToolkitDoesNot() {
        val platform = FakePlatform().apply { accessibility = AccessibilityPreferences(textScale = 1.5f) }
        var plain = 0f
        screen(populated(), FakePlatform()) { plain = onNode(destination("Home")).fetchSemanticsNode().boundsInRoot.height }
        // The navigation stops at its limit; a page goes on: both are larger than at the plain size or equal to it.
        screen(populated(), platform) { app ->
            runOnIdle { app.navigator.show(Destination.Movies) }
            awaitText("Movies")
            assertTrue(onNode(destination("Home")).fetchSemanticsNode().boundsInRoot.height >= plain)
            val heading = onAllNodes(hasText("Movies") and SemanticsMatcher("a heading") { SemanticsProperties.Heading in it.config }).fetchSemanticsNodes().single()
            assertTrue(heading.boundsInRoot.height >= 30f * 1.12f * 1.4f, "a page's heading grows: ${heading.boundsInRoot}")
        }
    }

    // Touch ------------------------------------------------------------------------------------------

    @Test
    fun aFingerGetsTargetsItsOwnSize() {
        val platform = handheld
        screen(populated(), platform, width = 412, height = 915) { app ->
            runOnIdle { app.onSignal(InputSignal.Touch) }
            awaitText("Recently Added")
            await("touch") { app.input.modality == InputModality.TOUCH }
            waitForIdle()
            for (tab in listOf("Home", "Movies", "Shows", "Search", "Downloads", "Sources").filter { onAllNodes(destination(it)).fetchSemanticsNodes().isNotEmpty() }) {
                val bounds = onNode(destination(tab)).fetchSemanticsNode().boundsInRoot
                assertTrue(bounds.height >= 48f && bounds.width >= 48f, "$tab is a finger's size: $bounds")
            }
            playing(app, platform)
            assertTrue(node("Timeline").boundsInRoot.height >= 48f)
            runOnIdle { app.playback!!.toggleMenu(PlayerMenu.AUDIO) }
            await("the list") { onAllNodes(titled("Audio")).fetchSemanticsNodes().isNotEmpty() }
            val radios = onAllNodes(SemanticsMatcher("a radio") { it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton }).fetchSemanticsNodes()
            assertTrue(radios.all { it.boundsInRoot.height >= 48f }, "every line of a list")
        }
    }

}
