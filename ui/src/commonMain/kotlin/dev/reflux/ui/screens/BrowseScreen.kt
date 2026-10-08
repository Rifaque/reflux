package dev.reflux.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import dev.reflux.design.MaterialRole
import dev.reflux.ui.components.Button
import dev.reflux.ui.components.Chip
import dev.reflux.ui.components.Icon
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

/** A grid of posters with a header above it. The grid's density follows the adaptive layout. */
@Composable
private fun PosterGrid(app: AppState, cards: List<MediaCard>, header: @Composable () -> Unit) {
    val layout = LocalLayout.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(layout.posterWidthDp.dp),
        modifier = Modifier.fillMaxSize(),
        state = rememberLazyGridState(),
        contentPadding = PaddingValues(start = layout.safeMarginDp.dp, end = layout.safeMarginDp.dp, top = ScreenTopInset, bottom = 64.dp),
        horizontalArrangement = Arrangement.spacedBy(layout.gutterDp.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        fullWidth("header") { header() }
        items(cards, key = { it.id.value }) { card ->
            PosterCard(card, { app.navigator.open(Destination.Details(card.detailsId)) }, fill = true)
        }
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
    PosterGrid(app, cards) {
        Column(Modifier.padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = LocalTypography.current.headline, maxLines = 1)
            Text(
                when (cards.size) {
                    0 -> "Nothing here yet"
                    1 -> "1 title"
                    else -> "${cards.size} titles"
                },
                color = RefluxColors.textTertiary,
            )
        }
    }
}

/** Queries the smart search understands without any metadata, offered while the field is empty. */
private val SUGGESTIONS = listOf("4K movies I haven't watched", "unfinished shows", "90s movies", "favorites", "movies under 2 hours")

@Composable
internal fun SearchScreen(app: AppState) {
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
    DisposableEffect(Unit) { onDispose { app.textInputActive = false } }

    PosterGrid(app, model?.cards.orEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).glass(MaterialRole.ELEVATED, CircleShape).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icon.SEARCH, tint = RefluxColors.textSecondary)
                BasicTextField(
                    value = query,
                    onValueChange = { app.searchQuery = it },
                    modifier = Modifier.weight(1f).focusRequester(field).onFocusChanged { app.textInputActive = it.isFocused },
                    singleLine = true,
                    textStyle = LocalTypography.current.title.copy(color = RefluxColors.textPrimary),
                    cursorBrush = SolidColor(RefluxColors.accent),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text("Search titles, people, genres, or “4K movies I haven't watched”", style = LocalTypography.current.title, color = RefluxColors.textTertiary, maxLines = 1)
                            }
                            inner()
                        }
                    },
                )
            }
            val current = model
            when {
                query.isBlank() -> Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Try", color = RefluxColors.textTertiary)
                    SUGGESTIONS.forEach { suggestion -> Button(suggestion, { app.searchQuery = suggestion }) }
                }
                current == null -> Unit
                else -> {
                    if (current.interpretation.isNotEmpty()) {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Showing", style = LocalTypography.current.caption, color = RefluxColors.textTertiary)
                            current.interpretation.forEach { Chip(it) }
                        }
                    }
                    if (current.cards.isEmpty()) Text("Nothing in your library matches “$query”.", color = RefluxColors.textSecondary)
                }
            }
        }
    }
}

private const val TYPING_PAUSE_MS = 140L
