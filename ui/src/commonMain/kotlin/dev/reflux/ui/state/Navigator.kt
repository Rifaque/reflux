package dev.reflux.ui.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.SourceId
import dev.reflux.core.source.ConnectionKind
import dev.reflux.library.LibraryScope

/** A place in the browsing experience. Playback is not a destination: it sits above whatever is open. */
sealed interface Destination {
    /** The global destinations (docs/NAVIGATION.md, section 1): each is reached from the navigation, everywhere. */
    sealed interface Section : Destination

    data object Home : Section
    data object Movies : Section
    data object Shows : Section
    data object Search : Section

    /** Every copy kept on this device, or on its way, with what can be done about each. */
    data object Downloads : Section

    /** Where the library comes from: every source, whether it can be reached, what of it is in the library, and its own pages. */
    data object Sources : Section

    /**
     * Reflux's settings: the device's (online lookups, Media Atmosphere) and the active profile's own (its name, its
     * PIN, clearing its watch history), and the way to About. Part of the utility area (docs/NAVIGATION.md, section 1).
     */
    data object Settings : Section

    /**
     * Who is watching: the profiles of this installation, switching between them, adding one. The profile entry of the
     * utility area; on a phone, where the utility area is this one entry, it also leads to Downloads, Sources, and
     * Settings.
     */
    data object Profiles : Section

    /**
     * A work's page. [scope] is where it was opened from: the unified library, or a source's own library, whose
     * page lists and plays only that source's copies (docs/NAVIGATION.md, section 4).
     */
    data class Details(val itemId: MediaId, val scope: LibraryScope = LibraryScope.Unified) : Destination

    /** Telling Reflux what a movie or show is ("Identify"), reached from its details page. */
    data class Identify(val itemId: MediaId) : Destination

    /** The complete contents of a Home row: the results of a smart search, under the row's title. */
    data class Listing(val title: String, val query: String) : Destination

    /**
     * What one source holds, or one of its libraries ([section]), whether or not it takes part in the unified
     * library: "show me what is on this source".
     */
    data class SourceLibrary(val sourceId: SourceId, val section: String? = null) : Destination

    /** Choosing what kind of media to add: a folder, a server, a network share. */
    data object AddSource : Destination

    /**
     * Connecting a server or a share; with [editing], signing in again or changing where it is. [type] is the
     * product chosen, where there is more than one of [kind] (Jellyfin, Plex).
     */
    data class Connect(val kind: ConnectionKind, val editing: SourceId? = null, val type: String? = null) : Destination

    /** Reflux itself: its version and build, what it sends where, and its credits. Reached from the media sources. */
    data object About : Destination

    /** The third-party software in this build, each with its license. */
    data object Notices : Destination

    /** One component's notice and license texts, by its [name] in the notices (or Reflux's own license). */
    data class Notice(val name: String) : Destination
}

/** The primary destinations (docs/NAVIGATION.md, section 1): the library itself, in the navigation's order. */
val PrimaryDestinations: List<Destination.Section> = listOf(Destination.Home, Destination.Movies, Destination.Shows, Destination.Search)

/**
 * The utility area (docs/NAVIGATION.md, section 1): managing where the library comes from, what is kept on the device,
 * settings, and who is watching. At the foot of a rail or sidebar; on a phone, behind the profile entry at the top.
 */
val UtilityDestinations: List<Destination.Section> = listOf(Destination.Downloads, Destination.Sources, Destination.Settings, Destination.Profiles)

/** Every global destination, in the navigation's order: the primary ones, then the utility area. */
val Destinations: List<Destination.Section> = PrimaryDestinations + UtilityDestinations

/**
 * The pages about where the library comes from, and the About pages reached from them: the Sources destination
 * and what is stacked on it.
 */
val Destination.aboutSources: Boolean
    get() = this is Destination.Sources || this is Destination.AddSource || this is Destination.Connect || this is Destination.SourceLibrary ||
        this is Destination.About || this is Destination.Notices || this is Destination.Notice

/**
 * Back-stack navigation (docs/NAVIGATION.md, section 2). Home is always the root: another section replaces
 * whatever was above Home, pages stack on top of the section they were opened from, and Back unwinds one step
 * at a time until Home.
 */
@Stable
class Navigator {
    private val stack = mutableStateListOf<Destination>(Destination.Home)

    val current: Destination get() = stack.last()

    /** The section the current destination was reached from. */
    val section: Destination.Section get() = stack.last { it is Destination.Section } as Destination.Section

    /** The destination to show as current in the navigation: always the section the viewer is in. */
    val highlighted: Destination.Section get() = section

    val canGoBack: Boolean get() = stack.size > 1

    /** The pages from Home to here, for tests and diagnostics. */
    val path: List<Destination> get() = stack.toList()

    fun open(destination: Destination) {
        if (destination is Destination.Section) return show(destination)
        if (current != destination) stack += destination
    }

    /**
     * Switches to a section, dropping whatever was stacked above the previous one. Choosing the section the
     * viewer is already in goes back to its top.
     */
    fun show(section: Destination.Section) {
        stack.clear()
        stack += Destination.Home
        if (section != Destination.Home) stack += section
    }

    /** Goes back past every destination that is part of a flow the user has just finished. */
    fun leave(flow: (Destination) -> Boolean) {
        while (canGoBack && flow(current)) stack.removeAt(stack.lastIndex)
    }

    /** Returns false at the root, where there is nothing to go back to. */
    fun back(): Boolean {
        if (!canGoBack) return false
        stack.removeAt(stack.lastIndex)
        return true
    }
}
