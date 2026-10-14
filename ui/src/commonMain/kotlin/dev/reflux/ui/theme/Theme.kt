package dev.reflux.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.reflux.design.Argb
import dev.reflux.design.LayoutProfile
import dev.reflux.design.MotionSpec
import dev.reflux.design.Palette

fun Argb.toColor(): Color = Color(value.toInt())

/** The design tokens of `design` as Compose colors. */
object RefluxColors {
    val background = Palette.background.toColor()
    val surface = Palette.surface.toColor()
    val surfaceRaised = Palette.surfaceRaised.toColor()
    val textPrimary = Palette.textPrimary.toColor()
    val textSecondary = Palette.textSecondary.toColor()
    val textTertiary = Palette.textTertiary.toColor()
    val primaryAction = Palette.primaryAction.toColor()
    val onPrimaryAction = Palette.onPrimaryAction.toColor()
    val focusRing = Palette.focusRing.toColor()
    val progress = Palette.progress.toColor()
    val hairline = Palette.hairline.toColor()
    val unavailable = Palette.unavailable.toColor()
    val error = Palette.error.toColor()
}

/**
 * The type scale. Sizes are multiplied by [LayoutProfile.textScale] (10-foot UI on TVs); [displaySize] lets a
 * phone set its featured titles smaller than a wide window does.
 *
 * Type carries the hierarchy: a title is large and tight, what describes it is a step down in size, weight,
 * and tone, so it supports the title rather than competing with it.
 */
@Immutable
class Typography(private val scale: Float, displaySize: Float = 52f) {
    private fun style(size: Float, weight: FontWeight, lineHeight: Float, tracking: Float = 0f) = TextStyle(
        fontSize = (size * scale).sp,
        fontWeight = weight,
        lineHeight = (size * lineHeight * scale).sp,
        letterSpacing = tracking.em,
    )

    /** Featured titles on Home and details pages. */
    val display = style(displaySize, FontWeight.Bold, 1.04f, -0.028f)
    val headline = style(30f, FontWeight.SemiBold, 1.12f, -0.02f)
    val title = style(20f, FontWeight.SemiBold, 1.25f, -0.01f)
    val body = style(15f, FontWeight.Normal, 1.55f)
    val bodyStrong = style(15f, FontWeight.SemiBold, 1.4f)

    /** What describes a title (year, runtime, rating, how much is left): quieter than the title, not tiny. */
    val metadata = style(14f, FontWeight.Medium, 1.35f, 0.01f)
    val caption = style(13f, FontWeight.Normal, 1.35f)
    val captionStrong = style(13f, FontWeight.SemiBold, 1.35f)

    /** Small labels: badges, button prompts, the titles of menus and notices. Sentence case, lightly tracked. */
    val label = style(12f, FontWeight.SemiBold, 1.3f, 0.02f)
}

val LocalTypography = staticCompositionLocalOf { Typography(1f) }

/** The current adaptive layout: recomputed when the window or the input modality changes. */
val LocalLayout = compositionLocalOf<LayoutProfile> { error("No layout profile provided") }


fun <T> MotionSpec.tween(): TweenSpec<T> =
    tween(durationMillis = durationMs, easing = CubicBezierEasing(easing.x1, easing.y1, easing.x2, easing.y2))

/** Text in the Reflux type scale. The single text primitive of the experience layer. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTypography.current.body,
    color: Color = RefluxColors.textPrimary,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = align ?: TextAlign.Unspecified),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
