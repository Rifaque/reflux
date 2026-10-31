package dev.reflux.ui

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import dev.reflux.core.identify.ParsedKind
import dev.reflux.core.identify.ParsedMedia
import dev.reflux.core.input.Key
import dev.reflux.core.model.Availability
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.StableIds
import dev.reflux.core.offline.DownloadFailure
import dev.reflux.core.offline.DownloadState
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.AudioStream
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoStream
import dev.reflux.core.source.CatalogEntry
import dev.reflux.core.source.CatalogSource
import dev.reflux.core.source.CatalogVersion
import dev.reflux.core.source.DownloadableSource
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.core.source.SourceCapability
import dev.reflux.core.source.SourceDescriptor
import dev.reflux.core.source.SourceLocality
import dev.reflux.core.source.SourceUnavailableException
import dev.reflux.library.DownloadException
import dev.reflux.library.DownloadReceiver
import dev.reflux.library.DownloadTransport
import dev.reflux.library.FileDownloadStore
import dev.reflux.library.Offline
import dev.reflux.library.downloads
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val MINUTE = 60_000L

/** A server with a film and a show in two seasons, handing out its files; transfers can be held or made to fail. */
private class ShowServer : CatalogSource, DownloadableSource {
    var reachable = true
    val files = HashMap<String, ByteArray>()

    /** A transfer whose address contains the key waits half-way until the value completes. */
    val holds = HashMap<String, CompletableDeferred<Unit>>()

    /** Transfers whose address contains one of these find the source gone. */
    val failing = HashSet<String>()

    /** Lets every held transfer finish. */
    fun release() {
        holds.values.forEach { it.complete(Unit) }
        holds.clear()
    }

    override val descriptor = SourceDescriptor(
        StableIds.sourceId("server", "living-room"), "server", "Living Room", SourceLocality.LOCAL_NETWORK,
        setOf(SourceCapability.PROVIDES_CATALOG, SourceCapability.DOWNLOAD),
    )

    private fun stream(minutes: Long) = StreamInfo(Container.MATROSKA, VideoStream(VideoCodec.H264, 1920, 1080, 8), listOf(AudioStream(AudioCodec.AAC, 2, "eng")), durationMs = minutes * MINUTE)

    private fun file(path: String, megabytes: Int, minutes: Long, parsed: ParsedMedia): CatalogEntry {
        files["server://$path"] = ByteArray(64 * 1024) { it.toByte() }
        // The catalog says what the file weighs; the stand-in content is small so that tests are quick.
        return CatalogEntry(parsed, listOf(CatalogVersion(path, megabytes * 1024L * 1024, 1, stream(minutes))))
    }

    private val titles = listOf("Good News About Hell", "Half Loop", "In Perpetuity", "The You You Are")
    private val entries = listOf(file("items/ronin", 4_300, 122, ParsedMedia(ParsedKind.MOVIE, "Ronin", 1998))) +
        titles.mapIndexed { index, title -> file("items/slow-horses-1-${index + 1}", 1_200, 55, ParsedMedia(ParsedKind.EPISODE, "Slow Horses", season = 1, episode = index + 1, episodeTitle = title)) } +
        (1..2).map { number -> file("items/slow-horses-2-$number", 1_300, 50, ParsedMedia(ParsedKind.EPISODE, "Slow Horses", season = 2, episode = number, episodeTitle = "Second $number")) }

    override suspend fun availability() = if (reachable) Availability.AVAILABLE else Availability.UNAVAILABLE
    override suspend fun playbackTarget(path: String) = PlaybackTarget("server://$path")
    override suspend fun downloadTarget(path: String) = playbackTarget(path)
    override fun catalog(): Flow<CatalogEntry> = if (reachable) entries.asFlow() else flow { throw SourceUnavailableException(descriptor.id) }

    val transport = object : DownloadTransport {
        override suspend fun fetch(target: PlaybackTarget, offset: Long, receiver: DownloadReceiver) {
            if (failing.any { it in target.uri }) throw DownloadException(DownloadFailure.SOURCE_UNAVAILABLE)
            val bytes = files.getValue(target.uri)
            receiver.started(0, bytes.size.toLong())
            receiver.bytes(bytes, bytes.size / 2)
            holds.entries.firstOrNull { it.key in target.uri }?.value?.await()
            receiver.bytes(bytes, bytes.size / 2)
        }
    }
}

