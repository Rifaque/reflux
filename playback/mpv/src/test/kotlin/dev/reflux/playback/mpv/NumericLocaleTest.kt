package dev.reflux.playback.mpv

import dev.reflux.core.playback.PlaybackRequest
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.source.PlaybackTarget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B-031: libmpv refuses a process whose numeric locale is not "C". Run on Linux under a locale such as de_DE.UTF-8
 * (`LANG=de_DE.UTF-8 ./gradlew :playback:mpv:test`), these tests are the reproduction; on Windows, or without libmpv,
 * the engine tests skip themselves and only the rule is checked.
 */
class NumericLocaleTest {
    private val linux = System.getProperty("os.name").startsWith("Linux")

    @Test
    fun onlyExactlyCIsLeftAlone() {
        assertFalse(NumericLocale.needsChange("C"))
        assertFalse(NumericLocale.needsChange(null), "an unknown locale is not guessed at")
        assertTrue(NumericLocale.needsChange("de_DE.UTF-8"))
        assertTrue(NumericLocale.needsChange("fr_FR.UTF-8"))
        assertTrue(NumericLocale.needsChange("C.UTF-8"))
        assertTrue(NumericLocale.needsChange("POSIX"))
    }

    @Test
    fun windowsIsLeftAsItIs() {
        assumeTrue("Windows only", System.getProperty("os.name").startsWith("Windows"))
        assertNull(NumericLocale.prepareProcess(), "nothing is managed there")
        assertNull(NumericLocale.adjustedFrom)
    }

    @Test
    fun onLinuxTheNumericLocaleIsCAndNothingElseChanges() {
        assumeTrue("Linux only", linux)
        val language = java.util.Locale.getDefault()
        assertEquals("C", NumericLocale.prepareProcess())
        assertEquals("C", NumericLocale.current())
        // Java's own locale, which formats everything the interface shows, is the person's still.
        assertEquals(language, java.util.Locale.getDefault())
        // Asked again, nothing is written: it is already right.
        val before = NumericLocale.adjustedFrom
        assertEquals("C", NumericLocale.prepareProcess())
        assertEquals(before, NumericLocale.adjustedFrom)
        System.getenv("LANG")?.takeIf { it.startsWith("de_") }?.let { assertEquals(it, NumericLocale.adjustedFrom, "it was the person's locale before") }
    }

    @Test
    fun theEngineStartsAndStopsFiftyTimesAndPlaysALocalFile() {
        assumeTrue("libmpv not installed", MpvPlayer.isAvailable)
        val device = DesktopCapabilities.of(dev.reflux.core.playback.DisplayCapabilities(1920, 1080))
        repeat(50) { round ->
            val player = assertNotNull(MpvPlayer.create(device, mapOf("vo" to "null", "ao" to "null")), "engine $round: ${MpvPlayer.lastFailure}")
            player.close()
        }
        assertNull(MpvPlayer.lastFailure)
        assumeTrue("needs FFmpeg", TestMedia.ffmpegAvailable)
        val directory = Files.createTempDirectory("reflux-locale")
        val file = TestMedia.multiTrackMkv(directory)
        MpvPlayer.create(device, mapOf("vo" to "null", "ao" to "null"))!!.use { player ->
            player.load(PlaybackRequest(PlaybackTarget(file.toUri().toString()), "Clip"))
            val state = runBlocking { withTimeout(20_000) { player.state.first { it.status == PlayerStatus.PLAYING && it.positionMs > 200 } } }
            assertTrue(state.positionMs > 200)
            // A number the engine reads from a string, as Reflux sets them: in a comma locale "1.5" would not be one and a half.
            player.setSpeed(1.5)
            assertEquals(1.5, runBlocking { withTimeout(5_000) { player.state.first { it.speed == 1.5 } } }.speed)
        }
        directory.toFile().deleteRecursively()
    }
}
