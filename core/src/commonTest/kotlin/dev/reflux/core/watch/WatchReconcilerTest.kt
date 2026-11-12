package dev.reflux.core.watch

import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.WatchState
import dev.reflux.core.source.SourceUserState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MINUTE = 60_000L

class WatchReconcilerTest {
    private val id = MediaId("m")
    private val local = copy("local", 120.0)
    private val onServer = copy("server", 120.0, source = "server")

    private fun source(played: Boolean = false, minutes: Long = 0, at: Long? = null) =
        SourceUserState(played, minutes * MINUTE, if (played) 1 else 0, at, favorite = false)

    private fun baselineOf(state: SourceUserState, reconciledAt: Long) =
        SyncBaseline(state.played, state.positionMs, state.lastPlayedAtEpochMs, reconciledAt)

    /** The viewer stopped [minutes] into [version] at time [at]. */
    private fun watched(version: MediaVersion, minutes: Long, at: Long): WatchState =
        WatchRules.onStop(null, id, minutes * MINUTE, version.stream.durationMs, at, version)

    private fun reconcile(mine: WatchState?, theirs: SourceUserState, baseline: SyncBaseline?, now: Long = 1_000, version: MediaVersion? = onServer) =
        WatchReconciler.reconcile(id, mine, theirs, baseline, version, now)

    @Test
    fun aFirstMeetingAdoptsWhatOnlyTheSourceKnows() {
        val outcome = reconcile(null, source(minutes = 40, at = 500), baseline = null)
        assertEquals(SyncDecision.SOURCE_ADOPTED, outcome.decision)
        val state = assertNotNull(outcome.state)
        assertEquals(40 * MINUTE, state.positionMs)
        // The position is a time on the source's copy, and the state says so.
        assertEquals(onServer.id, state.versionId)
        assertEquals(120 * MINUTE, state.durationMs)
        assertEquals(500, state.updatedAtEpochMs, "as old as what the user did, not as the moment Reflux heard of it")
        assertNull(outcome.update)

        val played = reconcile(null, source(played = true), baseline = null)
        assertTrue(assertNotNull(played.state).completed, "a played flag needs no date when Reflux knows nothing")
        assertEquals(SyncDecision.IN_AGREEMENT, reconcile(null, source(), baseline = null).decision)
    }

    @Test
    fun aFirstMeetingKeepsTheNewerOfTwoHistoriesAndTellsTheSourceNothing() {
        val mine = watched(local, 30, at = 800)
        val older = reconcile(mine, source(minutes = 10, at = 500), baseline = null)
        assertEquals(SyncDecision.REFLUX_KEPT, older.decision)
        assertNull(older.state)
        assertNull(older.update, "connecting a server does not rewrite what the server knows")

        val newer = reconcile(mine, source(minutes = 50, at = 900), baseline = null)
        assertEquals(50 * MINUTE, newer.state?.positionMs)

        val undated = reconcile(mine, source(played = true), baseline = null)
        assertEquals(SyncDecision.REFLUX_KEPT, undated.decision, "an undated record cannot show that it is the later one")
    }

    @Test
    fun whatTheSourceAlreadySaidNeverOverwritesWhatHappenedSince() {
        // The source said "10 minutes" at the last comparison and still says it; the viewer went on locally.
        val theirs = source(minutes = 10, at = 500)
        val mine = watched(local, 45, at = 2_000)
        val outcome = reconcile(mine, theirs, baselineOf(theirs, reconciledAt = 1_000), now = 3_000)
        assertEquals(SyncDecision.REFLUX_KEPT, outcome.decision)
        assertNull(outcome.state)
        // ...and the source is behind: it is told where the viewer is, on its own copy of the same cut.
        assertEquals(SourceUpdate(played = null, positionMs = 45 * MINUTE), outcome.update)
        assertEquals(1_000, outcome.baseline.reconciledAtEpochMs, "undelivered: the next comparison tries again")
    }

