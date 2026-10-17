package dev.reflux.library

import dev.reflux.core.identify.ParsedKind
import dev.reflux.core.identify.ParsedMedia
import dev.reflux.core.model.Availability
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.StableIds
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.AudioStream
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoStream
import dev.reflux.core.source.CatalogEntry
import dev.reflux.core.source.CatalogSource
import dev.reflux.core.source.CatalogVersion
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.core.source.SourceCapability
import dev.reflux.core.source.SourceDescriptor
import dev.reflux.core.source.SourceLocality
import dev.reflux.core.source.SourceUnavailableException
import dev.reflux.core.source.SourceUserState
import dev.reflux.core.source.WatchStateSyncSource
import dev.reflux.core.watch.WatchRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val MINUTE = 60_000L
private const val RUNNING_TIME = 120 * MINUTE

/**
 * A media server that keeps its own record the way a real Jellyfin server was measured to (WATCH_STATE.md,
 * "What a real server does"): a playback's first report opens a session and counts a play, without storing
 * its position; later reports store theirs at once; any report past 90% of the item's running time marks it
 * played and clears its position; marking it played counts another play; every report dates the record.
 */
private class SelfMarkingServer(private val clock: () -> Long) : CatalogSource, WatchStateSyncSource {
    var reachable = true
    var played = false
    var positionMs = 0L
    var plays = 0
    var lastPlayed: Long? = null
    private var session = false
    val calls = mutableListOf<String>()

    override val descriptor = SourceDescriptor(
        StableIds.sourceId("server", "https://media.example"), "server", "Media Server", SourceLocality.LOCAL_NETWORK,
        setOf(SourceCapability.PROVIDES_CATALOG, SourceCapability.WATCH_STATE_SYNC),
    )
    override val marksPlayedPast = 0.90

    override suspend fun availability() = if (reachable) Availability.AVAILABLE else Availability.UNAVAILABLE
    override suspend fun playbackTarget(path: String) = PlaybackTarget("https://media.example/$path")
    override fun catalog(): Flow<CatalogEntry> = if (!reachable) flow { throw SourceUnavailableException(descriptor.id) } else flowOf(
        CatalogEntry(
            ParsedMedia(ParsedKind.MOVIE, "Arrival", 2016),
            listOf(CatalogVersion("items/arrival", 4_000_000_000, 1, STREAM)),
            userState = SourceUserState(played, positionMs, plays, lastPlayed, favorite = false),
        ),
    )

    override suspend fun reportProgress(path: String, positionMs: Long, paused: Boolean) {
        reach()
        if (!session) {
            session = true
            plays++
            calls += "start ${positionMs / 1000}"
        } else {
            calls += "progress ${positionMs / 1000}"
            keep(positionMs)
        }
        lastPlayed = clock()
    }

    override suspend fun reportStopped(path: String, positionMs: Long) {
        reach()
        session = false
        calls += "stop ${positionMs / 1000}"
        keep(positionMs)
        lastPlayed = clock()
    }

    /** As the Jellyfin adapter does: an open session hears a position as its progress. */
    override suspend fun updatePosition(path: String, positionMs: Long) =
        if (session) reportProgress(path, positionMs, paused = false) else reportStopped(path, positionMs)

    override suspend fun setPlayed(path: String, played: Boolean) {
        reach()
        calls += "played $played"
        if (played) { this.played = true; positionMs = 0; plays++ } else { this.played = false; positionMs = 0; plays = 0; lastPlayed = null }
    }

    /** What another client of the server does. */
    fun anotherClientMarksItPlayed() {
        played = true
        positionMs = 0
        plays++
        lastPlayed = clock()
    }

    private fun keep(position: Long) {
        if (position > RUNNING_TIME * 0.90) { played = true; positionMs = 0 } else positionMs = position
    }

    private fun reach() { if (!reachable) throw SourceUnavailableException(descriptor.id) }

    companion object {
        val STREAM = StreamInfo(Container.MATROSKA, VideoStream(VideoCodec.H264, 1920, 1080, 8), listOf(AudioStream(AudioCodec.AC3, 6, "eng")), durationMs = RUNNING_TIME)
    }
}

/**
 * A server that counts a work watched once a reported position passes its own threshold never hears such a
 * position from Reflux before a playback has finished, so that it says "played" only when that is true, and
 * Reflux's own record, which is canonical and exact, is never overwritten by an echo of its own reports.
 */
class ServerPlayedThresholdTest {
    private var clock = 1_790_000_000_000L
    private val library = Library(testDatabase()) { clock }
    private val server = SelfMarkingServer { clock }

    init {
        library.addSource(server, "https://media.example")
    }

    private suspend fun arrival(): Pair<MediaId, MediaVersion> {
        library.scan(server)
        val item = library.movies().single().item.id
        return item to library.versions(item).single().version
    }

    private fun minutes(fraction: Double) = (RUNNING_TIME * fraction).toLong()

    private suspend fun scan() {
        clock += MINUTE
        library.scan(server)
    }

    @Test
    fun progressNearTheEndStaysShortOfTheServersThreshold() = runTest(UnconfinedTestDispatcher()) {
        val (item, version) = arrival()
        val playback = SyncingWatchReporter(library, version, server, this)
        playback.progress(item, minutes(0.50), RUNNING_TIME)
        clock += 10_000
        playback.progress(item, minutes(0.95), RUNNING_TIME)

        assertFalse(server.played, "the server was never told a position it counts as watched")
        assertTrue(server.positionMs < RUNNING_TIME * 0.90, "it holds ${server.positionMs / 1000}s")
        assertEquals(minutes(0.95), library.watchState(item)?.positionMs, "Reflux's own position is exact")
        assertFalse(library.watchState(item)!!.completed)

        // A scan while the playback is open (the server's record dated after Reflux's own report) finds the two
        // in agreement: the server holds Reflux's position as it was told it, and that is not news.
        val calls = server.calls.size
        scan()
        assertEquals(minutes(0.95), library.watchState(item)?.positionMs)
        assertFalse(library.watchState(item)!!.completed)
        assertEquals(calls, server.calls.size)
    }

