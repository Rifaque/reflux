package dev.reflux.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.reflux.core.model.MediaId
import dev.reflux.design.FormFactor
import dev.reflux.design.MaterialRole
import dev.reflux.design.NavigationStyle
import dev.reflux.ui.components.ArtworkImage
import dev.reflux.ui.components.Button
import dev.reflux.ui.components.ButtonStyle
import dev.reflux.ui.components.Icon
import dev.reflux.ui.components.IconButton
import dev.reflux.ui.components.TitlePlaceholder
import dev.reflux.ui.components.absorbsPointer
import dev.reflux.ui.components.initialFocus
import dev.reflux.ui.components.pane
import dev.reflux.ui.components.takesFocus
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import dev.reflux.ui.state.detailsModel
import dev.reflux.ui.state.libraryState
import dev.reflux.ui.state.offlineState
import dev.reflux.ui.state.offlineStatus
import dev.reflux.ui.theme.LocalLayout
import dev.reflux.ui.theme.LocalTypography
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.Shapes
import dev.reflux.ui.theme.Text
import dev.reflux.ui.theme.glass

/**
 * Media Quick View (F-P3): a film or show at a glance, over the page it was opened from, with what can be done with
 * it here. Its main action (Play, or Resume) has focus when it opens; Back, X, Menu, or a click outside closes it,
 * and focus goes back to the card that opened it. Only actions that work for this work on this device are offered:
 * Play where something can play, Download where the source and the platform can keep a copy. Missing artwork, a
 * missing overview, missing facts simply leave their place out.
 */
@Composable
internal fun QuickViewOverlay(app: AppState, itemId: MediaId) {
    val loaded by app.libraryState(itemId) { QuickViewLoaded(detailsModel(itemId, app.platform.device, app.viewer, app.sourceCapabilities)) }
    val offline by app.offlineState(itemId) { app.platform.offline?.let { offlineStatus(itemId, it, dev.reflux.library.LibraryScope.Unified) } }
    val layout = LocalLayout.current
    val phone = layout.navigation == NavigationStyle.BOTTOM_BAR || layout.navigation == NavigationStyle.COMPACT_RAIL
    Box(
        Modifier.fillMaxSize().background(RefluxColors.background.copy(alpha = 0.72f)).pointerInput(app) { detectTapGestures { app.closeQuickView() } },
        contentAlignment = Alignment.Center,
    ) {
        // Loading takes a moment at most: nothing is drawn until there is something to say, then it is said whole.
        val state = loaded ?: return@Box
        val details = state.details
        Column(
            Modifier.widthIn(max = if (layout.formFactor == FormFactor.TV) 760.dp else 640.dp).fillMaxWidth()
                .padding(horizontal = layout.safeMarginDp.dp + 8.dp, vertical = 24.dp)
                .absorbsPointer()
                .glass(MaterialRole.ELEVATED, Shapes.large)
                .pane("Quick view")
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (details == null) {
                // The work left the library while the view was opening: said, with the way out.
                Text("This is no longer in your library.", Modifier.semantics { heading() }, style = LocalTypography.current.bodyStrong)
                Button("Close", { app.closeQuickView() }, Modifier.initialFocus(onArrival = true).takesFocus(), style = ButtonStyle.QUIET)
                return@Column
            }
            val card = details.card
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (!phone) {
                    ArtworkImage(card.poster, 140.dp, Modifier.width(140.dp).aspectRatio(2f / 3f).clip(Shapes.medium)) { TitlePlaceholder(card.title, null) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(card.title, Modifier.semantics { heading() }, style = LocalTypography.current.headline, maxLines = 2)
                    val facts = details.facts.filter { it.isNotBlank() }.take(4)
                    if (facts.isNotEmpty()) Text(facts.joinToString(" · "), color = RefluxColors.contentSecondary, maxLines = 2)
                    if (details.genres.isNotEmpty()) Text(details.genres.take(3).joinToString(" · "), style = LocalTypography.current.caption, color = RefluxColors.contentTertiary, maxLines = 1)
                    (details.tagline ?: details.overview)?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = RefluxColors.contentSecondary, maxLines = if (phone) 4 else 3)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val playId = details.playId
                val playable = playId != null && !card.offline
                if (playable) {
                    Button(details.playLabel, { app.closeQuickView(); app.play(playId!!) }, Modifier.initialFocus(onArrival = true).takesFocus(), icon = Icon.PLAY, style = ButtonStyle.PRIMARY)
                }
                IconButton(
                    if (card.entry.favorite) Icon.HEART_FILLED else Icon.HEART,
                    if (card.entry.favorite) "Remove from favorites" else "Add to favorites",
                    { app.toggleFavorite(card.entry) },
                    if (playable) Modifier else Modifier.initialFocus(onArrival = true).takesFocus(),
                    glass = true,
                    selected = card.entry.favorite,
                )
                if (offline?.offered == true) {
                    IconButton(
                        if (offline?.downloaded == true) Icon.DOWNLOADED else Icon.DOWNLOAD,
                        if (offline?.downloaded == true) "Downloaded" else "Download",
                        { app.closeQuickView(); app.reviewDownload(card.id) },
                        glass = true,
                        selected = offline?.downloaded == true,
                    )
                }
                Button("Details", { app.closeQuickView(); app.navigator.open(Destination.Details(card.id)) }, style = ButtonStyle.QUIET)
            }
        }
    }
}

private class QuickViewLoaded(val details: dev.reflux.ui.state.DetailsModel?)
