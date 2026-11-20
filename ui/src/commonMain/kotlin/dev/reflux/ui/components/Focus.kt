package dev.reflux.ui.components

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.height
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawBehind
import dev.reflux.ui.theme.States
import dev.reflux.ui.theme.Shapes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.core.input.InputMapping
import dev.reflux.core.input.SemanticAction
import dev.reflux.design.MaterialRole
import dev.reflux.ui.theme.LocalLayout
import dev.reflux.ui.theme.LocalTypography
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.Text
import dev.reflux.ui.theme.glass

/**
 * Where directional focus starts. A pointer user never has focus, so the first arrow or D-pad press has
 * nothing to move from; it goes to the control the current screen marked with [initialFocus] instead of to
 * whatever happens to be nearest a window corner.
 */
class FocusEntry {
    /** Whether anything on the screen has focus. Observable, so a television can notice when nothing does. */
    internal var hasFocus by mutableStateOf(false)

    /**
     * Each page's starting control and the control to come back to, kept per page. While one page gives way to
     * another both are composed for a moment, and the leaving one can still offer: a list item that arrives late
     * (Home's featured title, once the library has been read) composes on the page being left. Kept in one slot,
     * that offer replaced the arriving page's, and withdrawing it when the page went left the arriving page with
     * none, so the first arrow fell to the toolkit's own search (on About, the Back button).
     */
    private val initial = HashMap<Any?, FocusRequester>()
    private val returning = HashMap<Any?, FocusRequester>()

    /**
     * The page on screen. While one page gives way to another both exist for a moment, and only what the
     * arriving one offers counts: focus put on the page that is leaving is lost when it goes.
     */
    internal var screen: Any? = null

    /**
     * The item the user last went into on each page (see [rememberFocusReturn]), so that coming back to any
     * of them, however many pages deep the user went, finds it again.
     */
    internal val left = HashMap<Any?, String>()

    /**
     * While focus is on the navigation rather than in the page: the direction that leads back to the page
     * (down from a bar along the top, up from one along the bottom).
     */
    internal var chrome: SemanticAction? = null

    /** Whether focus is on the navigation rather than in the page. */
    internal val inChrome: Boolean get() = chrome != null

    /** Whether focus is inside a panel that floats over the page (and will be lost when the panel goes). */
    internal var inPanel = false

    /**
     * Leaves the navigation for the page in direction [action], when that is the way back to it. The
     * toolkit's own search does not find its way from a bar into a scrolling page, and where it would land
     * is less use than where the user was: the item they came from, or the page's main action.
     */
    internal fun leaveChrome(action: SemanticAction): Boolean = action == chrome && start()

    /** Advances whenever a screen names a new main action, so a television can start there. */
    internal var starts by mutableIntStateOf(0)
        private set

    internal fun offer(requester: FocusRequester, screen: Any?, returning: Boolean = false) {
        if (returning) {
            this.returning[screen] = requester
        } else {
            initial[screen] = requester
            starts++
        }
    }

    /** Takes back what [requester] offered, on whichever page it offered it; nothing another control offered. */
    internal fun withdraw(requester: FocusRequester) {
        initial.entries.removeAll { it.value === requester }
        returning.entries.removeAll { it.value === requester }
    }

    /**
     * Focuses where the user left off, or else the screen's starting control, when nothing has focus.
     * Returns false when focus should just move.
     */
    internal fun enter(): Boolean {
        if (hasFocus) return false
        return start()
    }

    /**
     * The control that opened a panel, for focus to come back to once the panel closes, instead of going to
     * the top of the page. Used once.
     */
    internal var afterPanel: FocusRequester? = null

    /** Puts focus where a screen starts, wherever it is now. Returns false while the screen has nowhere yet. */
    internal fun start(): Boolean {
        afterPanel?.let { requester ->
            // Not while the panel is still going: what has focus then is on its way out with it.
            if (inPanel) return false
            afterPanel = null
            if (runCatching { requester.requestFocus(FocusDirection.Enter) }.getOrNull() == true) return true
        }
        return focus(returning[screen]) || focus(initial[screen])
    }

    private fun focus(requester: FocusRequester?): Boolean =
        runCatching { requester?.requestFocus(FocusDirection.Enter) }.getOrNull() ?: false
}

val LocalFocusEntry = staticCompositionLocalOf<FocusEntry?> { null }

