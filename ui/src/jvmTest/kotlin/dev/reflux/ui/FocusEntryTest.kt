package dev.reflux.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import dev.reflux.core.input.InputModality
import dev.reflux.design.AdaptiveLayout
import dev.reflux.ui.components.FocusEntry
import dev.reflux.ui.components.LocalFocusEntry
import dev.reflux.ui.components.LocalFocusScreen
import dev.reflux.ui.components.initialFocus
import dev.reflux.ui.components.rememberFocusReturn
import dev.reflux.ui.theme.LocalLayout
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Where the first arrow goes while one page gives way to another. Both pages are composed for a moment, and the
 * leaving one can still offer a starting control: Home's featured title arrives once the library has been read,
 * which can be after the viewer has already opened another page. Only the page on screen counts, whatever the
 * leaving page offers or takes back. (Found 2026-11-16: About's first arrow went to its Back button, because Home's
 * late offer replaced About's and then, withdrawn as Home went, left none; AboutUiTest's keyboard walk.)
 */
@OptIn(ExperimentalTestApi::class)
class FocusEntryTest {
    @Test
    fun aLeavingPagesLateOfferNeitherReplacesNorRemovesTheArrivingPagesStart() = runDesktopComposeUiTest(400, 300) {
        val entry = FocusEntry()
        var leavingOffers by mutableStateOf(false)
        setContent {
            CompositionLocalProvider(LocalFocusEntry provides entry) {
                Column {
                    CompositionLocalProvider(LocalFocusScreen provides "arriving") {
                        Box(Modifier.size(40.dp).initialFocus().focusable().testTag("arriving"))
                    }
                    if (leavingOffers) {
                        CompositionLocalProvider(LocalFocusScreen provides "leaving") {
                            Box(Modifier.size(40.dp).initialFocus().focusable().testTag("leaving"))
                        }
                    }
                }
            }
        }
        runOnIdle { entry.screen = "arriving" }
        // The leaving page's control arrives after the arriving page has offered its own…
        runOnIdle { leavingOffers = true }
        waitForIdle()
        // …and while both are there, the first arrow goes to the page on screen.
        runOnIdle { assertTrue(entry.enter(), "the arriving page's start is still offered") }
        onNodeWithTag("arriving").assertIsFocused()
        onNodeWithTag("leaving").assertIsNotFocused()
        // Then the leaving page goes, taking back what it offered: the arriving page keeps its start.
        runOnIdle { leavingOffers = false }
        waitForIdle()
        runOnIdle { entry.hasFocus = false; assertTrue(entry.start(), "withdrawing the leaving page's offer left the arriving page's") }
        onNodeWithTag("arriving").assertIsFocused()
    }

    @Test
    fun aLeavingPagesReturnOfferDoesNotTakeTheArrivingPagesPlace() = runDesktopComposeUiTest(400, 300) {
        val entry = FocusEntry().apply { left["arriving"] = "row"; left["leaving"] = "row" }
        var leavingOffers by mutableStateOf(false)
        // A desktop window used with a pointer: focus is not shown until the first key, as on About.
        val layout = AdaptiveLayout.profile(400f, 300f, television = false, modality = InputModality.POINTER)
        setContent {
            CompositionLocalProvider(LocalFocusEntry provides entry, LocalLayout provides layout) {
                Column {
                    CompositionLocalProvider(LocalFocusScreen provides "arriving") {
                        val back = rememberFocusReturn("row")
                        Box(back.modifier.size(40.dp).focusable().testTag("arriving"))
                    }
                    if (leavingOffers) {
                        CompositionLocalProvider(LocalFocusScreen provides "leaving") {
                            val back = rememberFocusReturn("row")
                            Box(back.modifier.size(40.dp).focusable().testTag("leaving"))
                        }
                    }
                }
            }
        }
        runOnIdle { entry.screen = "arriving" }
        runOnIdle { leavingOffers = true }
        waitForIdle()
        runOnIdle { leavingOffers = false }
        waitForIdle()
        runOnIdle { assertTrue(entry.start(), "the arriving page's place to come back to is still offered") }
        onNodeWithTag("arriving").assertIsFocused()
    }

    /**
     * B-076: an item becomes the one to come back to as soon as it has focus (B-008). If something then recomposes it
     * (here a counter it shows; on the Downloads page, a copy's state changing) after the viewer has moved on, it must not
     * take focus back: only an item that was the one to come back to when the page appeared does. Before the fix, the
     * recomposition entered the return branch and its request put focus back on the line, undoing the viewer's Right.
     */
    @Test
    fun anItemThatBecameTheOneToComeBackToDoesNotTakeFocusBackWhenItRecomposes() = runDesktopComposeUiTest(400, 300) {
        val entry = FocusEntry().apply { screen = "page" }
        var tick by mutableStateOf(0)
        val line = FocusRequester()
        val action = FocusRequester()
        // A television: focus is shown, as with a remote or a controller.
        val layout = AdaptiveLayout.profile(960f, 540f, television = true, modality = InputModality.DIRECTIONAL)
        setContent {
            CompositionLocalProvider(LocalFocusEntry provides entry, LocalLayout provides layout, LocalFocusScreen provides "page") {
                Row {
                    val back = rememberFocusReturn("line")
                    Box(Modifier.size(40.dp).then(back.modifier).focusRequester(line).focusable().testTag("line")) { BasicText("$tick") }
                    Box(Modifier.size(40.dp).focusRequester(action).focusable().testTag("action"))
                }
            }
        }
        // The page starts on its line, which so becomes the one to come back to…
        runOnIdle { line.requestFocus() }
        waitForIdle()
        onNodeWithTag("line").assertIsFocused()
        // …the viewer moves to its action…
        runOnIdle { action.requestFocus() }
        waitForIdle()
        onNodeWithTag("action").assertIsFocused()
        // …and the line recomposes (what it shows changes): focus stays where the viewer put it.
        runOnIdle { tick++ }
        waitForIdle()
        onNodeWithTag("action").assertIsFocused()
        onNodeWithTag("line").assertIsNotFocused()
    }
}
