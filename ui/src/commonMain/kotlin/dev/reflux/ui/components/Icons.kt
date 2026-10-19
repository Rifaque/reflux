package dev.reflux.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.reflux.ui.theme.RefluxColors

/** The icon set, drawn on a 24-unit grid with one stroke weight so every glyph has the same optical weight. */
enum class Icon {
    PLAY, PAUSE, NEXT, PREVIOUS, REWIND, FORWARD,
    BACK, CHEVRON_RIGHT, CHEVRON_UP, CHEVRON_DOWN, CLOSE, CHECK, PLUS, SEARCH,
    HEART, HEART_FILLED, FOLDER, REFRESH, INFO, OFFLINE, DOWNLOAD, DOWNLOADED, SERVER, NETWORK,
    VOLUME, VOLUME_MUTED, SUBTITLES, AUDIO, CHAPTERS, FULLSCREEN, FULLSCREEN_EXIT,
}

@Composable
fun Icon(icon: Icon, modifier: Modifier = Modifier, size: Dp = 20.dp, tint: Color = RefluxColors.textPrimary) {
    Canvas(modifier.size(size)) {
        scale(this.size.minDimension / 24f, pivot = Offset.Zero) { draw(icon, tint) }
    }
}

private val line = Stroke(width = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val softEdge = Stroke(width = 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun path(build: Path.() -> Unit): Path = Path().apply(build)

private fun DrawScope.stroke(color: Color, build: Path.() -> Unit) = drawPath(path(build), color, style = line)

/** Filled shapes get a thin round stroke of the same color, which softens their corners. */
private fun DrawScope.solid(color: Color, build: Path.() -> Unit) {
    val shape = path(build)
    drawPath(shape, color, style = Fill)
    drawPath(shape, color, style = softEdge)
}

private fun Path.bar(x: Float, y: Float, width: Float, height: Float) =
    addRoundRect(RoundRect(Rect(Offset(x, y), Size(width, height)), CornerRadius(1f)))

private fun Path.speaker() {
    moveTo(4f, 9.5f); lineTo(7.5f, 9.5f); lineTo(12f, 5.5f); lineTo(12f, 18.5f); lineTo(7.5f, 14.5f); lineTo(4f, 14.5f); close()
}

private fun Path.heart() {
    moveTo(12f, 20f)
    cubicTo(12f, 20f, 3.5f, 14.6f, 3.5f, 8.8f)
    cubicTo(3.5f, 6f, 5.7f, 4f, 8.2f, 4f)
    cubicTo(9.8f, 4f, 11.2f, 4.8f, 12f, 6.2f)
    cubicTo(12.8f, 4.8f, 14.2f, 4f, 15.8f, 4f)
    cubicTo(18.3f, 4f, 20.5f, 6f, 20.5f, 8.8f)
    cubicTo(20.5f, 14.6f, 12f, 20f, 12f, 20f)
    close()
}

/** An open circle with an arrowhead at its top, turning counter-clockwise (rewind). */
private fun DrawScope.turn(color: Color) {
    drawArc(color, startAngle = -50f, sweepAngle = 320f, useCenter = false, topLeft = Offset(4.5f, 5.5f), size = Size(15f, 15f), style = line)
    stroke(color) {
        moveTo(14.6f, 2.6f); lineTo(11.6f, 5.5f); lineTo(14.6f, 8.4f)
    }
}

private fun DrawScope.draw(icon: Icon, color: Color) {
    when (icon) {
        Icon.PLAY -> solid(color) { moveTo(8f, 5.2f); lineTo(18.6f, 12f); lineTo(8f, 18.8f); close() }
        Icon.PAUSE -> solid(color) { bar(6.6f, 5.2f, 3.6f, 13.6f); bar(13.8f, 5.2f, 3.6f, 13.6f) }
        Icon.NEXT -> solid(color) { moveTo(5.5f, 5.5f); lineTo(14.5f, 12f); lineTo(5.5f, 18.5f); close(); bar(16.4f, 5.5f, 2.2f, 13f) }
        Icon.PREVIOUS -> scale(-1f, 1f, pivot = Offset(12f, 12f)) { draw(Icon.NEXT, color) }
        Icon.REWIND -> turn(color)
        Icon.FORWARD -> scale(-1f, 1f, pivot = Offset(12f, 12f)) { turn(color) }
        Icon.BACK -> stroke(color) { moveTo(14.5f, 5.5f); lineTo(8f, 12f); lineTo(14.5f, 18.5f) }
        Icon.CHEVRON_RIGHT -> stroke(color) { moveTo(9.5f, 5.5f); lineTo(16f, 12f); lineTo(9.5f, 18.5f) }
        Icon.DOWNLOAD -> stroke(color) {
            moveTo(12f, 4.5f); lineTo(12f, 14.5f)
            moveTo(7.5f, 10.5f); lineTo(12f, 15f); lineTo(16.5f, 10.5f)
            moveTo(5.5f, 19f); lineTo(18.5f, 19f)
        }
        Icon.DOWNLOADED -> stroke(color) {
            moveTo(6f, 10.5f); lineTo(10.2f, 14.7f); lineTo(18f, 6.5f)
            moveTo(5.5f, 19f); lineTo(18.5f, 19f)
        }
        Icon.SERVER -> {
            drawRoundRect(color, Offset(4f, 4.5f), Size(16f, 6.5f), CornerRadius(2f), style = line)
            drawRoundRect(color, Offset(4f, 13f), Size(16f, 6.5f), CornerRadius(2f), style = line)
            drawCircle(color, radius = 1.1f, center = Offset(7.6f, 7.75f))
            drawCircle(color, radius = 1.1f, center = Offset(7.6f, 16.25f))
        }
        Icon.NETWORK -> {
            drawRoundRect(color, Offset(8.5f, 3.5f), Size(7f, 5.5f), CornerRadius(1.5f), style = line)
            drawRoundRect(color, Offset(3f, 15f), Size(7f, 5.5f), CornerRadius(1.5f), style = line)
            drawRoundRect(color, Offset(14f, 15f), Size(7f, 5.5f), CornerRadius(1.5f), style = line)
            stroke(color) { moveTo(12f, 9f); lineTo(12f, 12f); moveTo(6.5f, 15f); lineTo(6.5f, 12f); lineTo(17.5f, 12f); lineTo(17.5f, 15f) }
        }
        Icon.CHEVRON_UP -> stroke(color) { moveTo(5.5f, 15f); lineTo(12f, 8.5f); lineTo(18.5f, 15f) }
        Icon.CHEVRON_DOWN -> stroke(color) { moveTo(5.5f, 9f); lineTo(12f, 15.5f); lineTo(18.5f, 9f) }
        Icon.CLOSE -> stroke(color) { moveTo(6.5f, 6.5f); lineTo(17.5f, 17.5f); moveTo(17.5f, 6.5f); lineTo(6.5f, 17.5f) }
        Icon.CHECK -> stroke(color) { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) }
        Icon.PLUS -> stroke(color) { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) }
        Icon.SEARCH -> {
            drawCircle(color, radius = 6f, center = Offset(11f, 11f), style = line)
            stroke(color) { moveTo(15.6f, 15.6f); lineTo(20f, 20f) }
        }
        Icon.HEART -> stroke(color) { heart() }
        Icon.HEART_FILLED -> solid(color) { heart() }
        Icon.FOLDER -> stroke(color) {
            moveTo(3.5f, 7f); lineTo(9f, 7f); lineTo(11f, 9.5f); lineTo(20.5f, 9.5f); lineTo(20.5f, 18.5f); lineTo(3.5f, 18.5f); close()
        }
        Icon.REFRESH -> {
            drawArc(color, startAngle = -40f, sweepAngle = 300f, useCenter = false, topLeft = Offset(4.5f, 4.5f), size = Size(15f, 15f), style = line)
            stroke(color) { moveTo(18.9f, 3.6f); lineTo(18.4f, 8f); lineTo(14.2f, 7.2f) }
        }
        Icon.INFO -> {
            drawCircle(color, radius = 8f, center = Offset(12f, 12f), style = line)
            stroke(color) { moveTo(12f, 11f); lineTo(12f, 16f) }
            drawCircle(color, radius = 1.2f, center = Offset(12f, 7.9f))
        }
        Icon.OFFLINE -> {
            drawCircle(color, radius = 8f, center = Offset(12f, 12f), style = line)
            stroke(color) { moveTo(6.4f, 6.4f); lineTo(17.6f, 17.6f) }
        }
        Icon.VOLUME -> {
            solid(color) { speaker() }
            drawArc(color, -42f, 84f, false, Offset(9.5f, 7.5f), Size(9f, 9f), style = line)
            drawArc(color, -42f, 84f, false, Offset(6f, 4f), Size(16f, 16f), style = line)
        }
        Icon.VOLUME_MUTED -> {
            solid(color) { speaker() }
            stroke(color) { moveTo(16f, 9.5f); lineTo(21f, 14.5f); moveTo(21f, 9.5f); lineTo(16f, 14.5f) }
        }
        Icon.SUBTITLES -> {
            drawRoundRect(color, Offset(3f, 5.5f), Size(18f, 13f), CornerRadius(3f), style = line)
            stroke(color) {
                moveTo(6.8f, 11f); lineTo(9.8f, 11f); moveTo(12.6f, 11f); lineTo(17.2f, 11f)
                moveTo(6.8f, 14.6f); lineTo(12.4f, 14.6f); moveTo(15.2f, 14.6f); lineTo(17.2f, 14.6f)
            }
        }
        Icon.AUDIO -> stroke(color) {
            moveTo(5.5f, 10f); lineTo(5.5f, 14f); moveTo(9.8f, 6f); lineTo(9.8f, 18f)
            moveTo(14.2f, 9f); lineTo(14.2f, 15f); moveTo(18.5f, 7.5f); lineTo(18.5f, 16.5f)
        }
        Icon.CHAPTERS -> stroke(color) {
            moveTo(4.5f, 7f); lineTo(5f, 7f); moveTo(9f, 7f); lineTo(19.5f, 7f)
            moveTo(4.5f, 12f); lineTo(5f, 12f); moveTo(9f, 12f); lineTo(19.5f, 12f)
            moveTo(4.5f, 17f); lineTo(5f, 17f); moveTo(9f, 17f); lineTo(19.5f, 17f)
        }
        Icon.FULLSCREEN -> stroke(color) {
            moveTo(4.5f, 9f); lineTo(4.5f, 4.5f); lineTo(9f, 4.5f); moveTo(15f, 4.5f); lineTo(19.5f, 4.5f); lineTo(19.5f, 9f)
            moveTo(19.5f, 15f); lineTo(19.5f, 19.5f); lineTo(15f, 19.5f); moveTo(9f, 19.5f); lineTo(4.5f, 19.5f); lineTo(4.5f, 15f)
        }
        Icon.FULLSCREEN_EXIT -> stroke(color) {
            moveTo(9f, 4.5f); lineTo(9f, 9f); lineTo(4.5f, 9f); moveTo(19.5f, 9f); lineTo(15f, 9f); lineTo(15f, 4.5f)
            moveTo(15f, 19.5f); lineTo(15f, 15f); lineTo(19.5f, 15f); moveTo(4.5f, 15f); lineTo(9f, 15f); lineTo(9f, 19.5f)
        }
    }
}
