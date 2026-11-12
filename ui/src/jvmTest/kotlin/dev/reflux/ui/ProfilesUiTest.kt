package dev.reflux.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import dev.reflux.core.input.SemanticAction
import dev.reflux.library.createProfile
import dev.reflux.library.profiles
import dev.reflux.library.setPin
import dev.reflux.library.switchProfile
import dev.reflux.ui.state.Destination
import dev.reflux.ui.state.ProfileGate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MINUTE = 60_000L

/**
 * Profiles in the experience layer (M8; docs/WATCH_STATE.md, "Profiles", and docs/NAVIGATION.md, section 1): one
 * profile is never mentioned, a second is a viewer of its own, a PIN is asked for, a television asks who is
 * watching, a playback stays with the profile it was for, and clearing history asks first.
 */
@OptIn(ExperimentalTestApi::class)
class ProfilesUiTest {
    @Test
    fun oneProfileIsNeverMentionedAndASecondIsAViewerOfItsOwn() {
        val world = populated()
        val heat = world.movie("Heat").item.id
        world.library.stopped(heat, 40 * MINUTE, 170 * MINUTE)
        screen(world) { app ->
            awaitText("Continue Watching")
            assertFalse(saysAnywhere("Who's watching"), "a single viewer is never asked")
            assertEquals(null, app.profileGate)
            runOnIdle { app.go(Destination.Profiles) }
            awaitText("Add a profile")
            onNodeWithText("Add a profile").performClick()
            onNode(hasSetTextAction()).performTextInput("Sam")
            onNodeWithText("Add").performClick()
            await("Sam watching") { app.activeProfile?.name == "Sam" }
            assertEquals(Destination.Home, app.navigator.current, "a new viewer starts at Home")
            awaitText("Watching as Sam")
            assertNull(world.library.watchState(heat), "Sam has none of Main's viewing")
            assertFalse(saysAnywhere("Continue Watching"), "and no Continue Watching of Main's")
            // The rail's profile entry is the viewer's name once there are several.
            awaitText("Sam")
            save("profiles-second-viewer")
            // Back to Main: everything is as it was.
            runOnIdle { app.chooseProfile(app.profiles.first { it.name == "Main" }) }
            await("Main again") { app.activeProfile?.name == "Main" }
            awaitText("Continue Watching")
            assertEquals(40 * MINUTE, world.library.watchState(heat)?.positionMs)
        }
    }

    @Test
    fun aProfileWithAPinAsksForItAndAWrongOneIsSaid() {
        val world = populated()
        val platform = FakePlatform()
        val sam = world.library.createProfile("Sam")
        world.library.setPin(sam, "2580", platform.pinKey!!)
        screen(world, platform) { app ->
            awaitText("Recently Added")
            await("the profiles") { app.profiles.size == 2 }
            runOnIdle { app.chooseProfile(app.profiles.first { it.name == "Sam" }) }
            awaitText("Enter this profile's PIN.")
            assertIs<ProfileGate.Unlock>(app.profileGate)
            assertEquals("Main", app.activeProfile?.name, "nothing of Sam's opens before the PIN")
            save("profiles-pin")
            onNode(hasSetTextAction()).performTextInput("0000")
            awaitText("That's not the PIN. Try again.")
            onNode(hasSetTextAction()).performTextReplacement("2580")
            await("Sam open") { app.activeProfile?.name == "Sam" && app.profileGate == null }
        }
    }

    @Test
    fun aTelevisionAsksWhoIsWatchingAtStartWhenThereAreSeveralAndBackDoesNotGetPastIt() {
        val world = populated()
        world.library.createProfile("Sam")
        television(world) { app ->
            awaitText("Who's watching?")
            assertEquals(ProfileGate.Choose(required = true), app.profileGate)
            assertFalse(runOnIdle { app.dispatch(SemanticAction.BACK) }, "Back leaves Reflux rather than getting past the question")
            save("profiles-tv-who")
            runOnIdle { app.chooseProfile(app.profiles.first { it.name == "Sam" }) }
            await("Sam watching") { app.activeProfile?.name == "Sam" && app.profileGate == null }
        }
    }