/** The populated library, the server beside it, and Smart Offline over both. */
private class DownloadsWorld {
    val world = populated()
    val server = ShowServer()
    private val directory = Files.createTempDirectory("reflux-ui-downloads").toFile().also { it.deleteOnExit() }
    val offline = Offline(
        world.library, RoomyStore(FileDownloadStore(directory)), server.transport, world.registry::resolve, { testDevice }, listOf("en"),
        world.registry::register, Dispatchers.Default,
    )
    val platform = FakePlatform(offline = offline)
    fun tvPlatform() = FakePlatform(television = true, offline = offline)

    init {
        world.library.addSource(server, "living-room")
        world.registry.register(server)
        runBlocking { world.pipeline.refresh() }
    }

    val show: MediaId get() = world.library.shows().first { it.item.title == "Slow Horses" }.item.id
    fun season(number: Int): MediaId = world.library.showDetail(show)!!.seasons.single { it.season.number == number }.season.id
    fun episode(season: Int, number: Int): MediaId =
        world.library.showDetail(show)!!.seasons.single { it.season.number == season }.episodes[number - 1].item.id
    val ronin: MediaId get() = world.library.movies().first { it.item.title == "Ronin" }.item.id

    @OptIn(ExperimentalTestApi::class)
    fun DesktopComposeUiTest.begin(app: AppState) {
        runOnIdle {
            app.start()
            offline.start(app.scope)
        }
        waitForIdle()
    }
}

/**
 * Smart Offline from the user's side: an episode from its row or its own page, a whole season beside its tabs,
 * a show that is only as kept as its episodes, and the Downloads page with every copy and what can be done about it.
 */
@OptIn(ExperimentalTestApi::class)
class DownloadsUiTest {
    private fun DesktopComposeUiTest.shows(text: String) = onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    private fun DesktopComposeUiTest.has(description: String) = onAllNodesWithContentDescription(description, substring = true).fetchSemanticsNodes().isNotEmpty()
    private fun DesktopComposeUiTest.focused() = onAllNodes(isFocused()).fetchSemanticsNodes().map { node ->
        node.config.filter { it.key.name == "ContentDescription" || it.key.name == "Text" }.joinToString { "${it.key.name}=${it.value}" }
    }

