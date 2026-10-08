package dev.reflux.design

import dev.reflux.core.input.InputModality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorTest {
    @Test
    fun contrastFollowsWcag() {
        assertEquals(21.0, Argb(0xFFFFFFFFL).contrast(Argb(0xFF000000L)), 0.01)
        assertEquals(1.0, Palette.accent.contrast(Palette.accent), 0.0001)
    }

    @Test
    fun hslRoundTrips() {
        for (color in listOf(Argb(255, 0, 0), Argb(18, 52, 86), Argb(200, 200, 40), Argb(128, 128, 128))) {
            val back = Hsl.of(color).toArgb()
            assertTrue(kotlin.math.abs(back.red - color.red) <= 1 && kotlin.math.abs(back.green - color.green) <= 1 && kotlin.math.abs(back.blue - color.blue) <= 1, "$color -> $back")
        }
    }
}

class TokenTest {
    @Test
    fun textMeetsAaOnEverySurface() {
        for (surface in listOf(Palette.background, Palette.surface, Palette.surfaceRaised)) {
            assertTrue(Palette.textPrimary.contrast(surface) >= 7.0, "primary on $surface")
            assertTrue(Palette.textSecondary.contrast(surface) >= 4.5, "secondary on $surface")
            assertTrue(Palette.textTertiary.contrast(surface) >= 3.0, "tertiary (large text only) on $surface")
        }
        assertTrue(Palette.onAccent.contrast(Palette.accent) >= 4.5)
    }

    @Test
    fun glassStaysReadableOverBrightArtwork() {
        for (role in MaterialRole.entries.filter { it != MaterialRole.BASE }) {
            val alpha = Materials.scrimFor(role, Palette.textPrimary)
            val spec = Materials.spec(role)
            val surface = spec.tint.withAlpha(alpha).over(Argb(0xFFD8D8D8L))
            assertTrue(Palette.textPrimary.contrast(surface) >= 4.5, "$role at $alpha")
        }
    }

    @Test
    fun materialsExpressHierarchy() {
        assertTrue(Materials.spec(MaterialRole.FOCUSED).highlightAlpha > Materials.spec(MaterialRole.INTERACTIVE).highlightAlpha)
        assertTrue(Materials.spec(MaterialRole.MODAL).blurRadiusDp > Materials.spec(MaterialRole.ELEVATED).blurRadiusDp)
    }
}

class LayoutTest {
    @Test
    fun formFactors() {
        assertEquals(FormFactor.PHONE, AdaptiveLayout.formFactor(390f, 844f, television = false))
        assertEquals(FormFactor.PHONE, AdaptiveLayout.formFactor(844f, 390f, television = false))
        assertEquals(FormFactor.TABLET, AdaptiveLayout.formFactor(820f, 1180f, television = false))
        assertEquals(FormFactor.DESKTOP, AdaptiveLayout.formFactor(1600f, 900f, television = false))
        assertEquals(FormFactor.TV, AdaptiveLayout.formFactor(960f, 540f, television = true))
    }

    @Test
    fun controllerMorphChangesDensityNotStructure() {
        val pointer = AdaptiveLayout.profile(1600f, 900f, false, InputModality.POINTER)
        val controller = AdaptiveLayout.profile(1600f, 900f, false, InputModality.DIRECTIONAL)
        assertEquals(pointer.formFactor, controller.formFactor)
        assertTrue(controller.posterWidthDp > pointer.posterWidthDp)
        assertTrue(controller.columns <= pointer.columns)
        assertTrue(controller.focusScale > pointer.focusScale)
        assertTrue(controller.showsButtonPrompts && controller.showsFocus)
        assertTrue(!pointer.showsButtonPrompts && !pointer.showsFocus)
    }

    @Test
    fun televisionKeepsContentInsideTheSafeArea() {
        val tv = AdaptiveLayout.profile(960f, 540f, true, InputModality.DIRECTIONAL)
        assertTrue(tv.safeMarginDp >= 960f * 0.05f)
        assertEquals(1.5f, tv.textScale)
        assertTrue(tv.minTargetDp >= 56f)
    }

    @Test
    fun columnsGrowWithWidthAndNeverDropBelowTwo() {
        val widths = listOf(320f, 600f, 900f, 1280f, 1920f, 2560f)
        val columns = widths.map { AdaptiveLayout.profile(it, 900f, false, InputModality.POINTER).columns }
        assertTrue(columns.zipWithNext().all { (a, b) -> b >= a }, "non-decreasing: $columns")
        assertTrue(columns.all { it >= 2 })
        assertTrue(columns.last() > columns.first())
    }

    @Test
    fun touchTargetsAreLargeEnough() {
        assertTrue(AdaptiveLayout.profile(390f, 844f, false, InputModality.TOUCH).minTargetDp >= 48f)
        assertEquals(1f, AdaptiveLayout.profile(390f, 844f, false, InputModality.TOUCH).focusScale)
    }
}

class ArtworkAccentTest {
    private fun image(vararg colors: Pair<Argb, Int>): LongArray = colors.flatMap { (c, n) -> List(n) { c.value } }.toLongArray()

    @Test
    fun dominantVividHueWins() {
        val accent = ArtworkAccent.from(image(Argb(20, 20, 20) to 600, Argb(220, 60, 40) to 300, Argb(40, 90, 200) to 100))
        val hue = Hsl.of(accent).hue
        assertTrue(hue < 25 || hue > 340, "red-orange expected, got $hue ($accent)")
        assertTrue(Palette.onAccent.contrast(accent) >= 4.5)
    }

    @Test
    fun greyArtworkKeepsTheDefault() {
        assertEquals(Palette.accent, ArtworkAccent.from(image(Argb(30, 30, 30) to 500, Argb(200, 200, 200) to 500)))
        assertEquals(Palette.accent, ArtworkAccent.from(LongArray(0)))
    }

    @Test
    fun accentsAreAlwaysReadable() {
        for (hue in 0 until 360 step 15) {
            val accent = ArtworkAccent.from(image(Hsl(hue.toDouble(), 0.9, 0.35).toArgb() to 100))
            assertTrue(Palette.onAccent.contrast(accent) >= 4.5, "hue $hue -> $accent")
        }
    }
}
