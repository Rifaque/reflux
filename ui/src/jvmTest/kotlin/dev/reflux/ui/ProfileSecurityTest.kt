package dev.reflux.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.reflux.core.profile.PinCheck
import dev.reflux.library.checkPin
import dev.reflux.library.createProfile
import dev.reflux.library.profiles
import dev.reflux.library.setPin
import dev.reflux.ui.state.Destination
import dev.reflux.ui.state.ProfileGate
import dev.reflux.ui.state.SystemConfirmation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val MINUTE = 60_000L

/**
 * A profile's PIN, changed and removed only with itself (B-049), and Forgot PIN's choices (DECISIONS.md, 2026-11-19):
 * keep the profile, reset it, or have the device's own lock confirm its owner, which removes the PIN and keeps
 * everything. Nothing is a way around a PIN.
 */
@OptIn(ExperimentalTestApi::class)
class ProfileSecurityTest {
    @Test
    fun removingAPinNeedsThatPinAndKeepsEverythingElse() {
        val world = populated()
        val platform = FakePlatform()
        val heat = world.movie("Heat").item.id
        world.library.stopped(heat, 40 * MINUTE, 170 * MINUTE)
        val main = world.library.profiles().single().id
        val sam = world.library.createProfile("Sam")
        world.library.setPin(main, "1357", platform.pinKey!!)
        world.library.setPin(sam, "2468", platform.pinKey!!)
        screen(world, platform) { app ->
            awaitText("Enter this profile's PIN.")
            onNode(hasSetTextAction()).performTextInput("1357")
            await("open") { app.profileGate == null }
            runOnIdle { app.go(Destination.Settings) }
            awaitText("Remove PIN")
            onNodeWithText("Remove PIN").performScrollTo().performClick()
            awaitText("Remove Main's PIN?")
            // Keep PIN changes nothing.
            onNodeWithText("Keep PIN").performClick()
            await("closed") { !saysAnywhere("Remove Main's PIN?") }
            assertTrue(world.library.profiles().first { it.id == main }.hasPin)
            // A wrong PIN: refused, counted, nothing changes.
            onNodeWithText("Remove PIN").performScrollTo().performClick()
            awaitText("Remove Main's PIN?")
            onNode(hasSetTextAction()).performTextInput("0000")
            onNodeWithText("Remove PIN").performClick()
            awaitText("That's not the PIN. Try again.")
            assertTrue(world.library.profiles().first { it.id == main }.hasPin)
            // The other profile's PIN is not this one's.
            onNode(hasSetTextAction()).performTextInput("2468")
            onNodeWithText("Remove PIN").performClick()
            await("refused again") { app.pinRefused?.attempt == 2 }
            assertTrue(world.library.profiles().first { it.id == main }.hasPin)
            // The right one.
            onNode(hasSetTextAction()).performTextInput("1357")
            onNodeWithText("Remove PIN").performClick()
            await("removed") { !world.library.profiles().first { it.id == main }.hasPin }
            assertEquals(40 * MINUTE, world.library.watchState(heat)?.positionMs, "the profile's history stays")
            assertEquals(PinCheck.Accepted, world.library.checkPin(sam, "2468", platform.pinKey!!), "Sam's PIN is untouched")
            assertTrue(world.library.profiles().first { it.id == sam }.hasPin)
            awaitText("Add a PIN")
        }
        // Kept across a restart: with two profiles Reflux asks who is watching, and Main then opens without a PIN.
        screen(world, platform) { app ->
            await("the profiles") { app.profiles.size == 2 }
            runOnIdle { app.chooseProfile(app.profiles.first { it.name == "Main" }) }
            await("Main open, no PIN asked") { app.profileGate == null && app.activeProfile?.name == "Main" }
            assertFalse(app.profiles.first { it.name == "Main" }.hasPin)
        }
    }