    @Test
    fun anEpisodeIsKeptFromItsRowOrItsOwnPageAndTheShowIsNotCalledDownloaded() {
        val downloads = DownloadsWorld()
        screen(downloads.world, downloads.platform) { app ->
            awaitText("Recently Added")
            with(downloads) { begin(app) }
            val watched = downloads.world.library.watchState(downloads.episode(1, 2))
            runOnIdle { app.navigator.open(Destination.Details(downloads.show)) }
            await("a mark beside the episodes, and the season's action") { has("Download S1 · E1") && has("Download S1 · E2") && shows("Download season") }
            save("downloads-show-before")

            // From the row: what would be kept, for this episode alone, and nothing until it is confirmed.
            onNodeWithContentDescription("Download S1 · E2", substring = true).performScrollTo().performClick()
            awaitText("Watch offline")
            await("the plan") { shows("Half Loop") && shows("from Living Room") && shows("Slow Horses  ·  S1 · E2") }
            assertFalse(shows("Next episode"), "an episode the user chose is not called the next one")
            assertTrue(app.library.downloads().isEmpty())
            save("downloads-episode-review")
            onNodeWithText("Download").performClick()
            await("the episode to be kept", { focused() }) { has("S1 · E2 · Half Loop is on this device") }

            // One episode is not the show.
            await("the show's own line") { shows("1 of 6 episodes on this device") }
            assertFalse(has("Downloaded"), "the show is not downloaded because one episode is")
            assertTrue(has("Download"))
            await("the season to say how much of it is") { shows("1 of 4 episodes on this device") }
            save("downloads-show-one-episode")

            // From an episode's own page: the same action as a film's.
            runOnIdle { app.navigator.open(Destination.Details(downloads.episode(1, 3))) }
            awaitText("All episodes")
            await("the action") { onAllNodesWithContentDescription("Download").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithContentDescription("Download").performClick()
            await("the plan") { shows("In Perpetuity") && shows("from Living Room") }
            onNodeWithText("Download").performClick()
            await("the copy to be here") { onAllNodesWithContentDescription("Downloaded").fetchSemanticsNodes().isNotEmpty() && shows("On this device") }
            save("downloads-episode-page")

            // Downloading watched nothing and moved nothing.
            assertEquals(watched, downloads.world.library.watchState(downloads.episode(1, 2)))
            assertEquals(2, app.library.downloads().count { it.state == DownloadState.COMPLETED })
        }
    }

    @Test
    fun aWholeSeasonIsKeptAndTheShowSaysHowMuchOfItIs() {
        val downloads = DownloadsWorld()
        screen(downloads.world, downloads.platform) { app ->
            awaitText("Recently Added")
            with(downloads) { begin(app) }
            val pilot = downloads.episode(1, 1)
            runOnIdle {
                val version = app.library.versions(pilot).single().version
                app.library.stopped(pilot, 20 * MINUTE, version.stream.durationMs, version)
                app.invalidate()
            }
            val before = (1..4).associateWith { app.library.watchState(downloads.episode(1, it)) }
            // The pilot is kept already.
            runBlocking { downloads.offline.enqueue(downloads.offline.planFor(listOf(pilot))) }
            await("the pilot to be kept") { app.library.downloads().singleOrNull()?.state == DownloadState.COMPLETED }

            runOnIdle { app.navigator.open(Destination.Details(downloads.show)) }
            awaitText("Download season")
            await("the season's line") { shows("1 of 4 episodes on this device") }
            onNodeWithText("Download season").performScrollTo().performClick()
            awaitText("Watch offline")
            // The rest of the season, each episode by its own name, and the one that is here among the downloads.
            await("the plan") { shows("Slow Horses  ·  Season 1") && shows("Download 3") }
            assertTrue(shows("S1 · E2 · Half Loop") && shows("S1 · E3 · In Perpetuity") && shows("S1 · E4 · The You You Are"))
            assertTrue(shows("3.5 GB needed"), "what it costs")
            save("downloads-season-review")
            onNodeWithText("Download 3").performClick()
            await("the season to be kept") { shows("Season on this device") }
            assertEquals(4, app.library.downloads().size, "nothing was downloaded twice")

            // Four of six episodes is not the show.
            await("the show's own line") { shows("4 of 6 episodes on this device") }
            assertFalse(has("Downloaded"))
            save("downloads-season-kept")

            // The other season, and then the show is on the device.
            onNodeWithText("Season 2").performScrollTo().performClick()
            await("the second season") { shows("Download season") }
            onNodeWithText("Download season").performScrollTo().performClick()
            await("the plan") { shows("Download 2") }
            onNodeWithText("Download 2").performClick()
            await("the show to be kept") { shows("All 6 episodes on this device") && onAllNodesWithContentDescription("Downloaded").fetchSemanticsNodes().isNotEmpty() }
            save("downloads-show-kept")

            // A season goes the same way, once, and takes nothing that was watched with it.
            onNodeWithText("Season 1").performScrollTo().performClick()
            await("the first season") { shows("Season on this device") }
            onNodeWithText("Season on this device").performScrollTo().performClick()
            awaitText("Remove all from this device")
            onNodeWithText("Remove all from this device").performClick()
            await("the question") { shows("Remove these 4 episodes from this device?") }
            onNodeWithText("Remove all").performClick()
            await("the season's copies to be gone") { app.library.downloads().size == 2 }
            assertEquals(before, (1..4).associateWith { app.library.watchState(downloads.episode(1, it)) })
            assertEquals(6, app.library.showDetail(downloads.show)!!.seasons.sumOf { it.episodes.size }, "every episode stays in the library")
        }
    }

    @Test
    fun theDownloadsPageListsEveryCopyWithWhatCanBeDoneAboutIt() {
        val downloads = DownloadsWorld()
        screen(downloads.world, downloads.platform) { app ->
            awaitText("Recently Added")
            with(downloads) { begin(app) }

            // Reached from the navigation, as a page of its own; empty, it says how to fill it.
            onNodeWithContentDescription("Downloads").performClick()
            awaitText("Nothing is downloaded. Use Download on a movie, an episode, or a season to keep it on this device and watch it without its source.")
            assertEquals(Destination.Downloads, app.navigator.current)
            save("downloads-page-empty")

            // A film on its way, an episode that will not come, and an episode that does.
            downloads.server.holds["ronin"] = CompletableDeferred()
            downloads.server.failing += "slow-horses-1-3"
            runBlocking {
                with(downloads.offline) {
                    enqueue(planFor(listOf(downloads.ronin)))
                    enqueue(planFor(listOf(downloads.episode(1, 3))))
                    enqueue(planFor(listOf(downloads.episode(1, 1))))
                }
            }
            await("the film under way") { shows("Downloading") && has("Downloads, downloading") && onAllNodesWithContentDescription("Pause the download of Ronin, 1998").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(shows("1080p  ·  64 KB  ·  from Living Room"), "which version, how large, and from where")
            save("downloads-page-active")

            // Paused, it waits, and the others go ahead.
            onNodeWithContentDescription("Pause the download of Ronin, 1998").performClick()
            await("the episodes to have their turn") { shows("Download paused") && shows("Didn't finish") && shows("On this device") }
            assertTrue(shows("Waiting for its source to come back"))
            assertTrue(shows("Good News About Hell"), "an episode is named by its show, number, and title")
            save("downloads-page-states")

            // Tried again once its source hands it out.
            downloads.server.failing.clear()
            onNodeWithContentDescription("Try the download of Slow Horses, S1 · E3 · In Perpetuity again").performClick()
            await("the episode to be kept") { app.library.downloads().count { it.state == DownloadState.COMPLETED } == 2 }
            await("nothing left that did not finish") { !shows("Didn't finish") }

            // Continued, the film finishes from where it was.
            downloads.server.release()
            onNodeWithContentDescription("Continue the download of Ronin, 1998").performClick()
            await("every copy to be here") { app.library.downloads().all { it.state == DownloadState.COMPLETED } && !shows("Downloading") }
            assertTrue(shows("3 on this device"))
            save("downloads-page-kept")

            // Removing asks once, and takes only the copy.
            onNodeWithContentDescription("Remove Ronin, 1998 from this device").performScrollTo().performClick()
            await("the question") { shows("Only the copy goes.") }
            onNodeWithText("Remove").performClick()
            await("the copy to be gone") { app.library.downloads().size == 2 }
            assertTrue(app.library.movies().any { it.item.id == downloads.ronin })
            // The page has let its line go before the next one is selected (the lines below it move up).
            await("its line to be gone") { !shows("Ronin") }
            waitForIdle()

            // A line leads to its work's page; Back comes back here.
            onNode(hasText("Good News About Hell", substring = true) and hasClickAction()).performClick()
            awaitText("All episodes")
            assertEquals(Destination.Details(downloads.episode(1, 1)), app.navigator.current)
            app.navigator.back()
            awaitText("Downloads")
            // Selecting Downloads again from there leaves it.
            onNodeWithContentDescription("Downloads").performClick()
            await("the page to be left") { app.navigator.current != Destination.Downloads }
        }
    }

    @Test
    fun itFitsUnderAThumb() {
        val downloads = DownloadsWorld()
        screen(downloads.world, downloads.platform, width = 412, height = 915) { app ->
            awaitText("Recently Added")
            with(downloads) { begin(app) }
            runOnIdle { app.navigator.open(Destination.Details(downloads.show)) }
            await("the season's action") { shows("Download season") }
            // The season's action beneath its tabs, the mark at the end of each row, all within the screen.
            val season = onNodeWithText("Download season").performScrollTo().fetchSemanticsNode().boundsInRoot
            assertTrue(season.left >= 0f && season.right <= 412f, "within the screen: $season")
            await("the marks") { has("Download S1 · E1") }
            val mark = onNodeWithContentDescription("Download S1 · E1", substring = true).performScrollTo().fetchSemanticsNode().boundsInRoot
            assertTrue(mark.left >= 0f && mark.right <= 412f, "within the screen: $mark")
            save("downloads-phone-show")
            onNodeWithText("Download season").performScrollTo().performTouchInput { click() }
            await("the plan") { shows("Download 4") }
            onNodeWithText("Download 4").performTouchInput { click() }
            await("the season to be kept") { app.library.downloads().count { it.state == DownloadState.COMPLETED } == 4 }

            // The page, from the top of a section.
            runOnIdle { app.navigator.show(Destination.Home) }
            await("the way to the downloads") { has("Downloads") }
            onNodeWithContentDescription("Downloads").performTouchInput { click() }
            awaitText("On this device")
            val remove = onNodeWithContentDescription("Remove Slow Horses, S1 · E1 · Good News About Hell from this device").fetchSemanticsNode().boundsInRoot
            assertTrue(remove.right <= 412f, "the action is on the screen: $remove")
            save("downloads-phone-page")
        }
    }

    @Test
    fun aRemoteReachesEveryDownloadControlAndFocusComesBack() {
        val downloads = DownloadsWorld()
        television(downloads.world, downloads.tvPlatform()) { app ->
            awaitText("Recently Added")
            with(downloads) { begin(app) }
            runOnIdle { app.navigator.open(Destination.Details(downloads.show)) }
            await("focus on the page", { focused() }) { onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() && has("Download S1 · E1") }

            // Down to the pilot's row, then a step to the right to its mark.
            var steps = 0
            while (onAllNodes(isFocused() and hasText("Good News About Hell", substring = true)).fetchSemanticsNodes().isEmpty() && steps++ < 8) remote(app, Key.ARROW_DOWN)
            await("focus on the pilot", { focused() }) { onAllNodes(isFocused() and hasText("Good News About Hell", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            remote(app, Key.ARROW_RIGHT)
            await("focus on its mark", { focused() }) { onAllNodes(isFocused() and hasContentDescription("Download S1 · E1", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            // The mark's focus never sits on the episode's picture, title, or text.
            val row = onNode(hasText("Good News About Hell", substring = true) and hasClickAction()).fetchSemanticsNode().boundsInRoot
            val mark = onNodeWithContentDescription("Download S1 · E1", substring = true).fetchSemanticsNode().boundsInRoot
            assertTrue(mark.left - row.right >= 20f, "a gap for the focus ring between $row and $mark")
            assertTrue(mark.top >= row.top - 1f && mark.bottom <= row.bottom + 1f, "beside the row, not over another: $mark by $row")
            save("downloads-tv-mark")

            // OK: the plan, with focus on its confirmation; OK again keeps it, and focus comes back to the mark.
            downloads.server.holds["slow-horses-1-1"] = CompletableDeferred()
            remote(app, Key.DPAD_CENTER)
            awaitText("Not now")
            await("focus on Download in the sheet", { focused() }) { onAllNodes(isFocused() and hasText("Download")).fetchSemanticsNodes().isNotEmpty() }
            save("downloads-tv-review")
            remote(app, Key.DPAD_CENTER)
            await("focus back on the mark while it downloads", { focused() }) {
                onAllNodes(isFocused() and hasContentDescription("Downloading S1 · E1", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            save("downloads-tv-downloading")
            downloads.server.release()
            await("the pilot to be kept", { focused() }) { onAllNodes(isFocused() and hasContentDescription("is on this device", substring = true)).fetchSemanticsNodes().isNotEmpty() }

            // Up from the mark is the season's action, over the marks; down again is the mark.
            remote(app, Key.ARROW_UP)
            await("focus on the season's action", { focused() }) { onAllNodes(isFocused() and hasText("Download season")).fetchSemanticsNodes().isNotEmpty() }
            remote(app, Key.ARROW_DOWN)
            await("focus on the mark again", { focused() }) { onAllNodes(isFocused() and hasContentDescription("is on this device", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            remote(app, Key.ARROW_UP)
            await("focus on the season's action", { focused() }) { onAllNodes(isFocused() and hasText("Download season")).fetchSemanticsNodes().isNotEmpty() }
            // Left of it, across its line, are the seasons.
            steps = 0
            while (onAllNodes(isFocused() and hasText("Season 2")).fetchSemanticsNodes().isEmpty() && steps++ < 4) remote(app, Key.ARROW_LEFT)
            await("focus on a season's tab", { focused() }) { onAllNodes(isFocused() and hasText("Season 2")).fetchSemanticsNodes().isNotEmpty() }
            remote(app, Key.ARROW_RIGHT)
            await("focus on the season's action", { focused() }) { onAllNodes(isFocused() and hasText("Download season")).fetchSemanticsNodes().isNotEmpty() }
            save("downloads-tv-season")
            remote(app, Key.DPAD_CENTER)
            await("the plan, with focus on it", { focused() }) { onAllNodes(isFocused() and hasText("Download 3")).fetchSemanticsNodes().isNotEmpty() }
            // Back closes the sheet without keeping anything, and focus is where it was.
            remote(app, Key.BACK)
            await("the sheet to close") { !app.offlineOpen }
            await("focus back on the season's action", { focused() }) { onAllNodes(isFocused() and hasText("Download season")).fetchSemanticsNodes().isNotEmpty() }
            assertEquals(1, app.library.downloads().size)

            // The Downloads page starts on its first copy; its actions are a step to the right.
            runBlocking { downloads.offline.enqueue(downloads.offline.planFor(listOf(downloads.episode(1, 2)))) }
            await("a second copy") { app.library.downloads().count { it.state == DownloadState.COMPLETED } == 2 }
            runOnIdle { app.navigator.open(Destination.Downloads) }
            await("focus on the copy", { focused() }) { onAllNodes(isFocused() and hasText("Good News About Hell", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            remote(app, Key.ARROW_RIGHT)
            await("focus on its removal", { focused() }) { onAllNodes(isFocused() and hasContentDescription("from this device", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            val line = onNode(hasText("Good News About Hell", substring = true) and hasClickAction()).fetchSemanticsNode().boundsInRoot
            val removal = onNodeWithContentDescription("Good News About Hell from this device", substring = true).fetchSemanticsNode().boundsInRoot
            assertTrue(removal.left - line.right >= 20f, "a gap for the focus ring between $line and $removal")
            save("downloads-tv-page")
            // Asked, focus is on keeping it: one press too many does not remove it.
            remote(app, Key.DPAD_CENTER)
            await("focus on Keep", { focused() }) { onAllNodes(isFocused() and hasText("Keep")).fetchSemanticsNodes().isNotEmpty() }
            remote(app, Key.ARROW_LEFT)
            await("focus on Remove", { focused() }) { onAllNodes(isFocused() and hasText("Remove")).fetchSemanticsNodes().isNotEmpty() }
            remote(app, Key.DPAD_CENTER)
            await("the copy to be gone") { app.library.downloads().size == 1 }
            // Focus goes to the copy after it, not off the page.
            await("focus on the next copy", { focused() }) { onAllNodes(isFocused() and hasText("Half Loop", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            // Back is the show's page.
            remote(app, Key.BACK)
            await("the show again") { app.navigator.current == Destination.Details(downloads.show) }
        }
    }

    @Test
    fun aRemoteRemovesACopyFromTheSheetWithoutLosingFocus() {
        val downloads = DownloadsWorld()
        television(downloads.world, downloads.tvPlatform()) { app ->
            awaitText("Recently Added")
            with(downloads) { begin(app) }
            runBlocking { downloads.offline.enqueue(downloads.offline.planFor(listOf(downloads.episode(1, 1)))) }
            await("the pilot to be kept") { app.library.downloads().singleOrNull()?.state == DownloadState.COMPLETED }
            runOnIdle { app.navigator.open(Destination.Details(downloads.show)) }
            await("focus on the page", { focused() }) { onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() && has("is on this device") }
            var steps = 0
            while (onAllNodes(isFocused() and hasText("Good News About Hell", substring = true)).fetchSemanticsNodes().isEmpty() && steps++ < 8) remote(app, Key.ARROW_DOWN)
            remote(app, Key.ARROW_RIGHT)
            await("focus on the kept mark", { focused() }) { onAllNodes(isFocused() and hasContentDescription("is on this device", substring = true)).fetchSemanticsNodes().isNotEmpty() }

            // The sheet lists the copy; focus starts on Done, and the copy's removal is above it.
            remote(app, Key.DPAD_CENTER)
            await("focus on Done", { focused() }) { onAllNodes(isFocused() and hasText("Done")).fetchSemanticsNodes().isNotEmpty() }
            remote(app, Key.ARROW_UP)
            await("focus on the removal", { focused() }) { onAllNodes(isFocused() and hasContentDescription("from this device", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            remote(app, Key.DPAD_CENTER)
            // Asked, focus is on keeping it, inside the sheet; then on removing it.
            await("focus on Keep", { focused() }) { onAllNodes(isFocused() and hasText("Keep")).fetchSemanticsNodes().isNotEmpty() }
            save("downloads-tv-sheet-remove")
            remote(app, Key.ARROW_LEFT)
            await("focus on Remove", { focused() }) { onAllNodes(isFocused() and hasText("Remove")).fetchSemanticsNodes().isNotEmpty() }
            remote(app, Key.DPAD_CENTER)
            await("the copy to be gone") { app.library.downloads().isEmpty() }
            // The sheet stays, now proposing to keep it again, with focus on that.
            await("focus on Download in the sheet", { focused() }) { onAllNodes(isFocused() and hasText("Download")).fetchSemanticsNodes().isNotEmpty() }
            assertTrue(app.offlineOpen)
            remote(app, Key.BACK)
            await("focus back on the mark", { focused() }) { onAllNodes(isFocused() and hasContentDescription("Download S1 · E1", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        }
    }
}
