package dev.reflux.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.core.playback.TrickplayManifest
import dev.reflux.core.playback.TrickplaySource
import dev.reflux.core.source.MediaSource
import dev.reflux.ui.player.TRICKPLAY_PICTURE
import dev.reflux.ui.player.TRICKPLAY_PREVIEW
import dev.reflux.ui.state.AppState
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Scene previews in the player (B-030) and the right stick through them (B-029), against a source that keeps
 * previews as Jellyfin does (tiled sheets), and against one that keeps none.
 */
@OptIn(ExperimentalTestApi::class)
class TrickplayUiTest {
    /** The source of [world], keeping previews: a picture every 10 s, 4 by 4 on each sheet; counts the sheets fetched. */
    private class Previews(world: TestWorld) : TrickplaySource, MediaSource by world.source {
        val asked = AtomicInteger()
        val fetched = mutableListOf<Int>()
        val manifest = TrickplayManifest.of("test", intervalMs = 10_000, count = 300, frameWidth = 32, frameHeight = 18, columns = 4, rows = 4)!!

        override suspend fun trickplay(path: String): TrickplayManifest = manifest.also { asked.incrementAndGet() }

        override suspend fun trickplaySheet(path: String, manifest: TrickplayManifest, sheet: Int): ByteArray {
            synchronized(fetched) { fetched += sheet }
            val surface = Surface.makeRasterN32Premul(32 * 4, 18 * 4)
            val paint = Paint().apply { color = Color.makeRGB((sheet * 40) % 255, 120, 200) }
            surface.canvas.drawRect(Rect.makeWH(32f * 4, 18f * 4), paint)
            return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
        }
    }

    private fun DesktopComposeUiTest.playing(app: AppState, platform: FakePlatform) {
        runOnIdle { app.play(app.library.movies().first { it.item.title == "Heat" }.item.id) }
        await("the player") { platform.players.isNotEmpty() && app.playback != null }
        await("the timeline") { exists("Timeline") }
    }

    private fun DesktopComposeUiTest.hoverTimeline(fraction: Float) {
        val bar = onNode(hasContentDescription("Timeline")).fetchSemanticsNode().boundsInRoot
        onRoot().performMouseInput { moveTo(Offset(bar.left + bar.width * fraction, bar.center.y)) }
        mainClock.advanceTimeBy(200)
        waitForIdle()
    }

    private fun DesktopComposeUiTest.exists(description: String) = onAllNodes(hasContentDescription(description)).fetchSemanticsNodes().isNotEmpty()

    private fun DesktopComposeUiTest.shown(tag: String) = onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aPointerOverTheTimelineShowsThatScene_andEachSheetIsFetchedOnce() {
        val world = populated()
        val previews = Previews(world)
        world.registry.register(previews)
        val platform = FakePlatform()
        screen(world, platform, width = 1440, height = 900) { app ->
            awaitText("Recently Added")
            playing(app, platform)
            await("the previews known") { app.playback?.previews?.manifest != null }
            assertTrue(previews.fetched.isEmpty(), "nothing is fetched before a place is looked at")
            hoverTimeline(0.1f)
            await("the scene's picture") { shown(TRICKPLAY_PICTURE) }
            save("trickplay-hover")
            // 10% of 50 minutes: 300 s, picture 30, on sheet 1 (16 a sheet).
            assertEquals(30, app.playback?.previews?.picture?.frame?.index)
            assertEquals(listOf(1), previews.fetched.toList())
            // Along the same sheet: no new fetch. Away and back: the kept sheet is used again.
            hoverTimeline(0.105f)
            hoverTimeline(0.5f)
            await("another sheet") { app.playback?.previews?.picture?.frame?.sheet == 9 }
            hoverTimeline(0.1f)
            await("back on the first") { app.playback?.previews?.picture?.frame?.index == 30 }
            assertEquals(listOf(1, 9), previews.fetched.toList(), "never the same sheet twice")
            // The pointer leaves: so does the preview, and playback never moved.
            onRoot().performMouseInput { moveTo(Offset(700f, 300f)) }
            mainClock.advanceTimeBy(200)
            await("the preview gone") { !shown(TRICKPLAY_PREVIEW) }
            assertTrue(platform.players.last().seeks.isEmpty(), "looking is not seeking")
        }
    }

    @Test
    fun withoutPreviewsTheTimelineShowsTheTimeAlone() {
        val platform = FakePlatform()
        screen(populated(), platform, width = 1440, height = 900) { app ->
            awaitText("Recently Added")
            playing(app, platform)
            await("known to have none") { app.playback?.previews?.known == true }
            assertNull(app.playback?.previews?.manifest)
            hoverTimeline(0.25f)
            await("the time") { shown(TRICKPLAY_PREVIEW) }
            assertFalse(shown(TRICKPLAY_PICTURE), "no picture where the source has none")
            save("trickplay-no-previews")
        }
    }

    /**
     * The stick's own timing (the idle timer, repeats while held) runs on the test's clock, so the clock is moved by hand
     * here: an idle wait would otherwise run through the 8 s idle timer at once.
     */
    private fun DesktopComposeUiTest.flick(app: AppState, value: Float) {
        runOnUiThread {
            app.onRightStick(value, ControllerFamily.XBOX)
            app.onRightStick(0f, ControllerFamily.XBOX)
        }
        mainClock.advanceTimeBy(48)
    }

