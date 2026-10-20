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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
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
import dev.reflux.ui.components.LocalArtworkLoader
import dev.reflux.ui.components.LocalFocusEntry
import dev.reflux.ui.components.LocalFocusScreen
import dev.reflux.ui.components.LocalFocusedControl
import dev.reflux.ui.components.Tab
import dev.reflux.ui.components.absorbsPointer
import dev.reflux.ui.screens.BrowseScreen
import dev.reflux.ui.screens.DetailsScreen
import dev.reflux.ui.screens.FolderPickerPanel
import dev.reflux.ui.screens.HomeScreen
import dev.reflux.ui.screens.IdentifyScreen
import dev.reflux.ui.screens.DownloadsScreen
import dev.reflux.ui.state.libraryState
import dev.reflux.ui.state.offlineState
import dev.reflux.library.downloads
import dev.reflux.ui.screens.OfflinePanel
import dev.reflux.ui.screens.SearchScreen
import dev.reflux.ui.screens.AboutScreen
import dev.reflux.ui.screens.AddSourceScreen
import dev.reflux.ui.screens.NoticeScreen
import dev.reflux.ui.screens.NoticesScreen
import dev.reflux.ui.screens.ConnectScreen
import dev.reflux.ui.screens.SourceSectionsScreen
import dev.reflux.ui.screens.SourcesScreen
import dev.reflux.ui.state.aboutSources
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

/** How many frames a television waits for a new screen to have something to focus. */
private const val FOCUS_PATIENCE_FRAMES = 30

/** The height of the floating navigation bar. */
internal val NavigationBarHeight = 56.dp

/**
 * How much of a screen's top and bottom the floating navigation occupies. Screens start their content
 * below [top] and let it scroll out from under [bottom].
 *
 * On a phone the navigation is at the bottom, within reach of a thumb, and the top only carries what a page
 * needs there; everywhere else it is one bar along the top.
 */
internal data class ChromeInsets(val top: Dp, val bottom: Dp)

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

internal val LocalChromeInsets = staticCompositionLocalOf { ChromeInsets(top = 14.dp + NavigationBarHeight + 22.dp, bottom = 64.dp) }

