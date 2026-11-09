package dev.reflux.ui.screens

import dev.reflux.ui.theme.States
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import dev.reflux.design.FormFactor
import dev.reflux.ui.components.FocusScrollsNothing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import dev.reflux.core.model.Availability
import dev.reflux.design.Motion
import dev.reflux.library.SourceRecord
import dev.reflux.ui.LocalChromeInsets
import dev.reflux.ui.ReportPageScroll
import dev.reflux.ui.activityLabel
import dev.reflux.ui.components.ArtworkImage
import dev.reflux.ui.components.rememberArtwork
import dev.reflux.ui.components.rememberPageTone
import dev.reflux.ui.components.ReportMaterialBackdrop
import androidx.compose.ui.layout.layout
import dev.reflux.ui.components.Button
import dev.reflux.ui.components.ButtonStyle
import dev.reflux.ui.components.FocusScrollInsets
import dev.reflux.ui.components.Icon
import dev.reflux.ui.components.IconButton
import dev.reflux.ui.components.PosterCard
import dev.reflux.ui.components.SectionHeader
import dev.reflux.ui.components.TitleBackdrop
import dev.reflux.ui.components.WideCard
import dev.reflux.ui.components.heading
import dev.reflux.ui.components.initialFocus
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.ArtworkChoice
import dev.reflux.ui.state.Destination
import dev.reflux.ui.state.Formatting
import dev.reflux.ui.state.HomeRowModel
import dev.reflux.ui.state.MediaCard
import dev.reflux.ui.state.homeModel
import dev.reflux.ui.state.libraryState
import dev.reflux.ui.theme.LocalLayout
import dev.reflux.ui.theme.LocalTypography
import dev.reflux.ui.theme.MotionPolicy
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.Text
import dev.reflux.ui.theme.tween
import kotlinx.coroutines.launch

/** Room a screen leaves above its first content so it starts below the floating navigation bar. */

@Composable
internal fun HomeScreen(app: AppState) {
    val model by app.libraryState { homeModel() }
    // Reading a large library on a cold start is a genuine wait: the loader, which shows only if it lasts (DESIGN.md,
    // "Motion language").
    val home = model ?: return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        dev.reflux.ui.components.RefluxLoader(size = 64.dp, label = "Loading your library")
    }
    if (home.empty) return EmptyLibrary(app, home.sources)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The hero leaves the first row in view, so Home never opens on artwork alone.
        val heroHeight = (maxHeight * 0.62f).coerceIn(340.dp, 720.dp)
        val width = maxWidth
        val featured = home.featured
        // Without wide artwork the poster fills the hero where the hero is not too wide for it (a phone held
        // upright); otherwise the hero is generated, in the color of the poster where there is one.
        val heroImage = rememberArtwork(featured?.let { ArtworkChoice.landscape(it.wide, it.poster, width / heroHeight) }.orEmpty(), width)
        val heroPoster = rememberArtwork(featured?.poster, 220.dp)
        val atmosphere = if (featured != null) rememberPageTone(heroImage ?: heroPoster, featured.title, app.mediaAtmosphere) else RefluxColors.background
        val list = rememberLazyListState()
        val heroPx = with(LocalDensity.current) { heroHeight.toPx() }
        ReportMaterialBackdrop(heroImage ?: heroPoster) { list.artworkPresence(heroPx) }
        // Where the artwork runs under the navigation, the page's own content starts after it.
        val contentStart = LocalChromeInsets.current.contentStart
        // The atmosphere belongs to the hero: it recedes as the hero scrolls away, and the library beneath is plain.
        val tone = { lerp(RefluxColors.background, atmosphere, list.artworkPresence(heroPx)) }
        PageAtmosphere(tone, heroHeight)
        ReportPageScroll(remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0 } }.value)
        val rows: @Composable (place: (index: Int) -> Modifier) -> Unit = { place ->
            LazyColumn(
                Modifier.fillMaxSize(),
                state = list,
                contentPadding = PaddingValues(start = contentStart, bottom = LocalChromeInsets.current.bottom),
                verticalArrangement = Arrangement.spacedBy(if (LocalLayout.current.formFactor == FormFactor.PHONE) 26.dp else 36.dp),
            ) {
                featured?.let { featured -> item(key = "featured") { Box(place(0).bleedStart(contentStart)) { Hero(app, featured, heroImage, heroPoster, tone, home.featuredOverview, heroHeight) } } }
                val first = if (home.featured == null) 0 else 1
                itemsIndexed(home.rows, key = { _, row -> row.id }) { index, row -> Box(place(first + index)) { RowSection(app, row) } }
            }
        }
        if (LocalLayout.current.formFactor == FormFactor.TV) {
            // A television's page moves a row at a time: the row with focus sits under the navigation, its
            // title showing, and the one after it is in view. Nothing stops half way under the bar.
            val scope = rememberCoroutineScope()
            val under = with(LocalDensity.current) { LocalChromeInsets.current.top.roundToPx() }
            FocusScrollsNothing {
                rows { index ->
                    Modifier.onFocusChanged { focus ->
                        if (focus.hasFocus) scope.launch { list.moveToItem(index, if (index == 0) 0 else -under) }
                    }
                }
            }
        } else {
            rows { Modifier }
        }
    }
}