/**
 * Marks a panel that floats over the page and keeps focus while it is open. Focus inside it is not focus
 * in the page: when the panel closes, the page gets it back where it belongs instead of losing it.
 */
@Composable
fun Modifier.focusPanel(): Modifier {
    val entry = LocalFocusEntry.current ?: return this
    DisposableEffect(entry) { onDispose { entry.inPanel = false } }
    return onFocusChanged { entry.inPanel = it.hasFocus }
}

/** The page a control belongs to, for [FocusEntry]; null outside the paged part of the application. */
val LocalFocusScreen = staticCompositionLocalOf<Any?> { null }

/**
 * Marks the control the first directional input on this screen focuses: its main action or first item.
 *
 * @param onArrival also focus it as soon as the page is shown, for a controller or a remote (B-053): a page opened from an
 * item (Details) has its main action ready, so Select again plays. Never for a pointer or a finger, which see no focus; never
 * while focus is in the navigation; and only once, as the page arrives, so it never takes focus back from where the
 * viewer has moved it.
 */
@Composable
fun Modifier.initialFocus(onArrival: Boolean = false): Modifier {
    val entry = LocalFocusEntry.current ?: return this
    val screen = LocalFocusScreen.current
    val requester = remember { FocusRequester() }
    DisposableEffect(entry, screen) {
        entry.offer(requester, screen)
        onDispose { entry.withdraw(requester) }
    }
    if (onArrival) {
        val showsFocus = LocalLayout.current.showsFocus
        LaunchedEffect(Unit) { if (showsFocus && entry.screen == screen && !entry.inChrome) runCatching { requester.requestFocus() } }
    }
    return focusRequester(requester)
}

/**
 * For the first control of a panel that opens in place of the control that opened it (a rename field, a PIN panel, a
 * confirmation): it takes focus as the panel appears, for input that shows focus. Without it the control that had focus was
 * gone, focus fell to the navigation, and Back there left the page with the panel open (Phase 2's close, the Google TV
 * walk, 2026-11-20). Never for a pointer or a finger, which see no focus.
 */
@Composable
fun Modifier.takesFocus(): Modifier {
    val requester = remember { FocusRequester() }
    val showsFocus = LocalLayout.current.showsFocus
    LaunchedEffect(Unit) {
        if (showsFocus) {
            withFrameNanos { }
            runCatching { requester.requestFocus() }
        }
    }
    return focusRequester(requester)
}

/** Where focus goes back to when a panel opened in place closes: the control that opened it ([returnsFocus]). */
class PanelReturn internal constructor(internal val requester: FocusRequester) {
    internal var pending by mutableStateOf(false)

    /** Call as the panel closes. */
    fun back() {
        pending = true
    }
}

@Composable
fun rememberPanelReturn(): PanelReturn = remember { PanelReturn(FocusRequester()) }

/** Marks the control that opens a panel in place: focus comes back to it when the panel closes, for input that shows focus. */
@Composable
fun Modifier.returnsFocus(to: PanelReturn): Modifier {
    val showsFocus = LocalLayout.current.showsFocus
    LaunchedEffect(to.pending) {
        if (to.pending) {
            withFrameNanos { }
            if (showsFocus) runCatching { to.requester.requestFocus() }
            to.pending = false
        }
    }
    return focusRequester(to.requester)
}

/**
 * Marks an item that leads somewhere, named by [key]. Call [FocusReturn.leaving] when it is activated; when
 * its screen is shown again, focus goes back to it, at once for a keyboard or a controller, and on the
 * first directional press otherwise. Without this, every Back would send a controller to the top of the page.
 */
@Composable
fun rememberFocusReturn(key: String): FocusReturn {
    val entry = LocalFocusEntry.current
    val requester = remember { FocusRequester() }
    val screen = LocalFocusScreen.current
    val returning = entry != null && entry.left[screen] == key
    if (returning) {
        val showsFocus = LocalLayout.current.showsFocus
        DisposableEffect(entry, screen) {
            entry!!.offer(requester, screen, returning = true)
            onDispose { entry.withdraw(requester) }
        }
        // Only on the page being come back to: the same item on the page being left has just been activated. Not while
        // focus is on the navigation (a destination chosen there): it stays there until the viewer comes back in.
        LaunchedEffect(Unit) { if (showsFocus && entry!!.screen == screen && !entry.inChrome) runCatching { requester.requestFocus() } }
    }
    // The item with focus is also the one to come back to (B-008): leaving the page by the navigation, the bumpers, or a
    // shortcut, and not only by opening an item, finds it again on return, instead of the page's first control.
    return remember(entry, key, screen) {
        FocusReturn(
            Modifier.focusRequester(requester).onFocusChanged { if (it.isFocused && entry?.screen == screen) entry?.left?.set(screen, key) },
        ) { entry?.left?.set(screen, key) }
    }
}

