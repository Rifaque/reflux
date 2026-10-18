package dev.reflux.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import dev.reflux.design.MaterialRole
import dev.reflux.design.Materials
import dev.reflux.design.Palette

/**
 * The content that glass materials float above. It is recorded as it draws, so materials can blur what is
 * behind them (docs/DESIGN.md: blur applies to what is behind a material, never to its own content).
 */
class GlassBackdrop internal constructor(internal val layer: GraphicsLayer) {
    /** Advances whenever the backdrop is redrawn, so the materials above it redraw too. */
    internal var frame by mutableIntStateOf(0)
    internal var origin by mutableStateOf(Offset.Zero)
}

val LocalGlassBackdrop = compositionLocalOf<GlassBackdrop?> { null }

/** Whether this platform can blur what is drawn behind a material. Android before 12 ignores render effects. */
internal expect val platformBlursBackdrop: Boolean

/** Whether glass may blur its backdrop here; tests provide `false` to draw as Android before 12 does. */
val LocalBackdropBlur = staticCompositionLocalOf { platformBlursBackdrop }

/**
 * Hosts [content] as the backdrop of [overlay]: glass materials inside [overlay] blur [content].
 * Materials inside [content] itself get tint and highlight only.
 */
@Composable
fun GlassScene(
    modifier: Modifier = Modifier,
    overlay: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val layer = rememberGraphicsLayer()
    val backdrop = remember(layer) { GlassBackdrop(layer) }
    Box(modifier) {
        Box(
            Modifier
                .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                    // Unobserved, or this draw would invalidate itself by reading what it writes.
                    Snapshot.withoutReadObservation { backdrop.frame++ }
                },
        ) {
            CompositionLocalProvider(LocalGlassBackdrop provides null) { content() }
        }
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop) { overlay() }
    }
}

/**
 * Draws a liquid-glass material: the blurred backdrop (inside a [GlassScene] overlay), a tint, a top-lit
 * hairline highlight, and a shadow. The tint is raised until secondary text stays readable over bright
 * artwork, so glass never costs legibility.
 *
 * @param tint overrides the material's tint, e.g. over video, where the backdrop cannot be sampled.
 */
@Composable
fun Modifier.glass(
    role: MaterialRole,
    shape: Shape = RoundedCornerShape(Materials.spec(role).cornerRadiusDp.dp),
    tint: Color? = null,
): Modifier {
    val spec = Materials.spec(role)
    val backdrop = LocalGlassBackdrop.current
    val tintColor = tint ?: spec.tint.toColor().copy(alpha = Materials.scrimFor(role, Palette.textSecondary))
    val highlight = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = spec.highlightAlpha), Color.White.copy(alpha = spec.highlightAlpha * 0.3f)),
    )
    val base = if (spec.shadowElevationDp > 0f) shadow(spec.shadowElevationDp.dp, shape, clip = false) else this
    if (backdrop == null || spec.blurRadiusDp <= 0f) {
        return base.clip(shape).drawBehind { drawRect(tintColor) }.border(1.dp, highlight, shape)
    }
    if (!LocalBackdropBlur.current) {
        // Unblurred, the page beneath would show sharp through the tint and compete with the material's own
        // text (seen on Fire OS 8, Android 11). Where there is no blur the material is solid instead.
        return base.clip(shape).drawBehind { drawRect(RefluxColors.background); drawRect(tintColor) }.border(1.dp, highlight, shape)
    }
    val blurred = rememberGraphicsLayer()
    var position by remember { mutableStateOf(Offset.Zero) }
    return base
        .onGloballyPositioned { position = it.positionInRoot() }
        .clip(shape)
        .drawBehind {
            backdrop.frame // read: redraw whenever the backdrop does
            val radius = spec.blurRadiusDp.dp.toPx()
            blurred.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
            val offset = backdrop.origin - position
            blurred.record { translate(offset.x, offset.y) { drawLayer(backdrop.layer) } }
            // The recording is transparent wherever the page drew nothing, and the page itself is right
            // beneath this material. Without an opaque base the sharp original would show through the blur.
            drawRect(RefluxColors.background)
            drawLayer(blurred)
            drawRect(tintColor)
        }
        .border(1.dp, highlight, shape)
}
