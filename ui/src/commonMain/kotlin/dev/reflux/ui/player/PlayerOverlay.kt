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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.dp
import dev.reflux.core.input.SemanticAction
import dev.reflux.core.model.Episode
import dev.reflux.core.playback.PlaybackController
import dev.reflux.core.playback.PlayerState
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.playback.SkipSegment
import dev.reflux.core.playback.SkipSegments
import dev.reflux.design.MaterialRole
import dev.reflux.design.Materials
import dev.reflux.design.Motion
import dev.reflux.ui.RefluxTheme
import dev.reflux.ui.components.Button
import dev.reflux.ui.components.ButtonStyle
import dev.reflux.ui.components.Icon
import dev.reflux.ui.components.IconButton
import dev.reflux.ui.components.Pressable
import dev.reflux.ui.components.Slider
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Formatting
import dev.reflux.ui.state.PlaybackSession
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
import kotlin.math.abs
import kotlin.math.ceil

private enum class Menu { AUDIO, SUBTITLES, SPEED }

/** The video cannot be sampled for blur, so player glass is a denser tint of the same material. */
private val playerTint = Materials.spec(MaterialRole.FLOATING).tint.toColor().copy(alpha = 0.78f)

/**
 * The player's controls, drawn over the platform's video surface. Simple by default: a title, a scrubber,
 * and transport controls that fade away while playing; tracks and speed are one click deeper.
 *
 * The background stays transparent so the shell's video shows through.
 *
 * @param hiddenCursor the pointer icon to use while the controls are hidden, where the platform has one.
 */
@Composable
fun PlayerOverlay(app: AppState, session: PlaybackSession, modifier: Modifier = Modifier, hiddenCursor: PointerIcon? = null) {
    RefluxTheme(app, modifier) {
        val state by session.state.collectAsState()
        var menu by remember { mutableStateOf<Menu?>(null) }
        var visible by remember { mutableStateOf(true) }
        var lastPointerMs by remember { mutableLongStateOf(0L) }
        val playing = state.status == PlayerStatus.PLAYING
        val pinned = menu != null || session.upNext != null || session.error != null

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
                            if (event.type != PointerEventType.Move && event.type != PointerEventType.Press) continue
                            // Pointer movement is continuous; a few reports a second are enough to keep controls up.
                            val now = event.changes.firstOrNull()?.uptimeMillis ?: continue
                            if (now - lastPointerMs >= POINTER_REPORT_MS || event.type == PointerEventType.Press) {
                                lastPointerMs = now
                                session.touch()
                            }
                        }
                    }
                }
                .pointerInput(session) {
                    var lastTapMs = 0L
                    detectTapGestures { _ ->
                        val now = lastPointerMs
                        if (menu != null) {
                            menu = null
                        } else {
                            // Click toggles playback at once; a second click soon after also toggles fullscreen.
                            session.handle(SemanticAction.PLAY_PAUSE)
                            if (now - lastTapMs <= DOUBLE_CLICK_MS) session.handle(SemanticAction.FULLSCREEN)
                        }
                        lastTapMs = now
                    }
                }
                .then(if (!visible && hiddenCursor != null) Modifier.pointerHoverIcon(hiddenCursor) else Modifier),
        ) {
            if (state.status == PlayerStatus.LOADING || state.status == PlayerStatus.BUFFERING) Spinner(Modifier.align(Alignment.Center))

            AnimatedVisibility(visible, enter = fadeIn(Motion.controlsShow.tween()), exit = fadeOut(Motion.controlsHide.tween())) {
                Controls(app, session, state, menu) { menu = if (menu == it) null else it }
            }

            val layout = LocalLayout.current
            val corner = Modifier.align(Alignment.BottomEnd).padding(end = layout.safeMarginDp.dp, bottom = if (visible) 148.dp else 40.dp)
            val prompt = session.upNext
            val error = session.error
            val segment = remember(state.chapters, state.durationMs) { SkipSegments.from(state.chapters, state.durationMs) }
                .let { SkipSegments.at(it, state.positionMs) }
            when {
                error != null -> Notice(Modifier.align(Alignment.Center), "Playback stopped", error) {
                    Button("Back to library", { session.close() }, style = ButtonStyle.PRIMARY)
                }
                prompt != null -> UpNextCard(session, prompt, corner)
                segment != null && menu == null -> Button(skipLabel(segment), { session.skip(segment) }, corner, style = ButtonStyle.PRIMARY)
            }
        }
    }
}

private fun skipLabel(segment: SkipSegment): String = when (segment.kind) {
    SkipSegment.Kind.INTRO -> "Skip intro"
    SkipSegment.Kind.RECAP -> "Skip recap"
    SkipSegment.Kind.CREDITS -> "Skip credits"
}

