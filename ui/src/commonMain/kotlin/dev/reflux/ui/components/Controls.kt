package dev.reflux.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.dp
import dev.reflux.core.input.InputModality
import dev.reflux.design.FormFactor
import dev.reflux.design.MaterialRole
import dev.reflux.design.Motion
import dev.reflux.ui.theme.LocalLayout
import dev.reflux.ui.theme.LocalTypography
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.Text
import dev.reflux.ui.theme.glass
import dev.reflux.ui.theme.tween

/**
 * What a semantic Select activates: the control that has focus. Keys reach a focused control through the
 * toolkit; a controller button has no key, so the focused [Pressable] offers its action here instead.
 */
class FocusedControl {
    internal var activate: (() -> Unit)? = null

    /** Activates the focused control. Returns false when nothing activatable has focus. */
    fun activate(): Boolean = activate?.let { it(); true } ?: false
}

val LocalFocusedControl = staticCompositionLocalOf<FocusedControl?> { null }

/**
 * Makes a surface solid to the pointer: taps, hovers, and scrolls on it stop here instead of reaching what
 * is drawn beneath it. Floating chrome needs this, or a click on its background lands on the content below.
 */
fun Modifier.absorbsPointer(): Modifier = pointerInput(Unit) { detectTapGestures { } }

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
    /** What activating it does, for a screen reader's "double tap to …", where its name does not say. */
    actionLabel: String? = null,
    content: @Composable BoxScope.(active: Boolean) -> Unit,
) {
    val layout = LocalLayout.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val ring = focused && layout.showsFocus
    val active = enabled && (hovered || ring)
    val focusedControl = LocalFocusedControl.current
    val click by rememberUpdatedState(onClick)
    if (focusedControl != null && focused && enabled) {
        DisposableEffect(focusedControl) {
            val action = { click() }
            focusedControl.activate = action
            onDispose { if (focusedControl.activate === action) focusedControl.activate = null }
        }
    }
    val scale by animateFloatAsState(if (active) 1f + (layout.focusScale - 1f) * lift else 1f, Motion.focusChange.tween())
    // From a sofa a hairline is not enough: what a controller or a remote has focus on is also lit from behind.
    val glow by animateFloatAsState(if (ring && layout.modality == InputModality.DIRECTIONAL) 1f else 0f, Motion.focusChange.tween())
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .focusGlow(shape, glow)
            .then(if (ring) Modifier.focusRing(shape) else Modifier)
            .clip(shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClickLabel = actionLabel, role = role, onClick = onClick),
        content = { content(active) },
    )
}

/**
 * A block of text a keyboard or a remote can rest on, so that text longer than the screen is read by moving
 * through it: focus brings each block into view, with the same ring and glow as a control. It is never an action.
 */
@Composable
fun Modifier.readable(shape: Shape = RoundedCornerShape(14.dp)): Modifier {
    val layout = LocalLayout.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ring = focused && layout.showsFocus
    val glow by animateFloatAsState(if (ring && layout.modality == InputModality.DIRECTIONAL) 1f else 0f, Motion.focusChange.tween())
    return this.focusGlow(shape, glow).then(if (ring) Modifier.focusRing(shape) else Modifier).focusable(interactionSource = interaction)
}

/**
 * A white ring set just outside [shape], with a sliver of the page between them, so it reads the same around
 * a white button as around a poster or a dark row.
 */
private fun Modifier.focusRing(shape: Shape): Modifier = drawBehind {
    val gap = RING_GAP.toPx()
    val stroke = RING_WIDTH.toPx()
    val spread = gap + stroke / 2
    val outline = shape.createOutline(Size(size.width + spread * 2, size.height + spread * 2), layoutDirection, this)
    translate(-spread, -spread) { drawOutline(outline, RefluxColors.focusRing, style = Stroke(stroke)) }
}

private val RING_GAP = 3.dp
private val RING_WIDTH = 2.dp

