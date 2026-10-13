package dev.reflux.core.watch

import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaLocation
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.SourceId
import dev.reflux.core.model.StableIds
import dev.reflux.core.model.StreamInfoOrigin
import dev.reflux.core.model.WatchState
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.StreamInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MINUTE = 60_000L

internal fun copy(name: String, minutes: Double?, edition: String? = null, source: String = "local", item: MediaId = MediaId("m")): MediaVersion {
    val location = MediaLocation(SourceId(source), "$name.mkv")
    return MediaVersion(
        id = StableIds.versionId(location),
        itemId = item,
        location = location,
        sizeBytes = 1,
        modifiedAtEpochMs = 0,
        stream = StreamInfo(container = Container.MATROSKA, durationMs = minutes?.let { (it * MINUTE).toLong() }),
        streamOrigin = StreamInfoOrigin.PROBE,
        edition = edition,
    )
}

class HandoffTest {
    private val id = MediaId("m")
    private val local = copy("local-1080p", 120.0)

    /** The viewer stopped [minutes] into [version]. */
    private fun watched(version: MediaVersion, minutes: Long, now: Long = 1_000): WatchState =
        WatchRules.onStop(null, id, minutes * MINUTE, version.stream.durationMs, now, version)

    @Test
    fun onlyLabelsThatSayTheFilmWasEditedDifferentlyNameACut() {
        assertEquals("extended", Cuts.of("Extended Edition"))
        assertEquals("extended", Cuts.of("Extended"))
        assertEquals("directors-cut", Cuts.of("Director's Cut"))
        assertEquals("final-cut", Cuts.of("Final Cut"))
        assertEquals("unrated", Cuts.of("Unrated"))
        // The ordinary cut, however it is presented or packaged.
        for (label in listOf(null, "Theatrical", "Theatrical Cut", "Remastered", "IMAX", "Criterion", "Open Matte", "Anniversary Edition")) {
            assertNull(Cuts.of(label), "$label")
        }
        // A server's name for one of its files says nothing about the cut.
        assertNull(Cuts.of("Heat 4K"))
        assertEquals("extended", Cuts.of("Aliens - Extended Cut 1080p"))
    }

    @Test
    fun aTimelineIsTheCutNotTheFile() {
        fun match(a: MediaVersion, b: MediaVersion) = Timelines.compare(Timeline.of(a), Timeline.of(b))
        // Another encode, container, resolution, or source of the same cut.
        assertEquals(TimelineMatch.SAME, match(local, copy("remote-4k", 120.0, source = "server")))
        assertEquals(TimelineMatch.SAME, match(local, copy("other-encode", 120.06)))
        // A different edit, named or not.
        assertEquals(TimelineMatch.DIFFERENT, match(local, copy("extended", 137.0, "Extended")))
        assertEquals(TimelineMatch.DIFFERENT, match(local, copy("longer", 137.0)))
        assertEquals(TimelineMatch.DIFFERENT, match(copy("a", 150.0, "Extended"), copy("b", 150.0, "Director's Cut")))
        // A label on one copy only does not make it another cut when both run equally long.
        assertEquals(TimelineMatch.SAME, match(copy("tagged", 137.0, "Extended"), copy("untagged", 137.0, source = "server")))
        // Without both running times only the labels are left.
        assertEquals(TimelineMatch.UNVERIFIED, match(local, copy("unprobed", null)))
        assertEquals(TimelineMatch.UNVERIFIED, match(copy("a", null, "Extended"), copy("b", 137.0, "Extended Cut")))
        assertEquals(TimelineMatch.DIFFERENT, match(local, copy("unprobed-extended", null, "Extended")))
    }

    @Test
    fun whatCountsAsTheSameRunningTimeScalesWithTheWork() {
        assertEquals(15_000, Timelines.tolerance(22 * MINUTE, 22 * MINUTE), "short episodes: a quarter of a minute")
        assertEquals(72_000, Timelines.tolerance(120 * MINUTE, 119 * MINUTE), "a film: about a minute")
    }

