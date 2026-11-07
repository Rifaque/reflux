package dev.reflux.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import dev.reflux.design.Argb
import dev.reflux.design.ColorRoles
import dev.reflux.design.FormFactor
import dev.reflux.design.MaterialTier
import dev.reflux.design.Motion
import dev.reflux.design.Radius
import dev.reflux.design.StateLayers
import dev.reflux.design.TypeScale
import dev.reflux.design.TypeSpec
import dev.reflux.design.LayoutProfile
import dev.reflux.design.MotionSpec

fun Argb.toColor(): Color = Color(value.toInt())

/**
 * The appearance in force: the color roles and the material tier. Held here rather than only in composition locals
 * because drawing code (scrims, rings) is not composable and cannot read one; [dev.reflux.ui.RefluxTheme] keeps it
 * in step with the system's settings. There is one appearance, [ColorRoles.Reflux]; high contrast is that same
 * appearance answering an accessibility setting (DECISIONS.md, 2026-11-07, "One visual identity; themes are V2").
 */
internal object AppearancePolicy {
    var roles by mutableStateOf(ColorRoles.Reflux)
}

/**
 * The semantic color roles (`design`'s [ColorRoles]) as Compose colors. Components name a role, never a value.
 * Each read follows the appearance in force, so a change of it repaints what reads it.
 */
object RefluxColors {
    private val roles get() = AppearancePolicy.roles
    val background get() = roles.background.toColor()
    val backgroundElevated get() = roles.backgroundElevated.toColor()
    val surface get() = roles.surface.toColor()
    val surfaceElevated get() = roles.surfaceElevated.toColor()
    val contentPrimary get() = roles.contentPrimary.toColor()
    val contentSecondary get() = roles.contentSecondary.toColor()
    val contentTertiary get() = roles.contentTertiary.toColor()
    val contentDisabled get() = roles.contentDisabled.toColor()
    val divider get() = roles.divider.toColor()
    val focused get() = roles.focused.toColor()
    val primaryAction get() = roles.primaryAction.toColor()
    val onPrimaryAction get() = roles.onPrimaryAction.toColor()
    val progress get() = roles.progress.toColor()
    val unavailable get() = roles.unavailable.toColor()
    val error get() = roles.error.toColor()
    val success get() = roles.success.toColor()
    val warning get() = roles.warning.toColor()
    val scrim get() = roles.scrim.toColor()
}

/**
 * Interaction state layers ([StateLayers]): the light a hovered, pressed, selected, or switched-on surface is lit
 * by. Stronger at high contrast. Every component lights its states from here, so they look alike everywhere.
 */
object States {
    /** The state layer at [alpha] ([StateLayers]' values, or a sum of them). */
    fun layer(alpha: Float): Color {
        val roles = AppearancePolicy.roles
        return roles.stateLayer.toColor().copy(alpha = (alpha * roles.stateStrength).coerceIn(0f, 1f))
    }

    val rest get() = layer(StateLayers.REST)
    val hover get() = layer(StateLayers.HOVER)
    val pressed get() = layer(StateLayers.PRESSED)
    val selected get() = layer(StateLayers.SELECTED)
    val on get() = layer(StateLayers.ON)
    val disabled get() = layer(StateLayers.DISABLED)
}

/** The radius family (`design`'s [Radius]) as shapes. A component takes one of these or a pill. */
object Shapes {
    val small = RoundedCornerShape(Radius.SMALL.dp)
    val medium = RoundedCornerShape(Radius.MEDIUM.dp)
    val large = RoundedCornerShape(Radius.LARGE.dp)
    val xlarge = RoundedCornerShape(Radius.XLARGE.dp)
}

/**
 * The type roles (`design`'s [TypeScale]). Sizes are multiplied by [LayoutProfile.textScale] (a step up on a
 * television); only the featured title changes with the window ([TypeScale.display]).
 *
 * Type carries the hierarchy: a title is large and tight, what describes it is a step down in size, weight,
 * and tone, so it supports the title rather than competing with it.
 */
@Immutable
class Typography(private val scale: Float, formFactor: FormFactor = FormFactor.DESKTOP) {
    private fun style(spec: TypeSpec) = TextStyle(
        fontSize = (spec.size * scale).sp,
        fontWeight = FontWeight(spec.weight),
        lineHeight = (spec.size * spec.lineHeight * scale).sp,
        letterSpacing = spec.tracking.em,
    )

    /** Featured titles on Home and details pages: the hero title. */
    val display = style(TypeScale.display(formFactor))

    /** A page's own title. */
    val headline = style(TypeScale.pageTitle)

    /** A section of a page. */
    val title = style(TypeScale.sectionTitle)
    val body = style(TypeScale.body)
    val bodyStrong = style(TypeScale.bodyStrong)

    /** What describes a title (year, runtime, rating, how much is left): quieter than the title, not tiny. */
    val metadata = style(TypeScale.metadata)
    val caption = style(TypeScale.caption)

    /** A card's title under its artwork. */
    val captionStrong = style(TypeScale.mediaTitle)

    /** Small labels: badges, button prompts, the titles of menus and notices. Sentence case, lightly tracked. */
    val label = style(TypeScale.utility)

    /** A destination's name beside its icon. */
    val navigation = style(TypeScale.navigation)

    /** A destination's name beneath its icon. */
    val navigationCompact = style(TypeScale.navigationCompact)

    /** A label read from across a room. */
    val tvLabel = style(TypeScale.tvLabel)
}

val LocalTypography = staticCompositionLocalOf { Typography(1f) }

/** How much of the material system this device draws ([MaterialTiers]); internal, never a setting. */
val LocalMaterialTier = staticCompositionLocalOf { MaterialTier.ENHANCED }

/** How motion is applied on this form factor ([Motion.profile]). */
val LocalMotion = staticCompositionLocalOf { Motion.profile(FormFactor.DESKTOP) }

/** The current adaptive layout: recomputed when the window or the input modality changes. */
val LocalLayout = compositionLocalOf<LayoutProfile> { error("No layout profile provided") }


/**
 * Whether motion is reduced. The one gate almost every animation already passes through ([tween]). It is held
 * here rather than in a composition local because transition specifications are not composable and cannot read
 * one. [dev.reflux.ui.RefluxTheme] keeps it in step with the system.
 */
internal object MotionPolicy {
    var reduced by mutableStateOf(false)
}

/** The spec as Compose wants it; with motion reduced a state change takes no time at all. */
fun <T> MotionSpec.tween(): TweenSpec<T> =
    tween(durationMillis = if (MotionPolicy.reduced) 0 else durationMs, easing = CubicBezierEasing(easing.x1, easing.y1, easing.x2, easing.y2))

/** Text in the Reflux type scale. The single text primitive of the experience layer. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTypography.current.body,
    color: Color = RefluxColors.contentPrimary,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = align ?: TextAlign.Unspecified),
        onTextLayout = onTextLayout,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
