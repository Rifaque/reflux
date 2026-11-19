package dev.reflux.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.core.input.Key
import dev.reflux.library.createProfile
import dev.reflux.library.profiles
import dev.reflux.library.setPin
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import dev.reflux.ui.state.InputKind
import dev.reflux.ui.state.KeyboardKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The interaction contract (B-025; docs/INPUT_MODEL.md, "Event priority"), through the real screens: the active text
 * editor first, a modal next, then the focused control, the destination, and global shortcuts. Reproduces B-019 and
 * B-020 (Backspace in a PIN or a profile name went Home) and drives the Reflux keyboard (B-023) with a controller.
 */
@OptIn(ExperimentalTestApi::class)
class InteractionContractTest {
    /** A key as a desktop shell delivers it: to Reflux first, and to the toolkit (the field) only if Reflux leaves it. */
    private fun DesktopComposeUiTest.keyboardKey(app: AppState, key: Key, toolkit: androidx.compose.ui.input.key.Key) {
        val taken = runOnIdle { app.onKey(key) }
        if (!taken) onNode(isFocused()).performKeyInput { pressKey(toolkit) }
        waitForIdle()
    }

    private fun DesktopComposeUiTest.press(app: AppState, button: GamepadButton, family: ControllerFamily = ControllerFamily.XBOX): Boolean {
        val handled = runOnIdle { app.onGamepadButton(button, family) }
        waitForIdle()
        return handled
    }

