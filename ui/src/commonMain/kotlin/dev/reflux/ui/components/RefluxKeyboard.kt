package dev.reflux.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.core.input.InputMapping
import dev.reflux.design.FormFactor
import dev.reflux.design.MaterialRole
import dev.reflux.ui.state.InputKind
import dev.reflux.ui.state.KeyboardKey
import dev.reflux.ui.state.KeyboardLayout
import dev.reflux.ui.state.KeyboardMode
import dev.reflux.ui.state.KeyboardPage
import dev.reflux.ui.state.KeyboardSession
import dev.reflux.ui.state.PlacedKey
import dev.reflux.ui.state.ShiftState
import dev.reflux.ui.theme.LocalLayout
import dev.reflux.ui.theme.LocalTypography
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.Shapes
import dev.reflux.ui.theme.States
import dev.reflux.ui.theme.Text
import dev.reflux.ui.theme.glass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The Reflux keyboard (B-023, F-P4): one keyboard with two presentations of the same keys. Under a controller or a
 * remote, the controller's grid with the chosen key lit (it is chosen, not toolkit focus: directions move it, so it
 * never leaves the keyboard for the page behind). Under a pointer on a desk (Windows, Linux), a picture of a physical
 * keyboard in the system's layout, pressed where it is, with nothing left lit. When the hand changes, each key moves
 * and resizes to its place in the other presentation (planning/F-P4-ADAPTIVE-KEYBOARD.md §8); keys only one has fade
 * out first and in last; with reduced motion the change is a short crossfade. What is typed, the cursor, Shift and the
 * page are the session's and never change with it.
 *
 * @param onKey a key pressed by a pointer or a finger where it is drawn.
 * @param onComplete a title completion chosen by a pointer or a finger.
 * @param onPointerTravel the pointer moved over the keyboard by this many dp (for the presentation's arbiter, §7).
 * @param prompts what the controller's buttons do here, in its own terms, or null without a controller in hand.
 */
@Composable
fun RefluxKeyboard(
    session: KeyboardSession,
    onKey: (KeyboardKey) -> Unit,
    prompts: ControllerFamily?,
    modifier: Modifier = Modifier,
    onComplete: (Int) -> Unit = {},
    onPointerTravel: (Float) -> Unit = {},
) {
    val layout = LocalLayout.current
    val shown = session.layout
    val controller = session.mode == KeyboardMode.CONTROLLER
    val keyHeight = when {
        layout.formFactor == FormFactor.TV -> 40.dp
        layout.formFactor == FormFactor.PHONE -> 44.dp
        controller -> 46.dp
        else -> 40.dp
    }
    // A key unit: the controller's keys are larger targets; a desk keyboard is a desk keyboard's proportions (u ≈ 40 dp).
    val maxUnit = if (controller) (if (layout.formFactor == FormFactor.TV) 66.dp else 74.dp) else 42.dp
    val travel by rememberUpdatedState(onPointerTravel)
    val density = LocalDensity.current
    Column(
        modifier.widthIn(max = if (session.kind == InputKind.PIN) 420.dp else 980.dp).fillMaxWidth()
            .glass(MaterialRole.MODAL, Shapes.xlarge).absorbsPointer()
            .pane("Keyboard, ${session.label}")
            .pointerInput(Unit) {
                // Pointer travel over the keyboard, for switching presentations only after a deliberate move (§7).
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type != PointerEventType.Move) continue
                        val change = event.changes.firstOrNull() ?: continue
                        if (change.type != androidx.compose.ui.input.pointer.PointerType.Mouse) continue
                        val moved = change.positionChange()
                        travel((abs(moved.x) + abs(moved.y)) / density.density)
                    }
                }
            }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Preview(session)
            shown.note?.let { note ->
                Text(note, Modifier.align(Alignment.TopEnd).padding(8.dp).semantics { testTag = "keyboard-note" }, style = LocalTypography.current.caption, color = RefluxColors.contentTertiary, maxLines = 1)
            }
        }
        if (session.completions.isNotEmpty()) Completions(session, onComplete)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val unit = minOf(maxWidth / shown.width, maxUnit)
            KeyField(session, shown, unit, keyHeight, onKey)
        }
        if (prompts != null) {
            val list = buildList {
                add(InputMapping.cancelButton(prompts) to "Close")
                add(GamepadButton.FACE_WEST to "Erase")
                if (session.kind != InputKind.PIN) add(GamepadButton.FACE_NORTH to "Space")
                add(GamepadButton.START to "Done")
            }
            ButtonPrompts(list, prompts, Modifier.padding(top = 2.dp), floating = false)
        }
    }
}