/** How far a focus ring reaches outside its control. A list that clips leaves this much room at its ends. */
val FocusRingReach = RING_GAP + RING_WIDTH + 1.dp

/** A soft light around [shape], fading outwards from the focus ring. Drawn outside the control's own bounds. */
private fun Modifier.focusGlow(shape: Shape, strength: Float): Modifier = if (strength <= 0f) this else drawBehind {
    val step = 2.dp.toPx()
    val start = (RING_GAP + RING_WIDTH).toPx()
    for (ring in 1..GLOW_RINGS) {
        val spread = start + ring * step
        val outline = shape.createOutline(Size(size.width + spread * 2, size.height + spread * 2), layoutDirection, this)
        translate(-spread, -spread) {
            drawOutline(outline, Color.White.copy(alpha = 0.16f * strength * (1f - ring / (GLOW_RINGS + 1f))), style = Stroke(step))
        }
    }
}

private const val GLOW_RINGS = 6

/**
 * [PRIMARY] is the one thing a screen exists to do (play, connect, use this folder): bright and neutral, so it
 * leads without a color of its own. A screen has at most one. [GLASS] is a frosted secondary action that holds
 * its own over artwork; [QUIET] is text that lights up when it is pointed at.
 */
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
    /** Its name for a screen reader, where [text] is only a value ("1×"); those words are then not said as well. */
    name: String? = null,
) {
    val layout = LocalLayout.current
    val shape = CircleShape
    val height = maxOf(40f, layout.minTargetDp).dp
    Pressable(onClick, if (name != null) modifier.semantics { contentDescription = name } else modifier, shape, enabled, lift = 0.6f) { active ->
        val surface = when (style) {
            // Active is the same surface lit fully; what cannot be done right now is not lit at all.
            ButtonStyle.PRIMARY -> Modifier.background(
                if (!enabled) Color.White.copy(alpha = 0.08f) else if (active) RefluxColors.primaryAction else RefluxColors.primaryAction.copy(alpha = 0.9f),
            )
            ButtonStyle.GLASS -> Modifier.glass(MaterialRole.INTERACTIVE, shape).background(Color.White.copy(alpha = if (active) 0.14f else 0f))
            ButtonStyle.QUIET -> Modifier.background(Color.White.copy(alpha = if (active) 0.10f else 0f))
        }
        val color = when {
            !enabled -> RefluxColors.textTertiary
            style == ButtonStyle.PRIMARY -> RefluxColors.onPrimaryAction
            else -> RefluxColors.textPrimary
        }
        Row(
            // At least this tall, so a large text size grows the button rather than cutting its words.
            Modifier.then(surface).heightIn(min = height).padding(horizontal = if (style == ButtonStyle.QUIET) 14.dp else 22.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, size = 18.dp, tint = color)
            Text(text, if (name != null) Modifier.clearAndSetSemantics { } else Modifier, style = LocalTypography.current.bodyStrong, color = color, maxLines = 1)
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
    /** Switched on (a favorite, a watched mark): shown by a lit surface rather than by a color. */
    selected: Boolean = false,
    /** The key that does the same, named in the pointer's label ("Space"); null where there is none. */
    shortcut: String? = null,
    /** [Role.Tab] where the button is one of a set of places to be, as Search is among the sections. */
    role: Role = Role.Button,
    /** Whether what it opens is open, where it opens something in place (a list in the player); null otherwise. */
    expanded: Boolean? = null,
) {
    val layout = LocalLayout.current
    val target = maxOf(size.value, minOf(layout.minTargetDp, 56f)).dp
    // A pointer can rest on an icon and ask what it is; a finger or a remote cannot, and is not shown labels.
    // The pointer is always followed, so the input changing mid-press never changes what is being pressed.
    val pointer = remember { HoverState() }
    Pressable(
        onClick,
        modifier.semantics {
            contentDescription = description
            // Only what is on says so: "not selected" on every button would be noise. A tab always says.
            if (selected || role == Role.Tab) this.selected = selected
            expanded?.let { stateDescription = if (it) "Open" else "Closed" }
        }.followPointer(pointer),
        CircleShape,
        enabled,
        lift = 0.8f,
        role = role,
    ) { active ->
        if (layout.modality == InputModality.POINTER) HoverLabel(pointer, if (shortcut != null) "$description  ·  $shortcut" else description)
        Box(
            Modifier.size(target)
                .then(if (glass) Modifier.glass(MaterialRole.INTERACTIVE, CircleShape) else Modifier)
                .background(Color.White.copy(alpha = (if (selected) 0.18f else 0f) + (if (active) 0.14f else 0f))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, size = (target.value * 0.5f).dp, tint = if (enabled) tint else RefluxColors.textTertiary)
        }
    }
}

/**
 * What an icon is, shown beside it once the pointer has rested on it a moment: the desktop's tooltip. It is
 * for the eye only; assistive technology already has the control's description.
 */
@Composable
private fun HoverLabel(pointer: HoverState, text: String) {
    val wanted = pointer.over && !pointer.pressed
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(wanted) {
        if (wanted) delay(HOVER_LABEL_DELAY_MS)
        shown = wanted
    }
    if (!shown) return
    Popup(popupPositionProvider = AboveOrBelow) {
        // Nothing for a screen reader to read twice; tagged so tests can see it is there.
        Box(Modifier.clearAndSetSemantics { testTag = "hover:$text" }.glass(MaterialRole.MODAL, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 5.dp)) {
            Text(text, style = LocalTypography.current.caption, maxLines = 1)
        }
    }
}

private const val HOVER_LABEL_DELAY_MS = 600L

/** Whether the pointer is over a control, and whether it has pressed it since it came (which puts the label away until it leaves). */
private class HoverState {
    var over by mutableStateOf(false)
    var pressed by mutableStateOf(false)
}

private fun Modifier.followPointer(state: HoverState): Modifier = pointerInput(state) {
    awaitPointerEventScope {
        while (true) {
            when (awaitPointerEvent(PointerEventPass.Initial).type) {
                PointerEventType.Enter -> state.over = true
                PointerEventType.Exit -> { state.over = false; state.pressed = false }
                PointerEventType.Press -> state.pressed = true
            }
        }
    }
}

/** Centred above the control, below it where there is no room above, and always inside the window. */
private object AboveOrBelow : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val gap = 6
        val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val above = anchorBounds.top - gap - popupContentSize.height
        return IntOffset(x, if (above >= 0) above else anchorBounds.bottom + gap)
    }
}

