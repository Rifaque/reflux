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
import dev.reflux.ui.state.FullscreenMode
import kotlin.math.abs
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
        // The sections stay one pill in the middle, not spread to the screen's two ends.
        val home = onNodeWithText("Home").fetchSemanticsNode().boundsInRoot
        val search = onNodeWithContentDescription("Search").fetchSemanticsNode().boundsInRoot
        assertTrue(search.right - home.left <= 420f, "the sections span ${search.right - home.left}")
        assertTrue(abs((home.left + search.right) / 2 - phoneHeight / 2f) < 24f, "and are centred: $home $search")
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

    /** What an Android phone or tablet is to the player: its own volume keys, and fullscreen as turning the picture sideways. */
    private fun handheld() = FakePlatform(systemVolume = true, fullscreenMode = FullscreenMode.LANDSCAPE)

    @Test
    fun aPhonePlayerPutsTheTransportUnderTheThumbs() {
        val platform = handheld()
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
            for (control in listOf("Timeline", "Next episode", "Audio", "Subtitles", "More options", "Fullscreen")) {
                val bounds = onNodeWithContentDescription(control).fetchSemanticsNode().boundsInRoot
                assertTrue(bounds.left >= 0f && bounds.right <= phoneWidth && bounds.top > phoneHeight * 0.7f, "$control fits along the bottom: $bounds")
            }
            assertTrue(!has("Volume") && !has("Mute"), "volume is the device's own buttons")
            assertTrue(!has("Previous"), "the first episode has none before it")
            assertTrue(!has("Playback speed") && !has("Chapters"), "the lists wanted less often are behind More")
            // More opens them, and their tabs lead to the rest; the panel fits the screen.
            onNodeWithContentDescription("More options").performTouchInput { click() }
            awaitText("Speed")
            val panel = onNodeWithText("Speed").fetchSemanticsNode().boundsInRoot
            assertTrue(panel.right <= phoneWidth, "the lists' tabs fit the screen: $panel")
            // The longest list is taller than the space under the transport: the transport steps aside while it is open.
            onNodeWithText("Speed").performTouchInput { click() }
            awaitText("Normal")
            assertTrue(!has("Play") && !has("Back 10 seconds"), "nothing sits under the open list")
            save("phone-player-more")
            onNodeWithContentDescription("More options").performTouchInput { click() }
            await("the lists to close") { app.playback?.menu == null }
            await("the transport to return") { has("Play") }
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
        val platform = handheld()
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
            // A film has no episode either side, and a phone on its side is already all picture.
            assertTrue(!has("Previous") && !has("Next episode") && !has("Fullscreen"))
            save("phone-player-landscape")
        }
    }

    @Test
    fun aTabletPlayerIsMadeForHandsNotStretchedFromADesktop() {
        val platform = handheld()
        val (width, height) = 1163 to 777 // a Xiaomi Pad 7 on its side
        screen(populated(), platform, width = width, height = height) { app ->
            awaitText("Recently Added")
            tap("Shows")
            awaitText("Breaking Bad")
            assertEquals(InputModality.TOUCH, app.input.modality)
            runOnIdle { app.play(app.library.shows().first { it.item.title == "Breaking Bad" }.item.id) }
            awaitText("S1 · E1 · Pilot")
            platform.players.single().apply { playTo(95_000) }.pause()
            waitForIdle()
            // Play and the seeks in the middle of the picture, where the hands are; the bar keeps the rest.
            val play = centre("Play")
            assertTrue(play.x in width * 0.4f..width * 0.6f && play.y in height * 0.35f..height * 0.65f, "play is in the middle: $play")
            assertEquals(1, onAllNodesWithContentDescription("Play").fetchSemanticsNodes().size, "and only there")
            for (control in listOf("Timeline", "Next episode", "Chapters", "Audio", "Subtitles", "Playback speed")) {
                assertTrue(onNodeWithContentDescription(control).fetchSemanticsNode().boundsInRoot.top > height * 0.75f, "$control is in the bar")
            }
            // Nothing that the device does itself, or that would do nothing here.
            assertTrue(!has("Volume") && !has("Mute"), "volume is the tablet's own keys")
            assertTrue(!has("Fullscreen") && !has("Exit fullscreen"), "on its side, the player is already the whole screen")
            assertTrue(!has("Previous") && !has("More options"))
            save("tablet-player")
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