/** The gap between keys. */
private val KEY_GAP = 6.dp

/**
 * Every key of [shown] at its place, [unit] wide and [keyHeight] tall a unit. Keys are composed by identity, so a key
 * keeps its node, its press state and its semantics while it moves to its place in another layout.
 */
@Composable
private fun KeyField(session: KeyboardSession, shown: KeyboardLayout, unit: Dp, keyHeight: Dp, onKey: (KeyboardKey) -> Unit) {
    val reduce = LocalAccessibility.current.reduceMotion
    // Keys that just left the layout are drawn where they were while they fade out (§8).
    val leaving = remember { mutableStateMapOf<KeyboardKey, PlacedKey>() }
    var last by remember { mutableStateOf(shown) }
    if (last !== shown) {
        last.keys.filter { it.key !in shown }.forEach { leaving[it.key] = it }
        shown.keys.forEach { leaving.remove(it.key) }
        last = shown
    }
    val width by animateDpAsState(unit * shown.width, if (reduce) tween(0) else spring(dampingRatio = 0.9f, stiffness = 500f))
    val height by animateDpAsState(keyHeight * shown.height + KEY_GAP * (shown.height - 1), if (reduce) tween(0) else spring(dampingRatio = 0.9f, stiffness = 500f))
    // Reduced motion: no key travels; the presentation crossfades in 150 ms instead.
    val fade = remember { Animatable(1f) }
    LaunchedEffect(session.mode) {
        if (!reduce) return@LaunchedEffect
        fade.snapTo(0f)
        fade.animateTo(1f, tween(150))
    }
    Box(Modifier.fillMaxWidth()) { Box(Modifier.width(width).height(height).align(Alignment.TopCenter).graphicsLayer { alpha = fade.value }) {
        for (placed in shown.keys) {
            key(placed.key) {
                MovingKey(session, placed, unit, keyHeight, reduce, entering = true, onKey)
            }
        }
        for ((gone, placed) in leaving.toMap()) {
            key("leaving", gone) {
                MovingKey(session, placed, unit, keyHeight, reduce, entering = false, onKey)
                LaunchedEffect(Unit) {
                    delay(LEAVE_MS)
                    leaving.remove(gone)
                }
            }
        }
    } }
}

private const val LEAVE_MS = 120L

