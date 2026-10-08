package dev.reflux.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.reflux.design.MaterialRole
import dev.reflux.design.Motion
import dev.reflux.ui.theme.LocalAccent
import dev.reflux.ui.theme.LocalLayout
import dev.reflux.ui.theme.LocalTypography
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.Text
import dev.reflux.ui.theme.glass
import dev.reflux.ui.theme.tween

/**
 * The base of everything that can be activated: pointer hover and keyboard or controller focus are the
 * same "active" state, shown by a small lift; a focus ring is added only for input that needs one
 * ([dev.reflux.design.LayoutProfile.showsFocus]), so pointer users never see stray rings.
 *
 * @param lift how much of the layout's focus scale to apply; 0 for controls that should not move.
 */
@Composable
fun Pressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    enabled: Boolean = true,
    lift: Float = 1f,
    role: Role = Role.Button,
    content: @Composable BoxScope.(active: Boolean) -> Unit,
) {
    val layout = LocalLayout.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val ring = focused && layout.showsFocus
    val active = enabled && (hovered || ring)
    val scale by animateFloatAsState(if (active) 1f + (layout.focusScale - 1f) * lift else 1f, Motion.focusChange.tween())
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (ring) Modifier.border(2.dp, RefluxColors.focusRing, shape) else Modifier)
            .clip(shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = role, onClick = onClick),
        content = { content(active) },
    )
}

enum class ButtonStyle { PRIMARY, GLASS, QUIET }

/** A text button. Its height follows the layout's minimum target size, so it grows for controllers and TVs. */
@Composable
fun Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Icon? = null,
    style: ButtonStyle = ButtonStyle.GLASS,
    enabled: Boolean = true,
) {
    val layout = LocalLayout.current
    val shape = CircleShape
    val accent = LocalAccent.current
    val height = maxOf(40f, layout.minTargetDp).dp
    Pressable(onClick, modifier, shape, enabled, lift = 0.6f) { active ->
        val surface = when (style) {
            ButtonStyle.PRIMARY -> Modifier.background(if (active) Color.White else accent)
            ButtonStyle.GLASS -> Modifier.glass(MaterialRole.INTERACTIVE, shape).background(Color.White.copy(alpha = if (active) 0.12f else 0f))
            ButtonStyle.QUIET -> Modifier.background(Color.White.copy(alpha = if (active) 0.10f else 0f))
        }
        val color = when {
            !enabled -> RefluxColors.textTertiary
            style == ButtonStyle.PRIMARY -> RefluxColors.onAccent
            else -> RefluxColors.textPrimary
        }
        Row(
            Modifier.then(surface).height(height).padding(horizontal = if (style == ButtonStyle.QUIET) 14.dp else 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, size = 18.dp, tint = color)
            Text(text, style = LocalTypography.current.bodyStrong, color = color, maxLines = 1)
        }
    }
}

/** A round icon-only button. [description] names it for accessibility. */
@Composable
fun IconButton(
    icon: Icon,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    glass: Boolean = false,
    tint: Color = RefluxColors.textPrimary,
    enabled: Boolean = true,
) {
    val layout = LocalLayout.current
    val target = maxOf(size.value, minOf(layout.minTargetDp, 56f)).dp
    Pressable(onClick, modifier.semantics { contentDescription = description }, CircleShape, enabled, lift = 0.8f) { active ->
        Box(
            Modifier.size(target)
                .then(if (glass) Modifier.glass(MaterialRole.INTERACTIVE, CircleShape) else Modifier)
                .background(Color.White.copy(alpha = if (active) 0.14f else 0f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, size = (target.value * 0.5f).dp, tint = if (enabled) tint else RefluxColors.textTertiary)
        }
    }
}

/** A small, non-interactive label: genres, quality badges, how a search was understood. */
@Composable
fun Chip(text: String, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier.clip(shape)
            .background(if (emphasized) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.10f))
            .then(if (emphasized) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = 0.10f), shape))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(text, style = LocalTypography.current.label, color = if (emphasized) RefluxColors.onAccent else RefluxColors.textSecondary, maxLines = 1)
    }
}

/** Progress through a work: a hairline that reads at a glance without competing with artwork. */
@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 3.dp, color: Color = RefluxColors.progress) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(Color.White.copy(alpha = 0.24f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(color))
    }
}

/** A section title with an optional trailing action ("See all"). */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth().defaultMinSize(minHeight = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = LocalTypography.current.title, maxLines = 1)
        if (action != null) Button(action, onAction, style = ButtonStyle.QUIET)
    }
}

/**
 * A horizontal slider for the timeline and the volume. [onChange] follows the pointer while dragging;
 * [onChangeFinished] reports where it was released.
 *
 * @param marks positions (0..1) to notch on the track, e.g. chapter starts.
 */
@Composable
fun Slider(
    fraction: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onChangeFinished: (Float) -> Unit = {},
    marks: List<Float> = emptyList(),
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val active = enabled && (hovered || dragging)
    val thickness by animateDpAsState(if (active) 6.dp else 4.dp, Motion.focusChange.tween())
    val change by rememberUpdatedState(onChange)
    val finished by rememberUpdatedState(onChangeFinished)
    Canvas(
        modifier.height(28.dp).hoverable(interaction, enabled).pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                dragging = true
                var value = (down.position.x / size.width).coerceIn(0f, 1f)
                change(value)
                drag(down.id) { moved ->
                    value = (moved.position.x / size.width).coerceIn(0f, 1f)
                    change(value)
                    moved.consume()
                }
                dragging = false
                finished(value)
            }
        },
    ) {
        val y = size.height / 2
        val stroke = thickness.toPx()
        val x = size.width * fraction.coerceIn(0f, 1f)
        drawLine(Color.White.copy(alpha = 0.26f), Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
        if (x > 0f) drawLine(Color.White, Offset(0f, y), Offset(x, y), stroke, StrokeCap.Round)
        marks.forEach { mark ->
            drawRect(Color.Black.copy(alpha = 0.6f), Offset(size.width * mark - 1.dp.toPx(), y - stroke / 2), Size(2.dp.toPx(), stroke))
        }
        if (active) drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(x, y))
    }
}
