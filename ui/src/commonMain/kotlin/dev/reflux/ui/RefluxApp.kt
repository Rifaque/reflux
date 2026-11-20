@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package dev.reflux.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import dev.reflux.design.ColorRoles
import dev.reflux.design.MaterialEnvironment
import dev.reflux.design.MaterialTiers
import dev.reflux.design.Radius
import dev.reflux.design.StateLayers
import dev.reflux.ui.theme.AppearancePolicy
import dev.reflux.ui.theme.LocalBackdropBlur
import dev.reflux.ui.theme.LocalMaterialBackdrop
import dev.reflux.ui.theme.LocalMaterialTier
import dev.reflux.ui.theme.LocalMotion
import dev.reflux.ui.theme.MaterialBackdrop
import dev.reflux.ui.theme.Shapes
import dev.reflux.ui.theme.States
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.core.input.InputMapping
import dev.reflux.core.input.InputSignal
import dev.reflux.core.input.SemanticAction
import dev.reflux.design.AdaptiveLayout
import dev.reflux.design.FormFactor
import dev.reflux.design.MaterialRole
import dev.reflux.design.Motion
import dev.reflux.library.PipelineActivity
import dev.reflux.ui.components.ButtonPrompts
import dev.reflux.ui.components.FocusEntry
import dev.reflux.ui.components.FocusScrollInsets
import dev.reflux.ui.components.FocusedControl
import dev.reflux.ui.components.Icon
import dev.reflux.ui.components.IconButton
import dev.reflux.ui.components.LimitedTextSize
import dev.reflux.ui.components.LocalAccessibility
import dev.reflux.ui.components.NAVIGATION_TEXT_LIMIT
import dev.reflux.ui.components.Traversal
import dev.reflux.ui.components.announces
import dev.reflux.ui.components.inert
import dev.reflux.ui.components.pane
import dev.reflux.ui.components.traversal
import dev.reflux.ui.components.LocalArtworkLoader
import dev.reflux.ui.components.LocalFocusEntry
import dev.reflux.ui.components.LocalFocusScreen
import dev.reflux.ui.components.LocalFocusedControl
import dev.reflux.ui.components.absorbsPointer
import dev.reflux.ui.screens.SettingsScreen
import dev.reflux.ui.screens.ProfilesScreen
import dev.reflux.ui.screens.ProfileGateOverlay
import dev.reflux.ui.screens.ProfileMark
import dev.reflux.ui.screens.BrowseScreen
import dev.reflux.ui.screens.DetailsScreen
import dev.reflux.ui.screens.FolderPickerPanel
import dev.reflux.ui.screens.HomeScreen
import dev.reflux.ui.screens.IdentifyScreen
import dev.reflux.ui.screens.DownloadsScreen
import dev.reflux.ui.state.offlineState
import dev.reflux.library.visibleDownloads
import dev.reflux.ui.screens.OfflinePanel
import dev.reflux.ui.screens.SearchScreen
import dev.reflux.ui.screens.AboutScreen
import dev.reflux.ui.screens.AddSourceScreen
import dev.reflux.ui.screens.NoticeScreen
import dev.reflux.ui.screens.NoticesScreen
import dev.reflux.ui.screens.CreditsScreen
import dev.reflux.ui.screens.ConnectScreen
import dev.reflux.ui.screens.SourcesScreen
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import dev.reflux.ui.state.PrimaryDestinations
import dev.reflux.ui.theme.GlassScene
import dev.reflux.ui.theme.LocalLayout
import dev.reflux.ui.theme.LocalTypography
import dev.reflux.ui.theme.MotionPolicy
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.Text
import dev.reflux.ui.theme.Typography
import dev.reflux.ui.theme.glass
import dev.reflux.ui.theme.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.draw.clipToBounds
import dev.reflux.ui.components.FocusedBounds
import dev.reflux.ui.components.RefluxLockup
import dev.reflux.ui.components.RefluxMark
import dev.reflux.ui.components.SelectionTrack
import dev.reflux.ui.components.focusedBounds
import dev.reflux.ui.components.lockupMarkWidth
import dev.reflux.ui.components.selectionMark
import dev.reflux.ui.components.selectionPlace
import dev.reflux.library.Profile
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import dev.reflux.design.NavigationStyle
import dev.reflux.design.MiniPlayerDock
import dev.reflux.ui.player.MiniPlayerDock
import dev.reflux.ui.state.PlaybackSession
import dev.reflux.ui.components.Pressable
import dev.reflux.ui.screens.SourceLibraryScreen

/** How many frames a television waits for a new screen to have something to focus. */
private const val FOCUS_PATIENCE_FRAMES = 30

/** The height of the floating navigation bar. */
internal val NavigationBarHeight = 56.dp

/**
 * How much of a screen the navigation occupies. Screens start their content below [top] and let it scroll out
 * from under [bottom]; the whole page area starts [start] from the left, where a rail or a sidebar runs down the
 * side (docs/NAVIGATION.md, section 3).
 *
 * On a phone held upright the navigation is at the bottom, within reach of a thumb, and the top only carries what a
 * page needs there; everywhere else it runs down the left side, and the top of the page carries the way back.
 */
internal data class ChromeInsets(
    val top: Dp,
    val bottom: Dp,
    val start: Dp = 0.dp,
    /**
     * How far a page that opens on artwork indents its own content: where its artwork runs under the navigation
     * (MaterialTier.artworkUnderNavigation), the page is the whole window and its content starts after [start].
     * Zero on every other page, which is placed beside the navigation instead.
     */
    val contentStart: Dp = 0.dp,
)

/**
 * The system's bars and cutouts around the window, which Reflux draws beneath (edge to edge) and keeps its
 * controls clear of. A shell without such bars (a desktop window, a television) passes [None].
 */
data class SystemBars(val top: Dp = 0.dp, val bottom: Dp = 0.dp, val start: Dp = 0.dp, val end: Dp = 0.dp) {
    companion object {
        val None = SystemBars()
    }
}

internal val LocalSystemBars = staticCompositionLocalOf { SystemBars.None }

/**
 * Whether the page on screen has moved from its top, as its main list reports it. Where pages move a row
 * at a time (a television), what has gone up under the navigation is faded out rather than left half showing.
 */
internal class PageScroll {
    var scrolled by mutableStateOf(false)

    /** The page that said so last; one that is on its way out does not speak for the one that replaced it. */
    internal var page: Any? = null
}

internal val LocalPageScroll = staticCompositionLocalOf<PageScroll?> { null }

/** Reports whether [scrolled] to the page's chrome while the caller is on screen. */
@Composable
internal fun ReportPageScroll(scrolled: Boolean) {
    val page = LocalPageScroll.current ?: return
    val self = remember { Any() }
    DisposableEffect(page, scrolled) {
        page.page = self
        page.scrolled = scrolled
        onDispose { if (page.page === self) page.scrolled = false }
    }
}

internal val LocalChromeInsets = staticCompositionLocalOf { ChromeInsets(top = PageBarHeight, bottom = 32.dp) }

/** The row across the top of a page beside a rail: the way back, and a sign of the library being updated. */
internal val PageBarHeight = 64.dp

/** How far below the top bar its scrim fades out, once a page has scrolled. */
internal val TopBarScrimFade = 24.dp

private fun chromeInsets(navigation: NavigationStyle, onSection: Boolean, prompts: Boolean, bars: SystemBars, artworkPage: Boolean, dock: MiniPlayerDock? = null): ChromeInsets {
    val insets = when (navigation) {
        // A page reached from somewhere has a way back in its top corner; with a controller in hand, its
        // button prompts sit above the sections, and the page ends clear of them too.
        NavigationStyle.BOTTOM_BAR -> ChromeInsets(top = if (onSection) 20.dp else 72.dp, bottom = 10.dp + NavigationBarHeight + 28.dp + if (prompts) 52.dp else 0.dp)
        // A remote has its own Back: a television's pages start close to the top of its safe area.
        NavigationStyle.TV_RAIL -> ChromeInsets(top = 36.dp, bottom = 32.dp, start = navigation.widthDp.dp)
        else -> ChromeInsets(top = PageBarHeight, bottom = 32.dp, start = navigation.widthDp.dp)
    }
    // Edge to edge: the page is drawn under the system's bars, and its content starts clear of them.
    // A page ends clear of the mini-player too, while something plays there: its last row scrolls out from under it.
    val docked = dock?.let { (it.heightDp + MiniPlayerDock.GAP_DP).dp } ?: 0.dp
    val withBars = insets.copy(top = insets.top + bars.top, bottom = insets.bottom + bars.bottom + docked, start = insets.start + bars.start)
    return if (artworkPage && navigation.vertical) withBars.copy(contentStart = withBars.start) else withBars
}

