package dev.reflux.ui.screens

import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.reflux.core.input.InputModality
import dev.reflux.design.FormFactor
import dev.reflux.design.MaterialRole
import dev.reflux.design.StateLayers
import dev.reflux.ui.LocalChromeInsets
import dev.reflux.ui.ReportPageScroll
import dev.reflux.ui.components.Button
import dev.reflux.ui.components.ButtonStyle
import dev.reflux.ui.components.FieldKeyboard
import dev.reflux.ui.components.Icon
import dev.reflux.ui.components.IconButton
import dev.reflux.ui.components.InputKind
import dev.reflux.ui.components.PosterCard
import dev.reflux.ui.components.Pressable
import dev.reflux.ui.components.announces
import dev.reflux.ui.components.heading
import dev.reflux.ui.components.initialFocus
import dev.reflux.ui.components.rememberFieldValue
import dev.reflux.ui.components.showsKeyboardOnFocus
import dev.reflux.ui.components.textEntry
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import dev.reflux.ui.state.GenreFind
import dev.reflux.ui.state.QuickFind
import dev.reflux.ui.state.QuickFindKind
import dev.reflux.ui.state.SearchDiscovery
import dev.reflux.ui.state.SearchModel
import dev.reflux.ui.state.SearchScope
import dev.reflux.ui.state.forgetSearches
import dev.reflux.ui.state.rememberSearch
import dev.reflux.ui.state.searchDiscovery
import dev.reflux.ui.state.searchModel
import dev.reflux.ui.state.searchSections
import dev.reflux.ui.theme.LocalLayout
import dev.reflux.ui.theme.LocalTypography
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.Shapes
import dev.reflux.ui.theme.States
import dev.reflux.ui.theme.Text
import dev.reflux.ui.theme.glass
import kotlinx.coroutines.delay

/**
 * Search (B-057): one page from the first glance to what was found.
 *
 * - **The field leads**, and its own line under it holds the scope as plain tabs (All, Movies, Shows): a filter, part of
 *   the search, never a widget beside it (B-057's second design, after the owner's review).
 * - **Idle, the page is an index set in type**, made of this library only: recent searches (this profile's, on this
 *   device), "Start with" (smart searches that find something here, each with its count), and the genre index. No boxes,
 *   badges or colours; nothing offered that would find nothing.
 * - **Results** say how many there are and what was understood, in sections by kind where there is more than one.
 * - **Nothing found** keeps the query in the field and offers ways on: clear it, search everything, or a quick find
 *   that does find something.
 *
 * Every element is a control a controller, a remote, a mouse and a finger reach alike; Down from the field goes on to them.
 */
