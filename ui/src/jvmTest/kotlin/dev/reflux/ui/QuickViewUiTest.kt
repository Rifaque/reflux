package dev.reflux.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.click
import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.core.input.Key
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Media Quick View (F-P3): X, right-click, a long touch and a remote's Menu on a card; focus on Play; Back returns. */
@OptIn(ExperimentalTestApi::class)
class QuickViewUiTest {
    private fun DesktopComposeUiTest.press(app: AppState, button: GamepadButton) {
        runOnIdle { app.onGamepadButton(button, ControllerFamily.XBOX) }
        mainClock.advanceTimeBy(400)
        waitForIdle()
    }

    private fun DesktopComposeUiTest.focusedName(): String? =
        onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull()?.let { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() ?: node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
        }

    private val quickView = SemanticsMatcher("Quick view") { it.config.getOrNull(SemanticsProperties.PaneTitle) == "Quick view" }

    private fun DesktopComposeUiTest.open() = onAllNodes(quickView).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun xOnAFocusedCardOpensItsQuickViewOnPlay_andBGoesBackToTheCard() {
        screen(populated(), width = 1440, height = 900) { app ->
            awaitText("Recently Added")
            press(app, GamepadButton.DPAD_DOWN)
            press(app, GamepadButton.DPAD_DOWN)
            val card = assertNotNull(focusedName(), "a card has focus")
            press(app, GamepadButton.FACE_WEST)
            await("Quick View") { open() }
            assertNotNull(app.quickView)
            await("focus on its main action") { focusedName()?.let { it == "Play" || it == "Resume" || it.startsWith("Play") || it.startsWith("Continue") } == true }
            save("quick-view-controller")
            assertEquals(Destination.Home, app.navigator.current, "opening it navigated nowhere")
            assertNull(app.playback, "and played nothing")
            press(app, GamepadButton.FACE_EAST)
            await("closed") { !open() }
            await("focus back on the card") { focusedName() == card }
            assertEquals(Destination.Home, app.navigator.current)
        }
    }

    @Test
    fun playFromQuickViewPlaysTheWork_andDetailsOpensItsPage() {
        val platform = FakePlatform()
        screen(populated(), platform, width = 1440, height = 900) { app ->
            awaitText("Recently Added")
            val heat = app.library.movies().first { it.item.title == "Heat" }.item.id
            runOnIdle { app.openQuickView(heat) }
            await("Quick View") { open() }
            awaitText("Details")
            onNodeWithText("Details").performClick()
            await("the page") { app.navigator.current == Destination.Details(heat) && app.quickView == null }
            runOnIdle { app.navigator.back() }
            runOnIdle { app.openQuickView(heat) }
            await("Quick View again") { open() }
            onNodeWithText("Play").performClick()
            await("playing Heat") { app.playback != null && platform.players.isNotEmpty() }
            assertNull(app.quickView)
        }
    }

    @Test
    fun aRightClickOpensIt_andAClickOutsideClosesIt() {
        screen(populated(), width = 1440, height = 900) { app ->
            awaitText("Recently Added")
            val card = onAllNodes(SemanticsMatcher("a card") { it.boundsInRoot.height > 150f && it.config.getOrNull(SemanticsProperties.ContentDescription) != null })
                .fetchSemanticsNodes().first { it.boundsInRoot.left > 200f }.boundsInRoot
            onRoot().performMouseInput { moveTo(card.center); press(androidx.compose.ui.test.MouseButton.Secondary); release(androidx.compose.ui.test.MouseButton.Secondary) }
            await("Quick View") { open() }
            assertEquals(Destination.Home, app.navigator.current, "a right-click opens no page")
            onRoot().performMouseInput { click(Offset(20f, 880f)) }
            await("closed") { !open() }
        }
    }

    @Test
    fun aLongTouchOpensIt() {
        screen(populated(), width = 412, height = 915) { app ->
            awaitText("Recently Added")
            val card = onAllNodes(SemanticsMatcher("a card") { it.boundsInRoot.height > 150f && it.config.getOrNull(SemanticsProperties.ContentDescription) != null })
                .fetchSemanticsNodes().first().boundsInRoot
            onRoot().performTouchInput { longClick(card.center) }
            await("Quick View") { open() }
            assertEquals(Destination.Home, app.navigator.current)
            save("quick-view-phone")
        }
    }

    @Test
    fun aRemotesMenuOnACardOpensIt_butAControllersStartStillOpensSources() {
        television(populated()) { app ->
            awaitText("Recently Added")
            runOnIdle { app.onKey(Key.ARROW_DOWN) }
            runOnIdle { app.onKey(Key.ARROW_DOWN) }
            mainClock.advanceTimeBy(400)
            waitForIdle()
            runOnIdle { app.onKey(Key.MENU) }
            await("Quick View") { open() }
            save("quick-view-tv")
            runOnIdle { app.onKey(Key.BACK) }
            await("closed") { !open() }
            assertEquals(Destination.Home, app.navigator.current)
            // Start is never the context key: it opens Sources, as it did.
            press(app, GamepadButton.START)
            await("Sources") { app.navigator.current == Destination.Sources }
            assertFalse(open())
        }
    }

    @Test
    fun xWithNoFilmOrShowFocusedDoesNothing() {
        screen(populated(), width = 1440, height = 900) { app ->
            awaitText("Recently Added")
            runOnIdle { app.go(Destination.Settings) }
            awaitText("Clear watch history")
            press(app, GamepadButton.DPAD_DOWN)
            press(app, GamepadButton.FACE_WEST)
            assertNull(app.quickView)
            assertFalse(open())
            assertEquals(Destination.Settings, app.navigator.current)
        }
    }
}
