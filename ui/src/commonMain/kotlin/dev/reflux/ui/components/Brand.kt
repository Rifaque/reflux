package dev.reflux.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.reflux.design.BrandMark
import dev.reflux.ui.theme.LocalTypography
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.Text
import kotlinx.coroutines.delay

/**
 * The Reflux mark as motion (DESIGN.md, "Motion language"), drawn from its own centreline (design's BrandMark), so the
 * app, the website, and the Android splash trace the same line. Animation happens in the draw phase only: the frames
 * read animated values while drawing, and nothing recomposes for them.
 */
private class MarkPaths {
    /** The whole line, first pass then return, as one path: what the loader flows along. */
    val line: Path = PathParser().parsePathString(BrandMark.FIRST_PASS + " " + BrandMark.RETURN_PASS.substringAfter(" C").let { "C$it" }).toPath()
    val fill: Path = PathParser().parsePathString(BrandMark.FILL).toPath().apply { fillType = PathFillType.EvenOdd }
    val measure = PathMeasure().apply { setPath(line, false) }
    val length = measure.length
    val segment = Path()

    fun segment(from: Float, to: Float): Path {
        segment.reset()
        measure.getSegment(from.coerceIn(0f, 1f) * length, to.coerceIn(0f, 1f) * length, segment, true)
        return segment
    }
}

/** Draws in the mark's own 100-unit square, centred in the canvas. */
private fun DrawScope.inMark(draw: DrawScope.() -> Unit) {
    val unit = size.minDimension / BrandMark.VIEWPORT
    translate((size.width - size.minDimension) / 2, (size.height - size.minDimension) / 2) {
        scale(unit, unit, pivot = Offset.Zero) { draw() }
    }
}

private val Flow = CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f)

/**
 * The loader for a genuine wait (a player loading, a library being built): a bright length of the line flowing along
 * the faint mark, again and again, like media passing through. It appears only after [delayMs], so a short wait never
 * flashes it; with reduce motion on, the still mark and the word instead.
 */
@Composable
fun RefluxLoader(modifier: Modifier = Modifier, size: Dp = 56.dp, label: String = "Loading", delayMs: Long = 400) {
    var shown by remember { mutableStateOf(delayMs <= 0) }
    LaunchedEffect(Unit) { delay(delayMs); shown = true }
    val busy = Modifier.semantics {
        contentDescription = label
        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
    }
    if (!shown) return
    val paths = remember { MarkPaths() }
    val color = RefluxColors.contentPrimary
    if (LocalAccessibility.current.reduceMotion) {
        Column(modifier.then(busy), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Canvas(Modifier.size(size)) { inMark { drawPath(paths.fill, color.copy(alpha = 0.55f)) } }
            Text("$label…", style = LocalTypography.current.bodyStrong)
        }
        return
    }
    val transition = rememberInfiniteTransition()
    val phase = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = Flow), RepeatMode.Restart))
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(240, easing = LinearEasing)) }
    Canvas(modifier.size(size).then(busy)) {
        val width = BrandMark.STROKE_WIDTH
        inMark {
            drawPath(paths.line, color.copy(alpha = 0.16f * appear.value), style = Stroke(width, cap = StrokeCap.Round))
            // A third of the line, entering at the start and leaving at the end.
            val head = phase.value * 1.34f
            drawPath(paths.segment(head - 0.34f, head), color.copy(alpha = appear.value), style = Stroke(width, cap = StrokeCap.Round))
        }
    }
}

/**
 * The mark drawing itself once, then settling into the solid mark: the brand's own motion (flow → gather → transform →
 * settle), for a moment that introduces Reflux (the first-run screen). Immediate with reduce motion on.
 */
@Composable
fun RefluxMarkDrawIn(modifier: Modifier = Modifier, size: Dp = 64.dp) {
    val paths = remember { MarkPaths() }
    val color = RefluxColors.contentPrimary
    val reduce = LocalAccessibility.current.reduceMotion
    val drawn = remember { Animatable(if (reduce) 1f else 0f) }
    val settled = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduce) return@LaunchedEffect
        drawn.animateTo(1f, tween(900, easing = Flow))
        settled.animateTo(1f, tween(240, easing = LinearEasing))
    }
    Canvas(modifier.size(size).semantics { contentDescription = "Reflux" }) {
        inMark {
            if (settled.value < 1f) {
                drawPath(paths.segment(0f, drawn.value), color.copy(alpha = 1f - settled.value), style = Stroke(BrandMark.STROKE_WIDTH, cap = StrokeCap.Round))
            }
            if (settled.value > 0f) drawPath(paths.fill, color.copy(alpha = settled.value))
        }
    }
}
