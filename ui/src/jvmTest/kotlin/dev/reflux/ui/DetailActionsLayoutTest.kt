package dev.reflux.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import dev.reflux.ui.state.Destination
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The actions of a details page keep to one tidy group at every width: Play or Resume first and most prominent, "From the
 * start" beside it, then the compact ones (watched, favorite). No action is ever left alone on a second line because the
 * page has four of them. Rendered offscreen at handheld widths, with and without large text.
 */
@OptIn(ExperimentalTestApi::class)
class DetailActionsLayoutTest {
    private class Actions(val primary: Rect, val fromStart: Rect?, val watched: Rect, val favorite: Rect)

    private fun DesktopComposeUiTest.bounds(text: String) = onNodeWithText(text).fetchSemanticsNode().boundsInRoot
    private fun DesktopComposeUiTest.bounds(description: String, byDescription: Boolean) = onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot

    /** Opens the details of a film left unfinished ([resume]) or not, and reads where its actions are. */
    private fun measured(width: Int, height: Int = 915, fontScale: Float = 1f, resume: Boolean = true, title: String = "Heat", touch: Boolean = false, block: (Actions) -> Unit) {
        val world = populated()
        if (resume) world.library.stopped(world.movie(title).item.id, 62 * 60_000L, 170 * 60_000L)
        screen(world, FakePlatform(), width, height, fontScale = fontScale) { app ->
            awaitText("Recently Added")
            app.navigator.open(Destination.Details(app.library.movies().first { it.item.title == title }.item.id))
            awaitText(if (resume) "Resume" else "Play")
            // A finger makes the compact actions 48 dp targets, as they are on a phone in hand.
            if (touch) onNodeWithText(title).performTouchInput { click() }
            waitForIdle()
            block(
                Actions(
                    primary = bounds(if (resume) "Resume" else "Play"),
                    fromStart = if (resume) bounds("From the start") else null,
                    watched = bounds(if (resume) "Mark as watched" else "Mark as watched", true),
                    favorite = bounds("Add to favorites", true),
                ),
            )
        }
    }

    private fun Actions.all() = listOfNotNull(primary, fromStart, watched, favorite)

    /** Whether every action sits on the same line (they share a vertical centre within a few pixels). */
    private fun Actions.oneLine() = all().let { rects -> rects.maxOf { it.center.y } - rects.minOf { it.center.y } < 6f }

    /** The compact actions are a group: they share a line whatever else the line does. */
    private fun Actions.pairTogether() = kotlin.math.abs(watched.center.y - favorite.center.y) < 6f

    private fun Actions.describe() = "primary=$primary fromStart=$fromStart watched=$watched favorite=$favorite"

    @Test
    fun fourActionsSitOnOneLineOnAPhone() = measured(width = 412) { actions ->
        println("TRIAL phone 412: ${actions.describe()}")
        assertTrue(actions.oneLine(), "Resume, From the start, watched, and favorite share a line: ${actions.describe()}")
    }

    @Test
    fun theLeadActionStaysTheMostProminentAndTheCompactOnesStayCompact() = measured(width = 412) { actions ->
        assertTrue(actions.primary.width > actions.watched.width * 2 && actions.primary.height >= actions.watched.height, "Resume leads: ${actions.describe()}")
        assertTrue(actions.watched.width <= 56f && actions.favorite.width <= 56f, "the compact ones stay icons: ${actions.describe()}")
    }

    @Test
    fun withAFingerAllFourActionsStillSitOnOneLine() {
        // 48 dp targets, as on a phone in hand: Resume, the quiet From the start, watched, favorite, on one line.
        measured(width = 412, touch = true) { actions ->
            println("TRIAL touch 412: ${actions.describe()}")
            assertTrue(actions.watched.width >= 47f && actions.favorite.width >= 47f, "touch targets of 48 dp: ${actions.describe()}")
            assertTrue(actions.oneLine(), "all four share a line: ${actions.describe()}")
            assertTrue(actions.primary.width > 100f, "Resume keeps its size: ${actions.describe()}")
        }
    }

    @Test
    fun threeActionsStillSitOnOneLine() = measured(width = 412, resume = false) { actions ->
        assertTrue(actions.oneLine(), "Play, watched, and favorite share a line: ${actions.describe()}")
    }

    @Test
    fun noActionIsLeftAloneWhenTheLineMustBreak() {
        // At a narrower phone, or with large text, the line may break; what must not happen is one icon alone on the second line.
        for ((width, scale) in listOf(360 to 1f, 360 to 2f, 412 to 2f, 320 to 1f)) {
            measured(width = width, fontScale = scale) { actions ->
                println("TRIAL $width at ${scale}x: ${actions.describe()}")
                val lines = actions.all().groupBy { Math.round(it.center.y / 10f) }.values
                val lone = lines.filter { it.size == 1 && (it.single() == actions.favorite || it.single() == actions.watched) }
                assertTrue(lone.isEmpty(), "neither compact action is alone on a line at $width dp, ${scale}x text: ${actions.describe()}")
                assertTrue(actions.pairTogether(), "the pair stays together at $width dp, ${scale}x text: ${actions.describe()}")
            }
        }
    }

    @Test
    fun aLongTitleDoesNotMoveTheActionsOntoTwoLines() = measured(width = 412, title = "Heat") { actions ->
        assertTrue(actions.oneLine(), actions.describe())
    }

    @Test
    fun aTabletKeepsTheSameRow() = measured(width = 900, height = 1200) { actions ->
        assertTrue(actions.oneLine(), "on a tablet's page: ${actions.describe()}")
    }
}
