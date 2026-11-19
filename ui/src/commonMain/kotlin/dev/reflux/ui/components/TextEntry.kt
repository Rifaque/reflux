package dev.reflux.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import dev.reflux.core.input.InputModality
import dev.reflux.design.FormFactor
import dev.reflux.ui.state.KeyboardRequest
import dev.reflux.ui.theme.LocalLayout

/**
 * What a text field needs from the application (B-025's contract, docs/INPUT_MODEL.md, "Event priority"): to say
 * that it has focus, so that keys type instead of acting (one way for every field, never set by a screen), and to
 * open the Reflux keyboard for a controller or a remote (B-023).
 */
interface TextEntry {
    /** [field] has focus, or no longer has it. Typing is active while any field has focus. */
    fun focused(field: Any, focused: Boolean)

    /** Opens the Reflux keyboard for a field. */
    fun open(request: KeyboardRequest)

    /** Closes the Reflux keyboard if it is [field]'s. */
    fun close(field: Any)

    /** Where the Reflux keyboard's cursor is in [field], while it is open for [field]; else null. */
    fun cursorOf(field: Any): Int? = null
}

/**
 * A field's value for the toolkit, with its caret where the Reflux keyboard's cursor is while the keyboard types into it
 * (the walk on Windows, 2026-11-19: the caret stayed at the start while the bumpers moved the keyboard's cursor). Otherwise
 * the field keeps its own selection, as a keyboard, a pointer or a finger leaves it.
 */
@Composable
internal fun rememberFieldValue(field: Any, value: String, onValueChange: (String) -> Unit): Pair<androidx.compose.ui.text.input.TextFieldValue, (androidx.compose.ui.text.input.TextFieldValue) -> Unit> {
    val entry = LocalTextEntry.current
    var held by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(value, androidx.compose.ui.text.TextRange(value.length))) }
    val typing = entry?.cursorOf(field)
    val shown = when {
        typing != null && held.text == value -> held.copy(selection = androidx.compose.ui.text.TextRange(typing.coerceIn(0, value.length)))
        held.text == value -> held
        else -> androidx.compose.ui.text.input.TextFieldValue(value, androidx.compose.ui.text.TextRange((typing ?: value.length).coerceIn(0, value.length)))
    }
    if (shown != held) held = shown
    val change by rememberUpdatedState(onValueChange)
    return shown to { next ->
        held = next
        if (next.text != value) change(next.text)
    }
}

val LocalTextEntry = staticCompositionLocalOf<TextEntry?> { null }

/**
 * The fields that have focus now. Several can say so in either order while focus passes from one to another, so a
 * set, not a flag: typing is active while it is not empty.
 */
class TextEditing(private val changed: (Boolean) -> Unit) {
    private val fields = HashSet<Any>()

    fun report(field: Any, focused: Boolean) {
        if (focused) fields += field else fields -= field
        changed(fields.isNotEmpty())
    }
}

/**
 * Makes a field one of Reflux's text fields, for every input:
 *
 * - it says when it has focus ([TextEntry.focused]), so keys type into it and are never shortcuts (B-019 to B-021);
 * - with a controller, confirm on it opens the Reflux keyboard (it is the focused control, as a button is), and with
 *   a remote, OK does, on its release so that the press is not typed (B-004, B-011, B-023);
 * - under directional input, and always on a television, the system keyboard is kept away: it cannot be driven with a
 *   D-pad on a tablet, and on a television its lifecycle is not Reflux's (it closed itself, and OK did not reliably
 *   bring it back: B-004). A touchscreen keeps its system keyboard, the better one for a finger.
 */
@Composable
internal fun Modifier.textEntry(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    kind: InputKind,
    onDone: () -> Unit,
    /** Who the field is, for [rememberFieldValue] to find the keyboard's cursor in it. */
    field: Any = remember { Any() },
): Modifier {
    val entry = LocalTextEntry.current
    val control = LocalFocusedControl.current
    val layout = LocalLayout.current
    val keyboard = LocalSoftwareKeyboardController.current
    val self = field
    val text by rememberUpdatedState(value)
    val write by rememberUpdatedState(onValueChange)
    val finish by rememberUpdatedState(onDone)
    val name by rememberUpdatedState(label)
    var focused by remember { mutableStateOf(false) }
    val open = remember(entry, kind) {
        {
            keyboard?.hide()
            entry?.open(KeyboardRequest(self, name, kind, { text }, { write(it) }, { finish() }))
            Unit
        }
    }
    DisposableEffect(entry) {
        onDispose {
            entry?.focused(self, false)
            entry?.close(self)
        }
    }
    if (focused && control != null) {
        DisposableEffect(control, open) {
            control.activate = open
            onDispose { if (control.activate === open) control.activate = null }
        }
    }
    val awayFromSystemKeyboard = layout.formFactor == FormFactor.TV || layout.modality == InputModality.DIRECTIONAL
    LaunchedEffect(focused, awayFromSystemKeyboard) {
        if (!focused || !awayFromSystemKeyboard) return@LaunchedEffect
        // A field can open the system keyboard by itself as it takes focus, a moment later.
        repeat(KEYBOARD_PATIENCE_FRAMES) {
            withFrameNanos { }
            keyboard?.hide()
        }
    }
    return onFocusChanged {
        focused = it.isFocused
        entry?.focused(self, it.isFocused)
    }.onPreviewKeyEvent { event ->
        // A remote's OK (and a keyboard's numeric-pad centre): the Reflux keyboard, on release.
        if (event.key != Key.DirectionCenter) return@onPreviewKeyEvent false
        if (event.type == KeyEventType.KeyUp) open()
        true
    }
}

/** Whether a field opens the system keyboard as it takes focus: on a touchscreen and a desk, not under directional input or on a television. */
@Composable
internal fun showsKeyboardOnFocus(): Boolean {
    val layout = LocalLayout.current
    return layout.formFactor != FormFactor.TV && layout.modality != InputModality.DIRECTIONAL
}

/** How many frames after focus arrives a field keeps hiding a system keyboard nobody asked for. */
private const val KEYBOARD_PATIENCE_FRAMES = 20
