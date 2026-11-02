@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.reflux.android.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.text.CueEncoder
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.text.pgs.PgsParser
import java.io.ByteArrayOutputStream

/**
 * A PGS subtitle file (`.sup`, what a server extracts from a disc rip) read one presentation at a time.
 *
 * Media3 parses PGS as it sits in Matroska, without the file's `PG` framing, and a side-loaded subtitle would be
 * parsed whole: a film's 2,400 presentations at once, as full-width bitmaps. Here each one is taken from the file as
 * playback needs it, decoded by Media3's own [PgsParser], and handed on as cues, as Matroska's PGS is. Nothing else
 * of PGS is Reflux's: only the framing.
 */
internal class SupExtractor(private val format: Format) : Extractor {
    private lateinit var output: TrackOutput
    private val parser = PgsParser()
    private val sets = SupDisplaySets()
    private val header = ByteArray(SupDisplaySets.HEADER)
    private val encoder = CueEncoder()

    /** What a seek asked for. Presentations long before it are read past and not decoded. */
    private var decodeFromUs = 0L

    override fun sniff(input: ExtractorInput): Boolean {
        val magic = ByteArray(2)
        return input.peekFully(magic, 0, 2, true) && SupDisplaySets.isHeader(magic)
    }

    override fun init(extractorOutput: ExtractorOutput) {
        output = extractorOutput.track(0, C.TRACK_TYPE_TEXT)
        output.format(
            format.buildUpon().setSampleMimeType(MimeTypes.APPLICATION_MEDIA3_CUES).setCodecs(MimeTypes.APPLICATION_PGS)
                .setCueReplacementBehavior(Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE).build(),
        )
        extractorOutput.endTracks()
        extractorOutput.seekMap(FromTheStart)
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        if (!input.readFully(header, 0, header.size, true)) return Extractor.RESULT_END_OF_INPUT
        val data = ByteArray(SupDisplaySets.segmentLength(header))
        input.readFully(data, 0, data.size)
        val set = sets.add(header, data) ?: return Extractor.RESULT_CONTINUE
        if (set.timeUs < decodeFromUs - LOOK_BEHIND_US) return Extractor.RESULT_CONTINUE
        parser.parse(set.segments, 0, set.segments.size, SubtitleParser.OutputOptions.allCues()) { cues ->
            val sample = encoder.encode(cues.cues, C.TIME_UNSET)
            output.sampleData(ParsableByteArray(sample), sample.size)
            output.sampleMetadata(set.timeUs, C.BUFFER_FLAG_KEY_FRAME, sample.size, 0, null)
        }
        return Extractor.RESULT_CONTINUE
    }

    override fun seek(position: Long, timeUs: Long) {
        sets.reset()
        decodeFromUs = timeUs
    }

    override fun release() = Unit

    /**
     * A `.sup` has no index, so every seek reads from the start and skips what is long before the place: cheap, as
     * reading is fast and only decoding is not. Duration is the video's to say.
     */
    private object FromTheStart : SeekMap {
        override fun isSeekable() = true
        override fun getDurationUs() = C.TIME_UNSET
        override fun getSeekPoints(timeUs: Long) = SeekMap.SeekPoints(SeekPoint(timeUs, 0))
    }

    companion object {
        /** A presentation that began before the place may still be on screen when playback gets there. */
        private const val LOOK_BEHIND_US = 10_000_000L

        /** How a subtitle attachment says it is PGS in its file form: Jellyfin's `Stream.pgssub`, or `.sup`. */
        const val MIME = "application/x-reflux-sup"
    }
}

/**
 * Opens its upstream on the first read, not on open: a server extracting a subtitle answers only when it has finished
 * (49 s seen on a 5.6 GB film), and the picture must not wait for that. Until it answers there are no cues, as a late
 * subtitle should be.
 */
internal class OpenOnRead(private val upstream: DataSource) : DataSource {
    private var spec: DataSpec? = null
    private var opened = false

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        spec = dataSpec
        opened = false
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!opened) {
            upstream.open(spec!!)
            opened = true
        }
        return upstream.read(buffer, offset, length)
    }

    override fun getUri(): Uri? = spec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = if (opened) upstream.responseHeaders else emptyMap()

    override fun close() {
        if (opened) upstream.close()
        opened = false
    }
}

/** The framing of a `.sup` file, apart from Media3, so it can be tested without a device. */
internal class SupDisplaySets {
    class DisplaySet(val timeUs: Long, val segments: ByteArray)

    private val segments = ByteArrayOutputStream()
    private var timeUs = C.TIME_UNSET

    /** One segment: its 13-byte header and its data. A presentation when it was the set's last. */
    fun add(header: ByteArray, data: ByteArray): DisplaySet? {
        require(isHeader(header)) { "not a PGS segment" }
        if (segments.size() == 0) timeUs = presentationTimeUs(header)
        // What Media3 parses: type, size, data, as in Matroska.
        segments.write(header, TYPE, 3)
        segments.write(data, 0, data.size)
        if (header[TYPE] != END) return null
        return DisplaySet(timeUs, segments.toByteArray()).also { reset() }
    }

    fun reset() {
        segments.reset()
        timeUs = C.TIME_UNSET
    }

    companion object {
        const val HEADER = 13
        private const val TYPE = 10
        private const val END: Byte = 0x80.toByte()

        fun isHeader(bytes: ByteArray) = bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'G'.code.toByte()

        fun segmentLength(header: ByteArray) = ((header[11].toInt() and 0xFF) shl 8) or (header[12].toInt() and 0xFF)

        /** The presentation time, in a 90 kHz clock. */
        fun presentationTimeUs(header: ByteArray): Long {
            val ticks = (2..5).fold(0L) { value, at -> (value shl 8) or (header[at].toLong() and 0xFF) }
            return ticks * 1_000 / 90
        }
    }
}