@Composable
internal fun SearchScreen(app: AppState) {
    val focus = LocalFocusManager.current
    val layout = LocalLayout.current
    val query = app.searchQuery
    val scope = app.searchScope
    var model by remember { mutableStateOf<SearchModel?>(null) }
    var discovery by remember { mutableStateOf<SearchDiscovery?>(null) }
    var recentVersion by remember { mutableStateOf(0) }
    val field = remember { FocusRequester() }
    LaunchedEffect(query, app.revision) {
        if (query.isBlank()) {
            model = null
        } else {
            delay(TYPING_PAUSE_MS)
            model = app.read { searchModel(query) }
        }
    }
    LaunchedEffect(scope, app.revision, recentVersion) { discovery = app.read { searchDiscovery(scope) } }
    LaunchedEffect(Unit) { field.requestFocus() }

    fun ask(words: String) {
        app.searchQuery = words
        runCatching { field.requestFocus() }
    }
    fun open(card: dev.reflux.ui.state.MediaCard) {
        // What was searched for and then opened is worth remembering; a query nothing came of is not.
        val asked = query
        if (asked.isNotBlank()) app.attempt("That search couldn't be remembered.") { app.read { rememberSearch(asked) } }.invokeOnCompletion { recentVersion++ }
        app.navigator.open(Destination.Details(card.detailsId))
    }

    val sections = model?.let { searchSections(it.cards, scope) }.orEmpty()
    // Down from the field, and Done on the Reflux keyboard, go straight to what was found; the filter is one step up from it.
    val firstResult = remember { FocusRequester() }
    val hasResults = query.isNotBlank() && sections.any { it.cards.isNotEmpty() }
    val grid = rememberLazyGridState()
    ReportPageScroll(remember { derivedStateOf { grid.firstVisibleItemIndex > 0 || grid.firstVisibleItemScrollOffset > 0 } }.value)
    LazyVerticalGrid(
        columns = EqualColumns(layout.posterWidthDp.dp),
        modifier = Modifier.fillMaxSize(),
        state = grid,
        contentPadding = PaddingValues(start = layout.safeMarginDp.dp, end = layout.safeMarginDp.dp, top = LocalChromeInsets.current.top, bottom = LocalChromeInsets.current.bottom),
        horizontalArrangement = Arrangement.spacedBy(layout.gutterDp.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        fullWidth("field") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SearchField(app, query, field, Modifier, if (hasResults) firstResult else null, onDone = {
                    if (query.isNotBlank()) app.attempt("That search couldn't be remembered.") { app.read { rememberSearch(query) } }.invokeOnCompletion { recentVersion++ }
                    if (!hasResults || runCatching { firstResult.requestFocus() }.isFailure) focus.moveFocus(FocusDirection.Down)
                })
                // The field's own line: what to search in, and (with a query) what was found.
                SearchLine(app, model, sections.sumOf { it.cards.size }, query.isBlank(), field)
            }
        }
        val current = model
        when {
            query.isBlank() -> discovery?.let { found ->
                fullWidth("index") {
                    if (found.isEmpty) Starting()
                    else DiscoveryIndex(
                        found,
                        onAsk = ::ask,
                        onClearRecent = { app.attempt("That couldn't be cleared. Try again.") { app.read { forgetSearches() } }.invokeOnCompletion { recentVersion++ } },
                    )
                }
            }
            current == null -> Unit
            sections.isEmpty() || sections.all { it.cards.isEmpty() } -> fullWidth("none") {
                NoResults(
                    query, scope, discovery?.quickFinds.orEmpty().take(3),
                    onClear = { ask("") },
                    onEverything = {
                        app.searchScope = SearchScope.ALL
                        runCatching { field.requestFocus() }
                    },
                    onBrowse = { app.go(Destination.Movies) },
                    onChoose = { ask(it.query) },
                )
            }
            else -> for ((index, section) in sections.withIndex()) {
                section.heading?.let { heading ->
                    fullWidth("heading-$heading") {
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                            Text(heading, Modifier.heading(), style = LocalTypography.current.title, maxLines = 1)
                            Text("${section.cards.size}", style = LocalTypography.current.caption, color = RefluxColors.contentTertiary, maxLines = 1)
                        }
                    }
                }
                items(section.cards, key = { "${section.heading}-${it.id.value}" }) { card ->
                    val first = index == 0 && card === section.cards.first()
                    PosterCard(card, { open(card) }, if (first) Modifier.focusRequester(firstResult) else Modifier, fill = true)
                }
            }
        }
    }
}