/** [modifier] goes on the item; [leaving] is called when the user goes into it. */
class FocusReturn internal constructor(val modifier: Modifier, val leaving: () -> Unit)

/**
 * Keeps a newly focused item [start] and [end] away from the edges of the scrolling containers in
 * [content], so it never ends up under floating chrome or flush against the window.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusScrollInsets(start: Dp, end: Dp, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val spec = remember(density, start, end) {
        with(density) { InsetBringIntoViewSpec(start.toPx(), end.toPx()) }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

/**
 * Leaves the scrolling containers in [content] where they are when focus moves inside them, for pages that
 * place the focused row themselves. Containers nested deeper can still bring their own items into view.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusScrollsNothing(content: @Composable () -> Unit) = CompositionLocalProvider(LocalBringIntoViewSpec provides StillBringIntoViewSpec, content = content)

@OptIn(ExperimentalFoundationApi::class)
private object StillBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

@OptIn(ExperimentalFoundationApi::class)
private class InsetBringIntoViewSpec(private val start: Float, private val end: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val leading = offset - start
        val trailing = offset + size - (containerSize - end)
        return when {
            leading >= 0f && trailing <= 0f -> 0f
            // Larger than the room there is: show its beginning.
            size > containerSize - start - end -> leading
            leading < 0f -> leading
            else -> trailing
        }
    }
}

/**
 * What the buttons of the controller in the user's hands do right now. Buttons are named by position and
 * labelled for the controller's family, so a DualSense shows Cross and Circle where an Xbox pad shows A and B.
 */
/**
 * A button that means something when held (B-052): its prompt wears a small down-pointing triangle above it, as games mark a
 * hold, and a ring around it that fills while it is held ([progress], 0 to 1), complete at the moment the hold acts.
 */
data class HoldPrompt(val button: GamepadButton, val progress: Float)

@Composable
fun ButtonPrompts(prompts: List<Pair<GamepadButton, String>>, family: ControllerFamily, modifier: Modifier = Modifier, floating: Boolean = true, hold: HoldPrompt? = null) {
    // Calm (B-055): every hint keeps its place while the bar is up, so one that comes or goes opens or closes where it
    // belongs, on one short ease with no overshoot; the bar is exactly as wide as its hints at every moment, so a hint at
    // its anchored end stays where it is. A changed label cross-fades in place.
    val order = remember { ArrayList<GamepadButton>() }
    val labels = remember { HashMap<GamepadButton, String>() }
    var previous: GamepadButton? = null
    for ((button, action) in prompts) {
        labels[button] = action
        if (button !in order) order.add(previous?.let { order.indexOf(it) + 1 } ?: 0, button)
        previous = button
    }
    val current = prompts.toMap()
    val ease = tween<androidx.compose.ui.unit.IntSize>(220, easing = androidx.compose.animation.core.FastOutSlowInEasing)
    Row(
        // On its own over video it needs a surface; inside other chrome it borrows that one.
        // A hint for the eye about the buttons in the hand, not something on the screen to reach or read.
        // Tagged so tests can see it is there.
        (if (floating) modifier.glass(MaterialRole.FLOATING, CircleShape).padding(horizontal = 8.dp, vertical = 7.dp) else modifier)
            .clearAndSetSemantics {
                testTag = "prompts:" + prompts.joinToString(",") { (button, action) -> InputMapping.glyph(button, family) + " " + action } +
                    (hold?.let { "|hold:" + InputMapping.glyph(it.button, family) + ":" + (it.progress * 100).toInt() } ?: "")
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (button in order.toList()) {
            key(button) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = button in current,
                    enter = androidx.compose.animation.fadeIn(tween(160, delayMillis = 80)) +
                        androidx.compose.animation.expandHorizontally(ease, expandFrom = Alignment.End),
                    exit = androidx.compose.animation.fadeOut(tween(110)) +
                        androidx.compose.animation.shrinkHorizontally(ease, shrinkTowards = Alignment.End),
                ) {
                    Prompt(button, family, labels[button].orEmpty(), hold?.takeIf { it.button == button })
                }
            }
        }
    }
}

