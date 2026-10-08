package dev.reflux.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import dev.reflux.core.input.InputSignal
import dev.reflux.core.input.SemanticAction
import dev.reflux.design.AdaptiveLayout
import dev.reflux.design.MaterialRole
import dev.reflux.design.Motion
import dev.reflux.library.PipelineActivity
import dev.reflux.ui.components.Icon
import dev.reflux.ui.components.IconButton
import dev.reflux.ui.components.LocalArtworkLoader
import dev.reflux.ui.components.Pressable
import dev.reflux.ui.screens.BrowseScreen
import dev.reflux.ui.screens.DetailsScreen
import dev.reflux.ui.screens.HomeScreen
import dev.reflux.ui.screens.SearchScreen
import dev.reflux.ui.screens.SourcesPanel
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import dev.reflux.ui.theme.GlassScene
import dev.reflux.ui.theme.LocalLayout
import dev.reflux.ui.theme.LocalTypography
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.Text
import dev.reflux.ui.theme.Typography
import dev.reflux.ui.theme.glass
import dev.reflux.ui.theme.tween

/** The height of the floating navigation bar; screens leave this much room above their first content. */
internal val NavigationBarHeight = 56.dp

/**
 * Provides the adaptive layout and design tokens to [content]. The layout follows the window and the input
 * modality; density changes are animated, which is what turns a controller press into a morph rather than
 * a mode switch (docs/DESIGN.md).
 */
@Composable
fun RefluxTheme(app: AppState, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val target = AdaptiveLayout.profile(maxWidth.value, maxHeight.value, app.platform.television, app.input.modality)
        val posterWidth by animateFloatAsState(target.posterWidthDp, Motion.inputMorph.tween())
        val focusScale by animateFloatAsState(target.focusScale, Motion.inputMorph.tween())
        val minTarget by animateFloatAsState(target.minTargetDp, Motion.inputMorph.tween())
        val layout = target.copy(posterWidthDp = posterWidth, focusScale = focusScale, minTargetDp = minTarget)
        CompositionLocalProvider(
            LocalLayout provides layout,
            LocalTypography provides remember(target.textScale) { Typography(target.textScale) },
            LocalArtworkLoader provides app.platform.artwork,
            content = content,
        )
    }
}

/** Reports pointer activity to the modality tracker without consuming it. */
internal fun Modifier.trackPointer(app: AppState): Modifier = pointerInput(app) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull() ?: continue
            when {
                change.type == PointerType.Touch -> app.onSignal(InputSignal.Touch)
                event.type == PointerEventType.Press -> app.onSignal(InputSignal.PointerClick)
                event.type == PointerEventType.Move -> {
                    val delta = change.positionChange()
                    app.onSignal(InputSignal.PointerMove(delta.x / density, delta.y / density))
                }
            }
        }
    }
}

/** The browsing experience: library screens beneath a floating glass navigation bar. */
@Composable
fun RefluxApp(app: AppState, modifier: Modifier = Modifier) {
    RefluxTheme(app, modifier) {
        val focus = LocalFocusManager.current
        DisposableEffect(app, focus) {
            app.moveFocus = { action ->
                focus.moveFocus(
                    when (action) {
                        SemanticAction.NAVIGATE_UP -> FocusDirection.Up
                        SemanticAction.NAVIGATE_DOWN -> FocusDirection.Down
                        SemanticAction.NAVIGATE_LEFT -> FocusDirection.Left
                        else -> FocusDirection.Right
                    },
                )
            }
            app.clearFocus = { focus.clearFocus() }
            onDispose {
                app.moveFocus = null
                app.clearFocus = null
            }
        }
        val screens = rememberSaveableStateHolder()
        GlassScene(
            Modifier.fillMaxSize().background(RefluxColors.background).trackPointer(app),
            overlay = { Chrome(app) },
        ) {
            AnimatedContent(
                targetState = app.navigator.current,
                transitionSpec = {
                    (fadeIn(Motion.navigation.tween()) + slideInVertically(Motion.navigation.tween()) { it / 40 }) togetherWith
                        fadeOut(Motion.focusChange.tween())
                },
            ) { destination ->
                screens.SaveableStateProvider(destination.toString()) {
                    when (destination) {
                        Destination.Home -> HomeScreen(app)
                        Destination.Movies -> BrowseScreen(app, "Movies", query = null, shows = false)
                        Destination.Shows -> BrowseScreen(app, "TV Shows", query = null, shows = true)
                        Destination.Search -> SearchScreen(app)
                        is Destination.Listing -> BrowseScreen(app, destination.title, destination.query, shows = false)
                        is Destination.Details -> DetailsScreen(app, destination.itemId)
                    }
                }
            }
        }
    }
}

