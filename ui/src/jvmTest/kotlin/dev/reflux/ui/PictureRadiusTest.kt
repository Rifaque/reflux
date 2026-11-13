package dev.reflux.ui

import androidx.compose.ui.unit.dp
import dev.reflux.design.Radius
import dev.reflux.ui.player.DOCK_PICTURE_RADIUS
import dev.reflux.ui.player.VideoPlacement
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/** The corners of the dock's picture (the shell's surface is clipped to them) and how they follow the dock's own. */
class PictureRadiusTest {
    @Test
    fun theDocksPictureIsOneStepBelowTheDockItIsPaddedInside() {
        // Nested shapes step down one size (Radius): the dock is XLARGE, the picture inside it is LARGE.
        assertEquals(Radius.LARGE.dp, DOCK_PICTURE_RADIUS)
        assertEquals(Radius.XLARGE, Radius.LARGE + dev.reflux.design.MiniPlayerDock.PADDING_DP)
    }

    @Test
    fun aPictureIsSquareAcrossTheWindowAndRoundedByHowFarItHasTravelledToTheDock() = runBlocking {
        val placement = VideoPlacement()
        assertEquals(0.dp, placement.pictureRadius)
        placement.docking.snapTo(0.5f)
        assertEquals(DOCK_PICTURE_RADIUS / 2, placement.pictureRadius)
        placement.docking.snapTo(1f)
        assertEquals(DOCK_PICTURE_RADIUS, placement.pictureRadius)
    }
}
