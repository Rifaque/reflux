package dev.reflux.ui.player

import dev.reflux.core.input.InputModality
import dev.reflux.design.FormFactor
import dev.reflux.ui.state.FullscreenMode
import dev.reflux.ui.state.PlayerMenu

/** Where play, pause, and the ten-second seeks are. */
enum class Transport {
    /** In the middle of the picture, under the thumbs. */
    CENTRE,

    /** In the bar, beside the timeline, for a pointer or a keyboard. */
    BAR,

    /** Nowhere on screen: the remote's own keys play, pause, and seek. */
    REMOTE,
}

/** The player's controls on this device in this state: what is on screen, and where. */
data class PlayerControlSet(
    val transport: Transport,
    /** The way back to the library on screen (a television's remote has its own Back). */
    val back: Boolean,
    val previous: Boolean,
    val next: Boolean,
    /** Loudness in the bar, where the device has no volume of its own. */
    val volume: Boolean,
    val fullscreen: Boolean,
    /** The lists with a button of their own in the bar, in order. */
    val lists: List<PlayerMenu>,
    /** The list a "More options" button opens, where the rest of them wait behind one button; null where there is none. */
    val more: PlayerMenu?,
    /** The lists named beneath the timeline, as what Down opens (a television). */
    val listHints: Boolean,
)

/**
 * Which player controls a device shows. Reflux is one product with one set of actions, but not one bar copied
 * onto every screen: a control is shown where it is the best way to do something there, and left out where
 * the device's own buttons do it, where it would do nothing, or where there is nothing for it to act on.
 * Everything left out stays reachable: by the remote, the keyboard, a gesture, or the lists' own tabs.
 */
object PlayerControls {
    /** On a phone, the lists that keep a button of their own; the rest are behind "More options". */
    private val phoneLists = listOf(PlayerMenu.SUBTITLES, PlayerMenu.AUDIO)

    fun of(
        formFactor: FormFactor,
        modality: InputModality,
        /** Whether loudness is the device's own (its keys, a remote). */
        systemVolume: Boolean,
        fullscreenMode: FullscreenMode,
        fullscreen: Boolean,
        /** Whether the window is taller than it is wide. */
        portrait: Boolean,
        hasPrevious: Boolean,
        hasNext: Boolean,
        /** The lists that have something in them for what is playing (PlaybackSession.menus). */
        menus: List<PlayerMenu>,
    ): PlayerControlSet {
        if (formFactor == FormFactor.TV) {
            return PlayerControlSet(Transport.REMOTE, back = false, previous = false, next = false, volume = false, fullscreen = false, lists = emptyList(), more = null, listHints = true)
        }
        val phone = formFactor == FormFactor.PHONE
        // Episodes are a tab among the lists; Previous and Next are their buttons in the bar.
        val lists = menus.filter { it != PlayerMenu.EPISODES }
        val inline = if (phone) lists.filter { it in phoneLists } else lists
        return PlayerControlSet(
            transport = if (phone || modality == InputModality.TOUCH) Transport.CENTRE else Transport.BAR,
            back = true,
            // Previous at the start of a film would only go back to its beginning, which the timeline does.
            previous = hasPrevious,
            next = hasNext,
            volume = !systemVolume && !phone,
            fullscreen = when (fullscreenMode) {
                FullscreenMode.WINDOW -> true
                // Already the whole screen: only turning a portrait screen sideways, or back, changes anything.
                FullscreenMode.LANDSCAPE -> fullscreen || portrait
                FullscreenMode.NONE -> false
            },
            lists = inline,
            more = lists.firstOrNull { it !in inline },
            listHints = false,
        )
    }
}
