package dev.reflux.ui.player

import dev.reflux.ui.theme.States
import dev.reflux.ui.theme.Shapes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import dev.reflux.ui.state.UiClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.core.input.InputMapping
import dev.reflux.core.input.InputModality
import dev.reflux.core.input.SemanticAction
import dev.reflux.core.model.Episode
import dev.reflux.core.playback.PlayerState
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.playback.SkipSegment
import dev.reflux.core.playback.SkipSegments
import dev.reflux.design.FormFactor
import dev.reflux.design.LayoutProfile
import dev.reflux.design.PlayerChrome
import dev.reflux.design.PlayerChromeSpec
import dev.reflux.design.PlayerChromeTier
import dev.reflux.design.MaterialRole
import dev.reflux.design.Materials
import dev.reflux.design.Motion
import dev.reflux.ui.RefluxTheme
import dev.reflux.ui.components.Button
import dev.reflux.ui.components.ButtonPrompts
import dev.reflux.ui.components.ButtonStyle
import dev.reflux.ui.components.FocusRingReach
import dev.reflux.ui.components.Icon
import dev.reflux.ui.components.IconButton
import dev.reflux.ui.components.LocalAccessibility
import dev.reflux.ui.components.Pressable
import dev.reflux.ui.components.Slider
import dev.reflux.ui.components.SliderStyle
import dev.reflux.ui.state.FullscreenMode
import dev.reflux.ui.components.Tab
import dev.reflux.ui.components.absorbsPointer
import dev.reflux.ui.components.announces
import dev.reflux.ui.components.pane
import dev.reflux.ui.components.traversal
import dev.reflux.ui.components.Traversal
import dev.reflux.ui.screens.moveToItem
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Formatting
import dev.reflux.ui.state.MenuOption
import dev.reflux.ui.state.PlaybackError
import dev.reflux.ui.state.PlaybackSession
import dev.reflux.ui.state.PlayerMenu
import dev.reflux.ui.state.UpNext
import dev.reflux.ui.theme.LocalLayout
import dev.reflux.ui.theme.LocalTypography
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.Text
import dev.reflux.ui.theme.glass
import dev.reflux.ui.theme.toColor
import dev.reflux.ui.theme.tween
import dev.reflux.ui.trackPointer
import kotlinx.coroutines.delay
import kotlin.math.ceil

/** The video cannot be sampled for blur, so player glass is a denser tint of the same material. */
private val playerTint = Materials.spec(MaterialRole.FLOATING).tint.toColor().copy(alpha = 0.72f)

/**
 * The player's controls, drawn over the platform's video surface. The picture is the interface and the controls are
 * temporary: a title and the way out, a light transport, and one row with the timeline and the actions, which leave
 * after a pause while the video plays. Chapters, tracks, and speed are one step deeper, in lists that work the same
 * with a pointer, a keyboard, or a controller.
 *
 * What is drawn is one derived value, [chrome], and nothing else decides it (docs/planning/M10-PLAYER-SPEC.md, section
 * 7): the player's state and the viewer's last doing go in, one deadline is waited for, and each [ChromeMode] has the
 * layers it may draw. While opening or waiting for data it is the Reflux loader and the way out, and nothing that
 * cannot act on a wait.
 *
 * The background stays transparent so the shell's video shows through.
 *
 * @param hiddenCursor the pointer icon to use while the controls are hidden, where the platform has one.
 */
