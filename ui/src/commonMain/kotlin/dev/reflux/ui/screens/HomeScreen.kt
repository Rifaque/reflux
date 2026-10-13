package dev.reflux.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import dev.reflux.core.model.Availability
import dev.reflux.design.Motion
import dev.reflux.library.SourceRecord
import dev.reflux.ui.LocalChromeInsets
import dev.reflux.ui.activityLabel
import dev.reflux.ui.components.ArtworkImage
import dev.reflux.ui.components.Button
import dev.reflux.ui.components.ButtonStyle
import dev.reflux.ui.components.FocusScrollInsets
import dev.reflux.ui.components.Icon
import dev.reflux.ui.components.IconButton
import dev.reflux.ui.components.PosterCard
import dev.reflux.ui.components.SectionHeader
import dev.reflux.ui.components.TitleBackdrop
import dev.reflux.ui.components.WideCard
import dev.reflux.ui.components.initialFocus
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import dev.reflux.ui.state.Formatting
import dev.reflux.ui.state.HomeRowModel
import dev.reflux.ui.state.MediaCard
import dev.reflux.ui.state.homeModel
import dev.reflux.ui.state.libraryState
import dev.reflux.ui.theme.LocalLayout
import dev.reflux.ui.theme.LocalTypography
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.Text
import dev.reflux.ui.theme.tween
import kotlinx.coroutines.launch

/** Room a screen leaves above its first content so it starts below the floating navigation bar. */

@Composable
internal fun HomeScreen(app: AppState) {
    val model by app.libraryState { homeModel() }
    val home = model ?: return
    if (home.empty) return EmptyLibrary(app, home.sources)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The hero leaves the first row in view, so Home never opens on artwork alone.
        val heroHeight = (maxHeight * 0.56f).coerceIn(340.dp, 660.dp)
        val width = maxWidth
        LazyColumn(
            Modifier.fillMaxSize(),
            state = rememberLazyListState(),
            contentPadding = PaddingValues(bottom = LocalChromeInsets.current.bottom),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            home.featured?.let { featured -> item(key = "featured") { Hero(app, featured, home.featuredOverview, width, heroHeight) } }
            items(home.rows, key = { it.id }) { row -> RowSection(app, row) }
        }
    }
}

/** The work Home opens on, over its backdrop (or a backdrop generated from its title). */
@Composable
private fun Hero(app: AppState, card: MediaCard, overview: String?, width: Dp, height: Dp) {
    val layout = LocalLayout.current
    val background = RefluxColors.background
    Box(Modifier.fillMaxWidth().height(height)) {
        ArtworkImage(card.wide, width, Modifier.fillMaxSize(), alignment = Alignment.TopCenter) { TitleBackdrop(card.title) }
        // Scrims keep the title readable on any artwork and blend the image into the page.
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to background.copy(alpha = 0.88f), 0.5f to background.copy(alpha = 0.3f), 1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to background.copy(alpha = 0.6f), 0.24f to Color.Transparent, 0.6f to Color.Transparent, 1f to background)))
        Column(
            Modifier.align(Alignment.BottomStart).padding(horizontal = layout.safeMarginDp.dp).padding(bottom = 8.dp).widthIn(max = 580.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val inProgress = card.entry.watchState?.inProgress == true
            Text(
                if (inProgress) "CONTINUE WATCHING" else "IN YOUR LIBRARY",
                style = LocalTypography.current.label,
                color = RefluxColors.textSecondary,
            )
            Text(card.title, style = LocalTypography.current.display, maxLines = 2)
            val facts = (listOfNotNull("Offline".takeIf { card.offline }, card.caption.takeIf { card.detailsId != card.id }) + Formatting.facts(card.entry) +
                listOfNotNull(Formatting.remaining(card.entry.watchState))).distinct()
            if (facts.isNotEmpty()) Text(facts.joinToString("   ·   "), style = LocalTypography.current.bodyStrong, color = RefluxColors.textSecondary, maxLines = 1)
            if (!overview.isNullOrBlank()) Text(overview, color = RefluxColors.textSecondary, maxLines = 3)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(if (inProgress) "Resume" else "Play", { app.play(card.id) }, Modifier.initialFocus(), icon = Icon.PLAY, style = ButtonStyle.PRIMARY, enabled = !card.offline)
                Button("Details", { app.navigator.open(Destination.Details(card.detailsId)) })
            }
        }
    }
}