    @Test
    fun aProfileWithAPinOpensLockedAtStart() {
        val world = populated()
        val platform = FakePlatform()
        world.library.setPin(world.library.profiles().single().id, "1357", platform.pinKey!!)
        screen(world, platform) { app ->
            awaitText("Enter this profile's PIN.")
            assertEquals(true, app.profileGate?.required)
            assertFalse(runOnIdle { app.dispatch(SemanticAction.BACK) }, "the only profile's PIN cannot be stepped past")
            onNode(hasSetTextAction()).performTextInput("1357")
            await("unlocked") { app.profileGate == null }
        }
    }

    @Test
    fun switchingProfileWhilePlayingStopsThePlaybackForTheProfileItWasFor() {
        val world = populated()
        val heat = world.movie("Heat").item.id
        val platform = FakePlatform()
        val sam = world.library.createProfile("Sam")
        screen(world, platform) { app ->
            awaitText("Recently Added")
            await("the profiles") { app.profiles.size == 2 }
            runOnIdle { app.play(heat) }
            await("playing") { app.playback != null && platform.players.isNotEmpty() }
            runOnIdle { platform.players.last().playTo(25 * MINUTE) }
            runOnIdle { app.chooseProfile(app.profiles.first { it.id == sam }) }
            await("Sam watching, the player closed") { app.activeProfile?.id == sam && app.playback == null }
            assertNull(world.library.watchState(heat, sam), "the playback never became Sam's")
            world.library.switchProfile(dev.reflux.core.model.ProfileId.DEFAULT)
            assertEquals(25 * MINUTE, world.library.watchState(heat)?.positionMs, "it was recorded as Main's, where it was playing")
        }
    }

    @Test
    fun clearingWatchHistoryAsksFirstAndEmptiesContinueWatching() {
        val world = populated()
        val heat = world.movie("Heat").item.id
        world.library.stopped(heat, 40 * MINUTE, 170 * MINUTE)
        screen(world) { app ->
            awaitText("Continue Watching")
            runOnIdle { app.go(Destination.Settings) }
            awaitText("Clear watch history")
            onNodeWithText("Clear watch history").performScrollTo().performClick()
            awaitText("Clear watch history on this device?")
            assertEquals(40 * MINUTE, world.library.watchState(heat)?.positionMs, "nothing is cleared until it is confirmed")
            save("settings-clear-history")
            onNodeWithText("Clear history").performClick()
            await("cleared") { world.library.watchState(heat) == null }
            runOnIdle { app.go(Destination.Home) }
            awaitText("Recently Added")
            assertFalse(saysAnywhere("Continue Watching"))
        }
    }

    @Test
    fun aLauncherCardOpensInItsProfileWithoutAskingAndAPinStillAsks() {
        val world = populated()
        val heat = world.movie("Heat").item.id
        val platform = FakePlatform()
        val sam = world.library.createProfile("Sam")
        screen(world, platform) { app ->
            awaitText("Recently Added")
            runOnIdle { app.continueFromOutside(heat, sam) }
            await("in Sam's profile, playing") { app.activeProfile?.id == sam && app.playback != null }
            assertNull(app.profileGate, "no question between the card and the work")
            runOnIdle { app.playback?.close() }
            await("closed") { app.playback == null }
            // A card of a profile that has a PIN now opens on the PIN, never past it.
            val main = app.profiles.first { it.name == "Main" }.id
            world.library.setPin(main, "4321", platform.pinKey!!)
            runOnIdle { app.continueFromOutside(heat, main) }
            awaitText("Enter this profile's PIN.")
            assertEquals(sam, app.activeProfile?.id)
            assertTrue(app.playback == null)
            onNode(hasSetTextAction()).performTextInput("4321")
            await("Main's, playing") { app.activeProfile?.id == main && app.playback != null }
        }
    }

