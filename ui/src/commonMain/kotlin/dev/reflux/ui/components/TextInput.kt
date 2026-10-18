package dev.reflux.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.reflux.design.FormFactor
import dev.reflux.design.Motion
import dev.reflux.ui.theme.LocalLayout
import dev.reflux.ui.theme.LocalTypography
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.Text
import dev.reflux.ui.theme.tween

/** What a field holds, which decides the keyboard a phone or a television offers for it. */
enum class InputKind { ADDRESS, NAME, SECRET }

/**
 * A labelled, single-line field. It is quiet until it has focus, when its edge lights up in white; a field
 * the last problem was about has a red edge until it changes. Its height follows the layout's target size,
 * so it is as easy to reach with a remote as a button is.
 *
 * @param onFocus reports when typing starts and stops, so keys are not taken for shortcuts meanwhile.
 * @param onDone what the keyboard's action key does: go on to the next field, or submit.
 */
@Composable
fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    /** For the field itself rather than the label and field together: where focus goes from it, for one. */
    fieldModifier: Modifier = Modifier,
    kind: InputKind = InputKind.NAME,
    placeholder: String? = null,
    last: Boolean = false,
    error: Boolean = false,
    enabled: Boolean = true,
    onFocus: (Boolean) -> Unit = {},
    onDone: () -> Unit = {},
) {
    val layout = LocalLayout.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val lit by animateFloatAsState(if (focused) 1f else 0f, Motion.focusChange.tween())
    val shape = RoundedCornerShape(14.dp)
    // The label is part of the field: touching or clicking it is touching the field.
    val field = remember { FocusRequester() }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            Modifier.pointerInput(enabled) { detectTapGestures { if (enabled) runCatching { field.requestFocus() } } },
            style = LocalTypography.current.captionStrong,
            color = RefluxColors.textSecondary,
            maxLines = 1,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldModifier.focusRequester(field).keyboardByRemote()
                .fillMaxWidth().semantics { contentDescription = label }.onFocusChanged { onFocus(it.isFocused) },
            enabled = enabled,
            singleLine = true,
            interactionSource = interaction,
            textStyle = LocalTypography.current.body.copy(color = RefluxColors.textPrimary),
            cursorBrush = SolidColor(RefluxColors.textPrimary),
            visualTransformation = if (kind == InputKind.SECRET) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = when (kind) {
                    InputKind.ADDRESS -> KeyboardType.Uri
                    InputKind.NAME -> KeyboardType.Text
                    InputKind.SECRET -> KeyboardType.Password
                },
                imeAction = if (last) ImeAction.Done else ImeAction.Next,
                showKeyboardOnFocus = showsKeyboardOnFocus(),
            ),
            keyboardActions = KeyboardActions(onNext = { onDone() }, onDone = { onDone() }),
            decorationBox = { inner ->
                val edge = when {
                    error -> RefluxColors.error
                    else -> Color.White.copy(alpha = 0.12f + 0.58f * lit)
                }
                Box(
                    Modifier.fillMaxWidth().height(maxOf(48f, layout.minTargetDp).dp).clip(shape)
                        .background(Color.White.copy(alpha = 0.06f + 0.05f * lit))
                        .border(if (focused || error) 1.5.dp else 1.dp, edge, shape)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = LocalTypography.current.body, color = RefluxColors.textTertiary, maxLines = 1)
                    }
                    inner()
                }
            },
        )
    }
}

/**
 * How a text field takes a remote. Focus only passes through a field on its way somewhere, so a television
 * keeps the keyboard, which takes over the remote while it is up, away until OK asks for it; and OK opens it
 * when it is let go of. A keyboard opened as focus arrives, or on the press, takes the press or its release
 * for its own: Fire TV's then types the key it starts on, so a search began with an "a". Every field a
 * remote can reach uses this, with [showsKeyboardOnFocus] in its keyboard options.
 */
@Composable
internal fun Modifier.keyboardByRemote(): Modifier {
    val television = LocalLayout.current.formFactor == FormFactor.TV
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    // Whether OK has asked for the keyboard since focus arrived; until it has, a television keeps it away.
    var asked by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (!focused) asked = false
        if (!television || !focused) return@LaunchedEffect
        // The field opens the keyboard by itself as it takes focus, a moment later.
        repeat(KEYBOARD_PATIENCE_FRAMES) {
            withFrameNanos { }
            if (!asked) keyboard?.hide()
        }
    }
    return onFocusChanged { focused = it.isFocused }.onPreviewKeyEvent { event ->
        if (event.key != Key.DirectionCenter) return@onPreviewKeyEvent false
        if (event.type == KeyEventType.KeyUp) {
            asked = true
            keyboard?.show()
        }
        true
    }
}

/** Whether a field opens the keyboard as it takes focus: everywhere but on a television ([keyboardByRemote]). */
@Composable
internal fun showsKeyboardOnFocus(): Boolean = LocalLayout.current.formFactor != FormFactor.TV

/** How many frames after focus arrives a television keeps hiding a keyboard nobody asked for. */
private const val KEYBOARD_PATIENCE_FRAMES = 20
