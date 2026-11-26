package dev.reflux.core.playback

import dev.reflux.core.source.MediaSource

/**
 * The scene previews a source keeps for one version (B-030; docs/product/PLAYBACK.md, "Trickplay"): pictures taken every
 * [intervalMs] through the video, [count] of them, laid out [columns] by [rows] on each of [sheets] (a server's tile
 * images; a single picture per sheet where the source keeps them one by one). Only a source that already has them
 * offers them: Reflux never generates previews, and never pretends a version has them.
 */
data class TrickplayManifest(
    /** Names this set of pictures: two manifests with the same key are the same images (a cache key). */
    val key: String,
    val intervalMs: Long,
    val count: Int,
    val frameWidth: Int,
    val frameHeight: Int,
    val columns: Int,
    val rows: Int,
    /** How many sheets there are; sheet `i` holds pictures `i * perSheet` to `(i + 1) * perSheet - 1`. */
    val sheets: Int,
) {
    init {
        require(intervalMs > 0 && count > 0 && frameWidth > 0 && frameHeight > 0 && columns > 0 && rows > 0 && sheets > 0)
    }

    val perSheet: Int get() = columns * rows

    /** The picture shown for [positionMs]: the last one taken at or before it, within the video's pictures. */
    fun frameAt(positionMs: Long): TrickplayFrame = frame((positionMs.coerceAtLeast(0) / intervalMs).toInt())

    /** Picture [index] (clamped to the pictures there are), where it lies on its sheet, and when it was taken. */
    fun frame(index: Int): TrickplayFrame {
        val i = index.coerceIn(0, count - 1)
        val within = i % perSheet
        return TrickplayFrame(
            index = i,
            sheet = i / perSheet,
            x = (within % columns) * frameWidth,
            y = (within / columns) * frameHeight,
            width = frameWidth,
            height = frameHeight,
            timeMs = i * intervalMs,
        )
    }

    companion object {
        /**
         * Builds a manifest from what a source says, or null when what it says cannot be one (no pictures, a zero
         * interval or size): a malformed answer is no previews, never a failure of playback.
         */
        fun of(key: String, intervalMs: Long?, count: Int?, frameWidth: Int?, frameHeight: Int?, columns: Int = 1, rows: Int = 1): TrickplayManifest? {
            if (intervalMs == null || count == null || frameWidth == null || frameHeight == null) return null
            if (intervalMs <= 0 || count <= 0 || frameWidth <= 0 || frameHeight <= 0 || columns <= 0 || rows <= 0) return null
            val perSheet = columns * rows
            return TrickplayManifest(key, intervalMs, count, frameWidth, frameHeight, columns, rows, (count + perSheet - 1) / perSheet)
        }
    }
}

/** One preview picture: on [sheet], the rectangle at [x], [y] of [width] by [height], taken at [timeMs]. */
data class TrickplayFrame(val index: Int, val sheet: Int, val x: Int, val y: Int, val width: Int, val height: Int, val timeMs: Long)

/**
 * A source that keeps scene previews for some of its versions (Jellyfin's trickplay tiles, Plex's preview index).
 * A source that has none for a version says so; neither call ever fails.
 */
interface TrickplaySource : MediaSource {
    /** The previews of the version at [path], or null when it has none (or the source cannot say now). */
    suspend fun trickplay(path: String): TrickplayManifest?

    /** The encoded image (JPEG) of [sheet] of [manifest], or null when it cannot be had now. */
    suspend fun trickplaySheet(path: String, manifest: TrickplayManifest, sheet: Int): ByteArray?
}

/**
 * Roku's BIF ("base index frames"), the format Plex serves a version's previews in: a header, an index of
 * (timestamp, offset) pairs ended by 0xFFFFFFFF, then the JPEGs back to back. Read without trusting it: anything
 * malformed is no previews.
 */
object BifIndex {
    private val MAGIC = byteArrayOf(0x89.toByte(), 0x42, 0x49, 0x46, 0x0d, 0x0a, 0x1a, 0x0a)
    private const val HEADER = 64

    /** The previews in [bytes]: when each was taken and its JPEG, in order; null when [bytes] is not a well-formed BIF. */
    fun read(bytes: ByteArray): Bif? {
        if (bytes.size < HEADER + 8) return null
        for (i in MAGIC.indices) if (bytes[i] != MAGIC[i]) return null
        val count = int(bytes, 12)
        val multiplier = int(bytes, 16).let { if (it == 0L) 1000L else it }
        if (count <= 0 || count > 1_000_000) return null
        val entries = ArrayList<Pair<Long, Long>>(count.toInt() + 1)
        var at = HEADER
        while (at + 8 <= bytes.size) {
            val stamp = int(bytes, at)
            val offset = int(bytes, at + 4)
            entries += stamp to offset
            at += 8
            if (stamp == 0xFFFFFFFFL) break
            if (entries.size > count + 1) return null
        }
        if (entries.size < 2) return null
        val frames = ArrayList<Pair<Long, IntRange>>(entries.size - 1)
        for (i in 0 until entries.size - 1) {
            val start = entries[i].second
            val end = entries[i + 1].second
            if (start < at || end <= start || end > bytes.size) return null
            frames += Pair(entries[i].first * multiplier, start.toInt() until end.toInt())
        }
        return Bif(bytes, frames, multiplier)
    }

    /** A JPEG's width and height from its frame header, or null when [jpeg] has none. */
    fun jpegSize(jpeg: ByteArray): Pair<Int, Int>? {
        if (jpeg.size < 4 || jpeg[0] != 0xFF.toByte() || jpeg[1] != 0xD8.toByte()) return null
        var at = 2
        while (at + 9 < jpeg.size) {
            if (jpeg[at] != 0xFF.toByte()) return null
            val marker = jpeg[at + 1].toInt() and 0xFF
            val length = ((jpeg[at + 2].toInt() and 0xFF) shl 8) or (jpeg[at + 3].toInt() and 0xFF)
            if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                val height = ((jpeg[at + 5].toInt() and 0xFF) shl 8) or (jpeg[at + 6].toInt() and 0xFF)
                val width = ((jpeg[at + 7].toInt() and 0xFF) shl 8) or (jpeg[at + 8].toInt() and 0xFF)
                return if (width > 0 && height > 0) width to height else null
            }
            if (length < 2) return null
            at += 2 + length
        }
        return null
    }

    private fun int(bytes: ByteArray, at: Int): Long =
        (bytes[at].toLong() and 0xFF) or ((bytes[at + 1].toLong() and 0xFF) shl 8) or ((bytes[at + 2].toLong() and 0xFF) shl 16) or ((bytes[at + 3].toLong() and 0xFF) shl 24)

    /** A read BIF: each picture's time and where its JPEG lies in [bytes]. */
    class Bif internal constructor(private val bytes: ByteArray, private val frames: List<Pair<Long, IntRange>>, val intervalMs: Long) {
        val count: Int get() = frames.size

        fun timeMs(index: Int): Long = frames[index].first

        fun jpeg(index: Int): ByteArray = frames[index].second.let { bytes.copyOfRange(it.first, it.last + 1) }
    }
}