/** A key moving to [placed]: position and size on one spring, read where the key is placed, so nothing recomposes per frame. */
@Composable
private fun MovingKey(session: KeyboardSession, placed: PlacedKey, unit: Dp, keyHeight: Dp, reduce: Boolean, entering: Boolean, onKey: (KeyboardKey) -> Unit) {
    val density = LocalDensity.current
    val target = with(density) {
        floatArrayOf(
            (unit * placed.x).toPx(), ((keyHeight + KEY_GAP) * placed.y).toPx(),
            (unit * placed.width - KEY_GAP).toPx(), (keyHeight * placed.height + KEY_GAP * (placed.height - 1)).toPx(),
        )
    }
    val rect = remember { target.map { Animatable(it) } }
    LaunchedEffect(target[0], target[1], target[2], target[3]) {
        rect.forEachIndexed { i, value ->
            launch { if (reduce) value.snapTo(target[i]) else value.animateTo(target[i], spring(dampingRatio = 0.9f, stiffness = 500f)) }
        }
    }
    // Entering keys fade and grow in once their neighbours are under way; leaving ones fade and shrink out at once.
    val presence = remember { Animatable(if (entering) 0f else 1f) }
    LaunchedEffect(entering) {
        if (reduce) presence.snapTo(if (entering) 1f else 0f)
        else if (entering) presence.animateTo(1f, tween(180, delayMillis = if (presence.value == 0f) 120 else 0))
        else presence.animateTo(0f, tween(LEAVE_MS.toInt()))
    }
    // The chosen key is lit for a controller or a remote, which move it; a pointer or a finger presses keys where they are,
    // and leaves none lit behind it (B-051), also where the controller's grid is the only presentation.
    val directional = LocalLayout.current.let { it.modality == dev.reflux.core.input.InputModality.DIRECTIONAL || it.formFactor == FormFactor.TV }
    val lit = directional && session.mode == KeyboardMode.CONTROLLER && session.completion == null && session.chosen == placed.key && entering
    KeyCap(
        session, placed.key, lit,
        Modifier
            .layout { measurable, _ ->
                val w = rect[2].value.toInt().coerceAtLeast(0)
                val h = rect[3].value.toInt().coerceAtLeast(0)
                val p = measurable.measure(Constraints.fixed(w, h))
                layout(w, h) { p.place(rect[0].value.toInt(), rect[1].value.toInt()) }
            }
            .graphicsLayer {
                alpha = presence.value
                val scale = 0.92f + 0.08f * presence.value
                scaleX = scale
                scaleY = scale
            },
        enabled = entering,
    ) { onKey(placed.key) }
}

/** Title completions (D2): a row above the keys; lit only when a controller moved into it. */
@Composable
private fun Completions(session: KeyboardSession, onComplete: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().semantics { contentDescription = "Suggestions, ${session.completions.size} titles" }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        session.completions.forEachIndexed { index, title ->
            val lit = session.completion == index
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            Box(
                Modifier.weight(1f, fill = false).clip(Shapes.small)
                    .background(if (lit) RefluxColors.contentPrimary else States.layer(if (hovered) 0.14f else 0.08f))
                    .hoverable(interaction)
                    .clickable(interaction, indication = null) { onComplete(index) }
                    .semantics {
                        contentDescription = title
                        role = Role.Button
                        selected = lit
                        testTag = "completion:$title"
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(title, style = LocalTypography.current.bodyStrong, color = if (lit) RefluxColors.background else RefluxColors.contentPrimary, maxLines = 1)
            }
        }
    }
}

