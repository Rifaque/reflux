package dev.reflux.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import dev.reflux.design.StateLayers
import dev.reflux.ui.components.ButtonStyle
import dev.reflux.ui.components.FocusRingReach
import dev.reflux.ui.components.FocusScrollInsets
import dev.reflux.ui.components.Pressable
import dev.reflux.ui.theme.States
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.reflux.design.MaterialRole
import dev.reflux.library.LibraryScope
import dev.reflux.ui.LocalChromeInsets
import dev.reflux.ui.ReportPageScroll
import dev.reflux.ui.components.Button
import dev.reflux.ui.components.Chip
import dev.reflux.ui.components.Icon
import dev.reflux.ui.components.IconButton
import dev.reflux.ui.components.announces
import dev.reflux.ui.components.heading
import dev.reflux.ui.components.initialFocus
import dev.reflux.ui.components.textEntry
import dev.reflux.ui.components.InputKind
import dev.reflux.ui.components.showsKeyboardOnFocus
import dev.reflux.ui.components.PosterCard
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import dev.reflux.ui.state.MediaCard
import dev.reflux.ui.state.SearchModel
import dev.reflux.ui.state.cards
import dev.reflux.ui.state.libraryState
import dev.reflux.ui.state.searchModel
import dev.reflux.ui.theme.LocalLayout
import dev.reflux.ui.theme.LocalTypography
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.Text
import dev.reflux.ui.theme.glass
import kotlinx.coroutines.delay

/**
 * A grid of posters with a header above it. The grid's density follows the adaptive layout. Each poster opens its
 * work's page in [scope]: from a source's own page, the work as that source has it.
 */
@Composable
internal fun PosterGrid(
    app: AppState,
    cards: List<MediaCard>,
    startsOnFirst: Boolean = true,
    scope: LibraryScope = LibraryScope.Unified,
    /** Pulling down at the top checks the library for changes (B-048): Movies and Shows. */
    pullToRefresh: Boolean = false,
    header: @Composable () -> Unit,
) {
    val layout = LocalLayout.current
    val grid = rememberLazyGridState()
    if (pullToRefresh) {
        dev.reflux.ui.components.PullToRefresh(
            app.pullRefreshing, { app.pullToRefresh() },
            enabled = layout.modality == dev.reflux.core.input.InputModality.TOUCH,
            top = LocalChromeInsets.current.top,
        ) { PosterGridContent(app, cards, startsOnFirst, scope, grid, header) }
    } else {
        PosterGridContent(app, cards, startsOnFirst, scope, grid, header)
    }
}

@Composable
private fun PosterGridContent(
    app: AppState,
    cards: List<MediaCard>,
    startsOnFirst: Boolean,
    scope: LibraryScope,
    grid: androidx.compose.foundation.lazy.grid.LazyGridState,
    header: @Composable () -> Unit,
) {
    val layout = LocalLayout.current
    ReportPageScroll(remember { derivedStateOf { grid.firstVisibleItemIndex > 0 || grid.firstVisibleItemScrollOffset > 0 } }.value)
    LazyVerticalGrid(
        columns = EqualColumns(layout.posterWidthDp.dp),
        modifier = Modifier.fillMaxSize(),
        state = grid,
        contentPadding = PaddingValues(start = layout.safeMarginDp.dp, end = layout.safeMarginDp.dp, top = LocalChromeInsets.current.top, bottom = LocalChromeInsets.current.bottom),
        horizontalArrangement = Arrangement.spacedBy(layout.gutterDp.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        fullWidth("header") { header() }
        itemsIndexed(cards, key = { _, card -> card.id.value }) { index, card ->
            PosterCard(
                card,
                { app.navigator.open(Destination.Details(card.detailsId, scope)) },
                if (index == 0 && startsOnFirst) Modifier.initialFocus() else Modifier,
                fill = true,
            )
        }
    }
}

/**
 * As many columns of at least [minimum] as fit, every one exactly the same width. Columns that differ by a
 * pixel make posters of different heights, and the captions under a row no longer line up.
 */
private data class EqualColumns(private val minimum: Dp) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val count = ((availableSize + spacing) / (minimum.roundToPx() + spacing)).coerceAtLeast(1)
        return List(count) { (availableSize - spacing * (count - 1)) / count }
    }
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

