package dev.reflux.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.SourceId
import dev.reflux.core.source.MediaSource
import dev.reflux.library.ArtworkCache
import dev.reflux.ui.state.ArtworkLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import java.nio.file.Files
import java.nio.file.Path

/**
 * Decodes artwork from the disk cache with Skia, scaled down to the size it is shown at, and keeps recently
 * used images in memory so scrolling back does not decode again.
 */
internal class DesktopArtworkLoader(
    private val cache: ArtworkCache,
    private val sources: (SourceId) -> MediaSource?,
    private val memoryBudgetBytes: Long = 192L * 1024 * 1024,
) : ArtworkLoader {
    private data class Key(val locator: ArtworkLocator, val widthPx: Int)

    private val memory = LinkedHashMap<Key, ImageBitmap>(64, 0.75f, true)
    private var memoryBytes = 0L

    /** Bounds simultaneous downloads and decodes when a whole grid appears at once. */
    private val permits = Semaphore(6)

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
        val image = Image.makeFromEncoded(Files.readAllBytes(file))
        if (image.width <= widthPx) return@runCatching image.toComposeImageBitmap()
        val height = (image.height.toLong() * widthPx / image.width).toInt().coerceAtLeast(1)
        val bitmap = Bitmap().apply { allocN32Pixels(widthPx, height) }
        val pixels = bitmap.peekPixels() ?: return@runCatching image.toComposeImageBitmap()
        image.scalePixels(pixels, FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR), false)
        bitmap.setImmutable()
        bitmap.asComposeImageBitmap()
    }.getOrNull() // a corrupt or unsupported image shows the placeholder
}