/** The field's name and its text so far, with the cursor where typing goes. */
@Composable
private fun Preview(session: KeyboardSession) {
    val text = session.text
    val shown = if (session.secret) "•".repeat(text.length) else text
    val at = session.cursor.coerceIn(0, shown.length)
    Column(
        Modifier.fillMaxWidth().clip(Shapes.medium).background(States.layer(0.06f)).padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics { contentDescription = if (session.secret) "${session.label}, ${text.length} characters" else "${session.label}, $text"; testTag = "keyboard-text:" + (if (session.secret) shown else text) + "|" + at },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(session.label, style = LocalTypography.current.caption, color = RefluxColors.contentTertiary, maxLines = 1)
        Row(Modifier.heightIn(min = 26.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(shown.substring(0, at), style = LocalTypography.current.body, maxLines = 1)
            Box(Modifier.width(2.dp).height(20.dp).background(RefluxColors.contentPrimary))
            Text(shown.substring(at), style = LocalTypography.current.body, maxLines = 1)
        }
    }
}

@Composable
private fun KeyCap(session: KeyboardSession, key: KeyboardKey, chosen: Boolean, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val name = keyName(session, key)
    val on = (key == KeyboardKey.Shift || key == KeyboardKey.ShiftRight) && session.shift == ShiftState.ONCE ||
        key == KeyboardKey.Caps && session.shift == ShiftState.LOCKED ||
        key == KeyboardKey.Shift && session.mode == KeyboardMode.CONTROLLER && session.shift != ShiftState.OFF
    val shape = Shapes.small
    // A pointer's or a finger's press (B-051): the key sinks a little and brightens while it is down, and for a moment after a
    // click too quick to see, then is itself again. Never a lit key left behind.
    val interaction = remember { MutableInteractionSource() }
    val down by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered) session.hovering(key) }
    var flash by remember { mutableIntStateOf(0) }
    var flashing by remember { mutableStateOf(false) }
    LaunchedEffect(flash) {
        if (flash == 0) return@LaunchedEffect
        flashing = true
        delay(KEY_FLASH_MS)
        flashing = false
    }
    val pressed = !chosen && (down || flashing)
    val sink by animateFloatAsState(if (pressed) 0.93f else 1f, tween(if (pressed) 60 else 140))
    val tone by animateFloatAsState(
        when {
            pressed -> 0.26f
            on -> 0.22f
            hovered && !chosen -> 0.13f
            else -> 0.08f
        },
        tween(if (pressed) 60 else 160),
    )
    Box(
        modifier.graphicsLayer { scaleX *= sink; scaleY *= sink }.clip(shape)
            .background(if (chosen) RefluxColors.contentPrimary else States.layer(tone))
            .then(if (key == KeyboardKey.Shift && session.mode == KeyboardMode.CONTROLLER && session.shift == ShiftState.LOCKED) Modifier.border(2.dp, RefluxColors.contentPrimary, shape) else Modifier)
            .hoverable(interaction, enabled)
            .clickable(interaction, indication = null, enabled = enabled) {
                flash++
                onClick()
            }
            .semantics {
                contentDescription = name
                role = Role.Button
                selected = chosen
                testTag = "key:" + name
            },
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (chosen) RefluxColors.background else RefluxColors.contentPrimary
        when (key) {
            KeyboardKey.CursorBack -> Icon(Icon.BACK, size = 18.dp, tint = tint)
            KeyboardKey.CursorForward -> Icon(Icon.CHEVRON_RIGHT, size = 18.dp, tint = tint)
            else -> Text(label(session, key), style = LocalTypography.current.bodyStrong, color = tint, maxLines = 1)
        }
    }
}

/** How long a clicked key stays pressed-looking after a click too quick to see (B-051). */
private const val KEY_FLASH_MS = 110L

/** What a key shows. */
private fun label(session: KeyboardSession, key: KeyboardKey): String = when (key) {
    is KeyboardKey.Type -> session.typed(key)
    KeyboardKey.Shift, KeyboardKey.ShiftRight -> "Shift"
    KeyboardKey.Caps -> "Caps"
    KeyboardKey.Page -> if (session.page == KeyboardPage.LETTERS) "&123" else "ABC"
    KeyboardKey.Space -> "Space"
    KeyboardKey.Erase -> if (session.mode == KeyboardMode.POINTER) "Backspace" else "Erase"
    KeyboardKey.Clear -> "Clear"
    KeyboardKey.CursorBack, KeyboardKey.CursorForward -> ""
    KeyboardKey.Done -> if (session.mode == KeyboardMode.POINTER) "Enter" else "Done"
    KeyboardKey.Hide -> "Hide"
}

/** What a key is called to a screen reader, and in tests: the same in both presentations, so a key is one key. */
internal fun keyName(session: KeyboardSession, key: KeyboardKey): String = when (key) {
    is KeyboardKey.Type -> session.typed(key)
    KeyboardKey.Shift -> when (session.shift) {
        ShiftState.OFF -> "Shift"
        ShiftState.ONCE -> "Shift, on for the next letter"
        ShiftState.LOCKED -> "Shift, capitals on"
    }
    KeyboardKey.ShiftRight -> if (session.shift == ShiftState.ONCE) "Right Shift, on for the next letter" else "Right Shift"
    KeyboardKey.Caps -> if (session.shift == ShiftState.LOCKED) "Caps Lock, on" else "Caps Lock"
    KeyboardKey.Page -> if (session.page == KeyboardPage.LETTERS) "Symbols" else "Letters"
    KeyboardKey.Space -> "Space"
    KeyboardKey.Erase -> "Erase"
    KeyboardKey.Clear -> "Clear"
    KeyboardKey.CursorBack -> "Cursor back"
    KeyboardKey.CursorForward -> "Cursor forward"
    KeyboardKey.Done -> "Done"
    KeyboardKey.Hide -> "Hide keyboard"
}