    private fun DesktopComposeUiTest.fieldText(): String =
        onNode(hasSetTextAction() and isFocused()).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    private fun DesktopComposeUiTest.keyboardShows(text: String): Boolean =
        onAllNodes(SemanticsMatcher("keyboard text") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("keyboard-text:$text|") == true })
            .fetchSemanticsNodes().isNotEmpty()

    @Test
    fun backspaceInADeletionPinErasesADigitAndNeverGoesHome() {
        // B-019: the deletion PIN on Profiles; Backspace was Back, and Back from a section is Home.
        val world = populated()
        val platform = FakePlatform()
        val sam = world.library.createProfile("Sam")
        world.library.setPin(sam, "2580", platform.pinKey!!)
        screen(world, platform) { app ->
            awaitText("Recently Added")
            runOnIdle { app.go(Destination.Profiles) }
            awaitText("Delete Sam…")
            onNodeWithText("Delete Sam…").performScrollTo().performClick()
            onNodeWithText("Delete").performClick()
            awaitText("Enter Sam's PIN to delete it.")
            onNode(hasSetTextAction()).performClick()
            onNode(hasSetTextAction()).performTextInput("25")
            await("typing") { app.textInputActive }
            keyboardKey(app, Key.BACKSPACE, androidx.compose.ui.input.key.Key.Backspace)
            assertEquals(Destination.Profiles, app.navigator.current, "Backspace in the PIN is not Back")
            assertEquals(1, fieldText().length, "one digit erased (a PIN is shown masked)")
            keyboardKey(app, Key.BACKSPACE, androidx.compose.ui.input.key.Key.Backspace)
            keyboardKey(app, Key.BACKSPACE, androidx.compose.ui.input.key.Key.Backspace)
            assertEquals(Destination.Profiles, app.navigator.current, "nor in an empty one")
            assertEquals(2, world.library.profiles().size)
        }
    }

    @Test
    fun backspaceInTheUnlockPinErasesAndTheGateStays() {
        // B-019 on the question of who is watching: Backspace there was the gate's Back.
        val world = populated()
        val platform = FakePlatform()
        val sam = world.library.createProfile("Sam")
        world.library.setPin(sam, "2580", platform.pinKey!!)
        screen(world, platform) { app ->
            awaitText("Recently Added")
            await("the profiles") { app.profiles.size == 2 }
            runOnIdle { app.chooseProfile(app.profiles.first { it.name == "Sam" }) }
            awaitText("Enter this profile's PIN.")
            onNode(hasSetTextAction()).performClick()
            onNode(hasSetTextAction()).performTextInput("25")
            val gate = app.profileGate
            keyboardKey(app, Key.BACKSPACE, androidx.compose.ui.input.key.Key.Backspace)
            assertEquals(gate, app.profileGate, "the gate is still asking")
            assertEquals(1, fieldText().length, "one digit erased (a PIN is shown masked)")
        }
    }

    @Test
    fun backspaceInAProfileNameErasesAndEscapeLetsGoFirst() {
        // B-020: the name of a new profile, and Settings' rename. Escape lets go of the field; the next Escape is Back.
        screen(populated()) { app ->
            awaitText("Recently Added")
            runOnIdle { app.go(Destination.Profiles) }
            awaitText("Add a profile")
            onNodeWithText("Add a profile").performClick()
            onNode(hasSetTextAction()).performClick()
            onNode(hasSetTextAction()).performTextInput("Samm")
            keyboardKey(app, Key.BACKSPACE, androidx.compose.ui.input.key.Key.Backspace)
            assertEquals(Destination.Profiles, app.navigator.current)
            assertEquals("Sam", fieldText())
            assertTrue(runOnIdle { app.onKey(Key.ESCAPE) })
            await("the field let go") { !app.textInputActive }
            assertEquals(Destination.Profiles, app.navigator.current, "the first Escape only lets go of the field")
            assertTrue(runOnIdle { app.onKey(Key.ESCAPE) })
            await("back") { app.navigator.current == Destination.Home }

            runOnIdle { app.go(Destination.Settings) }
            awaitText("Name")
            onNodeWithText("Name").performClick()
            await("renaming") { runCatching { onNode(hasSetTextAction()).fetchSemanticsNode() }.isSuccess }
            onNode(hasSetTextAction()).performClick()
            onNode(hasSetTextAction()).performTextInput("x")
            keyboardKey(app, Key.BACKSPACE, androidx.compose.ui.input.key.Key.Backspace)
            assertEquals(Destination.Settings, app.navigator.current, "Backspace in Settings' rename is not Back")
        }
    }

    @Test
    fun aControllerTypesThroughTheRefluxKeyboardAndItsBackClosesItFirst() {
        // B-023 and the priority: the keyboard takes the D-pad, confirm, and its buttons; B closes it and nothing else.
        screen(populated()) { app ->
            awaitText("Recently Added")
            runOnIdle { app.go(Destination.Profiles) }
            awaitText("Add a profile")
            onNodeWithText("Add a profile").performClick()
            onNode(hasSetTextAction()).performClick()
            await("the field") { app.textInputActive }
            // Confirm on the field opens the keyboard: a field is the focused control, as a button is.
            assertTrue(press(app, GamepadButton.FACE_SOUTH))
            val session = assertNotNull(app.keyboard)
            assertEquals(InputKind.NAME, session.kind)
            awaitText("Done")
            // Starts on "q"; a name starts with a capital.
            assertTrue(press(app, GamepadButton.FACE_SOUTH))
            await("Q typed") { fieldText() == "Q" && keyboardShows("Q") }
            press(app, GamepadButton.DPAD_RIGHT)
            press(app, GamepadButton.FACE_SOUTH)
            assertEquals("Qw", fieldText())
            press(app, GamepadButton.FACE_NORTH)
            assertEquals("Qw ", fieldText())
            press(app, GamepadButton.FACE_WEST)
            press(app, GamepadButton.FACE_WEST)
            assertEquals("Q", fieldText())
            // Nothing global while typing: the bumpers move the cursor rather than the destination, Menu is nothing.
            press(app, GamepadButton.SHOULDER_RIGHT)
            press(app, GamepadButton.FACE_NORTH)
            press(app, GamepadButton.SHOULDER_LEFT)
            assertEquals(Destination.Profiles, app.navigator.current)
            // B closes the keyboard and keeps the text; the field still has focus.
            assertTrue(press(app, GamepadButton.FACE_EAST))
            await("closed") { app.keyboard == null }
            assertEquals(Destination.Profiles, app.navigator.current)
            assertTrue(app.textInputActive)
            assertEquals("Q ", fieldText())
            // Confirm reopens it, with the text as it was; Start finishes, which is the field's own action (Add).
            press(app, GamepadButton.FACE_SOUTH)
            await("reopened") { app.keyboard != null && keyboardShows("Q ") }
            press(app, GamepadButton.START)
            await("added") { app.profiles.any { it.name.trim() == "Q" } }
            assertNull(app.keyboard)
        }
    }

    @Test
    fun aPinIsEnteredWithAControllerOverTheQuestionOfWhoIsWatching() {
        // The keyboard is above the profile gate, and a PIN gets the number pad.
        val world = populated()
        val platform = FakePlatform()
        val sam = world.library.createProfile("Sam")
        world.library.setPin(sam, "2580", platform.pinKey!!)
        screen(world, platform) { app ->
            awaitText("Recently Added")
            await("the profiles") { app.profiles.size == 2 }
            runOnIdle { app.chooseProfile(app.profiles.first { it.name == "Sam" }) }
            awaitText("Enter this profile's PIN.")
            // The first press focuses the gate's start, the PIN field; confirm opens the number pad.
            press(app, GamepadButton.DPAD_DOWN)
            await("the field") { app.textInputActive }
            press(app, GamepadButton.FACE_SOUTH)
            val session = assertNotNull(app.keyboard)
            assertEquals(InputKind.PIN, session.kind)
            fun digit(d: Char) {
                val (row, column) = session.rows.withIndex().firstNotNullOf { (r, keys) -> keys.indexOf(KeyboardKey.Type(d.toString())).takeIf { it >= 0 }?.let { r to it } }
                runOnIdle { app.pressKey(row, column) }
            }
            "2580".forEach(::digit)
            await("Sam open") { app.activeProfile?.name == "Sam" && app.profileGate == null }
            await("the keyboard went with the field") { app.keyboard == null }
        }
    }

    @Test
    fun aRemoteOpensTheRefluxKeyboardOnATelevisionAndBackPutsItAway() {
        // B-004's lifecycle: the system keyboard is never relied on; OK opens Reflux's, on release.
        screen(populated(), FakePlatform(television = true), width = 960, height = 540) { app ->
            awaitText("Recently Added")
            runOnIdle { app.go(Destination.Search) }
            await("the field") { app.textInputActive }
            onNode(isFocused()).performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionCenter) }
            await("the keyboard") { app.keyboard != null }
            assertEquals(InputKind.SEARCH, app.keyboard?.kind)
            // A remote's D-pad and OK through the shell.
            assertTrue(runOnIdle { app.onKey(Key.ARROW_RIGHT) })
            assertTrue(runOnIdle { app.onKey(Key.DPAD_CENTER) })
            await("w typed") { fieldText() == "w" }
            assertTrue(runOnIdle { app.onKey(Key.BACK) })
            await("put away") { app.keyboard == null }
            assertEquals(Destination.Search, app.navigator.current, "Back put the keyboard away and went nowhere")
            assertEquals("w", app.searchQuery)
            onNode(isFocused()).performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionCenter) }
            await("again") { app.keyboard != null && keyboardShows("w") }
        }
    }

    @Test
    fun searchIsUsableWithAControllerAlone() {
        // B-011: reach the field, type, go on to what was found, open it, come back with the query kept, clear it.
        screen(populated()) { app ->
            awaitText("Recently Added")
            press(app, GamepadButton.DPAD_DOWN)
            // The bumpers reach Search (Home, Movies, Shows, Search).
            repeat(3) { press(app, GamepadButton.SHOULDER_RIGHT) }
            await("search") { app.navigator.current == Destination.Search }
            await("the field") { app.textInputActive }
            assertTrue(press(app, GamepadButton.FACE_SOUTH))
            val session = assertNotNull(app.keyboard)
            // Type "heat" by the keys' places.
            for (letter in "heat") {
                val (row, column) = session.rows.withIndex().firstNotNullOf { (r, keys) -> keys.indexOf(KeyboardKey.Type(letter.toString())).takeIf { it >= 0 }?.let { r to it } }
                while (session.row != row) press(app, if (session.row < row) GamepadButton.DPAD_DOWN else GamepadButton.DPAD_UP)
                while (session.column != column) press(app, if (session.column < column) GamepadButton.DPAD_RIGHT else GamepadButton.DPAD_LEFT)
                press(app, GamepadButton.FACE_SOUTH)
            }
            assertEquals("heat", app.searchQuery)
            await("what was found") { saysAnywhere("Heat, 1995") }
            // Start finishes typing and goes on to the results.
            press(app, GamepadButton.START)
            await("the results") { app.keyboard == null && !app.textInputActive && runCatching { onNode(isFocused()).fetchSemanticsNode().boundsInRoot.height > 150f }.getOrDefault(false) }
            press(app, GamepadButton.FACE_SOUTH)
            await("details") { app.navigator.current is Destination.Details }
            press(app, GamepadButton.FACE_EAST)
            await("search again") { app.navigator.current == Destination.Search }
            assertEquals("heat", app.searchQuery, "the query is kept")
            awaitText("Heat")
        }
    }
}
