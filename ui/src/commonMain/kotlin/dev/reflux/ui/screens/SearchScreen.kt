package dev.reflux.ui.screens

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
 * - **The field leads**: larger than a page's controls, with a quiet line saying what can be asked.
 * - **A filter, not a suggestion**: All, Movies or Shows, a switch under the field, narrowing whatever is asked.
 * - **Idle, the page is a starting point** made of this library only: recent searches (this profile's, on this device),
 *   quick finds (smart searches that find something here, each with its count), and the library's genres. Nothing is
 *   offered that would find nothing, and nothing is there only to fill space.
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
            val phone = layout.formFactor == FormFactor.PHONE
            val searchField: @Composable (Modifier) -> Unit = { modifier ->
                    SearchField(app, query, field, modifier, if (hasResults) firstResult else null, onDone = {
                        if (query.isNotBlank()) app.attempt("That search couldn't be remembered.") { app.read { rememberSearch(query) } }.invokeOnCompletion { recentVersion++ }
                        if (!hasResults || runCatching { firstResult.requestFocus() }.isFailure) focus.moveFocus(FocusDirection.Down)
                    })
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Wide, the switch sits at the field's end, on its line; on a phone, under it.
                if (phone) {
                    searchField(Modifier)
                    ScopeSwitch(app)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        searchField(Modifier.weight(1f))
                        ScopeSwitch(app)
                    }
                }
                Summary(model, sections.sumOf { it.cards.size }, query.isBlank())
            }
        }
        val current = model
        when {
            query.isBlank() -> discovery?.let { found ->
                if (found.recent.isNotEmpty()) fullWidth("recent") {
                    RecentSearches(found.recent, onChoose = ::ask, onClear = { app.attempt("That couldn't be cleared. Try again.") { app.read { forgetSearches() } }.invokeOnCompletion { recentVersion++ } })
                }
                if (found.quickFinds.isNotEmpty()) fullWidth("quick") {
                    Section("Quick finds") { QuickFinds(found.quickFinds, onChoose = { ask(it.query) }) }
                }
                if (found.genres.isNotEmpty()) fullWidth("genres") {
                    Section("Genres") { Genres(found.genres, onChoose = { ask(it.name.lowercase()) }) }
                }
                if (found.isEmpty) fullWidth("nothing") { Starting() }
            }
            current == null -> Unit
            sections.isEmpty() || sections.all { it.cards.isEmpty() } -> fullWidth("none") {
                NoResults(
                    query, scope, discovery?.quickFinds.orEmpty().take(3),
                    onClear = { ask("") },
                    onEverything = { app.searchScope = SearchScope.ALL },
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
 * Under the field: what a query found and how it was understood ("12 results · Movies · 4K"); idle, a quiet line on what can
 * be asked instead.
 */
@Composable
private fun Summary(model: SearchModel?, count: Int, idle: Boolean) {
    val narrow = LocalLayout.current.formFactor == FormFactor.PHONE
    val summary: @Composable () -> Unit = {
        when {
            idle -> Text("Titles, people, genres, or ask: “90s comedies under 2 hours”", style = LocalTypography.current.caption, color = RefluxColors.contentTertiary, maxLines = if (narrow) 2 else 1)
            // Nothing found says so below, in its own words: no "0 results" above it.
            model == null || count == 0 -> Unit
            else -> {
                val total = if (count == 1) "1 result" else "$count results"
                // How many, then each thing understood, as separate quiet words: "12 results · Movies · 4K".
                // Someone not looking at the page is told how many there are, by a node of its own.
                Box(Modifier.announces(total))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(total, style = LocalTypography.current.captionStrong, color = RefluxColors.contentSecondary, maxLines = 1)
                    for (understood in model.interpretation) {
                        Text("·", style = LocalTypography.current.caption, color = RefluxColors.contentTertiary)
                        Text(understood, style = LocalTypography.current.caption, color = RefluxColors.contentTertiary, maxLines = 1)
                    }
                }
            }
        }
    }
    summary()
}

/** All, Movies, Shows: one control, the choice lit within it, so it reads as a filter and never as a suggestion. */
@Composable
private fun ScopeSwitch(app: AppState) {
    val layout = LocalLayout.current
    Row(
        Modifier.background(States.layer(0.05f), CircleShape).border(1.dp, RefluxColors.divider, CircleShape).padding(3.dp)
            .semantics { contentDescription = "Search in" },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (scope in SearchScope.entries) {
            val chosen = app.searchScope == scope
            Pressable({ app.searchScope = scope }, Modifier.testTag("scope:${scope.label}"), CircleShape, lift = 0f) { active ->
                Box(
                    Modifier.heightIn(min = maxOf(34f, layout.minTargetDp - 6f).dp)
                        .background(
                            when {
                                chosen -> RefluxColors.contentPrimary.copy(alpha = 0.92f)
                                active -> States.layer(StateLayers.ACTIVE_ON_GLASS)
                                else -> Color.Transparent
                            },
                            CircleShape,
                        )
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(scope.label, style = LocalTypography.current.captionStrong, color = if (chosen) RefluxColors.background else RefluxColors.contentSecondary, maxLines = 1)
                }
            }
        }
    }
}

/** A section of the idle page: a heading in the page's section style, then its content. */
@Composable
private fun Section(title: String, trailing: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f).heading(), style = LocalTypography.current.title, maxLines = 1)
            trailing?.invoke()
        }
        content()
    }
}

