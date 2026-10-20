package dev.reflux.ui

import dev.reflux.core.input.InputModality
import dev.reflux.design.FormFactor
import dev.reflux.ui.player.PlayerControlSet
import dev.reflux.ui.player.PlayerControls
import dev.reflux.ui.player.Transport
import dev.reflux.ui.state.FullscreenMode
import dev.reflux.ui.state.PlayerMenu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Which player controls each kind of device shows, and why: the policy itself, without drawing anything. */
class PlayerControlsTest {
    private val allLists = PlayerMenu.entries.toList()

    private fun controls(
        formFactor: FormFactor,
        modality: InputModality,
        systemVolume: Boolean,
        mode: FullscreenMode,
        fullscreen: Boolean = false,
        portrait: Boolean = false,
        previous: Boolean = true,
        next: Boolean = true,
        menus: List<PlayerMenu> = allLists,
    ): PlayerControlSet = PlayerControls.of(formFactor, modality, systemVolume, mode, fullscreen, portrait, previous, next, menus)

    private fun phone(portrait: Boolean = true, fullscreen: Boolean = false, menus: List<PlayerMenu> = allLists) =
        controls(FormFactor.PHONE, InputModality.TOUCH, systemVolume = true, mode = FullscreenMode.LANDSCAPE, fullscreen = fullscreen, portrait = portrait, menus = menus)

    private fun tablet(portrait: Boolean = false) =
        controls(FormFactor.TABLET, InputModality.TOUCH, systemVolume = true, mode = FullscreenMode.LANDSCAPE, portrait = portrait)

    private fun desktop(modality: InputModality = InputModality.POINTER, formFactor: FormFactor = FormFactor.DESKTOP) =
        controls(formFactor, modality, systemVolume = false, mode = FullscreenMode.WINDOW)

    private fun television() =
        controls(FormFactor.TV, InputModality.DIRECTIONAL, systemVolume = true, mode = FullscreenMode.NONE)

    @Test
    fun aTelevisionShowsWhereItIsAndLeavesTheRestToTheRemote() {
        val tv = television()
        assertEquals(Transport.REMOTE, tv.transport)
        assertFalse(tv.back || tv.previous || tv.next || tv.volume || tv.fullscreen, "every one of these is a key on the remote: $tv")
        assertTrue(tv.lists.isEmpty() && tv.more == null)
        assertTrue(tv.listHints, "the lists are named, as what Down opens")
    }

    @Test
    fun aPhoneKeepsTheTransportUnderTheThumbsAndTheFewListsItNeeds() {
        val upright = phone()
        assertEquals(Transport.CENTRE, upright.transport)
        assertTrue(upright.back)
        assertFalse(upright.volume, "its own keys are the volume")
        assertEquals(listOf(PlayerMenu.AUDIO, PlayerMenu.SUBTITLES), upright.lists, "the lists most often wanted keep a button")
        assertEquals(PlayerMenu.CHAPTERS, upright.more, "the rest wait behind More, which opens on the first of them")
        // With only subtitles and speed, speed is still behind More rather than given a button of its own.
        assertEquals(PlayerMenu.SPEED, phone(menus = listOf(PlayerMenu.SUBTITLES, PlayerMenu.SPEED)).more)
    }

    @Test
    fun fullscreenIsOfferedOnlyWhereItChangesSomething() {
        // A handheld already fills its screen: fullscreen turns an upright picture sideways, and back.
        assertTrue(phone(portrait = true).fullscreen)
        assertFalse(phone(portrait = false).fullscreen, "held sideways already")
        assertTrue(phone(portrait = false, fullscreen = true).fullscreen, "but the way back stays while it is on")
        assertFalse(tablet(portrait = false).fullscreen, "a tablet on its side is already all picture")
        assertTrue(tablet(portrait = true).fullscreen)
        // A desktop window always can, and a television never.
        assertTrue(desktop().fullscreen)
        assertFalse(television().fullscreen)
    }

    @Test
    fun aTabletIsATouchDeviceNotAStretchedDesktop() {
        val tablet = tablet()
        assertEquals(Transport.CENTRE, tablet.transport, "the transport is where the hands are")
        assertFalse(tablet.volume, "its keys are the volume")
        assertEquals(listOf(PlayerMenu.CHAPTERS, PlayerMenu.AUDIO, PlayerMenu.SUBTITLES, PlayerMenu.SPEED), tablet.lists, "room for every list")
        assertNull(tablet.more)
        // The same tablet with a mouse and keyboard attached is driven like a desktop, with its own volume keys still.
        val docked = controls(FormFactor.TABLET, InputModality.POINTER, systemVolume = true, mode = FullscreenMode.LANDSCAPE)
        assertEquals(Transport.BAR, docked.transport)
        assertFalse(docked.volume)
    }

    @Test
    fun aDesktopHasThePointersBarWithVolumeAndTheWindow() {
        for (modality in listOf(InputModality.POINTER, InputModality.KEYBOARD)) {
            val desktop = desktop(modality)
            assertEquals(Transport.BAR, desktop.transport)
            assertTrue(desktop.back && desktop.volume && desktop.fullscreen)
            assertEquals(listOf(PlayerMenu.CHAPTERS, PlayerMenu.AUDIO, PlayerMenu.SUBTITLES, PlayerMenu.SPEED), desktop.lists)
        }
        // A small desktop window is laid out as a tablet, but it is still a desktop: a pointer, a window, no volume keys.
        val small = desktop(formFactor = FormFactor.TABLET)
        assertEquals(Transport.BAR, small.transport)
        assertTrue(small.volume && small.fullscreen)
    }

    @Test
    fun previousAndNextAppearOnlyWhereThereIsSomewhereToGo() {
        for (set in listOf(phone(), tablet(), desktop())) {
            assertTrue(set.previous && set.next)
        }
        val film = controls(FormFactor.DESKTOP, InputModality.POINTER, systemVolume = false, mode = FullscreenMode.WINDOW, previous = false, next = false)
        assertFalse(film.previous || film.next, "a film's start is on its timeline; there is no episode either side")
        val first = controls(FormFactor.DESKTOP, InputModality.POINTER, systemVolume = false, mode = FullscreenMode.WINDOW, previous = false, next = true)
        assertFalse(first.previous)
        assertTrue(first.next)
    }

    @Test
    fun episodesAreATabNeverAButton() {
        for (set in listOf(phone(), tablet(), desktop(), television())) {
            assertFalse(PlayerMenu.EPISODES in set.lists)
            assertTrue(set.more != PlayerMenu.EPISODES)
        }
    }
}