@Composable
private fun Controls(app: AppState, session: PlaybackSession, state: PlayerState, menu: Menu?, onMenu: (Menu) -> Unit) {
    val layout = LocalLayout.current
    val margin = layout.safeMarginDp.dp
    Box(Modifier.fillMaxSize()) {
        // Scrims carry the title and controls over bright scenes.
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.28f).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent))))
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.36f).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)))))

        Row(
            Modifier.align(Alignment.TopStart).padding(horizontal = margin, vertical = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(Icon.BACK, "Back to library", { session.close() }, glass = true)
            Column {
                Text(session.title, style = LocalTypography.current.title, maxLines = 1)
                session.subtitle?.let { Text(it, style = LocalTypography.current.caption, color = RefluxColors.textSecondary, maxLines = 1) }
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).padding(horizontal = margin).padding(bottom = 24.dp).widthIn(max = 1180.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            when (menu) {
                Menu.AUDIO -> TrackMenu("Audio", state.audioTracks.map { it.id to Formatting.track(it) }, state.selectedAudioId) { it?.let(session::selectAudio) }
                Menu.SUBTITLES -> TrackMenu(
                    "Subtitles",
                    listOf<Pair<String?, String>>(null to "Off") + state.subtitleTracks.map { it.id to Formatting.track(it) },
                    state.selectedSubtitleId,
                    session::selectSubtitle,
                )
                Menu.SPEED -> TrackMenu(
                    "Speed",
                    PlaybackController.SPEEDS.map { it.toString() to if (it == 1.0) "Normal" else Formatting.speed(it) },
                    PlaybackController.SPEEDS.minBy { abs(it - state.speed) }.toString(),
                ) { it?.toDoubleOrNull()?.let(session::setSpeed) }
                null -> Unit
            }
            Column(
                Modifier.fillMaxWidth().glass(MaterialRole.FLOATING, RoundedCornerShape(24.dp), tint = playerTint)
                    // Clicks on the panel itself must not reach the video's click-to-pause.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Timeline(session, state)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(Icon.PREVIOUS, "Previous", { session.handle(SemanticAction.PREVIOUS) })
                    IconButton(Icon.REWIND, "Back 10 seconds", { session.handle(SemanticAction.SEEK_BACKWARD) })
                    IconButton(
                        if (state.status == PlayerStatus.PLAYING || state.status == PlayerStatus.BUFFERING) Icon.PAUSE else Icon.PLAY,
                        if (state.status == PlayerStatus.PLAYING) "Pause" else "Play",
                        { session.handle(SemanticAction.PLAY_PAUSE) },
                        size = 48.dp,
                    )
                    IconButton(Icon.FORWARD, "Forward 10 seconds", { session.handle(SemanticAction.SEEK_FORWARD) })
                    if (session.next != null) IconButton(Icon.NEXT, "Next episode", { session.playNext() })
                    Spacer(Modifier.weight(1f))
                    IconButton(if (state.muted || state.volume == 0) Icon.VOLUME_MUTED else Icon.VOLUME, if (state.muted) "Unmute" else "Mute", { session.handle(SemanticAction.MUTE) })
                    Slider(
                        fraction = if (state.muted) 0f else state.volume / 100f,
                        onChange = { session.setVolume((it * 100).toInt()) },
                        modifier = Modifier.width(96.dp).padding(end = 8.dp),
                    )
                    Button(if (state.speed == 1.0) "1×" else Formatting.speed(state.speed), { onMenu(Menu.SPEED) }, style = ButtonStyle.QUIET)
                    if (state.audioTracks.size > 1) IconButton(Icon.AUDIO, "Audio track", { onMenu(Menu.AUDIO) })
                    if (state.subtitleTracks.isNotEmpty()) {
                        IconButton(Icon.SUBTITLES, "Subtitles", { onMenu(Menu.SUBTITLES) }, tint = if (state.selectedSubtitleId != null) RefluxColors.textPrimary else RefluxColors.textSecondary)
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
            onChange = { preview = it },
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

@Composable
private fun TrackMenu(title: String, options: List<Pair<String?, String>>, selected: String?, onSelect: (String?) -> Unit) {
    Column(
        Modifier.width(300.dp).glass(MaterialRole.MODAL, tint = playerTint.copy(alpha = 0.9f))
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(8.dp),
    ) {
        Text(title.uppercase(), Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = LocalTypography.current.label, color = RefluxColors.textTertiary)
        Column(Modifier.widthIn(max = 300.dp).verticalScroll(rememberScrollState())) {
            options.forEach { (id, label) ->
                Pressable({ onSelect(id) }, Modifier.fillMaxWidth(), RoundedCornerShape(10.dp), lift = 0f) { active ->
                    Row(
                        Modifier.fillMaxWidth().background(Color.White.copy(alpha = if (active) 0.10f else 0f)).padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, Modifier.weight(1f), style = LocalTypography.current.bodyStrong, color = if (id == selected) RefluxColors.textPrimary else RefluxColors.textSecondary, maxLines = 1)
                        if (id == selected) Icon(Icon.CHECK, size = 16.dp)
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

@Composable
private fun Notice(modifier: Modifier, title: String, body: String, actions: @Composable () -> Unit) {
    Column(
        modifier.widthIn(max = 420.dp).glass(MaterialRole.MODAL, tint = playerTint.copy(alpha = 0.9f))
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title.uppercase(), style = LocalTypography.current.label, color = RefluxColors.textSecondary)
        Text(body, style = LocalTypography.current.title, maxLines = 2)
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
private const val DOUBLE_CLICK_MS = 320L
