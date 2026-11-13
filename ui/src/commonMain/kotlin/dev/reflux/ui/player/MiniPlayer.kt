package dev.reflux.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import dev.reflux.core.input.SemanticAction
import dev.reflux.core.playback.PlayerState
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.design.DockStyle
import dev.reflux.design.MaterialRole
import dev.reflux.design.MiniPlayerDock
import dev.reflux.design.Motion
import dev.reflux.ui.RefluxTheme
import dev.reflux.ui.components.Icon
import dev.reflux.ui.components.IconButton
import dev.reflux.ui.components.Pressable
import dev.reflux.ui.components.RefluxLoader
import dev.reflux.ui.components.absorbsPointer
import dev.reflux.ui.components.announces
import dev.reflux.ui.components.pane
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.PlaybackSession
import dev.reflux.ui.theme.LocalLayout
import dev.reflux.ui.theme.LocalTypography
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.Shapes
import dev.reflux.ui.theme.States
import dev.reflux.ui.theme.Text
import dev.reflux.ui.theme.glass
import dev.reflux.ui.theme.tween
import dev.reflux.ui.trackPointer

/**
 * What the mini-player says beneath the title: what needs the viewer first (a failure, a question), then how
 * playback stands, and otherwise which episode or year it is.
 */
internal fun miniStatus(session: PlaybackSession, state: PlayerState): String? {
    val prompt = session.upNext
    return when {
        session.error != null -> "Playback stopped"
        prompt?.asks == true -> "Still watching?"
        prompt != null -> "Up next: ${prompt.entry.item.title}"
        state.status == PlayerStatus.LOADING || state.status == PlayerStatus.BUFFERING -> "Loading"
        state.status == PlayerStatus.PAUSED -> "Paused"
        else -> session.subtitle
    }
}

/** What the mini-player's one playback button does now, in the words on it; null where there is nothing for it to do. */
internal fun miniAction(session: PlaybackSession, state: PlayerState): Pair<Icon, String>? {
    val prompt = session.upNext
    return when {
        session.error != null -> (Icon.PLAY to "Try again").takeIf { session.error?.canRetry == true }
        prompt?.asks == true -> Icon.PLAY to "Keep watching"
        prompt != null -> Icon.PLAY to "Play next now"
        state.status == PlayerStatus.PLAYING || state.status == PlayerStatus.BUFFERING -> Icon.PAUSE to "Pause"
        else -> Icon.PLAY to "Play"
    }
}

/**
 * The mini-player: what is playing, in the navigation's dock, while the viewer browses (docs/NAVIGATION.md,
 * section 8). It is a presentation of the session the full player shows, never a player of its own: the picture is the
 * shell's one video surface, placed in the dock's picture box, and every control here is the session's.
 *
 * To assistive technology it is one named part of the screen with three stops: what is playing, which is also the way
 * back to the full player; play or pause; and stop. What needs the viewer (a failure, "Still watching?") is said when
 * it happens.
 */
