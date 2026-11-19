package dev.reflux.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dev.reflux.design.MaterialRole
import dev.reflux.ui.theme.MotionPolicy
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.glass
import kotlinx.coroutines.launch

/**
 * Pull down at the top of a page to check the library for changes (B-048). Built on nested scrolling, because the
 * project draws its own components on Compose Foundation and has no Material dependency to take Material's from.
 *
 * Only a finger's drag pulls: the list must already be at its top (what the list leaves of a drag is what pulls, so a
 * drag in the middle of a page scrolls the page), a fling that reaches the top does not pull, and a pointer's wheel
 * never does ([enabled] is a touchscreen's). Pulled past [threshold] and let go, [onRefresh] runs once; while
 * [refreshing] the sign turns, and it goes when the work is done, whatever came of it.
 */
@Composable
fun PullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    enabled: Boolean,
    top: Dp,
    modifier: Modifier = Modifier,
    threshold: Dp = 72.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val limit = with(density) { threshold.toPx() }
    val pulled = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val refresh by rememberUpdatedState(onRefresh)
    val busy by rememberUpdatedState(refreshing)
    val active by rememberUpdatedState(enabled)
    // Once the work is done the sign goes back up.
    LaunchedEffect(refreshing) { if (!refreshing && pulled.value > 0f) pulled.animateTo(0f) }
    val connection = remember(limit) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Pushing back up while pulled takes the pull back before the list scrolls.
                if (available.y < 0f && pulled.value > 0f && !busy) {
                    val taken = maxOf(available.y, -pulled.value)
                    scope.launch { pulled.snapTo(pulled.value + taken) }
                    return Offset(0f, taken)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!active || busy || source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                // Resistance: the sign moves at half the finger's pace, and no further than half again its threshold.
                scope.launch { pulled.snapTo((pulled.value + available.y / 2f).coerceAtMost(limit * 1.5f)) }
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pulled.value <= 0f || busy) return Velocity.Zero
                if (pulled.value >= limit) {
                    pulled.animateTo(limit)
                    refresh()
                } else {
                    pulled.animateTo(0f)
                }
                return available
            }
        }
    }
    Box(modifier.nestedScroll(connection)) {
        content()
        val progress = (pulled.value / limit).coerceIn(0f, 1.5f)
        if (progress > 0f || refreshing) {
            val spin = if (refreshing && !MotionPolicy.reduced) {
                rememberInfiniteTransition().animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing))).value
            } else {
                progress * 270f
            }
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = top)
                    .graphicsLayer {
                        translationY = if (refreshing) limit * 0.4f else pulled.value * 0.6f
                        alpha = if (refreshing) 1f else progress.coerceAtMost(1f)
                    }
                    .size(38.dp).glass(MaterialRole.FLOATING, CircleShape)
                    .semantics { contentDescription = if (refreshing) "Checking for changes" else "Pull to check for changes" },
                contentAlignment = Alignment.Center,
            ) {
                val color = RefluxColors.contentPrimary
                Box(
                    Modifier.size(18.dp).drawBehind {
                        val stroke = 2.dp.toPx()
                        drawArc(
                            color, startAngle = spin - 90f, sweepAngle = if (refreshing) 270f else 300f * progress.coerceAtMost(1f), useCenter = false,
                            topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    },
                )
            }
        }
    }
}
