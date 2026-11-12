package dev.reflux.design

import dev.reflux.core.input.InputModality

enum class FormFactor { PHONE, TABLET, DESKTOP, TV }

/**
 * Where the global navigation sits (docs/NAVIGATION.md, section 3). The destinations are the same everywhere;
 * only their place and how much of each is shown change.
 */
enum class NavigationStyle {
    /** A phone held upright: a bar along the bottom, in reach of a thumb, of icons without words. */
    BOTTOM_BAR,

    /** A phone on its side: a narrow rail of icons on the left, leaving the short screen to the content. */
    COMPACT_RAIL,

    /** A tablet: a rail on the left, icons with their names beneath. */
    RAIL,

    /** A desktop window: a sidebar on the left, icons with their names beside. */
    SIDEBAR,

    /** A television: a rail of icons on the left that shows their names while it has focus. */
    TV_RAIL,
    ;

    /** Whether the navigation runs down the left side, so pages start to its right. */
    val vertical: Boolean get() = this != BOTTOM_BAR

    /** How much of the window's width the navigation keeps, in dp; pages start after it. */
    val widthDp: Float
        get() = when (this) {
            BOTTOM_BAR -> 0f
            COMPACT_RAIL -> 72f
            RAIL -> 88f
            SIDEBAR -> 216f
            TV_RAIL -> 64f
        }
}

/** How the mini-player is laid out in its dock. */
enum class DockStyle {
    /** One row: the picture at its leading end, what is playing beside it, and its controls. For short or narrow windows. */
    BAR,

    /** The picture above what is playing and its controls, in a corner of the page. */
    CARD,
}

/**
 * Where what is playing sits while the viewer browses: the mini-player's dock (docs/NAVIGATION.md, section 8). The
 * dock is part of the navigation's own layer: above the phone's bar, or in the bottom corner of the page beside a
 * rail or a sidebar. Whether a device presents playback there at all is the platform's to say, not the layout's.
 */
data class MiniPlayerDock(
    val style: DockStyle,
    /** The dock's width; a [DockStyle.BAR] is at most this wide and narrower where the window is. */
    val widthDp: Float,
    /** The picture's box inside the dock, 16:9; the video is fitted inside it at its own shape. */
    val videoWidthDp: Float,
) {
    val videoHeightDp: Float get() = videoWidthDp * 9f / 16f

    /** How tall the dock is, before a large text size grows its words. */
    val heightDp: Float
        get() = when (style) {
            DockStyle.BAR -> videoHeightDp + 2 * PADDING_DP
            DockStyle.CARD -> PADDING_DP + videoHeightDp + CARD_STRIP_DP + PADDING_DP
        }

    companion object {
        /** The space between the dock's edge and what is in it. */
        const val PADDING_DP = 8f

        /** The row beneath a card's picture: what is playing, and its controls. */
        const val CARD_STRIP_DP = 60f

        /** The space kept between the dock and the navigation or the window's edge. */
        const val GAP_DP = 8f
    }
}

/** Concrete layout values for a window. Recomputed whenever the window size or input modality changes. */
data class LayoutProfile(
    val formFactor: FormFactor,
    val modality: InputModality,
    val posterWidthDp: Float,
    val columns: Int,
    val gutterDp: Float,
    /** Outer margins; TVs keep content inside the overscan-safe area. */
    val safeMarginDp: Float,
    /** Scale applied to a focused card. 1 when focus is not shown by scaling (touch). */
    val focusScale: Float,
    /** Minimum interactive target size. */
    val minTargetDp: Float,
    /** Multiplier for the type scale (a step up on TVs). */
    val textScale: Float,
    /** Whether button-glyph prompts are shown (controller/remote). */
    val showsButtonPrompts: Boolean,
    /** Whether a persistent focus indicator is shown. */
    val showsFocus: Boolean,
    /** Where the global navigation sits. */
    val navigation: NavigationStyle = NavigationStyle.BOTTOM_BAR,
    /** The mini-player's dock in this window; null where there is none (a television). */
    val dock: MiniPlayerDock? = null,
) {
    val posterHeightDp: Float get() = posterWidthDp * 1.5f
}

/**
 * Adaptive layout rules. Form factor comes from the device and window; modality refines it.
 *
 * Switching modality never changes the form factor or the information architecture — only density, focus
 * visuals, and prompts — which is what makes the controller morph a smooth transition rather than a
 * destructive "TV mode".
 */
