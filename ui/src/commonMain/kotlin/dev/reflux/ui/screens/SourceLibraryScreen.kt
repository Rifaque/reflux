package dev.reflux.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.reflux.core.model.Availability
import dev.reflux.core.model.SourceId
import dev.reflux.library.Library
import dev.reflux.library.LibraryScope
import dev.reflux.library.SourceLibrary
import dev.reflux.library.SourceRecord
import dev.reflux.library.inclusion
import dev.reflux.ui.components.Button
import dev.reflux.ui.components.ButtonStyle
import dev.reflux.ui.components.Icon
import dev.reflux.ui.components.Tab
import dev.reflux.ui.components.heading
import dev.reflux.ui.components.initialFocus
import dev.reflux.ui.components.pane
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import dev.reflux.ui.state.MediaCard
import dev.reflux.ui.state.cards
import dev.reflux.ui.state.libraryState
import dev.reflux.ui.theme.LocalTypography
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.Text

/** What a source's own page shows: the source, its libraries, and what is on it (or in one of them). */
internal class SourceLibraryModel(
    val record: SourceRecord,
    val libraries: List<SourceLibrary>,
    val cards: List<MediaCard>,
    val signInRequired: Boolean,
)

internal fun Library.sourceLibraryModel(sourceId: SourceId, section: String?): SourceLibraryModel? {
    val record = source(sourceId) ?: return null
    val scope = LibraryScope.OfSource(sourceId, section)
    // In the order the library's own grids use: by title, leading articles aside (TitleText.sortKey).
    val works = (movies(scope) + shows(scope)).sortedBy { dev.reflux.core.identify.TitleText.sortKey(it.item.title) }
    return SourceLibraryModel(record, inclusion(sourceId)?.libraries.orEmpty(), cards(works), record.signInRequired)
}

/**
 * "Show me what is on this source" (docs/NAVIGATION.md, section 4): one source, or one of its libraries, as it is,
 * whether or not it takes part in the unified library. Its works open, play, and are kept from this source's copies;
 * it reads the same library as everything else, and says when the source is away, refuses Reflux, or holds nothing.
 */
@Composable
internal fun SourceLibraryScreen(app: AppState, sourceId: SourceId, section: String?) {
    // The library shown is the page's own state: Back from a work comes back to it.
    var shown by rememberSaveable(sourceId) { mutableStateOf(section) }
    val loaded by app.libraryState(sourceId, shown) { LoadedSource(sourceLibraryModel(sourceId, shown)) }
    val model = (loaded ?: return).value ?: return Removed(app)
    val record = model.record
    val library = model.libraries.firstOrNull { it.id == shown }
    val title = library?.name ?: record.displayName
    val scope = LibraryScope.OfSource(sourceId, shown)
    PosterGrid(app, model.cards, startsOnFirst = model.cards.isNotEmpty(), scope = scope) {
        Column(Modifier.padding(bottom = 6.dp).pane(title), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, Modifier.heading(), style = LocalTypography.current.headline, maxLines = 1)
                val state = when {
                    model.signInRequired -> "Needs signing in again"
                    record.availability == Availability.UNAVAILABLE -> "Can't be reached"
                    else -> null
                }
                val inclusion = when {
                    !record.included -> "Not in your library"
                    library != null && !library.included -> "Not in your library"
                    else -> "In your library"
                }
                Text(
                    listOfNotNull(library?.let { record.displayName }, state, inclusion, count(model.cards.size)).joinToString("  ·  "),
                    color = RefluxColors.textTertiary,
                )
            }
            // A source made of several libraries can be looked at one library at a time.
            if (model.libraries.size > 1) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Tab("All", shown == null, if (model.cards.isEmpty() && shown == null) Modifier.initialFocus() else Modifier) { shown = null }
                    model.libraries.forEach { candidate -> Tab(candidate.name, shown == candidate.id) { shown = candidate.id } }
                }
            }
            val note = when {
                model.signInRequired -> "${record.displayName} no longer accepts Reflux's sign-in. What it had is listed as it was last seen; sign in again from Sources to play it."
                record.availability == Availability.UNAVAILABLE -> "Reflux can't reach ${record.displayName} right now. What it had is listed as it was last seen, and plays again when it's back."
                !record.included -> "What's here is not in Home, Movies, Shows, or Search. It can be opened and played here, and you can put it in your library from Sources."
                library != null && !library.included -> "This library is not in Home, Movies, Shows, or Search. It can be opened and played here."
                else -> null
            }
            note?.let { Text(it, color = RefluxColors.textSecondary) }
            if (model.cards.isEmpty()) {
                Text(
                    when {
                        record.lastScanAtEpochMs == null && record.availability != Availability.UNAVAILABLE -> "Reading ${record.displayName}…"
                        model.libraries.isEmpty() && library == null && record.lastScanAtEpochMs != null && app.platform.connections?.sectioned(sourceId) == true ->
                            "${record.displayName} has no libraries with movies or shows."
                        else -> "No movies or shows here yet."
                    },
                    color = RefluxColors.textTertiary,
                )
                if (model.libraries.size <= 1) Button("Sources", { app.navigator.back() }, Modifier.initialFocus(), icon = Icon.BACK, style = ButtonStyle.QUIET)
            }
        }
    }
}

private class LoadedSource(val value: SourceLibraryModel?)

private fun count(titles: Int): String = when (titles) {
    0 -> "Nothing here yet"
    1 -> "1 title"
    else -> "$titles titles"
}

/** A source that was removed while its page was open: there is nothing left to show, and Back leads out. */
@Composable
private fun Removed(app: AppState) {
    Page {
        item(key = "gone") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("This source is no longer in Reflux.", color = RefluxColors.textSecondary)
                Button("Sources", { app.go(Destination.Sources) }, Modifier.initialFocus(), icon = Icon.BACK, style = ButtonStyle.PRIMARY)
            }
        }
    }
}
