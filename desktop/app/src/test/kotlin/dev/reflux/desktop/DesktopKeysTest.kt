package dev.reflux.desktop

import dev.reflux.core.input.InputContext
import dev.reflux.core.input.InputMapping
import dev.reflux.core.input.Key
import dev.reflux.core.input.SemanticAction
import java.awt.Component
import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Media keys and held keys, at the desktop shell's edge: the toolkit's key events become the product's keys, which the
 * input model already maps (docs/INPUT_MODEL.md); no playback code sees a platform's key number.
 */
class DesktopKeysTest {
    private val source = object : Component() {}

    /**
     * What a focused AWT window was given on Windows 11 (JDK 22) for each media key pressed with a synthetic key event
     * (VERIFICATION.md, "M10: probe P3"): no key code, and the system's virtual-key in `rawCode`.
     */
    private fun observed(raw: Int, scancode: Int) =
        "java.awt.event.KeyEvent[KEY_PRESSED,keyCode=0,keyText=Unknown keyCode: 0x0,keyChar=Undefined keyChar,keyLocation=KEY_LOCATION_STANDARD,rawCode=$raw,primaryLevelUnicode=0,scancode=$scancode,extendedKeyCode=0x0] on frame0"

    @Test
    fun theMediaKeysAWindowsKeyboardSendAreTheProductsMediaKeys() {
        assertEquals(Key.MEDIA_PLAY_PAUSE, mediaKeyOf(observed(179, 34), "Windows 11"))
        assertEquals(Key.MEDIA_NEXT, mediaKeyOf(observed(176, 25), "Windows 11"))
        assertEquals(Key.MEDIA_PREVIOUS, mediaKeyOf(observed(177, 16), "Windows 11"))
        assertEquals(Key.MEDIA_STOP, mediaKeyOf(observed(178, 36), "Windows 11"))
        assertEquals(Key.MEDIA_PLAY, mediaKeyOf(observed(250, 0), "Windows 10"))
    }

    @Test
    fun theVolumeKeysAreTheSystemsAndNothingElseIsGuessedAt() {
        assertNull(mediaKeyOf(observed(173, 32), "Windows 11"), "mute: the system does it; Reflux doing it too would be twice")
        assertNull(mediaKeyOf(observed(174, 46), "Windows 11"))
        assertNull(mediaKeyOf(observed(175, 48), "Windows 11"))
        assertNull(mediaKeyOf(observed(0, 0), "Windows 11"))
        assertNull(mediaKeyOf("java.awt.event.KeyEvent[KEY_PRESSED,keyCode=0,keyText=Unknown keyCode: 0x0]", "Windows 11"), "no raw code, no key")
        assertNull(mediaKeyOf(observed(179, 34), "Linux"), "another system's numbers are not Windows'")
        assertNull(mediaKeyOf(observed(179, 34), "Mac OS X"))
    }

    @Test
    fun aMediaKeyEventBecomesTheMediaKeyWithoutAKeyCode() {
        // A real event cannot be given a raw code; what the toolkit would make of one is its description, asserted above. Here,
        // that an event with no code and no description of a media key is still nothing, and that ordinary keys are untouched.
        val unknown = KeyEvent(source, KeyEvent.KEY_PRESSED, 0L, 0, KeyEvent.VK_UNDEFINED, KeyEvent.CHAR_UNDEFINED)
        assertNull(unknown.toRefluxKey())
        assertEquals(Key.SPACE, KeyEvent(source, KeyEvent.KEY_PRESSED, 0L, 0, KeyEvent.VK_SPACE, ' ').toRefluxKey())
    }

    @Test
    fun theMediaKeysMeanWhatTheInputModelSays() {
        // The product's own mapping, which is not the shell's to know: play or pause, next, previous, stop.
        assertEquals(SemanticAction.PLAY_PAUSE, InputMapping.map(Key.MEDIA_PLAY_PAUSE, InputContext.PLAYBACK))
        assertEquals(SemanticAction.PLAY_PAUSE, InputMapping.map(Key.MEDIA_PLAY_PAUSE, InputContext.BROWSE))
        assertEquals(SemanticAction.NEXT, InputMapping.map(Key.MEDIA_NEXT, InputContext.BROWSE))
        assertEquals(SemanticAction.PREVIOUS, InputMapping.map(Key.MEDIA_PREVIOUS, InputContext.BROWSE))
        assertEquals(SemanticAction.STOP, InputMapping.map(Key.MEDIA_STOP, InputContext.BROWSE))
    }

    // Held keys ---------------------------------------------------------------------------------------------------

    private class Clock {
        var now = 0L
        private val due = ArrayList<Triple<Long, () -> Unit, Int>>()
        private var next = 0

        fun schedule(delay: Long, action: () -> Unit): () -> Unit {
            val id = next++
            due += Triple(now + delay, action, id)
            return { due.removeAll { it.third == id } }
        }

        fun advance(ms: Long) {
            now += ms
            due.filter { it.first <= now }.sortedBy { it.first }.forEach { entry ->
                if (due.remove(entry)) entry.second()
            }
        }
    }

    @Test
    fun aKeyHeldOnWindowsRepeatsWithNoReleaseBetween() {
        val clock = Clock()
        val let = ArrayList<Key>()
        val repeats = KeyRepeats(clock::schedule, let::add)
        assertFalse(repeats.pressed(Key.ARROW_RIGHT), "the first press is a press")
        clock.advance(30)
        assertTrue(repeats.pressed(Key.ARROW_RIGHT))
        clock.advance(30)
        assertTrue(repeats.pressed(Key.ARROW_RIGHT))
        repeats.released(Key.ARROW_RIGHT)
        assertTrue(let.isEmpty(), "a release is believed after its grace")
        clock.advance(KeyRepeats.RELEASE_GRACE_MS)
        assertEquals(listOf(Key.ARROW_RIGHT), let)
        assertFalse(repeats.pressed(Key.ARROW_RIGHT), "and the next press is new")
    }

    @Test
    fun aKeyHeldOnX11IsAReleaseAndAPressAMomentApartAndStillHeld() {
        val clock = Clock()
        val let = ArrayList<Key>()
        val repeats = KeyRepeats(clock::schedule, let::add)
        assertFalse(repeats.pressed(Key.ARROW_LEFT))
        repeat(10) {
            clock.advance(30)
            repeats.released(Key.ARROW_LEFT)
            clock.advance(2)
            assertTrue(repeats.pressed(Key.ARROW_LEFT), "a press right after a release is the held key")
        }
        assertTrue(let.isEmpty(), "the key is still held")
        repeats.released(Key.ARROW_LEFT)
        clock.advance(KeyRepeats.RELEASE_GRACE_MS)
        assertEquals(listOf(Key.ARROW_LEFT), let)
    }

    @Test
    fun eachKeyIsHeldOnItsOwn() {
        val clock = Clock()
        val let = ArrayList<Key>()
        val repeats = KeyRepeats(clock::schedule, let::add)
        repeats.pressed(Key.ARROW_RIGHT)
        repeats.pressed(Key.SPACE)
        repeats.released(Key.SPACE)
        clock.advance(KeyRepeats.RELEASE_GRACE_MS)
        assertEquals(listOf(Key.SPACE), let)
        assertTrue(repeats.pressed(Key.ARROW_RIGHT), "the other is held still")
        repeats.released(Key.ESCAPE)
        clock.advance(100)
        assertEquals(listOf(Key.SPACE), let, "a key never pressed is never let go")
    }
}