private fun chromeInsets(compact: Boolean, onSection: Boolean, prompts: Boolean): ChromeInsets = when {
    !compact -> ChromeInsets(top = 14.dp + NavigationBarHeight + 22.dp, bottom = 64.dp)
    // A page reached from somewhere has a way back in its top corner; with a controller in hand, its
    // button prompts sit above the sections, and the page ends clear of them too.
    else -> ChromeInsets(top = if (onSection) 20.dp else 72.dp, bottom = 10.dp + NavigationBarHeight + 28.dp + if (prompts) 52.dp else 0.dp)
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
        val posterWidth by animateFloatAsState(target.posterWidthDp, Motion.inputMorph.tween())
        val focusScale by animateFloatAsState(target.focusScale, Motion.inputMorph.tween())
        val minTarget by animateFloatAsState(target.minTargetDp, Motion.inputMorph.tween())
        val layout = target.copy(posterWidthDp = posterWidth, focusScale = focusScale, minTargetDp = minTarget)
        CompositionLocalProvider(
            LocalLayout provides layout,
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

/** The browsing experience: library screens beneath a floating glass navigation bar. */
@Composable
fun RefluxApp(app: AppState, modifier: Modifier = Modifier) {
    RefluxTheme(app, modifier) {
        val focus = LocalFocusManager.current
        val entry = remember { FocusEntry() }
        val focused = remember { FocusedControl() }
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
            onDispose {
                app.moveFocus = null
                app.clearFocus = null
                app.activateFocused = null
            }
        }
        val screens = rememberSaveableStateHolder()
        entry.screen = app.navigator.current
        val insets = chromeInsets(LocalLayout.current.formFactor == FormFactor.PHONE, app.navigator.current is Destination.Section, app.namesButtons)
        // A television is only ever operated through focus, so something always has it: when a screen
        // appears, or what had focus goes away, it lands where the user left off or on the main action.
        if (LocalLayout.current.formFactor == FormFactor.TV) {
            // A screen that has just appeared starts on its own main action, not on whatever the toolkit
            // found first (the navigation above it).
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
        }
        val page = remember { PageScroll() }
        CompositionLocalProvider(LocalFocusEntry provides entry, LocalFocusedControl provides focused, LocalChromeInsets provides insets, LocalPageScroll provides page) {
            GlassScene(
                Modifier.fillMaxSize().background(RefluxColors.background).trackPointer(app).onFocusChanged { entry.hasFocus = it.hasFocus },
                overlay = { Chrome(app, page) },
            ) {
                // Focus that scrolls a page stops short of the floating navigation.
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
                        Box(Modifier.fillMaxSize().then(if (leaving) Modifier.clearAndSetSemantics {} else Modifier)) {
                            screens.SaveableStateProvider(destination.toString()) {
                                CompositionLocalProvider(LocalFocusScreen provides destination) {
                                    when (destination) {
                                        Destination.Home -> HomeScreen(app)
                                        Destination.Movies -> BrowseScreen(app, "Movies", query = null, shows = false)
                                        Destination.Shows -> BrowseScreen(app, "TV Shows", query = null, shows = true)
                                        Destination.Search -> SearchScreen(app)
                                        is Destination.Listing -> BrowseScreen(app, destination.title, destination.query, shows = false)
                                        is Destination.Details -> DetailsScreen(app, destination.itemId)
                                        is Destination.Identify -> IdentifyScreen(app, destination.itemId)
                                        Destination.Downloads -> DownloadsScreen(app)
                                        Destination.Sources -> SourcesScreen(app)
                                        Destination.AddSource -> AddSourceScreen(app)
                                        is Destination.Connect -> ConnectScreen(app, destination)
                                        is Destination.SourceSections -> SourceSectionsScreen(app, destination.sourceId)
                                        Destination.About -> AboutScreen(app)
                                        Destination.Notices -> NoticesScreen(app)
                                        is Destination.Notice -> NoticeScreen(app, destination.name)
                                    }
                                }
                            }
                        }
                    }
                }
                if (LocalLayout.current.formFactor == FormFactor.TV) {
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

/** Everything that floats above the screens: navigation, the offline sheet, button prompts, and notices. */
@Composable
private fun Chrome(app: AppState, page: PageScroll) {
    val layout = LocalLayout.current
    val compact = layout.formFactor == FormFactor.PHONE
    val margin = Modifier.fillMaxSize().padding(horizontal = layout.safeMarginDp.dp)
    // Over a page that opens on artwork the bar is just its labels, so the artwork reaches the top of the
    // window; it frosts over once the page moves under it, and everywhere else it always is.
    val current = app.navigator.current
    val clear = (current == Destination.Home || current is Destination.Details) && !page.scrolled
    Box(Modifier.fillMaxSize()) {
        Box(margin) {
            if (compact) CompactNavigation(app) else NavigationBar(app, clear, Modifier.align(Alignment.TopCenter).padding(top = 14.dp))
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
            // On a phone the sheet rises from the bottom, under the thumb; elsewhere it hangs from the navigation.
            AnimatedVisibility(
                visible = app.offlineOpen,
                modifier = if (compact) {
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp + NavigationBarHeight + 12.dp)
                } else {
                    Modifier.align(Alignment.TopEnd).padding(top = 14.dp + NavigationBarHeight + 10.dp)
                },
                enter = fadeIn(Motion.controlsShow.tween()) + slideInVertically(Motion.controlsShow.tween()) { if (compact) it / 8 else -it / 12 },
                exit = fadeOut(Motion.controlsHide.tween()),
            ) {
                OfflinePanel(app)
            }
            // A notice never covers an open sheet: on a phone, where the sheet rises from the bottom, it is said at the top instead.
            val aboveSheet = compact && app.offlineOpen
            AnimatedContent(
                targetState = app.notice,
                modifier = if (aboveSheet) {
                    Modifier.align(Alignment.TopCenter).padding(top = 12.dp + 44.dp + 12.dp)
                } else {
                    Modifier.align(Alignment.BottomCenter).padding(bottom = if (compact) 10.dp + NavigationBarHeight + 14.dp else 32.dp)
                },
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

/**
 * Navigation on a phone: the sections along the bottom, where a thumb reaches them, and only what a page
 * needs at the top (the way back, the media sources, a sign of the library being updated).
 */
@Composable
private fun BoxScope.CompactNavigation(app: AppState) {
    val navigator = app.navigator
    val entry = LocalFocusEntry.current
    val activity by app.activity.collectAsState()
    Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (navigator.current !is Destination.Section) IconButton(Icon.BACK, "Back", { navigator.back() }, size = 44.dp, glass = true)
        Spacer(Modifier.weight(1f))
        ActivityIndicator(activity)
        // Search has its field across the top; the downloads and the sources are a tap away from any other section.
        if (navigator.current is Destination.Section && navigator.current != Destination.Search) {
            DownloadsButton(app, size = 44.dp, glass = true)
            IconButton(Icon.FOLDER, "Media sources", { app.toggleSources() }, size = 44.dp, glass = true)
        } else if (navigator.current == Destination.Downloads) {
            DownloadsButton(app, size = 44.dp, glass = true)
        } else if (navigator.current.aboutSources) {
            // Where the user is, said by the one control that leads here.
            IconButton(Icon.FOLDER, "Media sources", { app.toggleSources() }, size = 44.dp, glass = true, selected = true)
        }
    }
    // While typing, the keyboard owns the bottom of the screen: the sections step aside rather than sit on
    // top of the keyboard and cover what is being typed into, or the button that comes after it.
    AnimatedVisibility(!app.textInputActive, Modifier.align(Alignment.BottomCenter), enter = fadeIn(Motion.controlsShow.tween()), exit = fadeOut(Motion.controlsHide.tween())) {
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
                Modifier.align(Alignment.CenterHorizontally).widthIn(max = 420.dp).fillMaxWidth().height(NavigationBarHeight).glass(MaterialRole.FLOATING, CircleShape).absorbsPointer()
                    .onFocusChanged { entry?.chrome = SemanticAction.NAVIGATE_UP.takeIf { _ -> it.hasFocus } }.padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tab("Home", navigator.highlighted == Destination.Home) { navigator.show(Destination.Home) }
                Tab("Movies", navigator.highlighted == Destination.Movies) { navigator.show(Destination.Movies) }
                Tab("Shows", navigator.highlighted == Destination.Shows) { navigator.show(Destination.Shows) }
                IconButton(Icon.SEARCH, "Search", { navigator.show(Destination.Search) }, tint = tabColor(navigator.highlighted == Destination.Search))
            }
        }
    }
}

@Composable
private fun NavigationBar(app: AppState, clear: Boolean, modifier: Modifier = Modifier) {
    val entry = LocalFocusEntry.current
    val activity by app.activity.collectAsState()
    val frost by animateFloatAsState(if (clear) 0f else 1f, Motion.navigation.tween())
    Box(modifier.fillMaxWidth().height(NavigationBarHeight)) {
        Box(Modifier.matchParentSize().graphicsLayer { alpha = frost }.glass(MaterialRole.FLOATING, CircleShape))
        NavigationBarContent(app, activity, Modifier.matchParentSize().absorbsPointer()
            .onFocusChanged { entry?.chrome = SemanticAction.NAVIGATE_DOWN.takeIf { _ -> it.hasFocus } }.padding(horizontal = 10.dp))
    }
}

@Composable
private fun NavigationBarContent(app: AppState, activity: PipelineActivity, modifier: Modifier) {
    val navigator = app.navigator
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        // A remote has a Back button of its own; on a television an arrow for it would only be one more stop.
        if (navigator.current !is Destination.Section && LocalLayout.current.formFactor != FormFactor.TV) {
            IconButton(Icon.BACK, "Back", { navigator.back() })
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text("Reflux", Modifier.padding(start = 4.dp, end = 22.dp), style = LocalTypography.current.title.copy(letterSpacing = (-0.02).em))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            Tab("Home", navigator.highlighted == Destination.Home) { navigator.show(Destination.Home) }
            Tab("Movies", navigator.highlighted == Destination.Movies) { navigator.show(Destination.Movies) }
            Tab("Shows", navigator.highlighted == Destination.Shows) { navigator.show(Destination.Shows) }
        }
        Spacer(Modifier.weight(1f))
        // With a controller in hand, the bar says what its buttons do here, in that controller's own terms.
        AnimatedVisibility(
            visible = app.namesButtons,
            enter = fadeIn(Motion.inputMorph.tween()),
            exit = fadeOut(Motion.controlsHide.tween()),
        ) {
            val family = app.input.controllerFamily ?: ControllerFamily.GENERIC
            ButtonPrompts(
                listOfNotNull(
                    InputMapping.confirmButton(family) to "Select",
                    (InputMapping.cancelButton(family) to if (app.offlineOpen) "Close" else "Back").takeIf { app.offlineOpen || navigator.canGoBack },
                    (GamepadButton.START to "Sources").takeIf { navigator.current != Destination.Sources },
                ),
                family,
                Modifier.padding(end = 14.dp),
                floating = false,
            )
        }
        ActivityIndicator(activity)
        IconButton(Icon.SEARCH, "Search", { navigator.show(Destination.Search) }, tint = tabColor(navigator.highlighted == Destination.Search))
        DownloadsButton(app)
        IconButton(Icon.FOLDER, "Media sources", { app.toggleSources() }, tint = tabColor(navigator.current.aboutSources), selected = navigator.current.aboutSources)
    }
}

/**
 * The way to the Downloads page, where this platform keeps copies and there is anything to keep or kept. While
 * something is being downloaded a small dot says so; it does not move.
 */
@Composable
private fun DownloadsButton(app: AppState, size: Dp = 40.dp, glass: Boolean = false) {
    val offline = app.platform.offline ?: return
    val offered by app.libraryState { offline.possible() || downloads().isNotEmpty() }
    val busy by app.offlineState { downloads().any { it.active } }
    if (offered != true) return
    val here = app.navigator.current == Destination.Downloads
    Box {
        IconButton(Icon.DOWNLOAD, if (busy == true) "Downloads, downloading" else "Downloads", { app.toggleDownloads() }, size = size, glass = glass, tint = tabColor(here), selected = here)
        if (busy == true) Box(Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 7.dp).size(7.dp).background(RefluxColors.textPrimary, CircleShape))
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

internal fun activityLabel(activity: PipelineActivity): String? = when (activity) {
    PipelineActivity.Idle -> null
    is PipelineActivity.Scanning -> "Scanning ${activity.sourceName}"
    PipelineActivity.Probing -> "Reading media details"
    PipelineActivity.FetchingMetadata -> "Finding titles and details"
    PipelineActivity.FetchingArtwork -> "Fetching artwork"
}
