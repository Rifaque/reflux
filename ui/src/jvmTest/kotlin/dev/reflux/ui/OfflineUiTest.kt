package dev.reflux.ui

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import dev.reflux.core.identify.ParsedKind
import dev.reflux.core.identify.ParsedMedia
import dev.reflux.core.input.Key
import dev.reflux.core.model.Availability
import dev.reflux.core.model.StableIds
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
import dev.reflux.library.DownloadReceiver
import dev.reflux.library.DownloadTransport
import dev.reflux.library.DownloadsSource
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

/** A server that hands out its files, with two films nobody has on this device. */
private class Server : CatalogSource, DownloadableSource {
    var reachable = true
    val files = HashMap<String, ByteArray>()

    /** Completed by the test to let a held transfer finish. */
    var hold: CompletableDeferred<Unit>? = null

    override val descriptor = SourceDescriptor(
        StableIds.sourceId("server", "home"), "server", "Home Server", SourceLocality.LOCAL_NETWORK,
        setOf(SourceCapability.PROVIDES_CATALOG, SourceCapability.DOWNLOAD),
    )

    private fun film(title: String, year: Int, minutes: Long, megabytes: Int): CatalogEntry {
        val path = "items/${title.lowercase()}"
        files["server://$path"] = ByteArray(64 * 1024) { it.toByte() }
        val stream = StreamInfo(Container.MATROSKA, VideoStream(VideoCodec.H264, 1920, 1080, 8), listOf(AudioStream(AudioCodec.AAC, 2, "eng")), durationMs = minutes * MINUTE)
        // The catalog says what the file weighs; the stand-in content is small so that tests are quick.
        return CatalogEntry(ParsedMedia(ParsedKind.MOVIE, title, year), listOf(CatalogVersion(path, megabytes * 1024L * 1024, 1, stream)))
    }

    private val entries = listOf(film("Ronin", 1998, 122, 4_300), film("Sicario", 2015, 121, 6_200))

    override suspend fun availability() = if (reachable) Availability.AVAILABLE else Availability.UNAVAILABLE
    override suspend fun playbackTarget(path: String) = PlaybackTarget("server://$path")
    override suspend fun downloadTarget(path: String) = playbackTarget(path)
    override fun catalog(): Flow<CatalogEntry> = if (reachable) entries.asFlow() else flow { throw SourceUnavailableException(descriptor.id) }

    val transport = object : DownloadTransport {
        override suspend fun fetch(target: PlaybackTarget, offset: Long, receiver: DownloadReceiver) {
            val bytes = files.getValue(target.uri)
            receiver.started(0, bytes.size.toLong())
            receiver.bytes(bytes, bytes.size / 2)
            hold?.await()
            receiver.bytes(bytes, bytes.size / 2)
        }
    }
}

/** The populated library, a server next to it, and Smart Offline over both. */
private class OfflineWorld {
    val world = populated()
    val server = Server()
    private val directory = Files.createTempDirectory("reflux-ui-offline").toFile().also { it.deleteOnExit() }
    val offline = Offline(
        world.library, FileDownloadStore(directory), server.transport, world.registry::resolve, { testDevice }, listOf("en"),
        world.registry::register, Dispatchers.Default,
    )
    val platform = FakePlatform(offline = offline)

    init {
        world.library.addSource(server, "home")
        world.registry.register(server)
        runBlocking { world.pipeline.refresh() }
    }

    /** As a shell does at start-up: the library is kept current, and copies are made. */
    @OptIn(ExperimentalTestApi::class)
    fun DesktopComposeUiTest.begin(app: AppState) {
        runOnIdle {
            app.start()
            offline.start(app.scope)
        }
        waitForIdle()
    }

    fun movie(title: String) = world.library.movies().first { it.item.title == title }.item.id
    fun tvPlatform() = FakePlatform(television = true, offline = offline)
}

/**
 * Keeping things for offline, as the user meets it: one action on a details page, one sheet, and no
 * question about versions, codecs, or sources anywhere.
 */
