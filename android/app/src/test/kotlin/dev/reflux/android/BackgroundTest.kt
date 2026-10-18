package dev.reflux.android

import dev.reflux.android.launcher.WatchNextPublisher
import dev.reflux.android.playback.SystemControls
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import androidx.media3.common.Player as EnginePlayer

/**
 * What Reflux does on Android while it is out of sight, and what it leaves to the platform: it does not play
 * in the background, whatever reaches its media session, and it keeps Watch Next programs only where a
 * launcher shows them (DECISIONS.md, 2026-10-18).
 */
class BackgroundTest {
    /** An engine that only records what it was asked to do. */
    private class Engine {
        val calls = mutableListOf<String>()
        val player: EnginePlayer = Proxy.newProxyInstance(EnginePlayer::class.java.classLoader, arrayOf(EnginePlayer::class.java)) { _, method, args ->
            calls += method.name + (args?.joinToString(",", "(", ")").orEmpty())
            when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                java.lang.Float.TYPE -> 0f
                else -> null
            }
        } as EnginePlayer
    }

    /** Everything the platform's controls can ask of a session that would start or move playback. */
    private fun SystemControls.everythingThatStartsPlayback() {
        play()
        prepare()
        playWhenReady = true
        seekToDefaultPosition()
        seekToDefaultPosition(0)
        seekTo(5_000)
        seekTo(0, 5_000)
        seekBack()
        seekForward()
        seekToNext()
        seekToNextMediaItem()
        seekToPrevious()
        seekToPreviousMediaItem()
    }

    @Test
    fun outOfSightThePlayKeyIsNotObeyed() {
        val engine = Engine()
        val skips = mutableListOf<Boolean>()
        val controls = SystemControls(engine.player, inSight = { false }, onSkip = { skips += it })

        // A headset, a Bluetooth device, or a remote's play key reaching the session with Reflux in the background.
        controls.everythingThatStartsPlayback()
        assertEquals(emptyList(), engine.calls, "nothing resumed the work or prepared the engine again (a decoder and audio focus)")
        assertEquals(emptyList(), skips, "nor moved to another work")

        // Pausing and stopping are still the session's: they never start anything.
        controls.pause()
        controls.playWhenReady = false
        controls.stop()
        assertEquals(listOf("pause", "setPlayWhenReady(false)", "stop"), engine.calls)
    }

    @Test
    fun inSightTheSystemsControlsAreTheEngines() {
        val engine = Engine()
        val skips = mutableListOf<Boolean>()
        val controls = SystemControls(engine.player, inSight = { true }, onSkip = { skips += it })

        controls.everythingThatStartsPlayback()
        assertEquals(
            listOf(
                "play", "prepare", "setPlayWhenReady(true)", "seekToDefaultPosition", "seekToDefaultPosition(0)",
                "seekTo(5000)", "seekTo(0,5000)", "seekBack", "seekForward",
            ),
            engine.calls,
        )
        // Moving between works is Reflux's, not the engine's: it holds one item.
        assertEquals(listOf(true, true, false, false), skips)
    }

    @Test
    fun theSameControlsFollowTheAppInAndOutOfSight() {
        val engine = Engine()
        var seen = true
        val controls = SystemControls(engine.player, inSight = { seen }, onSkip = {})
        controls.play()
        seen = false
        controls.play()
        controls.prepare()
        seen = true
        controls.play()
        assertEquals(listOf("play", "play"), engine.calls)
    }

    @Test
    fun watchNextIsWrittenOnlyWhereALauncherShowsIt() {
        // Android TV and Google TV: the TV provider, and a launcher that reads it.
        assertTrue(WatchNextPublisher.publishes(tvProvider = true, fireOs = false))
        // Fire OS has the provider too, but its home screen does not show the row.
        assertFalse(WatchNextPublisher.publishes(tvProvider = true, fireOs = true))
        // Phones and tablets have no provider.
        assertFalse(WatchNextPublisher.publishes(tvProvider = false, fireOs = false))
        assertFalse(WatchNextPublisher.publishes(tvProvider = false, fireOs = true))
        assertEquals("amazon.hardware.fire_tv", WatchNextPublisher.FIRE_TV_FEATURE)
    }
}
