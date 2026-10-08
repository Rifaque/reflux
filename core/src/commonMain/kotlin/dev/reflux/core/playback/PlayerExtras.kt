package dev.reflux.core.playback

/** A chapter marker reported by the engine. */
data class Chapter(val title: String?, val startMs: Long)

/** A part of a video the user can skip with one action. */
data class SkipSegment(val kind: Kind, val startMs: Long, val endMs: Long) {
    enum class Kind { INTRO, RECAP, CREDITS }

    operator fun contains(positionMs: Long): Boolean = positionMs in startMs until endMs
}

/**
 * Finds skippable segments from chapter names ("Intro", "Opening", "OP", "Recap", "Credits", "ED", ...).
 * Deterministic and conservative: only clearly named chapters count, so nothing is ever skipped silently —
 * the player offers a "Skip" action while the position is inside a segment.
 */
object SkipSegments {
    private val intro = Regex("""^(?:intro(?:duction)?|opening(?: (?:credits|theme|song))?|op\d*|title sequence|main title)$""", RegexOption.IGNORE_CASE)
    private val recap = Regex("""^(?:recap|previously(?: on.*)?|prologue recap)$""", RegexOption.IGNORE_CASE)
    private val credits = Regex("""^(?:credits|end(?:ing)?(?: credits| theme| song)?|ed\d*|outro|closing credits)$""", RegexOption.IGNORE_CASE)

    fun from(chapters: List<Chapter>, durationMs: Long?): List<SkipSegment> {
        val sorted = chapters.sortedBy { it.startMs }
        return sorted.mapIndexedNotNull { index, chapter ->
            val name = chapter.title?.trim().orEmpty()
            val kind = when {
                intro.matches(name) -> SkipSegment.Kind.INTRO
                recap.matches(name) -> SkipSegment.Kind.RECAP
                credits.matches(name) -> SkipSegment.Kind.CREDITS
                else -> return@mapIndexedNotNull null
            }
            val end = sorted.getOrNull(index + 1)?.startMs ?: durationMs ?: return@mapIndexedNotNull null
            if (end <= chapter.startMs) null else SkipSegment(kind, chapter.startMs, end)
        }
    }

    fun at(segments: List<SkipSegment>, positionMs: Long): SkipSegment? = segments.firstOrNull { positionMs in it }
}

/**
 * Post-play for episodes: count down into the next episode, but stop after several episodes in a row without
 * any user interaction ("Are you still watching?"), so a sleeping viewer does not binge a whole season.
 */
class AutoplayPolicy(
    val countdownMs: Long = 10_000,
    private val maxUnattended: Int = 3,
) {
    private var unattended = 0

    /** Any user input during playback. */
    fun onInteraction() {
        unattended = 0
    }

    sealed interface Decision {
        /** Start the next episode after [countdownMs] unless the user cancels. */
        data class Countdown(val countdownMs: Long) : Decision

        /** Ask before continuing. */
        data object AskStillWatching : Decision

        /** Nothing to continue with. */
        data object Stop : Decision
    }

    /** Called when an item ends. [hasNext] is whether Next Up has an episode. */
    fun onEnded(hasNext: Boolean): Decision {
        if (!hasNext) return Decision.Stop
        unattended++
        return if (unattended > maxUnattended) Decision.AskStillWatching else Decision.Countdown(countdownMs)
    }

    /** The user confirmed they are still watching. */
    fun onStillWatching() {
        unattended = 0
    }
}
