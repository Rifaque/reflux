package dev.reflux.design

/**
 * How large the player's controls are, by the room a desktop window gives them. Same design, responsive density: the
 * arrangement never changes, only the size of what is arranged, so a compact window keeps today's sizes and a theater-sized
 * or fullscreen one gets controls, clocks and a timeline that read from the seat.
 *
 * Handhelds and televisions are [BASELINE] whatever their size: their controls are sized by thumbs and distance, and they do
 * not grow with the screen (docs/DESIGN.md, "The player").
 */
enum class PlayerChromeTier { BASELINE, COMPACT, STANDARD, LARGE }

/** One tier's sizes. Dp unless named otherwise. */
data class PlayerChromeSpec(
    val tier: PlayerChromeTier,
    val buttonDp: Float,
    val playButtonDp: Float,
    val playGlyphDp: Float,
    val buttonGapDp: Float,
    val clockSp: Float,
    val titleSp: Float,
    val subtitleSp: Float,
    val trackDp: Float,
    val trackActiveDp: Float,
    /** The thumb's diameter at rest, 0 for none, and while hovered, dragged or focused. */
    val thumbDp: Float,
    val thumbActiveDp: Float,
    /** A pointer's height for the timeline: what is drawn stays a thin line through its middle. */
    val timelineHitDp: Float,
    val clockGapDp: Float,
    val volumeWidthDp: Float,
    val barMaxWidthDp: Float,
    /** Beyond the layout's own safe margin; 0 where that margin is enough. */
    val sideMarginDp: Float,
    val bottomPaddingDp: Float,
    val topPaddingDp: Float,
    val cornerLiftDp: Float,
    val inactiveTrackAlpha: Float,
    /** Whether the track sits on a soft dark edge, so a thin line stays visible over a bright scene. */
    val trackHalo: Boolean,
)

object PlayerChrome {
    const val STANDARD_MIN_WIDTH_DP = 1200f
    const val STANDARD_MIN_HEIGHT_DP = 700f
    const val LARGE_MIN_WIDTH_DP = 1680f
    const val LARGE_MIN_HEIGHT_DP = 945f

    /**
     * The tier for a player [widthDp] x [heightDp] in a window ([windowed]) with a pointer's bar ([barTransport]). By size
     * and not by "is it fullscreen", so a maximized window and a fullscreen one agree and the answer is deterministic.
     */
    fun tier(widthDp: Float, heightDp: Float, windowed: Boolean, barTransport: Boolean): PlayerChromeTier = when {
        !windowed || !barTransport -> PlayerChromeTier.BASELINE
        widthDp >= LARGE_MIN_WIDTH_DP && heightDp >= LARGE_MIN_HEIGHT_DP -> PlayerChromeTier.LARGE
        widthDp >= STANDARD_MIN_WIDTH_DP && heightDp >= STANDARD_MIN_HEIGHT_DP -> PlayerChromeTier.STANDARD
        else -> PlayerChromeTier.COMPACT
    }

    private val BASELINE = PlayerChromeSpec(
        PlayerChromeTier.BASELINE, buttonDp = 40f, playButtonDp = 44f, playGlyphDp = 22f, buttonGapDp = 2f, clockSp = 13f, titleSp = 30f,
        subtitleSp = 14f, trackDp = 4f, trackActiveDp = 6f, thumbDp = 0f, thumbActiveDp = 14f, timelineHitDp = 28f, clockGapDp = 12f,
        volumeWidthDp = 88f, barMaxWidthDp = 1180f, sideMarginDp = 0f, bottomPaddingDp = 20f, topPaddingDp = 18f, cornerLiftDp = 104f,
        inactiveTrackAlpha = 0.26f, trackHalo = false,
    )

    /** A small desktop window: today's sizes, with the timeline made easier to see (a thumb at rest, a stronger track, a soft edge). */
    private val COMPACT = BASELINE.copy(tier = PlayerChromeTier.COMPACT, thumbDp = 10f, inactiveTrackAlpha = 0.40f, trackHalo = true)

    private val STANDARD = COMPACT.copy(
        tier = PlayerChromeTier.STANDARD, buttonDp = 44f, playButtonDp = 48f, playGlyphDp = 24f, buttonGapDp = 4f, clockSp = 14f,
        thumbDp = 12f, thumbActiveDp = 16f, timelineHitDp = 32f, volumeWidthDp = 96f, barMaxWidthDp = 1280f, bottomPaddingDp = 24f,
        topPaddingDp = 20f, cornerLiftDp = 112f,
    )

    private val LARGE = STANDARD.copy(
        tier = PlayerChromeTier.LARGE, buttonDp = 60f, playButtonDp = 68f, playGlyphDp = 36f, buttonGapDp = 8f, clockSp = 17f, titleSp = 40f,
        subtitleSp = 17f, trackDp = 5f, trackActiveDp = 8f, thumbDp = 16f, thumbActiveDp = 22f, timelineHitDp = 44f, clockGapDp = 18f,
        volumeWidthDp = 128f, barMaxWidthDp = 1760f, sideMarginDp = 64f, bottomPaddingDp = 40f, topPaddingDp = 28f, cornerLiftDp = 144f,
    )

    fun spec(tier: PlayerChromeTier): PlayerChromeSpec = when (tier) {
        PlayerChromeTier.BASELINE -> BASELINE
        PlayerChromeTier.COMPACT -> COMPACT
        PlayerChromeTier.STANDARD -> STANDARD
        PlayerChromeTier.LARGE -> LARGE
    }
}