object AdaptiveLayout {
    fun formFactor(widthDp: Float, heightDp: Float, television: Boolean): FormFactor = when {
        television -> FormFactor.TV
        minOf(widthDp, heightDp) < 600f -> FormFactor.PHONE
        widthDp < 1200f -> FormFactor.TABLET
        else -> FormFactor.DESKTOP
    }

    fun profile(widthDp: Float, heightDp: Float, television: Boolean, modality: InputModality): LayoutProfile {
        val formFactor = formFactor(widthDp, heightDp, television)
        val directional = modality == InputModality.DIRECTIONAL
        val basePoster = when (formFactor) {
            FormFactor.PHONE -> 112f
            FormFactor.TABLET -> 148f
            FormFactor.DESKTOP -> 168f
            // A television's canvas is 960 by 540 whatever its resolution: six titles across.
            FormFactor.TV -> 124f
        }
        // Controller and remote targets are larger and fewer, so focus travel stays short.
        val posterWidth = if (directional && formFactor != FormFactor.TV) basePoster * 1.15f else basePoster
        val margin = when (formFactor) {
            FormFactor.TV -> maxOf(48f, widthDp * 0.05f) // 5% overscan-safe area
            FormFactor.PHONE -> 16f
            FormFactor.TABLET -> 24f
            FormFactor.DESKTOP -> 40f
        }
        val gutter = when (formFactor) {
            FormFactor.PHONE -> 10f
            FormFactor.TV -> 18f
            else -> 16f
        }
        val navigation = when (formFactor) {
            FormFactor.TV -> NavigationStyle.TV_RAIL
            FormFactor.PHONE -> if (widthDp > heightDp) NavigationStyle.COMPACT_RAIL else NavigationStyle.BOTTOM_BAR
            FormFactor.TABLET -> NavigationStyle.RAIL
            FormFactor.DESKTOP -> NavigationStyle.SIDEBAR
        }
        // Pages start to the right of a navigation that runs down the side.
        val usable = widthDp - navigation.widthDp - 2 * margin
        val columns = maxOf(2, ((usable + gutter) / (posterWidth + gutter)).toInt())
        return LayoutProfile(
            formFactor = formFactor,
            modality = modality,
            posterWidthDp = posterWidth,
            columns = columns,
            gutterDp = gutter,
            safeMarginDp = margin,
            focusScale = when {
                modality == InputModality.TOUCH -> 1f
                directional -> 1.08f
                else -> 1.04f
            },
            minTargetDp = when {
                // Focus needs no finger-sized target, and a television's canvas is small: its controls
                // are sized to be read, not to be hit.
                formFactor == FormFactor.TV -> 46f
                directional -> 56f
                modality == InputModality.TOUCH -> 48f
                else -> 32f
            },
            // The 10-foot scale is already in a television's density (1080p is 960 by 540); type is
            // only a step larger than at a desk.
            textScale = if (formFactor == FormFactor.TV) 1.1f else 1f,
            showsButtonPrompts = directional,
            showsFocus = directional || modality == InputModality.KEYBOARD,
            navigation = navigation,
            dock = dock(navigation, widthDp),
        )
    }

    /**
     * The mini-player's dock for a window whose navigation is [navigation]. A phone has a row: above its bar when held
     * upright, in the bottom corner on its side, where the screen is too short for a picture above words. A tablet and
     * a desktop have a card in the bottom corner of the page, which never takes more than half the page's width: a
     * tablet window too narrow for that (one of two side by side) has the row instead. A television has none: its
     * player is the whole screen, and leaving it pauses (DECISIONS.md, 2026-11-12).
     */
    fun dock(navigation: NavigationStyle, widthDp: Float): MiniPlayerDock? = when (navigation) {
        NavigationStyle.BOTTOM_BAR -> MiniPlayerDock(DockStyle.BAR, widthDp = 360f, videoWidthDp = 96f)
        NavigationStyle.COMPACT_RAIL -> MiniPlayerDock(DockStyle.BAR, widthDp = 328f, videoWidthDp = 96f)
        NavigationStyle.RAIL ->
            if ((widthDp - navigation.widthDp) / 2 >= 288f) MiniPlayerDock(DockStyle.CARD, widthDp = 288f, videoWidthDp = 272f)
            else MiniPlayerDock(DockStyle.BAR, widthDp = 328f, videoWidthDp = 96f)
        NavigationStyle.SIDEBAR -> MiniPlayerDock(DockStyle.CARD, widthDp = 336f, videoWidthDp = 320f)
        NavigationStyle.TV_RAIL -> null
    }
}