@Composable
fun PlayerOverlay(app: AppState, session: PlaybackSession, modifier: Modifier = Modifier, hiddenCursor: PointerIcon? = null) {
    RefluxTheme(app, modifier) {
        val state by session.state.collectAsState()
        val layout = LocalLayout.current
        val screenReader = LocalAccessibility.current.screenReader
        // Someone who asked the system for longer to act gets it here too.
        val patience = LocalAccessibilityManager.current?.calculateRecommendedTimeoutMillis(
            Motion.CONTROLS_AUTO_HIDE_MS, containsIcons = true, containsText = true, containsControls = true,
        ) ?: Motion.CONTROLS_AUTO_HIDE_MS

        // What the chrome depends on: facts of the playback, and what the viewer did last.
        var revealedUntil by remember { mutableLongStateOf(0L) }
        var dismissed by remember { mutableStateOf(false) }
        var now by remember { mutableLongStateOf(UiClock.nowMs()) }
        var bufferingSince by remember { mutableStateOf<Long?>(null) }
        var opening by remember { mutableStateOf(false) }
        var scrubbing by remember { mutableStateOf(false) }
        var focusInside by remember { mutableStateOf(false) }
        val hover = remember { MutableInteractionSource() }
        val pointerOver by hover.collectIsHoveredAsState()
        var shownAtPress by remember { mutableStateOf(true) }
        var lastPointerMs by remember { mutableLongStateOf(LONG_AGO) }

        // Any interaction reveals the controls for the viewer's patience, and gives back what a touch put away.
        LaunchedEffect(session.interactions, patience) {
            now = UiClock.nowMs()
            revealedUntil = now + patience
            dismissed = false
        }
        LaunchedEffect(state.status) {
            bufferingSince = if (state.status == PlayerStatus.BUFFERING) bufferingSince ?: UiClock.nowMs() else null
            // The controls are shown once when an opening ends, so the viewer sees what they have.
            if (state.status == PlayerStatus.LOADING) {
                opening = true
            } else if (opening) {
                opening = false
                now = UiClock.nowMs()
                revealedUntil = now + patience
                dismissed = false
            }
        }

        val facts = ChromeFacts(session.presentation, state.status, bufferingSince, session.error != null, session.upNext != null, app.systemTransition)
        val pins = buildSet {
            if (state.status == PlayerStatus.PAUSED) add(Pin.PAUSED)
            if (session.menu != null) add(Pin.MENU)
            // A screen reader finds controls by reading them: controls that left could never be found again, since the
            // movement that brings them back is not something it makes.
            if (screenReader) add(Pin.SCREEN_READER)
            if (focusInside && layout.showsFocus) add(Pin.FOCUS_IN_CONTROLS)
            if (pointerOver) add(Pin.POINTER_OVER_CONTROLS)
            if (scrubbing) add(Pin.SCRUBBING)
            if (session.seekPreview != null) add(Pin.SEEK_HOLD)
        }
        val hold = ChromeHold(revealedUntil, pins, dismissed)
        // The one timer: the next moment the chrome changes by itself, and nothing else is waited for.
        LaunchedEffect(facts, hold) {
            now = UiClock.nowMs()
            nextChange(facts, hold, now)?.let { at ->
                delay((at - now).coerceAtLeast(0) + 1)
                now = UiClock.nowMs()
            }
        }
        val mode = chrome(facts, hold, now)
        // What the pointer handlers below ask: the mode as it is at the moment of the press, not as it was when they started.
        val currentMode by rememberUpdatedState(mode)
        val layers = mode.layers()
        // A list left open under a wait would be open and not drawn.
        LaunchedEffect(mode) { if (mode == ChromeMode.LOADING) session.closeMenu() }

        BoxWithConstraints(
            Modifier.fillMaxSize()
                // Said as the player opens, and when it moves on to the next episode; the page behind has gone.
                .pane(if (session.title.isEmpty()) "Player" else "Playing ${session.title}")
                .trackPointer(app)
                .pointerInput(session) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type != PointerEventType.Move && event.type != PointerEventType.Press && event.type != PointerEventType.Enter) continue
                            // Pointer movement is continuous; a few reports a second are enough to keep controls up.
                            val at = event.changes.firstOrNull()?.uptimeMillis ?: continue
                            if (event.type == PointerEventType.Press) shownAtPress = currentMode == ChromeMode.SHOWN
                            if (at - lastPointerMs >= POINTER_REPORT_MS || event.type == PointerEventType.Press) {
                                lastPointerMs = at
                                session.touch()
                            }
                        }
                    }
                }
                .pointerInput(session) {
                    // The wheel is the volume, unless a list under the pointer used it to scroll.
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull() ?: continue
                            if (event.type != PointerEventType.Scroll || change.isConsumed || change.scrollDelta.y == 0f) continue
                            session.handle(if (change.scrollDelta.y < 0f) SemanticAction.VOLUME_UP else SemanticAction.VOLUME_DOWN)
                            change.consume()
                        }
                    }
                }
                .pointerInput(session) {
                    var lastTapMs = LONG_AGO
                    detectTapGestures { position ->
                        val at = lastPointerMs
                        val again = at - lastTapMs <= DOUBLE_CLICK_MS
                        if (session.menu != null) {
                            session.closeMenu()
                        } else if (session.error != null) {
                            // Only the failure's own buttons act.
                        } else if (app.input.modality == InputModality.TOUCH) {
                            // A finger covers what it taps: a tap shows the controls or puts them away, and
                            // a second tap on a side of the picture skips back or ahead.
                            val side = position.x / size.width
                            when {
                                again && side < 1f / 3 -> session.handle(SemanticAction.SEEK_BACKWARD)
                                again && side > 2f / 3 -> session.handle(SemanticAction.SEEK_FORWARD)
                                // A screen reader's controls stay, whatever brushes the screen.
                                shownAtPress && !app.platform.accessibility.screenReader -> {
                                    now = UiClock.nowMs()
                                    revealedUntil = 0L
                                    dismissed = true
                                }
                            }
                        } else {
                            // Click toggles playback at once; a second click soon after also toggles fullscreen.
                            session.handle(SemanticAction.PLAY_PAUSE)
                            if (again) session.handle(SemanticAction.FULLSCREEN)
                        }
                        lastTapMs = at
                    }
                }
                .then(if (mode == ChromeMode.HIDDEN && hiddenCursor != null) Modifier.pointerHoverIcon(hiddenCursor) else Modifier),
        ) {
            val error = session.error
            // How large the controls are for the room this window gives them (a desktop's; a handheld's and a television's stay as they are).
            val chrome = chromeOf(app, layout, maxWidth.value, maxHeight.value)
            // Subtitles move above the bar while it shows, rather than being read through it.
            var bar by remember { mutableFloatStateOf(0f) }
            val clearance = if (mode == ChromeMode.SHOWN) bar else 0f
            LaunchedEffect(clearance) { session.keepSubtitlesClear(clearance) }

            // The controls fade in and out as one layer. A change of mode (a wait begins, the episode ends) is at once, so
            // nothing of the last mode is left drawn beside the next.
            val fading = mode == ChromeMode.HIDDEN
            val alpha by animateFloatAsState(
                if (mode == ChromeMode.HIDDEN || mode == ChromeMode.NONE) 0f else 1f,
                if (mode == ChromeMode.HIDDEN) Motion.controlsHide.tween() else Motion.controlsShow.tween(),
            )
            val drawn = if (fading && alpha > 0.01f) ChromeMode.SHOWN else mode
            // A back gesture on its way: the controls go first, and the picture, which the shell shrinks, is what is left.
            val preview = app.backPreview?.let { 1f - minOf(1f, it / 0.4f) } ?: 1f
            if (drawn != ChromeMode.HIDDEN && drawn != ChromeMode.NONE) {
                Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = (if (fading) alpha else 1f) * preview }.onFocusChanged { focusInside = it.hasFocus }) {
                    Controls(app, session, state, drawn, hover, chrome, { scrubbing = it }) { bar = it }
                }
            }
            if (layers.loader) Spinner(Modifier.align(Alignment.Center))
            // Said as it happens, from a node that was already here.
            Box(Modifier.announces(if (mode == ChromeMode.LOADING) (if (bufferingSince != null) "Buffering" else "Loading ${session.title}") else null))

            // Kept off the screen's edges where the window is narrower than the notice can be.
            Box(Modifier.align(Alignment.TopCenter).padding(top = 28.dp).padding(horizontal = layout.safeMarginDp.dp).announces(app.notice)) {
                AnimatedVisibility(
                    visible = app.notice != null,
                    enter = fadeIn(Motion.controlsShow.tween()),
                    exit = fadeOut(Motion.controlsHide.tween()),
                ) {
                    // The page's own notice is beneath the video, so what is said while playing is said here.
                    var shown by remember { mutableStateOf("") }
                    app.notice?.let { shown = it }
                    Box(Modifier.widthIn(max = 560.dp).glass(MaterialRole.MODAL, CircleShape, tint = playerTint.copy(alpha = 0.92f)).absorbsPointer().padding(horizontal = 22.dp, vertical = 12.dp)) {
                        Text(shown, style = LocalTypography.current.bodyStrong, maxLines = 2)
                    }
                }
            }

            val remote = app.platform.systemVolume && layout.modality == InputModality.DIRECTIONAL
            val corner = Modifier.align(Alignment.BottomEnd).padding(end = layout.safeMarginDp.dp, bottom = if (mode == ChromeMode.SHOWN) chrome.cornerLiftDp.dp else 40.dp)
            val prompt = session.upNext
            val segment = session.skippable(state)
            when {
                layers.failure && error != null -> Failure(session, error, Modifier.align(Alignment.Center).padding(horizontal = layout.safeMarginDp.dp))
                layers.upNext && prompt != null -> UpNextCard(session, prompt, corner)
                // With a remote, up is what takes the offer; the arrow says so.
                layers.skipOffer && segment != null && session.menu == null ->
                    Button(skipLabel(segment), { session.skip(segment) }, corner, icon = Icon.CHEVRON_UP.takeIf { remote }, style = ButtonStyle.PRIMARY)
            }
        }
    }
}