    @Test
    fun theSameVersionResumesWhereItStopped() {
        val point = Handoff.resume(watched(local, 30), local)
        assertEquals(ResumePoint(30 * MINUTE, ResumeBasis.SAME_VERSION), point)
        assertTrue(point.exact)
    }

    @Test
    fun aFileReplacedInPlaceByAnotherCutIsNotTheSameVersionAnymore() {
        val state = watched(local, 100)
        // The same path, and so the same version identity, now holds a copy seventeen minutes shorter.
        val replaced = local.copy(stream = local.stream.copy(durationMs = 103 * MINUTE))
        assertEquals(local.id, replaced.id)
        assertEquals(ResumePoint(83 * MINUTE, ResumeBasis.ADJUSTED), Handoff.resume(state, replaced))
        assertEquals(TimelineMatch.DIFFERENT, Handoff.relation(state, replaced))
        // A running time the probe has not delivered yet changes nothing.
        assertEquals(ResumeBasis.SAME_VERSION, Handoff.resume(state, local.copy(stream = local.stream.copy(durationMs = null))).basis)
    }

    @Test
    fun anEquivalentCopyResumesAtTheSameMoment() {
        val state = watched(local, 30)
        assertEquals(ResumePoint(30 * MINUTE, ResumeBasis.SAME_TIMELINE), Handoff.resume(state, copy("server-1080p", 120.0, source = "server")))
        // Same cut, another encode: a few seconds of difference are not a different film.
        assertEquals(ResumePoint(30 * MINUTE, ResumeBasis.SAME_TIMELINE), Handoff.resume(state, copy("web-dl", 120.07, source = "server")))
    }

    @Test
    fun aCopyThatCouldNotBeInspectedIsNotHeldAgainstTheViewer() {
        val point = Handoff.resume(watched(local, 30), copy("unprobed", null, source = "share"))
        assertEquals(ResumePoint(30 * MINUTE, ResumeBasis.UNVERIFIED), point)
        assertTrue(point.exact)
    }

    @Test
    fun progressRecordedBeforeTimelinesWereKnownStillResumes() {
        // What an earlier Reflux stored: a position and the running time its player saw, nothing else.
        val legacy = WatchState(id, positionMs = 30 * MINUTE, durationMs = 120 * MINUTE, lastPlayedAtEpochMs = 5)
        assertEquals(ResumePoint(30 * MINUTE, ResumeBasis.SAME_TIMELINE), Handoff.resume(legacy, local))
        assertEquals(ResumeBasis.ADJUSTED, Handoff.resume(legacy, copy("extended", 137.0, "Extended")).basis)
    }

    @Test
    fun aLongerCutIsNotEnteredPastWhatTheViewerHasSeen() {
        val extended = copy("extended", 137.0, "Extended Edition")
        val point = Handoff.resume(watched(local, 100), extended)
        // Whatever was added before this moment only pushes it later: the same position is at or before it.
        assertEquals(ResumePoint(100 * MINUTE, ResumeBasis.ADJUSTED), point)
        assertFalse(point.exact)
    }

    @Test
    fun aShorterCutIsEnteredEarlierByWhatMayBeMissing() {
        val extended = copy("extended", 137.0, "Extended Edition")
        val point = Handoff.resume(watched(extended, 100), local)
        assertEquals(ResumePoint(83 * MINUTE, ResumeBasis.ADJUSTED), point)
        // The same holds for an unnamed difference, such as a sped-up transfer.
        assertEquals(ResumePoint(25 * MINUTE, ResumeBasis.ADJUSTED), Handoff.resume(watched(local, 30), copy("pal", 115.0)))
    }

    @Test
    fun whereNothingSafeIsLeftPlaybackStartsOver() {
        val extended = copy("extended", 137.0, "Extended Edition")
        assertEquals(ResumePoint(null, ResumeBasis.NOT_TRANSFERABLE), Handoff.resume(watched(extended, 10), local))
        // Another cut and nothing to measure the difference by.
        assertEquals(ResumePoint(null, ResumeBasis.NOT_TRANSFERABLE), Handoff.resume(watched(local, 30), copy("unprobed-extended", null, "Extended")))
    }