/**
 * Provides the adaptive layout and design tokens to [content]. The layout follows the window and the input
 * modality; density changes are animated, which is what turns a controller press into a morph rather than
 * a mode switch (docs/DESIGN.md).
 */
@Composable
fun RefluxTheme(app: AppState, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val target = AdaptiveLayout.profile(maxWidth.value, maxHeight.value, app.platform.television, app.input.modality)
        val accessibility = app.platform.accessibility
        val roles = if (accessibility.highContrast) ColorRoles.RefluxHighContrast else ColorRoles.Reflux
        // Internal, never a setting: what this device can draw, and what the viewer's system asks for.
        val tier = MaterialTiers.select(
            MaterialEnvironment(
                formFactor = target.formFactor,
                blursBackdrop = LocalBackdropBlur.current,
                reduceTransparency = accessibility.reduceTransparency,
                highContrast = accessibility.highContrast,
                constrained = app.platform.constrainedGraphics,
            ),
        )
        SideEffect {
            MotionPolicy.reduced = accessibility.reduceMotion
            AppearancePolicy.roles = roles
        }
        val density = LocalDensity.current
        val posterWidth by animateFloatAsState(target.posterWidthDp, Motion.inputMorph.tween())
        val focusScale by animateFloatAsState(target.focusScale, Motion.inputMorph.tween())
        val minTarget by animateFloatAsState(target.minTargetDp, Motion.inputMorph.tween())
        val layout = target.copy(posterWidthDp = posterWidth, focusScale = focusScale, minTargetDp = minTarget)
        CompositionLocalProvider(
            LocalLayout provides layout,
            LocalAccessibility provides accessibility,
            LocalDensity provides remember(density, accessibility.textScale) { Density(density.density, density.fontScale * accessibility.textScale) },
            LocalTypography provides remember(target.textScale, target.formFactor) { Typography(target.textScale, target.formFactor) },
            LocalArtworkLoader provides app.platform.artwork,
            LocalMaterialTier provides tier,
            LocalMotion provides Motion.profile(target.formFactor),
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

/**
 * The browsing experience: library screens beside, or above, the global navigation (docs/NAVIGATION.md).
 *
 * @param bars the system's bars around the window. Reflux draws under them (edge to edge) and keeps every control
 * clear of them; the shell does not pad the application for them.
 */
@Composable
fun RefluxApp(
    app: AppState,
    modifier: Modifier = Modifier,
    bars: SystemBars = SystemBars.None,
    /**
     * The room the on-screen keyboard takes at the bottom of the window (Android passes its inset). Pages and what
     * floats over them move above it; the layout is still chosen from the whole window, so a tablet stays a tablet
     * while the viewer types (VERIFICATION.md, 2026-11-16, "Search on the Pad 7").
     */
    keyboard: Modifier = Modifier,
) {
    RefluxTheme(app, modifier) {
        val focus = LocalFocusManager.current
        val entry = remember { FocusEntry() }
        val focused = remember { FocusedControl() }
        val rail = remember { RailFocus() }
        val sideNavigation = LocalLayout.current.navigation.vertical
        val waiting = androidx.compose.runtime.rememberCoroutineScope()
        DisposableEffect(app, focus, sideNavigation) {
            app.moveFocus = { action ->
                val direction = when (action) {
                    SemanticAction.NAVIGATE_UP -> FocusDirection.Up
                    SemanticAction.NAVIGATE_DOWN -> FocusDirection.Down
                    SemanticAction.NAVIGATE_LEFT -> FocusDirection.Left
                    else -> FocusDirection.Right
                }
                // From a rail, Right goes back into the page where focus was in it (each page restores its last focused item).
                val back = action == SemanticAction.NAVIGATE_RIGHT && sideNavigation && entry.inChrome
                // Nothing has focus and the page has nothing to start on yet (it is still being read: a large library
                // takes a moment on its way back to Home). The toolkit's own search would land on the navigation beside
                // it, which is what made Right from Home "sometimes not enter the content" (B-008). The press waits for
                // the page's start instead, for a moment, and only then falls back to the toolkit.
                if (!entry.hasFocus && !entry.inChrome) {
                    if (!entry.start()) waiting.launch {
                        repeat(FOCUS_PATIENCE_FRAMES) {
                            withFrameNanos { }
                            if (entry.hasFocus || entry.start()) return@launch
                        }
                        focus.moveFocus(direction)
                    }
                    true
                } else entry.enter() || (back && (rail.enterPage() || entry.start())) || entry.leaveChrome(action) || if (action == SemanticAction.NAVIGATE_LEFT && sideNavigation && !entry.inChrome) {
                    // Left is toward the rail. The toolkit's search also takes what is only partly to the left (a
                    // button above, in a centred column); whatever is not strictly to the left of where focus was
                    // gives way to the rail, on the destination the viewer is in.
                    val before = rail.focusedBounds
                    rail.leavingPage(focused)
                    val moved = focus.moveFocus(FocusDirection.Left)
                    val after = rail.focusedBounds
                    if (!entry.inChrome && (!moved || (before != null && after != null && after.right > before.left + 1f))) rail.focusCurrent() || moved else moved
                } else {
                    focus.moveFocus(direction)
                }
            }
            app.clearFocus = { focus.clearFocus() }
            app.activateFocused = focused::activate
            // F-P3: the focused card's Quick View; focus comes back to that card when it closes.
            app.focusedContext = {
                val card = focused.requester
                focused.context().also { opened -> if (opened && card != null) entry.afterPanel = card }
            }
            // Back on a television's destination goes to the navigation first, landing on where the viewer is.
            app.focusNavigation = { !entry.inChrome && rail.leavingPage(focused).let { rail.focusCurrent() } }
            // Y (F-P2): into the navigation, landing on where the viewer is; and from it back to exactly the item that was
            // left, which keeps its row's scroll and the page's state because the page never changed. If that item is gone
            // (another destination was opened from the navigation), the page's own start.
            app.toggleNavigation = if (!sideNavigation) null else {
                {
                    if (entry.inChrome) rail.enterPage() || entry.start()
                    else rail.leavingPage(focused).let { rail.focusCurrent() }
                }
            }
            onDispose {
                app.moveFocus = null
                app.clearFocus = null
                app.activateFocused = null
                app.focusedContext = null
                app.focusNavigation = null
                app.toggleNavigation = null
            }
        }
        val screens = rememberSaveableStateHolder()
        entry.screen = app.navigator.current
        val layout = LocalLayout.current
        val tier = LocalMaterialTier.current
        val motion = LocalMotion.current
        // A page that opens on artwork lets it run under the navigation where the material can carry it (M4); on
        // BASIC the page stays beside the navigation, which is the cleaner layout there.
        val artworkPage = tier.artworkUnderNavigation && opensOnArtwork(app.navigator.current)
        val dock = layout.dock?.takeIf { app.docked != null }
        val insets = chromeInsets(layout.navigation, app.navigator.current is Destination.Section, app.namesButtons, bars, artworkPage, dock)
        // A television is only ever operated through focus, so something always has it: when a screen
        // appears, or what had focus goes away, it lands where the user left off or on the main action.
        if (layout.formFactor == FormFactor.TV) {
            // A screen that has just appeared starts on its own main action, not on whatever the toolkit
            // found first (the navigation beside it).
            // The same when a page gains a main action it did not have (a library that just appeared), or a
            // panel over it closes, unless the user has since put focus somewhere themselves: in the page, or on
            // the navigation. Once the screen has started, focus on the navigation is the viewer's doing (Back on
            // a destination puts it there); a page that then gains a new first line (a removal finishing a moment
            // later) must not pull it back. On arrival it is not: that is where the toolkit put it.
            val covered = app.offlineOpen || app.folderPicker != null || app.profileGate != null || app.quickView != null
            var started by remember { mutableStateOf<Destination?>(null) }
            LaunchedEffect(app.navigator.current, entry.starts, covered) {
                if (covered) return@LaunchedEffect
                val arrived = started != app.navigator.current
                repeat(FOCUS_PATIENCE_FRAMES) {
                    withFrameNanos { }
                    if (!arrived && entry.hasFocus && !entry.inPanel) return@LaunchedEffect
                    if (entry.start()) {
                        started = app.navigator.current
                        return@LaunchedEffect
                    }
                }
            }
            val adrift = !entry.hasFocus && !covered
            LaunchedEffect(adrift, app.revision) {
                if (!adrift) return@LaunchedEffect
                repeat(FOCUS_PATIENCE_FRAMES) {
                    withFrameNanos { }
                    if (entry.hasFocus || entry.enter()) return@LaunchedEffect
                }
            }
        } else if (layout.showsFocus) {
            // A keyboard or a controller at a desk: a panel that closes hands focus back to what opened it at
            // once, not on the next arrow, so a key pressed next acts where the user was.
            val covered = app.offlineOpen || app.folderPicker != null || app.profileGate != null || app.quickView != null
            LaunchedEffect(covered) {
                if (covered) return@LaunchedEffect
                repeat(FOCUS_PATIENCE_FRAMES) {
                    withFrameNanos { }
                    if (entry.afterPanel == null || entry.start()) return@LaunchedEffect
                }
            }
        }
        // What is in front is all a screen reader finds: the player, or a panel that holds focus until it closes.
        val panelOpen = app.offlineOpen || app.folderPicker != null || app.quickView != null
        // What plays in the mini-player is beside the page, not in front of it: the page stays the viewer's.
        val behind = app.watching != null || panelOpen || app.profileGate != null
        val page = remember { PageScroll() }
        val materialBackdrop = remember { MaterialBackdrop() }
        // Every text field reports itself and asks for the Reflux keyboard here (B-025's contract): no screen does.
        val textEntry = remember(app) {
            object : dev.reflux.ui.components.TextEntry {
                override fun focused(field: Any, focused: Boolean) = app.editing.report(field, focused)
                override fun open(request: dev.reflux.ui.state.KeyboardRequest) = app.openKeyboard(request)
                override fun close(field: Any) = app.closeKeyboard(field)
                override fun cursorOf(field: Any): Int? = app.keyboard?.takeIf { it.request.field === field }?.cursor
            }
        }
        // The toolkit's own haptics (text selection's handles, long presses) go through Reflux's gateway like everything
        // else: nothing vibrates before the viewer turns interface haptics on (F-001).
        val toolkitHaptics = remember(app) {
            object : androidx.compose.ui.hapticfeedback.HapticFeedback {
                override fun performHapticFeedback(hapticFeedbackType: androidx.compose.ui.hapticfeedback.HapticFeedbackType) =
                    app.haptics.touch(dev.reflux.ui.state.HapticEvent.CONFIRM)
            }
        }
        // One instance for the whole session: a static local given a new value would recompose every page under it.
        val quickView: (dev.reflux.core.model.MediaId) -> Unit = remember(app, focused, entry) {
            { id ->
                // Opened by a pointer or a finger on a card that may not have focus: its place is where focus comes back to.
                focused.requester?.let { entry.afterPanel = it }
                app.openQuickView(id)
            }
        }
        CompositionLocalProvider(
            LocalFocusEntry provides entry, LocalFocusedControl provides focused, LocalChromeInsets provides insets, LocalPageScroll provides page,
            dev.reflux.ui.components.LocalQuickView provides quickView,
            LocalSystemBars provides bars, LocalMaterialBackdrop provides materialBackdrop, dev.reflux.ui.components.LocalTextEntry provides textEntry,
            androidx.compose.ui.platform.LocalHapticFeedback provides toolkitHaptics,
        ) {
            GlassScene(
                Modifier.fillMaxSize().background(RefluxColors.background).trackPointer(app).onFocusChanged { entry.hasFocus = it.hasFocus },
                overlay = { Chrome(app, page, rail, navigationInert = behind, noticeInert = app.watching != null, keyboard = keyboard) },
            ) {
                // Pages start beside a navigation that runs down the side, except a page that opens on artwork where
                // the material lets it run beneath; focus that scrolls a page stops short of the chrome.
                Box(Modifier.fillMaxSize().then(keyboard).padding(end = bars.end)) {
                    FocusScrollInsets(start = insets.top, end = insets.bottom + 8.dp) {
                        AnimatedContent(
                            targetState = app.navigator.current,
                            transitionSpec = {
                                // A page arrives (travelling a little, except at ten feet) and the one it replaces fades.
                                val travel = motion.pageTravel
                                val enter = fadeIn(motion.pageEnter.tween()) + if (travel > 0f) slideInVertically(motion.pageEnter.tween()) { (it * travel).toInt() } else EnterTransition.None
                                // The page that leaves is not clipped: one that opens on artwork reaches under the navigation.
                                (enter togetherWith fadeOut(Motion.focusChange.tween())) using SizeTransform(clip = false)
                            },
                        ) { destination ->
                            // A page on its way out stays on screen while it fades, but it is no longer there for
                            // assistive technology, which would otherwise find two pages at once.
                            val leaving = transition.targetState == EnterExitState.PostExit
                            val named = accessibleTitle(destination)
                            // Each page keeps its own place while it leaves: one that opens on artwork reaches under the
                            // navigation, the others start beside it.
                            val own = chromeInsets(layout.navigation, destination is Destination.Section, app.namesButtons, bars, tier.artworkUnderNavigation && opensOnArtwork(destination), dock)
                            Box(
                                Modifier.fillMaxSize().padding(start = own.start - own.contentStart).traversal(Traversal.PAGE).inert(leaving || behind)
                                    .then(if (named != null) Modifier.pane(named) else Modifier)
                                    // Coming back into the page from the navigation lands where focus was in it (RailFocus):
                                    // the page is a focus group, and one that can say where focus is inside it. A page on
                                    // its way out is not asked.
                                    .then(if (leaving) Modifier.focusGroup() else Modifier.focusedBounds(rail.bounds)),
                            ) {
                                screens.SaveableStateProvider(destination.toString()) {
                                    CompositionLocalProvider(LocalFocusScreen provides destination, LocalChromeInsets provides own) {
                                        // While Reflux asks who is watching, the page is not there at all: not to a
                                        // finger, a pointer, a keyboard's Tab, or assistive technology.
                                        if (app.profileGate == null) Screen(app, destination)
                                    }
                                }
                            }
                        }
                    }
                    // What has scrolled up under the top bar is faded out rather than left showing through it: on a
                    // television, where pages move a row at a time; and beside a navigation down the side (tablet,
                    // desktop) on a page with its way back floating in that bar, where rows otherwise passed behind
                    // and beside the Back button (a show's season tabs on a tablet). The band is the chrome's own top
                    // inset, status bar included. A phone's corner Back is left as it was.
                    val scrimsTopBar = layout.formFactor == FormFactor.TV ||
                        (layout.navigation.vertical && app.navigator.current !is Destination.Section)
                    if (scrimsTopBar) {
                        val fade by animateFloatAsState(if (page.scrolled) 1f else 0f, Motion.navigation.tween())
                        val background = RefluxColors.background
                        // Opaque enough through the whole bar, its Back button included (the bar ends at the band's
                        // inset), then a soft fade below it: the bar's controls never sit over bright rows, and the
                        // page meets the bar without an edge. It is there only once the page has moved.
                        val band = insets.top + TopBarScrimFade
                        val barEnd = insets.top / band
                        Box(
                            Modifier.fillMaxWidth().height(band).graphicsLayer { alpha = fade }
                                .background(
                                    Brush.verticalGradient(
                                        0f to background,
                                        barEnd * 0.6f to background.copy(alpha = 0.97f),
                                        barEnd to background.copy(alpha = 0.9f),
                                        1f to Color.Transparent,
                                    ),
                                ),
                        )
                    }
                    // Edge to edge, the system's status bar sits over the page: a soft protection keeps its icons
                    // readable over bright artwork and posters scrolling beneath it, without a band of its own.
                    if (bars.top > 0.dp) {
                        Box(Modifier.fillMaxWidth().height(bars.top + 12.dp).background(Brush.verticalGradient(0f to RefluxColors.scrim.copy(alpha = 0.55f), 1f to Color.Transparent)))
                    }
                }
            }
        }
    }
}

/** Whether a destination opens on its artwork (a hero or a header), and so may let it run under the navigation. */
internal fun opensOnArtwork(destination: Destination): Boolean = destination == Destination.Home || destination is Destination.Details

@Composable
private fun Screen(app: AppState, destination: Destination) {
    when (destination) {
        Destination.Home -> HomeScreen(app)
        Destination.Movies -> BrowseScreen(app, "Movies", query = null, shows = false)
        Destination.Shows -> BrowseScreen(app, "TV Shows", query = null, shows = true)
        Destination.Search -> SearchScreen(app)
        is Destination.Listing -> BrowseScreen(app, destination.title, destination.query, shows = false)
        is Destination.Details -> DetailsScreen(app, destination.itemId, destination.scope)
        is Destination.Identify -> IdentifyScreen(app, destination.itemId)
        Destination.Downloads -> DownloadsScreen(app)
        Destination.Sources -> SourcesScreen(app)
        is Destination.SourceLibrary -> SourceLibraryScreen(app, destination.sourceId, destination.section)
        Destination.AddSource -> AddSourceScreen(app)
        is Destination.Connect -> ConnectScreen(app, destination)
        Destination.Settings -> SettingsScreen(app)
        Destination.Profiles -> ProfilesScreen(app)
        Destination.About -> AboutScreen(app)
        Destination.Notices -> NoticesScreen(app)
        Destination.Credits -> CreditsScreen(app)
        is Destination.Notice -> NoticeScreen(app, destination.name)
    }
}

/** Everything that floats above the screens: navigation, the offline sheet, button prompts, and notices. */
@Composable
private fun Chrome(app: AppState, page: PageScroll, rail: RailFocus, navigationInert: Boolean, noticeInert: Boolean, keyboard: Modifier) {
    val layout = LocalLayout.current
    val bars = LocalSystemBars.current
    val bottomBar = layout.navigation == NavigationStyle.BOTTOM_BAR
    // Controls stay clear of the system's bars; what floats is placed inside them.
    val margin = Modifier.fillMaxSize().padding(start = bars.start, end = bars.end, top = bars.top, bottom = bars.bottom).padding(horizontal = layout.safeMarginDp.dp)
    // The navigation's words stop growing before they stop fitting; the destinations always say all of them to a screen reader.
    // A navigation down the side keeps the window's height while the on-screen keyboard is up, which rises over its foot;
    // everything else here moves above the keyboard, as the page does.
    Box(Modifier.fillMaxSize()) {
        if (app.profileGate == null && !bottomBar) LimitedTextSize(NAVIGATION_TEXT_LIMIT) {
            Box(Modifier.fillMaxSize().padding(start = bars.start, end = bars.end).inert(navigationInert)) {
                NavigationRail(app, layout.navigation, rail, Modifier.align(Alignment.TopStart).padding(top = bars.top, bottom = bars.bottom).traversal(Traversal.BEFORE_PAGE))
                PageBar(app, Modifier.align(Alignment.TopStart).padding(start = layout.navigation.widthDp.dp, top = bars.top).traversal(Traversal.BEFORE_PAGE))
            }
        }
    Box(Modifier.fillMaxSize().then(keyboard)) {
        if (app.profileGate == null && bottomBar) LimitedTextSize(NAVIGATION_TEXT_LIMIT) {
            Box(margin.inert(navigationInert)) { CompactNavigation(app) }
        }
        // What is playing, beside the page while the viewer browses (docs/NAVIGATION.md, section 8): part of the
        // navigation's own layer, above the phone's bar or in the page's bottom corner beside a rail or a sidebar.
        layout.dock?.takeIf { app.profileGate == null }?.let { dock ->
            var shown by remember { mutableStateOf<PlaybackSession?>(null) }
            app.docked?.let { shown = it }
            val place = if (bottomBar) {
                // Above the destinations, and above what a controller's buttons are said to do there.
                margin.padding(bottom = 10.dp + NavigationBarHeight + MiniPlayerDock.GAP_DP.dp + if (app.namesButtons) 52.dp else 0.dp)
            } else {
                Modifier.fillMaxSize().padding(end = bars.end + DockMargin, bottom = bars.bottom + DockMargin)
            }
            Box(place.inert(navigationInert), contentAlignment = if (bottomBar) Alignment.BottomCenter else Alignment.BottomEnd) {
                // It arrives and leaves in place: the picture travels to and from it, and words that slid would
                // move the box the picture is heading for.
                AnimatedVisibility(
                    app.docked != null && app.folderPicker == null && !(bottomBar && app.textInputActive && app.systemKeyboardShown),
                    Modifier.traversal(Traversal.AFTER_PAGE),
                    enter = fadeIn(Motion.playerDock.tween()),
                    exit = fadeOut(Motion.controlsHide.tween()),
                ) {
                    shown?.let { MiniPlayerDock(app, it, dock) }
                }
            }
        }
        // A click outside the open panel closes it and goes no further.
        if (app.offlineOpen) {
            Box(Modifier.fillMaxSize().pointerInput(app) { detectTapGestures { app.offlineOpen = false } })
        }
        // Media Quick View (F-P3): over the page, which steps back; a click outside closes it.
        AnimatedVisibility(app.quickView != null, enter = fadeIn(Motion.controlsShow.tween()), exit = fadeOut(Motion.controlsHide.tween())) {
            var shown by remember { mutableStateOf(app.quickView) }
            app.quickView?.let { shown = it }
            shown?.let { itemId -> dev.reflux.ui.screens.QuickViewOverlay(app, itemId) }
        }
        // Choosing a folder takes the whole screen's attention: the page behind it steps back.
        AnimatedVisibility(app.folderPicker != null, enter = fadeIn(Motion.controlsShow.tween()), exit = fadeOut(Motion.controlsHide.tween())) {
            var shown by remember { mutableStateOf(app.folderPicker) }
            app.folderPicker?.let { shown = it }
            shown?.let { picker ->
                Box(
                    Modifier.fillMaxSize().background(RefluxColors.background.copy(alpha = 0.72f)).pointerInput(picker) { detectTapGestures { picker.close() } },
                    contentAlignment = Alignment.Center,
                ) {
                    FolderPickerPanel(picker)
                }
            }
        }
        Box(margin) {
            // On a phone the sheet rises from the bottom, under the thumb; elsewhere it hangs from the top of the page.
            val sheetTravel = LocalMotion.current.sheetTravel
            AnimatedVisibility(
                visible = app.offlineOpen,
                modifier = if (bottomBar) {
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp + NavigationBarHeight + 12.dp)
                } else {
                    Modifier.align(Alignment.TopEnd).padding(top = PageBarHeight + 4.dp)
                },
                enter = fadeIn(Motion.sheetEnter.tween()) + slideInVertically(Motion.sheetEnter.tween()) { (it * sheetTravel).toInt() * (if (bottomBar) 1 else -1) },
                exit = fadeOut(Motion.sheetExit.tween()),
            ) {
                OfflinePanel(app)
            }
            // A notice never covers an open sheet: on a phone, where the sheet rises from the bottom, it is said at the top instead.
            val aboveSheet = bottomBar && app.offlineOpen
            AnimatedContent(
                targetState = app.notice,
                modifier = (
                    if (aboveSheet) {
                        Modifier.align(Alignment.TopCenter).padding(top = 12.dp + 44.dp + 12.dp)
                    } else {
                        // Never over the mini-player: above it on a phone, beside it where it is in the page's corner.
                        val docked = layout.dock?.takeIf { app.docked != null }
                        val aboveDock = if (bottomBar && docked != null) (docked.heightDp + MiniPlayerDock.GAP_DP).dp else 0.dp
                        val besideDock = if (!bottomBar && docked != null) (docked.widthDp + MiniPlayerDock.GAP_DP).dp else 0.dp
                        Modifier.align(Alignment.BottomCenter).padding(bottom = (if (bottomBar) 10.dp + NavigationBarHeight + 14.dp else 32.dp) + aboveDock)
                            .padding(start = layout.navigation.widthDp.dp, end = besideDock)
                    }
                ).announces(app.notice.takeUnless { noticeInert }),
                transitionSpec = {
                    (fadeIn(Motion.controlsShow.tween()) + slideInVertically(Motion.controlsShow.tween()) { it / 3 }) togetherWith
                        (fadeOut(Motion.controlsHide.tween()) + slideOutVertically(Motion.controlsHide.tween()) { it / 3 })
                },
            ) { message ->
                if (message != null) {
                    Box(
                        Modifier.widthIn(max = 640.dp).glass(MaterialRole.MODAL, Shapes.xlarge).absorbsPointer()
                            .padding(horizontal = 22.dp, vertical = 13.dp),
                    ) {
                        Text(message, style = LocalTypography.current.bodyStrong, align = TextAlign.Center)
                    }
                }
            }
        }
        // Who is watching: over everything, which it makes inert (docs/WATCH_STATE.md, "Profiles").
        // It is there at once when Reflux opens on it; asked for later it arrives as a page does, and it leaves the
        // way one leaves, so that choosing who is watching is one movement into their Home.
        var asked by remember { mutableStateOf(app.profileGate) }
        app.profileGate?.let { asked = it }
        AnimatedVisibility(app.profileGate != null, enter = fadeIn(LocalMotion.current.pageEnter.tween()), exit = fadeOut(Motion.focusChange.tween())) {
            asked?.let { gate -> Box(Modifier.fillMaxSize().inert(app.profileGate == null)) { ProfileGateOverlay(app, gate) } }
        }
        // The Reflux keyboard (B-023), above everything while a controller or a remote types: at the foot of the
        // window, clear of the system's bars, rising as a sheet does.
        var typing by remember { mutableStateOf(app.keyboard) }
        app.keyboard?.let { typing = it }
        AnimatedVisibility(
            app.keyboard != null,
            Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(Motion.sheetEnter.tween()) + slideInVertically(Motion.sheetEnter.tween()) { it / 4 },
            exit = fadeOut(Motion.sheetExit.tween()),
        ) {
            typing?.let { session ->
                Box(Modifier.fillMaxWidth().padding(start = bars.start + 12.dp, end = bars.end + 12.dp, bottom = bars.bottom + 12.dp), contentAlignment = Alignment.BottomCenter) {
                    // Title completions as the query changes (D2), on Search's own typing pause; never taking focus.
                    if (session.kind == dev.reflux.ui.state.InputKind.SEARCH) {
                        LaunchedEffect(session, session.text, session.mode) {
                            kotlinx.coroutines.delay(140)
                            session.offer(app.completionsFor(session.text, if (session.mode == dev.reflux.ui.state.KeyboardMode.POINTER) 5 else 3))
                        }
                    }
                    dev.reflux.ui.components.RefluxKeyboard(
                        session,
                        onKey = { key -> app.pressKey(key) },
                        prompts = app.input.controllerFamily?.takeIf { app.namesButtons },
                        onComplete = app::completeOnKeyboard,
                        onPointerTravel = app::keyboardPointerTravel,
                    )
                }
            }
        }
    }
    }
}

/** What a destination is called, and how it is drawn in the navigation. */
private fun label(destination: Destination.Section): String = when (destination) {
    Destination.Home -> "Home"
    Destination.Movies -> "Movies"
    Destination.Shows -> "Shows"
    Destination.Search -> "Search"
    Destination.Downloads -> "Downloads"
    Destination.Sources -> "Sources"
    Destination.Settings -> "Settings"
    Destination.Profiles -> "Profiles"
}

private fun icon(destination: Destination.Section): Icon = when (destination) {
    Destination.Home -> Icon.HOME
    Destination.Movies -> Icon.MOVIES
    Destination.Shows -> Icon.SHOWS
    Destination.Search -> Icon.SEARCH
    Destination.Downloads -> Icon.DOWNLOAD
    Destination.Sources -> Icon.SOURCES
    Destination.Settings -> Icon.SETTINGS
    Destination.Profiles -> Icon.PROFILE
}

/**
 * Navigation on a phone held upright: Home, Movies, Shows, and Search along the bottom as icons, where a thumb
 * reaches them, and only what a page needs at the top (the way back, a sign of the library being updated, and the
 * utility area as one entry: the profile, which leads to Downloads, Sources, and Settings; docs/NAVIGATION.md).
 */
@Composable
private fun BoxScope.CompactNavigation(app: AppState) {
    val navigator = app.navigator
    val entry = LocalFocusEntry.current
    val activity by app.activity.collectAsState()
    Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(top = 12.dp).traversal(Traversal.BEFORE_PAGE), verticalAlignment = Alignment.CenterVertically) {
        if (navigator.current !is Destination.Section) IconButton(Icon.BACK, "Back", { navigator.back() }, size = 44.dp, glass = true)
        Spacer(Modifier.weight(1f))
        // The page's heading starts beside this row: the sign is the dot alone here, and on Search, whose field runs
        // across this row, it steps aside as the profile entry does (B-005); Sources says it in full.
        if (navigator.current != Destination.Search) ActivityIndicator(activity, compact = true)
        // Search has its field across the top; the utility area is a tap away from every other destination, but not
        // from Profiles itself, where it would only lead to the page already shown (B-046). From Downloads, Sources, and
        // Settings, reached from Profiles, it is the way back there, lit as where the viewer is.
        if (showsProfileButton(navigator.current)) ProfileButton(app)
    }
    // While the system keyboard is up, it owns the bottom of the screen: the destinations step aside rather than sit on
    // top of it and cover what is being typed into, or the button that comes after it. Once it is put away they come
    // back, even while the field keeps focus (B-036: they used to stay away, with only the system's Back to recover).
    AnimatedVisibility(!(app.textInputActive && app.systemKeyboardShown), Modifier.align(Alignment.BottomCenter).traversal(Traversal.AFTER_PAGE), enter = fadeIn(Motion.controlsShow.tween()), exit = fadeOut(Motion.controlsHide.tween())) {
        Column(Modifier.padding(bottom = 10.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // With a controller in hand, what its buttons do here, in that controller's own terms.
            AnimatedVisibility(app.namesButtons, enter = fadeIn(Motion.inputMorph.tween()), exit = fadeOut(Motion.controlsHide.tween())) {
                val family = app.input.controllerFamily ?: ControllerFamily.GENERIC
                ButtonPrompts(browsePrompts(app, family), family, hold = rememberBackHold(app, family))
            }
            // The bar is as wide as its four destinations and no wider: a compact capsule that floats over the page.
            val track = remember { SelectionTrack() }
            Row(
                Modifier.align(Alignment.CenterHorizontally).height(NavigationBarHeight).glass(MaterialRole.FLOATING, CircleShape).absorbsPointer()
                    .onFocusChanged { entry?.chrome = SemanticAction.NAVIGATE_UP.takeIf { _ -> it.hasFocus } }.selectableGroup().padding(horizontal = 6.dp)
                    // Where the viewer is: one lit pill, which travels to the destination they go to.
                    .selectionMark(track, navigator.highlighted.takeIf { it in PrimaryDestinations }),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Icons, with no words beneath them: each still says its name and whether it is where the viewer is.
                PrimaryDestinations.forEach { destination ->
                    val here = navigator.highlighted == destination
                    // A white icon in the lit pill. Shape and light, not color alone.
                    Box(Modifier.selectionPlace(track, destination)) {
                        IconButton(icon(destination), label(destination), { app.go(destination) }, size = 44.dp, width = 64.dp, tint = tabColor(here), selected = here, role = Role.Tab, lit = false)
                    }
                }
            }
        }
    }
}

/**
 * Focus in the navigation rail: the current destination's item, which is where a television's Back and Left land.
 */
internal class RailFocus {
    internal val current = FocusRequester()

    /** Where what has focus is, in the window, asked of the toolkit when a move needs to know. */
    internal val bounds = FocusedBounds()
    internal val focusedBounds: androidx.compose.ui.geometry.Rect? get() = bounds.now()

    internal fun focusCurrent(): Boolean = runCatching { current.requestFocus() }.getOrDefault(false)

    /** Where focus was in the page when it left for the navigation. */
    private var left: FocusRequester? = null

    /** Remembers where focus is in the page, before it leaves for the navigation. */
    internal fun leavingPage(focused: FocusedControl) {
        left = focused.requester
    }

    /** Back into the page where focus was in it; false when that is gone (another page, a closed panel). */
    internal fun enterPage(): Boolean = runCatching { left?.requestFocus() ?: false }.getOrDefault(false)
}

/**
 * The navigation down the left side (docs/NAVIGATION.md, section 3): a compact rail of icons on a phone on its side,
 * a rail with names beneath on a tablet, a sidebar with names beside on a desktop, and on a television a rail of
 * icons that shows their names while it has focus. Entering it lands on the destination the viewer is in.
 */
@Composable
private fun NavigationRail(app: AppState, style: NavigationStyle, focus: RailFocus, modifier: Modifier = Modifier) {
    val navigator = app.navigator
    val entry = LocalFocusEntry.current
    val television = style == NavigationStyle.TV_RAIL
    val compact = style == NavigationStyle.COMPACT_RAIL
    val track = remember { SelectionTrack() }
    // A phone's rail has one entry for the whole utility area; it is where the viewer is while they are anywhere in it.
    val marked: Any? = navigator.highlighted?.let { if (compact && it in app.utilityDestinations) Destination.Profiles else it }
    var focused by remember { mutableStateOf(false) }
    // A television's rail widens over the page while it has focus, to say what each icon is.
    val open = television && focused
    val motion = LocalMotion.current
    val width by animateDpAsState(if (open) TV_RAIL_OPEN else style.widthDp.dp, motion.railExpand.tween())
    val openness by animateFloatAsState(if (open) 1f else 0f, motion.railExpand.tween())
    Box(
        modifier.fillMaxHeight().width(width)
            .absorbsPointer()
            .onFocusChanged {
                focused = it.hasFocus
                entry?.chrome = SemanticAction.NAVIGATE_RIGHT.takeIf { _ -> it.hasFocus }
            },
    ) {
        if (television) {
            // Content first: closed, the rail is its icons over a soft shade that keeps them readable over artwork;
            // open, it is functional material over the page, as far as its names reach.
            Box(
                Modifier.matchParentSize().drawBehind {
                    val shade = RefluxColors.background
                    val reach = size.width + TV_RAIL_SHADE.toPx()
                    drawRect(Brush.horizontalGradient(0f to shade.copy(alpha = 0.82f * (1f - openness)), 1f to Color.Transparent, endX = reach), size = Size(reach, size.height))
                },
            )
            if (openness > 0f) {
                Box(Modifier.matchParentSize().graphicsLayer { alpha = openness }.glass(MaterialRole.FLOATING, RoundedCornerShape(topEnd = Radius.XLARGE.dp, bottomEnd = Radius.XLARGE.dp)))
            }
        }
        Column(
            Modifier.fillMaxHeight().fillMaxWidth()
                .then(if (television) Modifier else Modifier.padding(start = 8.dp, top = 10.dp, bottom = 10.dp, end = 4.dp).glass(MaterialRole.FLOATING, Shapes.xlarge))
                .focusProperties { onEnter = { focus.focusCurrent() } }
                .focusGroup()
                .selectableGroup()
                .padding(vertical = if (television) 28.dp else 12.dp, horizontal = if (television) 10.dp else 6.dp)
                // Where the viewer is: one lit shape with a bar at its leading edge, which travels between destinations.
                .selectionMark(track, marked, radius = if (compact) null else Radius.MEDIUM.dp, bar = !compact),
            verticalArrangement = Arrangement.spacedBy(if (style == NavigationStyle.SIDEBAR) 4.dp else 6.dp),
            horizontalAlignment = if (style == NavigationStyle.SIDEBAR || open) Alignment.Start else Alignment.CenterHorizontally,
        ) {
            // Reflux says its own name where there is room to: the lockup on a desktop, the mark alone on a tablet, and
            // on a television the mark, joined by the wordmark while the rail is open. A phone's rail has no room.
            // On a tablet and a desktop it is also the way Home, as a website's name is: the same as choosing Home (the
            // stack back to Home alone, playback and the library untouched). A television's rail is left as it was.
            when (style) {
                NavigationStyle.SIDEBAR -> BrandHome(app, Modifier.padding(start = 4.dp, top = 2.dp, bottom = 12.dp)) {
                    RefluxLockup(Modifier.padding(horizontal = 6.dp, vertical = 6.dp), height = 22.dp)
                }
                // The same height as the mark alone had (44), so the destinations beneath stay where they were.
                NavigationStyle.RAIL -> BrandHome(app, Modifier.padding(bottom = 6.dp)) {
                    RefluxMark(Modifier.padding(6.dp), size = 26.dp)
                }
                NavigationStyle.TV_RAIL -> {
                    val height = 20.dp
                    val mark = lockupMarkWidth(height)
                    val whole = height * (dev.reflux.design.BrandMark.LOCKUP_WIDTH / dev.reflux.design.BrandMark.LOCKUP_HEIGHT)
                    Box(Modifier.padding(start = if (open) 10.dp else 0.dp, bottom = 14.dp).width(mark + (whole - mark) * openness).height(height).clipToBounds().clearAndSetSemantics { }) {
                        RefluxLockup(Modifier.wrapContentWidth(Alignment.Start, unbounded = true), height = height, color = RefluxColors.contentPrimary.copy(alpha = 0.62f + 0.38f * openness))
                    }
                }
                else -> Unit
            }
            val shown = when {
                style == NavigationStyle.SIDEBAR -> NavigationLabel.BESIDE
                style == NavigationStyle.RAIL -> NavigationLabel.BENEATH
                // A television's item stays one focusable thing whether its name shows or not, so focus never
                // drops when the rail widens around it.
                television -> if (open) NavigationLabel.BESIDE else NavigationLabel.HIDDEN
                else -> NavigationLabel.NONE
            }
            // A phone's rail, on its side, has the height for the library and one more: its utility area is the one
            // profile entry, as when it is held upright (docs/NAVIGATION.md, section 3).
            val shownDestinations = if (compact) PrimaryDestinations + Destination.Profiles else app.destinations
            for (destination in shownDestinations) {
                // The utility area sits apart, at the foot (docs/NAVIGATION.md, section 1), beneath a hairline.
                if (destination == shownDestinations.firstOrNull { it in app.utilityDestinations }) {
                    Spacer(Modifier.weight(1f))
                    if (!compact) Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(bottom = 4.dp).height(1.dp).background(RefluxColors.divider))
                }
                if (compact && destination == Destination.Profiles) {
                    Box(Modifier.selectionPlace(track, destination)) {
                        CompactProfileEntry(app, if (navigator.highlighted in app.utilityDestinations) Modifier.focusRequester(focus.current) else Modifier)
                    }
                    continue
                }
                val here = navigator.highlighted == destination
                val busy = destination == Destination.Downloads && downloading(app)
                // Once there are several viewers, the profile entry is the viewer: their name, and their initial.
                val viewer = app.activeProfile?.takeIf { destination == Destination.Profiles && app.profilesShown }
                NavigationItem(
                    icon(destination),
                    viewer?.name ?: label(destination),
                    here,
                    shown,
                    busy,
                    if (here) Modifier.focusRequester(focus.current) else Modifier,
                    Modifier.selectionPlace(track, destination),
                    viewer,
                ) { app.go(destination) }
            }
        }
    }
}

