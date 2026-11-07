package dev.reflux.design

/**
 * A text role as numbers: size in sp before any platform scale, weight (100–900), line height as a multiple of
 * the size, tracking in em.
 */
data class TypeSpec(val size: Float, val weight: Int, val lineHeight: Float, val tracking: Float = 0f)

/**
 * The semantic type roles (docs/DESIGN.md). Hierarchy comes from size, weight, and tone together, never from
 * scaling everything by the screen: a television sets the whole scale one step up ([LayoutProfile.textScale]) and a
 * phone sets only its featured title smaller ([display]); every other role keeps its size.
 */
object TypeScale {
    /** A featured title (Home's hero, a work's page): large and tight. Its size depends on the window. */
    fun display(formFactor: FormFactor) = TypeSpec(
        when (formFactor) {
            FormFactor.PHONE -> 40f
            FormFactor.TV -> 48f
            else -> 54f
        },
        700, 1.04f, -0.028f,
    )

    /** A page's own title (Sources, Downloads, a listing). */
    val pageTitle = TypeSpec(30f, 600, 1.12f, -0.02f)

    /** A section on a page: a row on Home, "Versions", "Seasons". */
    val sectionTitle = TypeSpec(20f, 600, 1.25f, -0.01f)

    val body = TypeSpec(15f, 400, 1.55f)
    val bodyStrong = TypeSpec(15f, 600, 1.4f)

    /** What describes a title (year, runtime, rating, what is left): a step below it in size, weight, and tone. */
    val metadata = TypeSpec(14f, 500, 1.35f, 0.01f)

    /** A card's title under its artwork. */
    val mediaTitle = TypeSpec(13f, 600, 1.35f)
    val caption = TypeSpec(13f, 400, 1.35f)

    /** Badges, button prompts, menu and notice titles. Sentence case, lightly tracked. */
    val utility = TypeSpec(12f, 600, 1.3f, 0.02f)

    /** A destination's name beside its icon (a sidebar, a television's open rail). */
    val navigation = TypeSpec(15f, 600, 1.3f, -0.005f)

    /** A destination's name beneath its icon (a tablet's rail): small, so the rail stays narrow. */
    val navigationCompact = TypeSpec(12f, 600, 1.25f, 0.01f)

    /** What has focus says its name on a television, read from across the room. */
    val tvLabel = TypeSpec(16f, 600, 1.3f)

    /** Every role but [display], largest first: the hierarchy a test holds. */
    val hierarchy: List<TypeSpec> get() = listOf(pageTitle, sectionTitle, body, metadata, caption, utility)
}

/** The spacing rhythm, in dp: a 4 dp grid with a few larger steps. Layouts use these, not one-off numbers. */
object Spacing {
    const val XXS = 2f
    const val XS = 4f
    const val S = 8f
    const val M = 12f
    const val L = 16f
    const val XL = 24f
    const val XXL = 32f
    const val XXXL = 48f

    val all: List<Float> get() = listOf(XXS, XS, S, M, L, XL, XXL, XXXL)
}

/**
 * The radius family, in dp. Every rounded shape is one of these or a pill; a component does not choose its own.
 * Nested shapes step down one size, so a pill inside a panel is never rounder than the panel's corner allows.
 */
object Radius {
    /** Badges, chips, a hover label. */
    const val SMALL = 8f

    /** Posters, thumbnails, wide cards, navigation items: anything that holds media or is one of a set. */
    const val MEDIUM = 12f

    /** Rows, cards of text, interactive surfaces. */
    const val LARGE = 16f

    /** Functional material: rails, sheets, panels, notices. */
    const val XLARGE = 24f

    val family: List<Float> get() = listOf(SMALL, MEDIUM, LARGE, XLARGE)
}

/**
 * Depth without relying on shadows alone: each level adds a tonal step and a top-lit hairline, and only then a
 * shadow. Over video or a dark page a shadow is invisible; the tone and the hairline still separate.
 */
enum class Elevation(val shadowDp: Float, val highlightAlpha: Float, val tonalLift: Float) {
    /** On the page: no separation of its own. */
    FLAT(0f, 0f, 0f),

    /** Set apart from the page without floating: a card of text, a lifted row. */
    RAISED(2f, 0.06f, 0.03f),

    /** Floats above the page: navigation, bars, notices. */
    FLOATING(12f, 0.09f, 0.05f),

    /** Above everything for a moment: sheets, panels, menus. */
    TRANSIENT(24f, 0.10f, 0.07f),
}