/** Whether Play/Pause would pause: playback that is waiting for data or for a seek still wants to run. */
private val PlayerState.pauses: Boolean get() = status == PlayerStatus.PLAYING || status == PlayerStatus.BUFFERING

private fun skipLabel(segment: SkipSegment): String = when (segment.kind) {
    SkipSegment.Kind.INTRO -> "Skip intro"
    SkipSegment.Kind.RECAP -> "Skip recap"
    SkipSegment.Kind.CREDITS -> "Skip credits"
}

private fun menuIcon(menu: PlayerMenu): Icon = when (menu) {
    PlayerMenu.CHAPTERS -> Icon.CHAPTERS
    PlayerMenu.AUDIO -> Icon.AUDIO
    PlayerMenu.SUBTITLES -> Icon.SUBTITLES
    PlayerMenu.SPEED, PlayerMenu.EPISODES, PlayerMenu.INFO -> error("not an icon in the bar")
}

/**
 * What [mode] draws (its [layers]): the way out and what is playing; in [ChromeMode.SHOWN] also the transport in the middle
 * of the picture, one row at the bottom, and an open list.
 */
@Composable
private fun Controls(
    app: AppState,
    session: PlaybackSession,
    state: PlayerState,
    mode: ChromeMode,
    hover: MutableInteractionSource,
    chrome: PlayerChromeSpec,
    onScrubbing: (Boolean) -> Unit,
    onBar: (Float) -> Unit,
) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val layout = LocalLayout.current
    val margin = (layout.safeMarginDp + chrome.sideMarginDp).dp
    val layers = mode.layers()
    val menus = session.menus(state)
    val open = session.menu?.takeIf { it in menus }
    val failed = session.error != null
    val accessibility = app.platform.accessibility
    // Where transparency is not wanted the bar is a plate, and the scrims are denser.
    val solid = accessibility.reduceTransparency || accessibility.highContrast
    // What this device shows, and where: a phone or tablet in hand has the transport under the thumbs and
    // its own volume keys; a television has a remote for all of it; a desktop has a pointer and a window.
    val controls = PlayerControls.of(
        layout.formFactor,
        layout.modality,
        app.platform.systemVolume,
        app.platform.fullscreenMode,
        app.platform.fullscreen,
        portrait = maxHeight > maxWidth,
        hasPrevious = session.previous != null,
        hasNext = session.next != null,
        menus = menus,
    )
    val compact = layout.formFactor == FormFactor.PHONE
    val centre = controls.transport == Transport.CENTRE
    val wide = maxWidth >= 560.dp
    // Lists the player's own keys reach; named in a pointer's label, and they are the same on every list.
    val listsKey = "O"
    val shown = mode == ChromeMode.SHOWN
    // Scrims carry the title and controls over bright scenes; the picture between them is left alone.
    if (shown) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.18f).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = if (solid) 0.85f else 0.55f), Color.Transparent))))
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.32f).background(Brush.verticalGradient(0f to Color.Transparent, 0.5f to Color.Black.copy(alpha = if (solid) 0.6f else 0.34f), 1f to Color.Black.copy(alpha = if (solid) 0.85f else 0.72f))))
    } else {
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.14f).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = if (solid) 0.85f else 0.5f), Color.Transparent))))
    }

    // The way out, and what is playing: a phone keeps the title at the top, beside it, clear of the transport in the
    // middle of the picture; elsewhere it is above the bar, and comes to the top while the bar is not there.
    val minimizes = controls.back && layers.minimize && app.miniPlayerAvailable
    Row(
        Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = margin, vertical = chrome.topPaddingDp.dp).hoverable(hover),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (controls.back) {
            // Where there is a mini-player, leaving the player steps aside into it and ending is its own button; a failure is answered here, not put aside.
            if (minimizes) IconButton(Icon.CHEVRON_DOWN, "Minimize player", { session.collapse() }, size = chrome.buttonDp.dp, shortcut = "Esc")
            else IconButton(Icon.BACK, "Back to library", { session.close() }, size = chrome.buttonDp.dp, shortcut = "Esc")
        }
        if (compact || !shown) NowPlaying(session, compact = true, chrome, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
        if (shown && app.namesButtons && !failed) {
            val family = app.input.controllerFamily ?: ControllerFamily.GENERIC
            ButtonPrompts(
                if (open != null) {
                    listOf(InputMapping.confirmButton(family) to "Choose", InputMapping.cancelButton(family) to "Close")
                } else {
                    listOfNotNull(
                        InputMapping.confirmButton(family) to if (state.pauses) "Pause" else "Play",
                        InputMapping.cancelButton(family) to if (minimizes) "Minimize" else "Back",
                        GamepadButton.START to "Options",
                        (GamepadButton.SHOULDER_RIGHT to "Next").takeIf { session.next != null },
                    )
                },
                family,
            )
        }
        if (minimizes) IconButton(Icon.CLOSE, "Stop playing", { session.close() }, size = chrome.buttonDp.dp, tint = RefluxColors.contentSecondary)
    }

    // The transport: glyphs in generous touch targets, with a soft shadow behind them for legibility, not discs. While a
    // list is open it has the screen's attention, and on a touch screen it can be taller than the space beneath the
    // transport, so the transport steps aside until the list closes.
    if (layers.centreTransport && centre && open == null) {
        // A tablet's picture is larger and its thumbs further apart; the cluster grows with it, not with the screen.
        val seek = if (compact) 56.dp else 64.dp
        val play = if (compact) 72.dp else 84.dp
        Box(Modifier.align(Alignment.Center), contentAlignment = Alignment.Center) {
            Box(Modifier.size(if (compact) 240.dp else 300.dp, if (compact) 120.dp else 150.dp).background(Brush.radialGradient(listOf(Color.Black.copy(alpha = if (solid) 0.5f else 0.3f), Color.Transparent))))
            Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 28.dp else 44.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(Icon.REWIND, "Back 10 seconds", { session.handle(SemanticAction.SEEK_BACKWARD) }, size = seek, iconSize = if (compact) 28.dp else 32.dp, tint = RefluxColors.contentSecondary)
                IconButton(
                    if (state.pauses) Icon.PAUSE else Icon.PLAY,
                    if (state.pauses) "Pause" else "Play",
                    { session.handle(SemanticAction.PLAY_PAUSE) },
                    size = play,
                    iconSize = if (compact) 40.dp else 48.dp,
                )
                IconButton(Icon.FORWARD, "Forward 10 seconds", { session.handle(SemanticAction.SEEK_FORWARD) }, size = seek, iconSize = if (compact) 28.dp else 32.dp, tint = RefluxColors.contentSecondary)
            }
        }
    }

    if (!layers.bar) return@BoxWithConstraints
    @Composable
    fun Timeline(modifier: Modifier) = Timeline(session, state, remote = controls.transport == Transport.REMOTE, chrome, onScrubbing, modifier)

    @Composable
    fun Transport() {
        if (controls.previous) IconButton(Icon.PREVIOUS, "Previous", { session.handle(SemanticAction.PREVIOUS) }, size = chrome.buttonDp.dp, shortcut = "Page Up")
        if (controls.transport == Transport.BAR) {
            IconButton(Icon.REWIND, "Back 10 seconds", { session.handle(SemanticAction.SEEK_BACKWARD) }, size = chrome.buttonDp.dp, shortcut = "←")
            IconButton(
                if (state.pauses) Icon.PAUSE else Icon.PLAY,
                if (state.pauses) "Pause" else "Play",
                { session.handle(SemanticAction.PLAY_PAUSE) },
                size = chrome.playButtonDp.dp,
                iconSize = chrome.playGlyphDp.dp.takeIf { chrome.tier != PlayerChromeTier.BASELINE },
                shortcut = "Space",
            )
            IconButton(Icon.FORWARD, "Forward 10 seconds", { session.handle(SemanticAction.SEEK_FORWARD) }, size = chrome.buttonDp.dp, shortcut = "→")
        }
        if (controls.next) IconButton(Icon.NEXT, "Next episode", { session.playNext() }, size = chrome.buttonDp.dp, shortcut = "Page Down")
    }

    @Composable
    fun Actions() {
        if (controls.volume) {
            IconButton(if (state.muted || state.volume == 0) Icon.VOLUME_MUTED else Icon.VOLUME, if (state.muted) "Unmute" else "Mute", { session.handle(SemanticAction.MUTE) }, size = chrome.buttonDp.dp, shortcut = "M")
            Slider(
                fraction = if (state.muted) 0f else state.volume / 100f,
                description = "Volume",
                onChange = { session.setVolume((it * 100).toInt()) },
                modifier = Modifier.width(chrome.volumeWidthDp.dp).padding(end = 8.dp),
            )
        }
        controls.lists.forEach { menu ->
            val tint = if (menu == open || (menu == PlayerMenu.SUBTITLES && state.selectedSubtitleId != null)) RefluxColors.contentPrimary else RefluxColors.contentSecondary
            if (menu == PlayerMenu.SPEED) {
                Button(
                    if (state.speed == 1.0) "1×" else Formatting.speed(state.speed),
                    { session.toggleMenu(menu) },
                    Modifier.semantics {
                        stateDescription = (if (state.speed == 1.0) "Normal" else Formatting.speed(state.speed)) + ", " + if (menu == open) "open" else "closed"
                    },
                    style = ButtonStyle.QUIET,
                    // What the button shows is only the value: its name is what a screen reader was missing.
                    name = "Playback speed",
                )
            } else {
                IconButton(menuIcon(menu), menu.title, { session.toggleMenu(menu) }, size = chrome.buttonDp.dp, tint = tint, shortcut = listsKey, expanded = menu == open)
            }
        }
        controls.more?.let { first ->
            // The rest of the lists, one tap away; their tabs lead from one to another.
            val lit = open != null && open !in controls.lists
            IconButton(Icon.MORE, "More options", { if (lit) session.closeMenu() else session.toggleMenu(first) }, size = chrome.buttonDp.dp, tint = if (lit) RefluxColors.contentPrimary else RefluxColors.contentSecondary, shortcut = listsKey, expanded = lit)
        }
        if (controls.fullscreen) {
            IconButton(
                if (app.platform.fullscreen) Icon.FULLSCREEN_EXIT else Icon.FULLSCREEN,
                if (app.platform.fullscreen) "Exit fullscreen" else "Fullscreen",
                { session.handle(SemanticAction.FULLSCREEN) },
                size = chrome.buttonDp.dp,
                shortcut = "F",
            )
        }
    }

    Column(
        Modifier.align(Alignment.BottomCenter).padding(horizontal = margin).padding(bottom = if (compact) 8.dp else chrome.bottomPaddingDp.dp).widthIn(max = chrome.barMaxWidthDp.dp)
            .onGloballyPositioned { bottom ->
                // The share of the window this block covers (what is playing, the bar, an open list), and a
                // little air: subtitles are kept above it.
                val window = bottom.findRootCoordinates().size.height.toFloat()
                if (window > 0f) onBar(((window - bottom.positionInRoot().y) / window + 0.02f).coerceIn(0f, 1f))
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Bottom) {
            if (!compact) NowPlaying(session, compact = false, chrome, Modifier.weight(1f).padding(start = 6.dp)) else Spacer(Modifier.weight(1f))
            if (layers.menus && open != null) MenuPanel(session, menus, open, session.options(open, state))
        }
        // One row, on the scrim: no panel of glass over the picture. Clicks on it must not reach the video's click-to-pause.
        Column(
            Modifier.fillMaxWidth().hoverable(hover).absorbsPointer()
                .then(if (solid) Modifier.background(RefluxColors.surface, Shapes.large).padding(horizontal = 12.dp, vertical = 6.dp) else Modifier.padding(horizontal = 4.dp)),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            when {
                controls.listHints -> {
                    Timeline(Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            if (state.status == PlayerStatus.PAUSED) Text("Paused", style = LocalTypography.current.captionStrong, color = RefluxColors.contentSecondary, maxLines = 1)
                        }
                        // Down opens these; the one that is open is the one lit. To the D-pad they are only names, so
                        // they take no focus; a screen reader, which goes through what is on screen, opens them directly.
                        Icon(Icon.CHEVRON_DOWN, size = 12.dp, tint = RefluxColors.contentTertiary)
                        menus.forEach { menu ->
                            Text(
                                menu.title,
                                Modifier.semantics {
                                    role = Role.Tab
                                    selected = menu == open
                                    onClick {
                                        session.toggleMenu(menu)
                                        true
                                    }
                                },
                                style = LocalTypography.current.captionStrong,
                                color = if (menu == open) RefluxColors.contentPrimary else RefluxColors.contentSecondary,
                                maxLines = 1,
                            )
                        }
                    }
                }
                wide -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(chrome.buttonGapDp.dp)) {
                    Transport()
                    Timeline(Modifier.weight(1f).padding(horizontal = 8.dp))
                    Actions()
                }
                else -> {
                    Timeline(Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Transport()
                        Spacer(Modifier.weight(1f))
                        Actions()
                    }
                }
            }
        }
    }
}

