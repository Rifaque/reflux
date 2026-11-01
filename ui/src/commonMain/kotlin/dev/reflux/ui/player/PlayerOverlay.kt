package dev.reflux.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
 * The player's controls, drawn over the platform's video surface. Simple by default: a title, a scrubber,
 * and transport controls that fade away while playing; chapters, tracks, and speed are one step deeper, in
 * menus that work the same with a pointer, a keyboard, or a controller.
 *
 * The background stays transparent so the shell's video shows through.
 *
 * @param hiddenCursor the pointer icon to use while the controls are hidden, where the platform has one.
 */
@Composable
fun PlayerOverlay(app: AppState, session: PlaybackSession, modifier: Modifier = Modifier, hiddenCursor: PointerIcon? = null) {
    RefluxTheme(app, modifier) {
        val state by session.state.collectAsState()
        var visible by remember { mutableStateOf(true) }
        var visibleAtPress by remember { mutableStateOf(true) }
        var lastPointerMs by remember { mutableLongStateOf(LONG_AGO) }
        val playing = state.status == PlayerStatus.PLAYING
        // A screen reader finds controls by reading them: controls that left could never be found again, since
        // the movement that brings them back is not something it makes.
        val pinned = session.menu != null || session.upNext != null || session.error != null || LocalAccessibility.current.screenReader
        // Someone who asked the system for longer to act gets it here too.
        val patience = LocalAccessibilityManager.current?.calculateRecommendedTimeoutMillis(
            Motion.CONTROLS_AUTO_HIDE_MS, containsIcons = true, containsText = true, containsControls = true,
        ) ?: Motion.CONTROLS_AUTO_HIDE_MS

        // Controls appear on any interaction and leave after a pause while the video is playing.
        LaunchedEffect(session.interactions, playing, pinned, patience) {
            visible = true
            if (playing && !pinned) {
                delay(patience)
                visible = false
            }
        }

        Box(
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
                            val now = event.changes.firstOrNull()?.uptimeMillis ?: continue
                            if (event.type == PointerEventType.Press) visibleAtPress = visible
                            if (now - lastPointerMs >= POINTER_REPORT_MS || event.type == PointerEventType.Press) {
                                lastPointerMs = now
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
                        val now = lastPointerMs
                        val again = now - lastTapMs <= DOUBLE_CLICK_MS
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
                                visibleAtPress && !app.platform.accessibility.screenReader -> visible = false
                            }
                        } else {
                            // Click toggles playback at once; a second click soon after also toggles fullscreen.
                            session.handle(SemanticAction.PLAY_PAUSE)
                            if (again) session.handle(SemanticAction.FULLSCREEN)
                        }
                        lastTapMs = now
                    }
                }
                .then(if (!visible && hiddenCursor != null) Modifier.pointerHoverIcon(hiddenCursor) else Modifier),
        ) {
            val error = session.error
            val waiting = state.status == PlayerStatus.LOADING || state.status == PlayerStatus.BUFFERING
            if (error == null && waiting) Spinner(Modifier.align(Alignment.Center))

            // Subtitles move above the bar while it shows, rather than being read through it.
            var bar by remember { mutableFloatStateOf(0f) }
            val clearance = if (visible && session.error == null) bar else 0f
            LaunchedEffect(clearance) { session.keepSubtitlesClear(clearance) }
            AnimatedVisibility(visible, enter = fadeIn(Motion.controlsShow.tween()), exit = fadeOut(Motion.controlsHide.tween())) {
                Controls(app, session, state) { bar = it }
            }

            // Kept off the screen's edges where the window is narrower than the notice can be.
            Box(Modifier.align(Alignment.TopCenter).padding(top = 28.dp).padding(horizontal = LocalLayout.current.safeMarginDp.dp).announces(app.notice)) {
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

            val layout = LocalLayout.current
            val remote = app.platform.systemVolume && layout.modality == InputModality.DIRECTIONAL
            val corner = Modifier.align(Alignment.BottomEnd).padding(end = layout.safeMarginDp.dp, bottom = if (visible) 148.dp else 40.dp)
            val prompt = session.upNext
            val segment = session.skippable(state)
            when {
                error != null -> Failure(session, error, Modifier.align(Alignment.Center).padding(horizontal = layout.safeMarginDp.dp))
                prompt != null -> UpNextCard(session, prompt, corner)
                // With a remote, up is what takes the offer; the arrow says so.
                segment != null && session.menu == null ->
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
    PlayerMenu.SPEED, PlayerMenu.EPISODES -> error("not an icon in the bar")
}

@Composable
private fun Controls(app: AppState, session: PlaybackSession, state: PlayerState, onBar: (Float) -> Unit) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val layout = LocalLayout.current
    val margin = layout.safeMarginDp.dp
    val menus = session.menus(state)
    val open = session.menu?.takeIf { it in menus }
    val failed = session.error != null
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
    // Lists the player's own keys reach; named in a pointer's label, and they are the same on every list.
    val listsKey = "O"
    // Scrims carry the title and controls over bright scenes; the picture between them is left alone.
    Box(Modifier.fillMaxWidth().fillMaxHeight(0.24f).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.66f), Color.Transparent))))
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.46f).background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Black.copy(alpha = 0.42f), 1f to Color.Black.copy(alpha = 0.84f))))

    // What is playing belongs with where it is: above the timeline. A phone keeps it at the top, beside the
    // way back, where it stays clear of the transport in the middle of the picture.
    Row(
        Modifier.align(Alignment.TopStart).padding(horizontal = margin, vertical = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (controls.back) IconButton(Icon.BACK, "Back to library", { session.close() }, glass = true, shortcut = "Esc")
        if (compact) NowPlaying(session, compact = true)
    }

    if (app.namesButtons && !failed) {
        val family = app.input.controllerFamily ?: ControllerFamily.GENERIC
        ButtonPrompts(
            if (open != null) {
                listOf(InputMapping.confirmButton(family) to "Choose", InputMapping.cancelButton(family) to "Close")
            } else {
                listOfNotNull(
                    InputMapping.confirmButton(family) to if (state.pauses) "Pause" else "Play",
                    InputMapping.cancelButton(family) to "Back",
                    GamepadButton.START to "Options",
                    (GamepadButton.SHOULDER_RIGHT to "Next").takeIf { session.next != null },
                )
            },
            family,
            Modifier.align(Alignment.TopEnd).padding(horizontal = margin, vertical = 24.dp),
        )
    }

    // While a list is open it has the screen's attention: on a touch screen it can be taller than the space
    // beneath the transport, so the transport steps aside until the list closes rather than sit under it.
    if (centre && !failed && open == null) {
        val waiting = state.status == PlayerStatus.LOADING || state.status == PlayerStatus.BUFFERING
        // A tablet's picture is larger and its thumbs further apart; the cluster grows with it, not with the screen.
        val seek = if (compact) 54.dp else 64.dp
        val play = if (compact) 72.dp else 84.dp
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(if (compact) 30.dp else 44.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(Icon.REWIND, "Back 10 seconds", { session.handle(SemanticAction.SEEK_BACKWARD) }, size = seek, glass = true)
            // While playback is waiting, the spinner is here instead.
            Box(Modifier.size(play), contentAlignment = Alignment.Center) {
                if (!waiting) {
                    IconButton(
                        if (state.pauses) Icon.PAUSE else Icon.PLAY,
                        if (state.pauses) "Pause" else "Play",
                        { session.handle(SemanticAction.PLAY_PAUSE) },
                        size = play,
                        glass = true,
                    )
                }
            }
            IconButton(Icon.FORWARD, "Forward 10 seconds", { session.handle(SemanticAction.SEEK_FORWARD) }, size = seek, glass = true)
        }
    }
    Column(
        Modifier.align(Alignment.BottomCenter).padding(horizontal = margin).padding(bottom = if (compact) 12.dp else 24.dp).widthIn(max = 1180.dp)
            .onGloballyPositioned { bottom ->
                // The share of the window this block covers (what is playing, the bar, an open list), and a
                // little air: subtitles are kept above it.
                val window = bottom.findRootCoordinates().size.height.toFloat()
                if (window > 0f) onBar(((window - bottom.positionInRoot().y) / window + 0.02f).coerceIn(0f, 1f))
            },
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Bottom) {
            if (!compact) NowPlaying(session, compact = false, Modifier.weight(1f).padding(start = 6.dp)) else Spacer(Modifier.weight(1f))
            if (open != null && !failed) MenuPanel(session, menus, open, session.options(open, state))
        }
        if (failed) return@Column
        Column(
            // Clicks on the panel itself must not reach the video's click-to-pause.
            Modifier.fillMaxWidth().glass(MaterialRole.FLOATING, RoundedCornerShape(26.dp), tint = playerTint).absorbsPointer()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Timeline(session, state, remote = controls.transport == Transport.REMOTE)
            if (controls.listHints) {
                Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        if (state.status == PlayerStatus.PAUSED) Text("Paused", style = LocalTypography.current.captionStrong, color = RefluxColors.textSecondary, maxLines = 1)
                    }
                    // Down opens these; the one that is open is the one lit. To the D-pad they are only names, so
                    // they take no focus; a screen reader, which goes through what is on screen, opens them directly.
                    Icon(Icon.CHEVRON_DOWN, size = 12.dp, tint = RefluxColors.textTertiary)
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
                            color = if (menu == open) RefluxColors.textPrimary else RefluxColors.textSecondary,
                            maxLines = 1,
                        )
                    }
                }
            } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (controls.previous) IconButton(Icon.PREVIOUS, "Previous", { session.handle(SemanticAction.PREVIOUS) }, shortcut = "Page Up")
                if (controls.transport == Transport.BAR) {
                    IconButton(Icon.REWIND, "Back 10 seconds", { session.handle(SemanticAction.SEEK_BACKWARD) }, shortcut = "←")
                    IconButton(
                        if (state.pauses) Icon.PAUSE else Icon.PLAY,
                        if (state.pauses) "Pause" else "Play",
                        { session.handle(SemanticAction.PLAY_PAUSE) },
                        size = 48.dp,
                        shortcut = "Space",
                    )
                    IconButton(Icon.FORWARD, "Forward 10 seconds", { session.handle(SemanticAction.SEEK_FORWARD) }, shortcut = "→")
                }
                if (controls.next) IconButton(Icon.NEXT, "Next episode", { session.playNext() }, shortcut = "Page Down")
                Spacer(Modifier.weight(1f))
                if (controls.volume) {
                    IconButton(if (state.muted || state.volume == 0) Icon.VOLUME_MUTED else Icon.VOLUME, if (state.muted) "Unmute" else "Mute", { session.handle(SemanticAction.MUTE) }, shortcut = "M")
                    Slider(
                        fraction = if (state.muted) 0f else state.volume / 100f,
                        description = "Volume",
                        onChange = { session.setVolume((it * 100).toInt()) },
                        modifier = Modifier.width(96.dp).padding(end = 8.dp),
                    )
                }
                controls.lists.forEach { menu ->
                    val tint = if (menu == open || (menu == PlayerMenu.SUBTITLES && state.selectedSubtitleId != null)) RefluxColors.textPrimary else RefluxColors.textSecondary
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
                        IconButton(menuIcon(menu), menu.title, { session.toggleMenu(menu) }, tint = tint, shortcut = listsKey, expanded = menu == open)
                    }
                }
                controls.more?.let { first ->
                    // The rest of the lists, one tap away; their tabs lead from one to another.
                    val lit = open != null && open !in controls.lists
                    IconButton(Icon.MORE, "More options", { if (lit) session.closeMenu() else session.toggleMenu(first) }, tint = if (lit) RefluxColors.textPrimary else RefluxColors.textSecondary, shortcut = listsKey, expanded = lit)
                }
                if (controls.fullscreen) {
                    IconButton(
                        if (app.platform.fullscreen) Icon.FULLSCREEN_EXIT else Icon.FULLSCREEN,
                        if (app.platform.fullscreen) "Exit fullscreen" else "Fullscreen",
                        { session.handle(SemanticAction.FULLSCREEN) },
                        shortcut = "F",
                    )
                }
            }
        }
    }
}

