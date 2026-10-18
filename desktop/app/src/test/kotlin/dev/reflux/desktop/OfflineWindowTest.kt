package dev.reflux.desktop

import dev.reflux.core.model.Availability
import dev.reflux.core.model.StableIds
import dev.reflux.core.offline.DownloadState
import dev.reflux.core.source.DownloadableSource
import dev.reflux.core.source.FileEnumeratingSource
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.core.source.SourceCapability
import dev.reflux.core.source.SourceDescriptor
import dev.reflux.core.source.SourceFile
import dev.reflux.core.source.SourceLocality
import dev.reflux.desktop.engine.DesktopEnvironment
import dev.reflux.library.DownloadsSource
import dev.reflux.library.downloads
import dev.reflux.library.planPlayback
import dev.reflux.ui.state.Destination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A share whose files are on this machine, handed out as they are, that can be made to go away. */
private class NearbyShare(private val root: Path) : FileEnumeratingSource, DownloadableSource {
    @Volatile
    var reachable = true

    override val descriptor = SourceDescriptor(
        StableIds.sourceId("nearby", root.toString()), "nearby", "Nearby", SourceLocality.LOCAL_NETWORK,
        setOf(SourceCapability.ENUMERATE_FILES, SourceCapability.DOWNLOAD),
    )

    override suspend fun availability() = if (reachable) Availability.AVAILABLE else Availability.UNAVAILABLE
    override suspend fun playbackTarget(path: String) = PlaybackTarget(root.resolve(path).toUri().toString())
    override suspend fun downloadTarget(path: String) = playbackTarget(path)
    override fun files(): Flow<SourceFile> = Files.walk(root).use { paths ->
        paths.filter { Files.isRegularFile(it) }.map { SourceFile(root.relativize(it).toString().replace(File.separatorChar, '/'), Files.size(it), 1) }.toList()
    }.asFlow()
}

/**
 * Smart Offline in the real desktop window: a season kept from its show's page with the mouse, the Downloads
 * page, and an episode played by libmpv from its copy with its share away.
 *
 * It opens windows, so it only runs on request: `./gradlew :desktop:app:test -Preflux.windowTests=true`.
 */
class OfflineWindowTest {
    private val root: Path = Files.createTempDirectory("reflux-offline-window")

    @Test
    fun aSeasonIsKeptAndPlaysFromItsCopyInTheRealWindow() {
        assumeTrue("window tests not requested", windowTestsRequested)
        assumeTrue("libmpv and ffmpeg are needed", needs("libmpv and ffmpeg", SampleMedia.available))
        val files = root.resolve("Share")
        val first = files.resolve("Harbour Lights/Season 1/Harbour.Lights.S01E01.mkv")
        SampleMedia.video(first, seconds = 20)
        Files.copy(first, files.resolve("Harbour Lights/Season 1/Harbour.Lights.S01E02.mkv"))
        val share = NearbyShare(files)
        val environment = DesktopEnvironment(root.resolve("data"))
        environment.library.addSource(share, "share")
        environment.registry.register(share)
        runBlocking { environment.pipeline.refresh() }
        val episodes = environment.library.showDetail(environment.library.shows().single().item.id)!!.seasons.single().episodes.map { it.item.id }
        try {
            Shell(environment).use { shell ->
                val app = shell.app
                shell.awaitShown("Recently Added")

                // The show's page: a mark beside each episode and the season's action.
                shell.click("Shows")
                shell.click("Harbour Lights")
                shell.awaitShown("Download season")
                assertTrue(shell.shows("Download S1 · E1", exact = false))
                shell.capture("window-offline-show")
                shell.click("Download season")
                shell.awaitShown("Download 2")
                shell.capture("window-offline-review")
                shell.click("Download 2")
                shell.awaitShown("Season on this device")
                assertTrue(environment.library.downloads().all { it.state == DownloadState.COMPLETED })
                assertTrue(shell.shows("Downloaded"), "every episode of the show is kept, so the show is")

                // The page, from the navigation.
                shell.click("Downloads")
                shell.awaitShown("On this device")
                assertEquals(Destination.Downloads, shell.ui { app.navigator.current })
                shell.capture("window-offline-downloads")

                // The share goes away; the episode plays from its copy, kept in Reflux's data directory.
                share.reachable = false
                shell.ui { app.refresh() }
                shell.await("the share to be seen as away") { environment.library.sources().first { it.id == share.descriptor.id }.availability == Availability.UNAVAILABLE }
                val plan = assertNotNull(runBlocking { environment.library.planPlayback(episodes[0], environment.deviceCapabilities(), environment::source) })
                assertEquals(DownloadsSource.ID, plan.version.version.location.sourceId)
                val downloads = root.resolve("data/downloads").toFile().canonicalPath
                assertTrue(File(URI(plan.request.target.uri)).canonicalPath.startsWith(downloads))
                shell.click("S1 · E1")
                shell.awaitShown("All episodes")
                shell.click("Play")
                shell.await("playback from the copy", state = { "Player: ${app.playback?.state?.value}" }) { (app.playback?.state?.value?.positionMs ?: 0) > 1_500 }
                shell.capture("window-offline-playing")
                shell.ui { app.playback?.close() }
                shell.await("the player to close") { app.playback == null }

                // Removing a copy from the page takes only the copy.
                shell.click("Downloads")
                shell.awaitShown("On this device")
                shell.click("Remove Harbour Lights, S1 · E2 from this device")
                shell.awaitShown("Remove")
                shell.click("Remove")
                shell.await("the copy to be gone") { environment.library.downloads().size == 1 }
                assertEquals(2, environment.library.showDetail(environment.library.shows().single().item.id)!!.seasons.single().episodes.size)
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