/** The field: the page's lead, its focus shown by a brighter rim, its clear and (on a desk) keyboard buttons inside it. */
@Composable
private fun SearchField(app: AppState, query: String, field: FocusRequester, modifier: Modifier, results: FocusRequester?, onDone: () -> Unit) {
    val layout = LocalLayout.current
    var focused by remember { mutableStateOf(false) }
    val height = when (layout.formFactor) {
        FormFactor.PHONE -> 56.dp
        FormFactor.TV -> 60.dp
        else -> 64.dp
    }
    Row(
        modifier.fillMaxWidth().heightIn(min = height)
            .glass(MaterialRole.ELEVATED, CircleShape)
            .border(if (focused) 1.5.dp else 1.dp, if (focused) RefluxColors.contentPrimary.copy(alpha = 0.55f) else RefluxColors.divider, CircleShape)
            .padding(start = 22.dp, end = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icon.SEARCH, size = 22.dp, tint = if (focused) RefluxColors.contentPrimary else RefluxColors.contentSecondary)
        val self = remember { Any() }
        val opener = remember { FieldKeyboard() }
        val (shown, change) = rememberFieldValue(self, query) { app.searchQuery = it }
        BasicTextField(
            value = shown,
            onValueChange = change,
            // Searching starts in the field, with a remote as with a keyboard.
            modifier = Modifier.weight(1f).focusRequester(field).initialFocus()
                .onFocusChanged { focused = it.isFocused }
                .focusProperties { if (results != null) down = results }
                // Done on the Reflux keyboard goes on to what was found (B-011).
                .textEntry(query, { app.searchQuery = it }, "Search", InputKind.SEARCH, onDone = onDone, field = self, opener = opener)
                .semantics { contentDescription = "Search your library" },
            singleLine = true,
            keyboardOptions = KeyboardOptions(showKeyboardOnFocus = showsKeyboardOnFocus()),
            textStyle = LocalTypography.current.title.copy(color = RefluxColors.contentPrimary),
            cursorBrush = SolidColor(RefluxColors.contentPrimary),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    // Short and quiet (B-034): lighter than what is typed.
                    if (query.isEmpty()) Text(SEARCH_PLACEHOLDER, style = LocalTypography.current.bodyStrong, color = RefluxColors.contentTertiary, maxLines = 1)
                    inner()
                }
            },
        )
        if (query.isNotEmpty()) {
            IconButton(Icon.CLOSE, "Clear search", {
                app.searchQuery = ""
                runCatching { field.requestFocus() }
            }, tint = RefluxColors.contentSecondary)
        }
        // B-051: on a Windows or Linux desk, the Reflux keyboard for someone with only a mouse; always there.
        if (app.platform.pointerKeyboard) {
            IconButton(Icon.KEYBOARD, "On-screen keyboard", {
                runCatching { field.requestFocus() }
                opener.open()
            }, tint = RefluxColors.contentSecondary)
        }
    }
}

/**
 * The field's own line, no surface of its own: the scope as plain tabs (All, Movies, Shows), the chosen one bright and
 * underlined, and at the far end what a query found and how it was understood ("12 results · Movies · 4K"). The same on every
 * screen: it reads as part of the field, never as a widget beside it.
 */
@Composable
private fun SearchLine(app: AppState, model: SearchModel?, count: Int, idle: Boolean, field: FocusRequester) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        ScopeTabs(app, field)
        Spacer(Modifier.weight(1f))
        if (!idle && model != null && count > 0) {
            val total = if (count == 1) "1 result" else "$count results"
            // Someone not looking at the page is told how many there are, by a node of its own.
            Box(Modifier.announces(total))
            Row(Modifier.padding(start = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                for (understood in model.interpretation.take(3)) {
                    Text(understood, style = LocalTypography.current.caption, color = RefluxColors.contentTertiary, maxLines = 1)
                    Text("\u00b7", style = LocalTypography.current.caption, color = RefluxColors.contentTertiary)
                }
                Text(total, style = LocalTypography.current.captionStrong, color = RefluxColors.contentSecondary, maxLines = 1)
            }
        }
    }
}

/** All, Movies, Shows: tabs of text. The chosen one is bright with a short rule under it; the others are quiet until pointed at. */
@Composable
private fun ScopeTabs(app: AppState, field: FocusRequester) {
    val layout = LocalLayout.current
    val ink = RefluxColors.contentPrimary
    Row(Modifier.semantics { contentDescription = "Search in" }, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (scope in SearchScope.entries) {
            val chosen = app.searchScope == scope
            Pressable(
                {
                    app.searchScope = scope
                    // A tab is the query's own filter: clicked, the typing goes on in the field, so Backspace still
                    // edits the query rather than going Back (the Linux walk, 2026-11-20). Chosen by a controller or the
                    // arrows, focus stays on the tabs to move along them.
                    if (app.input.modality == InputModality.POINTER) runCatching { field.requestFocus() }
                },
                Modifier.testTag("scope:${scope.label}").semantics { selected = chosen },
                Shapes.small, lift = 0f, role = Role.Tab,
            ) { active ->
                Box(
                    Modifier.heightIn(min = maxOf(36f, layout.minTargetDp - 8f).dp)
                        .background(if (active && !chosen) States.layer(StateLayers.ACTIVE_ON_GLASS) else Color.Transparent, Shapes.small)
                        .drawBehind {
                            if (chosen) {
                                val inset = 10.dp.toPx()
                                drawRect(ink, androidx.compose.ui.geometry.Offset(inset, size.height - 2.dp.toPx()), androidx.compose.ui.geometry.Size(size.width - 2 * inset, 2.dp.toPx()))
                            }
                        }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        scope.label, style = LocalTypography.current.captionStrong, maxLines = 1,
                        color = when {
                            chosen -> RefluxColors.contentPrimary
                            active -> RefluxColors.contentSecondary
                            else -> RefluxColors.contentTertiary
                        },
                    )
                }
            }
        }
    }
}

