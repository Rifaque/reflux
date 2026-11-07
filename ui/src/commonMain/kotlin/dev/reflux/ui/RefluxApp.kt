package dev.reflux.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import dev.reflux.ui.screens.BrowseScreen
import dev.reflux.ui.screens.DetailsScreen
import dev.reflux.ui.screens.FolderPickerPanel
import dev.reflux.ui.screens.HomeScreen
import dev.reflux.ui.screens.IdentifyScreen
import dev.reflux.ui.screens.DownloadsScreen
import dev.reflux.ui.state.offlineState
import dev.reflux.library.downloads
import dev.reflux.ui.screens.OfflinePanel
import dev.reflux.ui.screens.SearchScreen
import dev.reflux.ui.screens.AboutScreen
import dev.reflux.ui.screens.AddSourceScreen
import dev.reflux.ui.screens.NoticeScreen
import dev.reflux.ui.screens.NoticesScreen
import dev.reflux.ui.screens.ConnectScreen
import dev.reflux.ui.screens.SourcesScreen
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import dev.reflux.design.NavigationStyle
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
internal data class ChromeInsets(val top: Dp, val bottom: Dp, val start: Dp = 0.dp)

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

private fun chromeInsets(navigation: NavigationStyle, onSection: Boolean, prompts: Boolean): ChromeInsets = when (navigation) {
    // A page reached from somewhere has a way back in its top corner; with a controller in hand, its
    // button prompts sit above the sections, and the page ends clear of them too.
    NavigationStyle.BOTTOM_BAR -> ChromeInsets(top = if (onSection) 20.dp else 72.dp, bottom = 10.dp + NavigationBarHeight + 28.dp + if (prompts) 52.dp else 0.dp)
    // A remote has its own Back: a television's pages start close to the top of its safe area.
    NavigationStyle.TV_RAIL -> ChromeInsets(top = 36.dp, bottom = 32.dp, start = navigation.widthDp.dp)
    else -> ChromeInsets(top = PageBarHeight, bottom = 32.dp, start = navigation.widthDp.dp)
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
        SideEffect { MotionPolicy.reduced = accessibility.reduceMotion }
        val density = LocalDensity.current
        val posterWidth by animateFloatAsState(target.posterWidthDp, Motion.inputMorph.tween())
        val focusScale by animateFloatAsState(target.focusScale, Motion.inputMorph.tween())
        val minTarget by animateFloatAsState(target.minTargetDp, Motion.inputMorph.tween())
        val layout = target.copy(posterWidthDp = posterWidth, focusScale = focusScale, minTargetDp = minTarget)
        CompositionLocalProvider(
            LocalLayout provides layout,
            LocalAccessibility provides accessibility,
            LocalDensity provides remember(density, accessibility.textScale) { Density(density.density, density.fontScale * accessibility.textScale) },
            LocalTypography provides remember(target.textScale, target.formFactor) { Typography(target.textScale, displaySize(target.formFactor)) },
            LocalArtworkLoader provides app.platform.artwork,
            content = content,
        )
    }
}

