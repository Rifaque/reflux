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
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.core.input.InputMapping
import dev.reflux.design.MaterialRole
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
    internal var hasFocus = false
    private var initial: FocusRequester? = null

    internal fun offer(requester: FocusRequester) {
        initial = requester
    }

    internal fun withdraw(requester: FocusRequester) {
        if (initial === requester) initial = null
    }

    /** Focuses the screen's starting control when nothing has focus. Returns false when focus should just move. */
    internal fun enter(): Boolean {
        if (hasFocus) return false
        return runCatching { initial?.requestFocus(FocusDirection.Enter) }.getOrNull() ?: false
    }
}

val LocalFocusEntry = staticCompositionLocalOf<FocusEntry?> { null }

/** Marks the control the first directional input on this screen focuses: its main action or first item. */
@Composable
fun Modifier.initialFocus(): Modifier {
    val entry = LocalFocusEntry.current ?: return this
    val requester = remember { FocusRequester() }
    DisposableEffect(entry) {
        entry.offer(requester)
        onDispose { entry.withdraw(requester) }
    }
    return focusRequester(requester)
}

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
        if (floating) modifier.glass(MaterialRole.FLOATING, CircleShape).padding(horizontal = 16.dp, vertical = 9.dp) else modifier,
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
