package dev.reflux.design

/**
 * The default palette: a dark, cinematic base so artwork carries the color. Text tokens are chosen to meet
 * WCAG AA on every surface they are used on (verified by tests), including through glass materials.
 */
object Palette {
    val background = Argb(0xFF0B0C0FL)
    val surface = Argb(0xFF15171CL)
    val surfaceRaised = Argb(0xFF1E2128L)
    val textPrimary = Argb(0xFFF4F5F7L)
    val textSecondary = Argb(0xFFB4B9C4L)
    val textTertiary = Argb(0xFF8A909CL)
    val accent = Argb(0xFF6EA8FFL)
    val onAccent = Argb(0xFF06101FL)
    val focusRing = Argb(0xFFFFFFFFL)
    val progress = Argb(0xFFE8EAEEL)
    val unavailable = Argb(0xFF7A7F8AL)
    val error = Argb(0xFFFF7A7AL)
}

/** Liquid-glass material roles (docs/DESIGN.md). Each role communicates depth and interaction, not decoration. */
enum class MaterialRole { BASE, ELEVATED, FLOATING, INTERACTIVE, FOCUSED, MODAL }

/**
 * How a material is drawn: a tint over blurred content behind it, a hairline highlight, and a shadow.
 * Blur is applied to what is behind the material (typically artwork), never to the material's own content.
 */
data class MaterialSpec(
    val tint: Argb,
    val tintAlpha: Float,
    val blurRadiusDp: Float,
    val highlightAlpha: Float,
    val shadowElevationDp: Float,
    val cornerRadiusDp: Float,
)

object Materials {
    fun spec(role: MaterialRole): MaterialSpec = when (role) {
        MaterialRole.BASE -> MaterialSpec(Palette.background, 1f, 0f, 0f, 0f, 0f)
        MaterialRole.ELEVATED -> MaterialSpec(Palette.surface, 0.72f, 24f, 0.06f, 2f, 16f)
        MaterialRole.FLOATING -> MaterialSpec(Palette.surface, 0.62f, 40f, 0.10f, 12f, 20f)
        MaterialRole.INTERACTIVE -> MaterialSpec(Palette.surfaceRaised, 0.66f, 28f, 0.12f, 4f, 14f)
        MaterialRole.FOCUSED -> MaterialSpec(Palette.surfaceRaised, 0.80f, 28f, 0.35f, 16f, 14f)
        MaterialRole.MODAL -> MaterialSpec(Palette.surface, 0.86f, 56f, 0.08f, 24f, 24f)
    }

    /** Text over glass that sits on bright artwork gets an extra scrim so it always stays readable. */
    fun scrimFor(role: MaterialRole, text: Argb, minimumContrast: Double = 4.5): Float {
        val spec = spec(role)
        var alpha = spec.tintAlpha
        while (alpha < 1f && text.contrast(spec.tint.withAlpha(alpha).over(BRIGHT_ARTWORK)) < minimumContrast) alpha += 0.02f
        return alpha.coerceAtMost(1f)
    }

    private val BRIGHT_ARTWORK = Argb(0xFFD8D8D8L)
}

/** A cubic Bézier easing curve (CSS / Compose convention). */
data class Easing(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

data class MotionSpec(val durationMs: Int, val easing: Easing)

/** Motion communicates state and continuity; durations are short enough never to slow down navigation. */
object Motion {
    val standard = Easing(0.2f, 0f, 0f, 1f)
    val emphasized = Easing(0.05f, 0.7f, 0.1f, 1f)
    val exit = Easing(0.3f, 0f, 1f, 1f)

    val focusChange = MotionSpec(140, standard)
    val controlsShow = MotionSpec(180, standard)
    val controlsHide = MotionSpec(240, exit)
    val navigation = MotionSpec(320, emphasized)
    val posterToDetails = MotionSpec(420, emphasized)

    /** The signature input-modality morph: layout density, focus visuals, and prompts change together. */
    val inputMorph = MotionSpec(360, emphasized)

    /** Player controls hide after this much inactivity while playing. */
    const val CONTROLS_AUTO_HIDE_MS: Long = 3_500
}