/** All movies, all shows, or the complete contents of a Home row ([query] is its smart search). */
@Composable
internal fun BrowseScreen(app: AppState, title: String, query: String?, shows: Boolean) {
    val loaded by app.libraryState(title, query, shows) {
        cards(
            when {
                query != null -> smartSearch(query).results
                shows -> shows()
                else -> movies()
            },
        )
    }
    val cards = loaded ?: return
    PosterGrid(app, cards, pullToRefresh = query == null) {
        Column(Modifier.padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, Modifier.heading(), style = LocalTypography.current.headline, maxLines = 1)
            Text(
                when (cards.size) {
                    0 -> "Nothing here yet"
                    1 -> "1 title"
                    else -> "${cards.size} titles"
                },
                color = RefluxColors.contentTertiary,
            )
        }
    }
}

/** Queries the smart search understands without any metadata, offered while the field is empty. */
private val SUGGESTIONS = listOf("4K movies I haven't watched", "unfinished shows", "90s movies", "favorites", "movies under 2 hours")

@Composable
internal fun SearchScreen(app: AppState) {
    val focus = LocalFocusManager.current
    val query = app.searchQuery
    var model by remember { mutableStateOf<SearchModel?>(null) }
    val field = remember { FocusRequester() }
    LaunchedEffect(query, app.revision) {
        if (query.isBlank()) {
            model = null
        } else {
            delay(TYPING_PAUSE_MS)
            model = app.read { searchModel(query) }
        }
    }
    LaunchedEffect(Unit) { field.requestFocus() }

    PosterGrid(app, model?.cards.orEmpty(), startsOnFirst = false) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).glass(MaterialRole.ELEVATED, CircleShape).padding(start = 20.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icon.SEARCH, tint = RefluxColors.contentSecondary)
                val self = remember { Any() }
                val opener = remember { dev.reflux.ui.components.FieldKeyboard() }
                val (shown, change) = dev.reflux.ui.components.rememberFieldValue(self, query) { app.searchQuery = it }
                BasicTextField(
                    value = shown,
                    onValueChange = change,
                    // Searching starts in the field, with a remote as with a keyboard.
                    modifier = Modifier.weight(1f).focusRequester(field).initialFocus()
                        // Done on the Reflux keyboard goes on to what was found (B-011).
                        .textEntry(query, { app.searchQuery = it }, "Search", InputKind.SEARCH, onDone = { focus.moveFocus(FocusDirection.Down) }, field = self, opener = opener)
                        .semantics { contentDescription = "Search your library" },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(showKeyboardOnFocus = showsKeyboardOnFocus()),
                    textStyle = LocalTypography.current.title.copy(color = RefluxColors.contentPrimary),
                    cursorBrush = SolidColor(RefluxColors.contentPrimary),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            // Short and quiet (B-034): lighter and smaller than what is typed, which is the field's own
                            // larger, brighter text; the suggestions below show what else can be asked.
                            if (query.isEmpty()) {
                                Text(SEARCH_PLACEHOLDER, style = LocalTypography.current.body, color = RefluxColors.contentTertiary, maxLines = 1)
                            }
                            inner()
                        }
                    },
                )
                if (query.isNotEmpty()) {
                    IconButton(Icon.CLOSE, "Clear search", {
                        app.searchQuery = ""
                        field.requestFocus()
                    }, tint = RefluxColors.contentSecondary)
                }
                // B-051: on a Windows or Linux desk, the Reflux keyboard for someone with only a mouse; always there, beside the
                // clear button when there is something to clear.
                if (app.platform.pointerKeyboard) {
                    IconButton(Icon.KEYBOARD, "On-screen keyboard", {
                        runCatching { field.requestFocus() }
                        opener.open()
                    }, tint = RefluxColors.contentSecondary)
                }
            }
            val current = model
            // Results fill the page by themselves; someone not looking at it is told how many there are.
            Box(
                Modifier.announces(
                    when {
                        query.isBlank() || current == null -> null
                        current.cards.size == 1 -> "1 result"
                        else -> "${current.cards.size} results"
                    },
                ),
            )
            when {
                // Suggestions (B-035, B-010): small chips with no label before them, on a row that runs to the
                // window's edges and scrolls; far enough apart that a focused chip's ring never meets its neighbour,
                // and the one focused always scrolled whole into view.
                query.isBlank() -> SuggestionRow(SUGGESTIONS) { suggestion ->
                    app.searchQuery = suggestion
                    field.requestFocus()
                }
                current == null -> Unit
                else -> {
                    if (current.interpretation.isNotEmpty()) {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Showing", style = LocalTypography.current.caption, color = RefluxColors.contentTertiary)
                            current.interpretation.forEach { Chip(it) }
                        }
                    }
                    if (current.cards.isEmpty()) {
                        NoResults(query, onClear = {
                            app.searchQuery = ""
                            field.requestFocus()
                        }, onBrowse = { app.go(Destination.Movies) })
                    }
                }
            }
        }
    }
}

