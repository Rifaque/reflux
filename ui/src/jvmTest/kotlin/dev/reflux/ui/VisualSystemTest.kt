package dev.reflux.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.reflux.core.input.Key
import androidx.compose.ui.unit.dp
import dev.reflux.design.ColorRoles
import dev.reflux.design.FormFactor
import dev.reflux.design.Motion
import dev.reflux.ui.theme.MotionPolicy
import dev.reflux.ui.theme.tween
import dev.reflux.design.Palette
import dev.reflux.ui.components.ArtworkSamples
import dev.reflux.ui.state.AccessibilityPreferences
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import dev.reflux.ui.state.MEDIA_ATMOSPHERE
import dev.reflux.ui.theme.AppearancePolicy
import dev.reflux.ui.theme.toColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The visual system of M4 (docs/DESIGN.md, "Visual system"), held by what the rendered screens show rather than by
 * golden images: material tiers and their opaque fallback, artwork under the navigation, Media Atmosphere off and
 * on, edge to edge, and the cues for where the viewer is. Each test also saves its screens for review
 * (`-Preflux.screenshots=<dir>`), which together are the representative set for visual regressions.
 */
@OptIn(ExperimentalTestApi::class)
class VisualSystemTest {
    private fun DesktopComposeUiTest.pixels(): PixelMap = onRoot().captureToImage().toPixelMap()

