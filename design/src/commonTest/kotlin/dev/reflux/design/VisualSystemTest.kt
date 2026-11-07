package dev.reflux.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ColorRolesTest {
    private val appearances = listOf(ColorRoles.Reflux, ColorRoles.RefluxHighContrast)

    @Test
    fun contentReadsOnEverySurfaceInEveryAppearance() {
        for (roles in appearances) {
            for (surface in listOf(roles.background, roles.backgroundElevated, roles.surface, roles.surfaceElevated)) {
                assertTrue(roles.contentPrimary.contrast(surface) >= 7.0, "primary on $surface")
                assertTrue(roles.contentSecondary.contrast(surface) >= 4.5, "secondary on $surface")
                assertTrue(roles.contentTertiary.contrast(surface) >= 3.0, "tertiary on $surface")
            }
            assertTrue(roles.onPrimaryAction.contrast(roles.primaryAction) >= 7.0)
            assertTrue(roles.focused.contrast(roles.background) >= 7.0, "focus is visible on the page")
            assertTrue(roles.error.contrast(roles.background) >= 4.5)
            assertTrue(roles.success.contrast(roles.background) >= 4.5)
            assertTrue(roles.warning.contrast(roles.background) >= 4.5)
        }
    }

    @Test
    fun noAppearanceBringsAColorOfItsOwn() {
        for (roles in appearances) for (token in roles.chrome) assertTrue(Hsl.of(token).saturation <= 0.06, "$token has a hue")
    }

    @Test
    fun highContrastStrengthensWithoutChangingTheIdentity() {
        val canonical = ColorRoles.Reflux
        val high = ColorRoles.RefluxHighContrast
        assertTrue(high.contentSecondary.contrast(high.background) > canonical.contentSecondary.contrast(canonical.background))
        assertTrue(high.contentTertiary.contrast(high.background) >= 7.0, "tertiary reads as body text at high contrast")
        assertTrue(high.divider.alpha > canonical.divider.alpha)
        assertTrue(high.stateStrength > canonical.stateStrength)
        // The same page, the same primary action, the same focus: an accessibility answer, not a second theme.
        assertEquals(canonical.background, high.background)
        assertEquals(canonical.primaryAction, high.primaryAction)
        assertEquals(canonical.focused, high.focused)
    }

    @Test
    fun theCanonicalRolesAreThePalette() {
        val roles = ColorRoles.Reflux
        assertEquals(Palette.background, roles.background)
        assertEquals(Palette.textSecondary, roles.contentSecondary)
        assertEquals(Palette.hairline, roles.divider)
    }

    @Test
    fun stateLayersAreOrderedByHowMuchTheySay() {
        val layers = listOf(StateLayers.REST, StateLayers.HOVER, StateLayers.PRESSED, StateLayers.SELECTED, StateLayers.ON)
        assertEquals(layers.sorted(), layers)
        assertTrue(layers.all { it in 0f..0.25f }, "a state layer lights a surface; it never replaces it")
    }
}

class MaterialTierTest {
    private fun tier(
        formFactor: FormFactor = FormFactor.PHONE,
        blurs: Boolean = true,
        reduceTransparency: Boolean = false,
        highContrast: Boolean = false,
        constrained: Boolean = false,
    ) = MaterialTiers.select(MaterialEnvironment(formFactor, blurs, reduceTransparency, highContrast, constrained))

    @Test
    fun capableHandheldsAndDesktopsArePremium() {
        assertEquals(MaterialTier.PREMIUM, tier(FormFactor.PHONE))
        assertEquals(MaterialTier.PREMIUM, tier(FormFactor.TABLET))
        assertEquals(MaterialTier.PREMIUM, tier(FormFactor.DESKTOP))
    }

    @Test
    fun aTelevisionIsRestrained() {
        assertEquals(MaterialTier.ENHANCED, tier(FormFactor.TV))
        assertEquals(MaterialTier.BASIC, tier(FormFactor.TV, blurs = false), "Fire OS 8 (Android 11) cannot blur")
    }

    @Test
    fun legibilityRequestsAndMissingBlurAreBasic() {
        assertEquals(MaterialTier.BASIC, tier(blurs = false))
        assertEquals(MaterialTier.BASIC, tier(reduceTransparency = true))
        assertEquals(MaterialTier.BASIC, tier(FormFactor.DESKTOP, highContrast = true))
        assertEquals(MaterialTier.ENHANCED, tier(constrained = true))
    }

    @Test
    fun basicIsOpaqueAndSeparatedByToneAndEdge() {
        for (role in MaterialRole.entries.filter { it != MaterialRole.BASE }) {
            val basic = Materials.spec(role, MaterialTier.BASIC)
            assertEquals(1f, basic.tintAlpha, "$role")
            assertEquals(0f, basic.blurRadiusDp, "$role")
            assertTrue(basic.highlightAlpha >= 0.10f, "$role keeps an edge")
            assertTrue(basic.tint.contrast(Palette.background) > 1.0, "$role is a tone apart from the page")
        }
        assertTrue(!MaterialTier.BASIC.artworkUnderNavigation && !MaterialTier.BASIC.blurs)
    }

