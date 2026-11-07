package dev.reflux.design

/**
 * The canonical palette's raw values, from which [ColorRoles.Reflux] is made; components draw with the roles, not
 * with these. Neutral and dark, so artwork carries all of the color. Let the media be colorful; let
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

