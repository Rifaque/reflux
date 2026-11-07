package dev.reflux.design

/**
 * The semantic color roles every component draws with (docs/DESIGN.md, "Visual system"). A component names a
 * role ("secondary content", "selected"), never a value, so that an appearance is one instance of this class.
 *
 * Reflux ships exactly one appearance, [Reflux] (dark, neutral-first). [RefluxHighContrast] is not a theme: it is
 * the same appearance answering the system's high-contrast setting, as reduced motion answers its own. User-
 * selectable themes are deferred past 1.0 (DECISIONS.md, 2026-11-07, "One visual identity; themes are V2"); this
 * structure is what would let them be added without touching components.
 */
data class ColorRoles(
    /** The page itself: what everything sits on. */
    val background: Argb,
    /** A step above the page, for areas of the page that are set apart without floating (a source's card). */
    val backgroundElevated: Argb,
    /** The base of functional material: navigation, bars, sheets, panels. */
    val surface: Argb,
    /** Functional material one step up: an interactive surface, a lifted row. */
    val surfaceElevated: Argb,
    val contentPrimary: Argb,
    val contentSecondary: Argb,
    /** Tertiary metadata: what is there to be found rather than read first. Large text or non-essential only. */
    val contentTertiary: Argb,
    /** Content that cannot be acted on now. */
    val contentDisabled: Argb,
    /** The hairline that defines a shape against the page. */
    val divider: Argb,
    /** The focus ring: drawn as a ring, never as a fill, so focus is a shape and not only a color. */
    val focused: Argb,
    /** The one action a screen exists for. Bright and neutral, not a hue. */
    val primaryAction: Argb,
    val onPrimaryAction: Argb,
    val progress: Argb,
    /** A source or copy that is not there now. */
    val unavailable: Argb,
    val error: Argb,
    val success: Argb,
    val warning: Argb,
    /** What darkens artwork behind text and protects system bars; used with an alpha. */
    val scrim: Argb,
    /** The light state layers are made of (hover, selected, pressed), used with [StateLayers]' alphas. */
    val stateLayer: Argb,
    /** Multiplies [StateLayers]' alphas and the hairline's: high contrast draws states more strongly. */
    val stateStrength: Float = 1f,
) {
    /** Every role the interface's own chrome is drawn in: all neutral (a test holds them to no hue). */
    val chrome: List<Argb>
        get() = listOf(
            background, backgroundElevated, surface, surfaceElevated, contentPrimary, contentSecondary, contentTertiary,
            contentDisabled, divider, focused, primaryAction, onPrimaryAction, progress, unavailable, scrim, stateLayer,
        )

    companion object {
        /** The canonical Reflux appearance: near-black, charcoal, graphite, greys, white. */
        val Reflux = ColorRoles(
            background = Palette.background,
            backgroundElevated = Palette.surface,
            surface = Palette.surface,
            surfaceElevated = Palette.surfaceRaised,
            contentPrimary = Palette.textPrimary,
            contentSecondary = Palette.textSecondary,
            contentTertiary = Palette.textTertiary,
            contentDisabled = Palette.textTertiary,
            divider = Palette.hairline,
            focused = Palette.focusRing,
            primaryAction = Palette.primaryAction,
            onPrimaryAction = Palette.onPrimaryAction,
            progress = Palette.progress,
            unavailable = Palette.unavailable,
            error = Palette.error,
            success = Argb(0xFF8FD6A4L),
            warning = Argb(0xFFE8C77AL),
            scrim = Argb(0xFF000000L),
            stateLayer = Argb(0xFFFFFFFFL),
        )

        /**
         * The system asked for more contrast: secondary and tertiary content brighten, hairlines and state layers
         * strengthen, and material becomes opaque (MaterialTiers). Same hierarchy, same identity.
         */
        val RefluxHighContrast = Reflux.copy(
            contentSecondary = Argb(0xFFD4D4D8L),
            contentTertiary = Argb(0xFFB4B4BAL),
            contentDisabled = Argb(0xFF9A9AA0L),
            divider = Argb(0x66FFFFFFL),
            stateStrength = 1.6f,
        )
    }
}

/**
 * How strongly each interaction state lights a surface, as an alpha of [ColorRoles.stateLayer]. One table, so a
 * hovered button and a hovered row, a selected tab and a selected rail item, look alike.
 */
object StateLayers {
    /** A resting chip or badge: just enough to be a shape. */
    const val REST = 0.04f

    /** Hovered by a pointer or focused (the "active" state). */
    const val HOVER = 0.06f

    /** Pressed by a finger or a click, while it is held. */
    const val PRESSED = 0.10f

    /** Selected: where the viewer is (a destination, a season, a list). */
    const val SELECTED = 0.12f

    /** A switched-on control (a favorite) and an active glass control. */
    const val ON = 0.18f

    /** A strong active state on glass, where the material already carries some light. */
    const val ACTIVE_ON_GLASS = 0.14f

    /** A control that cannot be used now. */
    const val DISABLED = 0.08f
}