/**
 * Lets artwork run [bleed] further toward the start than its place in the page, under the navigation, while
 * taking only its place in the layout. Nothing else moves; zero is no change.
 */
internal fun Modifier.bleedStart(bleed: Dp): Modifier = if (bleed <= 0.dp) this else layout { measurable, constraints ->
    val extra = bleed.roundToPx()
    val wider = constraints.copy(
        minWidth = constraints.minWidth + extra,
        maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth,
    )
    val placeable = measurable.measure(wider)
    layout((placeable.width - extra).coerceAtLeast(0), placeable.height) { placeable.place(-extra, 0) }
}

/** Brings an item to [offset] the way the page moves, or at once with motion reduced. */
internal suspend fun LazyListState.moveToItem(index: Int, offset: Int = 0) =
    if (MotionPolicy.reduced) scrollToItem(index, offset) else animateScrollToItem(index, offset)

/**
 * How much of the artwork that opens this list (its first item, [heightPx] tall) is still on screen: 1 at the
 * top, 0 once it has gone.
 */
internal fun LazyListState.artworkPresence(heightPx: Float): Float =
    if (firstVisibleItemIndex > 0 || heightPx <= 0f) 0f else 1f - (firstVisibleItemScrollOffset / heightPx).coerceIn(0f, 1f)

/**
 * The page behind a work's artwork: [tone] (the background washed with the artwork's atmosphere) down to the
 * bottom of the artwork, [heroHeight], then fading to the plain background. The artwork's own scrims end in
 * the same tone, so the image dissolves into the page with no seam wherever it has scrolled to. [tone] is read
 * as the page draws, so scrolling repaints the wash without recomposing anything.
 */
@Composable
internal fun PageAtmosphere(tone: () -> Color, heroHeight: Dp) {
    val background = RefluxColors.background
    Box(
        Modifier.fillMaxSize().drawBehind {
            val start = heroHeight.toPx()
            drawRect(Brush.verticalGradient(0f to tone(), 1f to background, startY = start, endY = start + ATMOSPHERE_FADE.toPx()))
        },
    )
}

/** Scrims that keep text readable over artwork and dissolve it into the page's [tone]. */
internal fun Modifier.artworkScrims(tone: () -> Color, left: Float, right: Float, start: Dp = 0.dp): Modifier = drawBehind {
    val color = tone()
    // Darkest where the text starts. Where the artwork runs on under the navigation ([start]), it is shaded only
    // lightly: the navigation's own material keeps its labels readable, and the artwork stays visible beneath it.
    val from = (start.toPx() / size.width).coerceIn(0f, 0.5f)
    drawRect(
        Brush.horizontalGradient(
            0f to color.copy(alpha = if (from > 0f) left * UNDER_NAVIGATION else left),
            from to color.copy(alpha = left),
            from + 0.58f * (1f - from) to color.copy(alpha = left * 0.4f),
            1f to color.copy(alpha = right),
        ),
    )
    drawRect(Brush.verticalGradient(0f to color.copy(alpha = 0.55f), 0.23f to Color.Transparent, 0.55f to color.copy(alpha = 0.2f), 1f to color))
    // Where the navigation sits over the artwork, before the page scrolls and it turns to glass: a band of
    // fixed height, whatever the artwork's, so its labels and icons hold 3:1 over a bright sky.
    val band = NAVIGATION_SCRIM.toPx()
    drawRect(Brush.verticalGradient(0f to color.copy(alpha = 0.72f), 1f to Color.Transparent, endY = band), size = Size(size.width, band))
    // The page goes on in this tone. Overlapping it by a pixel keeps an edge that falls between pixels (as at
    // 2.625x) from leaving a sliver of the artwork showing through the antialiased bottom row.
    drawRect(color, Offset(0f, size.height - 1f), Size(size.width, 2f))
}

/** How much of the text's shade the artwork under the navigation keeps. */
private const val UNDER_NAVIGATION = 0.35f