    @Test
    fun goingBackFromNearTheEndAndLeavingIsAPositionEverywhere() = runTest(UnconfinedTestDispatcher()) {
        val (item, version) = arrival()
        // As the real run did: to 95%, back to thirty minutes, and leaving there.
        val playback = SyncingWatchReporter(library, version, server, this)
        playback.progress(item, minutes(0.50), RUNNING_TIME)
        clock += 10_000
        playback.progress(item, minutes(0.95), RUNNING_TIME)
        clock += 10_000
        playback.progress(item, 30 * MINUTE, RUNNING_TIME)
        clock += 10_000
        playback.stopped(item, 30 * MINUTE, RUNNING_TIME)

        assertFalse(server.played)
        assertEquals(30 * MINUTE, server.positionMs)
        assertEquals(1, server.plays, "one playback, one play")
        val state = assertNotNull(library.watchState(item))
        assertEquals(30 * MINUTE, state.positionMs)
        assertFalse(state.completed)

        // The next scan finds the two in agreement: nothing is adopted, nothing is sent.
        val calls = server.calls.size
        scan()
        assertEquals(30 * MINUTE, library.watchState(item)?.positionMs)
        assertFalse(library.watchState(item)!!.completed, "not watched because of the earlier report near the end")
        assertEquals(calls, server.calls.size)
    }

    @Test
    fun aPositionCloseToTheEndStaysExactInReflux() = runTest(UnconfinedTestDispatcher()) {
        val (item, version) = arrival()
        // Left at 89%: not watched by Reflux's rule; the server holds it a little earlier.
        val playback = SyncingWatchReporter(library, version, server, this)
        playback.progress(item, minutes(0.50), RUNNING_TIME)
        clock += 10_000
        playback.stopped(item, minutes(0.89), RUNNING_TIME)
        assertFalse(server.played)
        assertTrue(server.positionMs < minutes(0.89))

        // The server's record is dated after Reflux's own stop, and is still not news: it is what Reflux said.
        scan()
        scan()
        assertEquals(minutes(0.89), library.watchState(item)?.positionMs)
        assertFalse(library.watchState(item)!!.completed)
        assertEquals(listOf("start 3600", "stop ${minutes(0.85) / 1000}"), server.calls, "and it is not told again")
    }

    @Test
    fun aPositionAScanBringsUpToDateStaysShortOfTheThresholdAndTheSessionOpen() = runTest(UnconfinedTestDispatcher()) {
        val (item, version) = arrival()
        val playback = SyncingWatchReporter(library, version, server, this)
        playback.progress(item, minutes(0.50), RUNNING_TIME)
        clock += 10_000
        playback.progress(item, minutes(0.60), RUNNING_TIME)
        scan()
        // The server goes away while the playback goes on to 95%; it does not hear that.
        server.reachable = false
        clock += 10_000
        playback.progress(item, minutes(0.95), RUNNING_TIME)
        server.reachable = true

        // The scan when it is back tells it the position Reflux has, as the playback's progress, held short.
        scan()
        assertEquals("progress ${minutes(0.85) / 1000}", server.calls.last())
        assertFalse(server.calls.any { it.startsWith("stop") }, "the playback's session stays open")
        assertFalse(server.played)
        assertEquals(1, server.plays)
        assertEquals(minutes(0.95), library.watchState(item)?.positionMs)
        assertFalse(library.watchState(item)!!.completed)
    }

    @Test
    fun aPlaybackThatFinishesIsReportedAsHavingReachedTheEnd() = runTest(UnconfinedTestDispatcher()) {
        val (item, version) = arrival()
        // Reflux's rule is unchanged: 90% of the running time is watched.
        assertTrue(WatchRules.isCompletedAt(minutes(0.90), RUNNING_TIME))
        assertFalse(WatchRules.isCompletedAt(minutes(0.90) - 1, RUNNING_TIME))
        val playback = SyncingWatchReporter(library, version, server, this)
        playback.progress(item, minutes(0.50), RUNNING_TIME)
        clock += 10_000
        playback.progress(item, minutes(0.89), RUNNING_TIME)
        clock += 10_000
        playback.stopped(item, minutes(0.90), RUNNING_TIME)

        assertEquals("stop ${RUNNING_TIME / 1000}", server.calls.last(), "the end, so the server counts it watched by its own rule")
        assertTrue(server.played)
        assertEquals(1, server.plays, "as one of its own clients would have: one playback, one play")
        val state = assertNotNull(library.watchState(item))
        assertTrue(state.completed)
        assertEquals(1, state.playCount)

        // The two agree afterwards; the server is not marked played a second time.
        val calls = server.calls.size
        scan()
        assertTrue(library.watchState(item)!!.completed)
        assertEquals(calls, server.calls.size)
    }

    @Test
    fun anotherClientMarkingItPlayedIsStillNews() = runTest(UnconfinedTestDispatcher()) {
        val (item, version) = arrival()
        val playback = SyncingWatchReporter(library, version, server, this)
        playback.progress(item, minutes(0.50), RUNNING_TIME)
        clock += 10_000
        playback.stopped(item, minutes(0.60), RUNNING_TIME)
        scan()

        clock += 3_600_000
        server.anotherClientMarksItPlayed()
        scan()
        val state = assertNotNull(library.watchState(item))
        assertTrue(state.completed)
        assertEquals(0, state.positionMs)
    }
}