/**
 * The idle page as an index, set in type rather than built of boxes: on a wide screen two columns, "Start with" (recent
 * searches, then the library's quick finds) narrow on the left and the genre index wide on the right; on a phone, one above
 * the other. Everything here is this library's and finds something.
 */
@Composable
private fun DiscoveryIndex(found: SearchDiscovery, onAsk: (String) -> Unit, onClearRecent: () -> Unit) {
    val layout = LocalLayout.current
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        val wide = maxWidth >= 720.dp
        val starts: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(26.dp)) {
                if (found.recent.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(IndexRowGap)) {
                        Label("Recent", trailing = { QuietAction("Clear", onClearRecent) })
                        for (words in found.recent) IndexRow(words, null, "recent", quiet = true) { onAsk(words) }
                    }
                }
                if (found.quickFinds.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(IndexRowGap)) {
                        Label("Start with")
                        for (find in found.quickFinds) IndexRow(find.title, find.count, "suggestion") { onAsk(find.query) }
                    }
                }
            }
        }
        val genres: @Composable (Modifier) -> Unit = { modifier ->
            if (found.genres.isNotEmpty()) {
                Column(modifier, verticalArrangement = Arrangement.spacedBy(IndexRowGap)) {
                    Label("Genres")
                    GenreIndex(found.genres) { onAsk(it.name.lowercase()) }
                }
            }
        }
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(if (layout.formFactor == FormFactor.TV) 72.dp else 56.dp)) {
                starts(Modifier.weight(0.36f).widthIn(max = 420.dp))
                genres(Modifier.weight(0.64f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(30.dp)) {
                starts(Modifier.fillMaxWidth())
                genres(Modifier.fillMaxWidth())
            }
        }
    }
}

/** A section's name, small and spaced, in the page's quietest colour: a label for what follows, not a heading competing with it. */
@Composable
private fun Label(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text, Modifier.weight(1f).heading(),
            style = LocalTypography.current.label.copy(letterSpacing = 1.4.sp), color = RefluxColors.contentTertiary, maxLines = 1,
        )
        trailing?.invoke()
    }
}

/** A small text action beside a label (Clear). */
@Composable
private fun QuietAction(text: String, onClick: () -> Unit) {
    Pressable(onClick, Modifier, Shapes.small, lift = 0f) { active ->
        Box(
            Modifier.heightIn(min = 32.dp).background(if (active) States.layer(StateLayers.ACTIVE_ON_GLASS) else Color.Transparent, Shapes.small)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = LocalTypography.current.captionStrong, color = if (active) RefluxColors.contentPrimary else RefluxColors.contentSecondary, maxLines = 1)
        }
    }
}

/**
 * One line of the index: what it is, and on the right how many titles it finds, quietly. No box: pointed at or focused, the
 * line takes a soft light behind it. [quiet] is a recent search, in the secondary colour.
 */
@Composable
private fun IndexRow(title: String, count: Int?, tag: String, quiet: Boolean = false, onClick: () -> Unit) {
    val layout = LocalLayout.current
    Pressable(onClick, Modifier.fillMaxWidth().testTag(tag), Shapes.small, lift = 0f) { active ->
        Row(
            Modifier.fillMaxWidth().heightIn(min = maxOf(44f, layout.minTargetDp).dp)
                .background(if (active) States.layer(StateLayers.ACTIVE_ON_GLASS) else Color.Transparent, Shapes.small)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title, Modifier.weight(1f), style = LocalTypography.current.body, maxLines = 1,
                color = when {
                    active -> RefluxColors.contentPrimary
                    quiet -> RefluxColors.contentSecondary
                    else -> RefluxColors.contentPrimary.copy(alpha = 0.9f)
                },
            )
            if (count != null) Text("$count", style = LocalTypography.current.caption, color = RefluxColors.contentTertiary, maxLines = 1)
        }
    }
}

