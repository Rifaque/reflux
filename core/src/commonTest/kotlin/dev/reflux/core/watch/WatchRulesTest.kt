package dev.reflux.core.watch

import dev.reflux.core.model.Episode
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.WatchState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchRulesTest {
    private val id = MediaId("m")
    private val hour = 3_600_000L

    @Test
    fun earlyPositionsAreNotResumable() {
        val state = WatchRules.onStop(null, id, 30_000, 2 * hour, nowMs = 1)
        assertEquals(0, state.positionMs)
        assertNull(WatchRules.resumePosition(state))
        assertFalse(state.completed)
    }

    @Test
    fun midwayPositionsResume() {
        val state = WatchRules.onStop(null, id, hour, 2 * hour, nowMs = 1)
        assertEquals(hour, WatchRules.resumePosition(state))
        assertTrue(state.inProgress)
    }

    @Test
    fun passingTheCreditsThresholdCompletes() {
        val state = WatchRules.onStop(null, id, (1.85 * hour).toLong(), 2 * hour, nowMs = 1)
        assertTrue(state.completed)
        assertEquals(1, state.playCount)
        assertEquals(0, state.positionMs)
    }

    @Test
    fun progressReportsNeverInflatePlayCount() {
        var state: WatchState? = null
        repeat(5) { state = WatchRules.onProgress(state, id, (1.95 * hour).toLong(), 2 * hour, nowMs = it.toLong()) }
        assertEquals(0, state!!.playCount)
        assertFalse(state!!.completed)
        // Crashed near the end: do not offer to resume the credits.
        assertNull(WatchRules.resumePosition(state))
    }

    @Test
    fun markWatchedAndUnwatched() {
        val watched = WatchRules.markWatched(null, id, nowMs = 5)
        assertTrue(watched.completed)
        assertEquals(1, watched.playCount)
        assertFalse(WatchRules.markUnwatched(watched, id).completed)
    }

    private fun episode(season: Int, number: Int) = Episode(
        id = MediaId("s${season}e$number"),
        showId = MediaId("show"),
        seasonId = MediaId("s$season"),
        seasonNumber = season,
        episodeNumber = number,
    )

    @Test
    fun nextUpFollowsTheMostRecentEpisode() {
        val episodes = listOf(episode(1, 1), episode(1, 2), episode(2, 1), episode(0, 1))
        val states = mapOf(
            MediaId("s1e1") to WatchState(MediaId("s1e1"), completed = true, lastPlayedAtEpochMs = 10),
            MediaId("s1e2") to WatchState(MediaId("s1e2"), completed = true, lastPlayedAtEpochMs = 20),
        )
        assertEquals(MediaId("s2e1"), NextUp.of(episodes, states)?.id)
    }

    @Test
    fun nextUpResumesAnUnfinishedEpisode() {
        val episodes = listOf(episode(1, 1), episode(1, 2))
        val states = mapOf(MediaId("s1e2") to WatchState(MediaId("s1e2"), positionMs = 600_000, lastPlayedAtEpochMs = 5))
        assertEquals(MediaId("s1e2"), NextUp.of(episodes, states)?.id)
    }

    @Test
    fun nextUpIsEmptyForUnstartedOrFinishedShows() {
        val episodes = listOf(episode(1, 1), episode(1, 2))
        assertNull(NextUp.of(episodes, emptyMap()))
        val done = episodes.associate { it.id to WatchState(it.id, completed = true, lastPlayedAtEpochMs = 1) }
        assertNull(NextUp.of(episodes, done))
    }

    @Test
    fun specialsDoNotDriveNextUp() {
        val episodes = listOf(episode(0, 1), episode(1, 1))
        val states = mapOf(MediaId("s0e1") to WatchState(MediaId("s0e1"), completed = true, lastPlayedAtEpochMs = 1))
        assertNull(NextUp.of(episodes, states))
    }
}