/** One hint: the button as a key cap, and what it does. */
@Composable
private fun Prompt(button: GamepadButton, family: ControllerFamily, action: String, held: HoldPrompt?) {
    Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        KeyCap(button, family, held)
        androidx.compose.animation.Crossfade(targetState = action, animationSpec = tween(160), label = "hint") { label ->
            Text(label, style = LocalTypography.current.captionStrong, color = RefluxColors.contentPrimary.copy(alpha = 0.86f), maxLines = 1)
        }
    }
}

/**
 * A controller's button drawn in the shape it has on the controller, outlined (B-055): A, B, X and Y round; the bumpers
 * flat and wide; the triggers taller, rounded at the top; the menu buttons small pills. The controller's own marking is
 * inside (PlayStation's shapes drawn as shapes).
 *
 * A button to hold ([HoldPrompt]) keeps exactly its size, so the bar never grows: its own outline is the ring that fills as
 * it is held, from the top, and a small triangle above it, in the bar's margin, says "hold".
 */
@Composable
private fun KeyCap(button: GamepadButton, family: ControllerFamily, held: HoldPrompt?) {
    val glyph = capGlyph(button, family)
    val form = capForm(button)
    val ink = RefluxColors.contentPrimary
    val outline = States.layer(0.42f)
    val shape = form.shape
    Box(
        Modifier.height(form.height).defaultMinSize(minWidth = form.minWidth)
            .background(States.layer(0.16f), shape)
            .then(
                if (held == null) Modifier.border(1.25.dp, outline, shape) else Modifier.drawWithContent {
                    drawContent()
                    val stroke = 2.dp.toPx()
                    val inset = stroke / 2f
                    val arc = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                    val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                    val style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    // The outline is the ring: its track always, its fill as the button is held.
                    drawArc(outline, 0f, 360f, false, topLeft, arc, style = style)
                    if (held.progress > 0f) drawArc(ink, -90f, 360f * held.progress.coerceIn(0f, 1f), false, topLeft, arc, style = style)
                    // "Hold": a small triangle pointing down at the button, just above it, in the bar's own margin.
                    val w = 7.dp.toPx()
                    val h = 4.dp.toPx()
                    val top = -h - 2.dp.toPx()
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(size.width / 2f - w / 2f, top)
                        lineTo(size.width / 2f + w / 2f, top)
                        lineTo(size.width / 2f, top + h)
                        close()
                    }
                    drawPath(path, ink)
                },
            )
            .padding(horizontal = form.padding),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = LocalTypography.current.label, color = ink, maxLines = 1)
    }
}

/** How a kind of button is drawn. */
private class CapForm(val shape: androidx.compose.ui.graphics.Shape, val height: androidx.compose.ui.unit.Dp, val minWidth: androidx.compose.ui.unit.Dp, val padding: androidx.compose.ui.unit.Dp)

private val FaceForm = CapForm(CircleShape, 24.dp, 24.dp, 0.dp)
private val BumperForm = CapForm(RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp, bottomStart = 4.dp, bottomEnd = 4.dp), 20.dp, 34.dp, 7.dp)
private val TriggerForm = CapForm(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 5.dp, bottomEnd = 5.dp), 24.dp, 30.dp, 7.dp)
private val MenuForm = CapForm(RoundedCornerShape(50), 20.dp, 30.dp, 8.dp)

private fun capForm(button: GamepadButton): CapForm = when (button) {
    GamepadButton.FACE_SOUTH, GamepadButton.FACE_EAST, GamepadButton.FACE_WEST, GamepadButton.FACE_NORTH -> FaceForm
    GamepadButton.SHOULDER_LEFT, GamepadButton.SHOULDER_RIGHT -> BumperForm
    GamepadButton.TRIGGER_LEFT, GamepadButton.TRIGGER_RIGHT -> TriggerForm
    else -> MenuForm
}

/** What a cap shows: the controller's own marking, with PlayStation's shapes drawn as shapes rather than spelt out. */
private fun capGlyph(button: GamepadButton, family: ControllerFamily): String =
    if (family == ControllerFamily.PLAYSTATION) when (button) {
        GamepadButton.FACE_SOUTH -> "\u2715"
        GamepadButton.FACE_EAST -> "\u25CB"
        GamepadButton.FACE_WEST -> "\u25A1"
        GamepadButton.FACE_NORTH -> "\u25B3"
        else -> InputMapping.glyph(button, family)
    } else InputMapping.glyph(button, family)
