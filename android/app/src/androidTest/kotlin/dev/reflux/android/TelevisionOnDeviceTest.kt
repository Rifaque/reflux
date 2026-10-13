package dev.reflux.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.reflux.android.engine.AndroidEnvironment
import dev.reflux.android.engine.TreeAddition
import dev.reflux.android.playback.Media3Player
import dev.reflux.android.storage.StorageFolders
import dev.reflux.core.model.Availability
import dev.reflux.core.playback.PlaybackFeature
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.source.ChangeNotifyingSource
import dev.reflux.library.ScanReport
import dev.reflux.library.planPlayback
import dev.reflux.sources.local.LocalFolderSource
import dev.reflux.ui.state.FolderChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What a television does differently, on a real Android runtime: a folder chosen by its path (there is no
 * system picker to grant one) becomes a library, keeps its head when the folder goes away, and plays; and
 * the device says for itself what it can show and send out.
 */
@RunWith(AndroidJUnit4::class)
class TelevisionOnDeviceTest {
    private val directory = File(context.filesDir, "reflux-tv-${System.nanoTime()}").also { it.mkdirs() }
    private val storage = File(directory, "storage").also { it.mkdirs() }
    private val data = File(directory, "data").also { it.mkdirs() }
    private val database = "reflux-tv-${System.nanoTime()}.db"
    private val players = ArrayList<Media3Player>()
    private val scope = CoroutineScope(Dispatchers.Default)

    @After
    fun cleanUp() {
        players.forEach(Media3Player::close)
        scope.cancel()
        context.deleteDatabase(database)
        directory.deleteRecursively()
    }

    private fun stub(path: String) = File(storage, path).also { it.parentFile?.mkdirs() }.writeBytes(ByteArray(16))

    private fun clip(path: String) = File(storage, path).also { file ->
        file.parentFile?.mkdirs()
        InstrumentationRegistry.getInstrumentation().context.assets.open("clip.mp4").use { input -> file.outputStream().use(input::copyTo) }
    }

    @Test
    fun aFolderChosenByItsPathBecomesALibraryThatPlaysAndSurvivesItsStorage() = runBlocking {
        clip("Movies/Night Drive (2021)/Night Drive (2021).mp4")
        stub("Movies/Heat (1995)/Heat.1995.1080p.mkv")
        stub("Shows/Severance/Season 1/Severance.S01E01.mkv")
        val environment = AndroidEnvironment(context, data, database)
        val library = environment.library

        // The browser the television's picker walks, over this storage, registering through the engine.
        val folders = StorageFolders({ StorageFolders.volumes(context) }, { true }, environment::addFolder)
        val browsed = dev.reflux.ui.state.BrowsedFolder(storage.path, "storage")
        assertEquals(listOf("Movies", "Shows"), folders.list(browsed).folders.map { it.name })
        val movies = folders.list(browsed).folders.first()
        val added = assertIs<FolderChoice.Added>(folders.add(movies))
        assertEquals(ScanReport.Status.COMPLETED, environment.pipeline.refresh().single().status)
        assertEquals(listOf("Heat", "Night Drive"), library.movies().map { it.item.title }.sorted())
        assertTrue(library.shows().isEmpty(), "only the chosen folder is read")

        // One folder is one source: a folder inside it is already there, and its parent takes it over.
        assertIs<TreeAddition.AlreadyCovered>(environment.addFolder(File(storage, "Movies/Heat (1995)"), "Heat (1995)"))
        val whole = assertIs<TreeAddition.Added>(environment.addFolder(storage, "storage"))
        assertEquals(listOf(added.sourceId), whole.replaced.map { it.id })
        environment.pipeline.refresh()
        assertEquals(1, library.sources().size)
        assertEquals(listOf("Severance"), library.shows().map { it.item.title })
        assertEquals(2, library.movies().size, "the same files are not two versions of a work")

        // It notices what is added to it, on Android's own file watching.
        val source = assertNotNull(environment.source(whole.source.descriptor.id))
        val changed = scope.async { (source as ChangeNotifyingSource).changes().first() }
        delay(500)
        stub("Movies/Arrival (2016)/Arrival.2016.1080p.mkv")
        withTimeout(15_000) { changed.await() }
        environment.pipeline.refresh()
        assertEquals(3, library.movies().size)

        // It plays, from the path, on the device's own decoders, and progress is the work's.
        val film = library.movies().single { it.item.title == "Night Drive" }.item.id
        val plan = assertNotNull(library.planPlayback(film, environment.capabilities, environment.registry::resolve))
        val player = Media3Player(context, environment.capabilities).also { players += it }
        player.load(plan.request)
        player.await("playback from a path") { it.status == PlayerStatus.PLAYING && it.positionMs > 300 }
        player.close()

        // The engine comes back knowing the folder: a restart needs no picker and no grant.
        val restarted = AndroidEnvironment(context, File(directory, "data2").also { it.mkdirs() }, database)
        val again = assertNotNull(restarted.source(whole.source.descriptor.id), "the source is recreated from its record")
        assertIs<LocalFolderSource>(again)
        assertEquals(Availability.AVAILABLE, again.availability())

        // The drive is pulled out: the library stays, marked as away, and nothing crashes; back, it is whole.
        val parked = File(directory, "parked")
        assertTrue(storage.renameTo(parked))
        assertEquals(ScanReport.Status.UNAVAILABLE, environment.pipeline.refresh().single().status)
        assertEquals(3, library.movies().size)
        assertTrue(library.movies().all { it.availability == Availability.UNAVAILABLE })
        assertTrue(parked.renameTo(storage))
        assertEquals(ScanReport.Status.COMPLETED, environment.pipeline.refresh().single().status)
        assertTrue(library.movies().all { it.availability == Availability.AVAILABLE })
    }

    @Test
    fun theDeviceSaysForItselfWhatItCanShowAndSendOut() {
        val device = AndroidEnvironment(context, data, databaseName = null).capabilities
        // Nothing is assumed of a screen or an output: what is claimed is what was reported.
        assertTrue(device.videoDecoders.isNotEmpty() && device.display.maxWidth >= device.display.maxHeight)
        assertTrue(device.videoDecoders.all { it.maxWidth > 0 && it.maxHeight > 0 })
        assertTrue(device.maxOutputChannels >= 2)
        assertEquals(device.audioPassthrough.isNotEmpty(), PlaybackFeature.AUDIO_PASSTHROUGH in device.features)
        assertEquals(
            context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE),
            PlaybackFeature.PICTURE_IN_PICTURE in device.features,
        )
        // Kept in the test log, since what a given device reports is the point.
        android.util.Log.i("RefluxTest", "capabilities: display ${device.display}, channels ${device.maxOutputChannels}, passthrough ${device.audioPassthrough}, decoders ${device.videoDecoders}")
    }
}