    @Test
    fun aForgottenPinOnTheOnlyProfileIsResetAfterConfirmingAndStaysResetAtTheNextLaunch() {
        val world = populated()
        val platform = FakePlatform()
        val heat = world.movie("Heat").item.id
        world.library.stopped(heat, 40 * MINUTE, 170 * MINUTE)
        world.library.setFavorite(heat, true)
        val only = world.library.profiles().single().id
        world.library.setPin(only, "1357", platform.pinKey!!)
        val titlesBefore = world.library.movies().map { it.item.title }
        screen(world, platform) { app ->
            awaitText("Enter this profile's PIN.")
            onNode(hasSetTextAction()).performTextInput("0000")
            awaitText("That's not the PIN. Try again.")
            onNodeWithText("Forgot PIN").performClick()
            awaitText("Reset Main?")
            // Asking is not doing: nothing is erased until it is confirmed, and it can be taken back.
            assertEquals(40 * MINUTE, world.library.watchState(heat)?.positionMs)
            assertTrue(world.library.profiles().single().hasPin)
            save("profiles-forgot-pin")
            onNodeWithText("Keep it").performClick()
            awaitText("Enter this profile's PIN.")
            assertTrue(world.library.profiles().single().hasPin)
            onNodeWithText("Forgot PIN").performClick()
            awaitText("Reset Main?")
            onNodeWithText("Reset Main").performClick()
            await("the profile open again") { app.profileGate == null }
            awaitText("Recently Added")
            assertNull(world.library.watchState(heat), "what the PIN kept private went with it")
            assertTrue(world.library.favorites().isEmpty())
            assertFalse(world.library.profiles().single().hasPin)
            assertEquals(titlesBefore, world.library.movies().map { it.item.title }, "the library and its media are as they were")
            assertFalse(platform.reports.any { "1357" in it || "0000" in it }, "no PIN in anything reported")
            // A new PIN can be set afterwards.
            runOnIdle { app.setPin("2468") }
            await("the new PIN") { world.library.profiles().single().hasPin }
            runOnIdle { app.removePin() }
            await("no PIN") { !world.library.profiles().single().hasPin }
        }
        // The next launch: no question, the profile opens.
        screen(world, platform) { app ->
            awaitText("Recently Added")
            assertNull(app.profileGate)
        }
    }

    @Test
    fun resettingOneProfilesForgottenPinLeavesAnotherProfileAlone() {
        val world = populated()
        val platform = FakePlatform()
        val heat = world.movie("Heat").item.id
        val main = world.library.profiles().single().id
        world.library.stopped(heat, 40 * MINUTE, 170 * MINUTE)
        val sam = world.library.createProfile("Sam")
        world.library.stopped(heat, 10 * MINUTE, 170 * MINUTE, null, sam)
        world.library.setPin(sam, "9999", platform.pinKey!!)
        screen(world, platform) { app ->
            awaitText("Continue Watching")
            await("the profiles") { app.profiles.size == 2 }
            runOnIdle { app.chooseProfile(app.profiles.first { it.id == sam }) }
            awaitText("Enter this profile's PIN.")
            onNodeWithText("Forgot PIN").performClick()
            awaitText("Reset Sam?")
            onNodeWithText("Reset Sam").performClick()
            await("Sam open, reset") { app.activeProfile?.id == sam && app.profileGate == null }
            assertNull(world.library.watchState(heat, sam))
            assertEquals(40 * MINUTE, world.library.watchState(heat, main)?.positionMs, "Main's history is untouched")
            assertEquals(2, world.library.profiles().size)
        }
    }

    @Test
    fun aLinkToWhatAProfileCannotSeeOpensNothingOfIt() {
        val world = populated()
        screen(world) { app ->
            awaitText("Recently Added")
            runOnIdle { app.continueFromOutside(dev.reflux.core.model.MediaId("not-in-this-library")) }
            await("being told so") { app.notice == "There is nothing to play here yet." }
            assertEquals(listOf<Destination>(Destination.Home), app.navigator.path, "no page of it is opened")
            assertNull(app.playback)
        }
    }
}
