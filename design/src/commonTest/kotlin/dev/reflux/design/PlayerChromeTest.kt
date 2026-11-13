package dev.reflux.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerChromeTest {
    private fun desktop(w: Float, h: Float) = PlayerChrome.tier(w, h, windowed = true, barTransport = true)

    @Test
    fun aDesktopWindowsRoomPicksTheTier() {
        assertEquals(PlayerChromeTier.COMPACT, desktop(1199f, 900f))
        assertEquals(PlayerChromeTier.COMPACT, desktop(1400f, 699f))
        assertEquals(PlayerChromeTier.STANDARD, desktop(1200f, 700f))
        assertEquals(PlayerChromeTier.STANDARD, desktop(1679f, 1000f))
        assertEquals(PlayerChromeTier.STANDARD, desktop(1680f, 944f))
        assertEquals(PlayerChromeTier.LARGE, desktop(1680f, 945f))
        assertEquals(PlayerChromeTier.LARGE, desktop(2048f, 1152f))
        assertEquals(PlayerChromeTier.LARGE, desktop(2560f, 1440f))
    }

    @Test
    fun handheldsAndTelevisionsNeverGrowWithTheScreen() {
        // No window (Android's fullscreen is the activity), or a transport that is not the pointer's bar.
        assertEquals(PlayerChromeTier.BASELINE, PlayerChrome.tier(3840f, 2160f, windowed = false, barTransport = true))
        assertEquals(PlayerChromeTier.BASELINE, PlayerChrome.tier(3840f, 2160f, windowed = true, barTransport = false))
        assertEquals(PlayerChromeTier.BASELINE, PlayerChrome.tier(2560f, 1440f, windowed = false, barTransport = false))
    }

    @Test
    fun sizesOnlyGrowFromCompactToLarge() {
        val tiers = listOf(PlayerChromeTier.COMPACT, PlayerChromeTier.STANDARD, PlayerChromeTier.LARGE).map(PlayerChrome::spec)
        tiers.zipWithNext { a, b ->
            assertTrue(b.buttonDp >= a.buttonDp && b.playButtonDp >= a.playButtonDp && b.clockSp >= a.clockSp && b.titleSp >= a.titleSp)
            assertTrue(b.trackDp >= a.trackDp && b.thumbDp >= a.thumbDp && b.timelineHitDp >= a.timelineHitDp && b.barMaxWidthDp >= a.barMaxWidthDp)
        }
        assertTrue(tiers.last().clockSp > tiers.first().clockSp && tiers.last().buttonDp > tiers.first().buttonDp)
    }

    @Test
    fun theTimelineIsAThinLineWithAMuchLargerTargetAtEveryTier() {
        PlayerChromeTier.entries.map(PlayerChrome::spec).forEach { spec ->
            assertTrue(spec.timelineHitDp >= spec.trackActiveDp * 3.5f, "${spec.tier}: the target is generous around the line")
            assertTrue(spec.trackActiveDp <= 8f && spec.trackDp <= 5f, "${spec.tier}: the line stays thin")
            assertTrue(spec.thumbActiveDp <= spec.timelineHitDp, "${spec.tier}: the thumb fits the target")
        }
    }

    @Test
    fun theBaselineIsWhatHandheldsAndTelevisionsHadBeforeTiers() {
        val b = PlayerChrome.spec(PlayerChromeTier.BASELINE)
        assertEquals(40f, b.buttonDp)
        assertEquals(44f, b.playButtonDp)
        assertEquals(13f, b.clockSp)
        assertEquals(30f, b.titleSp)
        assertEquals(4f, b.trackDp)
        assertEquals(6f, b.trackActiveDp)
        assertEquals(28f, b.timelineHitDp)
        assertEquals(0f, b.thumbDp)
        assertEquals(0.26f, b.inactiveTrackAlpha)
        assertEquals(1180f, b.barMaxWidthDp)
        assertEquals(104f, b.cornerLiftDp)
    }

    @Test
    fun aCompactWindowKeepsTodaysSizes() {
        val b = PlayerChrome.spec(PlayerChromeTier.BASELINE)
        val c = PlayerChrome.spec(PlayerChromeTier.COMPACT)
        assertEquals(b.buttonDp, c.buttonDp)
        assertEquals(b.clockSp, c.clockSp)
        assertEquals(b.barMaxWidthDp, c.barMaxWidthDp)
    }
}