/**
 * Reflux's name at the head of a rail or a sidebar, which goes Home when chosen: a button named "Reflux, Home" for a
 * screen reader, a stop for a keyboard (above Home), and lit when pointed at like the destinations beneath it.
 */
@Composable
private fun BrandHome(app: AppState, modifier: Modifier, mark: @Composable () -> Unit) {
    Pressable(
        { app.go(Destination.Home) },
        modifier.semantics { contentDescription = BRAND_HOME_NAME },
        Shapes.medium,
        lift = 0f,
        actionLabel = "go to Home",
    ) { active ->
        Box(Modifier.background(States.layer(if (active) 0.06f else 0f)).clearAndSetSemantics { }) { mark() }
    }
}

/** What the navigation's Reflux mark is called to a screen reader, and found by in tests. */
internal const val BRAND_HOME_NAME = "Reflux, Home"

/** The utility area's one entry in a phone's rail: named as on the phone held upright, lit while the viewer is anywhere in it. */
@Composable
private fun CompactProfileEntry(app: AppState, modifier: Modifier) {
    val here = app.navigator.highlighted in app.utilityDestinations
    val who = app.activeProfile?.takeIf { app.profilesShown }?.name
    val busy = downloading(app)
    val name = listOfNotNull(who?.let { "Profile, $it" } ?: "Profile and settings", "downloading".takeIf { busy }).joinToString(", ")
    Box {
        IconButton(Icon.PROFILE, name, { app.go(Destination.Profiles) }, modifier, size = 44.dp, tint = tabColor(here), selected = here, role = Role.Tab, lit = false)
        if (busy) BusyDot(Modifier.align(Alignment.TopEnd))
    }
}

