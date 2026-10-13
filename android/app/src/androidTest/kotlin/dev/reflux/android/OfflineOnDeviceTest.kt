package dev.reflux.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.reflux.android.engine.AndroidEnvironment
import dev.reflux.android.playback.Media3Player
import dev.reflux.core.model.Availability
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.StableIds
import dev.reflux.core.offline.DownloadState
import dev.reflux.core.playback.PlaybackController
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.playback.TrackPreferences
import dev.reflux.core.source.DownloadableSource
import dev.reflux.core.source.FileEnumeratingSource
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.core.source.SourceCapability
import dev.reflux.core.source.SourceDescriptor
import dev.reflux.core.source.SourceFile
import dev.reflux.core.source.SourceLocality
import dev.reflux.core.source.SourceUnavailableException
import dev.reflux.core.watch.ResumeBasis
import dev.reflux.library.DownloadsSource
import dev.reflux.library.PlaybackPlan
import dev.reflux.library.SyncingWatchReporter
import dev.reflux.library.copyOf
import dev.reflux.library.downloads
import dev.reflux.library.planPlayback
import dev.reflux.sources.jellyfin.JellyfinSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A share on the network, stood in for by a folder: files Reflux identifies itself, handed out as they are. */
private class NetworkShare(private val root: File) : FileEnumeratingSource, DownloadableSource {
    var reachable = true
    override val descriptor = SourceDescriptor(
        StableIds.sourceId("share", root.path), "share", "NAS", SourceLocality.LOCAL_NETWORK,
        setOf(SourceCapability.ENUMERATE_FILES, SourceCapability.DOWNLOAD),
    )

    override suspend fun availability() = if (reachable) Availability.AVAILABLE else Availability.UNAVAILABLE
    override suspend fun playbackTarget(path: String) = PlaybackTarget(File(root, path).toURI().toString().replace("file:/", "file:///").replace("file:////", "file:///"))
    override suspend fun downloadTarget(path: String) = playbackTarget(path)
    override fun files(): Flow<SourceFile> = flow {
        if (!reachable) throw SourceUnavailableException(descriptor.id)
        root.walkTopDown().filter { it.isFile }.forEach { emit(SourceFile(it.relativeTo(root).invariantSeparatorsPath, it.length(), it.lastModified())) }
    }
}

/**
 * Smart Offline on a real Android runtime: the copy is written to the application's own storage, becomes a
 * version of the same work in Android's SQLite, plays in Media3 from where the viewer was, and is still
 * there after the application has been restarted and its source has gone.
 */
@RunWith(AndroidJUnit4::class)
class OfflineOnDeviceTest {
    private val directory = File(context.filesDir, "reflux-offline-${System.nanoTime()}").also { it.mkdirs() }
    private val remote = File(directory, "remote").also { it.mkdirs() }
    private val data = File(directory, "data").also { it.mkdirs() }
    private val database = "reflux-offline-${System.nanoTime()}.db"
    private val players = ArrayList<Media3Player>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun cleanUp() {
        players.forEach(Media3Player::close)
        scope.cancel()
        context.deleteDatabase(database)
        directory.deleteRecursively()
    }

    private fun AndroidEnvironment.film(title: String): MediaId = library.movies().single { it.item.title == title }.item.id

    private suspend fun AndroidEnvironment.awaitDownloads() = withTimeout(120_000) {
        while (library.downloads().any { it.active }) delay(100)
        library.downloads()
    }

    /** Plays [plan] on a real engine to about [untilMs], as a viewer would, and leaves. */
    private suspend fun AndroidEnvironment.watch(item: MediaId, plan: PlaybackPlan, untilMs: Long) {
        val player = Media3Player(context, capabilities).also { players += it }
        val controller = PlaybackController(player, item, SyncingWatchReporter(library, plan.version.version, plan.source, scope), TrackPreferences(listOf("en")))
        controller.start(scope, plan.request)
        player.await("playback to start", timeoutMs = 60_000) { it.status == PlayerStatus.PLAYING }
        player.seekTo(untilMs - 2_000)
        player.await("playback to reach ${untilMs / 1000} s", timeoutMs = 60_000) { it.status == PlayerStatus.PLAYING && it.positionMs >= untilMs }
        controller.stop()
        player.close()
    }

