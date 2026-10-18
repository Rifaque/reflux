package dev.reflux.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.reflux.core.model.MediaId
import dev.reflux.ui.components.ArtworkImage
import dev.reflux.ui.components.Button
import dev.reflux.ui.components.ButtonStyle
import dev.reflux.ui.components.Chip
import dev.reflux.ui.components.Icon
import dev.reflux.ui.components.Pressable
import dev.reflux.ui.components.TextInput
import dev.reflux.ui.components.TitlePlaceholder
import dev.reflux.ui.components.initialFocus
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.IdentifyCandidate
import dev.reflux.ui.state.IdentifyResults
import dev.reflux.ui.state.IdentifySession
import dev.reflux.ui.state.identifyTarget
import dev.reflux.ui.state.libraryState
import dev.reflux.ui.theme.LocalTypography
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.Text

/**
 * "Identify": the user tells Reflux what a movie or show is. A search, what it found with what tells the results
 * apart (year, original title, a small poster, what it is about), and choosing one. The page says plainly when
 * it is searching, when nothing was found, and when the catalog could not be reached.
 */
@Composable
internal fun IdentifyScreen(app: AppState, itemId: MediaId) {
    val loaded by app.libraryState(itemId) { identifyTarget(itemId) }
    val target = loaded ?: return
    // One session for the page's life: library changes while it is open do not throw away what was found.
    val session = remember(itemId) { IdentifySession(app, itemId, target.title, target.show, target.corrected, target.match, target.year, target.latestYear) }
    // The work's own name is searched once, as the page opens; after that, when the user asks.
    LaunchedEffect(session) { session.search() }
    DisposableEffect(Unit) { onDispose { app.textInputActive = false } }
    val what = if (session.show) "show" else "movie"
    Page {
        item(key = "heading") {
            Heading(
                "Identify this $what",
                "Search for what “${session.title}” really is. Reflux remembers your choice; your files aren't renamed or changed.",
            )
        }
        item(key = "search") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                TextInput(
                    session.query, { session.query = it }, "Title",
                    Modifier.weight(1f).initialFocus(),
                    placeholder = "A title, and its year if you know it",
                    last = true, enabled = !session.applying,
                    onFocus = { app.textInputActive = it }, onDone = { session.search() },
                )
                Button("Search", { session.search() }, icon = Icon.SEARCH, enabled = !session.applying)
            }
        }
        when (val results = session.results) {
            null, IdentifyResults.Searching -> item(key = "searching") { Note("Searching…") }
            IdentifyResults.NothingFound -> item(key = "nothing") {
                Note("Nothing was found for “${session.asked}”. Try another spelling or title, or add the year.")
            }
            IdentifyResults.Unreachable -> item(key = "unreachable") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Note("The catalog couldn't be reached, so nothing was searched. Check this device's internet connection. Your library is unchanged.")
                    Button("Try again", { session.search() }, icon = Icon.REFRESH)
                }
            }
            is IdentifyResults.Found -> items(results.candidates, key = { it.candidate.ref.toString() }) { candidate ->
                CandidateRow(candidate, enabled = !session.applying) { session.choose(candidate) }
            }
        }
        item(key = "actions") {
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                // What the user said can be taken back; Reflux then identifies it from its files again.
                if (session.corrected) Button("Let Reflux identify it", { session.undo() }, enabled = !session.applying)
                Button("Cancel", { app.navigator.back() }, style = ButtonStyle.QUIET)
                if (session.applying) Note("Applying…")
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, Modifier.padding(vertical = 6.dp), color = RefluxColors.textSecondary)
}

/** One result: a small poster, its name and year, and enough of what it is about to tell it from the others. */
@Composable
private fun CandidateRow(candidate: IdentifyCandidate, enabled: Boolean, onChoose: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Pressable(onChoose, Modifier.fillMaxWidth(), shape, enabled = enabled, lift = 0.15f) { active ->
        Row(
            Modifier.fillMaxWidth()
                .background(Color.White.copy(alpha = if (active) 0.07f else 0f))
                .border(1.dp, Color.White.copy(alpha = if (candidate.current) 0.14f else 0.06f), shape)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val poster = RoundedCornerShape(8.dp)
            Box(Modifier.width(POSTER_WIDTH).aspectRatio(2f / 3f).clip(poster)) {
                // Decoded at the size it is shown: a result never costs a full-size poster.
                ArtworkImage(candidate.poster, POSTER_WIDTH, Modifier.fillMaxSize()) { TitlePlaceholder(candidate.title, null) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(candidate.title, Modifier.weight(1f, fill = false), style = LocalTypography.current.bodyStrong, maxLines = 2)
                    if (candidate.current) Chip("Current match", emphasized = true)
                }
                val facts = listOfNotNull(candidate.year?.toString(), candidate.originalTitle)
                if (facts.isNotEmpty()) Text(facts.joinToString("  ·  "), style = LocalTypography.current.metadata, color = RefluxColors.textSecondary, maxLines = 1)
                candidate.overview?.let { Text(it, style = LocalTypography.current.caption, color = RefluxColors.textTertiary, maxLines = 2) }
            }
        }
    }
}

private val POSTER_WIDTH = 56.dp
