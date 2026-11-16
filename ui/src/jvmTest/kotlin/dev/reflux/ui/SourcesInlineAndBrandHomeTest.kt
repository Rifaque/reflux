package dev.reflux.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.reflux.core.model.Availability
import dev.reflux.design.NavigationStyle
import dev.reflux.ui.screens.sourceActionsInline
import dev.reflux.ui.state.Destination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The 2026-11-16 follow-up, approved by the owner: a source card's two everyday actions on the card beside a side
 * navigation (tablet, desktop), phones and televisions unchanged; Reflux's name at the head of a rail or a sidebar going
 * Home; and a tablet that stays a tablet while the on-screen keyboard is up (Search on the Pad 7 had turned into a phone
 * on its side, with no mark in its rail).
 */
@OptIn(ExperimentalTestApi::class)
class SourcesInlineAndBrandHomeTest {
    private fun DesktopComposeUiTest.count(matcher: SemanticsMatcher) = onAllNodes(matcher).fetchSemanticsNodes().size
    private val isSwitch = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
    private val libraryInlineSwitch = hasContentDescription("In your library, Media") and isSwitch

    /** Every switch about the source as a whole, inline or inside the card. */
    private val anyLibrarySwitch = (hasText("In your library", substring = true) or hasContentDescription("In your library", substring = true)) and isSwitch

