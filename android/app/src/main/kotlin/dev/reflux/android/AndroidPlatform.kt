package dev.reflux.android

import dev.reflux.core.BuildIdentity
import dev.reflux.ui.legal.NoticeTarget
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.LocaleList
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.reflux.android.engine.AndroidEnvironment
import dev.reflux.android.engine.TreeAddition
import dev.reflux.android.playback.Media3Player
import dev.reflux.android.storage.StorageFolders
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.SourceId
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.Player
import dev.reflux.core.source.MediaSource
import dev.reflux.library.ArtworkCache
import dev.reflux.library.Offline
import dev.reflux.library.SourceConnections
import dev.reflux.ui.state.AccessibilityPreferences
import dev.reflux.ui.state.ArtworkLoader
import dev.reflux.ui.state.FolderBrowser
import dev.reflux.ui.state.FolderChoice
import dev.reflux.ui.state.FullscreenMode
import dev.reflux.ui.state.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * Decodes artwork from the disk cache with the platform decoder, sampled down to the size it is shown at,
 * and keeps recently used images in memory so that scrolling back does not decode again.
 */
internal class AndroidArtworkLoader(
    private val cache: ArtworkCache,
    private val sources: (SourceId) -> MediaSource?,
    private val memoryBudgetBytes: Long = (Runtime.getRuntime().maxMemory() / 4).coerceIn(24L * 1024 * 1024, 160L * 1024 * 1024),
) : ArtworkLoader {
    private data class Key(val locator: ArtworkLocator, val widthPx: Int)

    private val memory = LinkedHashMap<Key, ImageBitmap>(64, 0.75f, true)
    private var memoryBytes = 0L

    /** Bounds simultaneous downloads and decodes when a whole grid appears at once. */
    private val permits = Semaphore(4)

    override fun cached(locator: ArtworkLocator, widthPx: Int): ImageBitmap? = synchronized(memory) { memory[Key(locator, widthPx)] }

    override suspend fun load(locator: ArtworkLocator, widthPx: Int): ImageBitmap? {
        cached(locator, widthPx)?.let { return it }
        val image = permits.withPermit {
            val file = cache.get(locator, sources) ?: return null
            withContext(Dispatchers.IO) { decode(file, widthPx) }
        } ?: return null
        remember(Key(locator, widthPx), image)
        return image
    }

    private fun remember(key: Key, image: ImageBitmap) = synchronized(memory) {
        memory.put(key, image)?.let { memoryBytes -= bytesOf(it) }
        memoryBytes += bytesOf(image)
        val eldest = memory.entries.iterator()
        while (memoryBytes > memoryBudgetBytes && eldest.hasNext()) {
            val entry = eldest.next()
            if (entry.key == key) continue
            memoryBytes -= bytesOf(entry.value)
            eldest.remove()
        }
    }

    private fun bytesOf(image: ImageBitmap): Long = image.width.toLong() * image.height * 4

    private fun decode(file: Path, widthPx: Int): ImageBitmap? = runCatching {
        val path = file.toString()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return@runCatching null
        // The decoder halves; the rest of the way down is a filtered scale.
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= widthPx) sample *= 2
        val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@runCatching null
        if (decoded.width <= widthPx) return@runCatching decoded.asImageBitmap()
        val height = (decoded.height.toLong() * widthPx / decoded.width).toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(decoded, widthPx, height, true).also { if (it !== decoded) decoded.recycle() }.asImageBitmap()
    }.getOrNull() // a corrupt or unsupported image shows the placeholder
}

/**
 * The Android shell's side of the experience layer: platform-decoded artwork, Media3 on a surface the
 * activity shows, the system's folder picker, and the screen's orientation and system bars for fullscreen.
 */
internal class AndroidPlatform(private val environment: AndroidEnvironment, override val television: Boolean = false) : Platform {
    /** Opens the system folder picker and returns the granted tree, or null when it was dismissed. Set by the activity. */
    var pickFolder: (suspend () -> Uri?)? = null

    /** Asks for the user's leave to read storage, if it is still needed; false when they refuse. Set by the activity. */
    var allowStorage: (suspend () -> Boolean)? = null

    override val offline: Offline get() = environment.offline

    override val build: BuildIdentity get() = environment.build

    override val notices: NoticeTarget get() = NoticeTarget.ANDROID

    override val connections: SourceConnections get() = environment.connections

    /** Loudness belongs to the device: a phone's keys, a television's remote. */
    override val systemVolume: Boolean get() = true

    /**
     * A television's system has no folder picker to hand over to, so there Reflux shows the storage itself
     * and the user walks it with the remote. Everywhere else the system's picker is the way in.
     */
    override val folderBrowser: FolderBrowser? = if (!television) null else StorageFolders(
        volumes = { StorageFolders.volumes(environment.context) },
        allowed = { allowStorage?.invoke() ?: false },
        register = environment::addFolder,
    )

    /** The engine whose video the activity should be showing, if any. */
    var player: Media3Player? by mutableStateOf(null)
        private set

    override val artwork: ArtworkLoader = AndroidArtworkLoader(environment.artwork, environment.registry::resolve)

    override val device: DeviceCapabilities get() = environment.capabilities

    override val preferredLanguages: List<String> =
        LocaleList.getDefault().let { locales -> (0 until locales.size()).map { locales[it].language } }.filter { it.isNotBlank() }.distinct().ifEmpty { listOf("en") }

    override val accessibility: AccessibilityPreferences get() = environment.accessibility.preferences

    override val canPlay: Boolean get() = true

    override fun report(problem: String, cause: Throwable?) = environment.report(problem, cause)

    /** The app already fills the screen: on a handheld fullscreen turns the picture sideways, on a television there is none. */
    override val fullscreenMode: FullscreenMode get() = if (television) FullscreenMode.NONE else FullscreenMode.LANDSCAPE

    override var fullscreen: Boolean by mutableStateOf(false)
        private set

    override fun requestFullscreen(fullscreen: Boolean) {
        this.fullscreen = fullscreen
    }

    override suspend fun createPlayer(): Player = withContext(Dispatchers.Main.immediate) {
        Media3Player(environment.context, device).also { player = it }
    }

    override suspend fun releasePlayer(player: Player) = withContext(Dispatchers.Main.immediate) {
        if (this@AndroidPlatform.player === player) this@AndroidPlatform.player = null
        player.close()
    }

    override suspend fun addMediaFolder(): FolderChoice {
        val pick = pickFolder ?: return FolderChoice.Cancelled
        val tree = pick() ?: return FolderChoice.Cancelled
        return when (val added = withContext(Dispatchers.IO) { environment.addFolder(tree) }) {
            is TreeAddition.Added -> FolderChoice.Added(added.source.descriptor.id)
            is TreeAddition.AlreadyCovered -> FolderChoice.AlreadyInLibrary(added.by.id, added.by.displayName)
            is TreeAddition.Unreadable -> FolderChoice.Unreadable(added.name)
        }
    }

    override suspend fun removeSource(id: SourceId) {
        // The server's sign-out goes on by itself; the removal is done here.
        withContext(Dispatchers.IO) { environment.removeSource(id) }
    }
}
