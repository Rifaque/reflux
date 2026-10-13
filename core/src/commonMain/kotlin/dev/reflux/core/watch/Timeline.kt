package dev.reflux.core.watch

import dev.reflux.core.identify.ReleaseTokens
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.WatchState
import kotlin.math.abs

/**
 * The timeline a playback position is measured on: which cut of the work, and how long it runs.
 *
 * Two copies of a work share a timeline when a position in one is the same moment in the other. That is a
 * matter of the cut, not of the file: another encode, container, resolution, or source of the same cut is
 * the same timeline. An extended cut is not the theatrical cut's timeline, and neither is a copy whose
 * running time is materially different, whatever it is called.
 */
data class Timeline(
    /** The cut, as [Cuts] names it; null is the work's ordinary cut. */
    val cut: String?,
    val durationMs: Long?,
) {
    companion object {
        fun of(version: MediaVersion): Timeline = Timeline(Cuts.of(version.edition), version.stream.durationMs)

        /** The timeline the position in [state] was measured on. */
        fun of(state: WatchState): Timeline = Timeline(state.cut, state.durationMs)
    }
}

/**
 * Which edition labels name a cut of their own.
 *
 * An edition label is free text ("Extended Edition", a server's "Heat 4K"). Only labels that say the film
 * was *edited* differently name a cut; labels about the transfer, the framing, or the packaging (remastered,
 * IMAX, open matte, anniversary edition) describe the ordinary cut, and so does "theatrical". Anything
 * unrecognized makes no claim, and the running times decide.
 */
object Cuts {
    private val names = listOf(
        "director" to "directors-cut",
        "extended" to "extended",
        "unrated" to "unrated",
        "uncut" to "uncut",
        "special" to "special-edition",
        "ultimate" to "ultimate",
        "final" to "final-cut",
        "rogue" to "rogue-cut",
        "redux" to "redux",
    )

    /** The cut [edition] names, or null for the ordinary cut and for labels that do not name one. */
    fun of(edition: String?): String? {
        val phrase = edition?.let(ReleaseTokens::findEdition)?.value?.lowercase() ?: return null
        return names.firstOrNull { (start, _) -> phrase.startsWith(start) }?.second
    }
}

enum class TimelineMatch {
    /** The same cut, and the running times agree. */
    SAME,

    /** Nothing says they differ, but a running time is missing to confirm it. */
    UNVERIFIED,

    /** Another cut, or running times too far apart to be the same edit. */
    DIFFERENT,
}

object Timelines {
    /**
     * How far two running times of the same cut may be apart: encodes of one cut differ by seconds
     * (trailing logos, frame rounding), a different edit by minutes.
     */
    const val TOLERANCE_RATIO: Double = 0.01
    const val TOLERANCE_FLOOR_MS: Long = 15_000

    fun tolerance(a: Long, b: Long): Long = maxOf(TOLERANCE_FLOOR_MS, (maxOf(a, b) * TOLERANCE_RATIO).toLong())

    /**
     * Whether a position on [a] is the same moment on [b].
     *
     * Two named cuts that differ are different. Otherwise the running times decide, when both are known:
     * a label on only one copy does not make it another cut if both run equally long, and equal labels do
     * not make two copies the same if one runs minutes longer. Without both running times, only the labels
     * are left: equal ones are [TimelineMatch.UNVERIFIED], a cut against the ordinary cut is different.
     */
    fun compare(a: Timeline, b: Timeline): TimelineMatch {
        if (a.cut != null && b.cut != null && a.cut != b.cut) return TimelineMatch.DIFFERENT
        val first = a.durationMs?.takeIf { it > 0 }
        val second = b.durationMs?.takeIf { it > 0 }
        if (first != null && second != null) {
            return if (abs(first - second) <= tolerance(first, second)) TimelineMatch.SAME else TimelineMatch.DIFFERENT
        }
        return if (a.cut == b.cut) TimelineMatch.UNVERIFIED else TimelineMatch.DIFFERENT
    }
}
