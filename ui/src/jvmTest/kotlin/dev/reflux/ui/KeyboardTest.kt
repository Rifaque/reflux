package dev.reflux.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.performKeyInput
import dev.reflux.ui.state.Destination
import kotlin.test.Test
import kotlin.test.assertEquals

/** The system keyboard as a shell provides it, recording when it was asked to show. */
private class RecordingKeyboard : SoftwareKeyboardController {
    var shown = 0
    var hidden = 0
    override fun show() { shown++ }
    override fun hide() { hidden++ }
}

@OptIn(ExperimentalTestApi::class)
class KeyboardTest {
    @Test
    fun aRemoteOpensTheSearchKeyboardWhenOkIsLetGo() {
        val keyboard = RecordingKeyboard()
        television(around = { content -> CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) { content() } }) { app ->
            awaitText("Recently Added")
            runOnIdle { app.navigator.show(Destination.Search) }
            await("focus in the search field") { onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
            // Focus only arrives there: a keyboard the system raises with it is put away, or Fire TV's, already up,
            // takes the remote's next OK for a press of its first key and every search begins with "a".
            await("a keyboard raised with focus to be put away") { keyboard.hidden > 0 }
            val before = keyboard.shown
            onNode(isFocused()).performKeyInput { keyDown(Key.DirectionCenter) }
            waitForIdle()
            assertEquals(before, keyboard.shown, "the keyboard must not open while OK is still held")
            onNode(isFocused()).performKeyInput { keyUp(Key.DirectionCenter) }
            waitForIdle()
            assertEquals(before + 1, keyboard.shown)
            assertEquals("", app.searchQuery)
        }
    }
}