    @Test
    fun aCopyIsKeptPlaysWithoutItsSourceAndOutlivesARestart() = runBlocking {
        File(remote, "Movies/Night Drive (2021)").mkdirs()
        InstrumentationRegistry.getInstrumentation().context.assets.open("clip.mp4").use { input ->
            File(remote, "Movies/Night Drive (2021)/Night Drive (2021).mp4").outputStream().use(input::copyTo)
        }
        val environment = AndroidEnvironment(context, data, database)
        val share = NetworkShare(remote)
        environment.library.addSource(share, remote.path)
        environment.registry.register(share)
        environment.pipeline.refresh()
        val library = environment.library
        val film = environment.film("Night Drive")
        val sources = { id: dev.reflux.core.model.SourceId -> environment.registry.resolve(id) }

        // Watched from the share to a minute in.
        environment.watch(film, assertNotNull(library.planPlayback(film, environment.capabilities, sources)), untilMs = 60_000)
        val left = assertNotNull(library.watchState(film)).positionMs
        assertTrue(abs(left - 60_000) < 4_000)

        // Prepared for offline: the plan fits this device's real storage, and the copy arrives.
        environment.startOffline(scope)
        val plan = environment.offline.planFor(listOf(film))
        assertEquals(listOf(film), plan.downloads.map { it.itemId })
        assertTrue(plan.requiredBytes in 1..plan.usableBytes, "needs ${plan.requiredBytes} of ${plan.usableBytes} usable, ${plan.freeBytes} free")
        environment.offline.enqueue(plan)
        val download = environment.awaitDownloads().single()
        assertEquals(DownloadState.COMPLETED, download.state)
        assertEquals(File(remote, "Movies/Night Drive (2021)/Night Drive (2021).mp4").length(), download.bytesDownloaded)

        // One work, two versions; the copy is in the application's own storage and nowhere public.
        assertEquals(1, library.movies().size)
        val copy = assertNotNull(library.copyOf(download)).version
        val target = environment.offline.source.playbackTarget(copy.location.path).uri
        assertTrue(target.startsWith("file:///data/"), "private storage: $target")
        assertTrue(File(java.net.URI(target)).canonicalPath.startsWith(context.filesDir.canonicalPath))

        // The share is gone. The library is whole, and the film plays from the device, from where it was left.
        share.reachable = false
        environment.pipeline.refresh()
        assertEquals(Availability.AVAILABLE, library.movies().single().availability)
        val offline = assertNotNull(library.planPlayback(film, environment.capabilities, sources))
        assertEquals(DownloadsSource.ID, offline.version.version.location.sourceId)
        assertEquals(ResumeBasis.SAME_TIMELINE, offline.resume.basis)
        assertEquals(left, offline.resume.positionMs)
        environment.watch(film, offline, untilMs = 120_000)
        assertTrue(abs(assertNotNull(library.watchState(film)).positionMs - 120_000) < 4_000)

        // The application is restarted with the share still away: nothing has to be asked or granted again.
        val restarted = AndroidEnvironment(context, data, database)
        val again = assertNotNull(restarted.library.planPlayback(restarted.film("Night Drive"), restarted.capabilities, restarted.registry::resolve))
        assertEquals(DownloadsSource.ID, again.version.version.location.sourceId)
        assertTrue(abs(again.resume.positionMs!! - 120_000) < 4_000)
        val player = Media3Player(context, restarted.capabilities).also { players += it }
        player.load(again.request)
        val resumed = player.await("the copy to play after a restart", timeoutMs = 60_000) { it.status == PlayerStatus.PLAYING }
        assertTrue(abs(resumed.positionMs - 120_000) < 6_000, "resumed at ${resumed.positionMs}")
        player.close()

        // Removing the copy removes the file. With the share away the film cannot play, and it is still in the library.
        restarted.offline.remove(download.id)
        assertTrue(!File(java.net.URI(target)).exists())
        assertEquals(1, restarted.library.movies().size)
        assertEquals(Availability.UNAVAILABLE, restarted.library.movies().single().availability)
    }