/** Everything that floats above the screens: navigation, the sources panel, and transient notices. */
@Composable
private fun Chrome(app: AppState) {
    val layout = LocalLayout.current
    Box(Modifier.fillMaxSize().padding(horizontal = layout.safeMarginDp.dp)) {
        NavigationBar(app, Modifier.align(Alignment.TopCenter).padding(top = 14.dp))
        AnimatedVisibility(
            visible = app.sourcesOpen,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 14.dp + NavigationBarHeight + 10.dp),
            enter = fadeIn(Motion.controlsShow.tween()) + slideInVertically(Motion.controlsShow.tween()) { -it / 12 },
            exit = fadeOut(Motion.controlsHide.tween()),
        ) {
            SourcesPanel(app)
        }
        AnimatedContent(
            targetState = app.notice,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            transitionSpec = {
                (fadeIn(Motion.controlsShow.tween()) + slideInVertically(Motion.controlsShow.tween()) { it / 3 }) togetherWith
                    (fadeOut(Motion.controlsHide.tween()) + slideOutVertically(Motion.controlsHide.tween()) { it / 3 })
            },
        ) { message ->
            if (message != null) {
                Box(Modifier.glass(MaterialRole.FLOATING, CircleShape).padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(message, style = LocalTypography.current.bodyStrong)
                }
            }
        }
    }
}

@Composable
private fun NavigationBar(app: AppState, modifier: Modifier = Modifier) {
    val navigator = app.navigator
    val activity by app.activity.collectAsState()
    Row(
        modifier.fillMaxWidth().height(NavigationBarHeight).glass(MaterialRole.FLOATING, CircleShape).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigator.current !is Destination.Section) {
            IconButton(Icon.BACK, "Back", { navigator.back() })
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text("Reflux", Modifier.padding(start = 4.dp, end = 20.dp), style = LocalTypography.current.title)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            Tab("Home", navigator.section == Destination.Home) { navigator.show(Destination.Home) }
            Tab("Movies", navigator.section == Destination.Movies) { navigator.show(Destination.Movies) }
            Tab("Shows", navigator.section == Destination.Shows) { navigator.show(Destination.Shows) }
        }
        Spacer(Modifier.weight(1f))
        ActivityIndicator(activity)
        IconButton(Icon.SEARCH, "Search", { navigator.show(Destination.Search) }, tint = tabColor(navigator.section == Destination.Search))
        IconButton(Icon.FOLDER, "Media sources", { app.sourcesOpen = !app.sourcesOpen }, tint = tabColor(app.sourcesOpen))
    }
}

private fun tabColor(selected: Boolean): Color = if (selected) RefluxColors.textPrimary else RefluxColors.textSecondary

@Composable
private fun Tab(label: String, selected: Boolean, onClick: () -> Unit) {
    Pressable(onClick, shape = CircleShape, lift = 0f) { active ->
        Box(
            Modifier.background(Color.White.copy(alpha = if (selected) 0.14f else if (active) 0.07f else 0f))
                .height(36.dp).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = LocalTypography.current.bodyStrong, color = tabColor(selected || active), maxLines = 1)
        }
    }
}

/** A quiet sign that the library is being updated; it never blocks anything. */
@Composable
private fun ActivityIndicator(activity: PipelineActivity) {
    AnimatedContent(
        targetState = activityLabel(activity),
        transitionSpec = { fadeIn(Motion.controlsShow.tween()) togetherWith fadeOut(Motion.controlsHide.tween()) },
    ) { label ->
        if (label != null) {
            Row(Modifier.padding(end = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(RefluxColors.accent, CircleShape))
                Text(label, style = LocalTypography.current.caption, color = RefluxColors.textSecondary, maxLines = 1)
            }
        }
    }
}

internal fun activityLabel(activity: PipelineActivity): String? = when (activity) {
    PipelineActivity.Idle -> null
    is PipelineActivity.Scanning -> "Scanning ${activity.sourceName}"
    PipelineActivity.Probing -> "Reading media details"
    PipelineActivity.FetchingMetadata -> "Finding titles and details"
    PipelineActivity.FetchingArtwork -> "Fetching artwork"
}
