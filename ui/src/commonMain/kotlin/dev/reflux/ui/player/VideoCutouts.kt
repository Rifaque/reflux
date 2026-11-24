package dev.reflux.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Where something drawn over the picture must be seen through it, on a platform whose video surface is a native window
 * above the toolkit's content and is shaped around what is drawn over it (Linux: B-084, CACHY-002). Elsewhere there is none
 * ([LocalVideoCutouts] is null) and [cutsVideo] changes nothing: Windows keeps its controls window, Android draws its
 * controls over its own surface.
 */
class VideoCutouts {
    private val areas = mutableStateMapOf<Any, Rect>()

    /** Every area to see through, in the window's pixels; read in a snapshot, so a change is observed. */
    val rects: List<Rect> get() = areas.values.toList()

    internal fun put(key: Any, rect: Rect) {
        if (areas[key] != rect) areas[key] = rect
    }

    internal fun remove(key: Any) {
        areas.remove(key)
    }
}

/** The platform's [VideoCutouts], where its video surface is shaped around what is drawn over it; null elsewhere. */
val LocalVideoCutouts = staticCompositionLocalOf<VideoCutouts?> { null }

/** This element is drawn over the picture and must be seen: the video surface leaves its bounds out while it is shown. */
@Composable
fun Modifier.cutsVideo(): Modifier {
    val cutouts = LocalVideoCutouts.current ?: return this
    val key = remember { Any() }
    DisposableEffect(cutouts) { onDispose { cutouts.remove(key) } }
    return onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        if (bounds.isEmpty) cutouts.remove(key) else cutouts.put(key, bounds)
    }
}