/** The title of what is playing, with the episode or year beneath it. */
@Composable
private fun NowPlaying(session: PlaybackSession, compact: Boolean, chrome: PlayerChromeSpec, modifier: Modifier = Modifier) {
    val typography = LocalTypography.current
    val title = if (compact) typography.title else typography.headline.let { if (chrome.tier == PlayerChromeTier.BASELINE) it else it.copy(fontSize = chrome.titleSp.sp, lineHeight = (chrome.titleSp * 1.2f).sp) }
    val second = if (compact) typography.caption else typography.metadata.let { if (chrome.tier == PlayerChromeTier.BASELINE) it else it.copy(fontSize = chrome.subtitleSp.sp) }
    // One stop: what is playing, whichever way the two lines happen to be laid out.
    Column(modifier.semantics(mergeDescendants = true) { }, verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 4.dp)) {
        Text(session.title, style = title, maxLines = 1)
        session.subtitle?.let { Text(it, style = second, color = RefluxColors.contentSecondary, maxLines = 1) }
    }
}

/**
 * Position, scrubber with chapter marks, and time remaining. Dragging previews the time before seeking.
 *
 * @param remote where no transport is on screen (a television): the scrubber then carries what the remote's own
 * keys do, for a screen reader, which has the keys for itself while it is on.
 */