    private fun distance(a: Color, b: Color) = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)

    private fun saturation(c: Color): Float {
        val max = maxOf(c.red, c.green, c.blue)
        val min = minOf(c.red, c.green, c.blue)
        return if (max <= 0f) 0f else (max - min) / max
    }

    /** Lets artwork arrive and its transitions settle. */
    private fun DesktopComposeUiTest.settle(platform: FakePlatform, part: String) {
        await("$part to load") { platform.artwork.painted(part) != null }
        waitForIdle()
        Thread.sleep(700)
        waitForIdle()
    }

    private val background = Palette.background.toColor()
    private val surface = Palette.surface.toColor()

    @Test
    fun capableLayoutsLetTheHeroRunUnderTheNavigationAndBasicKeepsItBeside() {
        // PREMIUM (a desktop that blurs): the hero reaches the window's edge, beside and beneath the sidebar.
        val premium = FakePlatform()
        lateinit var premiumPixels: PixelMap
        screen(populated(), premium) {
            awaitText("Recently Added")
            settle(premium, "backdrop")
            save("m4-desktop-premium-home")
            premiumPixels = pixels()
        }
        // BASIC (the system asked for less transparency): the page starts beside an opaque sidebar.
        val basic = FakePlatform().also { it.accessibility = AccessibilityPreferences(reduceTransparency = true) }
        lateinit var basicPixels: PixelMap
        screen(populated(), basic) {
            awaitText("Recently Added")
            settle(basic, "backdrop")
            save("m4-desktop-basic-home")
            basicPixels = pixels()
        }
        // Left of the sidebar's floating panel (its 8 dp margin), at the hero's height.
        val edge = 3 to 300
        assertTrue(distance(premiumPixels[edge.first, edge.second], background) > 0.06f, "artwork at the window's edge: ${premiumPixels[edge.first, edge.second]}")
        assertTrue(distance(basicPixels[edge.first, edge.second], background) < 0.04f, "the plain page (and the panel's shadow) beside the sidebar: ${basicPixels[edge.first, edge.second]}")
        // Inside the sidebar, below its destinations: opaque surface on BASIC whatever is behind it.
        val inside = basicPixels[110, 450]
        assertTrue(distance(inside, surface) < 0.03f, "opaque material: $inside against $surface")
    }

    @Test
    fun aHeroUnderTheRailKeepsItsTextClearOfTheRail() = screen(populated(), width = 1180, height = 820) {
        awaitText("Recently Added")
        val rail = onNode(destination("Home")).fetchSemanticsNode().boundsInRoot
        val play = onAllNodes(hasText("Play") or hasText("Resume")).onFirst().fetchSemanticsNode().boundsInRoot
        assertTrue(play.left > rail.right + 8f, "Play at $play is clear of the rail at $rail")
        val row = onNodeWithText("Recently Added").fetchSemanticsNode().boundsInRoot
        assertTrue(row.left > rail.right + 8f, "rows start after the rail: $row, $rail")
        save("m4-tablet-home")
    }

    @Test
    fun mediaAtmosphereIsOffByDefaultAndAWashWhenOn() {
        val platform = FakePlatform()
        screen(populated(), platform) { app ->
            assertEquals(false, app.mediaAtmosphere, "off by default")
            app.navigator.open(Destination.Details(app.library.movies().first { it.item.title == "Heat" }.item.id))
            awaitText("Versions")
            settle(platform, "backdrop/Heat")
            save("m4-atmosphere-off")
            // The page's right margin below the header: nothing is drawn there but the page itself.
            fun margin() = pixels().let { map -> (860 until 896 step 4).map { map[1432, it] } }
            assertTrue(margin().all { distance(it, background) < 0.02f }, "the plain page when off: ${margin()}")

            runOnIdle { app.setMediaAtmosphere(true) }
            waitForIdle()
            Thread.sleep(700)
            waitForIdle()
            save("m4-atmosphere-on")
            val on = margin()
            assertTrue(on.any { distance(it, background) > 0.03f && saturation(it) > 0.15f }, "the artwork's atmosphere when on: $on")
            // Never more than a wash: it stays dark enough for primary text at AA, wherever it is.
            assertTrue(on.all { Palette.textPrimary.toColor().let { text -> contrast(text, it) >= 7.0 } }, "readable: $on")
            await("the choice kept with the library") { app.library.setting(MEDIA_ATMOSPHERE) == "on" }
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        fun lum(c: Color): Double {
            fun ch(v: Float) = if (v <= 0.04045f) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
            return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
        }
        val x = lum(a) + 0.05
        val y = lum(b) + 0.05
        return if (x > y) x / y else y / x
    }

    @Test
    fun theMediaAtmosphereSwitchIsAnOrdinarySwitchAndIsRemembered() {
        val world = populated()
        screen(world) { app ->
            app.navigator.show(Destination.Sources)
            awaitText("Sources")
            onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("Media Atmosphere"))
            val switch = onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch) and SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off) and hasText("Media Atmosphere", substring = true))
            switch.performClick()
            await("on") { app.mediaAtmosphere && world.library.setting(MEDIA_ATMOSPHERE) == "on" }
            onNode(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On) and hasText("Media Atmosphere", substring = true)).assertExists()
            save("m4-sources-atmosphere-on")
        }
        // A new start of Reflux finds it as it was left.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val again = AppState(world.library, world.pipeline, world.registry::resolve, FakePlatform(), scope)
            val deadline = System.currentTimeMillis() + 10_000
            while (!again.mediaAtmosphere && System.currentTimeMillis() < deadline) Thread.sleep(20)
            assertTrue(again.mediaAtmosphere, "remembered")
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun edgeToEdgeTheArtworkReachesTheTopAndTheControlsStayClearOfTheBars() {
        val platform = FakePlatform()
        val bars = SystemBars(top = 32.dp, bottom = 24.dp)
        screen(populated(), platform, width = 412, height = 915, bars = bars) {
            awaitText("Recently Added")
            settle(platform, "backdrop")
            save("m4-phone-edge-to-edge")
            // Under the status bar: the hero's artwork (protected by a soft shade), not a band of plain page.
            val top = pixels()[300, 4]
            assertTrue(distance(top, background) > 0.03f, "artwork under the status bar: $top")
            // The destinations sit above the gesture area, and the way to Sources below the status bar.
            val home = onNode(destination("Home")).fetchSemanticsNode().boundsInRoot
            assertTrue(home.bottom <= 915f - 24f - 10f, "the bar clears the gesture area: $home")
            val sources = onNode(destination("Sources")).fetchSemanticsNode().boundsInRoot
            assertTrue(sources.top >= 32f, "below the status bar: $sources")
        }
    }

    @Test
    fun whereTheViewerIsIsShownByShapeAndLightNotColorAlone() = screen(populated()) {
        awaitText("Recently Added")
        val home = onNode(destination("Home")).fetchSemanticsNode().boundsInRoot
        val movies = onNode(destination("Movies")).fetchSemanticsNode().boundsInRoot
        val map = pixels()
        fun bar(item: Rect) = map[(item.left + 1.5f).toInt(), item.center.y.toInt()]
        fun pill(item: Rect) = map[(item.right - 12f).toInt(), (item.top + 6f).toInt()]
        // A bar at the selected destination's leading edge, and a lit pill; neither on the others.
        assertTrue(distance(bar(home), Color.White) < 0.3f, "the selection bar: ${bar(home)}")
        assertTrue(distance(bar(movies), Color.White) > 1.5f, "no bar beside Movies: ${bar(movies)}")
        assertTrue(pill(home).red > pill(movies).red + 0.03f, "the lit pill: ${pill(home)} against ${pill(movies)}")
        save("m4-desktop-selected")
    }

    @Test
    fun aTelevisionsRailIsQuietClosedAndMaterialOpen() = television { app ->
        awaitText("Recently Added")
        await("focus on the page") { onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
        val closed = onNode(destination("Home")).fetchSemanticsNode().boundsInRoot
        save("m4-tv-rail-closed")
        remote(app, Key.ARROW_LEFT)
        await("the rail") { onAllNodes(isFocused() and destination("Home")).fetchSemanticsNodes().isNotEmpty() }
        waitForIdle()
        Thread.sleep(400)
        waitForIdle()
        val open = onNode(destination("Home")).fetchSemanticsNode().boundsInRoot
        assertTrue(open.width > closed.width * 2, "the rail widens to say its names: $closed to $open")
        save("m4-tv-rail-open")
        remote(app, Key.ARROW_RIGHT)
        await("back in the page") { onAllNodes(isFocused() and destination("Home")).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun highContrastStrengthensTheSameAppearanceAndOpaquesTheMaterial() {
        val platform = FakePlatform().also { it.accessibility = AccessibilityPreferences(highContrast = true) }
        screen(populated(), platform) {
            awaitText("Recently Added")
            settle(platform, "backdrop")
            assertEquals(ColorRoles.RefluxHighContrast, AppearancePolicy.roles)
            val inside = pixels()[110, 450]
            assertTrue(distance(inside, surface) < 0.03f, "opaque material at high contrast: $inside")
            save("m4-desktop-high-contrast")
        }
        screen(populated()) {
            awaitText("Recently Added")
            assertEquals(ColorRoles.Reflux, AppearancePolicy.roles, "the canonical appearance otherwise")
        }
    }

    @Test
    fun artworkIsReadForItsColorOnceNotOnEveryVisit() {
        val platform = FakePlatform()
        screen(populated(), platform) { app ->
            runOnIdle { app.setMediaAtmosphere(true) }
            val heat = app.library.movies().first { it.item.title == "Heat" }.item.id
            awaitText("Recently Added")
            settle(platform, "backdrop")
            app.navigator.open(Destination.Details(heat))
            awaitText("Versions")
            settle(platform, "backdrop/Heat")
            // Both pages have been seen once: from here on, nothing on them is read again.
            runOnIdle { app.navigator.back() }
            awaitText("Recently Added")
            waitForIdle()
            app.navigator.open(Destination.Details(heat))
            awaitText("Versions")
            waitForIdle()
            val first = ArtworkSamples.reads
            repeat(3) {
                runOnIdle { app.navigator.back() }
                awaitText("Recently Added")
                app.navigator.open(Destination.Details(heat))
                awaitText("Versions")
                waitForIdle()
            }
            assertEquals(first, ArtworkSamples.reads, "visiting the page again reads no pixels")
        }
    }

    @Test
    fun withMotionReducedEveryNewTransitionTakesNoTime() {
        val vocabulary = listOf(
            Motion.railExpand, Motion.materialChange, Motion.artworkReveal, Motion.sheetEnter, Motion.sheetExit,
            Motion.profile(FormFactor.TV).pageEnter, Motion.profile(FormFactor.TV).railExpand, Motion.profile(FormFactor.DESKTOP).pageEnter,
        )
        val before = MotionPolicy.reduced
        try {
            MotionPolicy.reduced = true
            assertTrue(vocabulary.all { it.tween<Float>().durationMillis == 0 }, "immediate with motion reduced")
            MotionPolicy.reduced = false
            assertTrue(vocabulary.all { it.tween<Float>().durationMillis == it.durationMs }, "as designed otherwise")
        } finally {
            MotionPolicy.reduced = before
        }
    }
}
