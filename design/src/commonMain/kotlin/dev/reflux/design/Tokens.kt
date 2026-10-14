package dev.reflux.design

/**
 * The default palette: neutral and dark, so artwork carries all of the color. Let the media be colorful; let
 * the interface be calm. No token here has a hue of its own (verified by tests) except [error], which has to
 * be told apart from everything else. Text tokens meet WCAG AA on every surface they are used on, including
 * through glass materials.
 */
object Palette {
    val background = Argb(0xFF0A0A0BL)
    val surface = Argb(0xFF141416L)
    val surfaceRaised = Argb(0xFF1D1D20L)
    val textPrimary = Argb(0xFFF5F5F6L)
    val textSecondary = Argb(0xFFB2B2B8L)
    val textTertiary = Argb(0xFF85858CL)
    /** The one action a screen exists for (Play, Connect, Use this folder): bright and neutral, not a hue. */
    val primaryAction = Argb(0xFFF5F5F6L)
    val onPrimaryAction = Argb(0xFF0A0A0BL)
    val focusRing = Argb(0xFFFFFFFFL)
    /** How far the user is, wherever progress is drawn. */
    val progress = Argb(0xFFF5F5F6L)
    /** The edge of a surface or a badge: enough to define a shape on black, never enough to be noticed. */
    val hairline = Argb(0x24FFFFFFL)
    val unavailable = Argb(0xFF7C7C84L)
    val error = Argb(0xFFF08080L)

    /** Every token the interface's own chrome is drawn in: all of them neutral. */
    val chrome: List<Argb> get() = listOf(
        background, surface, surfaceRaised, textPrimary, textSecondary, textTertiary,
        primaryAction, onPrimaryAction, focusRing, progress, hairline, unavailable,
    )
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
        // Frosted rather than tinted: the blur does the separating, so the tint can stay light.
        MaterialRole.FLOATING -> MaterialSpec(Palette.surface, 0.56f, 48f, 0.09f, 12f, 20f)
        MaterialRole.INTERACTIVE -> MaterialSpec(Palette.surfaceRaised, 0.60f, 28f, 0.11f, 4f, 14f)
        MaterialRole.FOCUSED -> MaterialSpec(Palette.surfaceRaised, 0.80f, 28f, 0.30f, 16f, 14f)
        MaterialRole.MODAL -> MaterialSpec(Palette.surface, 0.84f, 60f, 0.08f, 24f, 24f)
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