    @Test
    fun aSourceThatFellBehindAndWasNeverToldIsStillNotBelieved() {
        // Reflux's state is older than the last comparison, the source still says what it said: nothing is news.
        val theirs = source(minutes = 10, at = 500)
        val mine = watched(local, 45, at = 900)
        val outcome = reconcile(mine, theirs, baselineOf(theirs, reconciledAt = 1_000), now = 3_000)
        assertEquals(SyncDecision.REFLUX_KEPT, outcome.decision)
        assertNull(outcome.update)
        assertEquals(3_000, outcome.baseline.reconciledAtEpochMs)
    }

    @Test
    fun newsFromTheSourceIsAdopted() {
        val before = source(minutes = 10, at = 500)
        val mine = watched(onServer, 10, at = 500)
        // Watched further on one of the server's own clients.
        val further = reconcile(mine, source(minutes = 70, at = 2_000), baselineOf(before, reconciledAt = 1_000), now = 3_000)
        assertEquals(SyncDecision.SOURCE_ADOPTED, further.decision)
        assertEquals(70 * MINUTE, further.state?.positionMs)
        // Finished there.
        val finished = reconcile(mine, source(played = true, at = 2_000), baselineOf(before, reconciledAt = 1_000), now = 3_000)
        assertTrue(assertNotNull(finished.state).completed)
        assertEquals(0, finished.state?.positionMs)
        // Started over there: an earlier position with a later date is where the viewer is.
        val restarted = reconcile(watched(onServer, 70, at = 600), source(minutes = 5, at = 2_000), baselineOf(source(minutes = 70, at = 600), 1_000), now = 3_000)
        assertEquals(5 * MINUTE, restarted.state?.positionMs)
    }

    @Test
    fun aSourceFallingSilentDoesNotEraseHistory() {
        // The server's record for the work is empty where it used to say "played": un-marked there, reset,
        // or lost in a rebuild. There is no telling which, and nothing is erased on the strength of it.
        val played = source(played = true, at = 400)
        val mine = WatchRules.markWatched(null, id, 400)
        val outcome = reconcile(mine, source(), baselineOf(played, reconciledAt = 1_000), now = 3_000)
        assertEquals(SyncDecision.REFLUX_KEPT, outcome.decision)
        assertNull(outcome.state)
        assertNull(outcome.update, "and the server is not corrected either: nothing changed in Reflux")

        val inProgress = watched(local, 45, at = 500)
        val forgotten = reconcile(inProgress, source(), baselineOf(source(minutes = 45, at = 500), 1_000), now = 3_000)
        assertEquals(SyncDecision.REFLUX_KEPT, forgotten.decision)
        assertNull(forgotten.update)
    }

    @Test
    fun whenBothMovedTheLaterOneIsWhereTheViewerIs() {
        val before = source(minutes = 10, at = 500)
        val baseline = baselineOf(before, reconciledAt = 1_000)
        val mine = watched(local, 45, at = 2_000)

        val sourceLater = reconcile(mine, source(minutes = 70, at = 2_500), baseline, now = 3_000)
        assertEquals(70 * MINUTE, sourceLater.state?.positionMs)

        val refluxLater = reconcile(mine, source(minutes = 20, at = 1_500), baseline, now = 3_000)
        assertEquals(SyncDecision.REFLUX_KEPT, refluxLater.decision)
        assertEquals(SourceUpdate(null, 45 * MINUTE), refluxLater.update)
    }

    @Test
    fun agreementIsNoticedWhateverTheClocksSay() {
        // Reflux reported the position itself while playing the server's copy; the server stamped it with its own clock.
        val mine = watched(onServer, 45, at = 2_000)
        val outcome = reconcile(mine, source(minutes = 45, at = 9_999_999), baselineOf(source(minutes = 10, at = 500), 1_000), now = 3_000)
        assertEquals(SyncDecision.IN_AGREEMENT, outcome.decision)
        assertNull(outcome.state)
        assertNull(outcome.update)
        assertEquals(3_000, outcome.baseline.reconciledAtEpochMs)

        // Positions too early to resume are the beginning on both sides.
        val barelyStarted = WatchRules.onStop(null, id, 20_000, 120 * MINUTE, 2_000, onServer)
        assertEquals(SyncDecision.IN_AGREEMENT, reconcile(barelyStarted, SourceUserState(false, 21_000, 0, 2_100, false), null).decision)
    }