/** A featured title fills a wide window; on a phone it stays large without breaking every word. */
private fun displaySize(formFactor: FormFactor): Float = when (formFactor) {
    FormFactor.PHONE -> 40f
    FormFactor.TV -> 48f
    else -> 54f
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

/** The browsing experience: library screens beside, or above, the global navigation (docs/NAVIGATION.md). */
@Composable
fun RefluxApp(app: AppState, modifier: Modifier = Modifier) {
    RefluxTheme(app, modifier) {
        val focus = LocalFocusManager.current
        val entry = remember { FocusEntry() }
        val focused = remember { FocusedControl() }
        val rail = remember { RailFocus() }
        DisposableEffect(app, focus) {
            app.moveFocus = { action ->
                entry.enter() || entry.leaveChrome(action) || focus.moveFocus(
                    when (action) {
                        SemanticAction.NAVIGATE_UP -> FocusDirection.Up
                        SemanticAction.NAVIGATE_DOWN -> FocusDirection.Down
                        SemanticAction.NAVIGATE_LEFT -> FocusDirection.Left
                        else -> FocusDirection.Right
                    },
                )
            }
            app.clearFocus = { focus.clearFocus() }
            app.activateFocused = focused::activate
            // Back on a television's destination goes to the navigation first, landing on where the viewer is.
            app.focusNavigation = { !entry.inChrome && rail.focusCurrent() }
            onDispose {
                app.moveFocus = null
                app.clearFocus = null
                app.activateFocused = null
                app.focusNavigation = null
            }
        }
        val screens = rememberSaveableStateHolder()
        entry.screen = app.navigator.current
        val layout = LocalLayout.current
        val insets = chromeInsets(layout.navigation, app.navigator.current is Destination.Section, app.namesButtons)
        // A television is only ever operated through focus, so something always has it: when a screen
        // appears, or what had focus goes away, it lands where the user left off or on the main action.
        if (layout.formFactor == FormFactor.TV) {
            // A screen that has just appeared starts on its own main action, not on whatever the toolkit
            // found first (the navigation beside it).
            // The same when a page gains a main action it did not have (a library that just appeared), or a
            // panel over it closes, unless the user has since put focus somewhere in the page themselves.
            val covered = app.offlineOpen || app.folderPicker != null
            var started by remember { mutableStateOf<Destination?>(null) }
            LaunchedEffect(app.navigator.current, entry.starts, covered) {
                if (covered) return@LaunchedEffect
                val arrived = started != app.navigator.current
                repeat(FOCUS_PATIENCE_FRAMES) {
                    withFrameNanos { }
                    if (!arrived && entry.hasFocus && !entry.inChrome && !entry.inPanel) return@LaunchedEffect
                    // Focus the viewer put on the navigation stays there until they leave it.
                    if (!arrived && entry.inChrome) return@LaunchedEffect
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
            val covered = app.offlineOpen || app.folderPicker != null
            LaunchedEffect(covered) {
                if (covered) return@LaunchedEffect
                repeat(FOCUS_PATIENCE_FRAMES) {
                    withFrameNanos { }
                    if (entry.afterPanel == null || entry.start()) return@LaunchedEffect
                }
            }
        }
        // What is in front is all a screen reader finds: the player, or a panel that holds focus until it closes.
        val panelOpen = app.offlineOpen || app.folderPicker != null
        val behind = app.playback != null || panelOpen
        val page = remember { PageScroll() }
        CompositionLocalProvider(LocalFocusEntry provides entry, LocalFocusedControl provides focused, LocalChromeInsets provides insets, LocalPageScroll provides page) {
            GlassScene(
                Modifier.fillMaxSize().background(RefluxColors.background).trackPointer(app).onFocusChanged { entry.hasFocus = it.hasFocus },
                overlay = { Chrome(app, page, rail, navigationInert = behind, noticeInert = app.playback != null) },
            ) {
                // Pages start beside a navigation that runs down the side; focus that scrolls a page stops short of the chrome.
                Box(Modifier.fillMaxSize().padding(start = insets.start)) {
                    FocusScrollInsets(start = insets.top, end = insets.bottom + 8.dp) {
                        AnimatedContent(
                            targetState = app.navigator.current,
                            transitionSpec = {
                                (fadeIn(Motion.navigation.tween()) + slideInVertically(Motion.navigation.tween()) { it / 40 }) togetherWith
                                    fadeOut(Motion.focusChange.tween())
                            },
                        ) { destination ->
                            // A page on its way out stays on screen while it fades, but it is no longer there for
                            // assistive technology, which would otherwise find two pages at once.
                            val leaving = transition.targetState == EnterExitState.PostExit
                            val named = accessibleTitle(destination)
                            Box(
                                Modifier.fillMaxSize().traversal(Traversal.PAGE).inert(leaving || behind)
                                    .then(if (named != null) Modifier.pane(named) else Modifier),
                            ) {
                                screens.SaveableStateProvider(destination.toString()) {
                                    CompositionLocalProvider(LocalFocusScreen provides destination) {
                                        Screen(app, destination)
                                    }
                                }
                            }
                        }
                    }
                    if (layout.formFactor == FormFactor.TV) {
                        val fade by animateFloatAsState(if (page.scrolled) 1f else 0f, Motion.navigation.tween())
                        val background = RefluxColors.background
                        Box(
                            Modifier.fillMaxWidth().height(insets.top).graphicsLayer { alpha = fade }
                                .background(Brush.verticalGradient(0f to background, 0.72f to background.copy(alpha = 0.94f), 1f to Color.Transparent)),
                        )
                    }
                }
            }
        }
    }
}

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
        Destination.About -> AboutScreen(app)
        Destination.Notices -> NoticesScreen(app)
        is Destination.Notice -> NoticeScreen(app, destination.name)
    }
}

/** Everything that floats above the screens: navigation, the offline sheet, button prompts, and notices. */
@Composable
private fun Chrome(app: AppState, page: PageScroll, rail: RailFocus, navigationInert: Boolean, noticeInert: Boolean) {
    val layout = LocalLayout.current
    val bottomBar = layout.navigation == NavigationStyle.BOTTOM_BAR
    val margin = Modifier.fillMaxSize().padding(horizontal = layout.safeMarginDp.dp)
    Box(Modifier.fillMaxSize()) {
        // The navigation's words stop growing before they stop fitting; the destinations always say all of them to a screen reader.
        LimitedTextSize(NAVIGATION_TEXT_LIMIT) {
            if (bottomBar) {
                Box(margin.inert(navigationInert)) { CompactNavigation(app) }
            } else {
                Box(Modifier.fillMaxSize().inert(navigationInert)) {
                    NavigationRail(app, layout.navigation, rail, Modifier.align(Alignment.TopStart).traversal(Traversal.BEFORE_PAGE))
                    PageBar(app, Modifier.align(Alignment.TopStart).padding(start = layout.navigation.widthDp.dp).traversal(Traversal.BEFORE_PAGE))
                }
            }
        }
        // A click outside the open panel closes it and goes no further.
        if (app.offlineOpen) {
            Box(Modifier.fillMaxSize().pointerInput(app) { detectTapGestures { app.offlineOpen = false } })
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
            AnimatedVisibility(
                visible = app.offlineOpen,
                modifier = if (bottomBar) {
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp + NavigationBarHeight + 12.dp)
                } else {
                    Modifier.align(Alignment.TopEnd).padding(top = PageBarHeight + 4.dp)
                },
                enter = fadeIn(Motion.controlsShow.tween()) + slideInVertically(Motion.controlsShow.tween()) { if (bottomBar) it / 8 else -it / 12 },
                exit = fadeOut(Motion.controlsHide.tween()),
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
                        Modifier.align(Alignment.BottomCenter).padding(bottom = if (bottomBar) 10.dp + NavigationBarHeight + 14.dp else 32.dp)
                            .padding(start = layout.navigation.widthDp.dp)
                    }
                ).announces(app.notice.takeUnless { noticeInert }),
                transitionSpec = {
                    (fadeIn(Motion.controlsShow.tween()) + slideInVertically(Motion.controlsShow.tween()) { it / 3 }) togetherWith
                        (fadeOut(Motion.controlsHide.tween()) + slideOutVertically(Motion.controlsHide.tween()) { it / 3 })
                },
            ) { message ->
                if (message != null) {
                    Box(
                        Modifier.widthIn(max = 640.dp).glass(MaterialRole.MODAL, RoundedCornerShape(22.dp)).absorbsPointer()
                            .padding(horizontal = 22.dp, vertical = 13.dp),
                    ) {
                        Text(message, style = LocalTypography.current.bodyStrong, align = TextAlign.Center)
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
}

private fun icon(destination: Destination.Section): Icon = when (destination) {
    Destination.Home -> Icon.HOME
    Destination.Movies -> Icon.MOVIES
    Destination.Shows -> Icon.SHOWS
    Destination.Search -> Icon.SEARCH
    Destination.Downloads -> Icon.DOWNLOAD
    Destination.Sources -> Icon.SOURCES
}

/**
 * Navigation on a phone held upright: Home, Movies, Shows, and Search along the bottom as icons, where a thumb
 * reaches them, and only what a page needs at the top (the way back, Downloads and Sources, a sign of the library
 * being updated).
 */
@Composable
private fun BoxScope.CompactNavigation(app: AppState) {
    val navigator = app.navigator
    val entry = LocalFocusEntry.current
    val activity by app.activity.collectAsState()
    Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(top = 12.dp).traversal(Traversal.BEFORE_PAGE), verticalAlignment = Alignment.CenterVertically) {
        if (navigator.current !is Destination.Section) IconButton(Icon.BACK, "Back", { navigator.back() }, size = 44.dp, glass = true)
        Spacer(Modifier.weight(1f))
        ActivityIndicator(activity)
        // Search has its field across the top; Downloads and Sources are a tap away from every other destination.
        if (navigator.current is Destination.Section && navigator.current != Destination.Search) {
            if (app.keepsCopies) DownloadsButton(app, size = 44.dp, glass = true)
            val here = navigator.section == Destination.Sources
            IconButton(Icon.SOURCES, "Sources", { app.go(Destination.Sources) }, size = 44.dp, glass = true, selected = here, role = Role.Tab)
        }
    }
    // While typing, the keyboard owns the bottom of the screen: the destinations step aside rather than sit on
    // top of the keyboard and cover what is being typed into, or the button that comes after it.
    AnimatedVisibility(!app.textInputActive, Modifier.align(Alignment.BottomCenter).traversal(Traversal.AFTER_PAGE), enter = fadeIn(Motion.controlsShow.tween()), exit = fadeOut(Motion.controlsHide.tween())) {
        Column(Modifier.padding(bottom = 10.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // With a controller in hand, what its buttons do here, in that controller's own terms.
            AnimatedVisibility(app.namesButtons, enter = fadeIn(Motion.inputMorph.tween()), exit = fadeOut(Motion.controlsHide.tween())) {
                val family = app.input.controllerFamily ?: ControllerFamily.GENERIC
                ButtonPrompts(
                    listOfNotNull(
                        InputMapping.confirmButton(family) to "Select",
                        (InputMapping.cancelButton(family) to if (app.offlineOpen) "Close" else "Back").takeIf { app.offlineOpen || navigator.canGoBack },
                    ),
                    family,
                )
            }
            Row(
                Modifier.align(Alignment.CenterHorizontally).widthIn(max = 360.dp).fillMaxWidth().height(NavigationBarHeight).glass(MaterialRole.FLOATING, CircleShape).absorbsPointer()
                    .onFocusChanged { entry?.chrome = SemanticAction.NAVIGATE_UP.takeIf { _ -> it.hasFocus } }.selectableGroup().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Icons, with no words beneath them: each still says its name and whether it is where the viewer is.
                listOf(Destination.Home, Destination.Movies, Destination.Shows, Destination.Search).forEach { destination ->
                    val here = navigator.highlighted == destination
                    IconButton(icon(destination), label(destination), { app.go(destination) }, size = 44.dp, tint = tabColor(here), selected = here, role = Role.Tab)
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

    internal fun focusCurrent(): Boolean = runCatching { current.requestFocus() }.getOrDefault(false)
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
    var focused by remember { mutableStateOf(false) }
    // A television's rail widens over the page while it has focus, to say what each icon is.
    val open = television && focused
    val width by animateDpAsState(if (open) 208.dp else style.widthDp.dp, Motion.navigation.tween())
    val shape = RoundedCornerShape(if (television) 0.dp else 24.dp)
    Column(
        modifier.fillMaxHeight().width(width)
            .then(
                when {
                    open -> Modifier.background(Brush.horizontalGradient(0f to RefluxColors.background, 0.7f to RefluxColors.background.copy(alpha = 0.96f), 1f to RefluxColors.background.copy(alpha = 0.82f)))
                    television -> Modifier
                    else -> Modifier.padding(start = 8.dp, top = 10.dp, bottom = 10.dp, end = 4.dp).glass(MaterialRole.FLOATING, shape)
                },
            )
            .absorbsPointer()
            .onFocusChanged {
                focused = it.hasFocus
                entry?.chrome = SemanticAction.NAVIGATE_RIGHT.takeIf { _ -> it.hasFocus }
            }
            .focusProperties { onEnter = { focus.focusCurrent() } }
            .focusGroup()
            .selectableGroup()
            .padding(vertical = if (television) 28.dp else 12.dp, horizontal = if (television) 12.dp else 4.dp),
        verticalArrangement = Arrangement.spacedBy(if (style == NavigationStyle.SIDEBAR) 4.dp else 6.dp),
        horizontalAlignment = if (style == NavigationStyle.SIDEBAR || open) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        if (style == NavigationStyle.SIDEBAR) {
            Text("Reflux", Modifier.padding(start = 14.dp, top = 6.dp, bottom = 18.dp), style = LocalTypography.current.title.copy(letterSpacing = (-0.02).em))
        }
        for (destination in app.destinations) {
            val here = navigator.highlighted == destination
            val busy = destination == Destination.Downloads && downloading(app)
            NavigationItem(
                icon(destination),
                label(destination),
                here,
                when {
                    style == NavigationStyle.SIDEBAR -> NavigationLabel.BESIDE
                    style == NavigationStyle.RAIL -> NavigationLabel.BENEATH
                    // A television's item stays one focusable thing whether its name shows or not, so focus never
                    // drops when the rail widens around it.
                    television -> if (open) NavigationLabel.BESIDE else NavigationLabel.HIDDEN
                    else -> NavigationLabel.NONE
                },
                busy,
                if (here) Modifier.focusRequester(focus.current) else Modifier,
            ) { app.go(destination) }
        }
    }
}

/** How much of a destination's name the navigation shows: none (an icon button), beneath, beside, or kept hidden in a row that can show it. */
private enum class NavigationLabel { NONE, HIDDEN, BENEATH, BESIDE }

@Composable
private fun NavigationItem(icon: Icon, label: String, selected: Boolean, shown: NavigationLabel, busy: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val name = if (busy) "$label, downloading" else label
    if (shown == NavigationLabel.NONE) {
        // An icon alone still names itself to assistive technology and, to a resting pointer, to the eye.
        Box {
            // The focus requester goes on the button itself: a box around it cannot take focus.
            IconButton(icon, name, onClick, modifier, size = 44.dp, tint = tabColor(selected), selected = selected, role = Role.Tab)
            if (busy) BusyDot(Modifier.align(Alignment.TopEnd))
        }
        return
    }
    val layout = LocalLayout.current
    Pressable(
        onClick,
        modifier.fillMaxWidth()
            .semantics {
                this.selected = selected
                if (busy) stateDescription = "Downloading"
                if (shown == NavigationLabel.HIDDEN) contentDescription = label
            },
        RoundedCornerShape(14.dp),
        lift = 0f,
        role = Role.Tab,
    ) { active ->
        val tint = tabColor(selected || active)
        val background = Modifier.background(Color.White.copy(alpha = (if (selected) 0.12f else 0f) + (if (active) 0.06f else 0f)))
        if (shown == NavigationLabel.BESIDE || shown == NavigationLabel.HIDDEN) {
            Row(
                background.fillMaxWidth().heightIn(min = maxOf(layout.minTargetDp.dp, 44.dp)).padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box { Icon(icon, size = 20.dp, tint = tint); if (busy) BusyDot(Modifier.align(Alignment.TopEnd)) }
                if (shown == NavigationLabel.BESIDE) Text(label, style = LocalTypography.current.bodyStrong, color = tint, maxLines = 1)
            }
        } else {
            Column(
                background.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                Box { Icon(icon, size = 22.dp, tint = tint); if (busy) BusyDot(Modifier.align(Alignment.TopEnd)) }
                Text(label, style = LocalTypography.current.captionStrong, color = tint, maxLines = 1)
            }
        }
    }
}

@Composable
private fun BusyDot(modifier: Modifier = Modifier) {
    Box(modifier.padding(top = 2.dp, end = 2.dp).size(7.dp).background(RefluxColors.textPrimary, CircleShape))
}

/** Whether something is being downloaded, for a small dot on the way to Downloads; it does not move. */
@Composable
private fun downloading(app: AppState): Boolean {
    if (app.platform.offline == null) return false
    val busy by app.offlineState { downloads().any { it.active } }
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
            ButtonPrompts(
                listOfNotNull(
                    InputMapping.confirmButton(family) to "Select",
                    (InputMapping.cancelButton(family) to if (app.offlineOpen) "Close" else "Back").takeIf { app.offlineOpen || navigator.canGoBack },
                    (GamepadButton.START to "Sources").takeIf { navigator.section != Destination.Sources },
                ),
                family,
                Modifier.padding(end = 14.dp),
            )
        }
        ActivityIndicator(activity)
    }
}

/** The way to Downloads on a phone held upright. While something is being downloaded a small dot says so; it does not move. */
@Composable
private fun DownloadsButton(app: AppState, size: Dp = 40.dp, glass: Boolean = false) {
    val busy = downloading(app)
    val here = app.navigator.section == Destination.Downloads
    Box {
        IconButton(Icon.DOWNLOAD, if (busy) "Downloads, downloading" else "Downloads", { app.go(Destination.Downloads) }, size = size, glass = glass, tint = tabColor(here), selected = here, role = Role.Tab)
        if (busy) Box(Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 7.dp).size(7.dp).background(RefluxColors.textPrimary, CircleShape))
    }
}

/** Where the user is shows as white against grey: the navigation never takes a color. */
private fun tabColor(selected: Boolean): Color = if (selected) RefluxColors.textPrimary else RefluxColors.textSecondary

/** A quiet sign that the library is being updated; it never blocks anything. */
@Composable
private fun ActivityIndicator(activity: PipelineActivity) {
    AnimatedContent(
        targetState = activityLabel(activity),
        transitionSpec = { fadeIn(Motion.controlsShow.tween()) togetherWith fadeOut(Motion.controlsHide.tween()) },
    ) { label ->
        if (label != null) {
            Row(Modifier.padding(end = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(RefluxColors.textSecondary, CircleShape))
                Text(label, style = LocalTypography.current.caption, color = RefluxColors.textSecondary, maxLines = 1)
            }
        }
    }
}

/** What a page is called when it arrives, for a screen reader; a work's or a source's page says its own name. */
private fun accessibleTitle(destination: Destination): String? = when (destination) {
    Destination.Home -> "Home"
    Destination.Movies -> "Movies"
    Destination.Shows -> "TV Shows"
    Destination.Search -> "Search"
    is Destination.Listing -> destination.title
    Destination.Downloads -> "Downloads"
    Destination.Sources -> "Sources"
    Destination.AddSource -> "Add media"
    is Destination.Connect -> "Connect"
    is Destination.SourceLibrary -> null
    Destination.About -> "About Reflux"
    Destination.Notices -> "Third-party software"
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
