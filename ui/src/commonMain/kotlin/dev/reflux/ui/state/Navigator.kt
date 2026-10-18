package dev.reflux.ui.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.SourceId
import dev.reflux.core.source.ConnectionKind

/** A place in the browsing experience. Playback is not a destination: it sits above whatever is open. */
sealed interface Destination {
    /** Top-level sections shown in the navigation bar. */
    sealed interface Section : Destination

    data object Home : Section
    data object Movies : Section
    data object Shows : Section
    data object Search : Section

    data class Details(val itemId: MediaId) : Destination

    /** Telling Reflux what a movie or show is ("Identify"), reached from its details page. */
    data class Identify(val itemId: MediaId) : Destination

    /** The complete contents of a Home row: the results of a smart search, under the row's title. */
    data class Listing(val title: String, val query: String) : Destination

    /** Where the library comes from: every source, whether it can be reached, and what can be done about it. */
    data object Sources : Destination

    /** Choosing what kind of media to add: a folder, a server, a network share. */
    data object AddSource : Destination

    /** Connecting a server or a share; with [editing], signing in again or changing where it is. */
    data class Connect(val kind: ConnectionKind, val editing: SourceId? = null) : Destination

    /** Choosing which parts of a source (a server's libraries) Reflux includes. */
    data class SourceSections(val sourceId: SourceId) : Destination
}

/** The pages about where the library comes from, which belong to no section of the library. */
val Destination.aboutSources: Boolean
    get() = this is Destination.Sources || this is Destination.AddSource || this is Destination.Connect || this is Destination.SourceSections

/**
 * Back-stack navigation. Home is always the root: sections replace each other above it, details and
 * listings stack on top, and Back unwinds one step at a time until Home.
 */
@Stable
class Navigator {
    private val stack = mutableStateListOf<Destination>(Destination.Home)

    val current: Destination get() = stack.last()

    /** The section the current destination was reached from. */
    val section: Destination.Section get() = stack.last { it is Destination.Section } as Destination.Section

    /**
     * The section to show as current in the navigation: the one the user is in, or none while they are
     * looking after the library's sources, which are not part of any section.
     */
    val highlighted: Destination.Section? get() = section.takeUnless { current.aboutSources }

    val canGoBack: Boolean get() = stack.size > 1

    fun open(destination: Destination) {
        if (destination is Destination.Section) return show(destination)
        if (current != destination) stack += destination
    }

    /** Switches to a top-level section, dropping whatever was stacked above the previous one. */
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
