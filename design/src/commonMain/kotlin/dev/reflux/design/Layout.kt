package dev.reflux.design

import dev.reflux.core.input.InputModality

enum class FormFactor { PHONE, TABLET, DESKTOP, TV }

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
    /** Multiplier for the type scale (10-foot UI on TVs). */
    val textScale: Float,
    /** Whether button-glyph prompts are shown (controller/remote). */
    val showsButtonPrompts: Boolean,
    /** Whether a persistent focus indicator is shown. */
    val showsFocus: Boolean,
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
            FormFactor.TV -> 196f
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
            FormFactor.TV -> 24f
            else -> 16f
        }
        val usable = widthDp - 2 * margin
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
                formFactor == FormFactor.TV || directional -> 56f
                modality == InputModality.TOUCH -> 48f
                else -> 32f
            },
            textScale = if (formFactor == FormFactor.TV) 1.5f else 1f,
            showsButtonPrompts = directional,
            showsFocus = directional || modality == InputModality.KEYBOARD,
        )
    }
}