    @Test
    fun changingAPinNeedsThePinItReplaces() {
        val world = populated()
        val platform = FakePlatform()
        val main = world.library.profiles().single().id
        world.library.setPin(main, "1357", platform.pinKey!!)
        screen(world, platform) { app ->
            awaitText("Enter this profile's PIN.")
            onNode(hasSetTextAction()).performTextInput("1357")
            await("open") { app.profileGate == null }
            // Through the state directly: without the current PIN, or with a wrong one, nothing changes.
            runOnIdle { app.setPin("4444") }
            await("refused") { app.pinRefused != null }
            runOnIdle { app.setPin("4444", "0000") }
            await("refused twice") { app.pinRefused?.attempt == 2 }
            assertEquals(PinCheck.Accepted, world.library.checkPin(main, "1357", platform.pinKey!!))
            runOnIdle { app.setPin("4444", "1357") }
            await("changed") { app.pinRefused == null && world.library.checkPin(main, "4444", platform.pinKey!!) == PinCheck.Accepted }
            assertIs<PinCheck.Rejected>(world.library.checkPin(main, "1357", platform.pinKey!!))
        }
    }

    @Test
    fun forgotPinWithTheDeviceLockRemovesThePinAndKeepsTheProfile() {
        val world = populated()
        val platform = FakePlatform()
        val heat = world.movie("Heat").item.id
        val sam = world.library.createProfile("Sam")
        world.library.stopped(heat, 12 * MINUTE, 170 * MINUTE, null, sam)
        world.library.setPin(sam, "2580", platform.pinKey!!)
        platform.systemLockAnswer = SystemConfirmation.CANCELLED
        screen(world, platform) { app ->
            awaitText("Recently Added")
            await("the profiles") { app.profiles.size == 2 }
            runOnIdle { app.chooseProfile(app.profiles.first { it.name == "Sam" }) }
            awaitText("Enter this profile's PIN.")
            onNodeWithText("Forgot PIN").performClick()
            awaitText("Forgot Sam's PIN?")
            save("profiles-forgot-pin-device-lock")
            // Cancelled or failed: nothing changes, nothing opens.
            onNodeWithText("Use device lock").performClick()
            awaitText("Nothing was changed.")
            platform.systemLockAnswer = SystemConfirmation.FAILED
            onNodeWithText("Use device lock").performClick()
            awaitText("Your device didn't confirm it's you. Nothing was changed.")
            assertEquals("Main", app.activeProfile?.name)
            assertTrue(world.library.profiles().first { it.id == sam }.hasPin)
            // Reset is not reachable without its own confirmation.
            runOnIdle { app.resetForgottenProfile() }
            waitForIdle()
            assertEquals(12 * MINUTE, world.library.watchState(heat, sam)?.positionMs)
            // Confirmed: the PIN goes, everything else stays, and Sam opens.
            platform.systemLockAnswer = SystemConfirmation.CONFIRMED
            onNodeWithText("Use device lock").performClick()
            await("Sam open") { app.activeProfile?.name == "Sam" && app.profileGate == null }
            assertFalse(world.library.profiles().first { it.id == sam }.hasPin)
            assertEquals(12 * MINUTE, world.library.watchState(heat, sam)?.positionMs, "Sam's history stays")
            assertEquals(3, platform.systemLockAsked)
            assertFalse(platform.reports.any { "2580" in it }, "no PIN in anything reported")
        }
    }

    @Test
    fun keepProfileChangesNothingAndBackStepsOutOfEachQuestion() {
        val world = populated()
        val platform = FakePlatform()
        val sam = world.library.createProfile("Sam")
        world.library.setPin(sam, "2580", platform.pinKey!!)
        screen(world, platform) { app ->
            awaitText("Recently Added")
            await("the profiles") { app.profiles.size == 2 }
            runOnIdle { app.chooseProfile(app.profiles.first { it.name == "Sam" }) }
            awaitText("Enter this profile's PIN.")
            onNodeWithText("Forgot PIN").performClick()
            onNodeWithText("Reset profile…").performClick()
            awaitText("Reset Sam?")
            // Back from the warning is the choices; Back again is the PIN.
            runOnIdle { app.dispatch(dev.reflux.core.input.SemanticAction.BACK) }
            awaitText("Forgot Sam's PIN?")
            runOnIdle { app.dispatch(dev.reflux.core.input.SemanticAction.BACK) }
            awaitText("Enter this profile's PIN.")
            assertIs<ProfileGate.Unlock>(app.profileGate)
            onNodeWithText("Forgot PIN").performClick()
            onNodeWithText("Keep profile").performClick()
            awaitText("Enter this profile's PIN.")
            assertTrue(world.library.profiles().first { it.id == sam }.hasPin)
            assertEquals("Main", app.activeProfile?.name)
        }
    }
}
