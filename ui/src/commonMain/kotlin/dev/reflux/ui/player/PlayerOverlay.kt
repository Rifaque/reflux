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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import dev.reflux.ui.components.Pressable
import dev.reflux.ui.components.Slider
import dev.reflux.ui.components.Tab
import dev.reflux.ui.components.absorbsPointer
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
        val pinned = session.menu != null || session.upNext != null || session.error != null

        // Controls appear on any interaction and leave after a pause while the video is playing.
        LaunchedEffect(session.interactions, playing, pinned) {
            visible = true
            if (playing && !pinned) {
                delay(Motion.CONTROLS_AUTO_HIDE_MS)
                visible = false
            }
        }

        Box(
            Modifier.fillMaxSize()
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
                                visibleAtPress -> visible = false
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

            AnimatedVisibility(
                visible = app.notice != null,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp),
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

            val layout = LocalLayout.current
            val remote = app.platform.systemVolume && layout.modality == InputModality.DIRECTIONAL
            val corner = Modifier.align(Alignment.BottomEnd).padding(end = layout.safeMarginDp.dp, bottom = if (visible) 148.dp else 40.dp)
            val prompt = session.upNext
            val segment = session.skippable(state)
            when {
                error != null -> Failure(session, error, Modifier.align(Alignment.Center))
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
private fun Controls(app: AppState, session: PlaybackSession, state: PlayerState, onBar: (Float) -> Unit) {
    val layout = LocalLayout.current
    val margin = layout.safeMarginDp.dp
    val menus = session.menus(state)
    val open = session.menu?.takeIf { it in menus }
    val failed = session.error != null
    // On a phone the transport sits in the middle of the picture, where thumbs are, and the bar along the
    // bottom keeps the timeline and what is used less often. Volume is the device's own buttons.
    val compact = layout.formFactor == FormFactor.PHONE
    // On a television nothing here is pressed with a pointer: the remote plays, seeks, and opens the lists,
    // so the bar only says where playback is and what there is beneath it.
    val television = layout.formFactor == FormFactor.TV
    Box(Modifier.fillMaxSize()) {
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
            if (!television) IconButton(Icon.BACK, "Back to library", { session.close() }, glass = true)
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

        if (compact && !failed) {
            val waiting = state.status == PlayerStatus.LOADING || state.status == PlayerStatus.BUFFERING
            Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(30.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(Icon.REWIND, "Back 10 seconds", { session.handle(SemanticAction.SEEK_BACKWARD) }, size = 54.dp, glass = true)
                // While playback is waiting, the spinner is here instead.
                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    if (!waiting) {
                        IconButton(
                            if (state.pauses) Icon.PAUSE else Icon.PLAY,
                            if (state.pauses) "Pause" else "Play",
                            { session.handle(SemanticAction.PLAY_PAUSE) },
                            size = 72.dp,
                            glass = true,
                        )
                    }
                }
                IconButton(Icon.FORWARD, "Forward 10 seconds", { session.handle(SemanticAction.SEEK_FORWARD) }, size = 54.dp, glass = true)
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
                Timeline(session, state)
                if (television) {
                    Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (state.pauses) "" else "Paused", Modifier.weight(1f), style = LocalTypography.current.captionStrong, color = RefluxColors.textSecondary, maxLines = 1)
                        // Down opens these; the one that is open is the one lit.
                        Icon(Icon.CHEVRON_DOWN, size = 12.dp, tint = RefluxColors.textTertiary)
                        menus.forEach { menu ->
                            Text(menu.title, style = LocalTypography.current.captionStrong, color = if (menu == open) RefluxColors.textPrimary else RefluxColors.textSecondary, maxLines = 1)
                        }
                    }
                } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(Icon.PREVIOUS, "Previous", { session.handle(SemanticAction.PREVIOUS) })
                    if (!compact) {
                        IconButton(Icon.REWIND, "Back 10 seconds", { session.handle(SemanticAction.SEEK_BACKWARD) })
                        IconButton(
                            if (state.pauses) Icon.PAUSE else Icon.PLAY,
                            if (state.pauses) "Pause" else "Play",
                            { session.handle(SemanticAction.PLAY_PAUSE) },
                            size = 48.dp,
                        )
                        IconButton(Icon.FORWARD, "Forward 10 seconds", { session.handle(SemanticAction.SEEK_FORWARD) })
                    }
                    if (session.next != null) IconButton(Icon.NEXT, "Next episode", { session.playNext() })
                    Spacer(Modifier.weight(1f))
                    if (!compact) {
                        IconButton(if (state.muted || state.volume == 0) Icon.VOLUME_MUTED else Icon.VOLUME, if (state.muted) "Unmute" else "Mute", { session.handle(SemanticAction.MUTE) })
                        Slider(
                            fraction = if (state.muted) 0f else state.volume / 100f,
                            description = "Volume",
                            onChange = { session.setVolume((it * 100).toInt()) },
                            modifier = Modifier.width(96.dp).padding(end = 8.dp),
                        )
                    }
                    // The bar has its own previous and next; the lists are what is left.
                    menus.filter { it != PlayerMenu.EPISODES }.forEach { menu ->
                        val tint = if (menu == open || (menu == PlayerMenu.SUBTITLES && state.selectedSubtitleId != null)) RefluxColors.textPrimary else RefluxColors.textSecondary
                        if (menu == PlayerMenu.SPEED) {
                            Button(
                                if (state.speed == 1.0) "1×" else Formatting.speed(state.speed),
                                { session.toggleMenu(menu) },
                                Modifier.semantics { contentDescription = "Playback speed" },
                                style = ButtonStyle.QUIET,
                            )
                        } else {
                            IconButton(menuIcon(menu), menu.title, { session.toggleMenu(menu) }, tint = tint)
                        }
                    }
                    IconButton(
                        if (app.platform.fullscreen) Icon.FULLSCREEN_EXIT else Icon.FULLSCREEN,
                        if (app.platform.fullscreen) "Exit fullscreen" else "Fullscreen",
                        { session.handle(SemanticAction.FULLSCREEN) },
                    )
                }
            }
        }
    }
}

