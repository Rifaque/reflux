package dev.reflux.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import dev.reflux.core.input.Key
import dev.reflux.ui.state.Destination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A panel that opens in place of the control that opened it takes focus for a remote or a controller, and gives it back to
 * that control when it closes. Before, the control with focus was gone, focus fell to the navigation, and Back there left
 * the page with the panel still open (Phase 2's close, the Google TV walk, 2026-11-20).
 */
@OptIn(ExperimentalTestApi::class)
class PanelFocusTest {
    private fun androidx.compose.ui.test.DesktopComposeUiTest.on(text: String) =
        await("focus on $text") { onAllNodes(isFocused() and hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun androidx.compose.ui.test.DesktopComposeUiTest.toSettings(app: dev.reflux.ui.state.AppState) {
        awaitText("Recently Added")
        runOnIdle { app.navigator.open(Destination.Settings) }
        awaitText("Clear watch history")
        fun onName() = onAllNodes(isFocused() and hasText("Name", substring = true)).fetchSemanticsNodes().isNotEmpty()
        // Into the page from wherever focus is (the navigation, on a television), then to its first option.
        if (!onName()) remote(app, Key.ARROW_RIGHT)
        repeat(3) { if (!onName()) remote(app, Key.ARROW_DOWN) }
        on("Name")
    }

    @Test
    fun aConfirmationTakesFocusOnKeepAndBackGivesItToWhatOpenedIt() = television { app ->
        toSettings(app)
        repeat(6) { if (onAllNodes(isFocused() and hasText("Clear watch history", substring = true)).fetchSemanticsNodes().isEmpty()) remote(app, Key.ARROW_DOWN) }
        on("Clear watch history")
        remote(app, Key.DPAD_CENTER)
        awaitText("Clear watch history on this device?")
        on("Keep")
        // Back closes the confirmation, not the page, and focus is on the option again.
        remote(app, Key.BACK)
        await("closed") { !saysAnywhere("Clear watch history on this device?") }
        assertEquals(Destination.Settings, app.navigator.current, "Back cancels the confirmation, not the page")
        on("Clear watch history")
        // Keep, chosen, does the same.
        remote(app, Key.DPAD_CENTER)
        on("Keep")
        remote(app, Key.DPAD_CENTER)
        await("kept") { !saysAnywhere("Clear watch history on this device?") }
        on("Clear watch history")
    }

    @Test
    fun renamingTakesFocusInTheFieldAndCancelGivesItBackToName() = television { app ->
        toSettings(app)
        remote(app, Key.DPAD_CENTER)
        await("the name field focused") { onAllNodes(isFocused() and hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        remote(app, Key.BACK)
        await("the field put away") { onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty() }
        assertEquals(Destination.Settings, app.navigator.current)
        on("Name")
    }

    @Test
    fun aPinPanelTakesFocusAndGivesItBack() = television { app ->
        toSettings(app)
        if (!app.pinsAvailable) return@television
        repeat(4) { if (onAllNodes(isFocused() and hasText("Add a PIN", substring = true)).fetchSemanticsNodes().isEmpty()) remote(app, Key.ARROW_DOWN) }
        on("Add a PIN")
        remote(app, Key.DPAD_CENTER)
        await("the PIN field focused") { onAllNodes(isFocused() and hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        remote(app, Key.BACK)
        await("the panel closed") { onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty() }
        assertTrue(app.navigator.current == Destination.Settings)
        on("Add a PIN")
    }
}