/** This profile's recent searches, newest first, as a short line of quiet entries; Clear forgets them. */
@Composable
private fun RecentSearches(recent: List<String>, onChoose: (String) -> Unit, onClear: () -> Unit) {
    Section("Recent", trailing = { Button("Clear", onClear, style = ButtonStyle.QUIET) }) {
        TileGrid(recent, minWidth = 180.dp, gap = SuggestionGap) { words ->
            Pressable({ onChoose(words) }, Modifier.testTag("recent"), Shapes.medium, lift = 0f) { active ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = maxOf(44f, LocalLayout.current.minTargetDp).dp)
                        .background(States.layer(if (active) StateLayers.ACTIVE_ON_GLASS else 0.03f), Shapes.medium)
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icon.SEARCH, size = 16.dp, tint = RefluxColors.contentTertiary)
                    Text(words, style = LocalTypography.current.body, color = RefluxColors.contentSecondary, maxLines = 1)
                }
            }
        }
    }
}

/**
 * Quick finds: compact tiles, each an emblem, a name and how many titles it finds here. Distinct from the genres below (no
 * colour of their own) and from the filter above (a tile runs a search; the switch narrows it).
 */
@Composable
private fun QuickFinds(finds: List<QuickFind>, onChoose: (QuickFind) -> Unit) {
    val phone = LocalLayout.current.formFactor == FormFactor.PHONE
    TileGrid(if (phone) finds.take(6) else finds, minWidth = if (phone) 150.dp else 220.dp, gap = SuggestionGap) { find ->
        Pressable({ onChoose(find) }, Modifier.testTag("suggestion"), Shapes.medium, lift = 0f) { active ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = maxOf(64f, LocalLayout.current.minTargetDp).dp)
                    .background(States.layer(if (active) StateLayers.ACTIVE_ON_GLASS else 0.05f), Shapes.medium)
                    .border(1.dp, RefluxColors.divider, Shapes.medium)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Emblem(find)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(find.title, style = LocalTypography.current.bodyStrong, maxLines = 1)
                    Text(if (find.count == 1) "1 title" else "${find.count} titles", style = LocalTypography.current.caption, color = RefluxColors.contentTertiary, maxLines = 1)
                }
            }
        }
    }
}

/** A quick find's mark: a short word ("4K", "’90s") or an icon, in a small round of its own. */
@Composable
private fun Emblem(find: QuickFind) {
    Box(Modifier.size(38.dp).background(States.layer(0.10f), CircleShape).border(1.dp, RefluxColors.divider, CircleShape), contentAlignment = Alignment.Center) {
        when {
            find.kind == QuickFindKind.CONTINUE -> Icon(Icon.PLAY, size = 16.dp)
            find.kind == QuickFindKind.FAVORITES -> Icon(Icon.HEART, size = 16.dp)
            else -> Text(find.emblem.orEmpty(), style = LocalTypography.current.label, maxLines = 1)
        }
    }
}

/**
 * Genres: tiles with a colour of their own, a quiet wash from one corner, the name and its count. The colour is the
 * genre's, the same each time, from a muted set that sits on the dark page without shouting.
 */