/** The band at the top of artwork that keeps the navigation over it legible. */
private val NAVIGATION_SCRIM = 160.dp

/** How far below the artwork its atmosphere reaches before the page is plain again. */
private val ATMOSPHERE_FADE = 520.dp

/** The work Home opens on, over its backdrop (or one generated in the color of its poster, or its title). */
@Composable
private fun Hero(app: AppState, card: MediaCard, image: ImageBitmap?, poster: ImageBitmap?, tone: () -> Color, overview: String?, height: Dp) {
    val layout = LocalLayout.current
    val compact = layout.formFactor == FormFactor.PHONE
    // At least this tall: a large text size makes it taller rather than running its words up under the navigation.
    Box(Modifier.fillMaxWidth().heightIn(min = height)) {
        ArtworkImage(image, Modifier.matchParentSize(), alignment = Alignment.TopCenter) { TitleBackdrop(card.title, poster = poster) }
        Box(Modifier.matchParentSize().artworkScrims(tone, left = 0.9f, right = 0f, start = LocalChromeInsets.current.contentStart))
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = LocalChromeInsets.current.contentStart).padding(horizontal = layout.safeMarginDp.dp)
                .padding(top = LocalChromeInsets.current.top, bottom = 4.dp).widthIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
        ) {
            val inProgress = card.entry.watchState?.inProgress == true
            // The title speaks for itself; only something worth knowing is said above it, quietly.
            if (inProgress) Text("Continue watching", style = LocalTypography.current.metadata, color = RefluxColors.contentSecondary)
            Text(card.title, Modifier.heading(), style = LocalTypography.current.display, maxLines = 2)
            val facts = (listOfNotNull("Not connected".takeIf { card.offline }, card.caption.takeIf { card.detailsId != card.id }) + Formatting.facts(card.entry) +
                listOfNotNull(Formatting.remaining(card.entry.watchState))).distinct()
            if (facts.isNotEmpty()) Text(facts.joinToString("  ·  "), style = LocalTypography.current.metadata, color = RefluxColors.contentSecondary, maxLines = 1)
            if (!overview.isNullOrBlank()) Text(overview, Modifier.widthIn(max = 540.dp), color = RefluxColors.contentSecondary, maxLines = if (compact) 2 else 3)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    // Vertical padding leaves room for the focus lift and ring, which would otherwise be clipped.
                    contentPadding = PaddingValues(horizontal = margin, vertical = 14.dp),
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

/**
 * A quiet light behind screens that have no artwork of their own, so an empty state still has depth: a
 * neutral glow from above, as if from a screen just out of view, and nothing else.
 */
@Composable
internal fun AmbientBackdrop() {
    Box(
        Modifier.fillMaxSize().drawBehind {
            drawRect(Brush.radialGradient(listOf(States.layer(0.075f), Color.Transparent), Offset(size.width * 0.5f, 0f), size.maxDimension * 0.6f))
            drawRect(Brush.radialGradient(listOf(States.layer(0.025f), Color.Transparent), Offset(size.width * 0.5f, size.height), size.maxDimension * 0.45f))
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
    val insets = LocalChromeInsets.current
    Box(Modifier.fillMaxSize().padding(start = insets.contentStart, top = insets.top, bottom = insets.bottom), contentAlignment = Alignment.Center) {
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
            // The brand's motion where it means something: the mark drawing itself to welcome a first run, and flowing
            // while a library is genuinely being built.
            when {
                sources.isEmpty() -> dev.reflux.ui.components.RefluxMarkDrawIn(Modifier.padding(bottom = 6.dp), size = 64.dp)
                working != null -> dev.reflux.ui.components.RefluxLoader(Modifier.padding(bottom = 6.dp), size = 64.dp, label = "Building your library", delayMs = 0)
            }
            Text(headline, Modifier.heading(), style = LocalTypography.current.headline, align = TextAlign.Center)
            Text(body, color = RefluxColors.contentSecondary, align = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Button(
                if (sources.isEmpty()) "Choose a folder" else "Add another folder",
                { app.addFolder() },
                Modifier.initialFocus(),
                icon = Icon.FOLDER,
                style = ButtonStyle.PRIMARY,
            )
            // The fastest way in is a folder; a server or a share is one step aside, not in the way.
            when {
                sources.isEmpty() && app.platform.connections?.kinds?.isNotEmpty() == true ->
                    Button("Connect a server or share", { app.navigator.open(Destination.AddSource) }, style = ButtonStyle.QUIET)
                sources.isNotEmpty() -> Button("Media sources", { app.navigator.open(Destination.Sources) }, style = ButtonStyle.QUIET)
            }
        }
    }
}