/**
 * A small, non-interactive label: quality badges, how a search was understood, what is not connected. Drawn
 * as a hairline outline so it describes without competing; [emphasized] fills it, for the one label on a
 * screen that matters more than the others (the version Play will use).
 */
@Composable
fun Chip(text: String, modifier: Modifier = Modifier, emphasized: Boolean = false, icon: Icon? = null) {
    val shape = RoundedCornerShape(7.dp)
    val color = if (emphasized) RefluxColors.textPrimary else RefluxColors.textSecondary
    Row(
        modifier.clip(shape)
            .background(Color.White.copy(alpha = if (emphasized) 0.14f else 0.04f))
            .border(1.dp, if (emphasized) Color.Transparent else RefluxColors.hairline, shape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, size = 12.dp, tint = color)
        Text(text, style = LocalTypography.current.label, color = color, maxLines = 1)
    }
}

/** Progress through a work: a hairline that reads at a glance without competing with artwork. */
@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 3.dp, color: Color = RefluxColors.progress) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(Color.White.copy(alpha = 0.22f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).clip(CircleShape).background(color))
    }
}

/**
 * How far something is, as a thin ring (a download beside its episode). Drawn again only when [fraction]
 * changes; it never spins on its own.
 */
@Composable
fun ProgressRing(fraction: Float, modifier: Modifier = Modifier, stroke: Dp = 2.dp, color: Color = RefluxColors.textPrimary) {
    Canvas(modifier) {
        val width = stroke.toPx()
        val inset = width / 2
        val arc = Size(size.width - width, size.height - width)
        drawArc(Color.White.copy(alpha = 0.18f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(width))
        drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arc, style = Stroke(width, cap = StrokeCap.Round))
    }
}