    /**
     * The same against a real Jellyfin server, when one is named (`-Preflux.jellyfin=http://10.0.2.2:8096`):
     * the server's own file fetched over HTTP, played with the server out of reach, and the server told
     * where the viewer got to when it is back.
     */
    @Test
    fun aFilmFromARealJellyfinServerIsKeptAndPlaysWithoutIt() {
        val url = InstrumentationRegistry.getArguments().getString("refluxJellyfin").orEmpty()
        assumeTrue("no Jellyfin server named (-Preflux.jellyfin=<url>)", url.isNotBlank())
        runBlocking { keepFromJellyfin(url) }
    }

    /**
     * Not a check: a way in. The application has no screen for adding a server yet, so to walk the offline
     * flow by hand in the installed application, this connects it to the server named with
     * `-e refluxConnect <url>` (user reflux / reflux-test, as `tools/ci/jellyfin-test-server.sh` sets up).
     * The server is then part of the application's own library, as if the user had added it.
     */
    @Test
    fun connectTheInstalledApplicationToAServer() {
        val url = InstrumentationRegistry.getArguments().getString("refluxConnect").orEmpty()
        assumeTrue("no server named to connect the installed application to (-e refluxConnect <url>)", url.isNotBlank())
        runBlocking {
            val environment = (context.applicationContext as RefluxApplication).environment
            val credentials = JellyfinSource.connect(url, "reflux", "reflux-test", environment.http, deviceId = environment.deviceId)
            val server = JellyfinSource(credentials, environment.http, "Home Server")
            environment.library.addSource(server, credentials.encode())
            environment.registry.register(server)
            assertEquals(dev.reflux.library.ScanReport.Status.COMPLETED, environment.pipeline.refresh(server.descriptor.id).single().status)
        }
    }

    private suspend fun keepFromJellyfin(url: String) {
        val environment = AndroidEnvironment(context, data, database)
        val http = environment.http
        val server = JellyfinSource(JellyfinSource.connect(url, "reflux", "reflux-test", http, deviceId = "reflux-android-offline-test"), http, "Jellyfin")
        // The real server stays up; taking it out of reach is done where sources are resolved.
        var reachable = true
        environment.library.addSource(server, "live")
        val sources = { id: dev.reflux.core.model.SourceId -> if (id == server.descriptor.id) server.takeIf { reachable } else environment.registry.resolve(id) }
        environment.registry.register(server)
        val entry = server.catalog().toList().first { catalog -> catalog.versions.any { (it.stream.durationMs ?: 0) >= 35 * MINUTE } }
        val path = entry.versions.first().path
        server.setPlayed(path, false)
        environment.pipeline.refresh()
        val library = environment.library
        val film = environment.film("Night Drive")

        // Watched from the server's stream to 00:30:12.
        val leftAt = 30 * MINUTE + 12_000
        environment.watch(film, assertNotNull(library.planPlayback(film, environment.capabilities, sources)), leftAt)

        // Prepared for offline: the server's own file, whole.
        environment.startOffline(scope)
        val plan = environment.offline.planFor(listOf(film))
        assertEquals(server.descriptor.id, plan.downloads.single().version.location.sourceId)
        environment.offline.enqueue(plan)
        val download = environment.awaitDownloads().single()
        assertEquals(DownloadState.COMPLETED, download.state)
        assertEquals(entry.versions.first().sizeBytes, download.bytesDownloaded, "the file as the server has it")
        assertEquals(1, library.movies().size)
        assertEquals(2, library.versions(film).size)

        // The server is out of reach: the film plays from the device, from 00:30:12, and on to 00:33:00.
        reachable = false
        val away = assertNotNull(library.planPlayback(film, environment.capabilities, sources))
        assertEquals(DownloadsSource.ID, away.version.version.location.sourceId)
        assertTrue(abs(away.resume.positionMs!! - leftAt) < 4_000)
        val further = 33 * MINUTE
        environment.watch(film, away, further)
        assertTrue(abs(library.watchState(film)!!.positionMs - further) < 4_000)

        // The server is back, and hears where the viewer got to without it.
        reachable = true
        environment.pipeline.refresh()
        delay(1_500)
        val onServer = server.catalog().toList().single { catalog -> catalog.versions.any { it.path == path } }.userState!!
        assertTrue(abs(onServer.positionMs - library.watchState(film)!!.positionMs) < 3_000, "the server has ${onServer.positionMs / 1000} s")
        server.setPlayed(path, false)
    }
}
