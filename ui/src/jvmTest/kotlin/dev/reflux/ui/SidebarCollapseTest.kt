package dev.reflux.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.design.NavigationStyle
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * F-P1 as the owner specified it in the CachyOS audit brief (2026-11-24; CACHY-004): a controller's Start (≡) shrinks the
 * sidebar to its icons and back without moving focus; Y still goes to the sidebar and back; where there is no sidebar to
 * shrink (a tablet's rail, a phone) Start opens Sources as before; a pointer has a button for it; the choice is kept.
 */
@OptIn(ExperimentalTestApi::class)
class SidebarCollapseTest {
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

    /** Whether the sidebar shows the word, not only a name for assistive technology. */
    private fun DesktopComposeUiTest.showsWord(word: String) = onAllNodes(hasText(word)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun startShrinksTheSidebarToItsIconsAndBackWithoutMovingFocus() {
        val world = populated()
        screen(world) { app ->
            awaitText("Recently Added")
            repeat(3) { press(app, GamepadButton.DPAD_DOWN) }
            press(app, GamepadButton.DPAD_RIGHT)
            val card = focusedName()
            val before = focused()!!.left
            assertTrue(before > NavigationStyle.SIDEBAR.widthDp, "a card in the page to begin with")
            assertTrue(showsWord("Movies"))
            save("sidebar-full-controller")

            assertTrue(press(app, GamepadButton.START))
            await("shrunk") { app.navigationCollapsed }
            await("the names gone") { !showsWord("Movies") }
            // Still every destination, named for a screen reader, and the page moved over into the room it gave.
            assertTrue(onAllNodes(hasContentDescription("Movies")).fetchSemanticsNodes().isNotEmpty())
            assertEquals(card, focusedName(), "focus did not move")
            assertEquals(Destination.Home, app.navigator.current, "Start opened nothing")
            // The room the sidebar gave up: 216 dp less 76.
            val gave = NavigationStyle.SIDEBAR.widthDp - NavigationStyle.COLLAPSED_SIDEBAR_DP
            await("the page moved into the room", state = { focused() }) { kotlin.math.abs((focused()?.left ?: 0f) - (before - gave)) < 4f }
            save("sidebar-collapsed-controller")

            // Y still goes to the sidebar, which is still navigable shrunk, and back to the card.
            press(app, GamepadButton.FACE_NORTH)
            await("in the sidebar") { focusedName() == "Home" }
            press(app, GamepadButton.DPAD_DOWN)
            assertEquals("Movies", focusedName())
            press(app, GamepadButton.FACE_NORTH)
            await("back") { focusedName() == card }

            assertTrue(press(app, GamepadButton.START))
            await("full again") { !app.navigationCollapsed && showsWord("Movies") }
            assertEquals(card, focusedName(), "focus did not move")
        }
    }

    @Test
    fun theChoiceIsKeptAcrossARestart() {
        val world = populated()
        screen(world) { app ->
            awaitText("Recently Added")
            press(app, GamepadButton.START)
            await("shrunk") { app.navigationCollapsed }
        }
        assertEquals("collapsed", world.library.setting("navigation_collapsed"))
        screen(world) { app ->
            await("shrunk again") { app.navigationCollapsed }
            awaitText("Recently Added")
            assertFalse(showsWord("Movies"))
        }
    }

    @Test
    fun whereThereIsNoSidebarStartStillOpensSources() {
        // A tablet-sized window has the rail with names beneath: already compact, so there is nothing to shrink.
        screen(populated(), width = 1000, height = 700) { app ->
            awaitText("Recently Added")
            assertTrue(press(app, GamepadButton.START))
            await("Sources") { app.navigator.current == Destination.Sources }
            assertFalse(app.navigationCollapsed)
        }
    }

    @Test
    fun theSidebarsNameShrinksAndExpandsItForAPointerAndAFinger() {
        // The owner's correction (2026-11-24): no button of its own; Reflux's name at the head of the sidebar is the toggle,
        // and while shrunk the mark alone is there to bring it back. Nothing else is a toggle.
        screen(populated()) { app ->
            awaitText("Recently Added")
            assertTrue(onAllNodes(hasContentDescription("Shrink sidebar")).fetchSemanticsNodes().isEmpty(), "no separate button")
            onNode(hasContentDescription(BRAND_SHRINK_NAME)).performClick()
            await("shrunk") { app.navigationCollapsed }
            assertEquals(Destination.Home, app.navigator.current, "the name did not go Home")
            assertTrue(onAllNodes(hasContentDescription("Expand sidebar")).fetchSemanticsNodes().isEmpty(), "no separate button")
            save("sidebar-collapsed-pointer")
            onNode(hasContentDescription(BRAND_EXPAND_NAME)).performTouchInput { click() }
            await("full") { !app.navigationCollapsed }
        }
    }

    @Test
    fun yMovesFocusAndNeverShrinksTheSidebar() {
        screen(populated()) { app ->
            awaitText("Recently Added")
            press(app, GamepadButton.DPAD_DOWN)
            val card = focusedName()
            repeat(2) {
                press(app, GamepadButton.FACE_NORTH)
                await("in the sidebar") { focusedName() == "Home" }
                assertFalse(app.navigationCollapsed, "Y does not shrink it")
                press(app, GamepadButton.FACE_NORTH)
                await("back") { focusedName() == card }
                assertFalse(app.navigationCollapsed)
            }
        }
    }
}
