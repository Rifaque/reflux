package dev.reflux.ui

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import dev.reflux.ui.state.Destination
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A page below the top level, beside a navigation that runs down the side (tablet, desktop), has its way back
 * floating in its top bar. Scrolled, its rows went on under that bar and showed through beside and behind the Back
 * button (a show's season tabs on a Xiaomi Pad 7, 2026-11-16): the television had a scrim there and the others had
 * none. Now the bar's band is kept clear once the page has moved, as on a television.
 */
@OptIn(ExperimentalTestApi::class)
class PageBarScrimTest {
    /**
     * How many pixels in the top bar's band, from just right of the Back button (x 300 at this size) across, are
     * bright: content showing through. The band is the bar's opaque part (the scrim fades out below 72% of it).
     */
    private fun androidx.compose.ui.test.DesktopComposeUiTest.brightInTopBar(width: Int, bandBottom: Int): Int {
        val pixels = onRoot().captureToImage().toPixelMap()
        var bright = 0
        for (x in 300 until width - 30) for (y in 4 until bandBottom) {
            val c = pixels[x, y]
            if (0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue > 0.25f) bright++
        }
        return bright
    }

    @Test
    fun rowsScrolledUnderTheTopBarDoNotShowThroughBesideTheWayBack() {
        val width = 1440
        screen(populated(), width = width, height = 640) { app ->
            awaitText("Recently Added")
            runOnIdle { app.navigator.open(Destination.Details(app.library.shows().first { it.item.title == "Breaking Bad" }.item.id)) }
            awaitText("A chemistry teacher diagnosed with cancer turns to making methamphetamine to secure his family's future.")
            // The last of the season's rows into view: the header and the first rows go up under the top bar.
            onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(hasText("Gray Matter", substring = true))
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            save("page-bar-scrolled")
            val bright = brightInTopBar(width, bandBottom = 44)
            assertTrue(bright < 40, "content shows through the top bar beside the way back: $bright bright pixels")
        }
    }
}
