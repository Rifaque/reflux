package dev.reflux.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import dev.reflux.core.input.InputModality
import dev.reflux.design.AdaptiveLayout
import dev.reflux.design.Motion
import dev.reflux.design.NavigationStyle
import dev.reflux.ui.legal.NoticeTarget
import dev.reflux.ui.state.AccessibilityPreferences
import dev.reflux.ui.state.Destination
import java.io.File
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Navigation & Surface System 2.0 (M9; docs/DESIGN.md, "Navigation surfaces"): how M8's structure looks and moves.
 * One mark shows where the viewer is and travels between destinations (at once, with motion reduced); the phone's bar
 * is as wide as its destinations; Reflux says its own name where there is room; and the shell's geometry in every
 * window class is held to a committed baseline.
 */
@OptIn(ExperimentalTestApi::class)
class ShellSurfaceTest {
    private fun DesktopComposeUiTest.bounds(name: String): Rect = onNode(destination(name)).fetchSemanticsNode().boundsInRoot
    private fun DesktopComposeUiTest.pixels(): PixelMap = onRoot().captureToImage().toPixelMap()
    /** How light a pixel is, as it is encoded: the measure in which a state layer's few percent of white is a few percent. */
    private fun PixelMap.light(at: Offset): Float = this[at.x.roundToInt(), at.y.roundToInt()].let { (it.red + it.green + it.blue) / 3f }

    /** Just inside a destination's leading edge, at its middle: in the lit pill when it is there, clear of the icon. */
    private fun inside(place: Rect) = Offset(place.left + 7f, place.center.y)

    /** In the space between two destinations: under the mark only while it is on its way from one to the other. */
    private fun between(a: Rect, b: Rect) = Offset((a.right + b.left) / 2f, a.center.y)

    private val lit = 0.03f

    @Test
    fun whereTheViewerIsTravelsFromOneDestinationToTheNext() {
        // An empty library: nothing behind the phone's bar but the page, so what is lit is the mark and nothing else.
        screen(TestWorld(), width = 412, height = 915) { app ->
            await("the bar") { onAllNodes(destination("Home")).fetchSemanticsNodes().isNotEmpty() }
            runOnIdle { app.go(Destination.Movies) }
            waitForIdle()
            val movies = bounds("Movies")
            val shows = bounds("Shows")
            val search = bounds("Search")
            val rest = pixels()
            val unlit = rest.light(inside(search))
            assertTrue(rest.light(inside(movies)) - unlit > lit, "Movies is lit while the viewer is there")
            assertTrue(rest.light(between(movies, shows)) - unlit < lit, "and nothing is lit between destinations at rest")

            mainClock.autoAdvance = false
            runOnIdle { app.go(Destination.Shows) }
            mainClock.advanceTimeBy(Motion.selectionTravel.durationMs / 3L)
            val onTheWay = pixels()
            assertTrue(onTheWay.light(between(movies, shows)) - unlit > lit, "the mark is on its way from Movies to Shows")
            assertEquals(listOf("Shows"), selectedDestination(), "what a screen reader hears is where the viewer already is")

            mainClock.advanceTimeBy(Motion.selectionTravel.durationMs * 2L)
            mainClock.autoAdvance = true
            waitForIdle()
            val arrived = pixels()
            assertTrue(arrived.light(inside(shows)) - unlit > lit, "it arrives on Shows")
            assertTrue(arrived.light(inside(movies)) - unlit < lit, "and has left Movies")
            assertTrue(arrived.light(between(movies, shows)) - unlit < lit)
        }
    }

    @Test
    fun withMotionReducedItIsThereAtOnce() {
        val platform = FakePlatform()
        platform.accessibility = AccessibilityPreferences(reduceMotion = true)
        screen(TestWorld(), platform, width = 412, height = 915) { app ->
            await("the bar") { onAllNodes(destination("Home")).fetchSemanticsNodes().isNotEmpty() }
            runOnIdle { app.go(Destination.Movies) }
            waitForIdle()
            val movies = bounds("Movies")
            val shows = bounds("Shows")
            val unlit = pixels().light(inside(bounds("Search")))
            mainClock.autoAdvance = false
            runOnIdle { app.go(Destination.Shows) }
            // A few frames, far short of the journey it would otherwise make.
            repeat(3) { mainClock.advanceTimeByFrame() }
            val now = pixels()
            assertTrue(now.light(inside(shows)) - unlit > lit, "on Shows already")
            assertTrue(now.light(inside(movies)) - unlit < lit, "and gone from Movies")
            assertTrue(now.light(between(movies, shows)) - unlit < lit, "with no journey between them")
        }
    }

