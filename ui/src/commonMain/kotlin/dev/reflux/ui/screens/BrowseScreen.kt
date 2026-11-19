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
internal data class EqualColumns(private val minimum: Dp) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val count = ((availableSize + spacing) / (minimum.roundToPx() + spacing)).coerceAtLeast(1)
        return List(count) { (availableSize - spacing * (count - 1)) / count }
    }
}

internal fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) =
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


/** Search's placeholder (B-034): a few words, never truncated at a phone's width. */
internal const val SEARCH_PLACEHOLDER = "Search your library"

/** Room between suggestion chips: more than a focus ring's reach, so a focused chip's ring never touches the next. */
internal val SuggestionGap = 12.dp

