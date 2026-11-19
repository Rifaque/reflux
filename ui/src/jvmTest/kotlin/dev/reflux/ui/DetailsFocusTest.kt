package dev.reflux.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.click
import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B-053: Select on a film or a show opens its page on Play, so Select again plays; nothing plays on arrival; a pointer is
 * never handed focus; Back comes back to the card.
 */
@OptIn(ExperimentalTestApi::class)
class DetailsFocusTest {
    private fun DesktopComposeUiTest.press(app: AppState, button: GamepadButton) {
        runOnIdle { app.onGamepadButton(button, ControllerFamily.XBOX) }
        waitForIdle()
    }

    private fun DesktopComposeUiTest.focusedName(): String? = runCatching {
        val node = onNode(isFocused()).fetchSemanticsNode()
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
            ?: node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
            ?: node.children.firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text }
    }.getOrNull()

    private fun SemanticsNodeInteraction.mouseClick() = performMouseInput { click() }

    private fun DesktopComposeUiTest.isPlayAction(name: String?) = name != null && (name.startsWith("Play") || name.startsWith("Resume"))

    @Test
    fun selectOnACardOpensItsPageOnPlayAndSelectAgainPlays() {
        val platform = FakePlatform()
        screen(populated(), platform) { app ->
            awaitText("Recently Added")
            // Down to the row's first card (past the hero's actions).
            repeat(3) { press(app, GamepadButton.DPAD_DOWN) }
            val card = focusedName()
            press(app, GamepadButton.FACE_SOUTH)
            await("details") { app.navigator.current is Destination.Details }
            await("Play focused", { focusedName() }) { isPlayAction(focusedName()) }
            assertNull(app.playback, "nothing plays on arrival")
            assertTrue(platform.players.isEmpty())
            press(app, GamepadButton.FACE_SOUTH)
            await("playing") { app.playback != null }
            // Back to the page, and Back again to the card that was opened.
            press(app, GamepadButton.FACE_EAST)
            runOnIdle { app.playback?.close() }
            await("details again") { app.playback == null && app.navigator.current is Destination.Details }
            press(app, GamepadButton.FACE_EAST)
            await("home") { app.navigator.current == Destination.Home }
            await("the card", { focusedName() }) { focusedName() == card }
        }
    }

    @Test
    fun fromMoviesAndFromShowsToo() {
        screen(populated()) { app ->
            awaitText("Recently Added")
            for (section in listOf(Destination.Movies, Destination.Shows)) {
                runOnIdle { app.go(section) }
                await("$section") { app.navigator.current == section }
                waitForIdle()
                press(app, GamepadButton.DPAD_DOWN)
                await("a card in $section", { focusedName() }) { focusedName() != null }
                press(app, GamepadButton.FACE_SOUTH)
                await("details from $section") { app.navigator.current is Destination.Details }
                await("Play focused from $section", { focusedName() }) { isPlayAction(focusedName()) }
                assertNull(app.playback)
                press(app, GamepadButton.FACE_EAST)
                await("back to $section") { app.navigator.current == section }
            }
        }
    }

    @Test
    fun aPointerOpensThePageWithoutFocusBeingTaken() {
        screen(populated()) { app ->
            awaitText("Recently Added")
            onAllNodesWithContentDescription("Heat, 1995").onFirst().mouseClick()
            await("details") { app.navigator.current is Destination.Details }
            waitForIdle()
            assertEquals(null, focusedName(), "no focus forced on a pointer user")
            assertNull(app.playback)
        }
    }
}
