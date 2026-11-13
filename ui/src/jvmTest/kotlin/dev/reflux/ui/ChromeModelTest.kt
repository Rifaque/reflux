package dev.reflux.ui

import dev.reflux.core.playback.PlayerStatus
import dev.reflux.ui.player.ChromeFacts
import dev.reflux.ui.player.ChromeHold
import dev.reflux.ui.player.ChromeMode
import dev.reflux.ui.player.Pin
import dev.reflux.ui.player.chrome
import dev.reflux.ui.player.layers
import dev.reflux.ui.player.nextChange
import dev.reflux.ui.state.PlayerPresentation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The chrome is one derived value (docs/planning/M10-PLAYER-SPEC.md, section 7): no combination of facts draws a loader beside controls. */
class ChromeModelTest {
    private fun facts(
        status: PlayerStatus = PlayerStatus.PLAYING,
        presentation: PlayerPresentation = PlayerPresentation.FULL,
        bufferingSince: Long? = null,
        failed: Boolean = false,
        postPlay: Boolean = false,
        transition: Boolean = false,
    ) = ChromeFacts(presentation, status, bufferingSince, failed, postPlay, transition)

    private fun hold(until: Long = 0, vararg pins: Pin, dismissed: Boolean = false) = ChromeHold(until, pins.toSet(), dismissed)

    @Test
    fun onlyTheFullPlayerHasChrome() {
        for (presentation in listOf(PlayerPresentation.MINI, PlayerPresentation.SYSTEM)) {
            for (status in PlayerStatus.entries) {
                assertEquals(ChromeMode.NONE, chrome(facts(status, presentation, failed = true, postPlay = true), hold(10_000, Pin.MENU), 0), "$presentation $status")
            }
        }
    }

    @Test
    fun enteringTheSystemsWindowClearsTheChromeFirst() {
        assertEquals(ChromeMode.NONE, chrome(facts(transition = true), hold(10_000), 0))
        assertEquals(ChromeMode.NONE, chrome(facts(PlayerStatus.LOADING, transition = true), hold(), 0))
    }

    @Test
    fun theOrderIsFailureThenTheNextEpisodeThenLoadingThenShown() {
        val pinned = hold(10_000, Pin.MENU, Pin.PAUSED)
        assertEquals(ChromeMode.FAILURE, chrome(facts(PlayerStatus.LOADING, failed = true, postPlay = true), pinned, 0))
        assertEquals(ChromeMode.ENDING, chrome(facts(PlayerStatus.ENDED, postPlay = true), pinned, 0))
        assertEquals(ChromeMode.LOADING, chrome(facts(PlayerStatus.LOADING), pinned, 0))
        assertEquals(ChromeMode.SHOWN, chrome(facts(PlayerStatus.PAUSED), pinned, 0))
    }

    @Test
    fun openingIsLoadingAtOnceWhateverTheViewerWasDoing() {
        assertEquals(ChromeMode.LOADING, chrome(facts(PlayerStatus.LOADING), hold(10_000, Pin.SCRUBBING, Pin.SEEK_HOLD, Pin.SCREEN_READER), 0))
    }

    @Test
    fun aShortBufferingShowsNothingNew() {
        val buffering = facts(PlayerStatus.BUFFERING, bufferingSince = 1_000)
        assertEquals(ChromeMode.HIDDEN, chrome(buffering, hold(), 1_300))
        assertEquals(ChromeMode.SHOWN, chrome(buffering, hold(5_000), 1_300), "the controls that were up stay")
        assertEquals(ChromeMode.LOADING, chrome(buffering, hold(5_000), 1_600), "a wait that goes on is a wait")
        assertEquals(ChromeMode.LOADING, chrome(buffering, hold(), 9_000))
    }

    @Test
    fun aSeekOfTheViewersOwnIsNotAWaitTheyAreShownAsOne() {
        val buffering = facts(PlayerStatus.BUFFERING, bufferingSince = 0)
        assertEquals(ChromeMode.SHOWN, chrome(buffering, hold(0, Pin.SCRUBBING), 5_000))
        assertEquals(ChromeMode.SHOWN, chrome(buffering, hold(0, Pin.SEEK_HOLD), 5_000))
    }