/** How wide a television's rail is while it has focus and shows its names. */
private val TV_RAIL_OPEN = 208.dp

/** How far past a television's closed rail its shade fades out over the page. */
private val TV_RAIL_SHADE = 56.dp

/** How much of a destination's name the navigation shows: none (an icon button), beneath, beside, or kept hidden in a row that can show it. */
private enum class NavigationLabel { NONE, HIDDEN, BENEATH, BESIDE }

/**
 * One destination in a rail or a sidebar. Where the viewer is, is the navigation's one lit shape and the short bar at
 * its leading edge ([selectionMark]), drawn behind the destinations and travelling between them: shape and light
 * together, so it never rests on a color or a brightness alone. Hover and focus are the active state (a quieter
 * light), which each destination shows itself; focus adds the ring for input that needs one.
 *
 * @param place on the destination's outermost box: where it is, for the mark.
 * @param viewer the profile this entry stands for, shown as its initial in place of the icon.
 */
@Composable
private fun NavigationItem(
    icon: Icon, label: String, selected: Boolean, shown: NavigationLabel, busy: Boolean, modifier: Modifier = Modifier, place: Modifier = Modifier, viewer: Profile? = null,
    onClick: () -> Unit,
) {
    val name = if (busy) "$label, downloading" else label
    if (shown == NavigationLabel.NONE) {
        // An icon alone still names itself to assistive technology and, to a resting pointer, to the eye.
        Box(place) {
            // The focus requester goes on the button itself: a box around it cannot take focus.
            IconButton(icon, name, onClick, modifier, size = 44.dp, tint = tabColor(selected), selected = selected, role = Role.Tab, lit = false)
            if (busy) BusyDot(Modifier.align(Alignment.TopEnd))
        }
        return
    }
    val layout = LocalLayout.current
    val television = layout.formFactor == FormFactor.TV
    Pressable(
        onClick,
        place.then(modifier).fillMaxWidth()
            .semantics {
                this.selected = selected
                if (busy) stateDescription = "Downloading"
                if (shown == NavigationLabel.HIDDEN) contentDescription = label
            },
        Shapes.medium,
        lift = 0f,
        role = Role.Tab,
    ) { active ->
        val tint = tabColor(selected || active)
        val fill by animateColorAsState(States.layer(if (active) StateLayers.HOVER else 0f), Motion.focusChange.tween())
        val background = Modifier.background(fill)
        if (shown == NavigationLabel.BESIDE || shown == NavigationLabel.HIDDEN) {
            Row(
                background.fillMaxWidth().heightIn(min = maxOf(layout.minTargetDp.dp, 44.dp)).padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box { if (viewer != null) ProfileMark(viewer, 22.dp, lit = selected) else Icon(icon, size = 20.dp, tint = tint); if (busy) BusyDot(Modifier.align(Alignment.TopEnd)) }
                if (shown == NavigationLabel.BESIDE) {
                    Text(label, style = if (television) LocalTypography.current.tvLabel else LocalTypography.current.navigation, color = tint, maxLines = 1)
                }
            }
        } else {
            Column(
                background.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                Box { if (viewer != null) ProfileMark(viewer, 24.dp, lit = selected) else Icon(icon, size = 22.dp, tint = tint); if (busy) BusyDot(Modifier.align(Alignment.TopEnd)) }
                Text(label, style = LocalTypography.current.navigationCompact, color = tint, maxLines = 1)
            }
        }
    }
}

