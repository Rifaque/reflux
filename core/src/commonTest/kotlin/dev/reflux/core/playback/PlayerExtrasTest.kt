package dev.reflux.core.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SkipSegmentsTest {
    private val chapters = listOf(
        Chapter("Previously on", 0),
        Chapter("Opening", 60_000),
        Chapter("Chapter 1", 150_000),
        Chapter("Chapter 2", 900_000),
        Chapter("End Credits", 2_520_000),
    )

    @Test
    fun namedChaptersBecomeSegments() {
        val segments = SkipSegments.from(chapters, durationMs = 2_640_000)
        assertEquals(
            listOf(
                SkipSegment(SkipSegment.Kind.RECAP, 0, 60_000),
                SkipSegment(SkipSegment.Kind.INTRO, 60_000, 150_000),
                SkipSegment(SkipSegment.Kind.CREDITS, 2_520_000, 2_640_000),
            ),
            segments,
        )
        assertEquals(SkipSegment.Kind.INTRO, SkipSegments.at(segments, 100_000)?.kind)
        assertNull(SkipSegments.at(segments, 150_000))
    }

    @Test
    fun animeStyleAndUnknownNames() {
        val segments = SkipSegments.from(listOf(Chapter("OP", 90_000), Chapter("Part A", 180_000), Chapter("ED", 1_300_000)), 1_420_000)
        assertEquals(listOf(SkipSegment.Kind.INTRO, SkipSegment.Kind.CREDITS), segments.map { it.kind })
        assertEquals(emptyList(), SkipSegments.from(listOf(Chapter("Chapter 1", 0), Chapter(null, 10)), 100))
        assertEquals(emptyList(), SkipSegments.from(listOf(Chapter("Credits", 0)), durationMs = null), "no end known")
    }
}

class AutoplayPolicyTest {
    @Test
    fun countsDownThenAsksAfterUnattendedEpisodes() {
        val policy = AutoplayPolicy(maxUnattended = 2)
        assertIs<AutoplayPolicy.Decision.Countdown>(policy.onEnded(hasNext = true))
        assertIs<AutoplayPolicy.Decision.Countdown>(policy.onEnded(hasNext = true))
        assertEquals(AutoplayPolicy.Decision.AskStillWatching, policy.onEnded(hasNext = true))
        policy.onStillWatching()
        assertIs<AutoplayPolicy.Decision.Countdown>(policy.onEnded(hasNext = true))
    }

    @Test
    fun interactionResetsAndTheEndStops() {
        val policy = AutoplayPolicy(maxUnattended = 1)
        policy.onEnded(hasNext = true)
        policy.onInteraction()
        assertIs<AutoplayPolicy.Decision.Countdown>(policy.onEnded(hasNext = true))
        assertEquals(AutoplayPolicy.Decision.Stop, policy.onEnded(hasNext = false))
    }
}