/**
 * One of a set of places to be: a section of the library, a season, a list in the player. The one that is
 * selected is white on a soft pill; the rest are grey until pointed at. [compact] is for small panels.
 */
@Composable
fun Tab(label: String, selected: Boolean, modifier: Modifier = Modifier, compact: Boolean = false, onClick: () -> Unit) {
    val layout = LocalLayout.current
    // A finger needs the height a pointer does not; words that have grown take what they need.
    val target = if (layout.modality == InputModality.TOUCH) layout.minTargetDp.dp else if (compact) 0.dp else 36.dp
    Pressable(onClick, modifier.semantics { this.selected = selected }, CircleShape, lift = 0f, role = Role.Tab) { active ->
        Box(
            Modifier.background(Color.White.copy(alpha = if (selected) 0.12f else if (active) 0.06f else 0f)).heightIn(min = target)
                .then(if (compact) Modifier.padding(horizontal = 11.dp, vertical = 7.dp) else Modifier.padding(horizontal = if (layout.formFactor == FormFactor.PHONE) 10.dp else 16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = if (compact) LocalTypography.current.captionStrong else LocalTypography.current.bodyStrong,
                color = if (selected || active) RefluxColors.textPrimary else RefluxColors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

/** A section title with an optional trailing action ("See all"). */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).heading(), style = LocalTypography.current.title, maxLines = 1)
        if (action != null) Button(action, onAction, style = ButtonStyle.QUIET)
    }
}

/**
 * A horizontal slider for the timeline and the volume. [onChange] follows the pointer while dragging;
 * [onChangeFinished] reports where it was released.
 *
 * @param description names the slider for accessibility.
 * @param valueText what the position means, as a screen reader says it ("12:04 of 45:00"); a percentage otherwise.
 * @param marks positions (0..1) to notch on the track, e.g. chapter starts.
 * @param color of the filled part.
 */
@Composable
fun Slider(
    fraction: Float,
    description: String,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onChangeFinished: (Float) -> Unit = {},
    marks: List<Float> = emptyList(),
    enabled: Boolean = true,
    color: Color = Color.White,
    valueText: String? = null,
) {
    // A finger gets the height of a target; what is drawn stays a line through its middle.
    val touch = LocalLayout.current.modality == InputModality.TOUCH
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val active = enabled && (hovered || dragging)
    val thickness by animateDpAsState(if (active) 6.dp else 4.dp, Motion.focusChange.tween())
    val change by rememberUpdatedState(onChange)
    val finished by rememberUpdatedState(onChangeFinished)
    Canvas(
        modifier.height(if (touch) 48.dp else 28.dp)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f)
                valueText?.let { stateDescription = it }
                // A screen reader moves a slider by action: without one it could only be read.
                if (enabled) setProgress { value ->
                    val target = value.coerceIn(0f, 1f)
                    change(target)
                    finished(target)
                    true
                } else disabled()
            }
            .hoverable(interaction, enabled).pointerInput(enabled) {
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
        if (x > 0f) drawLine(color, Offset(0f, y), Offset(x, y), stroke, StrokeCap.Round)
        marks.forEach { mark ->
            drawRect(Color.Black.copy(alpha = 0.6f), Offset(size.width * mark - 1.dp.toPx(), y - stroke / 2), Size(2.dp.toPx(), stroke))
        }
        if (active) drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(x, y))
    }
}
