package dev.reflux.ui

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A page below the top level, beside a navigation that runs down the side (tablet, desktop), has its way back
 * floating in its top bar. Scrolled, its rows went on under that bar and showed through beside and behind the Back
 * button (a show's season tabs on a Xiaomi Pad 7, 2026-11-16): the television had a scrim there and the others had
 * none. Then (the same day, on the Pad 7) the scrim faded out from 72% of the bar's height, so rows still passed
 * bright behind the button's lower half. Now the scrim holds through the whole bar and fades out below it.
 */
@OptIn(ExperimentalTestApi::class)
class PageBarScrimTest {
    /**
     * How many pixels between [top] and [bottom] (dp, the canvas is drawn at density 1) are bright, from just right of
     * the Back button across: content showing through the bar.
     */
    private fun androidx.compose.ui.test.DesktopComposeUiTest.brightInBar(width: Int, top: Int, bottom: Int): Int {
        val pixels = onRoot().captureToImage().toPixelMap()
        var bright = 0
        for (x in 300 until width - 30) for (y in top until bottom) {
            val c = pixels[x, y]
            if (0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue > 0.25f) bright++
        }
        return bright
    }

    private fun androidx.compose.ui.test.DesktopComposeUiTest.scrolledShow(app: AppState) {
        awaitText("Recently Added")
        runOnIdle { app.navigator.open(Destination.Details(app.library.shows().first { it.item.title == "Breaking Bad" }.item.id)) }
        awaitText("A chemistry teacher diagnosed with cancer turns to making methamphetamine to secure his family's future.")
        // The last of the season's rows into view: the header and the first rows go up under the top bar.
        onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(hasText("Gray Matter", substring = true))
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
    }

    @Test
    fun rowsScrolledUnderTheTopBarDoNotShowThroughBesideTheWayBack() {
        val width = 1440
        screen(populated(), width = width, height = 640) { app ->
            scrolledShow(app)
            save("page-bar-scrolled")
            // The whole of the Back button's height (10 to 54 in a 64 bar), not only its upper part.
            val bright = brightInBar(width, top = 4, bottom = 56)
            assertTrue(bright < 40, "content shows through the top bar beside the way back: $bright bright pixels")
        }
    }

    @Test
    fun underAStatusBarTheBarIsClearDownToTheFootOfTheWayBack() {
        // A Pad 7 in landscape: about 1164 by 777, with its status bar drawn over the page (edge to edge).
        val width = 1164
        val status = 32
        screen(populated(), width = width, height = 777, bars = SystemBars(top = status.dp)) { app ->
            scrolledShow(app)
            save("page-bar-scrolled-tablet")
            val bright = brightInBar(width, top = status + 4, bottom = status + 56)
            assertTrue(bright < 40, "content shows through behind the Back button's lower half: $bright bright pixels")
        }
    }
}
