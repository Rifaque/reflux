package dev.reflux.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.reflux.core.input.Key
import dev.reflux.core.versions.SelectionCriterion
import dev.reflux.library.pinnedVersions
import dev.reflux.ui.state.Destination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A version can be made the one Play always uses, from the versions themselves, and given back to Best Version;
 * by pointer, on a phone, and with a remote alone (DECISIONS.md, 2026-10-17, "Version pinning").
 */
@OptIn(ExperimentalTestApi::class)
class VersionChoiceTest {
    private val world = populated()
    private val heat = world.movie("Heat").item.id
    private fun selection() = world.library.selectVersion(heat, testDevice)

    @Test
    fun aVersionCanBeMadeTheOnePlayAlwaysUsesAndGivenBack() {
        val recommended = selection().best!!.version.id
        val other = selection().ranked.last().version.id
        assertTrue(other != recommended && selection().ranked.last().playable, "the fixture has a second version this device plays")
        screen(world) { app ->
            app.navigator.open(Destination.Details(heat))
            awaitText("Versions")
            assertEquals(selection().ranked.size, onAllNodesWithText("Always play this").fetchSemanticsNodes().size, "offered on every version this device plays")
            save("versions-choice")

            onAllNodesWithText("Always play this").onLast().performScrollTo().performClick()
            await("the choice to be shown", { world.library.pinnedVersions(heat) }) { has("Play always uses this one") }
            assertEquals(setOf(other), world.library.pinnedVersions(heat))
            assertEquals(other, selection().best?.version?.id)
            assertEquals(SelectionCriterion.USER_PREFERENCE, selection().decidedBy)
            onNodeWithText("Your choice").assertExists()
            awaitText("Play will always use this version.")
            // One choice at a time: the chosen version offers to give it back, the other to take it over.
            assertEquals(1, onAllNodesWithText("Let Reflux choose").fetchSemanticsNodes().size)
            assertEquals(selection().ranked.size - 1, onAllNodesWithText("Always play this").fetchSemanticsNodes().size)
            save("versions-pinned")

            onNodeWithText("Let Reflux choose").performScrollTo().performClick()
            await("Reflux's choice again") { has("Recommended") && world.library.pinnedVersions(heat).isEmpty() }
            assertEquals(recommended, selection().best?.version?.id)
            assertTrue(!has("Your choice"))
        }
    }

    @Test
    fun theChoiceIsOfferedBeneathAVersionOnAPhone() = screen(world, width = 412, height = 915) { app ->
        app.navigator.open(Destination.Details(heat))
        awaitText("Versions")
        // The first version's, clear of the navigation along the bottom.
        val choice = onAllNodesWithText("Always play this").onFirst().performScrollTo()
        val bounds = choice.fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.left >= 0f && bounds.right <= 412f, "within the screen: $bounds")
        save("versions-choice-phone")
        choice.performClick()
        await("the choice to be kept") { world.library.pinnedVersions(heat).isNotEmpty() }
        awaitText("Let Reflux choose")
        save("versions-pinned-phone")
    }

    @Test
    fun theChoiceIsReachedWithARemote() = television(world) { app ->
        app.navigator.open(Destination.Details(heat))
        awaitText("Versions")
        await("focus on Play") { onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
        // Down to the first version, then across to the choice beside it.
        remote(app, Key.ARROW_DOWN)
        remote(app, Key.ARROW_RIGHT)
        await("focus on the choice", { onAllNodes(isFocused()).fetchSemanticsNodes().map { it.config.toString() } }) {
            onAllNodes(isFocused() and hasText("Always play this")).fetchSemanticsNodes().isNotEmpty()
        }
        save("versions-choice-tv")
        remote(app, Key.DPAD_CENTER)
        await("the choice to be kept") { world.library.pinnedVersions(heat).isNotEmpty() }
        awaitText("Let Reflux choose")
        save("versions-pinned-tv")
    }

    private fun androidx.compose.ui.test.DesktopComposeUiTest.has(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
}