@Composable
private fun BusyDot(modifier: Modifier = Modifier) = DownloadBadge(modifier)

/**
 * The mark of something downloading, on the way to Downloads (B-047): a small arrow in a lit circle at the corner of the
 * entry, which says what it is where a bare dot said nothing. The entry's name says it as words ("…, downloading").
 */
@Composable
private fun DownloadBadge(modifier: Modifier = Modifier) {
    Box(modifier.size(14.dp).background(RefluxColors.contentPrimary, CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icon.DOWNLOAD, size = 10.dp, tint = RefluxColors.background)
    }
}

/** Whether something is being downloaded, for a small dot on the way to Downloads; it does not move. */
@Composable
private fun downloading(app: AppState): Boolean {
    if (app.platform.offline == null) return false
    val busy by app.offlineState { visibleDownloads().any { it.active } }
    return busy == true
}

/**
 * The top of a page beside a rail: the way back where the page was reached from somewhere (a remote has its own
 * Back), a quiet sign of the library being updated, and, with a controller in hand, what its buttons do.
 */
@Composable
private fun PageBar(app: AppState, modifier: Modifier = Modifier) {
    val navigator = app.navigator
    val layout = LocalLayout.current
    val activity by app.activity.collectAsState()
    Row(
        modifier.fillMaxWidth().height(PageBarHeight).padding(horizontal = layout.safeMarginDp.dp.coerceAtMost(24.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigator.current !is Destination.Section && layout.formFactor != FormFactor.TV) {
            IconButton(Icon.BACK, "Back", { navigator.back() }, size = 44.dp, glass = true)
        }
        Spacer(Modifier.weight(1f))
        AnimatedVisibility(visible = app.namesButtons, enter = fadeIn(Motion.inputMorph.tween()), exit = fadeOut(Motion.controlsHide.tween())) {
            val family = app.input.controllerFamily ?: ControllerFamily.GENERIC
            ButtonPrompts(browsePrompts(app, family), family, Modifier.padding(end = 14.dp), hold = rememberBackHold(app, family))
        }
        // A television's pages start inside this bar, under it: there the sign is the dot alone (B-005).
        ActivityIndicator(activity, compact = layout.formFactor == FormFactor.TV)
    }
}

/** How far the mini-player's dock sits from the window's bottom corner, where the navigation runs down the side. */
internal val DockMargin = 24.dp

/** The space between the phone's top-corner buttons (Downloads, Sources). */
internal val TopActionGap = 14.dp

/**
 * The utility area on a phone held upright: one entry at the top right, the profile, which leads to who is
 * watching, Downloads, Sources, and Settings (docs/NAVIGATION.md, section 1). While something is being downloaded a
 * small dot says so; it does not move.
 */
@Composable
private fun ProfileButton(app: AppState) {
    val busy = downloading(app)
    val here = app.navigator.section in app.utilityDestinations
    val who = app.activeProfile?.takeIf { app.profilesShown }?.name
    val name = listOfNotNull(who?.let { "Profile, $it" } ?: "Profile and settings", "downloading".takeIf { busy }).joinToString(", ")
    Box {
        Pressable(
            { app.go(Destination.Profiles) },
            Modifier.semantics { contentDescription = name; selected = here },
            CircleShape,
            lift = 0.4f,
            role = Role.Tab,
        ) {
            Box(Modifier.size(44.dp).glass(MaterialRole.FLOATING, CircleShape), contentAlignment = Alignment.Center) {
                if (who != null) ProfileMark(app.activeProfile, 34.dp, lit = here) else Icon(Icon.PROFILE, size = 22.dp, tint = tabColor(here))
            }
        }
        if (busy) DownloadBadge(Modifier.align(Alignment.TopEnd))
    }
}

/**
 * Whether a phone's top row carries the profile entry on [destination] (B-046): on a destination's own page, except
 * Search (its field runs across the row) and Profiles (the entry would lead to itself).
 */
internal fun showsProfileButton(destination: Destination): Boolean =
    destination is Destination.Section && destination != Destination.Search && destination != Destination.Profiles

/**
 * What a controller's buttons do while browsing, said only where each is available now (B-039, B-012; the mapping and
 * its conflict matrix are in docs/INPUT_MODEL.md, "Controller mapping"). Y (and Start) open Sources; X says nothing,
 * being reserved for Phase 3's Media Quick View (DECISIONS.md, 2026-11-19).
 */
/**
 * B-052: where holding Back closes the mini-player, its prompt is marked as one to hold, and its ring fills while Back is
 * held, over exactly the time it takes ([dev.reflux.ui.state.BACK_HOLD_MS]); let go, it empties at once.
 */
@Composable
internal fun rememberBackHold(app: AppState, family: ControllerFamily): dev.reflux.ui.components.HoldPrompt? {
    val progress by animateFloatAsState(
        if (app.backHolding) 1f else 0f,
        if (app.backHolding) tween(dev.reflux.ui.state.BACK_HOLD_MS.toInt(), easing = LinearEasing) else snap(),
    )
    if (!app.backWaitsForRelease || app.hintsHidden) return null
    return dev.reflux.ui.components.HoldPrompt(InputMapping.cancelButton(family), progress)
}

internal fun browsePrompts(app: AppState, family: ControllerFamily): List<Pair<GamepadButton, String>> =
    // B-055: hidden, the bar keeps only LT's own hint, the way to bring the others back.
    if (app.hintsHidden) listOf(GamepadButton.TRIGGER_LEFT to "Show hints") else allBrowsePrompts(app, family) + (GamepadButton.TRIGGER_LEFT to "Hide hints")

private fun allBrowsePrompts(app: AppState, family: ControllerFamily): List<Pair<GamepadButton, String>> = listOfNotNull(
    InputMapping.confirmButton(family) to "Select",
    when {
        app.offlineOpen -> InputMapping.cancelButton(family) to "Close"
        // B-052: with the mini-player up, holding B is one of the two ways to close it (RT, below, is the other).
        app.backWaitsForRelease -> InputMapping.cancelButton(family) to if (app.navigator.canGoBack) "Back · hold to close player" else "Hold to close player"
        app.navigator.canGoBack -> InputMapping.cancelButton(family) to "Back"
        else -> null
    },
    (GamepadButton.FACE_NORTH to "Sidebar").takeIf { app.toggleNavigation != null && app.folderPicker == null && !app.offlineOpen },
    // B-052: with the mini-player up, RT closes it (so does holding Back).
    (GamepadButton.TRIGGER_RIGHT to "Close player").takeIf { app.docked != null && app.watching == null },
)

/** Where the user is shows as white against grey: the navigation never takes a color. */
private fun tabColor(selected: Boolean): Color = if (selected) RefluxColors.contentPrimary else RefluxColors.contentSecondary

/**
 * A quiet sign that the library is being updated; it never blocks anything, and never covers anything (B-005). Where
 * the top of the page has a bar of its own for it (beside a rail or a sidebar) it says what is happening, in at most
 * [ActivityLabelWidth]; where the page's own heading or search field starts beside it (a phone's top row, a
 * television), it is the dot alone, in its fixed place, and says it to a screen reader. What each source is doing is
 * said in full on Sources.
 */
@Composable
private fun ActivityIndicator(activity: PipelineActivity, compact: Boolean = false) {
    AnimatedContent(
        targetState = activityLabel(activity),
        transitionSpec = { fadeIn(Motion.controlsShow.tween()) togetherWith fadeOut(Motion.controlsHide.tween()) },
    ) { label ->
        if (label != null) {
            if (compact) {
                // Where there is no room for its words, a small turning arc, the usual sign of work going on, rather than
                // a dot that said nothing of what it was (B-047). Its words are said to a screen reader, and on Sources.
                Box(Modifier.size(24.dp).semantics { contentDescription = label }.testTag("activity:$label"), contentAlignment = Alignment.Center) {
                    WorkingArc(Modifier.size(14.dp))
                }
            } else {
                Row(Modifier.padding(end = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(RefluxColors.contentSecondary, CircleShape))
                    Text(label, Modifier.widthIn(max = ActivityLabelWidth), style = LocalTypography.current.caption, color = RefluxColors.contentSecondary, maxLines = 1)
                }
            }
        }
    }
}

/** A small arc turning once a second (still, with motion reduced): the library being updated. */
@Composable
private fun WorkingArc(modifier: Modifier = Modifier) {
    val turn = if (dev.reflux.ui.theme.MotionPolicy.reduced) {
        0f
    } else {
        val spin = androidx.compose.animation.core.rememberInfiniteTransition()
        spin.animateFloat(
            0f, 360f,
            androidx.compose.animation.core.infiniteRepeatable<Float>(androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.LinearEasing)),
        ).value
    }
    val color = RefluxColors.contentSecondary
    Box(
        modifier.drawBehind {
            val stroke = 2.dp.toPx()
            drawArc(color, startAngle = turn, sweepAngle = 270f, useCenter = false, topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        },
    )
}

/** The most room the library's sign takes in a page's top bar: a long source name is cut short, never spread over the bar. */
internal val ActivityLabelWidth = 260.dp

/** What a page is called when it arrives, for a screen reader; a work's or a source's page says its own name. */
private fun accessibleTitle(destination: Destination): String? = when (destination) {
    Destination.Home -> "Home"
    Destination.Movies -> "Movies"
    Destination.Shows -> "TV Shows"
    Destination.Search -> "Search"
    is Destination.Listing -> destination.title
    Destination.Downloads -> "Downloads"
    Destination.Sources -> "Sources"
    Destination.Settings -> "Settings"
    Destination.Profiles -> "Profiles"
    Destination.AddSource -> "Add media"
    is Destination.Connect -> "Connect"
    is Destination.SourceLibrary -> null
    Destination.About -> "About Reflux"
    Destination.Notices -> "Third-party software"
    Destination.Credits -> "Credits and technology"
    is Destination.Notice -> destination.name
    is Destination.Details -> null
    is Destination.Identify -> "Identify"
}

internal fun activityLabel(activity: PipelineActivity): String? = when (activity) {
    PipelineActivity.Idle -> null
    is PipelineActivity.Scanning -> "Scanning ${activity.sourceName}"
    PipelineActivity.Probing -> "Reading media details"
    PipelineActivity.FetchingMetadata -> "Finding titles and details"
    PipelineActivity.FetchingArtwork -> "Fetching artwork"
}