    @Test
    fun theControlsLeaveWhenTheirTimeIsUpAndNothingPinsThem() {
        assertEquals(ChromeMode.SHOWN, chrome(facts(), hold(3_000), 2_999))
        assertEquals(ChromeMode.HIDDEN, chrome(facts(), hold(3_000), 3_000))
        assertEquals(ChromeMode.SHOWN, chrome(facts(), hold(3_000, Pin.POINTER_OVER_CONTROLS), 9_000))
        assertEquals(ChromeMode.SHOWN, chrome(facts(PlayerStatus.PAUSED), hold(0, Pin.PAUSED), 9_000))
        assertEquals(ChromeMode.SHOWN, chrome(facts(), hold(0, Pin.FOCUS_IN_CONTROLS), 9_000))
    }

    @Test
    fun aScreenReadersControlsNeverLeave() {
        assertEquals(ChromeMode.SHOWN, chrome(facts(), hold(0, Pin.SCREEN_READER, dismissed = true), 1_000_000))
        assertEquals(ChromeMode.SHOWN, chrome(facts(), hold(0, Pin.MENU, dismissed = true), 1_000_000))
    }

    @Test
    fun aTouchOnThePicturePutsAwayWhatOnlySoftlyPinned() {
        assertEquals(ChromeMode.HIDDEN, chrome(facts(PlayerStatus.PAUSED), hold(0, Pin.PAUSED, Pin.POINTER_OVER_CONTROLS, Pin.FOCUS_IN_CONTROLS, dismissed = true), 0))
        assertEquals(ChromeMode.SHOWN, chrome(facts(PlayerStatus.PAUSED), hold(0, Pin.PAUSED, Pin.POINTER_OVER_CONTROLS, Pin.FOCUS_IN_CONTROLS), 0))
    }

    @Test
    fun noCombinationOfFactsDrawsALoaderBesideControls() {
        val pinSets = listOf(emptySet(), Pin.entries.toSet(), setOf(Pin.PAUSED), setOf(Pin.MENU), setOf(Pin.SCRUBBING, Pin.SEEK_HOLD))
        for (status in PlayerStatus.entries) for (since in listOf(null, 0L, 5_000L)) for (failed in listOf(false, true)) for (post in listOf(false, true)) {
            for (pins in pinSets) for (until in listOf(0L, 100_000L)) for (now in listOf(0L, 700L, 10_000L)) {
                val layers = chrome(facts(status, bufferingSince = since, failed = failed, postPlay = post), ChromeHold(until, pins), now).layers()
                if (layers.loader) {
                    assertFalse(layers.centreTransport || layers.bar || layers.menus || layers.skipOffer || layers.upNext || layers.failure, "a loader with something else: $status $since $failed $post $pins $until $now")
                }
                if (layers.failure) assertFalse(layers.loader || layers.bar || layers.centreTransport || layers.upNext)
            }
        }
    }

    @Test
    fun eachModeDrawsOnlyWhatItsLayersSay() {
        assertFalse(ChromeMode.LOADING.layers().centreTransport)
        assertFalse(ChromeMode.LOADING.layers().bar)
        assertTrue(ChromeMode.LOADING.layers().context, "the way out and the title stay")
        assertTrue(ChromeMode.LOADING.layers().loader)
        assertFalse(ChromeMode.SHOWN.layers().loader)
        assertFalse(ChromeMode.FAILURE.layers().minimize, "a failure is not put aside")
        assertFalse(ChromeMode.HIDDEN.layers().context)
        assertTrue(ChromeMode.HIDDEN.layers().skipOffer)
        assertFalse(ChromeMode.NONE.layers().context)
    }

    @Test
    fun thePlayerWaitsForOneDeadline() {
        assertEquals(3_000, nextChange(facts(), hold(3_000), 1_000))
        assertNull(nextChange(facts(), hold(3_000, Pin.MENU), 1_000), "pinned: only a new fact changes it")
        assertNull(nextChange(facts(), hold(500), 1_000), "already past")
        // Buffering with controls up: the end of the grace is first.
        assertEquals(1_600, nextChange(facts(PlayerStatus.BUFFERING, bufferingSince = 1_000), hold(9_000), 1_100))
        assertNull(nextChange(facts(PlayerStatus.BUFFERING, bufferingSince = 0), hold(), 5_000), "already loading")
        assertNull(nextChange(facts(presentation = PlayerPresentation.MINI), hold(9_000), 0))
        assertNull(nextChange(facts(failed = true), hold(9_000), 0))
    }
}
