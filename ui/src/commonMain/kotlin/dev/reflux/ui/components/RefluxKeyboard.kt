package dev.reflux.ui.components

import dev.reflux.core.input.InputModality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.core.input.InputMapping
import dev.reflux.design.FormFactor
import dev.reflux.design.MaterialRole
import dev.reflux.ui.state.KeyboardKey
import dev.reflux.ui.state.KeyboardPage
import dev.reflux.ui.state.KeyboardSession
import dev.reflux.ui.state.ShiftState
import dev.reflux.ui.theme.LocalLayout
import dev.reflux.ui.theme.LocalTypography
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.Shapes
import dev.reflux.ui.theme.States
import dev.reflux.ui.theme.Text
import dev.reflux.ui.theme.glass

/**
 * The Reflux keyboard (B-023): a controller's or a remote's way to type, at the foot of the screen, over everything
 * while it is open. The chosen key is lit (it is chosen, not toolkit focus: directions move it, so it never leaves the
 * keyboard for the page behind). A finger or a pointer presses a key where it is. What is being typed is shown above
 * the keys with its cursor, hidden for a password or a PIN, because the field itself may be under the keyboard.
 *
 * @param prompts what the controller's buttons do here, in its own terms, or null without a controller in hand.
 */
@Composable
fun RefluxKeyboard(session: KeyboardSession, onPress: (row: Int, column: Int) -> Unit, prompts: ControllerFamily?, modifier: Modifier = Modifier) {
    val layout = LocalLayout.current
    val lit = layout.modality == InputModality.DIRECTIONAL || layout.formFactor == FormFactor.TV
    val keyHeight = when (layout.formFactor) {
        FormFactor.TV -> 40.dp
        FormFactor.PHONE -> 44.dp
        else -> 46.dp
    }
    Column(
        modifier.widthIn(max = if (session.kind == dev.reflux.ui.state.InputKind.PIN) 420.dp else 920.dp).fillMaxWidth()
            .glass(MaterialRole.MODAL, Shapes.xlarge).absorbsPointer()
            .pane("Keyboard, ${session.label}")
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Preview(session)
        session.rows.forEachIndexed { rowIndex, keys ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                keys.forEachIndexed { columnIndex, key ->
                    // The chosen key is lit for a controller or a remote, which move it; a pointer or a finger presses keys where
                    // they are, and leaves none lit behind it (B-051).
                    val chosen = lit && rowIndex == session.row && columnIndex == session.column
                    KeyCap(session, key, chosen, Modifier.weight(key.weight).height(keyHeight)) { onPress(rowIndex, columnIndex) }
                }
            }
        }
        if (prompts != null) {
            val list = buildList {
                add(InputMapping.cancelButton(prompts) to "Close")
                add(GamepadButton.FACE_WEST to "Erase")
                if (session.kind != dev.reflux.ui.state.InputKind.PIN) add(GamepadButton.FACE_NORTH to "Space")
                add(GamepadButton.START to "Done")
            }
            ButtonPrompts(list, prompts, Modifier.padding(top = 2.dp), floating = false)
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
private fun KeyCap(session: KeyboardSession, key: KeyboardKey, chosen: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val name = keyName(session, key)
    val on = (key == KeyboardKey.Shift && session.shift != ShiftState.OFF)
    val shape = Shapes.small
    // A pointer's or a finger's press (B-051): the key sinks a little and brightens while it is down, and for a moment after a
    // click too quick to see, then is itself again. Never a lit key left behind.
    val interaction = remember { MutableInteractionSource() }
    val down by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    var flash by remember { mutableIntStateOf(0) }
    var flashing by remember { mutableStateOf(false) }
    LaunchedEffect(flash) {
        if (flash == 0) return@LaunchedEffect
        flashing = true
        kotlinx.coroutines.delay(KEY_FLASH_MS)
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
        modifier.graphicsLayer { scaleX = sink; scaleY = sink }.clip(shape)
            .background(if (chosen) RefluxColors.contentPrimary else States.layer(tone))
            .then(if (key == KeyboardKey.Shift && session.shift == ShiftState.LOCKED) Modifier.border(2.dp, RefluxColors.contentPrimary, shape) else Modifier)
            .hoverable(interaction)
            .clickable(interaction, indication = null) {
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
    KeyboardKey.Shift -> "Shift"
    KeyboardKey.Page -> if (session.page == KeyboardPage.LETTERS) "&123" else "ABC"
    KeyboardKey.Space -> "Space"
    KeyboardKey.Erase -> "Erase"
    KeyboardKey.Clear -> "Clear"
    KeyboardKey.CursorBack, KeyboardKey.CursorForward -> ""
    KeyboardKey.Done -> "Done"
}

/** What a key is called to a screen reader, and in tests. */
internal fun keyName(session: KeyboardSession, key: KeyboardKey): String = when (key) {
    is KeyboardKey.Type -> session.typed(key)
    KeyboardKey.Shift -> when (session.shift) {
        ShiftState.OFF -> "Shift"
        ShiftState.ONCE -> "Shift, on for the next letter"
        ShiftState.LOCKED -> "Shift, capitals on"
    }
    KeyboardKey.Page -> if (session.page == KeyboardPage.LETTERS) "Symbols" else "Letters"
    KeyboardKey.Space -> "Space"
    KeyboardKey.Erase -> "Erase"
    KeyboardKey.Clear -> "Clear"
    KeyboardKey.CursorBack -> "Cursor back"
    KeyboardKey.CursorForward -> "Cursor forward"
    KeyboardKey.Done -> "Done"
}
