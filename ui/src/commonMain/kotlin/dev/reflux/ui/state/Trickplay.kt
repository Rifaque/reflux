package dev.reflux.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import dev.reflux.core.playback.TrickplayFrame
import dev.reflux.core.playback.TrickplayManifest
import dev.reflux.core.playback.TrickplaySource
import dev.reflux.core.source.MediaSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** Decodes an encoded image (a preview sheet), or null when it is not one. The platform's own decoder. */
internal expect fun decodePreviewImage(bytes: ByteArray): ImageBitmap?

/**
 * Preview sheets as their sources sent them (B-030), kept across playbacks so that no sheet is downloaded twice while
 * it fits: least recently used out beyond [maxBytes]. Encoded (a few hundred kilobytes a sheet), never decoded. Used from
 * the UI thread only: a sheet is put once its fetch has returned there.
 */
class TrickplaySheets(private val maxBytes: Long = 32L * 1024 * 1024) {
    private val sheets = LinkedHashMap<String, ByteArray>(16, 0.75f, true)
    private var bytes = 0L

    /** How many sheets were fetched from a source, for tests and diagnostics. */
    var fetched = 0
        private set

    internal fun get(key: String): ByteArray? = sheets[key]

    internal fun put(key: String, sheet: ByteArray) {
        fetched++
        sheets.put(key, sheet)?.let { bytes -= it.size }
        bytes += sheet.size
        val it = sheets.entries.iterator()
        while (bytes > maxBytes && it.hasNext()) {
            val eldest = it.next()
            if (eldest.key == key) continue
            bytes -= eldest.value.size
            it.remove()
        }
    }
}

/** A preview to draw: the decoded sheet, and the rectangle of [frame] on it. */
data class TrickplayPicture(val sheet: ImageBitmap, val frame: TrickplayFrame)

/**
 * The scene previews of one playback (B-030, B-029): whether the version has any, and the picture for the position the
 * viewer is looking at (a pointer over the timeline, a thumb dragged, the right stick's scene). Only the newest
 * position is loaded: one asked for while an older one is still loading cancels it, so an old picture never replaces
 * a newer one; a sheet not yet kept waits [settleMs] first, so sweeping across the timeline does not ask the server for
 * every sheet passed over. Nothing is loaded until a position is asked for, and loading runs on [background], away from
 * the player. A version without previews, or a source that cannot be reached, simply has none: the timeline then shows
 * the time alone.
 */
class TrickplayPreviews internal constructor(
    private val scope: CoroutineScope,
    private val background: CoroutineContext,
    private val source: MediaSource?,
    private val path: String?,
    private val sheets: TrickplaySheets,
    private val decode: (ByteArray) -> ImageBitmap? = ::decodePreviewImage,
    private val settleMs: Long = 60,
) {
    /** The version's previews: null until known, and while it has none. */
    var manifest: TrickplayManifest? by mutableStateOf(null)
        private set

    /** Whether it is known yet whether the version has previews. */
    var known: Boolean by mutableStateOf(false)
        private set

    /** The picture for the position last asked for, once it is loaded; null while none is asked for or none can be had. */
    var picture: TrickplayPicture? by mutableStateOf(null)
        private set

    private val wanted = MutableStateFlow<Long?>(null)
    private val decoded = LinkedHashMap<Int, ImageBitmap>(4, 0.75f, true)
    private var job: Job? = null

    /** Asks the source once whether the version has previews, then follows what is asked for. */
    internal fun start() {
        if (job != null) return
        job = scope.launch {
            val previews = (source as? TrickplaySource)?.takeIf { path != null }
            manifest = previews?.let { withContext(background) { runCatching { it.trickplay(path!!) }.getOrNull() } }
            known = true
            val found = manifest ?: return@launch
            val from = previews ?: return@launch
            wanted.collectLatest { position -> picture = position?.let { show(from, found, it) } }
        }
    }

    /** The position whose picture is wanted now, or null when none is (the pointer left, the scrub ended). */
    fun want(positionMs: Long?) {
        wanted.value = positionMs
        if (positionMs == null) picture = null
    }

    private suspend fun show(previews: TrickplaySource, manifest: TrickplayManifest, positionMs: Long): TrickplayPicture? {
        val frame = manifest.frameAt(positionMs)
        // The same sheet as on screen: only the rectangle changes, at once.
        picture?.takeIf { it.frame.sheet == frame.sheet }?.let { return it.copy(frame = frame) }
        decoded[frame.sheet]?.let { return TrickplayPicture(it, frame) }
        val key = "${manifest.key}#${frame.sheet}"
        val bytes = sheets.get(key) ?: run {
            delay(settleMs)
            withContext(background) { runCatching { previews.trickplaySheet(path!!, manifest, frame.sheet) }.getOrNull() }
                ?.also { sheets.put(key, it) }
        } ?: return null
        val image = withContext(background) { decode(bytes) } ?: return null
        decoded[frame.sheet] = image
        // A few decoded sheets at most: each is a few megapixels.
        while (decoded.size > DECODED_SHEETS) decoded.remove(decoded.keys.first())
        return TrickplayPicture(image, frame)
    }

    internal fun stop() {
        job?.cancel()
        job = null
        decoded.clear()
        picture = null
    }

    private companion object {
        const val DECODED_SHEETS = 3
    }
}