@OptIn(ExperimentalTestApi::class)
class OfflineUiTest {
    private fun DesktopComposeUiTest.shows(text: String) = onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    private fun DesktopComposeUiTest.has(description: String) = onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aFilmIsKeptWithOneActionAndSaysSoQuietly() {
        val offline = OfflineWorld()
        screen(offline.world, offline.platform) { app ->
            awaitText("Recently Added")
            with(offline) { begin(app) }

            // A film that is a file on this device has nothing to download.
            runOnIdle { app.navigator.open(Destination.Details(offline.movie("Heat"))) }
            awaitText("Versions")
            assertFalse(has("Download"))

            // One on the server does. Nothing is asked: the version is Reflux's to choose.
            runOnIdle { app.navigator.back(); app.navigator.open(Destination.Details(offline.movie("Ronin"))) }
            awaitText("Ronin")
            await("the action") { has("Download") }
            save("offline-details-before")
            offline.server.hold = CompletableDeferred()
            onNodeWithContentDescription("Download").performClick()
            // What would be kept is shown first, and nothing happens until the user says so.
            awaitText("Preparing the best offline version for this device.")
            await("the plan") { shows("1080p") && shows("from Home Server") && shows("available") }
            assertTrue(app.library.downloads().isEmpty())
            save("offline-review")
            onNodeWithText("Download").performClick()
            await("the sheet to close") { !app.offlineOpen }
            await("the download to be under way") { has("Downloading") && shows("Downloading") }
            save("offline-details-downloading")
            assertEquals(1, app.library.downloads().size)

            offline.server.hold?.complete(Unit)
            await("the copy to be here") { has("Downloaded") && shows("On this device") }
            // It is one more version of the same film, and the one that will play.
            awaitText("Downloaded")
            assertEquals(1, app.library.movies().count { it.item.title == "Ronin" })
            assertTrue(shows("Recommended"))
            save("offline-details-downloaded")

            // With the server away the film is still there, and still plays.
            offline.server.reachable = false
            runOnIdle { app.refresh() }
            await("the server to be seen as away") { shows("Not connected") }
            onNodeWithText("Play").performClick()
            await("playback from the device") { app.playback != null && offline.platform.players.isNotEmpty() }
            assertTrue(offline.platform.players.single().requests.single().target.uri.startsWith("file:"))
        }
    }

    @Test
    fun theSheetProposesWhatIsBeingWatchedAndKeepsNoSecretsAboutRoom() {
        val offline = OfflineWorld()
        screen(offline.world, offline.platform) { app ->
            awaitText("Recently Added")
            with(offline) { begin(app) }
            val ronin = offline.movie("Ronin")
            runOnIdle {
                val version = app.library.versions(ronin).single().version
                app.library.stopped(ronin, 40 * MINUTE, version.stream.durationMs, version)
                app.invalidate()
            }

            // Reached from the Downloads page, which the media sources lead to as well.
            onNodeWithContentDescription("Media sources").performClick()
            awaitText("Add media")
            onNodeWithText("Downloads").performClick()
            awaitText("Keep what you're watching")
            onNodeWithText("Keep what you're watching").performClick()
            awaitText("Ready to keep")
            assertTrue(shows("Ronin") && shows("Continue watching"))
            assertTrue(shows("4.2 GB needed") && shows("available"), "what it costs and what there is")
            assertFalse(shows("H.264"), "nothing technical is asked or told")
            save("offline-sheet-proposal")

            onNodeWithText("Download").performClick()
            await("the copy to be here") { app.library.downloads().singleOrNull()?.state == DownloadState.COMPLETED }
            awaitText("On this device")
            // What is kept is no longer proposed, and the page says it is here.
            await("the proposal to be gone") { !shows("Keep what you're watching") }
            save("offline-sheet-downloaded")

            // Removing asks once, says what goes and what stays, and takes only the copy.
            onNodeWithContentDescription("Remove Ronin, 1998 from this device").performClick()
            await("the question") { shows("Only the copy goes.") }
            save("offline-sheet-remove")
            onNodeWithText("Remove").performClick()
            await("the copy to be gone") { app.library.downloads().isEmpty() }
            assertEquals(40 * MINUTE, app.library.watchState(ronin)?.positionMs)
            assertTrue(app.library.movies().any { it.item.id == ronin })
        }
    }