@Composable
private fun Timeline(session: PlaybackSession, state: PlayerState, remote: Boolean, chrome: PlayerChromeSpec, onScrubbing: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val duration = state.durationMs?.takeIf { it > 0 }
    var preview by remember { mutableStateOf<Float?>(null) }
    // Where a held seek key would land, or where the thumb is dragged to, is what the clock says; the position otherwise.
    val position = preview?.let { fraction -> duration?.let { (it * fraction).toLong() } } ?: session.seekPreview ?: state.positionMs
    val clock = LocalTypography.current.captionStrong.let {
        if (chrome.tier == PlayerChromeTier.BASELINE) it else it.copy(fontSize = chrome.clockSp.sp)
    }
    val style = SliderStyle(
        chrome.trackDp, chrome.trackActiveDp, chrome.thumbDp, chrome.thumbActiveDp, chrome.timelineHitDp, chrome.inactiveTrackAlpha, chrome.trackHalo,
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(chrome.clockGapDp.dp)) {
        // The slider says where it is and how long it all is, so the two clocks beside it are not said again.
        Text(Formatting.clock(position), Modifier.clearAndSetSemantics { }, style = clock, color = RefluxColors.contentSecondary, maxLines = 1)
        Slider(
            fraction = if (duration == null) 0f else (position.toFloat() / duration).coerceIn(0f, 1f),
            description = "Timeline",
            color = RefluxColors.progress,
            onChange = {
                preview = it
                onScrubbing(true)
                session.touch()
            },
            onChangeFinished = { fraction ->
                preview = null
                onScrubbing(false)
                duration?.let { session.seekTo((it * fraction).toLong()) }
            },
            modifier = Modifier.weight(1f).then(if (remote) Modifier.semantics { customActions = transportActions(session, state) } else Modifier),
            valueText = duration?.let { Formatting.clock(position) + " of " + Formatting.clock(it) },
            style = style,
            marks = if (duration == null) emptyList() else state.chapters.map { it.startMs.toFloat() / duration }.filter { it > 0.005f && it < 0.995f },
            enabled = duration != null,
        )
        Text(
            duration?.let { "-" + Formatting.clock((it - position).coerceAtLeast(0)) } ?: "",
            Modifier.clearAndSetSemantics { },
            style = clock,
            color = RefluxColors.contentSecondary,
            maxLines = 1,
        )
    }
}