    private fun DesktopComposeUiTest.toggle(matcher: SemanticsMatcher) =
        onNode(matcher).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState)

    @Test
    fun onlyASideNavigationCarriesTheActionsOnTheCard() {
        assertTrue(sourceActionsInline(NavigationStyle.RAIL), "a tablet")
        assertTrue(sourceActionsInline(NavigationStyle.SIDEBAR), "a desktop")
        assertFalse(sourceActionsInline(NavigationStyle.BOTTOM_BAR), "a phone held upright")
        assertFalse(sourceActionsInline(NavigationStyle.COMPACT_RAIL), "a phone on its side")
        assertFalse(sourceActionsInline(NavigationStyle.TV_RAIL), "a television")
    }

    @Test
    fun aTabletAndADesktopCarryInYourLibraryAndRefreshOnTheCard() {
        for ((width, height) in listOf(1180 to 820, 1440 to 900)) {
            val world = populated()
            screen(world, width = width, height = height) { app ->
                awaitText("Recently Added")
                runOnIdle { app.navigator.open(Destination.Sources) }
                awaitText("Add media")
                // Closed: the switch and Refresh are on the card, named with the source; nothing destructive is.
                await("the switch on the card at $width") { count(libraryInlineSwitch) == 1 }
                assertEquals(ToggleableState.On, toggle(libraryInlineSwitch))
                assertEquals(1, count(hasContentDescription("Refresh Media")), "Refresh on the card at $width")
                assertEquals(0, count(hasText("Remove")), "Remove stays inside the card at $width")
                assertEquals("Closed", onNodeWithText("Media").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription), "the card says it opens")
                save("sources-inline-$width")

                // The switch takes the source out of the unified library and leaves its connection alone.
                onNode(libraryInlineSwitch).performClick()
                await("out of the library") { world.library.source(world.source.descriptor.id)?.included == false }
                await("said on the switch") { toggle(libraryInlineSwitch) == ToggleableState.Off }
                assertEquals(Availability.AVAILABLE, world.library.source(world.source.descriptor.id)?.availability, "still connected")
                assertTrue(world.library.sources().any { it.id == world.source.descriptor.id }, "still a source")
                onNode(libraryInlineSwitch).performClick()
                await("back in the library") { world.library.source(world.source.descriptor.id)?.included == true }

                // Refresh is a check of the source, not a way somewhere.
                onNodeWithContentDescription("Refresh Media").performClick()
                await("the check to finish") { app.checking.isEmpty() }
                assertEquals(Destination.Sources, app.navigator.current)

                // Open: what is inside, with no second switch or Refresh, and Remove still behind its question.
                onNodeWithText("Media").performClick()
                awaitText("Open")
                assertEquals("Open", onNodeWithText("Media").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
                assertEquals(1, count(anyLibrarySwitch), "one switch for the source at $width")
                assertEquals(1, count(hasContentDescription("Refresh Media") or hasText("Refresh")), "one Refresh at $width")
                save("sources-inline-open-$width")
                onNodeWithText("Remove").performClick()
                awaitText("Remove Media?")
                onNodeWithText("Keep").performClick()
                assertTrue(world.library.sources().any { it.id == world.source.descriptor.id }, "Keep keeps it")
            }
        }
    }

    @Test
    fun aPhoneKeepsEveryActionOneStepIn() {
        for ((width, height) in listOf(412 to 915, 915 to 412)) {
            screen(populated(), width = width, height = height) { app ->
                awaitText("Recently Added")
                runOnIdle { app.navigator.open(Destination.Sources) }
                awaitText("Add media")
                assertEquals(0, count(anyLibrarySwitch), "no switch on a closed card at $width")
                assertEquals(0, count(hasText("Refresh") or hasContentDescription("Refresh", substring = true)), "no Refresh on a closed card at $width")
                onNodeWithText("Media").performClick()
                awaitText("Refresh")
                // Inside, as before: the switch with its explanation, and Refresh among the card's actions.
                assertEquals(1, count(hasText("In your library") and isSwitch))
                assertTrue(count(hasText("Its movies and shows are in Home, Movies, Shows, and Search.", substring = true)) > 0)
            }
        }
    }

    @Test
    fun aTelevisionKeepsEveryActionOneStepIn() = television { app ->
        awaitText("Recently Added")
        runOnIdle { app.navigator.open(Destination.Sources) }
        awaitText("Add media")
        assertEquals(0, count(anyLibrarySwitch), "no switch on a closed card")
        assertEquals(0, count(hasContentDescription("Refresh", substring = true)), "no Refresh on a closed card")
        assertEquals(0, count(hasContentDescription(BRAND_HOME_NAME)), "a television's rail keeps its mark as it was, not a stop")
    }

    @Test
    fun refluxsNameGoesHomeOnATabletAndADesktop() {
        for ((width, height) in listOf(1180 to 820, 1440 to 900)) {
            screen(populated(), width = width, height = height) { app ->
                awaitText("Recently Added")
                val profile = app.activeProfile
                runOnIdle {
                    app.go(Destination.Movies)
                    app.navigator.open(Destination.Details(app.library.movies().first { it.item.title == "Heat" }.item.id))
                }
                await("the details at $width") { app.navigator.current is Destination.Details }
                val home = onNodeWithContentDescription(BRAND_HOME_NAME)
                assertEquals(Role.Button, home.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
                home.performClick()
                await("Home at $width") { app.navigator.current == Destination.Home }
                assertFalse(app.navigator.canGoBack, "Home alone on the stack, as choosing Home leaves it")
                assertEquals(listOf("Home"), selectedDestination())
                // Again, already Home: nothing is added.
                onNodeWithContentDescription(BRAND_HOME_NAME).performClick()
                waitForIdle()
                assertEquals(Destination.Home, app.navigator.current)
                assertFalse(app.navigator.canGoBack)
                assertSame(profile, app.activeProfile, "the viewer is who they were")
            }
        }
    }

    @Test
    fun aPhoneHasNoNameToPress() {
        for ((width, height) in listOf(412 to 915, 915 to 412)) {
            screen(populated(), width = width, height = height) { _ ->
                awaitText("Recently Added")
                assertEquals(0, count(hasContentDescription(BRAND_HOME_NAME)), "at $width")
            }
        }
    }

    @Test
    fun goingHomeByTheNameLeavesPlaybackInTheDock() {
        val platform = FakePlatform(docksVideo = true)
        screen(populated(), platform) { app ->
            awaitText("Recently Added")
            runOnIdle { app.play(app.library.movies().first { it.item.title == "Heat" }.item.id) }
            await("the player") { platform.players.isNotEmpty() && app.playback != null }
            val session = assertNotNull(app.playback)
            runOnIdle { assertTrue(session.collapse()) }
            await("the mini-player") { app.docked != null }
            runOnIdle { app.go(Destination.Movies) }
            await("Movies") { app.navigator.current == Destination.Movies }
            onNodeWithContentDescription(BRAND_HOME_NAME).performClick()
            await("Home") { app.navigator.current == Destination.Home }
            assertSame(session, app.playback, "the same playback goes on")
            assertNotNull(app.docked, "still in the dock")
            assertEquals(1, platform.players.size, "no second player")
        }
    }

    @Test
    fun aTabletStaysATabletWhileTheKeyboardIsUp() {
        // The Xiaomi Pad 7 in landscape is about 1164 by 777; its keyboard takes about 360 of that.
        screen(populated(), width = 1164, height = 777, keyboard = Modifier.padding(bottom = 360.dp)) { app ->
            awaitText("Recently Added")
            runOnIdle { app.go(Destination.Search) }
            await("the search field") { count(hasContentDescription("Search your library")) == 1 }
            save("tablet-search-keyboard")
            // A tablet's rail, with Reflux's name at its head and the utility area's own destinations, not a phone's
            // compact rail with its one profile entry.
            assertEquals(1, count(hasContentDescription(BRAND_HOME_NAME)), "the mark is in the rail")
            assertEquals(1, count(destination("Settings")), "the tablet's utility area")
            assertEquals(0, count(destination("Profile and settings")), "not a phone's single entry")
            // The page itself moves above the keyboard.
            val field = onNodeWithContentDescription("Search your library").fetchSemanticsNode().boundsInRoot
            assertTrue(field.bottom <= 777f - 360f, "the search field is above the keyboard ($field)")
        }
    }
}
