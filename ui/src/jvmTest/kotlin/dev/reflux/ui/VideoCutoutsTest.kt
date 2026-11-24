package dev.reflux.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import dev.reflux.ui.player.LocalVideoCutouts
import dev.reflux.ui.player.VideoCutouts
import dev.reflux.ui.state.AppState
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * B-084 (CACHY-002): where the video surface is a native window above the toolkit's content and is shaped around what is drawn
 * over it (Linux), the player says where its controls are. While they show, the areas cover the top row and the bottom block,
 * inside the window; when playback ends, none is left, so the picture of the next one is whole.
 */
@OptIn(ExperimentalTestApi::class)
class VideoCutoutsTest {
    private fun DesktopComposeUiTest.playing(app: AppState, platform: FakePlatform) {
        runOnIdle { app.play(app.library.movies().first { it.item.title == "Heat" }.item.id) }
        await("the player") { platform.players.isNotEmpty() && app.playback != null }
        await("the timeline") { onAllNodes(hasContentDescription("Timeline", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun theControlsSayWhereTheyAreAndLeaveNothingBehind() {
        val cutouts = VideoCutouts()
        val platform = FakePlatform()
        screen(populated(), platform, around = { content -> CompositionLocalProvider(LocalVideoCutouts provides cutouts) { content() } }) { app ->
            awaitText("Recently Added")
            assertTrue(cutouts.rects.isEmpty(), "nothing is cut before anything plays")
            playing(app, platform)
            await("the controls' areas") { cutouts.rects.size >= 2 }
            val areas = cutouts.rects
            val window = 1440f to 900f
            assertTrue(areas.all { it.left >= 0f && it.top >= 0f && it.right <= window.first + 0.5f && it.bottom <= window.second + 0.5f }, "inside the window: $areas")
            assertTrue(areas.any { it.top <= 1f && it.width >= window.first - 1f }, "the top row, across the window: $areas")
            assertTrue(areas.any { it.bottom >= window.second * 0.85f && it.top >= window.second * 0.6f }, "the bottom block: $areas")
            // A picture with nothing over it keeps every pixel: the areas are the controls, not the whole window.
            assertTrue(areas.sumOf { (it.width * it.height).toDouble() } < window.first * window.second * 0.5, "the controls, not the picture: $areas")

            runOnIdle { app.playback?.close() }
            await("playback ended") { app.playback == null }
            await("nothing left cut") { cutouts.rects.isEmpty() }
        }
    }

    @Test
    fun withoutAShapedSurfaceNothingIsCollected() {
        // Windows and Android provide no collector: the player draws as it always has.
        val platform = FakePlatform()
        screen(populated(), platform) { app ->
            awaitText("Recently Added")
            playing(app, platform)
            assertTrue(onAllNodes(hasContentDescription("Timeline", substring = true)).fetchSemanticsNodes().isNotEmpty())
        }
    }
}
