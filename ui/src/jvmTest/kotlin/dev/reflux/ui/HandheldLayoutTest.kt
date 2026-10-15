package dev.reflux.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import dev.reflux.core.input.InputModality
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.ui.state.Destination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The same experience on a phone and on a tablet: the information architecture does not change, where
 * things sit does. Rendered offscreen at handheld sizes and operated by touch.
 */
@OptIn(ExperimentalTestApi::class)
class HandheldLayoutTest {
    private val phoneWidth = 412
    private val phoneHeight = 915

    private fun DesktopComposeUiTest.top(description: String) = onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot.top
    private fun DesktopComposeUiTest.centre(description: String) = onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot.center
    private fun DesktopComposeUiTest.shows(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    private fun DesktopComposeUiTest.has(description: String) = onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
    private fun DesktopComposeUiTest.tap(text: String) = onNodeWithText(text).performTouchInput { click() }

    @Test
    fun aPhoneOnItsSideStillOpensDetailsOnTheTitleAndPlay() = screen(populated(), width = phoneHeight, height = phoneWidth) { app ->
        awaitText("Recently Added")
        app.navigator.open(Destination.Details(app.library.movies().first { it.item.title == "Heat" }.item.id))
        awaitText("Versions")
        save("phone-landscape-details")
        // A header taller than the screen would open on artwork alone, with the title and Play below the fold.
        val title = onAllNodesWithText("Heat").fetchSemanticsNodes().maxBy { it.boundsInRoot.height }.boundsInRoot
        val play = onNodeWithText("Play").fetchSemanticsNode().boundsInRoot
        assertTrue(title.top >= 0f && play.bottom < phoneWidth - 60f, "title $title and Play $play are in the first view")
    }

    @Test
    fun aPosterOnlyWorkOpensOnItsPosterOnAPhone() {
        val platform = FakePlatform(backdrops = false)
        screen(populated(), platform, width = phoneWidth, height = phoneHeight) { app ->
            awaitText("Recently Added")
            waitForIdle()
            fun acrossTheScreen() = platform.artwork.served.any { (url, width) -> url.endsWith("poster/Whiplash") && width >= phoneWidth }
            assertTrue(!acrossTheScreen(), "not already drawn across the screen by Home's hero")
            app.navigator.open(Destination.Details(app.library.movies().first { it.item.title == "Whiplash" }.item.id))
            awaitText("Play")
            // The header of a phone held upright is nearly poster-shaped: the poster fills it, at the header's width,
            // rather than leaving the work with a generated backdrop in its color.
            await("the poster across the header") { acrossTheScreen() }
            waitForIdle()
            save("phone-details-poster-only")
        }
    }

    @Test
    fun aPhoneNavigatesFromTheBottomOfTheScreen() = screen(populated(), width = phoneWidth, height = phoneHeight) { app ->
        awaitText("Recently Added")
        // The sections are within reach of a thumb, and there is no bar along the top.
        val tabs = listOf("Home", "Movies", "Shows").map { onNodeWithText(it).fetchSemanticsNode().boundsInRoot }
        assertTrue(tabs.all { it.top > phoneHeight * 0.85f && it.right <= phoneWidth }, "the sections are along the bottom: $tabs")
        assertTrue(top("Search") > phoneHeight * 0.85f)
        assertTrue(top("Media sources") < 80f, "what is needed less often is in the top corner")
        assertTrue(!shows("Reflux"), "no wordmark competing with the artwork")
        save("phone-home")

        tap("Movies")
        awaitText("Heat")
        assertEquals(Destination.Movies, app.navigator.current)
        assertEquals(InputModality.TOUCH, app.input.modality)
        save("phone-movies")

        // A page reached from somewhere has its way back in the top corner; the sections stay where they were.
        onNodeWithContentDescription("Heat, 1995").performTouchInput { click() }
        awaitText("Versions")
        assertTrue(top("Back") < 80f)
        assertTrue(!has("Media sources"))
        onNodeWithText("Play").assertIsDisplayed()
        onNodeWithText("Movies").assertIsDisplayed()
        save("phone-details")
        onNodeWithContentDescription("Back").performTouchInput { click() }
        await("the way back") { app.navigator.current == Destination.Movies }

        onNodeWithContentDescription("Search").performTouchInput { click() }
        await("search") { app.navigator.current == Destination.Search }
        save("phone-search")
    }

    @Test
    fun aPhonePlayerPutsTheTransportUnderTheThumbs() {
        val platform = FakePlatform()
        screen(populated(), platform, width = phoneWidth, height = phoneHeight) { app ->
            awaitText("Recently Added")
            tap("Shows")
            awaitText("Breaking Bad")
            runOnIdle { app.play(app.library.shows().first { it.item.title == "Breaking Bad" }.item.id) }
            awaitText("S1 · E1 · Pilot")
            val player = platform.players.single()
            player.apply { playTo(95_000) }.pause()
            waitForIdle()

            // Play, back, and ahead are in the middle of the picture; the bar keeps the timeline and the rest.
            val play = centre("Play")
            assertTrue(play.x in phoneWidth * 0.4f..phoneWidth * 0.6f && play.y in phoneHeight * 0.4f..phoneHeight * 0.6f, "play is in the middle: $play")
            assertTrue(centre("Back 10 seconds").x < play.x && centre("Forward 10 seconds").x > play.x)
            for (control in listOf("Timeline", "Next episode", "Audio", "Subtitles", "Playback speed", "Fullscreen")) {
                val bounds = onNodeWithContentDescription(control).fetchSemanticsNode().boundsInRoot
                assertTrue(bounds.left >= 0f && bounds.right <= phoneWidth && bounds.top > phoneHeight * 0.7f, "$control fits along the bottom: $bounds")
            }
            assertTrue(!has("Volume") && !has("Mute"), "volume is the device's own buttons")
            save("phone-player")

            // A tap on the picture puts the controls away and brings them back; it does not pause or play.
            onNodeWithContentDescription("Play").performTouchInput { click() }
            await("playback") { player.state.value.status == PlayerStatus.PLAYING }
            mainClock.autoAdvance = false
            onRoot().performTouchInput { click(Offset(phoneWidth / 2f, phoneHeight * 0.25f)) }
            mainClock.advanceTimeBy(600)
            assertTrue(!has("Pause"), "a tap puts the controls away")
            assertEquals(PlayerStatus.PLAYING, player.state.value.status)
            mainClock.advanceTimeBy(1_000)
            onRoot().performTouchInput { click(Offset(phoneWidth / 2f, phoneHeight * 0.25f)) }
            mainClock.advanceTimeBy(600)
            assertTrue(has("Pause"), "and another brings them back")
            assertEquals(PlayerStatus.PLAYING, player.state.value.status)

            // Two taps on a side of the picture skip that way.
            mainClock.advanceTimeBy(1_000)
            val before = player.state.value.positionMs
            repeat(2) {
                onRoot().performTouchInput { click(Offset(phoneWidth * 0.85f, phoneHeight * 0.3f)) }
                mainClock.advanceTimeBy(120)
            }
            assertEquals(before + 10_000, player.state.value.positionMs, "a double tap on the right skips ahead")
            repeat(2) {
                mainClock.advanceTimeBy(1_000)
                onRoot().performTouchInput { click(Offset(phoneWidth * 0.15f, phoneHeight * 0.3f)) }
                mainClock.advanceTimeBy(120)
                onRoot().performTouchInput { click(Offset(phoneWidth * 0.15f, phoneHeight * 0.3f)) }
                mainClock.advanceTimeBy(120)
            }
            assertEquals(before - 10_000, player.state.value.positionMs, "and on the left, back")
        }
    }

    @Test
    fun aPhoneOnItsSidePlaysAcrossTheLongEdge() {
        val platform = FakePlatform()
        screen(populated(), platform, width = phoneHeight, height = phoneWidth) { app ->
            awaitText("Recently Added")
            runOnIdle { app.play(app.library.movies().first { it.item.title == "Heat" }.item.id) }
            await("the player") { app.playback != null && platform.players.isNotEmpty() }
            platform.players.single().apply { playTo(95_000) }.pause()
            waitForIdle()
            val play = centre("Play")
            val timeline = onNodeWithContentDescription("Timeline").fetchSemanticsNode().boundsInRoot
            assertTrue(play.y < timeline.top - 24f, "the transport is clear of the bar beneath it: $play, $timeline")
            assertTrue(timeline.bottom <= phoneWidth)
            save("phone-player-landscape")
        }
    }

    @Test
    fun aTabletKeepsTheBarAndShowsMoreAtOnce() {
        var phoneColumns = 0
        screen(populated(), width = phoneWidth, height = phoneHeight) { app ->
            awaitText("Recently Added")
            tap("Movies")
            awaitText("Heat")
            phoneColumns = postersInTheFirstRow()
        }
        screen(populated(), width = 800, height = 1280) { app ->
            awaitText("Recently Added")
            // Room for the bar along the top, as on a desktop: the same experience, not a bigger phone.
            onNodeWithText("Reflux").assertIsDisplayed()
            assertTrue(onNodeWithText("Movies").fetchSemanticsNode().boundsInRoot.top < 100f)
            save("tablet-home")
            tap("Movies")
            awaitText("Heat")
            assertTrue(postersInTheFirstRow() > phoneColumns, "a tablet shows more titles in a row than a phone ($phoneColumns)")
            save("tablet-movies")
            onNodeWithContentDescription("Heat, 1995").performTouchInput { click() }
            awaitText("Versions")
            save("tablet-details")
        }
    }

    /** How many posters share the top row of the grid on screen. */
    private fun DesktopComposeUiTest.postersInTheFirstRow(): Int {
        val posters = onAllNodesWithContentDescription("", substring = true).fetchSemanticsNodes()
            .map { it.boundsInRoot }.filter { it.height > it.width * 1.2f && it.width > 80f }
        val firstRow = posters.minOfOrNull { it.top } ?: return 0
        return posters.count { it.top == firstRow }
    }
}