/** The title of what is playing, with the episode or year beneath it. */
@Composable
private fun NowPlaying(session: PlaybackSession, compact: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 4.dp)) {
        Text(session.title, style = if (compact) LocalTypography.current.title else LocalTypography.current.headline, maxLines = 1)
        session.subtitle?.let { Text(it, style = if (compact) LocalTypography.current.caption else LocalTypography.current.metadata, color = RefluxColors.textSecondary, maxLines = 1) }
    }
}

/** Position, scrubber with chapter marks, and time remaining. Dragging previews the time before seeking. */
@Composable
private fun Timeline(session: PlaybackSession, state: PlayerState) {
    val duration = state.durationMs?.takeIf { it > 0 }
    var preview by remember { mutableStateOf<Float?>(null) }
    val position = preview?.let { fraction -> duration?.let { (it * fraction).toLong() } } ?: state.positionMs
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(Formatting.clock(position), style = LocalTypography.current.captionStrong, color = RefluxColors.textSecondary, maxLines = 1)
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
            modifier = Modifier.weight(1f),
            marks = if (duration == null) emptyList() else state.chapters.map { it.startMs.toFloat() / duration }.filter { it > 0.005f && it < 0.995f },
            enabled = duration != null,
        )
        Text(
            duration?.let { "-" + Formatting.clock(it - position) } ?: "",
            style = LocalTypography.current.captionStrong,
            color = RefluxColors.textSecondary,
            maxLines = 1,
        )
    }
}

/**
 * The open menu: its siblings across the top, its options beneath. The highlighted option follows
 * directional input and is what Select chooses; a pointer just clicks.
 */
@Composable
private fun MenuPanel(session: PlaybackSession, menus: List<PlayerMenu>, open: PlayerMenu, options: List<MenuOption>) {
    val showsHighlight = LocalLayout.current.showsFocus
    val list = rememberLazyListState()
    LaunchedEffect(open) { list.scrollToItem(session.highlighted.coerceIn(0, options.lastIndex.coerceAtLeast(0))) }
    LaunchedEffect(session.highlighted) {
        val visible = list.layoutInfo.visibleItemsInfo
        if (visible.isNotEmpty() && (session.highlighted <= visible.first().index || session.highlighted >= visible.last().index)) {
            list.animateScrollToItem((session.highlighted - 2).coerceAtLeast(0))
        }
    }
    // Five lists across the top need more room than four.
    Column(Modifier.width(if (menus.size > 4) 430.dp else 340.dp).glass(MaterialRole.MODAL, tint = playerTint.copy(alpha = 0.92f)).absorbsPointer().padding(8.dp)) {
        Row(Modifier.padding(horizontal = 2.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            menus.forEach { menu -> Tab(menu.title, menu == open, compact = true) { if (menu != open) session.toggleMenu(menu) } }
        }
        LazyColumn(Modifier.heightIn(max = 312.dp), state = list, contentPadding = PaddingValues(vertical = FocusRingReach)) {
            itemsIndexed(options) { index, option ->
                Pressable(
                    { session.choose(open, index) },
                    Modifier.fillMaxWidth().semantics { selected = option.selected },
                    RoundedCornerShape(10.dp),
                    lift = 0f,
                ) { active ->
                    Row(
                        Modifier.fillMaxWidth()
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
    Notice(
        modifier,
        if (prompt.asks) "Still watching?" else "Up next in ${ceil(prompt.remainingMs / 1000.0).toInt()}",
        listOfNotNull(caption, title).joinToString(" · "),
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
    Notice(modifier, "Playback stopped", error.message, detail = error.detail) {
        if (error.canRetry) {
            Button("Try again", { session.retry() }, icon = Icon.PLAY, style = ButtonStyle.PRIMARY)
            Button("Back to library", { session.close() })
        } else {
            Button("Back to library", { session.close() }, style = ButtonStyle.PRIMARY)
        }
    }
}

@Composable
private fun Notice(modifier: Modifier, title: String, body: String, detail: String? = null, actions: @Composable () -> Unit) {
    Column(
        modifier.widthIn(max = 440.dp).glass(MaterialRole.MODAL, tint = playerTint.copy(alpha = 0.92f)).absorbsPointer().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = LocalTypography.current.captionStrong, color = RefluxColors.textSecondary)
        Text(body, style = LocalTypography.current.title, maxLines = 4)
        if (detail != null) Text("Details: $detail", style = LocalTypography.current.caption, color = RefluxColors.textTertiary, maxLines = 2)
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { actions() }
    }
}

@Composable
private fun Spinner(modifier: Modifier = Modifier) {
    val angle by rememberInfiniteTransition().animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)))
    Canvas(modifier.size(44.dp)) {
        rotate(angle) {
            drawArc(Color.White.copy(alpha = 0.9f), startAngle = 0f, sweepAngle = 270f, useCenter = false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

private const val POINTER_REPORT_MS = 300L

/** Before any pointer event of this session, whatever the platform's event clock starts at. */
private const val LONG_AGO = Long.MIN_VALUE / 2
private const val DOUBLE_CLICK_MS = 320L
