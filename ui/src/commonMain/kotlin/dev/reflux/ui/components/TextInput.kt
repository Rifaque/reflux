package dev.reflux.ui.components

import dev.reflux.ui.theme.States
import dev.reflux.ui.theme.Shapes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
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

/** What a field holds (`dev.reflux.ui.state.InputKind`), named here where fields are built. */
typealias InputKind = dev.reflux.ui.state.InputKind

/**
 * A labelled, single-line field. It is quiet until it has focus, when its edge lights up in white; a field
 * the last problem was about has a red edge until it changes. Its height follows the layout's target size,
 * so it is as easy to reach with a remote as a button is.
 *
 * Every field reports its own focus ([textEntry]), so keys are never taken for shortcuts while it has it, and a
 * controller or a remote types into it on the Reflux keyboard.
 *
 * @param onFocus reports when the field gains and loses focus, for a screen that needs to know.
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
    val shape = Shapes.medium
    // The label is part of the field: touching or clicking it is touching the field.
    val field = remember { FocusRequester() }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            // The field below is named after it: said once, there.
            Modifier.clearAndSetSemantics { }.pointerInput(enabled) { detectTapGestures { if (enabled) runCatching { field.requestFocus() } } },
            style = LocalTypography.current.captionStrong,
            color = RefluxColors.contentSecondary,
            maxLines = 1,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldModifier.focusRequester(field).textEntry(value, onValueChange, label, kind, onDone)
                .fillMaxWidth().semantics {
                    contentDescription = label
                    if (error) error("Check this field")
                }.onFocusChanged { onFocus(it.isFocused) },
            enabled = enabled,
            singleLine = true,
            interactionSource = interaction,
            textStyle = LocalTypography.current.body.copy(color = RefluxColors.contentPrimary),
            cursorBrush = SolidColor(RefluxColors.contentPrimary),
            visualTransformation = if (kind == InputKind.SECRET || kind == InputKind.PIN) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = when (kind) {
                    InputKind.ADDRESS -> KeyboardType.Uri
                    InputKind.NAME, InputKind.SEARCH -> KeyboardType.Text
                    InputKind.SECRET -> KeyboardType.Password
                    InputKind.PIN -> KeyboardType.NumberPassword
                },
                imeAction = if (last) ImeAction.Done else ImeAction.Next,
                showKeyboardOnFocus = showsKeyboardOnFocus(),
            ),
            keyboardActions = KeyboardActions(onNext = { onDone() }, onDone = { onDone() }),
            decorationBox = { inner ->
                val edge = when {
                    error -> RefluxColors.error
                    else -> States.layer(0.12f + 0.58f * lit)
                }
                Box(
                    Modifier.fillMaxWidth().heightIn(min = maxOf(48f, layout.minTargetDp).dp).clip(shape)
                        .background(States.layer(0.06f + 0.05f * lit))
                        .border(if (focused || error) 1.5.dp else 1.dp, edge, shape)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = LocalTypography.current.body, color = RefluxColors.contentTertiary, maxLines = 1)
                    }
                    inner()
                }
            },
        )
    }
}
