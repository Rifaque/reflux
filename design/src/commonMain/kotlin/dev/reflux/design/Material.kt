package dev.reflux.design

import kotlin.math.pow

/**
 * Material roles (docs/DESIGN.md). Each communicates depth and interaction, not decoration. Functional material
 * (glass) is for what floats above the media: navigation, bars, sheets, panels, notices, transient controls. It is
 * never put on cards, rows, posters, or whole backgrounds.
 */
enum class MaterialRole(val elevation: Elevation) {
    BASE(Elevation.FLAT),
    ELEVATED(Elevation.RAISED),
    FLOATING(Elevation.FLOATING),
    INTERACTIVE(Elevation.RAISED),
    FOCUSED(Elevation.FLOATING),
    MODAL(Elevation.TRANSIENT),
}

/**
 * How much of the material system a device draws. Internal: never a setting, never a different identity. The
 * hierarchy, roles, and semantics are the same in all three; only how material is rendered changes.
 */
enum class MaterialTier {
    /** Opaque and tonal: neutral surfaces, hairlines, restrained elevation. No backdrop effect. Works everywhere. */
    BASIC,

    /** Restrained frosted material where blur is cheap enough: functional surfaces blur what is behind them. */
    ENHANCED,

    /**
     * Capable handhelds and desktops: richer material whose strength follows the artwork actually behind it, and a
     * little more spatial depth.
     */
    PREMIUM,
    ;

    /** Whether a page that opens on artwork may let it run under the navigation, which becomes a foreground layer. */
    val artworkUnderNavigation: Boolean get() = this != BASIC

    /** Whether functional material blurs what is behind it. */
    val blurs: Boolean get() = this != BASIC

    /** Whether material strength is measured against the artwork behind it rather than the worst case. */
    val adaptsToBackdrop: Boolean get() = this == PREMIUM
}

/** What decides a device's [MaterialTier]: what it can do, and what the viewer's system settings ask for. */
data class MaterialEnvironment(
    val formFactor: FormFactor,
    /** Whether the toolkit can blur a backdrop here (Android before 12 cannot). */
    val blursBackdrop: Boolean,
    /** The system asked for less transparency (Windows' transparency effects off, for one). */
    val reduceTransparency: Boolean = false,
    /** The system asked for more contrast. */
    val highContrast: Boolean = false,
    /** A device the system calls low on memory, or one where effects are known to cost too much. */
    val constrained: Boolean = false,
)

object MaterialTiers {
    /**
     * Legibility first: a request for less transparency or more contrast, or a platform without blur, is BASIC. A
     * television is held to ENHANCED (remote navigation must stay instant, and the page is mostly artwork already),
     * as is a constrained device. Everything else is PREMIUM.
     */
    fun select(environment: MaterialEnvironment): MaterialTier = when {
        environment.reduceTransparency || environment.highContrast || !environment.blursBackdrop -> MaterialTier.BASIC
        environment.constrained || environment.formFactor == FormFactor.TV -> MaterialTier.ENHANCED
        else -> MaterialTier.PREMIUM
    }
}

/**
 * How a material is drawn: a tint over blurred content behind it, a hairline highlight, and a shadow. Blur is
 * applied to what is behind the material (typically artwork), never to the material's own content.
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
    /** The material for [role] as [tier] draws it. */
    fun spec(role: MaterialRole, tier: MaterialTier = MaterialTier.ENHANCED, colors: ColorRoles = ColorRoles.Reflux): MaterialSpec {
        val base = enhanced(role, colors)
        return when (tier) {
            MaterialTier.ENHANCED -> base
            // Opaque, separated by tone and a stronger hairline rather than by blur.
            MaterialTier.BASIC -> if (role == MaterialRole.BASE) base else base.copy(
                tintAlpha = 1f,
                blurRadiusDp = 0f,
                highlightAlpha = maxOf(base.highlightAlpha, 0.10f),
            )
            // A little more of the artwork comes through, and the edge catches a little more light; legibility is
            // still guarded by [scrimFor], against the artwork actually behind it.
            MaterialTier.PREMIUM -> if (role == MaterialRole.BASE) base else base.copy(
                tintAlpha = (base.tintAlpha - 0.08f).coerceAtLeast(0.40f),
                blurRadiusDp = base.blurRadiusDp * 1.25f,
                highlightAlpha = base.highlightAlpha + 0.02f,
            )
        }
    }

    private fun enhanced(role: MaterialRole, colors: ColorRoles): MaterialSpec = when (role) {
        MaterialRole.BASE -> MaterialSpec(colors.background, 1f, 0f, 0f, 0f, 0f)
        MaterialRole.ELEVATED -> MaterialSpec(colors.surface, 0.72f, 24f, role.elevation.highlightAlpha, role.elevation.shadowDp, Radius.LARGE)
        // Frosted rather than tinted: the blur does the separating, so the tint can stay light.
        MaterialRole.FLOATING -> MaterialSpec(colors.surface, 0.56f, 48f, role.elevation.highlightAlpha, role.elevation.shadowDp, Radius.XLARGE)
        MaterialRole.INTERACTIVE -> MaterialSpec(colors.surfaceElevated, 0.60f, 28f, 0.11f, 4f, Radius.LARGE)
        MaterialRole.FOCUSED -> MaterialSpec(colors.surfaceElevated, 0.80f, 28f, 0.30f, 16f, Radius.LARGE)
        MaterialRole.MODAL -> MaterialSpec(colors.surface, 0.84f, 60f, role.elevation.highlightAlpha, role.elevation.shadowDp, Radius.XLARGE)
    }

    /**
     * The tint alpha that keeps [text] at [minimumContrast] on [role] over [backdrop]: the material's own alpha, or
     * more where the backdrop is bright. Without a measured backdrop the worst case is assumed (bright artwork).
     */
    fun scrimFor(
        role: MaterialRole,
        text: Argb,
        minimumContrast: Double = 4.5,
        tier: MaterialTier = MaterialTier.ENHANCED,
        backdrop: Argb = BRIGHT_ARTWORK,
    ): Float {
        val spec = spec(role, tier)
        var alpha = spec.tintAlpha
        while (alpha < 1f && text.contrast(spec.tint.withAlpha(alpha).over(backdrop)) < minimumContrast) alpha += 0.02f
        return alpha.coerceAtMost(1f)
    }

    /** The bright artwork glass is held readable over when nothing better is known. */
    val BRIGHT_ARTWORK = Argb(0xFFD8D8D8L)

    /**
     * What is behind a material, from a sample of the artwork there (0xAARRGGBB): a grey as bright as its 85th
     * percentile, brighter than the blur's average, so a bright patch is allowed for. Null for an empty sample.
     */
    fun backdropOf(pixels: LongArray): Argb? {
        if (pixels.isEmpty()) return null
        val luminances = pixels.map { Argb(it).luminance }.sorted()
        val bright = luminances[((luminances.size - 1) * 0.85).toInt()]
        return greyOf(bright)
    }

    /** The grey of WCAG relative luminance [luminance]. */
    internal fun greyOf(luminance: Double): Argb {
        val s = if (luminance <= 0.0031308) luminance * 12.92 else 1.055 * luminance.pow(1 / 2.4) - 0.055
        val v = (s * 255).toInt().coerceIn(0, 255)
        return Argb(v, v, v)
    }
}