private const val TYPING_PAUSE_MS = 140L

/** Search's placeholder (B-034): a few words, never truncated at a phone's width. */
internal const val SEARCH_PLACEHOLDER = "Search your library"

/** Room between suggestion chips: more than a focus ring's reach, so a focused chip's ring never touches the next. */
internal val SuggestionGap = 12.dp

/**
 * The suggestions, as a row that runs edge to edge (B-035): it starts in line with the page and scrolls to the
 * window's edges rather than being cut at the page's margin, and focus moving along it keeps the chip whole in view
 * (B-010).
 */
@Composable
private fun SuggestionRow(suggestions: List<String>, onChoose: (String) -> Unit) {
    val margin = LocalLayout.current.safeMarginDp.dp
    FocusScrollInsets(start = margin, end = margin) {
        LazyRow(
            Modifier.bleedHorizontally(margin).semantics { contentDescription = "Suggestions" },
            contentPadding = PaddingValues(horizontal = margin, vertical = FocusRingReach),
            horizontalArrangement = Arrangement.spacedBy(SuggestionGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(suggestions) { suggestion -> SuggestionChip(suggestion) { onChoose(suggestion) } }
        }
    }
}

/** A suggestion: smaller than a button, its touch target still the layout's minimum. It does not lift, only lights. */
@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    val layout = LocalLayout.current
    Pressable(onClick, Modifier.testTag("suggestion"), CircleShape, lift = 0f) { active ->
        Box(
            Modifier.heightIn(min = maxOf(36f, layout.minTargetDp).dp)
                .background(States.layer(if (active) StateLayers.ACTIVE_ON_GLASS else StateLayers.REST), CircleShape)
                .border(1.dp, RefluxColors.divider, CircleShape)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = LocalTypography.current.captionStrong, maxLines = 1)
        }
    }
}

/** Lets a row run [bleed] past its place on both sides, to the window's edges, while taking only its place in the layout. */
private fun Modifier.bleedHorizontally(bleed: Dp): Modifier = layout { measurable, constraints ->
    val extra = bleed.roundToPx() * 2
    val wider = constraints.copy(
        minWidth = constraints.minWidth + extra,
        maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth,
    )
    val placeable = measurable.measure(wider)
    layout((placeable.width - extra).coerceAtLeast(0), placeable.height) { placeable.place(-extra / 2, 0) }
}

/**
 * Nothing found (B-037): said plainly, with what to do next: change the words, or go and browse. Light, in the page's
 * own type, never a picture taking the page.
 */
@Composable
private fun NoResults(query: String, onClear: () -> Unit, onBrowse: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(52.dp).background(States.layer(0.06f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icon.SEARCH, size = 24.dp, tint = RefluxColors.contentSecondary)
        }
        Text("No matches for “$query”", Modifier.heading(), style = LocalTypography.current.bodyStrong, align = TextAlign.Center, maxLines = 2)
        Text("Try fewer or different words, or ask for something like “unfinished shows”.", style = LocalTypography.current.caption, color = RefluxColors.contentSecondary, align = TextAlign.Center)
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button("Clear search", onClear, style = ButtonStyle.QUIET)
            Button("Browse movies", onBrowse, style = ButtonStyle.QUIET)
        }
    }
}