    @Test
    fun nothingInProgressStartsAtTheBeginning() {
        assertEquals(ResumePoint.START, Handoff.resume(null, local))
        assertEquals(ResumePoint.START, Handoff.resume(watched(local, 115), local), "watched to the end")
        assertNull(Handoff.relation(null, local))
    }

    @Test
    fun progressRemembersTheTimelineItWasMeasuredOn() {
        val extended = copy("extended", 137.0, "Extended Edition")
        val state = WatchRules.onProgress(null, id, 40 * MINUTE, null, nowMs = 7, version = extended)
        assertEquals(extended.id, state.versionId)
        assertEquals("extended", state.cut)
        assertEquals(137 * MINUTE, state.durationMs, "the copy's own running time when the player has none yet")
        assertEquals(7, state.updatedAtEpochMs)

        // A running time describes one copy and is not carried over to another.
        val elsewhere = WatchRules.onProgress(state, id, 41 * MINUTE, null, nowMs = 8, version = copy("unprobed", null, "Extended"))
        assertNull(elsewhere.durationMs)
        assertEquals("extended", elsewhere.cut)
    }

    @Test
    fun theFirstMomentsOfAnotherCutDoNotEraseWhereTheViewerWas() {
        val extended = copy("extended", 137.0, "Extended Edition")
        val state = watched(local, 100)
        val glanced = WatchRules.onStop(state, id, 20_000, extended.stream.durationMs, nowMs = 2_000, version = extended)
        assertEquals(100 * MINUTE, glanced.positionMs)
        assertEquals(local.id, glanced.versionId)
        assertEquals(2_000, glanced.lastPlayedAtEpochMs)

        // Watching it for real moves the viewer onto that cut.
        val moved = WatchRules.onStop(glanced, id, 104 * MINUTE, extended.stream.durationMs, nowMs = 3_000, version = extended)
        assertEquals(104 * MINUTE, moved.positionMs)
        assertEquals("extended", moved.cut)

        // On the same timeline, starting over is starting over.
        assertEquals(0, WatchRules.onStop(state, id, 20_000, null, nowMs = 2_000, version = copy("server", 120.0, source = "server")).positionMs)
    }

    @Test
    fun finishingAnyCopyCompletesTheWork() {
        val extended = copy("extended", 137.0, "Extended Edition")
        val done = WatchRules.onStop(watched(local, 100), id, 135 * MINUTE, 137 * MINUTE, nowMs = 9, version = extended)
        assertTrue(done.completed)
        assertEquals(1, done.playCount)
        assertEquals(ResumePoint.START, Handoff.resume(done, local))
    }

    @Test
    fun markingIsAChangeWithATime() {
        val watched = WatchRules.markWatched(null, id, nowMs = 5)
        assertEquals(5, watched.updatedAtEpochMs)
        val unwatched = WatchRules.markUnwatched(watched, id, nowMs = 9)
        assertEquals(9, WatchRules.changedAt(unwatched))
        assertEquals(5, unwatched.lastPlayedAtEpochMs, "un-marking is not playing")
    }

    @Test
    fun twoRecordsOfOneWorkBecomeTheMoreRecentOne() {
        val earlier = WatchRules.markWatched(null, MediaId("a"), nowMs = 10).copy(playCount = 3)
        val later = WatchRules.onStop(null, MediaId("b"), 30 * MINUTE, 120 * MINUTE, nowMs = 20, version = local)
        val merged = WatchRules.merge(id, earlier, later)
        assertEquals(id, merged.itemId)
        assertEquals(30 * MINUTE, merged.positionMs)
        assertFalse(merged.completed)
        assertEquals(3, merged.playCount)
        assertEquals(merged, WatchRules.merge(id, later, earlier), "whichever is named first")
    }
}