/**
 * The genre index: the library's genres as words, set larger than the list beside them and flowing like an index, each with
 * its count small after it. No colour of its own and no tile: a word lights, and is underlined, when it is pointed at or focused.
 */
@Composable
private fun GenreIndex(genres: List<GenreFind>, onChoose: (GenreFind) -> Unit) {
    val ink = RefluxColors.contentPrimary
    FlowRow(horizontalArrangement = Arrangement.spacedBy(SuggestionGap), verticalArrangement = Arrangement.spacedBy(IndexRowGap)) {
        for (genre in genres) {
            Pressable({ onChoose(genre) }, Modifier.testTag("genre"), Shapes.small, lift = 0f) { active ->
                Row(
                    Modifier.heightIn(min = maxOf(44f, LocalLayout.current.minTargetDp).dp)
                        .background(if (active) States.layer(StateLayers.ACTIVE_ON_GLASS) else Color.Transparent, Shapes.small)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        genre.name,
                        Modifier.drawBehind {
                            if (active) drawRect(ink.copy(alpha = 0.7f), androidx.compose.ui.geometry.Offset(0f, size.height - 1.5.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 1.5.dp.toPx()))
                        },
                        style = LocalTypography.current.title, maxLines = 1,
                        color = if (active) RefluxColors.contentPrimary else RefluxColors.contentPrimary.copy(alpha = 0.82f),
                    )
                    Text("${genre.count}", Modifier.padding(start = 6.dp, bottom = 10.dp), style = LocalTypography.current.caption, color = RefluxColors.contentTertiary, maxLines = 1)
                }
            }
        }
    }
}

/** Room between the lines of the index: enough that a focused line's ring never meets the next (B-010). */
private val IndexRowGap = 8.dp

/** A library with nothing yet to suggest: a word on what Search does, rather than an empty page. */
@Composable
private fun Starting() {
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Search your whole library", style = LocalTypography.current.bodyStrong, align = TextAlign.Center)
        Text("Type a title, a person or a genre, or ask for something like \u201cunfinished shows\u201d.", style = LocalTypography.current.caption, color = RefluxColors.contentSecondary, align = TextAlign.Center)
    }
}

/**
 * Nothing found (B-037, B-057): said plainly with the query, which stays in the field above to change, and ways on: clear it,
 * search everything when a tab narrowed it (else browse), or a line of the index that does find something here.
 */
@Composable
private fun NoResults(
    query: String,
    scope: SearchScope,
    alternatives: List<QuickFind>,
    onClear: () -> Unit,
    onEverything: () -> Unit,
    onBrowse: () -> Unit,
    onChoose: (QuickFind) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("No matches for \u201c$query\u201d", Modifier.heading(), style = LocalTypography.current.title, align = TextAlign.Center, maxLines = 2)
        Text(
            if (scope == SearchScope.ALL) "Check the spelling, try fewer words, or ask for something like \u201cunfinished shows\u201d."
            else "Nothing in ${scope.label}. It may be among the rest of your library.",
            Modifier.widthIn(max = 520.dp), style = LocalTypography.current.caption, color = RefluxColors.contentSecondary, align = TextAlign.Center,
        )
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button("Clear search", onClear, style = ButtonStyle.QUIET)
            if (scope != SearchScope.ALL) Button("Search everything", onEverything, style = ButtonStyle.QUIET)
            else Button("Browse movies", onBrowse, style = ButtonStyle.QUIET)
        }
        if (alternatives.isNotEmpty()) {
            Column(Modifier.widthIn(max = 420.dp).padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(IndexRowGap)) {
                Label("Or start with")
                for (find in alternatives) IndexRow(find.title, find.count, "suggestion") { onChoose(find) }
            }
        }
    }
}

private const val TYPING_PAUSE_MS = 140L
