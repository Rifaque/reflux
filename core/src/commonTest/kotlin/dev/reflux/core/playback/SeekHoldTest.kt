package dev.reflux.core.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SeekHoldTest {
    /** Key repeats arrive every [every] ms from a press at 0, until [until]; returns the hold and the last target shown. */
    private fun hold(every: Long, until: Long, direction: Int = 1, position: Long = 600_000, duration: Long? = 7_200_000): Pair<SeekHold, Long?> {
        val hold = SeekHold()
        hold.press(direction, position, duration, 0)
        var last: Long? = null
        var t = every
        while (t <= until) {
            last = hold.repeat(t) ?: last
            t += every
        }
        return hold to last
    }

    @Test
    fun aSinglePressIsTheTenSecondSeekItAlwaysWas() {
        val hold = SeekHold()
        assertEquals(610_000, hold.press(1, 600_000, 7_200_000, 0))
        assertNull(hold.target, "nothing beyond the press to show")
        assertNull(hold.release(), "and nothing more to seek on release")
        assertFalse(hold.active)
        assertEquals(590_000, hold.press(-1, 600_000, 7_200_000, 5_000))
    }

    @Test
    fun theTargetMovesWithTimeHeldAndNotWithHowOftenTheKeyRepeats() {
        val slowRemote = hold(every = 100, until = 2_000).second
        val fastKeyboard = hold(every = 50, until = 2_000).second
        // 10 s a second for the first second, 30 s a second for the next: 10 + 30 on top of the press's ten.
        assertEquals(600_000 + 10_000 + 10_000 + 30_000, slowRemote)
        assertEquals(slowRemote, fastKeyboard)
    }

    @Test
    fun itAcceleratesInTiers() {
        assertEquals(600_000 + 10_000 + 10_000, hold(100, 1_000).second)
        assertEquals(600_000 + 10_000 + 10_000 + 60_000, hold(100, 3_000).second)
        // Past three seconds: 60 s a second.
        assertEquals(600_000 + 10_000 + 10_000 + 60_000 + 120_000, hold(100, 5_000).second)
    }

    @Test
    fun theTargetIsOneSeekMadeOnRelease() {
        val (hold, shown) = hold(100, 2_000)
        assertEquals(shown, hold.target)
        assertEquals(shown, hold.release())
        assertFalse(hold.active)
        assertNull(hold.release(), "once")
    }

    @Test
    fun itNeverGoesPastEitherEnd() {
        assertEquals(7_200_000, hold(100, 60_000, 1, 7_190_000).second)
        assertEquals(0, hold(100, 60_000, -1, 20_000).second)
        // No known duration: only the start is a limit.
        assertEquals(0, hold(100, 60_000, -1, 20_000, null).second)
        assertTrue(hold(100, 5_000, 1, 0, null).second!! > 100_000)
    }

    @Test
    fun aReversalIsAFreshHoldFromWhereTheViewerIs() {
        val hold = SeekHold()
        hold.press(1, 100_000, 1_000_000, 0)
        (1..20).forEach { hold.repeat(it * 100L) }
        val forward = hold.target!!
        assertTrue(forward > 110_000)
        // Left is pressed with the viewer at the hold's target: that is the base, at the ten-second pace again.
        assertEquals(forward - 10_000, hold.press(-1, forward, 1_000_000, 2_100))
        assertNull(hold.target)
        hold.repeat(2_600)
        assertEquals(forward - 10_000 - 5_000, hold.target)
    }

    @Test
    fun aPressIsAlwaysFreshFromWhereTheViewerIsHoweverSoonItFollowsAnother() {
        val hold = SeekHold()
        hold.press(1, 100_000, 1_000_000, 0)
        hold.repeat(500)
        assertEquals(210_000, hold.press(1, 200_000, 1_000_000, 600))
        assertNull(hold.target)
    }

    @Test
    fun repeatsWithNoHoldDoNothing() {
        assertNull(SeekHold().repeat(1_000))
    }
}