@Composable
private fun Genres(genres: List<GenreFind>, onChoose: (GenreFind) -> Unit) {
    val layout = LocalLayout.current
    val phone = layout.formFactor == FormFactor.PHONE
    val tall = if (layout.formFactor == FormFactor.TV) 92.dp else if (phone) 72.dp else 84.dp
    TileGrid(if (phone) genres.take(8) else genres, minWidth = if (phone) 150.dp else 190.dp, gap = SuggestionGap) { genre ->
        val hue = GenreHues[(genre.name.lowercase().hashCode() and 0x7fffffff) % GenreHues.size]
        Pressable({ onChoose(genre) }, Modifier.testTag("genre"), Shapes.medium, lift = 0f) { active ->
            Box(
                Modifier.fillMaxWidth().height(tall)
                    .background(Brush.linearGradient(listOf(hue.copy(alpha = if (active) 0.62f else 0.46f), hue.copy(alpha = 0.10f))), Shapes.medium)
                    .border(1.dp, hue.copy(alpha = 0.35f), Shapes.medium)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.BottomStart,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(genre.name, style = LocalTypography.current.bodyStrong, maxLines = 1)
                    Text(if (genre.count == 1) "1 title" else "${genre.count} titles", style = LocalTypography.current.caption, color = RefluxColors.contentSecondary, maxLines = 1)
                }
            }
        }
    }
}

/** Muted hues for genre tiles: distinct from each other, never louder than the artwork elsewhere in Reflux. */
private val GenreHues = listOf(
    Color(0xFF6E5BC8), Color(0xFF3F7FBF), Color(0xFF2E9A8A), Color(0xFF5E9E4A), Color(0xFFB8893A),
    Color(0xFFB8604A), Color(0xFFA9487A), Color(0xFF7A6A9E), Color(0xFF4A8FA8), Color(0xFF8A7A4A),
)

/**
 * Tiles in as many equal columns of at least [minWidth] as fit, row by row: wide on a desk, two across a phone, a few
 * large ones on a television, never stretched into one long line. Rows are plain rows, so a D-pad moves along and between them.
 */
@Composable
private fun <T> TileGrid(items: List<T>, minWidth: Dp, gap: Dp, tile: @Composable (T) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = ((maxWidth + gap) / (minWidth + gap)).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            for (row in items.chunked(columns)) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (item in row) Box(Modifier.weight(1f)) { tile(item) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** A library with nothing yet to suggest: a word on what Search does, rather than an empty page. */
@Composable
private fun Starting() {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Search your whole library", style = LocalTypography.current.bodyStrong, align = TextAlign.Center)
        Text("Type a title or a name. Quick finds and genres appear here as your library grows.", style = LocalTypography.current.caption, color = RefluxColors.contentSecondary, align = TextAlign.Center)
    }
}

/**
 * Nothing found (B-037, B-057): said plainly with the query, which stays in the field above to change, and ways on: clear it,
 * search everything when a filter narrowed it, browse, or a quick find that does find something here.
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
    Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(56.dp).background(States.layer(0.06f), CircleShape).border(1.dp, RefluxColors.divider, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icon.SEARCH, size = 24.dp, tint = RefluxColors.contentSecondary)
        }
        Text("No matches for “$query”", Modifier.heading(), style = LocalTypography.current.bodyStrong, align = TextAlign.Center, maxLines = 2)
        Text(
            if (scope == SearchScope.ALL) "Check the spelling, try fewer words, or ask for something like “unfinished shows”."
            else "Nothing in ${scope.label}. It may be among the rest of your library.",
            Modifier.widthIn(max = 520.dp), style = LocalTypography.current.caption, color = RefluxColors.contentSecondary, align = TextAlign.Center,
        )
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button("Clear search", onClear, style = ButtonStyle.QUIET)
            if (scope != SearchScope.ALL) Button("Search everything", onEverything, style = ButtonStyle.QUIET)
            else Button("Browse movies", onBrowse, style = ButtonStyle.QUIET)
        }
        if (alternatives.isNotEmpty()) {
            Column(Modifier.widthIn(max = 760.dp).padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Or start from", style = LocalTypography.current.caption, color = RefluxColors.contentTertiary)
                QuickFinds(alternatives, onChoose)
            }
        }
    }
}

private const val TYPING_PAUSE_MS = 140L