/**
 * The size of the controls for a player [widthDp] x [heightDp]: a desktop's pointer bar grows with the room, in a window or
 * fullscreen alike; a handheld's thumbs and a television's distance size theirs (design/PlayerChrome.kt).
 */
private fun chromeOf(app: AppState, layout: LayoutProfile, widthDp: Float, heightDp: Float): PlayerChromeSpec {
    val bar = layout.formFactor != FormFactor.TV && layout.formFactor != FormFactor.PHONE && layout.modality != InputModality.TOUCH
    return PlayerChrome.spec(PlayerChrome.tier(widthDp, heightDp, app.platform.fullscreenMode == FullscreenMode.WINDOW, bar))
}

/** Play or pause, and the ten-second seeks, as actions on something a screen reader can land on. */
private fun transportActions(session: PlaybackSession, state: PlayerState): List<CustomAccessibilityAction> = listOf(
    CustomAccessibilityAction(if (state.pauses) "Pause" else "Play") { session.handle(SemanticAction.PLAY_PAUSE) },
    CustomAccessibilityAction("Back 10 seconds") { session.handle(SemanticAction.SEEK_BACKWARD) },
    CustomAccessibilityAction("Forward 10 seconds") { session.handle(SemanticAction.SEEK_FORWARD) },
)