@Composable
internal fun MiniPlayerDock(app: AppState, session: PlaybackSession, dock: MiniPlayerDock, modifier: Modifier = Modifier) {
    val state by session.state.collectAsState()
    val status = miniStatus(session, state)
    val action = miniAction(session, state)
    val playing = listOfNotNull(session.title.ifEmpty { "Player" }, session.subtitle).joinToString(", ")
    val attention = status.takeIf { session.error != null || session.upNext?.asks == true }
    val card = dock.style == DockStyle.CARD
    val pictureIsAStop = app.platform.dockPictureIsAStop
    val padding = MiniPlayerDock.PADDING_DP.dp

    @Composable
    fun Picture(pictureModifier: Modifier) {
        // One stop: what is playing and how it stands, and activating it goes back to the full player.
        Pressable(
            { session.expand() },
            // Where a screen reader can reach it, the picture is the stop; where it cannot, the words are (Platform.dockPictureIsAStop).
            if (pictureIsAStop) {
                pictureModifier.semantics {
                    contentDescription = playing
                    status?.takeIf { it != session.subtitle }?.let { stateDescription = it }
                }
            } else {
                pictureModifier.clearAndSetSemantics { }
            },
            Shapes.large,
            lift = 0f,
            actionLabel = "return to the player",
        ) {
            Box(
                Modifier.fillMaxSize().clip(Shapes.large).background(Color.Black)
                    .onGloballyPositioned { app.dockSlot = Rect(it.positionInRoot(), it.size.toSize()) },
                contentAlignment = Alignment.Center,
            ) {
                // Seen only while the picture is away: something covers the page, the surface is still arriving, or playback
                // stopped (the dead picture is not left showing, and the words beside it say what happened).
                if (session.error != null) Icon(Icon.INFO, size = 22.dp, tint = RefluxColors.warning)
                else Icon(Icon.PLAY, size = 20.dp, tint = RefluxColors.contentTertiary)
            }
        }
        DisposableEffect(app) { onDispose { app.dockSlot = null } }
    }

    @Composable
    fun Words(wordsModifier: Modifier) {
        // The picture already says this, as one stop; here it is for the eye, and a click on it goes back too. Where the picture
        // cannot be reached, these words are the stop.
        Column(
            wordsModifier.then(
                if (pictureIsAStop) {
                    Modifier.clearAndSetSemantics { }
                } else {
                    // One node that says it all: the texts inside are cleared, or a screen reader says the name twice.
                    Modifier.clearAndSetSemantics {
                        contentDescription = playing
                        status?.takeIf { it != session.subtitle }?.let { stateDescription = it }
                        role = Role.Button
                        onClick(label = "return to the player") {
                            session.expand()
                            true
                        }
                    }
                },
            ).pointerInput(session) { detectTapGestures { session.expand() } },
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            // What went wrong is the part not to cut short at a large text size (two lines); the name has its full form for a screen reader.
            Text(session.title, style = LocalTypography.current.bodyStrong, maxLines = 1)
            status?.let { Text(it, style = LocalTypography.current.caption, color = RefluxColors.contentSecondary, maxLines = if (attention != null) 2 else 1) }
        }
    }

    @Composable
    fun Controls() {
        action?.let { (icon, name) -> IconButton(icon, name, { session.handle(SemanticAction.PLAY_PAUSE) }) }
        // After a failure there is nothing playing to stop: it is closed.
        IconButton(Icon.CLOSE, if (session.error != null) "Close" else "Stop playing", { session.close() }, tint = RefluxColors.contentSecondary)
    }

    Box(
        modifier.then(if (card) Modifier.width(dock.widthDp.dp) else Modifier.widthIn(max = dock.widthDp.dp).fillMaxWidth())
            .glass(MaterialRole.FLOATING, Shapes.xlarge).absorbsPointer().pane("Mini player").focusGroup().padding(padding),
    ) {
        if (card) {
            Column {
                Picture(Modifier.fillMaxWidth().height(dock.videoHeightDp.dp))
                Progress(state, Modifier.fillMaxWidth().padding(top = 6.dp))
                Row(Modifier.fillMaxWidth().heightIn(min = (MiniPlayerDock.CARD_STRIP_DP - 8f).dp).padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Words(Modifier.weight(1f).padding(end = 8.dp))
                    Controls()
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().heightIn(min = dock.videoHeightDp.dp), verticalAlignment = Alignment.CenterVertically) {
                Picture(Modifier.size(dock.videoWidthDp.dp, dock.videoHeightDp.dp))
                Words(Modifier.weight(1f).padding(horizontal = 12.dp))
                Controls()
            }
        }
        // Said as it happens, from a node that was already here.
        Box(Modifier.announces(attention))
    }
}

/** How far playback is, as a line under the picture; for the eye alone, as the full player's timeline says it. */
@Composable
private fun Progress(state: PlayerState, modifier: Modifier = Modifier) {
    val track = States.layer(0.16f)
    val done = RefluxColors.progress
    Box(
        modifier.height(2.dp).clearAndSetSemantics { }.drawBehind {
            val duration = state.durationMs?.takeIf { it > 0 }
            val fraction = if (duration == null) 0f else (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
            drawRect(track)
            drawRect(done, size = Size(size.width * fraction, size.height))
        },
    )
}

/**
 * What a shell layers over its video surface while that surface is in the dock, where the surface takes pointer input
 * for itself (a native window): a click on the picture goes back to the full player, a resting pointer is shown
 * that it will, and a wait is shown as one. It says nothing to assistive technology: the dock's own picture box, in
 * the window beneath, is the stop that speaks and acts for it.
 *
 * @param silent whether this layer clears its own semantics, as it must in a window of its own (the desktop's). Where it
 * is laid over the dock's picture in the same tree (Android's), it must have no semantics node at all: a node over the
 * picture covers it, and the framework leaves a covered node out of what a screen reader can reach, which took the way back
 * to the player out of it (found by `PlayerAccessibilityOnDeviceTest`, M10).
 */
@Composable
fun MiniPlayerSurface(app: AppState, session: PlaybackSession, modifier: Modifier = Modifier, silent: Boolean = true) {
    RefluxTheme(app, modifier) {
        val state by session.state.collectAsState()
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val waiting = session.error == null && (state.status == PlayerStatus.LOADING || state.status == PlayerStatus.BUFFERING)
        Box(
            Modifier.fillMaxSize().then(if (silent) Modifier.clearAndSetSemantics { } else Modifier).trackPointer(app).hoverable(interaction)
                .pointerInput(session) { detectTapGestures { session.expand() } },
            contentAlignment = Alignment.Center,
        ) {
            // After the same grace as the player's: a seek or a moment's buffering is not a wait to show.
            if (waiting) RefluxLoader(size = 28.dp, delayMs = ChromeClock.LOADING_GRACE_MS)
            AnimatedVisibility(hovered && !waiting, enter = fadeIn(Motion.focusChange.tween()), exit = fadeOut(Motion.focusChange.tween())) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)), contentAlignment = Alignment.Center) {
                    Icon(Icon.FULLSCREEN, size = 26.dp)
                }
            }
        }
    }
}