    @Test
    fun theTiersKeepOneHierarchy() {
        for (tier in MaterialTier.entries) {
            assertTrue(Materials.spec(MaterialRole.FOCUSED, tier).highlightAlpha > Materials.spec(MaterialRole.INTERACTIVE, tier).highlightAlpha, "$tier")
            assertTrue(Materials.spec(MaterialRole.MODAL, tier).shadowElevationDp > Materials.spec(MaterialRole.ELEVATED, tier).shadowElevationDp, "$tier")
            assertTrue(Materials.spec(MaterialRole.MODAL, tier).cornerRadiusDp in Radius.family, "$tier uses the radius family")
        }
        assertTrue(Materials.spec(MaterialRole.FLOATING, MaterialTier.PREMIUM).blurRadiusDp > Materials.spec(MaterialRole.FLOATING, MaterialTier.ENHANCED).blurRadiusDp)
    }

    @Test
    fun everyTierStaysReadableOverAnyArtwork() {
        val backdrops = listOf(Materials.BRIGHT_ARTWORK, Argb(0xFFFFFFFFL), Argb(0xFF808080L), Argb(0xFF101010L), Argb(0xFFF0E070L))
        for (tier in MaterialTier.entries) for (role in MaterialRole.entries.filter { it != MaterialRole.BASE }) for (backdrop in backdrops) {
            val alpha = Materials.scrimFor(role, Palette.textSecondary, tier = tier, backdrop = backdrop)
            val surface = Materials.spec(role, tier).tint.withAlpha(alpha).over(backdrop)
            assertTrue(Palette.textSecondary.contrast(surface) >= 4.5, "$tier $role over $backdrop at $alpha")
        }
    }

    @Test
    fun premiumMaterialLetsDarkArtworkThroughAndHoldsBrightArtworkBack() {
        val dark = Materials.scrimFor(MaterialRole.FLOATING, Palette.textSecondary, tier = MaterialTier.PREMIUM, backdrop = Argb(0xFF1A1A1AL))
        val bright = Materials.scrimFor(MaterialRole.FLOATING, Palette.textSecondary, tier = MaterialTier.PREMIUM, backdrop = Argb(0xFFF2F2F2L))
        assertTrue(dark < bright, "dark $dark, bright $bright")
        assertEquals(Materials.spec(MaterialRole.FLOATING, MaterialTier.PREMIUM).tintAlpha, dark, "over dark artwork the material is as light as it is designed")
    }

    @Test
    fun aBackdropIsJudgedByItsBrightParts() {
        val mostlyDark = LongArray(100) { if (it < 80) Argb(20, 20, 20).value else Argb(240, 240, 240).value }
        val backdrop = assertNotNull(Materials.backdropOf(mostlyDark))
        assertTrue(backdrop.luminance > 0.5, "a fifth of bright sky still counts: $backdrop")
        assertEquals(null, Materials.backdropOf(LongArray(0)))
        val grey = Materials.greyOf(Argb(128, 128, 128).luminance)
        assertTrue(kotlin.math.abs(grey.red - 128) <= 1, "$grey")
    }
}

class ScaleTest {
    @Test
    fun typeHasOneHierarchyEverywhere() {
        val sizes = TypeScale.hierarchy.map { it.size }
        assertEquals(sizes.sortedDescending(), sizes)
        for (formFactor in FormFactor.entries) assertTrue(TypeScale.display(formFactor).size > TypeScale.pageTitle.size, "$formFactor")
        // A phone sets its featured title smaller; it does not shrink the rest of the scale.
        assertTrue(TypeScale.display(FormFactor.PHONE).size < TypeScale.display(FormFactor.DESKTOP).size)
        assertTrue(TypeScale.metadata.weight < TypeScale.bodyStrong.weight, "metadata is quieter than what it describes")
        assertTrue(TypeScale.tvLabel.size > TypeScale.navigation.size)
    }

    @Test
    fun spacingIsOneRhythm() {
        assertTrue(Spacing.all.all { it % 2f == 0f }, "a 2 dp grid")
        assertEquals(Spacing.all.sorted(), Spacing.all)
    }

    @Test
    fun radiiAreOneFamily() {
        assertEquals(Radius.family.sorted(), Radius.family)
        assertTrue(Radius.family.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun depthIsNotOnlyShadow() {
        val levels = Elevation.entries
        assertTrue(levels.zipWithNext().all { (a, b) -> b.shadowDp > a.shadowDp && b.highlightAlpha >= a.highlightAlpha && b.tonalLift > a.tonalLift })
        assertTrue(levels.drop(1).all { it.highlightAlpha > 0f && it.tonalLift > 0f }, "every raised level has an edge and a tone, not just a shadow")
    }
}

class MotionTest {
    @Test
    fun motionIsShort() {
        val all = listOf(
            Motion.focusChange, Motion.controlsShow, Motion.controlsHide, Motion.navigation, Motion.posterToDetails, Motion.inputMorph,
            Motion.railExpand, Motion.materialChange, Motion.artworkReveal, Motion.sheetEnter, Motion.sheetExit,
        )
        assertTrue(all.all { it.durationMs in 100..450 }, "${all.map { it.durationMs }}")
        assertTrue(Motion.focusChange.durationMs < Motion.navigation.durationMs, "focus answers faster than a page arrives")
    }

    @Test
    fun televisionMotionIsRestrained() {
        val tv = Motion.profile(FormFactor.TV)
        val desk = Motion.profile(FormFactor.DESKTOP)
        assertEquals(0f, tv.pageTravel, "a television's pages fade in place")
        assertEquals(0f, tv.sheetTravel)
        assertTrue(tv.pageEnter.durationMs < desk.pageEnter.durationMs)
        assertTrue(tv.railExpand.durationMs <= desk.railExpand.durationMs)
        assertTrue(desk.pageTravel > 0f)
    }
}
