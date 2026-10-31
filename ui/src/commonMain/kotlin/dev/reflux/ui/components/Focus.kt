package dev.reflux.ui.components

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
    private var initial: Offer? = null
    private var returning: Offer? = null

    /**
     * The page on screen. While one page gives way to another both exist for a moment, and only what the
     * arriving one offers counts: focus put on the page that is leaving is lost when it goes.
     */
    internal var screen: Any? = null

    private class Offer(val requester: FocusRequester, val screen: Any?)

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
            this.returning = Offer(requester, screen)
        } else {
            initial = Offer(requester, screen)
            starts++
        }
    }

    internal fun withdraw(requester: FocusRequester) {
        if (initial?.requester === requester) initial = null
        if (returning?.requester === requester) returning = null
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
        return focus(returning) || focus(initial)
    }

    private fun focus(offer: Offer?): Boolean =
        runCatching { offer?.takeIf { it.screen == screen }?.requester?.requestFocus(FocusDirection.Enter) }.getOrNull() ?: false
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

/** Marks the control the first directional input on this screen focuses: its main action or first item. */
@Composable
fun Modifier.initialFocus(): Modifier {
    val entry = LocalFocusEntry.current ?: return this
    val screen = LocalFocusScreen.current
    val requester = remember { FocusRequester() }
    DisposableEffect(entry, screen) {
        entry.offer(requester, screen)
        onDispose { entry.withdraw(requester) }
    }
    return focusRequester(requester)
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
        // Only on the page being come back to: the same item on the page being left has just been activated.
        LaunchedEffect(Unit) { if (showsFocus && entry!!.screen == screen) runCatching { requester.requestFocus() } }
    }
    return remember(entry, key, screen) { FocusReturn(Modifier.focusRequester(requester)) { entry?.left?.set(screen, key) } }
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
@Composable
fun ButtonPrompts(prompts: List<Pair<GamepadButton, String>>, family: ControllerFamily, modifier: Modifier = Modifier, floating: Boolean = true) {
    Row(
        // On its own over video it needs a surface; inside other chrome it borrows that one.
        // A hint for the eye about the buttons in the hand, not something on the screen to reach or read.
        // Tagged so tests can see it is there.
        (if (floating) modifier.glass(MaterialRole.FLOATING, CircleShape).padding(horizontal = 16.dp, vertical = 9.dp) else modifier)
            .clearAndSetSemantics { testTag = "prompts:" + prompts.joinToString(",") { (button, action) -> InputMapping.glyph(button, family) + " " + action } },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        prompts.forEach { (button, action) ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                val shape = RoundedCornerShape(7.dp)
                Box(
                    Modifier.defaultMinSize(minWidth = 26.dp).background(Color.White.copy(alpha = 0.12f), shape)
                        .border(1.dp, Color.White.copy(alpha = 0.22f), shape).padding(horizontal = 7.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(InputMapping.glyph(button, family), style = LocalTypography.current.label, maxLines = 1)
                }
                Text(action, style = LocalTypography.current.captionStrong, color = RefluxColors.textSecondary, maxLines = 1)
            }
        }
    }
}