    @Test
    fun theMarkGoesWhenTheViewerIsNowhereInTheBar() {
        screen(TestWorld(), width = 412, height = 915) { app ->
            await("the bar") { onAllNodes(destination("Home")).fetchSemanticsNodes().isNotEmpty() }
            val home = bounds("Home")
            val unlit = pixels().light(inside(bounds("Search")))
            assertTrue(pixels().light(inside(home)) - unlit > lit)
            // Settings is in the utility area: none of the bar's four is where the viewer is.
            runOnIdle { app.go(Destination.Settings) }
            awaitText("Settings")
            waitForIdle()
            assertTrue(pixels().light(inside(home)) - unlit < lit, "the navigation says where the viewer is, or nothing")
            assertEquals(emptyList(), selectedDestination().filter { it in listOf("Home", "Movies", "Shows", "Search") })
        }
    }

    @Test
    fun thePhonesBarIsAsWideAsItsDestinations() {
        for (width in listOf(320, 360, 412, 480)) screen(populated(), width = width, height = 800) { _ ->
            await("the bar") { onAllNodes(destination("Home")).fetchSemanticsNodes().isNotEmpty() }
            val places = listOf("Home", "Movies", "Shows", "Search").map { bounds(it) }
            val span = places.last().right - places.first().left
            assertEquals(4 * 64f + 3 * 4f, span, 1f, "four destinations and the space between them at $width")
            assertEquals(width / 2f, (places.first().left + places.last().right) / 2, 1f, "centred at $width")
            assertTrue(places.first().left >= 16f && places.last().right <= width - 16f, "inside the screen's margins at $width: $places")
            assertTrue(places.all { it.height >= 44f && it.width >= 48f }, "each still a target to press at $width")
        }
    }

    @Test
    fun refluxSaysItsNameWhereThereIsRoom() {
        // A desktop's sidebar carries the lockup, named once. The shell is the product's: the maker is credited in About, not here.
        screen(populated()) { _ ->
            awaitText("Recently Added")
            assertEquals(1, onAllNodesWithContentDescription("Reflux").fetchSemanticsNodes().size)
            assertTrue(onAllNodesWithText("Made by RSX Develops").fetchSemanticsNodes().isEmpty(), "the maker is not credited in the navigation")
        }
        // A phone has no room for it, and nothing stands in for it.
        screen(populated(), width = 412, height = 915) { _ ->
            await("the bar") { onAllNodes(destination("Home")).fetchSemanticsNodes().isNotEmpty() }
            assertTrue(onAllNodesWithContentDescription("Reflux").fetchSemanticsNodes().isEmpty())
        }
    }

    /**
     * The shell's geometry by window class, against `shell-baseline.txt`: which navigation and which dock a window
     * gets, and where its destinations are, in the measures that do not depend on a machine's fonts (so the same
     * baseline holds on Windows, Linux, and a CI runner; DECISIONS.md, 2026-11-12). A window is classed by its size,
     * never by its device: a phone-sized window on a tablet is a phone's shell.
     *
     * `REFLUX_WRITE_BASELINES=1` writes the file instead of comparing; the difference is then reviewed like any other.
     */
    @Test
    fun theShellInEveryWindowClassIsAsItsBaselineSays() {
        val classes = listOf(
            "phone, small" to (320 to 640), "phone" to (412 to 915), "phone on its side" to (915 to 412),
            "phone-sized window on a tablet" to (400 to 700), "tablet, upright" to (800 to 1280), "tablet, on its side" to (1280 to 800),
            "tablet, split in two" to (640 to 800), "desktop, smallest window" to (1200 to 600), "desktop" to (1440 to 900), "desktop, large" to (1920 to 1080),
        )
        val lines = ArrayList<String>()
        for ((name, size) in classes) {
            val (width, height) = size
            screen(populated(), width = width, height = height) { _ ->
                await("the navigation") { onAllNodes(destination("Home")).fetchSemanticsNodes().isNotEmpty() }
                waitForIdle()
                lines += describe(name, width, height, television = false)
            }
        }
        television(populated()) { _ ->
            awaitText("Recently Added")
            lines += describe("television", TV_WIDTH, TV_HEIGHT, television = true, scale = TV_DENSITY)
        }
        val actual = lines.joinToString("\n") + "\n"
        val file = File("src/jvmTest/resources/shell-baseline.txt")
        if (System.getenv("REFLUX_WRITE_BASELINES") == "1") {
            file.parentFile.mkdirs()
            file.writeText(actual)
        }
        assertTrue(file.isFile, "no baseline at ${file.absolutePath}: run once with REFLUX_WRITE_BASELINES=1 and review it")
        assertEquals(file.readText().replace("\r\n", "\n"), actual, "the shell's geometry changed; if that was meant, write the baseline again and review the difference")
    }