@Composable
private fun RowSection(app: AppState, row: HomeRowModel) {
    val layout = LocalLayout.current
    val margin = layout.safeMarginDp.dp
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column {
        SectionHeader(
            row.title,
            Modifier.padding(horizontal = margin),
            action = "See all".takeIf { row.seeAll != null },
            onAction = { row.seeAll?.let { app.navigator.open(Destination.Listing(row.title, it)) } },
        )
        Box(Modifier.hoverable(interaction)) {
            // Focus moving along the row keeps the next card in view instead of stopping at the window edge.
            FocusScrollInsets(start = margin, end = margin) {
                LazyRow(
                    state = state,
                    // Vertical padding leaves room for the focus lift, which would otherwise be clipped.
                    contentPadding = PaddingValues(horizontal = margin, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(layout.gutterDp.dp),
                ) {
                    items(row.cards, key = { it.id.value }) { card ->
                        if (row.wide) {
                            WideCard(card, { app.play(card.id) })
                        } else {
                            // The same work can be in several rows; focus returns to the one it was opened from.
                            PosterCard(card, { app.navigator.open(Destination.Details(card.detailsId)) }, focusKey = "${row.id}/${card.id.value}")
                        }
                    }
                }
            }
            // A mouse has no sideways wheel, so a row under the pointer offers to page.
            fun page(direction: Int) {
                scope.launch { state.animateScrollBy(direction * state.layoutInfo.viewportSize.width * 0.8f, Motion.navigation.tween()) }
            }
            RowPager(hovered && state.canScrollBackward, Icon.BACK, "Earlier in ${row.title}", Modifier.align(Alignment.CenterStart).padding(start = margin / 4)) { page(-1) }
            RowPager(hovered && state.canScrollForward, Icon.CHEVRON_RIGHT, "More of ${row.title}", Modifier.align(Alignment.CenterEnd).padding(end = margin / 4)) { page(1) }
        }
    }
}

@Composable
private fun RowPager(visible: Boolean, icon: Icon, description: String, modifier: Modifier, onClick: () -> Unit) {
    AnimatedVisibility(visible, modifier, enter = fadeIn(Motion.controlsShow.tween()), exit = fadeOut(Motion.controlsHide.tween())) {
        // Level with the artwork rather than with the captions under it.
        IconButton(icon, description, onClick, Modifier.padding(bottom = 44.dp), size = 44.dp, glass = true)
    }
}

/** A quiet glow behind screens that have no artwork of their own, so an empty state still has depth. */
@Composable
internal fun AmbientBackdrop() {
    val accent = RefluxColors.accent
    Box(
        Modifier.fillMaxSize().drawBehind {
            drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.22f), Color.Transparent), Offset(size.width * 0.5f, 0f), size.maxDimension * 0.6f))
            drawRect(Brush.radialGradient(listOf(Color(0xFF8A6CFF).copy(alpha = 0.10f), Color.Transparent), Offset(size.width, size.height), size.maxDimension * 0.5f))
        },
    )
}

/**
 * The first-run screen, and what Home shows while there is nothing to browse. One action: choose a folder.
 * Everything after that is automatic.
 */
@Composable
internal fun EmptyLibrary(app: AppState, sources: List<SourceRecord>) {
    val activity by app.activity.collectAsState()
    AmbientBackdrop()
    Box(Modifier.fillMaxSize().padding(top = LocalChromeInsets.current.top, bottom = LocalChromeInsets.current.bottom), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 520.dp).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val working = activityLabel(activity)
            val offline = sources.isNotEmpty() && sources.all { it.availability == Availability.UNAVAILABLE }
            val (headline, body) = when {
                sources.isEmpty() -> "Point Reflux at your media" to
                    "Choose a folder with your movies and shows. Reflux works out what is in it, finds artwork and details, " +
                    "and builds your library. Your files are never renamed, moved, or changed."
                working != null -> "Building your library" to "$working…"
                offline -> "Your media isn't connected" to
                    "Reflux can't reach ${sources.joinToString { it.displayName }} right now. Reconnect it and your library appears here."
                else -> "Nothing to watch here yet" to
                    "No movies or shows were found in ${sources.joinToString { it.displayName }}. Add another folder, or add media to this one and it shows up by itself."
            }
            Text(headline, style = LocalTypography.current.headline, align = TextAlign.Center)
            Text(body, color = RefluxColors.textSecondary, align = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Button(
                if (sources.isEmpty()) "Choose a folder" else "Add another folder",
                { app.addFolder() },
                Modifier.initialFocus(),
                icon = Icon.FOLDER,
                style = ButtonStyle.PRIMARY,
            )
        }
    }
}