/** The title of what is playing, with the episode or year beneath it. */
@Composable
private fun NowPlaying(session: PlaybackSession, compact: Boolean, modifier: Modifier = Modifier) {
    // One stop: what is playing, whichever way the two lines happen to be laid out.
    Column(modifier.semantics(mergeDescendants = true) { }, verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 4.dp)) {
        Text(session.title, style = if (compact) LocalTypography.current.title else LocalTypography.current.headline, maxLines = 1)
        session.subtitle?.let { Text(it, style = if (compact) LocalTypography.current.caption else LocalTypography.current.metadata, color = RefluxColors.textSecondary, maxLines = 1) }
    }
}

/**
 * Position, scrubber with chapter marks, and time remaining. Dragging previews the time before seeking.
 *
 * @param remote where no transport is on screen (a television): the scrubber then carries what the remote's own
 * keys do, for a screen reader, which has the keys for itself while it is on.
 */
@Composable
private fun Timeline(session: PlaybackSession, state: PlayerState, remote: Boolean) {
    val duration = state.durationMs?.takeIf { it > 0 }
    var preview by remember { mutableStateOf<Float?>(null) }
    val position = preview?.let { fraction -> duration?.let { (it * fraction).toLong() } } ?: state.positionMs
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // The slider says where it is and how long it all is, so the two clocks beside it are not said again.
        Text(Formatting.clock(position), Modifier.clearAndSetSemantics { }, style = LocalTypography.current.captionStrong, color = RefluxColors.textSecondary, maxLines = 1)
        Slider(
            fraction = if (duration == null) 0f else position.toFloat() / duration,
            description = "Timeline",
            color = RefluxColors.progress,
            onChange = {
                preview = it
                session.touch()
            },
            onChangeFinished = { fraction ->
                preview = null
                duration?.let { session.seekTo((it * fraction).toLong()) }
            },
            modifier = Modifier.weight(1f).then(if (remote) Modifier.semantics { customActions = transportActions(session, state) } else Modifier),
            valueText = duration?.let { Formatting.clock(position) + " of " + Formatting.clock(it) },
            marks = if (duration == null) emptyList() else state.chapters.map { it.startMs.toFloat() / duration }.filter { it > 0.005f && it < 0.995f },
            enabled = duration != null,
        )
        Text(
            duration?.let { "-" + Formatting.clock(it - position) } ?: "",
            Modifier.clearAndSetSemantics { },
            style = LocalTypography.current.captionStrong,
            color = RefluxColors.textSecondary,
            maxLines = 1,
        )
    }
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
                    RoundedCornerShape(10.dp),
                    lift = 0f,
                    role = if (choice) Role.RadioButton else Role.Button,
                ) { active ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = if (touch) 48.dp else 0.dp)
                            .background(Color.White.copy(alpha = if (active || (showsHighlight && index == session.highlighted)) 0.12f else 0f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(option.label, Modifier.weight(1f), style = LocalTypography.current.bodyStrong, color = if (option.selected) RefluxColors.textPrimary else RefluxColors.textSecondary, maxLines = 1)
                        option.detail?.let { Text(it, style = LocalTypography.current.caption, color = RefluxColors.textTertiary, maxLines = 1) }
                        if (option.selected) Icon(Icon.CHECK, size = 16.dp, tint = RefluxColors.textPrimary)
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
        Text(title, style = LocalTypography.current.captionStrong, color = RefluxColors.textSecondary)
        Text(body, style = LocalTypography.current.title, maxLines = 4)
        if (detail != null) Text("Details: $detail", style = LocalTypography.current.caption, color = RefluxColors.textTertiary, maxLines = 2)
        // Beneath one another rather than off the card, where a large text size leaves no room beside each other.
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { actions() }
    }
}

@Composable
private fun Spinner(modifier: Modifier = Modifier) {
    val busy = Modifier.semantics {
        contentDescription = "Loading"
        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
    }
    // That something is happening is not left to a movement some people have asked not to see.
    if (LocalAccessibility.current.reduceMotion) {
        Text("Loading…", modifier.then(busy), style = LocalTypography.current.bodyStrong)
        return
    }
    val angle by rememberInfiniteTransition().animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)))
    Canvas(modifier.size(44.dp).then(busy)) {
        rotate(angle) {
            drawArc(Color.White.copy(alpha = 0.9f), startAngle = 0f, sweepAngle = 270f, useCenter = false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

private const val POINTER_REPORT_MS = 300L

/** Before any pointer event of this session, whatever the platform's event clock starts at. */
private const val LONG_AGO = Long.MIN_VALUE / 2
private const val DOUBLE_CLICK_MS = 320L
