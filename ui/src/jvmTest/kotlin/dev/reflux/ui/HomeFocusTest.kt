package dev.reflux.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithText
import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Home with a controller (B-007, B-008, B-013): coming back to Home by every route leaves focus usable and where it
 * was; See all belongs to its row; the hero is shown whole when focus comes back to it.
 */
@OptIn(ExperimentalTestApi::class)
class HomeFocusTest {
    private fun DesktopComposeUiTest.press(app: AppState, button: GamepadButton): Boolean {
        val handled = runOnIdle { app.onGamepadButton(button, ControllerFamily.XBOX) }
        waitForIdle()
        return handled
    }

    private fun DesktopComposeUiTest.focused(): Rect? = runCatching { onNode(isFocused()).fetchSemanticsNode().boundsInRoot }.getOrNull()

    private fun DesktopComposeUiTest.focusedName(): String? = runCatching {
        val node = onNode(isFocused()).fetchSemanticsNode()
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
            ?: node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
            ?: node.children.firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text }
    }.getOrNull()

    /** The sidebar's width at 1440: the page starts after it. */
    private val sidebar = 220f

    private fun DesktopComposeUiTest.inPage(): Boolean = focused()?.let { it.left > sidebar } == true

    @Test
    fun homeToMoviesToHomeByTheRailLeavesRightIntoTheContent() {
        // B-008, as reported: Home → Movies → Home with a controller, then right into the content.
        screen(populated()) { app ->
            awaitText("Recently Added")
            repeat(3) { press(app, GamepadButton.DPAD_DOWN) }
            val before = focusedName()
            assertTrue(inPage(), "in the page to begin with")
            press(app, GamepadButton.DPAD_LEFT)
            await("the rail") { !inPage() }
            press(app, GamepadButton.DPAD_DOWN)
            press(app, GamepadButton.FACE_SOUTH)
            await("movies") { app.navigator.current == Destination.Movies }
            press(app, GamepadButton.DPAD_UP)
            press(app, GamepadButton.FACE_SOUTH)
            await("home") { app.navigator.current == Destination.Home }
            awaitText("Recently Added")
            press(app, GamepadButton.DPAD_RIGHT)
            assertTrue(inPage(), "right from the rail entered the page (focus: ${focusedName()} at ${focused()})")
            assertEquals(before, focusedName(), "and found the card it left")
        }
    }

    @Test
    fun everyRoundTripFromHomeComesBackUsable() {
        // B-008's flows: by the rail, the bumpers, Back, and repeatedly; each time Right or Down lands in the page.
        screen(populated()) { app ->
            awaitText("Recently Added")
            press(app, GamepadButton.DPAD_DOWN)
            for (destination in listOf(Destination.Movies, Destination.Shows, Destination.Search, Destination.Settings, Destination.Profiles, Destination.Movies)) {
                // Away by the bumper (and the utility area by the rail), back by the bumper or by Back.
                runOnIdle { app.go(destination) }
                await("$destination") { app.navigator.current == destination }
                if (destination == Destination.Search) {
                    await("the field") { app.textInputActive }
                    // B lets go of the field first, then goes back.
                    press(app, GamepadButton.FACE_EAST)
                }
                press(app, GamepadButton.FACE_EAST)
                await("home from $destination") { app.navigator.current == Destination.Home }
                awaitText("Recently Added")
                press(app, GamepadButton.DPAD_DOWN)
                assertTrue(focused() != null && inPage(), "after $destination, down lands in the page (focus: ${focusedName()} at ${focused()})")
            }
            // Repeatedly through the bumpers.
            repeat(4) {
                press(app, GamepadButton.SHOULDER_RIGHT)
                await("movies") { app.navigator.current == Destination.Movies }
                press(app, GamepadButton.SHOULDER_LEFT)
                await("home") { app.navigator.current == Destination.Home }
                press(app, GamepadButton.DPAD_RIGHT)
                assertTrue(focused() != null && inPage(), "round $it: right lands in the page (focus: ${focusedName()} at ${focused()})")
            }
        }
    }

    @Test
    fun aDetailsPageAndBackReturnsToTheCardItWasOpenedFrom() {
        screen(populated()) { app ->
            awaitText("Recently Added")
            repeat(2) { press(app, GamepadButton.DPAD_DOWN) }
            press(app, GamepadButton.DPAD_RIGHT)
            press(app, GamepadButton.DPAD_RIGHT)
            val card = focusedName()
            press(app, GamepadButton.FACE_SOUTH)
            await("details") { app.navigator.current is Destination.Details }
            press(app, GamepadButton.FACE_EAST)
            await("home") { app.navigator.current == Destination.Home }
            await("the card again") { focusedName() == card }
        }
    }

    @Test
    fun comingBackUpToTheHeroShowsAllOfIt() {
        // B-013: after scrolling down Home, moving back up to the featured title shows its artwork and words, not only
        // its buttons.
        screen(populated()) { app ->
            awaitText("Recently Added")
            press(app, GamepadButton.DPAD_DOWN)
            val title = onAllNodes(androidx.compose.ui.test.isHeading()).fetchSemanticsNodes().first().boundsInRoot
            repeat(5) { press(app, GamepadButton.DPAD_DOWN) }
            val hero = setOf("Play", "Resume", "Details")
            repeat(12) { if (focusedName() !in hero) press(app, GamepadButton.DPAD_UP) }
            assertTrue(focusedName() in hero, "back on the hero's buttons (${focusedName()})")
            await("the hero whole again") {
                val now = onAllNodes(androidx.compose.ui.test.isHeading()).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
                now != null && kotlin.math.abs(now.top - title.top) < 2f
            }
        }
    }

    @Test
    fun seeAllBelongsToItsRow() {
        // B-007: up from any card of a row with See all is that See all; down from it is the same row, at the card left,
        // never the next row's See all; and moving down card by card never stops on a See all.
        screen(populated()) { app ->
            awaitText("Recently Added")
            press(app, GamepadButton.DPAD_DOWN)
            val names = mutableListOf<String?>()
            repeat(4) {
                press(app, GamepadButton.DPAD_DOWN)
                names += focusedName()
            }
            assertTrue(names.none { it == "See all" } && names.distinct().size == names.size, "down moves card to card: $names")
            var checked = 0
            repeat(4) {
                val card = focused()!!
                val name = focusedName()
                press(app, GamepadButton.DPAD_UP)
                if (focusedName() == "See all") {
                    // Both measured now: focus moving to See all may have scrolled the page.
                    val header = focused()!!
                    val cardNow = onAllNodes(androidx.compose.ui.test.hasContentDescription(name!!)).fetchSemanticsNodes().first().boundsInRoot
                    assertTrue(header.bottom <= cardNow.top + 4f, "See all is above its own row: See all $header, card $name $cardNow")
                    press(app, GamepadButton.DPAD_DOWN)
                    assertEquals(name, focusedName(), "down from See all is back to the card in its own row")
                    press(app, GamepadButton.DPAD_UP)
                    press(app, GamepadButton.DPAD_UP)
                    assertTrue(focusedName() != "See all", "up from See all leaves the row, not to another See all")
                    checked++
                }
            }
            assertTrue(checked > 0, "a row with See all was walked")
        }
    }

    @Test
    fun yGoesToTheSidebarAndBackToExactlyWhereFocusWas() {
        // F-P2, the owner's decision on the Windows controller walk (2026-11-19): Y to the sidebar, Y again back to the item,
        // its row's scroll kept, as often as the viewer likes; moving in the sidebar without choosing changes nothing.
        screen(populated()) { app ->
            awaitText("Recently Added")
            repeat(3) { press(app, GamepadButton.DPAD_DOWN) }
            repeat(2) { press(app, GamepadButton.DPAD_RIGHT) }
            val card = focusedName()
            val where = focused()
            assertTrue(inPage(), "on a card to begin with")
            repeat(3) { round ->
                assertTrue(press(app, GamepadButton.FACE_NORTH), "Y is handled (round $round)")
                await("the sidebar (round $round)") { !inPage() }
                assertEquals("Home", focusedName(), "the sidebar on where the viewer is")
                assertTrue(press(app, GamepadButton.FACE_NORTH))
                await("back (round $round)") { inPage() }
                assertEquals(card, focusedName(), "the same card (round $round)")
                // Where it was, with its row scrolled as it was; the focus lift may still be settling by a pixel.
                val now = focused()
                assertTrue(now != null && where != null && kotlin.math.abs(now.left - where.left) < 2f && kotlin.math.abs(now.top - where.top) < 3f,
                    "where it was, with the row as it was (round $round): $where, now $now")
            }
            // Down the sidebar and back up without choosing: Y still returns to the card.
            press(app, GamepadButton.FACE_NORTH)
            await("the sidebar") { !inPage() }
            press(app, GamepadButton.DPAD_DOWN)
            press(app, GamepadButton.DPAD_DOWN)
            press(app, GamepadButton.FACE_NORTH)
            await("back") { inPage() }
            assertEquals(card, focusedName())
            assertEquals(Destination.Home, app.navigator.current, "nothing was chosen")
            // Another destination chosen from the sidebar: Y from there goes into that page's own start.
            press(app, GamepadButton.FACE_NORTH)
            await("the sidebar") { !inPage() }
            press(app, GamepadButton.DPAD_DOWN)
            press(app, GamepadButton.FACE_SOUTH)
            await("movies") { app.navigator.current == Destination.Movies }
            press(app, GamepadButton.DPAD_LEFT)
            await("the sidebar") { !inPage() }
            press(app, GamepadButton.FACE_NORTH)
            await("into Movies") { inPage() }
        }
    }
}