    @Test
    fun aMarkMadeWhileTheSourceWasAwayReachesItLater() {
        val played = source(played = true, at = 400)
        val unmarked = WatchRules.markUnwatched(WatchRules.markWatched(null, id, 400), id, nowMs = 2_000)
        val outcome = reconcile(unmarked, played, baselineOf(played, reconciledAt = 1_000), now = 3_000)
        assertEquals(SyncDecision.REFLUX_KEPT, outcome.decision, "the server's old 'played' does not come back")
        assertEquals(SourceUpdate(played = false, positionMs = null), outcome.update)

        val finished = WatchRules.onStop(null, id, 118 * MINUTE, 120 * MINUTE, 2_000, local)
        val unplayed = source(minutes = 10, at = 500)
        assertEquals(SourceUpdate(played = true, positionMs = null), reconcile(finished, unplayed, baselineOf(unplayed, 1_000), now = 3_000).update)
    }

    @Test
    fun aPositionIsNotSentToACopyOnAnotherTimeline() {
        val extendedOnServer = copy("server-extended", 137.0, "Extended Edition", source = "server")
        val theirs = source(minutes = 10, at = 500)
        val mine = watched(local, 45, at = 2_000)
        val outcome = reconcile(mine, theirs, baselineOf(theirs, reconciledAt = 1_000), now = 3_000, version = extendedOnServer)
        assertEquals(SyncDecision.REFLUX_KEPT, outcome.decision)
        assertNull(outcome.update, "45 minutes into the theatrical cut is not 45 minutes into the extended one")

        // Having watched it is true of the work, whatever the cut.
        val finished = WatchRules.onStop(null, id, 118 * MINUTE, 120 * MINUTE, 2_000, local)
        assertEquals(SourceUpdate(true, null), reconcile(finished, theirs, baselineOf(theirs, 1_000), now = 3_000, version = extendedOnServer).update)
    }

    // Clearing local watch history (docs/WATCH_STATE.md, "Clearing local watch history") ------------------------

    private fun afterClear(theirs: SourceUserState, baseline: SyncBaseline?, clearedAt: Long, now: Long = 5_000, mine: WatchState? = null) =
        WatchReconciler.reconcile(id, mine, theirs, baseline, onServer, now, clearedAtEpochMs = clearedAt)

    @Test
    fun aClearedHistoryIsNotBroughtBackByTheSourcesUnchangedRecord() {
        val theirs = source(minutes = 40, at = 500)
        // Compared before: the source has not changed since.
        val known = afterClear(theirs, baselineOf(theirs, reconciledAt = 1_000), clearedAt = 2_000)
        assertNull(known.state, "nothing is adopted")
        assertNull(known.update, "and nothing is sent: clearing is local")
        // Never compared (the source was away when the viewer cleared, or is newly signed in to): still not news.
        val firstMeeting = afterClear(theirs, baseline = null, clearedAt = 2_000)
        assertNull(firstMeeting.state)
        assertNull(firstMeeting.update)
        // A played flag the source cannot date is not news after a clearing either.
        val undated = afterClear(source(played = true), baseline = null, clearedAt = 2_000)
        assertNull(undated.state)
        assertNull(undated.update)
    }

    @Test
    fun whatHappensOnTheSourceAfterAClearingIsNewsAsUsual() {
        val before = source(minutes = 40, at = 500)
        val later = source(minutes = 70, at = 3_000)
        val outcome = afterClear(later, baselineOf(before, reconciledAt = 1_000), clearedAt = 2_000)
        assertEquals(SyncDecision.SOURCE_ADOPTED, outcome.decision, "watched elsewhere after the clearing")
        assertEquals(70 * MINUTE, outcome.state?.positionMs)
        assertNull(outcome.update)
    }

    @Test
    fun playingAgainAfterAClearingIsToldToTheSourceAsBefore() {
        val theirs = source(minutes = 40, at = 500)
        val mine = watched(local, 10, at = 3_000)
        val outcome = afterClear(theirs, baselineOf(theirs, reconciledAt = 1_000), clearedAt = 2_000, mine = mine)
        assertEquals(SyncDecision.REFLUX_KEPT, outcome.decision)
        assertEquals(SourceUpdate(played = null, positionMs = 10 * MINUTE), outcome.update, "new viewing is synchronized as usual")
    }
}

