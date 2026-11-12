package dev.reflux.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusTargetModifierNode
import androidx.compose.ui.focus.Focusability
import androidx.compose.ui.focus.getFocusedRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import dev.reflux.design.Motion
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.States
import dev.reflux.ui.theme.tween
import kotlinx.coroutines.launch

/**
 * Where each of a set of destinations is, inside the navigation that holds them, so that one mark can show where the
 * viewer is and travel between them (docs/DESIGN.md, "Navigation surfaces").
 */
@Stable
internal class SelectionTrack {
    internal val places = mutableStateMapOf<Any, Rect>()
}

/** Says where [key] is in [track]; on the destination's outermost box, a direct child of what draws the mark. */
internal fun Modifier.selectionPlace(track: SelectionTrack, key: Any): Modifier =
    onPlaced { track.places[key] = Rect(it.positionInParent(), it.size.toSize()) }

/**
 * Draws where the viewer is, behind the destinations of [track]: a lit shape around [selected] and, with [bar], a short
 * bar at its leading edge (shape and light together, never a color or a brightness alone). When the viewer goes
 * somewhere else it travels there; when the same destination is moved by the layout (a rail widening) it stays with
 * it; with nowhere to be it fades away. With motion reduced it is where it belongs at once.
 *
 * It goes last in the container's modifiers, after its padding, so it draws in the space the destinations are placed in.
 *
 * @param radius the lit shape's corner; null for a pill.
 */
@Composable
internal fun Modifier.selectionMark(track: SelectionTrack, selected: Any?, radius: Dp? = null, bar: Boolean = false): Modifier {
    val target = selected?.let { track.places[it] }
    val bounds = remember { Animatable(target ?: Rect.Zero, Rect.VectorConverter) }
    val presence = remember { Animatable(0f) }
    var on by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(selected, target) {
        when {
            target == null -> {
                presence.animateTo(0f, Motion.focusChange.tween())
                on = null
            }
            on == null -> {
                bounds.snapTo(target)
                on = selected
                presence.animateTo(1f, Motion.focusChange.tween())
            }
            on != selected -> {
                on = selected
                launch { presence.animateTo(1f, Motion.focusChange.tween()) }
                bounds.animateTo(target, Motion.selectionTravel.tween())
            }
            else -> {
                launch { presence.animateTo(1f, Motion.focusChange.tween()) }
                bounds.snapTo(target)
            }
        }
    }
    val fill = States.selected
    val line = RefluxColors.contentPrimary
    return drawBehind {
        val shown = presence.value
        val place = bounds.value
        if (shown <= 0f || place.isEmpty) return@drawBehind
        val corner = radius?.toPx() ?: (place.height / 2)
        drawRoundRect(fill.copy(alpha = fill.alpha * shown), place.topLeft, place.size, CornerRadius(corner))
        if (bar) {
            val height = minOf(place.height * 0.45f, 20.dp.toPx())
            val width = 3.dp.toPx()
            drawRoundRect(
                line.copy(alpha = line.alpha * shown),
                topLeft = Offset(place.left, place.top + (place.height - height) / 2),
                size = Size(width, height),
                cornerRadius = CornerRadius(width / 2),
            )
        }
    }
}

/**
 * Where what has focus is, asked when it is needed (the toolkit's `FocusTargetModifierNode.getFocusedRect`), in place
 * of being told on every change, which the toolkit no longer promises to do.
 */
internal class FocusedBounds {
    internal var node: FocusedBoundsNode? = null

    /** The bounds of what has focus beneath [focusedBounds], in the window; null when nothing there has it. */
    fun now(): Rect? = node?.rect()
}

/** Makes this the place [holder] asks about: a group that takes no focus itself and knows where focus is inside it. */
internal fun Modifier.focusedBounds(holder: FocusedBounds): Modifier = this then FocusedBoundsElement(holder)

private data class FocusedBoundsElement(val holder: FocusedBounds) : ModifierNodeElement<FocusedBoundsNode>() {
    override fun create() = FocusedBoundsNode(holder)
    override fun update(node: FocusedBoundsNode) {
        if (node.holder !== holder) {
            if (node.holder.node === node) node.holder.node = null
            node.holder = holder
            if (node.isAttached) holder.node = node
        }
    }
}

internal class FocusedBoundsNode(var holder: FocusedBounds) : DelegatingNode() {
    private val target = delegate(FocusTargetModifierNode(focusability = Focusability.Never))

    override fun onAttach() {
        holder.node = this
    }

    override fun onDetach() {
        if (holder.node === this) holder.node = null
    }

    fun rect(): Rect? = if (isAttached && target.focusState.hasFocus) target.getFocusedRect() else null
}
