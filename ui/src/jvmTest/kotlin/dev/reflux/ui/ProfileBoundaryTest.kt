package dev.reflux.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import dev.reflux.library.createProfile
import dev.reflux.library.profiles
import dev.reflux.library.setPin
import dev.reflux.ui.state.Destination
import dev.reflux.ui.state.ProfileGate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The boundaries the M8 architecture challenge probed (docs/VERIFICATION.md, "M8: architecture challenge"): nothing
 * behind the question of who is watching can be reached, the navigation fits every window it is placed in, and
 * nothing of one viewer's session is left on screen for the next.
 */
@OptIn(ExperimentalTestApi::class)
class ProfileBoundaryTest {
    @Test
    fun nothingBehindTheQuestionOfWhoIsWatchingCanBeTouched() {
        val world = populated()
        val platform = FakePlatform()
        world.library.setPin(world.library.profiles().single().id, "1357", platform.pinKey!!)
        for ((width, height) in listOf(412 to 915, 1440 to 900)) screen(world, platform, width = width, height = height) { app ->
            awaitText("Enter this profile's PIN.")
            // Taps all over the window, where the navigation and the page's cards are behind the question.
            for (x in listOf(0.05f, 0.25f, 0.5f, 0.75f, 0.95f)) for (y in listOf(0.04f, 0.3f, 0.75f, 0.93f, 0.97f)) {
                onRoot().performTouchInput { click(Offset(this.width * x, this.height * y)) }
            }
            waitForIdle()
            assertIs<ProfileGate.Unlock>(app.profileGate)
            assertEquals(listOf<Destination>(Destination.Home), app.navigator.path, "nothing behind it was opened at ${width}x$height")
            assertTrue(app.playback == null && !app.offlineOpen)
        }
    }

    @Test
    fun theNavigationFitsEveryWindowItIsPlacedIn() {
        // The rail holds eight destinations since M8: on a television's 960 x 540 canvas and on a phone on its side.
        val names = listOf("Home", "Movies", "Shows", "Search", "Downloads", "Sources", "Settings", "Profiles")
        val tv = OfflineWorld()
        television(tv.world, FakePlatform(television = true, offline = tv.offline)) { _ ->
            awaitText("Recently Added")
            val bottoms = names.map { onNode(destination(it)).fetchSemanticsNode().boundsInRoot.bottom / 2 }
            assertTrue(bottoms.all { it <= TV_HEIGHT * 0.95f }, "every destination inside the television's safe area: $bottoms")
        }
        for ((width, height) in listOf(915 to 412, 740 to 360)) {
            val phone = OfflineWorld()
            screen(phone.world, FakePlatform(offline = phone.offline), width = width, height = height) { _ ->
                await("the rail") { onAllNodes(destination("Home")).fetchSemanticsNodes().isNotEmpty() }
                val bounds = listOf("Home", "Movies", "Shows", "Search", "Profile and settings").map { onNode(destination(it)).fetchSemanticsNode().boundsInRoot }
                assertTrue(bounds.all { it.bottom <= height && it.height >= 40f }, "every destination on a ${width}x$height screen, at a size to press: ${bounds.map { it.top to it.bottom }}")
            }
        }
    }

    @Test
    fun nothingOfOneViewersSessionIsLeftForTheNext() {
        val world = populated()
        val sam = world.library.createProfile("Sam")
        screen(world) { app ->
            awaitText("Recently Added")
            await("the profiles") { app.profiles.size == 2 }
            runOnIdle { app.searchQuery = "a private search" }
            runOnIdle { app.chooseProfile(app.profiles.first { it.id == sam }) }
            await("Sam watching") { app.activeProfile?.id == sam }
            assertEquals("", app.searchQuery, "the last viewer's search is not the next viewer's")
        }
    }
}
