package dev.reflux.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.core.input.InputSignal
import dev.reflux.core.input.Key
import dev.reflux.core.input.SemanticAction
import dev.reflux.core.model.Episode
import dev.reflux.library.LibraryScope
import dev.reflux.library.setSourceIncluded
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A destination in the navigation, by its name, whether it is drawn as words or as an icon that says them. */
internal fun destination(name: String): SemanticsMatcher =
    (hasText(name) or hasContentDescription(name)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

@OptIn(ExperimentalTestApi::class)
internal fun DesktopComposeUiTest.selectedDestination(): List<String> = onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    .fetchSemanticsNodes().map { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString() ?: node.config.getOrNull(SemanticsProperties.Text)?.joinToString().orEmpty()
    }

/**
 * The global navigation (docs/NAVIGATION.md): the same six destinations on every window, placed for each, with a
 * back stack that always unwinds the same way, and the unified library told apart from each source's own.
 */
@OptIn(ExperimentalTestApi::class)
class NavigationTest {
    /** A library and a platform that keeps copies, so that every one of the six destinations is there. */
    private fun keeping(television: Boolean = false): Pair<TestWorld, FakePlatform> {
        val offline = OfflineWorld()
        current = offline.world
        return offline.world to FakePlatform(television = television, offline = offline.offline)
    }

    private fun DesktopComposeUiTest.bounds(matcher: SemanticsMatcher): Rect = onNode(matcher).fetchSemanticsNode().boundsInRoot
    private fun DesktopComposeUiTest.has(matcher: SemanticsMatcher) = onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    private fun DesktopComposeUiTest.go(name: String) = onNode(destination(name)).performClick()

    @Test
    fun aPhoneHeldUprightHasItsFourDestinationsAsIconsAlongTheBottom() = keeping().let { (world, platform) -> screen(world, platform, width = 412, height = 915) { app ->
        awaitText("Recently Added")
        for (name in listOf("Home", "Movies", "Shows", "Search")) {
            val node = onNode(destination(name)).fetchSemanticsNode()
            assertTrue(node.boundsInRoot.top > 915 - 120, "$name is along the bottom")
            assertEquals(null, node.config.getOrNull(SemanticsProperties.Text), "$name is an icon, with no words drawn beneath it")
            assertEquals(listOf(name), node.config.getOrNull(SemanticsProperties.ContentDescription), "but it says its name")
        }
        assertEquals(listOf("Home"), selectedDestination(), "where the viewer is is said, and only that")
        // Downloads and Sources are within reach without crowding the bar: in the top corner of every destination.
        assertTrue(bounds(destination("Sources")).top < 80f)
        assertTrue(bounds(destination("Downloads")).top < 80f)
        assertTrue(onAllNodesWithText("Movies").fetchSemanticsNodes().none { it.boundsInRoot.top > 800f }, "no permanent words in the bar")
        save("nav-phone-portrait")

        go("Movies")
        await("Movies") { app.navigator.current == Destination.Movies }
        assertEquals(listOf("Movies"), selectedDestination())
        go("Sources")
        await("Sources") { app.navigator.current == Destination.Sources }
        assertEquals(listOf("Sources"), selectedDestination(), "Sources says it is where the viewer is")
        assertTrue(runOnIdle { app.navigator.back() })
        assertEquals(Destination.Home, app.navigator.current, "a destination's Back is Home")
    } }

    @Test
    fun aPhoneOnItsSideHasACompactRailNotTheBarStretchedAcross() = keeping().let { (world, platform) -> screen(world, platform, width = 915, height = 412) { app ->
        awaitText("Recently Added")
        val names = listOf("Home", "Movies", "Shows", "Search", "Downloads", "Sources")
        val rail = names.map { bounds(destination(it)) }
        assertTrue(rail.all { it.right < 80f }, "every destination is in a narrow rail on the left: $rail")
        assertTrue(rail.zipWithNext().all { (a, b) -> b.top > a.top }, "in order, top to bottom")
        assertTrue(names.all { onNode(destination(it)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text) == null }, "icons alone: the short screen is the content's")
        save("nav-phone-landscape")
        go("Shows")
        await("Shows") { app.navigator.current == Destination.Shows }
        assertEquals(listOf("Shows"), selectedDestination())
    } }

    @Test
    fun aTabletHasARailWithNamesAndIsNotALargePhone() = keeping().let { (world, platform) -> screen(world, platform, width = 1180, height = 820) { app ->
        awaitText("Recently Added")
        for (name in listOf("Home", "Movies", "Shows", "Search", "Downloads", "Sources")) {
            val node = onNode(destination(name)).fetchSemanticsNode()
            assertTrue(node.boundsInRoot.right < 100f, "$name is in the rail")
            assertEquals(listOf(name), node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }, "with its name beneath its icon")
        }
        save("nav-tablet")
        go("Movies")
        awaitText("12 titles")
        // The page starts beside the rail.
        assertTrue(onAllNodesWithText("Movies").fetchSemanticsNodes().any { it.boundsInRoot.left > 100f }, "the page's own title is beside the rail")
    } }

    @Test
    fun aDesktopHasASidebarAndReachesEveryDestinationFromTheKeyboard() = keeping().let { (world, platform) -> screen(world, platform) { app ->
        awaitText("Recently Added")
        awaitText("Reflux")
        val sidebar = listOf("Home", "Movies", "Shows", "Search", "Downloads", "Sources").map { bounds(destination(it)) }
        assertTrue(sidebar.all { it.left < 40f && it.width > 120f }, "names beside icons in a sidebar: $sidebar")
        save("nav-desktop")
        val keys = listOf(Key.SHORTCUT_2 to Destination.Movies, Key.SHORTCUT_3 to Destination.Shows, Key.SHORTCUT_5 to Destination.Downloads, Key.SHORTCUT_6 to Destination.Sources, Key.SHORTCUT_1 to Destination.Home)
        for ((key, expected) in keys) {
            assertTrue(runOnIdle { app.onKey(key) })
            await("$expected") { app.navigator.current == expected }
        }
        assertTrue(runOnIdle { app.onKey(Key.SHORTCUT_4) })
        await("search, typing") { app.navigator.current == Destination.Search && app.textInputActive }
        // A shortcut is not typing: it works from inside the field.
        assertTrue(runOnIdle { app.onKey(Key.SHORTCUT_2) })
        await("movies") { app.navigator.current == Destination.Movies }
        // Escape is Back, from anywhere a destination opened.
        runOnIdle { app.navigator.open(Destination.Details(world().movie("Heat").item.id)) }
        assertTrue(runOnIdle { app.onKey(Key.ESCAPE) })
        assertEquals(Destination.Movies, app.navigator.current)
    } }

    private var current: TestWorld? = null
    private fun world(): TestWorld = current!!

    @Test
    fun aControllersBumpersStepThroughTheDestinations() {
        val (world, platform) = keeping()
        screen(world, platform) { app ->
            awaitText("Recently Added")
            fun bumper(button: GamepadButton) = runOnIdle { app.onGamepadButton(button, ControllerFamily.XBOX) }
            assertFalse(bumper(GamepadButton.SHOULDER_LEFT), "Home is the first: nothing before it")
            assertTrue(bumper(GamepadButton.SHOULDER_RIGHT))
            assertEquals(Destination.Movies, app.navigator.current)
            repeat(4) { bumper(GamepadButton.SHOULDER_RIGHT) }
            assertEquals(Destination.Sources, app.navigator.current)
            assertFalse(bumper(GamepadButton.SHOULDER_RIGHT), "and Sources the last")
            assertTrue(bumper(GamepadButton.SHOULDER_LEFT))
            assertEquals(Destination.Downloads, app.navigator.current)
        }
    }

    @Test
    fun backUnwindsTheSameStackEveryTime() {
        val world = populated().also { current = it }
        screen(world, FakePlatform()) { app ->
            awaitText("Recently Added")
            val heat = world.movie("Heat").item.id
            runOnIdle {
                app.go(Destination.Movies)
                app.navigator.open(Destination.Details(heat))
            }
            assertEquals(listOf(Destination.Home, Destination.Movies, Destination.Details(heat)), app.navigator.path)
            // Another destination replaces whatever was stacked on the one before.
            runOnIdle { app.go(Destination.Shows) }
            assertEquals(listOf(Destination.Home, Destination.Shows), app.navigator.path)
            // Choosing the destination already shown goes back to its top.
            val show = world.show("Breaking Bad").item.id
            runOnIdle { app.navigator.open(Destination.Details(show)) }
            runOnIdle { app.go(Destination.Shows) }
            assertEquals(listOf(Destination.Home, Destination.Shows), app.navigator.path)
            assertTrue(runOnIdle { app.dispatch(SemanticAction.BACK) })
            assertEquals(Destination.Home, app.navigator.current)
            assertFalse(runOnIdle { app.dispatch(SemanticAction.BACK) }, "at Home, Back is the platform's: Reflux has nothing to go back to")
        }
    }

    @Test
    fun showSeasonEpisodeAndBackAgainWithTheSeasonKept() {
        val world = populated().also { current = it }
        screen(world, FakePlatform()) { app ->
            awaitText("Recently Added")
            val show = world.show("Breaking Bad").item.id
            runOnIdle {
                app.go(Destination.Shows)
                app.navigator.open(Destination.Details(show))
            }
            awaitText("Season 2")
            onNode(hasText("Season 2") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
            val season2 = hasText("Season 2") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
            await("season 2 chosen") { onNode(season2).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true }
            // An episode plays from its row; the player is above the show's page, which is never left.
            val episode = world.episodes("Breaking Bad").first { (it.item as Episode).seasonNumber == 2 && (it.item as Episode).episodeNumber == 2 }
            runOnIdle { app.play(episode.item.id) }
            await("the player") { app.playback != null }
            assertEquals(Destination.Details(show), app.navigator.current)
            val playing = app.playback!!.entry?.item as? Episode
            assertEquals(2, playing?.seasonNumber, "season 2's episode is what plays")
            runOnIdle { app.dispatch(SemanticAction.BACK) }
            await("the player gone") { app.playback == null }
            assertEquals(Destination.Details(show), app.navigator.current, "closing the player returns to the page it was opened from")
            await("season 2 still chosen") { onNode(season2).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true }
            runOnIdle { app.dispatch(SemanticAction.BACK) }
            assertEquals(Destination.Shows, app.navigator.current)
        }
    }

    @Test
    fun aSourceOutOfTheLibraryIsStillOpenedAndPlayedFromSources() {
        val world = populated().also { current = it }
        screen(world, FakePlatform()) { app ->
            awaitText("Recently Added")
            val id = world.source.descriptor.id
            runBlocking { world.library.setSourceIncluded(id, false) }
            runOnIdle { app.invalidate(); app.go(Destination.Movies) }
            awaitText("Nothing here yet")
            // Sources: the source is still there, out of the library, and can be opened.
            go("Sources")
            await("its card") { saysAnywhere("Media") }
            runOnIdle { app.navigator.open(Destination.SourceLibrary(id)) }
            awaitText("Media")
            awaitText("Heat")
            assertTrue(has(hasText("not in Home, Movies, Shows, or Search", substring = true)), "the page says it is out of the library")
            save("source-library-excluded")
            onNode(hasContentDescription("Heat", substring = true)).performClick()
            await("Heat's page, from its source") { (app.navigator.current as? Destination.Details)?.scope == LibraryScope.OfSource(id) }
            awaitText("Play")
            onNode(hasText("Play") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
            await("the player") { app.playback != null }
            assertEquals(LibraryScope.OfSource(id), app.playback!!.libraryScope, "it plays from that source")
            runOnIdle { app.dispatch(SemanticAction.BACK) }
            await("the player gone") { app.playback == null }
            runOnIdle { app.dispatch(SemanticAction.BACK) }
            assertEquals(Destination.SourceLibrary(id), app.navigator.current, "Back unwinds to the source's page")
            runOnIdle { app.dispatch(SemanticAction.BACK) }
            assertEquals(Destination.Sources, app.navigator.current)
        }
    }

    @Test
    fun theSourceSwitchIsOnTheSourcesPageAndChangesOnlyTheUnifiedLibrary() {
        val world = populated().also { current = it }
        screen(world, FakePlatform()) { app ->
            awaitText("Recently Added")
            go("Sources")
            awaitText("Media")
            onNode(hasText("Media")).performClick()
            awaitText("In your library")
            val switch = hasText("In your library") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
            assertEquals(androidx.compose.ui.state.ToggleableState.On, onNode(switch).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState))
            save("sources-switch-on")
            onNode(switch).performClick()
            await("excluded") { world.library.source(world.source.descriptor.id)?.included == false }
            await("said") { app.notice?.contains("no longer in your library") == true }
            assertTrue(world.library.movies().isEmpty())
            assertEquals(10, world.library.movies(LibraryScope.OfSource(world.source.descriptor.id)).size)
            assertEquals(dev.reflux.core.model.Availability.AVAILABLE, world.library.source(world.source.descriptor.id)?.availability, "nothing about its connection changed")
            save("sources-switch-off")
            onNode(switch).performClick()
            await("included again") { world.library.source(world.source.descriptor.id)?.included == true }
            assertEquals(10, world.library.movies().size)
        }
    }

    @Test
    fun theRemoteHasARailBesideThePageAndBackGoesToItFirst() = keeping(television = true).let { (world, platform) -> television(world, platform) { app ->
        awaitText("Recently Added")
        await("focus on the page") { has(isFocused()) }
        val rail = listOf("Home", "Movies", "Shows", "Search", "Downloads", "Sources").map { onNode(destination(it)).fetchSemanticsNode().boundsInRoot.let { r -> Rect(r.left / 2, r.top / 2, r.right / 2, r.bottom / 2) } }
        assertTrue(rail.all { it.right < 90f }, "the rail is on the left: $rail")
        save("nav-tv")
        runOnIdle { app.go(Destination.Movies) }
        awaitText("12 titles")
        await("a poster") { has(isFocused() and hasContentDescription("Alien", substring = true)) }
        // Left from the first column is the rail, on the destination the viewer is in.
        remote(app, Key.ARROW_LEFT)
        await("the rail, on Movies") { has(isFocused() and destination("Movies")) }
        save("nav-tv-rail-open")
        remote(app, Key.ARROW_DOWN)
        await("Shows, the next stop") { has(isFocused() and destination("Shows")) }
        remote(app, Key.ARROW_UP)
        await("Movies again") { has(isFocused() and destination("Movies")) }
        // Right goes back to where the viewer was in the page.
        remote(app, Key.ARROW_RIGHT)
        await("the poster again") { has(isFocused() and hasContentDescription("Alien", substring = true)) }
        // Back on a destination goes to the rail first, then Home.
        remote(app, Key.BACK)
        await("the rail") { has(isFocused() and destination("Movies")) }
        assertEquals(Destination.Movies, app.navigator.current)
        remote(app, Key.BACK)
        await("home") { app.navigator.current == Destination.Home }
        // From the rail, OK goes to a destination.
        remote(app, Key.ARROW_LEFT)
        await("the rail on Home") { has(isFocused() and destination("Home")) }
        remote(app, Key.ARROW_DOWN, times = 5)
        await("Sources") { has(isFocused() and destination("Sources")) }
        remote(app, Key.ARROW_DOWN)
        assertTrue(has(isFocused() and destination("Sources")), "no stop after the last destination")
        remote(app, Key.DPAD_CENTER)
        await("the Sources page") { app.navigator.current == Destination.Sources }
    } }

    @Test
    fun theNavigationIsInertBehindThePlayerAndAnOpenSheet() {
        val world = populated().also { current = it }
        screen(world, FakePlatform()) { app ->
            awaitText("Recently Added")
            runOnIdle { app.play(world.movie("Heat").item.id) }
            await("the player") { app.playback != null }
            assertFalse(has(destination("Movies")), "behind the player, the navigation is not there for assistive technology")
            runOnIdle { app.dispatch(SemanticAction.BACK) }
            await("closed") { app.playback == null }
            await("the navigation back") { has(destination("Movies")) }
            // Nor is it reached by its shortcuts while something plays: the player is left first.
            runOnIdle { app.play(world.movie("Heat").item.id) }
            await("the player") { app.playback != null }
            assertFalse(runOnIdle { app.onKey(Key.SHORTCUT_2) })
            assertEquals(Destination.Home, app.navigator.current)
        }
    }

    @Test
    fun downloadsAndSearchAreDestinationsWithTheirOwnBack() {
        val (world, platform) = keeping()
        screen(world, platform) { app ->
            awaitText("Recently Added")
            go("Downloads")
            await("the empty Downloads page") { onAllNodesWithText("Nothing is downloaded", substring = true).fetchSemanticsNodes().isNotEmpty() }
            assertEquals(listOf(Destination.Home, Destination.Downloads), app.navigator.path)
            go("Search")
            await("search") { app.navigator.current == Destination.Search }
            runOnIdle { app.searchQuery = "Heat" }
            await("a result") { has(hasContentDescription("Heat", substring = true)) }
            onNode(hasContentDescription("Heat", substring = true)).performClick()
            await("Heat") { app.navigator.current is Destination.Details }
            runOnIdle { app.dispatch(SemanticAction.BACK) }
            assertEquals(Destination.Search, app.navigator.current)
            assertEquals("Heat", app.searchQuery, "what was searched for is still there")
        }
    }

    @Test
    fun leftFromAnywhereInACentredPageIsTheRailNotTheButtonAboveIt() = television { app ->
        awaitText("Recently Added")
        runOnIdle { app.go(Destination.Sources) }
        await("Add media") { has(isFocused() and hasText("Add media")) }
        remote(app, Key.ARROW_DOWN)
        await("the folder's card") { has(isFocused() and hasText("Media", substring = true)) }
        remote(app, Key.DPAD_CENTER)
        remote(app, Key.ARROW_DOWN)
        val switch = hasText("In your library", substring = true) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
        await("its switch") { has(isFocused() and switch) }
        // Add media is above and to the left: it is not where Left goes.
        remote(app, Key.ARROW_LEFT)
        await("the rail, on Sources", { runCatching { onNode(isFocused()).fetchSemanticsNode().config.toString() }.getOrNull() }) { has(isFocused() and destination("Sources")) }
        remote(app, Key.ARROW_RIGHT)
        await("back on the switch", { runCatching { onNode(isFocused()).fetchSemanticsNode().let { n -> n.config.getOrNull(SemanticsProperties.Text).toString() + n.config.getOrNull(SemanticsProperties.ContentDescription) + n.boundsInRoot } }.getOrNull() }) { has(isFocused() and switch) }
    }
}
