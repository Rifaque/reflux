package dev.reflux.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import dev.reflux.library.PipelineActivity
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-005: the sign that the library is being updated never covers what a page starts with: its heading, Search's field,
 * the way back. On a phone and a television it is the dot alone (said to a screen reader); beside a rail or a sidebar,
 * where the top bar is its own, it says what is happening, cut short rather than spread over the bar.
 */
@OptIn(ExperimentalTestApi::class)
class ActivityIndicatorLayoutTest {
    private val finding = "Finding titles and details"
    private val longScan = "Scanning " + "A Very Long Network Share Name On The Basement Server".repeat(2)

    private fun DesktopComposeUiTest.hold(app: AppState, activity: PipelineActivity) {
        runOnIdle { app.activityShown = MutableStateFlow(activity) }
        waitForIdle()
    }

    /** Where the sign is: its words where it says them, or the dot that says them to a screen reader. */
    private fun DesktopComposeUiTest.sign(label: String): Rect {
        val words = onAllNodesWithText(label).fetchSemanticsNodes()
        val dot = onAllNodesWithContentDescription(label).fetchSemanticsNodes()
        return (words + dot).single().boundsInRoot
    }

    /** What a page starts with, which nothing may cover. */
    private fun DesktopComposeUiTest.pageStart(): List<Rect> =
        onAllNodes(isHeading()).fetchSemanticsNodes().map { it.boundsInRoot } + onAllNodes(hasSetTextAction()).fetchSemanticsNodes().map { it.boundsInRoot }

    private fun DesktopComposeUiTest.clear(of: Rect, where: String) {
        val covered = pageStart().filter { it.overlaps(of) && it.width > 0 }
        assertTrue(covered.isEmpty(), "$where: the sign at $of covers $covered")
    }

    @Test
    fun onAPhoneItIsTheDotAloneAndCoversNeitherTheHeadingNorSearch() = screen(populated(), width = 412, height = 915) { app ->
        awaitText("Recently Added")
        hold(app, PipelineActivity.FetchingMetadata)
        await("the sign") { onAllNodesWithContentDescription(finding).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(onAllNodesWithText(finding).fetchSemanticsNodes().isEmpty(), "no words over a phone's page")
        clear(sign(finding), "Home")
        runOnIdle { app.go(Destination.Search) }
        await("Search's field") { onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        // Search's field runs across the phone's top row: the sign steps aside there, as the profile entry does.
        waitForIdle()
        assertEquals(0, onAllNodesWithContentDescription(finding).fetchSemanticsNodes().size, "nothing over Search's field")
        save("activity-phone-search")
        // A long source name changes nothing on a phone: it is never written there.
        runOnIdle { app.go(Destination.Movies) }
        hold(app, PipelineActivity.Scanning(longScan.removePrefix("Scanning ")))
        await("the sign") { onAllNodesWithContentDescription(longScan).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(onAllNodesWithText(longScan).fetchSemanticsNodes().isEmpty())
        clear(sign(longScan), "Movies, scanning")
    }

    @Test
    fun onATelevisionItIsTheDotAlone() = television() { app ->
        awaitText("Recently Added")
        hold(app, PipelineActivity.FetchingMetadata)
        await("the sign") { onAllNodesWithContentDescription(finding).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(onAllNodesWithText(finding).fetchSemanticsNodes().isEmpty())
        clear(sign(finding), "a television's Home")
        save("activity-television")
    }

    @Test
    fun besideARailOrASidebarItSaysWhatIsHappeningInItsOwnBar() {
        for ((width, height, where) in listOf(Triple(1200, 800, "a tablet"), Triple(1440, 900, "a desktop"), Triple(960, 640, "a small window"))) {
            screen(populated(), width = width, height = height) { app ->
                awaitText("Recently Added")
                hold(app, PipelineActivity.FetchingMetadata)
                awaitText(finding)
                clear(sign(finding), "$where, Home")
                runOnIdle { app.go(Destination.Search) }
                await("Search's field") { onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
                clear(sign(finding), "$where, Search")
                // A long name is cut short, within its room.
                hold(app, PipelineActivity.Scanning(longScan.removePrefix("Scanning ")))
                val long = sign(longScan)
                assertTrue(long.width <= ActivityLabelWidth.value + 1, "$where: the words keep to their room, ${long.width}")
                clear(long, "$where, Search, scanning")
                save("activity-$width")
            }
        }
    }

    @Test
    fun whenNothingIsHappeningThereIsNoSign() = screen(populated(), width = 412, height = 915) { app ->
        awaitText("Recently Added")
        hold(app, PipelineActivity.Idle)
        assertEquals(0, onAllNodesWithContentDescription(finding).fetchSemanticsNodes().size + onAllNodesWithText(finding).fetchSemanticsNodes().size)
    }
}