/**
 * The open menu: its siblings across the top, its options beneath. The highlighted option follows
 * directional input and is what Select chooses; a pointer just clicks.
 */
@Composable
private fun MenuPanel(session: PlaybackSession, menus: List<PlayerMenu>, open: PlayerMenu, options: List<MenuOption>) {
    val showsHighlight = LocalLayout.current.showsFocus
    val touch = LocalLayout.current.modality == InputModality.TOUCH
    val list = rememberLazyListState()
    LaunchedEffect(open) { list.scrollToItem(session.highlighted.coerceIn(0, options.lastIndex.coerceAtLeast(0))) }
    LaunchedEffect(session.highlighted) {
        val visible = list.layoutInfo.visibleItemsInfo
        if (visible.isNotEmpty() && (session.highlighted <= visible.first().index || session.highlighted >= visible.last().index)) {
            list.moveToItem((session.highlighted - 2).coerceAtLeast(0))
        }
    }
    // A list of what is in effect is one choice among its lines; one with nothing to be in effect is a list of actions.
    val choice = options.any { it.selected }
    // Five lists across the top need more room than four.
    Column(
        Modifier.width(if (menus.size > 4) 430.dp else 340.dp).glass(MaterialRole.MODAL, tint = playerTint.copy(alpha = 0.92f)).absorbsPointer()
            .pane(open.title) { session.closeMenu() }.traversal(Traversal.AFTER_PAGE).padding(8.dp),
    ) {
        // A large text size scrolls the tabs rather than cutting off the last of them.
        Row(Modifier.horizontalScroll(rememberScrollState()).selectableGroup().padding(horizontal = 2.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            menus.forEach { menu -> Tab(menu.title, menu == open, compact = true) { if (menu != open) session.toggleMenu(menu) } }
        }
        LazyColumn(Modifier.heightIn(max = 312.dp).then(if (choice) Modifier.selectableGroup() else Modifier), state = list, contentPadding = PaddingValues(vertical = FocusRingReach)) {
            itemsIndexed(options) { index, option ->
                Pressable(
                    { session.choose(open, index) },
                    Modifier.fillMaxWidth().then(if (choice) Modifier.semantics { selected = option.selected } else Modifier),
                    Shapes.large,
                    lift = 0f,
                    role = if (choice) Role.RadioButton else Role.Button,
                ) { active ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = if (touch) 48.dp else 0.dp)
                            .background(States.layer(if (active || (showsHighlight && index == session.highlighted)) 0.12f else 0f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(option.label, Modifier.weight(1f), style = LocalTypography.current.bodyStrong, color = if (option.selected) RefluxColors.contentPrimary else RefluxColors.contentSecondary, maxLines = 1)
                        option.detail?.let { Text(it, style = LocalTypography.current.caption, color = RefluxColors.contentTertiary, maxLines = 1) }
                        if (option.selected) Icon(Icon.CHECK, size = 16.dp, tint = RefluxColors.contentPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun UpNextCard(session: PlaybackSession, prompt: UpNext, modifier: Modifier = Modifier) {
    val (title, caption) = remember(prompt.entry) { nextHeading(prompt) }
    val next = listOfNotNull(caption, title).joinToString(" · ")
    Notice(
        modifier,
        if (prompt.asks) "Still watching?" else "Up next in ${ceil(prompt.remainingMs / 1000.0).toInt()}",
        next,
        // The countdown changes every second; what is said is said once.
        said = if (prompt.asks) "Still watching?" else "Up next: $next",
    ) {
        if (prompt.asks) {
            Button("Keep watching", { session.continueWatching() }, icon = Icon.PLAY, style = ButtonStyle.PRIMARY)
            Button("Stop", { session.close() })
        } else {
            Button("Play now", { session.playNext() }, icon = Icon.PLAY, style = ButtonStyle.PRIMARY)
            Button("Cancel", { session.cancelUpNext() })
        }
    }
}

private fun nextHeading(prompt: UpNext): Pair<String, String?> {
    val item = prompt.entry.item
    return if (item is Episode) item.title to Formatting.episodeNumber(item) else item.title to null
}

/** Why playback stopped, in the user's words, with the engine's own wording kept small for bug reports. */
@Composable
private fun Failure(session: PlaybackSession, error: PlaybackError, modifier: Modifier = Modifier) {
    Notice(modifier, "Playback stopped", error.message, said = "Playback stopped. ${error.message}", detail = error.detail) {
        if (error.canRetry) {
            Button("Try again", { session.retry() }, icon = Icon.PLAY, style = ButtonStyle.PRIMARY)
            Button("Back to library", { session.close() })
        } else {
            Button("Back to library", { session.close() }, style = ButtonStyle.PRIMARY)
        }
    }
}

@Composable
private fun Notice(modifier: Modifier, title: String, body: String, said: String, detail: String? = null, actions: @Composable () -> Unit) {
    Column(
        modifier.widthIn(max = 440.dp).glass(MaterialRole.MODAL, tint = playerTint.copy(alpha = 0.92f)).absorbsPointer().pane(said).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = LocalTypography.current.captionStrong, color = RefluxColors.contentSecondary)
        Text(body, style = LocalTypography.current.title, maxLines = 4)
        if (detail != null) Text("Details: $detail", style = LocalTypography.current.caption, color = RefluxColors.contentTertiary, maxLines = 2)
        // Beneath one another rather than off the card, where a large text size leaves no room beside each other.
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { actions() }
    }
}

/** A genuine wait for the picture: the Reflux loader (the brand's flow), after a moment, so a quick start shows nothing. */
@Composable
private fun Spinner(modifier: Modifier = Modifier) = dev.reflux.ui.components.RefluxLoader(modifier, size = 52.dp)

private const val POINTER_REPORT_MS = 300L

/** Before any pointer event of this session, whatever the platform's event clock starts at. */
private const val LONG_AGO = Long.MIN_VALUE / 2
private const val DOUBLE_CLICK_MS = 320L