    private fun DesktopComposeUiTest.until(what: String, condition: () -> Boolean) {
        repeat(200) {
            if (runOnUiThread(condition)) return
            mainClock.advanceTimeBy(16)
            Thread.sleep(5)
        }
        throw AssertionError("Timed out waiting for $what")
    }

    @Test
    fun theRightStickStepsByScenes_AGoesThere_BStays_andALostControllerCommitsNothing() {
        val world = populated()
        world.registry.register(Previews(world))
        val platform = FakePlatform()
        screen(world, platform, width = 1440, height = 900) { app ->
            awaitText("Recently Added")
            playing(app, platform)
            await("the previews known") { app.playback?.previews?.manifest != null }
            mainClock.autoAdvance = false
            val player = platform.players.last()
            runOnUiThread { player.playTo(125_000) }
            // A drift is nothing; a flick is one scene: from 2:05, the next picture is at 2:10.
            runOnUiThread { app.onRightStick(0.3f, ControllerFamily.XBOX); app.onRightStick(0f, ControllerFamily.XBOX) }
            assertNull(app.playback?.sceneScrub)
            flick(app, 0.9f)
            assertEquals(130_000, app.playback?.sceneScrub?.targetMs)
            flick(app, 0.9f)
            flick(app, -0.9f)
            flick(app, 0.9f)
            assertEquals(140_000, app.playback?.sceneScrub?.targetMs)
            assertEquals(125_000, app.playback?.sceneScrub?.originMs)
            until("its preview") { shown(TRICKPLAY_PICTURE) }
            save("trickplay-stick")
            assertTrue(player.seeks.isEmpty(), "nothing moves before A")
            runOnUiThread { app.onGamepadButton(GamepadButton.FACE_SOUTH, ControllerFamily.XBOX) }
            assertNull(app.playback?.sceneScrub, "A ended it")
            assertEquals(listOf(140_000L), player.seeks)
            assertEquals(dev.reflux.core.playback.PlayerStatus.PLAYING, player.state.value.status, "A went there; it did not pause")
            // B stays where playback is.
            flick(app, -0.9f)
            assertNotNull(app.playback?.sceneScrub)
            runOnUiThread { app.onGamepadButton(GamepadButton.FACE_EAST, ControllerFamily.XBOX) }
            assertNull(app.playback?.sceneScrub)
            assertEquals(listOf(140_000L), player.seeks, "B seeks nowhere")
            assertNotNull(app.playback, "and B only left the scenes, not the player")
            // The controller going away mid-scrub ends it without seeking.
            flick(app, 0.9f)
            runOnUiThread { app.onRightStick(null, ControllerFamily.XBOX) }
            assertNull(app.playback?.sceneScrub)
            // Left alone, a scrub ends by itself after 8 s, seeking nowhere.
            flick(app, 0.9f)
            mainClock.advanceTimeBy(7_000)
            assertNotNull(app.playback?.sceneScrub, "still there before its time")
            mainClock.advanceTimeBy(1_200)
            assertNull(app.playback?.sceneScrub, "gone after 8 s")
            assertEquals(listOf(140_000L), player.seeks)
        }
    }

    @Test
    fun aHeldStickRepeatsAndSpeedsUp() {
        val world = populated()
        world.registry.register(Previews(world))
        val platform = FakePlatform()
        screen(world, platform, width = 1440, height = 900) { app ->
            awaitText("Recently Added")
            playing(app, platform)
            await("the previews known") { app.playback?.previews?.manifest != null }
            mainClock.autoAdvance = false
            runOnUiThread { platform.players.last().playTo(0) }
            runOnUiThread { app.onRightStick(1f, ControllerFamily.XBOX) }
            // Held for a while (the stepper reads the wall clock between the ticks the test clock drives).
            repeat(60) { mainClock.advanceTimeBy(16); Thread.sleep(16) }
            runOnUiThread { app.onRightStick(0f, ControllerFamily.XBOX) }
            mainClock.advanceTimeBy(32)
            val target = assertNotNull(app.playback?.sceneScrub?.targetMs)
            assertTrue(target >= 30_000, "held about a second, several scenes on: $target")
        }
    }

    @Test
    fun withoutPreviewsTheStickStillStepsBySetAmounts() {
        val platform = FakePlatform()
        screen(populated(), platform, width = 1440, height = 900) { app ->
            awaitText("Recently Added")
            playing(app, platform)
            await("known to have none") { app.playback?.previews?.known == true }
            mainClock.autoAdvance = false
            runOnUiThread { platform.players.last().playTo(60_000) }
            flick(app, -1f)
            assertEquals(50_000, app.playback?.sceneScrub?.targetMs)
            until("the time shown") { shown(TRICKPLAY_PREVIEW) }
            assertFalse(shown(TRICKPLAY_PICTURE))
        }
    }

    @Test
    fun theRightStickMeansNothingWhileBrowsing() {
        screen(populated()) { app ->
            awaitText("Recently Added")
            runOnIdle { app.onRightStick(1f, ControllerFamily.XBOX) }
            runOnIdle { app.onRightStick(0f, ControllerFamily.XBOX) }
            assertNull(app.playback)
            assertEquals(dev.reflux.ui.state.Destination.Home, app.navigator.current)
        }
    }
}
