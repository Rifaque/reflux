package dev.reflux.design

/** A cubic Bézier easing curve (CSS / Compose convention). */
data class Easing(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

data class MotionSpec(val durationMs: Int, val easing: Easing)

/**
 * The motion vocabulary (docs/DESIGN.md, "Motion"). Motion says what changed and where it went; durations are
 * short enough never to slow navigation, and nothing moves on its own. With the system's reduce-motion setting,
 * every one of these takes no time, and the end state is drawn as it would have been.
 */
object Motion {
    val standard = Easing(0.2f, 0f, 0f, 1f)
    val emphasized = Easing(0.05f, 0.7f, 0.1f, 1f)
    val exit = Easing(0.3f, 0f, 1f, 1f)

    /** Focus moving, a hover lighting a control. */
    val focusChange = MotionSpec(140, standard)
    val controlsShow = MotionSpec(180, standard)
    val controlsHide = MotionSpec(240, exit)

    /** A page arriving in place of another. */
    val navigation = MotionSpec(320, emphasized)
    val posterToDetails = MotionSpec(420, emphasized)

    /** The signature input-modality morph: layout density, focus visuals, and prompts change together. */
    val inputMorph = MotionSpec(360, emphasized)

    /** A rail widening to show its names, and narrowing again. */
    val railExpand = MotionSpec(220, standard)

    /** Material changing strength or tone (the navigation frosting as the page moves under it). */
    val materialChange = MotionSpec(200, standard)

    /** Artwork arriving over its placeholder, or one hero giving way to another. */
    val artworkReveal = MotionSpec(240, standard)

    /** A sheet or a panel arriving, and leaving. */
    val sheetEnter = MotionSpec(260, emphasized)
    val sheetExit = MotionSpec(200, exit)

    /** What is playing moving between the whole window and its dock. */
    val playerDock = MotionSpec(280, emphasized)

    /** Where the viewer is, travelling from one destination to another in the navigation. */
    val selectionTravel = MotionSpec(260, emphasized)

    /** Player controls hide after this much inactivity while playing. */
    const val CONTROLS_AUTO_HIDE_MS: Long = 3_500

    /** How motion is applied on [formFactor]. */
    fun profile(formFactor: FormFactor): MotionProfile = if (formFactor == FormFactor.TV) TELEVISION else DEFAULT

    private val DEFAULT = MotionProfile(pageEnter = navigation, pageTravel = 1f / 40f, railExpand = railExpand, sheetTravel = 1f / 8f)

    /**
     * Restrained at ten feet: a page fades in without travelling, faster; a rail widens quickly; a sheet fades.
     * Every press of a remote is answered at once, and nothing slides across a large screen.
     */
    private val TELEVISION = MotionProfile(pageEnter = MotionSpec(240, standard), pageTravel = 0f, railExpand = MotionSpec(180, standard), sheetTravel = 0f)
}

/**
 * Motion as a form factor uses it. [pageTravel] and [sheetTravel] are how far an arriving page or sheet moves, as
 * a fraction of its height; 0 is a fade in place.
 */
data class MotionProfile(val pageEnter: MotionSpec, val pageTravel: Float, val railExpand: MotionSpec, val sheetTravel: Float)
