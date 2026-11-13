package dev.reflux.android

import dev.reflux.core.playback.PlayerStatus
import dev.reflux.ui.state.PlayerPresentation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** When leaving the app shrinks it into the system's window, what shape that is, and whether leaving the window was expanding it (docs/planning/M10-PLAYER-SPEC.md, section 6). */
class PictureInPictureTest {
    private fun eligible(
        presentation: PlayerPresentation? = PlayerPresentation.FULL,
        status: PlayerStatus? = PlayerStatus.PLAYING,
        failed: Boolean = false,
        asked: Boolean = false,
    ) = PictureInPicture.eligible(presentation, status, failed, asked)

    @Test
    fun aPictureThatIsMovingGoesIntoTheWindowWhenTheAppIsLeft() {
        assertTrue(eligible())
        assertTrue(eligible(status = PlayerStatus.BUFFERING), "waiting for data is playing: the viewer is waiting for the picture")
        assertTrue(eligible(presentation = PlayerPresentation.MINI), "from the dock too")
    }

    @Test
    fun nothingElseDoes() {
        assertFalse(eligible(presentation = null, status = null), "nothing playing")
        assertFalse(eligible(status = PlayerStatus.PAUSED), "a paused picture left in a corner is not what leaving meant")
        assertFalse(eligible(status = PlayerStatus.LOADING))
        assertFalse(eligible(status = PlayerStatus.ENDED))
        assertFalse(eligible(status = PlayerStatus.ERROR))
        assertFalse(eligible(failed = true), "a failure is read where it is")
        assertFalse(eligible(asked = true), "a question is not answered in a corner")
        assertFalse(eligible(presentation = PlayerPresentation.SYSTEM), "already there")
    }

    @Test
    fun theWindowTakesTheShapeOfThePictureWithinWhatTheSystemAccepts() {
        assertEquals(1777 to 1000, PictureInPicture.shape(16f / 9f))
        assertEquals(2390 to 1000, PictureInPicture.shape(2.76f), "a scope film is the widest the system takes")
        assertEquals(420 to 1000, PictureInPicture.shape(0.2f), "and a tall one the narrowest")
        assertNull(PictureInPicture.shape(null), "unknown: no shape is claimed")
        assertNull(PictureInPicture.shape(0f))
        assertNull(PictureInPicture.shape(Float.NaN))
        assertNull(PictureInPicture.shape(Float.POSITIVE_INFINITY))
        val (w, h) = PictureInPicture.shape(2.39f)!!
        assertTrue(w.toFloat() / h <= 2.39f + 0.0001f, "never outside the range, which would throw")
    }

    @Test
    fun aWindowLeftWhileTheActivityIsStillStartedWasExpanded() {
        assertTrue(PictureInPicture.expanded(activityStarted = true))
        assertFalse(PictureInPicture.expanded(activityStarted = false), "a closed window stops the activity")
    }
}
