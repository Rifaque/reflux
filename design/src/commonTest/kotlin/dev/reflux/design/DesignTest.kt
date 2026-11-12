package dev.reflux.design

import dev.reflux.core.input.InputModality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColorTest {
    @Test
    fun contrastFollowsWcag() {
        assertEquals(21.0, Argb(0xFFFFFFFFL).contrast(Argb(0xFF000000L)), 0.01)
        assertEquals(1.0, Palette.textPrimary.contrast(Palette.textPrimary), 0.0001)
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
        assertTrue(Palette.onPrimaryAction.contrast(Palette.primaryAction) >= 7.0)
    }

    @Test
    fun theInterfaceBringsNoColorOfItsOwn() {
        // Artwork is the color on screen; a hue creeping into a chrome token is a regression of the design.
        for (token in Palette.chrome) assertTrue(Hsl.of(token).saturation <= 0.06, "$token has a hue")
    }

    @Test
    fun progressReadsOnTheBase() {
        assertTrue(Palette.progress.contrast(Palette.background) >= 7.0)
        assertTrue(Palette.focusRing.contrast(Palette.surfaceRaised) >= 7.0)
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
        // A television's canvas is already scaled for the distance: type is a step up, not half again,
        // and five titles fit across beside the navigation rail.
        assertEquals(1.1f, tv.textScale)
        assertEquals(5, tv.columns)
        assertTrue(tv.minTargetDp >= 44f)
        assertTrue(tv.showsFocus && tv.focusScale > 1f)
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
    fun theNavigationSitsWhereEachWindowHasRoomForIt() {
        fun navigation(width: Float, height: Float, television: Boolean = false, modality: InputModality = InputModality.TOUCH) =
            AdaptiveLayout.profile(width, height, television, modality).navigation
        assertEquals(NavigationStyle.BOTTOM_BAR, navigation(412f, 915f), "a phone held upright: along the bottom")
        assertEquals(NavigationStyle.COMPACT_RAIL, navigation(915f, 412f), "on its side: a narrow rail, not the bar stretched across")
        assertEquals(NavigationStyle.RAIL, navigation(1180f, 820f), "a tablet: a rail")
        assertEquals(NavigationStyle.RAIL, navigation(820f, 1180f), "upright too: a tablet is not a large phone")
        assertEquals(NavigationStyle.SIDEBAR, navigation(1440f, 900f, modality = InputModality.POINTER))
        assertEquals(NavigationStyle.TV_RAIL, navigation(960f, 540f, television = true, modality = InputModality.DIRECTIONAL))
        // Picking up a controller changes density, never where the navigation is.
        assertEquals(navigation(1440f, 900f, modality = InputModality.POINTER), navigation(1440f, 900f, modality = InputModality.DIRECTIONAL))
        assertTrue(NavigationStyle.entries.filter { it.vertical }.all { it.widthDp > 0f })
        assertEquals(0f, NavigationStyle.BOTTOM_BAR.widthDp)
    }

    @Test
    fun everyWindowButATelevisionHasADockForWhatIsPlaying() {
        fun dock(width: Float, height: Float, television: Boolean = false) = AdaptiveLayout.profile(width, height, television, InputModality.TOUCH).dock
        // A phone has a row, upright and on its side: its screen is too short or too narrow for a picture above words.
        assertEquals(DockStyle.BAR, dock(412f, 915f)?.style)
        assertEquals(DockStyle.BAR, dock(915f, 412f)?.style)
        // A tablet and a desktop have a card in the page's corner, the picture above what is playing.
        assertEquals(DockStyle.CARD, dock(800f, 1280f)?.style)
        assertEquals(DockStyle.CARD, dock(1440f, 900f)?.style)
        // A television's player is the whole screen.
        // A tablet window with no room for a card beside its page (two windows side by side) has the row.
        assertEquals(DockStyle.BAR, dock(640f, 800f)?.style)
        assertNull(dock(960f, 540f, television = true))
        for ((width, height) in listOf(320f to 568f, 412f to 915f, 915f to 412f, 640f to 800f, 800f to 1280f, 1200f to 600f, 1920f to 1080f)) {
            val layout = AdaptiveLayout.profile(width, height, false, InputModality.TOUCH)
            val spec = assertNotNull(layout.dock)
            assertEquals(16f / 9f, spec.videoWidthDp / spec.videoHeightDp, 0.001f, "the picture's box is the shape of a picture")
            assertTrue(spec.videoWidthDp + 2 * MiniPlayerDock.PADDING_DP <= spec.widthDp, "the picture fits in its dock at ${width}x$height")
            // It leaves the page most of the window: beside a rail or sidebar it is a corner, on a phone a row.
            assertTrue(spec.heightDp <= height * 0.45f, "the dock is ${spec.heightDp} of $height high at ${width}x$height")
            if (spec.style == DockStyle.CARD) assertTrue(spec.widthDp <= (width - layout.navigation.widthDp) * 0.5f, "a card leaves the page its width at ${width}x$height")
        }
    }

    @Test
    fun touchTargetsAreLargeEnough() {
        assertTrue(AdaptiveLayout.profile(390f, 844f, false, InputModality.TOUCH).minTargetDp >= 48f)
        assertEquals(1f, AdaptiveLayout.profile(390f, 844f, false, InputModality.TOUCH).focusScale)
    }
}

class ArtworkAtmosphereTest {
    private fun image(vararg colors: Pair<Argb, Int>): LongArray = colors.flatMap { (c, n) -> List(n) { c.value } }.toLongArray()

    @Test
    fun dominantVividHueWins() {
        val atmosphere = assertNotNull(ArtworkAtmosphere.from(image(Argb(20, 20, 20) to 600, Argb(220, 60, 40) to 300, Argb(40, 90, 200) to 100)))
        val hue = Hsl.of(atmosphere).hue
        assertTrue(hue < 25 || hue > 340, "red-orange expected, got $hue ($atmosphere)")
    }

    @Test
    fun greyArtworkHasNoAtmosphere() {
        assertNull(ArtworkAtmosphere.from(image(Argb(30, 30, 30) to 500, Argb(200, 200, 200) to 500)))
        assertNull(ArtworkAtmosphere.from(LongArray(0)))
        assertNull(ArtworkAtmosphere.dominantHue(image(Argb(30, 30, 30) to 500, Argb(200, 200, 200) to 500)))
    }

    @Test
    fun theAtmosphereIsMadeFromTheDominantHue() {
        // A poster-only work's generated backdrop takes this hue, so it has to agree with the page's wash.
        val yellow = image(Argb(25, 25, 25) to 400, Argb(235, 200, 60) to 400, Argb(60, 70, 200) to 60)
        val hue = assertNotNull(ArtworkAtmosphere.dominantHue(yellow))
        assertTrue(hue in 35.0..60.0, "yellow expected, got $hue")
        assertEquals(ArtworkAtmosphere.ofHue(hue), ArtworkAtmosphere.from(yellow))
    }

    @Test
    fun atmosphereIsAlwaysABackground() {
        // Deep and muted whatever the artwork: primary text reads on it at 7:1, and it never outshines the chrome.
        for (hue in 0 until 360 step 15) {
            val atmosphere = assertNotNull(ArtworkAtmosphere.from(image(Hsl(hue.toDouble(), 0.9, 0.5).toArgb() to 100)))
            assertTrue(Palette.textPrimary.contrast(atmosphere) >= 7.0, "hue $hue -> $atmosphere")
            assertTrue(Hsl.of(atmosphere).saturation <= 0.5, "hue $hue -> $atmosphere")
        }
        assertEquals(ArtworkAtmosphere.ofHue(30.0), ArtworkAtmosphere.ofHue(390.0))
    }
}
