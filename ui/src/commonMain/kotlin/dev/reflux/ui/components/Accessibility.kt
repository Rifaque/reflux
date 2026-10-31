package dev.reflux.ui.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.reflux.ui.state.AccessibilityPreferences

/** What the system's accessibility settings ask for, as [dev.reflux.ui.RefluxTheme] provides them. */
val LocalAccessibility = compositionLocalOf { AccessibilityPreferences() }

/**
 * Takes this content out of the accessibility tree while [inert]: what is behind the player, a modal panel, or
 * on its way out still draws, but a screen reader finds only what is in front. Focus is not touched, so what
 * had it keeps it, and comes back to life when the content does.
 */
fun Modifier.inert(inert: Boolean): Modifier = if (inert) clearAndSetSemantics { } else this

/** Whether a screen reader walks this part of the screen as one, and when: lower first (see [Traversal]). */
fun Modifier.traversal(order: Float): Modifier = semantics {
    isTraversalGroup = true
    traversalIndex = order
}

/** The order a screen reader meets the parts of a screen, whichever of them happens to be drawn on top. */
object Traversal {
    /** What comes before the page: the navigation along the top, or the way back. */
    const val BEFORE_PAGE = -1f
    const val PAGE = 0f

    /** What comes after the page: the sections along the bottom of a phone. */
    const val AFTER_PAGE = 1f
}

/**
 * A part of the screen that appears by itself or opens over others: a page, a panel, a list, a question. A
 * screen reader says [title] when it arrives and walks it as one. [onDismiss] is the way out for someone who
 * cannot tap outside it.
 */
fun Modifier.pane(title: String, onDismiss: (() -> Unit)? = null): Modifier = semantics {
    paneTitle = title
    isTraversalGroup = true
    if (onDismiss != null) dismiss {
        onDismiss()
        true
    }
}

/** Marks text as what the part of the screen below it is about, so a screen reader can move by headings. */
fun Modifier.heading(): Modifier = semantics { heading() }

/**
 * Says [message] to a screen reader as it changes, and stands for what is drawn inside this one node, so it is
 * met once. A live region speaks when a node it already knows changes, never when a new one appears, so this goes
 * on something that is always there; with nothing to say it is one empty pixel.
 */
fun Modifier.announces(message: String?): Modifier = defaultMinSize(1.dp, 1.dp).clearAndSetSemantics {
    liveRegion = LiveRegionMode.Polite
    if (message != null) text = AnnotatedString(message)
}

/**
 * Text in [content] grows with the system's text size up to [largest] and no further. Only for chrome whose
 * width is fixed by what has to fit in it (the navigation): pages take any size, and scroll.
 */
@Composable
internal fun LimitedTextSize(largest: Float, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, minOf(density.fontScale, largest)), content = content)
}

/** The system's text size may go as far as this in the navigation; the words beyond it would not fit a phone. */
internal const val NAVIGATION_TEXT_LIMIT = 1.5f
