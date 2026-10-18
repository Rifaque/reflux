package dev.reflux.desktop.engine

import dev.reflux.core.model.Availability
import dev.reflux.core.model.StableIds
import dev.reflux.core.offline.DownloadState
import dev.reflux.core.offline.OfflineSkipReason
import dev.reflux.core.source.DownloadableSource
import dev.reflux.core.source.FileEnumeratingSource
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.core.source.SourceCapability
import dev.reflux.core.source.SourceDescriptor
import dev.reflux.core.source.SourceFile
import dev.reflux.core.source.SourceLocality
import dev.reflux.library.DownloadsSource
import dev.reflux.library.downloads
import dev.reflux.library.offlineCoverage
import dev.reflux.library.planPlayback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A share whose files are on this machine, handed out as they are: a stand-in for SMB or WebDAV. */
private class NearbyShare(private val root: Path) : FileEnumeratingSource, DownloadableSource {
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
 * Smart Offline on the desktop: the same engine as on Android, wired to Reflux's own data directory, making
 * copies while Reflux runs, and finding them again at the next launch.
 */
class DesktopOfflineTest {
    private val root: Path = Files.createTempDirectory("reflux-desktop-offline")
    private val data: Path = root.resolve("data")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun cleanUp() {
        scope.cancel()
        root.toFile().deleteRecursively()
    }

    private fun media(relative: String, seed: Int): Path =
        root.resolve("share").resolve(relative).also { it.parent.createDirectories() }.also { it.writeBytes(ByteArray(256 * 1024) { index -> (index * 7 + seed).toByte() }) }

    @Test
    fun aSeasonIsKeptInTheDataDirectoryAndPlaysFromThereAfterTheNextLaunch() = runBlocking {
        val first = media("Dark/Season 1/Dark.S01E01.mkv", 1)
        media("Dark/Season 1/Dark.S01E02.mkv", 2)
        val share = NearbyShare(root.resolve("share"))
        val environment = DesktopEnvironment(data)
        environment.library.addSource(share, "share")
        environment.registry.register(share)
        environment.library.scan(share)
        val season = assertNotNull(environment.library.showDetail(environment.library.shows().single().item.id)).seasons.single()
        val episodes = season.episodes.map { it.item.id }

        // Set up and running as the shell does it.
        assertTrue(environment.offline.possible())
        environment.startOffline(scope)
        val plan = environment.offline.planFor(listOf(season.season.id))
        assertEquals(episodes, plan.downloads.map { it.itemId })
        environment.offline.enqueue(plan)
        withTimeout(20_000) {
            while (!environment.library.downloads().all { it.state == DownloadState.COMPLETED }) delay(50)
        }

        // The copies are Reflux's own, inside its data directory, byte for byte.
        val downloads = data.resolve("downloads").toFile().canonicalFile
        val copy = environment.library.downloads().single { it.itemId == episodes[0] }
        val file = downloads.resolve(copy.path)
        assertTrue(file.canonicalPath.startsWith(downloads.path + File.separator))
        assertContentEquals(Files.readAllBytes(first), file.readBytes())
        assertTrue(environment.library.offlineCoverage(season.season.id).complete)

        // The next launch: the share is not there (its kind is not even known to this run), the copies are.
        val next = DesktopEnvironment(data)
        assertNotNull(next.source(DownloadsSource.ID), "the downloads source is recreated from what was persisted")
        assertEquals(2, next.library.downloads().count { it.state == DownloadState.COMPLETED })
        for (episode in episodes) {
            val playback = assertNotNull(next.library.planPlayback(episode, next.deviceCapabilities(), next::source), "plays without its share")
            assertEquals(DownloadsSource.ID, playback.version.version.location.sourceId)
            assertTrue(File(URI(playback.request.target.uri)).canonicalPath.startsWith(downloads.path + File.separator))
        }
        // And nothing of it is downloaded again.
        assertEquals(setOf(OfflineSkipReason.ALREADY_ON_DEVICE), next.offline.planFor(listOf(season.season.id)).skipped.map { it.reason }.toSet())
    }
}