    private fun DesktopComposeUiTest.describe(name: String, width: Int, height: Int, television: Boolean, scale: Float = 1f): String {
        val layout = AdaptiveLayout.profile(width.toFloat(), height.toFloat(), television, InputModality.POINTER)
        val tabs = onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).fetchSemanticsNodes()
        val places = tabs.map { node ->
            val said = node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() ?: node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text.orEmpty()
            said to node.boundsInRoot
        }
        fun dp(value: Float) = (value / scale).roundToInt()
        // Icons alone are the same on every machine in every measure; words beneath or beside an icon are as tall as
        // the machine's font makes them, so with words only what the layout fixes is held: where each starts across,
        // how wide it is, where the first begins, and where the last ends.
        val iconsOnly = layout.navigation == NavigationStyle.BOTTOM_BAR || layout.navigation == NavigationStyle.COMPACT_RAIL || layout.navigation == NavigationStyle.TV_RAIL
        val each = places.joinToString("; ") { (said, at) ->
            if (iconsOnly) "$said ${dp(at.left)},${dp(at.top)} ${dp(at.width)}x${dp(at.height)}" else "$said x${dp(at.left)} w${dp(at.width)}"
        }
        // A row's height is its label's, which is the machine's font: only where the first starts is fixed, and that
        // everything fits in the window is asserted rather than recorded (the Linux container's fonts are 2 dp apart).
        assertTrue(places.all { it.second.bottom / scale <= height + 0.5f && it.second.right / scale <= width }, "every destination is inside the window at $name: $places")
        val ends = if (iconsOnly) "" else " | first top ${dp(places.minOf { it.second.top })}"
        val dock = layout.dock?.let { "${it.style} ${it.widthDp.roundToInt()}x${it.heightDp.roundToInt()}" } ?: "none"
        return "$name (${width}x$height): ${layout.navigation}, dock $dock | $each$ends"
    }

    @Test
    fun theCreditsNameWhatRefluxIsBuiltOnAndWorksWith() {
        screen(populated()) { app ->
            awaitText("Recently Added")
            runOnIdle { app.navigator.open(Destination.About) }
            awaitText("Credits and technology")
            onNodeWithText("Credits and technology").performClick()
            await("the credits") { app.navigator.current == Destination.Credits }
            awaitText("Plays video on Windows and Linux")
            // A desktop build is credited with what a desktop build has.
            assertTrue(saysAnywhere("mpv") && saysAnywhere("FFmpeg"))
            assertFalse(saysAnywhere("AndroidX Media3"), "Android's player is not in a desktop's credits")
            // A server Reflux only connects to is its owner's, and Reflux is not its partner: said in words.
            for (server in listOf("Jellyfin", "Plex")) {
                val note = onAllNodes(hasText(server) and hasText("not affiliated with", substring = true)).fetchSemanticsNodes()
                assertEquals(1, note.size, "$server is credited as independent of Reflux")
            }
            // Names and roles only: no logo is shown for anything.
            assertTrue(onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image)).fetchSemanticsNodes().isEmpty())
            save("about-credits")
        }
        screen(populated(), FakePlatform(notices = NoticeTarget.ANDROID), width = 412, height = 915) { app ->
            await("the bar") { onAllNodes(destination("Home")).fetchSemanticsNodes().isNotEmpty() }
            runOnIdle { app.navigator.open(Destination.Credits) }
            awaitText("Plays video on Android, Android TV, and Fire TV")
            assertFalse(saysAnywhere("mpv"))
        }
    }
}
