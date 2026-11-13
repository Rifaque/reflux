package dev.reflux.ui

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import dev.reflux.ui.state.AppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The desktop player's controls grow with the room a window gives them, and only there: the same design, drawn larger on a
 * large window or fullscreen, the timeline easier to see and to grab, the arrangement unchanged (design/PlayerChrome.kt).
 */
@OptIn(ExperimentalTestApi::class)
class PlayerChromeLayoutTest {
    private class Measure(val pause: Float, val timelineHeight: Float, val timelineWidth: Float, val clockHeight: Float)

    /** Opens the first episode of Breaking Bad paused in its intro (the controls stay up) in a window of [width] x [height] dp. */
    private fun measured(width: Int, height: Int, television: Boolean = false, block: DesktopComposeUiTest.(AppState, Measure) -> Unit) {
        screen(populated(), FakePlatform(television = television), width, height) { app ->
            awaitText("Recently Added")
            runOnIdle { app.play(app.library.shows().first { it.item.title == "Breaking Bad" }.item.id) }
            awaitText("S1 · E1 · Pilot")
            val platform = app.platform as FakePlatform
            platform.players.single().apply { playTo(95_000) }.pause()
            waitForIdle()
            val pause = onNodeWithContentDescription(if (television) "Timeline" else "Play").fetchSemanticsNode().size
            val timeline = onNodeWithContentDescription("Timeline").fetchSemanticsNode().size
            block(app, Measure(pause.height.toFloat(), timeline.height.toFloat(), timeline.width.toFloat(), 0f))
        }
    }

    private fun sizes(width: Int, height: Int): Measure {
        var found: Measure? = null
        measured(width, height) { _, m -> found = m }
        return found!!
    }

    @Test
    fun theControlsAndTheTimelineGrowWithTheWindow() {
        val compact = sizes(1100, 700)
        val standard = sizes(1440, 900)
        val large = sizes(2560, 1440)
        assertTrue(compact.pause < standard.pause && standard.pause < large.pause, "play/pause: ${compact.pause} < ${standard.pause} < ${large.pause}")
        assertTrue(compact.timelineHeight < standard.timelineHeight && standard.timelineHeight < large.timelineHeight, "the timeline's target grows")
        assertEquals(44f, compact.pause, 1f, "a compact window keeps today's play button")
        assertEquals(28f, compact.timelineHeight, 1f, "and today's timeline target")
        assertEquals(68f, large.pause, 1f)
        assertEquals(44f, large.timelineHeight, 1f)
    }

    @Test
    fun theBarStaysInsideItsMaximumWidthOnAHugeWindow() {
        val large = sizes(2560, 1440)
        // The bar is the timeline plus the controls beside it: never wider than the tier's maximum (1760 dp) or the window less its margins.
        assertTrue(large.timelineWidth < 1760f, "the timeline fits inside a bar of at most 1760 dp: ${large.timelineWidth}")
        assertTrue(large.timelineWidth > 700f, "and still has real width: ${large.timelineWidth}")
    }

    @Test
    fun aTelevisionsTimelineIsNotChangedByASmallerOrLargerWindow() {
        var small = 0f
        var big = 0f
        measured(1280, 720, television = true) { _, m -> small = m.timelineHeight }
        measured(3840, 2160, television = true) { _, m -> big = m.timelineHeight }
        assertEquals(small, big, "a television is sized by distance, not by pixels")
        assertEquals(28f, small, 1f)
    }
}