    @Test
    fun whereNothingCanBeKeptNothingIsOffered() = screen(populated()) { app ->
        // No platform support for it (as on the desktop today): no action, no sheet, no entry.
        awaitText("Recently Added")
        runOnIdle { app.navigator.open(Destination.Details(app.library.movies().first { it.item.title == "Heat" }.item.id)) }
        awaitText("Versions")
        assertFalse(has("Download"))
        onNodeWithContentDescription("Media sources").performClick()
        awaitText("Add media")
        assertFalse(shows("Downloads"))
        assertFalse(has("Downloads"))
    }

    @Test
    fun itWorksUnderAThumbAndWithARemote() {
        val phone = OfflineWorld()
        screen(phone.world, phone.platform, width = 412, height = 915) { app ->
            awaitText("Recently Added")
            with(phone) { begin(app) }
            runOnIdle { app.navigator.open(Destination.Details(phone.movie("Sicario"))) }
            await("the action") { has("Download") }
            onNodeWithContentDescription("Download").performTouchInput { click() }
            awaitText("Not now")
            save("offline-phone-review")
            onNodeWithText("Download").performTouchInput { click() }
            await("the copy to be here") { has("Downloaded") }
            save("offline-phone-details")
            onNodeWithContentDescription("Downloaded").performTouchInput { click() }
            await("the sheet", { "open=${app.offlineOpen} review=${app.offlineReview}" }) { onAllNodesWithText("Watch offline").fetchSemanticsNodes().isNotEmpty() }
            val sheet = onNodeWithText("Watch offline").fetchSemanticsNode().boundsInRoot
            assertTrue(sheet.left >= 0f && sheet.right <= 412f)
            // A notice said while the sheet is open is said above it, never over the sheet's own buttons.
            runOnIdle { app.notify("A notice while the sheet is open.") }
            awaitText("A notice while the sheet is open.")
            val notice = onNodeWithText("A notice while the sheet is open.").fetchSemanticsNode().boundsInRoot
            assertTrue(notice.bottom < sheet.top, "the notice $notice is above the sheet $sheet")
            save("offline-phone-sheet")
        }

        val tv = OfflineWorld()
        television(tv.world, tv.tvPlatform()) { app ->
            awaitText("Recently Added")
            with(tv) { begin(app) }
            runOnIdle { app.navigator.open(Destination.Details(tv.movie("Ronin"))) }
            await("focus on Play") { onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() && has("Download") }
            // Along the actions to Download, and OK: the same one step.
            remote(app, Key.ARROW_RIGHT, 3)
            await("focus on Download") { onAllNodes(isFocused() and hasContentDescription("Download")).fetchSemanticsNodes().isNotEmpty() }
            remote(app, Key.DPAD_CENTER)
            // The plan, with focus on its confirmation: OK again is "yes, keep it".
            awaitText("Not now")
            await("focus on Download in the sheet") { onAllNodes(isFocused() and hasText("Download")).fetchSemanticsNodes().isNotEmpty() }
            save("offline-tv-review")
            tv.server.hold = CompletableDeferred()
            remote(app, Key.DPAD_CENTER)
            await("focus back on the action while it downloads", { onAllNodes(isFocused()).fetchSemanticsNodes().map { it.config.filter { e -> e.key.name == "ContentDescription" || e.key.name == "Text" }.joinToString { e -> e.key.name + "=" + e.value } + " offlineOpen=" + app.offlineOpen + " " + it.boundsInRoot } }) {
                onAllNodes(isFocused() and hasContentDescription("Downloading")).fetchSemanticsNodes().isNotEmpty()
            }
            tv.server.hold?.complete(Unit)
            await("the copy to be here") { has("Downloaded") }
            // Focus is back on the action that opened the sheet, not sent to the top of the page.
            await("focus back on the action", { onAllNodes(isFocused()).fetchSemanticsNodes().map { it.config.toString() } }) {
                onAllNodes(isFocused() and hasContentDescription("Downloaded")).fetchSemanticsNodes().isNotEmpty()
            }
            save("offline-tv-details")
            // OK on it again opens the sheet, with focus inside; Back closes it.
            remote(app, Key.DPAD_CENTER)
            awaitText("Watch offline")
            await("focus in the sheet") { onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
            save("offline-tv-sheet")
            remote(app, Key.BACK)
            await("the sheet to close") { !app.offlineOpen }
            assertTrue(app.navigator.current is Destination.Details)
        }
    }
}
