package dev.reflux.ui.state

import dev.reflux.core.input.Key
import dev.reflux.core.input.SemanticAction
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.AudioPlan
import dev.reflux.core.playback.AudioStream
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DeliveryMode
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.PlanReason
import dev.reflux.core.playback.PlannedPlayback
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.playback.SubtitleStream
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoPlan
import dev.reflux.core.playback.VideoStream
import dev.reflux.core.source.NegotiatedDelivery
import dev.reflux.core.source.NegotiatingSource
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.core.source.SourceCapability
import dev.reflux.core.playback.PlaybackEvidence
import dev.reflux.core.playback.PlaybackFailure
import dev.reflux.core.playback.StreamFate
import dev.reflux.core.source.MediaSource
import dev.reflux.core.source.PlaybackEvidenceSource
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.playback.PlaybackRequest
import dev.reflux.core.playback.TrackType
import dev.reflux.core.playback.SkipSegment
import dev.reflux.ui.FakePlatform
import dev.reflux.ui.TestWorld
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSessionTest {
    private class Playing(val world: TestWorld, val platform: FakePlatform, val app: AppState) {
        val session: PlaybackSession get() = app.playback!!
        val player get() = platform.players.last()
    }

    private suspend fun TestScope.playing(show: String, episode: Int, platform: FakePlatform = FakePlatform()): Playing {
        val world = TestWorld().apply { populate() }
        val app = appState(world, platform)
        app.play(world.episodes(show)[episode].item.id)
        runCurrent()
        return Playing(world, platform, app)
    }

    @Test
    fun aSubtitleThatNeverCameIsSaidAndNothingElseChanges() = runTest {
        val playing = playing("Breaking Bad", 0)
        val session = playing.session
        val requests = playing.player.requests.size
        playing.player.loseSubtitle()
        runCurrent()
        assertEquals(Problems.SUBTITLE_UNAVAILABLE, playing.app.notice)
        assertEquals(PlayerStatus.PLAYING, session.state.value.status)
        assertNull(session.error, "the picture is worth keeping")
        assertEquals(requests, playing.player.requests.size, "and it is not a reason to plan again")
        assertTrue(playing.platform.reports.any { "did not arrive" in it }, "${playing.platform.reports}")
    }

    @Test
    fun audioThatCannotBePlayedIsSaidOnceAndThePictureGoesOn() = runTest {
        val playing = playing("Breaking Bad", 0)
        val session = playing.session
        playing.player.loseSound(listOf("audio/vnd.dts"))
        runCurrent()
        assertFalse(session.silent, "an engine may still be settling on its tracks")

        advanceTimeBy(3_100)
        runCurrent()
        assertTrue(session.silent)
        assertEquals(Problems.NO_SOUND, playing.app.notice)
        assertEquals(PlayerStatus.PLAYING, session.state.value.status)
        assertNull(session.error, "the picture is worth keeping")
        assertTrue(playing.platform.reports.any { "audio/vnd.dts" in it && "none of it is playing" in it }, "${playing.platform.reports}")

        advanceTimeBy(60_000)
        playing.player.playTo(120_000)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1, playing.platform.reports.count { "none of it is playing" in it }, "said once")
    }

    @Test
    fun aMomentWithoutATrackIsNotSilence() = runTest {
        val playing = playing("Breaking Bad", 0)
        playing.player.loseSound()
        advanceTimeBy(1_000)
        runCurrent()
        playing.player.regainSound()
        advanceTimeBy(10_000)
        runCurrent()
        assertFalse(playing.session.silent)
        assertNull(playing.app.notice)
    }

    @Test
    fun mediaWithNoSoundAtAllIsNotSilence() = runTest {
        val platform = FakePlatform()
        val playing = playing("Breaking Bad", 0, platform)
        playing.player.tracks = emptyList()
        playing.player.load(playing.player.requests.last())
        playing.player.loseSound()
        advanceTimeBy(10_000)
        runCurrent()
        assertFalse(playing.session.silent)
    }

    @Test
    fun aNewFileStartsWithItsOwnSound() = runTest {
        val playing = playing("Breaking Bad", 0)
        playing.player.loseSound()
        advanceTimeBy(3_100)
        runCurrent()
        assertTrue(playing.session.silent)
        playing.session.playNext()
        runCurrent()
        assertFalse(playing.session.silent)
    }

    @Test
    fun whereNothingCanRemuxASeekIsRefusedWithAWordAndThePlaceKept() = runTest {
        val platform = FakePlatform().apply { seekable = { false } }
        val playing = playing("Breaking Bad", 0, platform)
        val requests = playing.player.requests.size
        playing.player.playTo(30_000)
        runCurrent()
        playing.session.seekTo(600_000)
        runCurrent()
        assertEquals(30_000L, playing.player.state.value.positionMs, "not back to the beginning")
        assertEquals(Problems.CANNOT_SEEK, playing.app.notice)
        assertEquals(requests, playing.player.requests.size, "nothing is planned again")
        assertNull(playing.session.error, "the film goes on")
    }

    @Test
    fun whereNothingCanRemuxALostPlaceIsSaidAndKeptForTheFirstMinute() = runTest {
        val world = TestWorld().apply { populate() }
        val platform = FakePlatform().apply { seekable = { false } }
        val app = appState(world, platform)
        val item = world.episodes("Breaking Bad")[0].item.id
        world.library.progress(item, 300_000, 50 * 60_000L)
        app.play(item)
        runCurrent()
        assertEquals(Problems.PLACE_NOT_REACHED, app.notice)
        assertEquals(1, platform.players.last().requests.size)
        platform.players.last().playTo(20_000)
        runCurrent()
        app.playback!!.close().join()
        assertEquals(300_000L, world.library.watchState(item)?.positionMs, "a minute of starting over does not erase the place")
    }

    @Test
    fun theBeginningCanStillBeAskedForInMediaThatCannotBeMovedIn() = runTest {
        val platform = FakePlatform().apply { seekable = { false } }
        val playing = playing("Breaking Bad", 0, platform)
        playing.player.playTo(30_000)
        runCurrent()
        playing.session.handle(SemanticAction.PREVIOUS)
        runCurrent()
        assertEquals(0L, playing.player.state.value.positionMs)
    }

    /** A server of the tests' own: it can convert, and it remembers being asked. */
    private class Converting(private val files: MediaSource) : MediaSource by files, NegotiatingSource, PlaybackEvidenceSource {
        val plans = mutableListOf<PlannedPlayback>()
        var released = 0
        var agrees = true
        override val descriptor = files.descriptor.copy(
            capabilities = files.descriptor.capabilities + setOf(SourceCapability.SERVER_TRANSCODE, SourceCapability.SERVER_AUDIO_CONVERSION, SourceCapability.SERVER_REMUX),
        )

        override suspend fun negotiate(version: MediaVersion, plan: PlannedPlayback, device: DeviceCapabilities): NegotiatedDelivery? {
            plans += plan
            if (!agrees && plan.delivery != DeliveryMode.DIRECT_PLAY) return null
            return NegotiatedDelivery(PlaybackTarget(if (plan.delivery == DeliveryMode.DIRECT_PLAY) "file:///original" else "http://server/master.m3u8"))
        }

        override suspend fun release(path: String) {
            released++
        }

        /** Only of a remux: what a real server says of the stream now open, whichever plan is asking. */
        override suspend fun playbackEvidence(path: String): PlaybackEvidence? =
            if (plans.lastOrNull()?.delivery == DeliveryMode.REMUX) PlaybackEvidence("a server stream", null, DeliveryMode.REMUX, StreamFate.PRESERVED, StreamFate.PRESERVED) else null
    }

    private class Served(val playing: Playing, val server: Converting)

    private suspend fun TestScope.served(seekable: (PlaybackRequest) -> Boolean? = { null }, savedAt: Long? = null): Served {
        val world = TestWorld().apply { populate() }
        val platform = FakePlatform()
        platform.seekable = seekable
        var server: Converting? = null
        val app = AppState(
            world.library, world.pipeline, { id -> world.registry.resolve(id)?.let { real -> server ?: Converting(real).also { server = it } } },
            platform, backgroundScope, StandardTestDispatcher(testScheduler),
        )
        val episode = world.episodes("Breaking Bad")[0]
        val version = world.library.versions(episode.item.id).single().version
        world.library.recordProbe(
            version.id,
            StreamInfo(
                Container.MATROSKA, VideoStream(VideoCodec.H264, 1280, 720, 8), listOf(AudioStream(AudioCodec.AAC, 2, "en", default = true), AudioStream(AudioCodec.AAC, 2, "fr")),
                listOf(SubtitleStream(SubtitleFormat.SRT, "es")),
            ),
        )
        savedAt?.let { world.library.progress(episode.item.id, it, 50 * 60_000L) }
        app.play(episode.item.id)
        runCurrent()
        return Served(Playing(world, platform, app), server!!)
    }

    /** As Media3 and a real file whose index it cannot reach: the original cannot be moved in, and a server's remux can. */
    private val originalWillNotSeek: (PlaybackRequest) -> Boolean? = { request -> "master.m3u8" in request.target.uri }

    @Test
    fun aSeekInAnOriginalTheEngineCannotMoveInGoesThereByRemux() = runTest {
        val served = served(originalWillNotSeek)
        val player = served.playing.player
        assertEquals(DeliveryMode.DIRECT_PLAY, served.server.plans.single().delivery, "nothing changes for a viewer who never seeks")
        player.playTo(30_000)
        runCurrent()

        served.playing.session.seekTo(600_000)
        runCurrent()

        val plan = served.server.plans.last()
        assertEquals(DeliveryMode.REMUX, plan.delivery)
        assertTrue(plan.video != VideoPlan.TRANSCODE, "$plan")
        assertEquals(AudioPlan.ORIGINAL_LOCAL_DECODE, plan.audio)
        assertTrue(PlanReason.ORIGINAL_FAILED in plan.reasons && PlanReason.SEEKING_LIMITED in plan.reasons, "${plan.reasons}")
        assertEquals("http://server/master.m3u8", player.requests.last().target.uri)
        assertEquals(600_000L, player.requests.last().startPositionMs)
        assertEquals(600_000L, player.state.value.positionMs, "the place the viewer asked for")
        assertNull(served.playing.app.notice)
    }

    @Test
    fun theSeekKeysReachTheSamePlaceByRemux() = runTest {
        val served = served(originalWillNotSeek)
        served.playing.player.playTo(30_000)
        runCurrent()
        served.playing.session.handle(SemanticAction.SEEK_FORWARD)
        runCurrent()
        assertEquals(DeliveryMode.REMUX, served.server.plans.last().delivery)
        assertEquals(40_000L, served.playing.player.requests.last().startPositionMs)
    }

    @Test
    fun aResumeThatFellToTheBeginningGoesBackByRemuxBeforeThePlaceIsLost() = runTest {
        val served = served(originalWillNotSeek, savedAt = 300_000)
        val player = served.playing.player
        assertEquals(listOf(DeliveryMode.DIRECT_PLAY, DeliveryMode.REMUX), served.server.plans.map { it.delivery })
        assertEquals(300_000L, player.requests.last().startPositionMs)
        assertEquals(300_000L, player.state.value.positionMs)
        served.playing.session.recorded()
        val item = served.playing.world.episodes("Breaking Bad")[0].item.id
        assertEquals(300_000L, served.playing.world.library.watchState(item)?.positionMs, "the saved place is untouched")
    }

    @Test
    fun aPlanLeftBehindIsNotComparedWithTheStreamThatReplacedIt() = runTest {
        val served = served(originalWillNotSeek, savedAt = 300_000)
        val platform = served.playing.platform
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(DeliveryMode.REMUX, served.playing.session.comparison?.planned?.delivery, "the plan on screen is the one compared")
        assertFalse(platform.reports.any { "not what was planned" in it }, "${platform.reports}")
    }

    @Test
    fun aViewersOwnAudioTrackGoesWithTheRemux() = runTest {
        val served = served(originalWillNotSeek)
        val session = served.playing.session
        session.selectAudio("2")
        session.seekTo(600_000)
        runCurrent()
        assertEquals(1, served.server.plans.last().audioIndex)
    }

    @Test
    fun aMoveInMediaThatCanBeMovedInChangesNothing() = runTest {
        val served = served({ true }, savedAt = 300_000)
        served.playing.session.seekTo(600_000)
        runCurrent()
        assertEquals(1, served.server.plans.size, "still Direct Play")
        assertEquals(600_000L, served.playing.player.state.value.positionMs)
    }

    @Test
    fun anEngineThatHasNotSaidIsSeekedAsItAlwaysWas() = runTest {
        val served = served({ null }, savedAt = 300_000)
        served.playing.session.seekTo(600_000)
        runCurrent()
        assertEquals(DeliveryMode.DIRECT_PLAY, served.server.plans.single().delivery)
        assertEquals(600_000L, served.playing.player.state.value.positionMs)
    }

    @Test
    fun aRemuxIsOnceForAWork() = runTest {
        val served = served({ false })
        val session = served.playing.session
        session.seekTo(600_000)
        runCurrent()
        assertEquals(2, served.server.plans.size)
        session.seekTo(700_000)
        runCurrent()
        assertEquals(2, served.server.plans.size, "no ladder")
        assertEquals(Problems.CANNOT_SEEK, served.playing.app.notice)
    }

    @Test
    fun aServerThatRefusesTheRemuxLeavesTheFilmPlaying() = runTest {
        val served = served(originalWillNotSeek)
        served.server.agrees = false
        served.playing.player.playTo(30_000)
        runCurrent()
        served.playing.session.seekTo(600_000)
        runCurrent()
        assertEquals(DeliveryMode.REMUX, served.server.plans[1].delivery)
        assertEquals(DeliveryMode.DIRECT_PLAY, served.server.plans.last().delivery, "what the client can do alone")
        assertEquals(PlayerStatus.PLAYING, served.playing.player.state.value.status)
        assertNull(served.playing.session.error, "it plays, and it can say so")
        assertEquals(Problems.PLACE_NOT_REACHED, served.playing.app.notice)
    }

    @Test
    fun anOriginalTheEngineCannotPlayIsPlannedAroundOnceFromTheSamePlace() = runTest {
        val served = served()
        val player = served.playing.player
        assertEquals(DeliveryMode.DIRECT_PLAY, served.server.plans.single().delivery)
        player.fail("no decoder took it", PlaybackFailure.UNSUPPORTED)
        runCurrent()
        assertEquals(DeliveryMode.VIDEO_TRANSCODE, served.server.plans.last().delivery)
        assertTrue(PlanReason.ORIGINAL_FAILED in served.server.plans.last().reasons)
        assertEquals("http://server/master.m3u8", player.requests.last().target.uri)
        assertNull(served.playing.session.error, "nothing to tell the viewer yet")

        player.fail("that did not play either", PlaybackFailure.UNSUPPORTED)
        runCurrent()
        assertEquals(2, served.server.plans.size, "once")
        assertNotNull(served.playing.session.error, "and then it is the failure it is")
    }

    @Test
    fun anotherTrackOfAConvertedStreamCarriesOnFromTheExactPlace() = runTest {
        val served = served()
        val player = served.playing.player
        player.fail("no decoder took it", PlaybackFailure.UNSUPPORTED)
        runCurrent()
        player.playTo(38_000)
        runCurrent()
        served.playing.session.choose(PlayerMenu.AUDIO, 1)
        runCurrent()
        assertEquals(38_000, player.requests.last().startPositionMs, "under a minute, which a return visit would start over")
    }

    @Test
    fun anOriginalThatFailsPartWayIsPlannedAroundFromThatPlace() = runTest {
        val served = served()
        val player = served.playing.player
        player.playTo(38_000)
        runCurrent()
        player.fail("no decoder took it", PlaybackFailure.UNSUPPORTED)
        runCurrent()
        assertEquals(38_000, player.requests.last().startPositionMs)
    }

    @Test
    fun aSourceThatGoesAwayIsNotPlannedAround() = runTest {
        val served = served()
        served.playing.player.fail("gone", PlaybackFailure.UNREACHABLE)
        runCurrent()
        assertEquals(1, served.server.plans.size)
        assertNotNull(served.playing.session.error)
    }

    @Test
    fun anOutputThatWillNotStartIsNotPlannedAround() = runTest {
        val served = served()
        served.playing.player.fail("no audio device", PlaybackFailure.OUTPUT)
        runCurrent()
        assertEquals(1, served.server.plans.size)
    }

    @Test
    fun aFileSourceHasNothingToPlanAroundAndStatesTheFailure() = runTest {
        val playing = playing("Breaking Bad", 0)
        playing.player.fail("no decoder took it", PlaybackFailure.UNSUPPORTED)
        runCurrent()
        assertEquals(1, playing.player.requests.size)
        assertNotNull(playing.session.error)
    }

    @Test
    fun audioAnAttemptShowedCannotPlayIsConvertedAndKeepsItsTrack() = runTest {
        val served = served()
        val session = served.playing.session
        session.selectAudio("2")
        served.playing.player.loseSound()
        advanceTimeBy(3_100)
        runCurrent()
        val plan = served.server.plans.last()
        assertEquals(AudioPlan.CONVERT, plan.audio)
        assertEquals(1, plan.audioIndex, "the French the viewer chose, not the English that plays")
        assertFalse(session.silent, "there is sound on its way")
        assertNull(served.playing.app.notice)
    }

    @Test
    fun aServerThatAlreadySaidNoIsNotAskedAgainWhenTheSoundGoes() = runTest {
        val served = served()
        served.server.agrees = false
        served.playing.session.selectAudio("2")
        served.playing.player.loseSound()
        advanceTimeBy(3_100)
        runCurrent()
        val attempts = served.server.plans.size
        served.playing.player.loseSound()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(attempts, served.server.plans.size)
        assertTrue(served.playing.session.silent)
    }

    @Test
    fun onAConvertedStreamTheMenusAreTheFiles() = runTest {
        val served = served()
        val session = served.playing.session
        served.playing.player.fail("no decoder took it", PlaybackFailure.UNSUPPORTED)
        runCurrent()
        assertEquals(DeliveryMode.VIDEO_TRANSCODE, served.server.plans.last().delivery)
        assertTrue(PlayerMenu.AUDIO in session.menus())
        assertEquals(2, session.options(PlayerMenu.AUDIO).size, "both of the file's tracks, whatever the stream carries")
        assertEquals(2, session.options(PlayerMenu.SUBTITLES).size)

        session.choose(PlayerMenu.AUDIO, 1)
        runCurrent()
        assertEquals(1, served.server.plans.last().audioIndex)
        assertEquals(VideoPlan.TRANSCODE, served.server.plans.last().video, "what failed stays failed")
        assertEquals(3, served.server.plans.size)
    }

    @Test
    fun whatIsConvertedStopsWhenThePlayerCloses() = runTest {
        val served = served()
        val released = served.server.released
        served.playing.session.close()
        runCurrent()
        assertEquals(released + 1, served.server.released)
    }

    @Test
    fun aMismatchBetweenPlanAndEngineIsReported() = runTest {
        val playing = playing("Breaking Bad", 0)
        val world = playing.world
        val version = world.library.versions(world.episodes("Breaking Bad")[0].item.id).single().version
        assertNotNull(version)
        playing.player.selectAudio("2")
        advanceTimeBy(5_100)
        runCurrent()
        assertNotNull(playing.session.comparison, "a source with no account still has the engine's side")
    }

    private class Evidencing(source: MediaSource, val found: PlaybackEvidence) : MediaSource by source, PlaybackEvidenceSource {
        var asked = 0
        override suspend fun playbackEvidence(path: String): PlaybackEvidence {
            asked++
            return found
        }
    }

    @Test
    fun whatTheSourceSaysHappenedIsKeptAndLoggedOnce() = runTest {
        val world = TestWorld().apply { populate() }
        val platform = FakePlatform()
        val found = PlaybackEvidence("the original file", "SupportsDirectPlay=true", DeliveryMode.DIRECT_PLAY, StreamFate.PRESERVED, StreamFate.PRESERVED)
        var source: Evidencing? = null
        val app = AppState(
            world.library, world.pipeline, { id -> world.registry.resolve(id)?.let { real -> source ?: Evidencing(real, found).also { source = it } } },
            platform, backgroundScope, StandardTestDispatcher(testScheduler),
        )
        app.play(world.episodes("Breaking Bad")[0].item.id)
        runCurrent()
        val session = app.playback!!
        assertNull(session.evidence, "not before the playback's first report has reached the source")
        advanceTimeBy(5_100)
        runCurrent()
        assertEquals(DeliveryMode.DIRECT_PLAY, session.evidence?.delivery)
        assertTrue(platform.reports.any { it.startsWith("Playback evidence: ") && "DIRECT_PLAY" in it }, "${platform.reports}")
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(1, source?.asked)
    }

    @Test
    fun theNextEpisodeStartsAfterACountdown() = runTest {
        val playing = playing("Breaking Bad", 0)
        val session = playing.session
        assertEquals("S1 · E2 · Cat's in the Bag...", session.next?.let { playing.world.library.heading(it.item).second })
        assertNull(session.previous)

        playing.player.end()
        runCurrent()
        val prompt = assertNotNull(session.upNext)
        assertFalse(prompt.asks)
        assertEquals(10_000, prompt.remainingMs)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(5_000, session.upNext?.remainingMs)
        advanceTimeBy(5_100)
        runCurrent()

        assertNull(session.upNext)
        assertEquals(2, playing.player.requests.size)
        assertEquals("S1 · E2 · Cat's in the Bag...", session.subtitle)
        assertTrue(playing.world.episodes("Breaking Bad")[0].watchState?.completed == true, "the finished episode is watched")
        assertEquals(1, playing.platform.players.size, "the same engine keeps playing")
    }

    @Test
    fun theCountdownCanBeCancelledOrSkipped() = runTest {
        val playing = playing("Breaking Bad", 0)
        playing.player.end()
        runCurrent()
        playing.session.cancelUpNext()
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(1, playing.player.requests.size)
        assertNotNull(playing.app.playback, "cancelling stays in the player")

        val again = playing("Breaking Bad", 0)
        again.player.end()
        runCurrent()
        assertTrue(again.session.handle(SemanticAction.PLAY_PAUSE))
        runCurrent()
        assertEquals(2, again.player.requests.size, "Play during the countdown starts the next episode now")
    }

    @Test
    fun aSleepingViewerIsAskedBeforeTheWholeSeasonPlays() = runTest {
        val playing = playing("Breaking Bad", 0)
        repeat(3) {
            playing.player.end()
            runCurrent()
            advanceTimeBy(10_100)
            runCurrent()
        }
        assertEquals(4, playing.player.requests.size)

        playing.player.end()
        runCurrent()
        assertTrue(assertNotNull(playing.session.upNext).asks)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(4, playing.player.requests.size, "nothing plays until the viewer answers")

        playing.session.continueWatching()
        runCurrent()
        assertEquals(5, playing.player.requests.size)
        assertEquals("S1 · E5 · Gray Matter", playing.session.subtitle)
    }

    @Test
    fun theLastEpisodeEndsThePlayer() = runTest {
        val playing = playing("Severance", 3)
        val player = playing.player
        player.end()
        runCurrent()
        assertNull(playing.app.playback)
        assertTrue(player.closed)
        assertTrue(playing.world.episodes("Severance")[3].watchState?.completed == true)
    }

    @Test
    fun episodesCanBeSteppedThroughDirectly() = runTest {
        val playing = playing("Breaking Bad", 1)
        playing.player.playTo(20 * 60_000L)
        runCurrent()
        assertTrue(playing.session.handle(SemanticAction.NEXT))
        runCurrent()
        assertEquals("S1 · E3 · ...And the Bag's in the River", playing.session.subtitle)
        assertEquals(20 * 60_000L, playing.world.episodes("Breaking Bad")[1].watchState?.positionMs, "the episode left behind keeps its position")

        // Previous restarts the current episode unless it has barely begun.
        playing.player.playTo(20 * 60_000L)
        playing.session.handle(SemanticAction.PREVIOUS)
        runCurrent()
        assertEquals(0L, playing.player.state.value.positionMs)
        assertEquals(2, playing.player.requests.size)
        playing.session.handle(SemanticAction.PREVIOUS)
        runCurrent()
        assertEquals("S1 · E2 · Cat's in the Bag...", playing.session.subtitle)
    }

    @Test
    fun semanticActionsDriveTheEngine() = runTest {
        val playing = playing("Breaking Bad", 0)
        val session = playing.session
        val player = playing.player
        val before = session.interactions

        session.handle(SemanticAction.SEEK_FORWARD)
        assertEquals(10_000L, player.state.value.positionMs)
        session.handle(SemanticAction.NAVIGATE_DOWN)
        assertEquals(95, player.state.value.volume)
        session.handle(SemanticAction.MUTE)
        assertTrue(player.state.value.muted)
        session.handle(SemanticAction.SPEED_UP)
        assertEquals(1.25, player.state.value.speed)
        session.selectSubtitle("1")
        assertEquals("1", player.state.value.selectedSubtitleId)
        assertTrue(session.interactions > before, "every action reveals the controls")

        session.handle(SemanticAction.FULLSCREEN)
        assertTrue(playing.platform.fullscreen)
        session.handle(SemanticAction.BACK)
        runCurrent()
        assertFalse(playing.platform.fullscreen, "leaving the player restores the window")
        assertEquals(PlayerStatus.IDLE, player.state.value.status)
        assertTrue(player.closed)
    }

    @Test
    fun volumeCarriesOverToTheNextSession() = runTest {
        val playing = playing("Breaking Bad", 0)
        playing.session.setVolume(40)
        playing.session.close()
        runCurrent()

        playing.app.play(playing.world.movie("Heat").item.id)
        runCurrent()
        assertEquals(2, playing.platform.players.size)
        assertEquals(40, playing.player.state.value.volume)
    }

    @Test
    fun aPlaybackFailureIsShownAndTheUserCanLeave() = runTest {
        val playing = playing("Breaking Bad", 0)
        playing.player.fail("unsupported format")
        runCurrent()
        assertEquals("unsupported format", assertNotNull(playing.session.error).detail)
        assertNotNull(playing.app.playback, "the player stays open to explain")
        playing.session.close()
        runCurrent()
        assertNull(playing.app.playback)
    }

    @Test
    fun failuresAreExplainedInTheUsersTermsAndKeptForDiagnostics() = runTest {
        // A file that could not be opened at all; one that stops arriving is SourceSilenceTest's.
        val playing = playing("Breaking Bad", 0, FakePlatform().apply { starts = false })
        playing.player.fail("loading failed", PlaybackFailure.UNREACHABLE)
        runCurrent()
        val error = assertNotNull(playing.session.error)
        assertTrue("moved or deleted" in error.message)
        assertEquals("loading failed", error.detail)
        assertTrue(playing.platform.reports.single().let { "Breaking Bad" in it && "UNREACHABLE" in it && "loading failed" in it })

        val unsupported = playing("Breaking Bad", 0)
        unsupported.player.fail("unrecognized file format", PlaybackFailure.UNSUPPORTED)
        runCurrent()
        assertTrue("format" in assertNotNull(unsupported.session.error).message)
    }

    @Test
    fun aFileThatDisappearedLeavesTheLibraryAfterItFailsToPlay() = runTest {
        val playing = playing("Severance", 0)
        val episode = playing.world.episodes("Severance")[0].item.id
        java.nio.file.Files.delete(playing.world.source.resolve("Severance/Season 1/Severance.S01E01.2160p.mkv"))

        playing.player.fail("loading failed", PlaybackFailure.UNREACHABLE)
        runCurrent()
        // The rescan reads the folder on real I/O threads.
        val deadline = System.currentTimeMillis() + 10_000
        while (playing.world.episodes("Severance").any { it.item.id == episode } && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            runCurrent()
        }
        assertEquals(3, playing.world.episodes("Severance").size, "the library no longer offers the missing episode")
    }

    @Test
    fun afterAFailureEveryWayOnIsTheWayOut() = runTest {
        val playing = playing("Breaking Bad", 0)
        playing.player.fail("no audio or video data played", PlaybackFailure.UNSUPPORTED)
        runCurrent()
        playing.session.handle(SemanticAction.MENU)
        assertNull(playing.session.menu, "no menus for something that is not playing")
        playing.session.handle(SemanticAction.PLAY_PAUSE)
        runCurrent()
        assertNull(playing.app.playback)
    }

    @Test
    fun whereLoudnessIsTheDevicesOwnDownOpensTheListsAndUpTakesTheOffer() = runTest {
        val playing = playing("Breaking Bad", 0, FakePlatform(systemVolume = true))
        val session = playing.session
        val player = playing.player

        // Nothing on offer: up does nothing to playback, and never touches the volume.
        session.handle(SemanticAction.NAVIGATE_UP)
        assertEquals(100, player.state.value.volume)
        assertEquals(0L, player.state.value.positionMs)

        // Inside the intro, up is the skip that is offered on screen.
        player.playTo(95_000)
        assertEquals(SkipSegment.Kind.INTRO, session.skippable()?.kind)
        session.handle(SemanticAction.NAVIGATE_UP)
        assertEquals(150_000L, player.state.value.positionMs)
        assertNull(session.skippable())

        // Down opens the lists where a controller's menu button would.
        session.handle(SemanticAction.NAVIGATE_DOWN)
        assertEquals(PlayerMenu.SUBTITLES, session.menu)
        assertEquals(100, player.state.value.volume)
        session.handle(SemanticAction.BACK)
        assertNull(session.menu)

        // Without subtitles it opens on the first list about what is playing, not on the episodes.
        player.tracks = player.tracks.filter { it.type != TrackType.SUBTITLE }
        player.load(player.requests.last())
        session.handle(SemanticAction.NAVIGATE_DOWN)
        assertEquals(PlayerMenu.CHAPTERS, session.menu)
    }

    @Test
    fun theEpisodesListLeadsToTheOnesEitherSide() = runTest {
        val playing = playing("Breaking Bad", 1)
        val session = playing.session
        assertEquals(PlayerMenu.EPISODES, session.menus().first())
        assertEquals(
            listOf("S1 · E3 · ...And the Bag's in the River" to "Next", "S1 · E1 · Pilot" to "Previous"),
            session.options(PlayerMenu.EPISODES).map { it.label to it.detail },
        )
        session.toggleMenu(PlayerMenu.EPISODES)
        assertEquals(0, session.highlighted, "the next one is the one usually wanted")
        session.handle(SemanticAction.NAVIGATE_DOWN)
        session.handle(SemanticAction.SELECT)
        runCurrent()
        assertEquals("S1 · E1 · Pilot", session.subtitle)
        assertNull(session.menu)
        // The first episode has nothing before it.
        assertEquals(listOf("Next"), session.options(PlayerMenu.EPISODES).map { it.detail })

        // A film has no neighbours, and no such list.
        val film = appState(playing.world, playing.platform)
        film.play(playing.world.library.movies().first { it.item.title == "Heat" }.item.id)
        runCurrent()
        assertFalse(PlayerMenu.EPISODES in film.playback!!.menus())
    }

    @Test
    fun menusAreDrivenBySemanticActions() = runTest {
        val playing = playing("Breaking Bad", 0)
        val session = playing.session
        val player = playing.player
        assertEquals(listOf(PlayerMenu.EPISODES, PlayerMenu.CHAPTERS, PlayerMenu.AUDIO, PlayerMenu.SUBTITLES, PlayerMenu.SPEED, PlayerMenu.INFO), session.menus())

        session.handle(SemanticAction.MENU)
        assertEquals(PlayerMenu.SUBTITLES, session.menu)
        assertEquals(listOf("Off" to true, "English" to false), session.options(PlayerMenu.SUBTITLES).take(2).map { it.label to it.selected })
        assertEquals(0, session.highlighted, "a menu opens on what is in effect")
        session.handle(SemanticAction.NAVIGATE_UP)
        assertEquals(0, session.highlighted)
        repeat(5) { session.handle(SemanticAction.NAVIGATE_DOWN) }
        assertEquals(2, session.highlighted, "the highlight stops at the last option")
        session.handle(SemanticAction.NAVIGATE_UP)
        session.handle(SemanticAction.SELECT)
        assertEquals("1", player.state.value.selectedSubtitleId)
        assertNull(session.menu)

        session.handle(SemanticAction.MENU)
        assertEquals(1, session.highlighted)
        session.handle(SemanticAction.NAVIGATE_RIGHT)
        assertEquals(PlayerMenu.SPEED, session.menu)
        session.handle(SemanticAction.NAVIGATE_RIGHT)
        assertEquals(PlayerMenu.INFO, session.menu, "why it plays as it does is the last list")
        assertEquals("How it plays", session.options(PlayerMenu.INFO).first().detail)
        session.handle(SemanticAction.NAVIGATE_RIGHT)
        assertEquals(PlayerMenu.INFO, session.menu, "there is no menu past the last")
        session.handle(SemanticAction.NAVIGATE_LEFT)
        assertEquals(PlayerMenu.SPEED, session.menu)
        assertEquals("Normal", session.options(PlayerMenu.SPEED)[session.highlighted].label)
        session.handle(SemanticAction.NAVIGATE_DOWN)
        session.handle(SemanticAction.SELECT)
        assertEquals(1.25, player.state.value.speed)

        session.toggleMenu(PlayerMenu.AUDIO)
        session.handle(SemanticAction.BACK)
        assertNull(session.menu)
        assertNotNull(playing.app.playback, "Back closes the menu first")
        session.handle(SemanticAction.BACK)
        runCurrent()
        assertNull(playing.app.playback)
    }

    @Test
    fun keysMeanNavigationWhileAMenuIsOpenAndPlaybackOtherwise() = runTest {
        val playing = playing("Breaking Bad", 0)
        val player = playing.player
        playing.app.onKey(Key.ARROW_RIGHT)
        assertEquals(10_000L, player.state.value.positionMs)

        playing.session.toggleMenu(PlayerMenu.CHAPTERS)
        playing.app.onKey(Key.ARROW_DOWN)
        playing.app.onKey(Key.ARROW_RIGHT)
        assertEquals(PlayerMenu.AUDIO, playing.session.menu)
        assertEquals(10_000L, player.state.value.positionMs, "arrows moved through the menus instead of seeking")
        assertEquals(100, player.state.value.volume)
        playing.app.onKey(Key.ARROW_DOWN)
        playing.app.onKey(Key.ENTER)
        assertEquals("2", player.state.value.selectedAudioId)
        assertEquals(PlayerStatus.PLAYING, player.state.value.status, "Enter chose the track; it did not pause")

        playing.app.onKey(Key.ESCAPE)
        runCurrent()
        assertNull(playing.app.playback)
    }

    @Test
    fun chaptersCanBeJumpedTo() = runTest {
        val playing = playing("Breaking Bad", 0)
        playing.player.playTo(200_000)
        val chapters = playing.session.options(PlayerMenu.CHAPTERS)
        assertEquals(listOf("Intro", "Part One", "Part Two", "Credits"), chapters.map { it.label })
        assertEquals(listOf("1:00", "2:30", "25:00", "47:00"), chapters.map { it.detail })
        assertEquals("Part One", chapters.single { it.selected }.label)

        playing.session.toggleMenu(PlayerMenu.CHAPTERS)
        assertEquals(1, playing.session.highlighted)
        playing.session.choose(PlayerMenu.CHAPTERS, 2)
        assertEquals(25 * 60_000L, playing.player.state.value.positionMs)

        playing.player.chapters = emptyList()
        playing.session.playNext()
        runCurrent()
        assertFalse(PlayerMenu.CHAPTERS in playing.session.menus(), "no chapter menu for a file without chapters")
    }

    @Test
    fun aTrackThatIsGoneIsRefusedWithAWord() = runTest {
        val playing = playing("Breaking Bad", 0)
        playing.session.selectAudio("9")
        assertEquals("1", playing.player.state.value.selectedAudioId)
        assertTrue(assertNotNull(playing.app.notice).contains("no longer available"))

        playing.session.selectSubtitle("9")
        assertNull(playing.player.state.value.selectedSubtitleId)
        playing.session.selectSubtitle(null)
        assertNull(playing.player.state.value.selectedSubtitleId, "turning subtitles off is always possible")
    }

    @Test
    fun aNextEpisodeOnAnOfflineSourceIsExplained() = runTest {
        val playing = playing("Breaking Bad", 0)
        playing.world.registry.unregister(playing.world.source.descriptor.id)
        playing.session.playNext()
        runCurrent()
        // M10: not a silent end. The session is still there, and says why where it is (the player, the dock, the system's window).
        assertSame(playing.session, playing.app.playback)
        val error = assertNotNull(playing.session.error)
        assertEquals("This can't be played right now. Media isn't connected.", error.message)
        assertFalse(error.canRetry, "there is nothing to try again: the way out is Close")
        assertNull(playing.session.upNext)
    }

    @Test
    fun anEngineThatNeverStartsIsNotWaitedForInSilence() = runTest {
        val playing = playing("Breaking Bad", 0)
        val session = playing.session
        val player = playing.player
        player.starts = false
        session.playNext()
        runCurrent()
        assertEquals(PlayerStatus.LOADING, player.state.value.status)
        advanceTimeBy(29_000)
        runCurrent()
        assertNull(session.error, "starting can take a while")

        advanceTimeBy(2_000)
        runCurrent()
        assertTrue("isn't starting" in assertNotNull(session.error).message)
        assertEquals(1, playing.platform.reports.size)
        assertNotNull(playing.app.playback, "the player stays, to say so and offer the way back")

        // Late is not never: if it starts after all, the message makes way.
        player.playTo(1_000)
        runCurrent()
        assertNull(session.error)

        // And an engine that stays stuck can be left.
        player.starts = false
        session.playNext()
        runCurrent()
        advanceTimeBy(31_000)
        runCurrent()
        session.handle(SemanticAction.BACK)
        runCurrent()
        assertNull(playing.app.playback)
    }
}
