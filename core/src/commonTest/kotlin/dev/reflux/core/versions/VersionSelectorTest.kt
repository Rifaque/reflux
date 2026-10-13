package dev.reflux.core.versions

import dev.reflux.core.model.Availability
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaLocation
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.SourceId
import dev.reflux.core.model.StableIds
import dev.reflux.core.model.StreamInfoOrigin
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.AudioStream
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.Devices
import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoStream
import dev.reflux.core.source.SourceLocality
import dev.reflux.core.watch.WatchRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VersionSelectorTest {
    private val item = MediaId("m1")

    private fun candidate(
        name: String,
        codec: VideoCodec = VideoCodec.H264,
        height: Int = 1080,
        range: DynamicRange = DynamicRange.SDR,
        audio: AudioStream = AudioStream(AudioCodec.AC3, 6),
        availability: Availability = Availability.AVAILABLE,
        locality: SourceLocality = SourceLocality.DEVICE,
        size: Long = 1_000,
        bitDepth: Int = if (range == DynamicRange.SDR) 8 else 10,
        minutes: Long? = null,
        edition: String? = null,
    ): VersionCandidate {
        val location = MediaLocation(SourceId(locality.name), "$name.mkv")
        val version = MediaVersion(
            id = StableIds.versionId(location),
            itemId = item,
            location = location,
            sizeBytes = size,
            modifiedAtEpochMs = 0,
            stream = StreamInfo(
                container = Container.MATROSKA,
                video = VideoStream(codec, height * 16 / 9, height, bitDepth, range),
                audio = listOf(audio),
                durationMs = minutes?.let { it * 60_000 },
            ),
            streamOrigin = StreamInfoOrigin.FILENAME_HINTS,
            edition = edition,
        )
        return VersionCandidate(version, availability, locality)
    }

    @Test
    fun capableTvPrefers4kHdr() {
        val uhd = candidate("uhd", VideoCodec.HEVC, 2160, DynamicRange.DOLBY_VISION)
        val hd = candidate("hd")
        val selection = VersionSelector.select(listOf(hd, uhd), Devices.tv4kHdr)
        assertEquals(uhd.version, selection.best?.version)
        assertEquals(SelectionCriterion.RESOLUTION, selection.decidedBy)
    }

    @Test
    fun weakDevicePrefersVersionItPlaysWell() {
        val uhd = candidate("uhd", VideoCodec.HEVC, 2160, DynamicRange.HDR10)
        val hd = candidate("hd", height = 720)
        val selection = VersionSelector.select(listOf(uhd, hd), Devices.laptop1080)
        assertEquals(hd.version, selection.best?.version)
        assertEquals(SelectionCriterion.COMPATIBILITY, selection.decidedBy)
    }

    @Test
    fun resolutionBeyondTheDisplayIsNotBetter() {
        val uhd = candidate("uhd", height = 2160, locality = SourceLocality.REMOTE)
        val hd = candidate("hd", height = 1080, locality = SourceLocality.DEVICE)
        val selection = VersionSelector.select(listOf(uhd, hd), Devices.laptop1080)
        assertEquals(hd.version, selection.best?.version)
        assertEquals(SelectionCriterion.LOCALITY, selection.decidedBy)
    }

    @Test
    fun localWinsWhenOtherwiseEquivalent() {
        val remote = candidate("a", locality = SourceLocality.REMOTE)
        val local = candidate("b", locality = SourceLocality.DEVICE)
        val selection = VersionSelector.select(listOf(remote, local), Devices.tv4kHdr)
        assertEquals(local.version, selection.best?.version)
        assertEquals(SelectionCriterion.LOCALITY, selection.decidedBy)
    }

    @Test
    fun unavailableVersionsAreNeverChosen() {
        val offline = candidate("uhd", VideoCodec.HEVC, 2160, availability = Availability.UNAVAILABLE)
        val online = candidate("hd", locality = SourceLocality.REMOTE)
        assertEquals(online.version, VersionSelector.select(listOf(offline, online), Devices.tv4kHdr).best?.version)
        assertNull(VersionSelector.select(listOf(offline), Devices.tv4kHdr).best)
    }

    @Test
    fun userPreferenceWinsWhilePlayable() {
        val uhd = candidate("uhd", VideoCodec.HEVC, 2160)
        val hd = candidate("hd")
        val selection = VersionSelector.select(listOf(uhd, hd), Devices.tv4kHdr, preferred = hd.version.id)
        assertEquals(hd.version, selection.best?.version)
        assertEquals(SelectionCriterion.USER_PREFERENCE, selection.decidedBy)

        val unsupported = VersionSelector.select(listOf(uhd, hd), Devices.oldPhone, preferred = uhd.version.id)
        assertEquals(hd.version, unsupported.best?.version)
    }

    @Test
    fun betterAudioBreaksTiesOnlyWhenTheDeviceBenefits() {
        val atmos = candidate("atmos", audio = AudioStream(AudioCodec.TRUEHD, 8, atmos = true))
        val stereo = candidate("stereo", audio = AudioStream(AudioCodec.AAC, 2), size = 2_000)
        val tv = VersionSelector.select(listOf(stereo, atmos), Devices.tv4kHdr)
        assertEquals(atmos.version, tv.best?.version)
        assertEquals(SelectionCriterion.AUDIO, tv.decidedBy)
        // A stereo laptop downmixes; that is not a compromise worth losing a lossless track over.
        val laptop = VersionSelector.select(listOf(stereo, atmos), Devices.laptop1080)
        assertEquals(atmos.version, laptop.best?.version)
        assertEquals(SelectionCriterion.AUDIO, laptop.decidedBy)
    }

    @Test
    fun largerFileBreaksOtherwiseEqualTies() {
        val small = candidate("small", size = 1_000)
        val large = candidate("large", size = 9_000)
        val selection = VersionSelector.select(listOf(small, large), Devices.tv4kHdr)
        assertEquals(large.version, selection.best?.version)
        assertEquals(SelectionCriterion.BITRATE, selection.decidedBy)
    }

    @Test
    fun theSameThingStreamedLosesToTheLocalFile() {
        // 1080p H.264 8-bit on the device, and the same from a server.
        val local = candidate("local", locality = SourceLocality.DEVICE)
        val server = candidate("server", locality = SourceLocality.LOCAL_NETWORK)
        for (device in listOf(Devices.tv4kHdr, Devices.laptop1080, Devices.oldPhone)) {
            val selection = VersionSelector.select(listOf(server, local), device)
            assertEquals(local.version, selection.best?.version)
            assertEquals(SelectionCriterion.LOCALITY, selection.decidedBy)
        }
    }

    @Test
    fun aLocalFileTheDeviceStrugglesWithLosesToAStreamItPlaysWell() {
        // 4K HEVC 10-bit on the device, 1080p H.264 from a server.
        val local = candidate("local-uhd", VideoCodec.HEVC, 2160, bitDepth = 10, locality = SourceLocality.DEVICE)
        val server = candidate("server-hd", locality = SourceLocality.REMOTE)

        // HEVC only in software here: the stream that decodes in hardware is the better evening.
        val laptop = VersionSelector.select(listOf(local, server), Devices.laptop1080)
        assertEquals(server.version, laptop.best?.version)
        assertEquals(SelectionCriterion.COMPATIBILITY, laptop.decidedBy)

        // No HEVC at all.
        assertEquals(server.version, VersionSelector.select(listOf(local, server), Devices.oldPhone).best?.version)

        // A device that plays both well takes the local file, which here is also the better picture.
        assertEquals(local.version, VersionSelector.select(listOf(local, server), Devices.tv4kHdr).best?.version)
    }

    @Test
    fun localIsATieBreakerNotAnOverride() {
        val local = candidate("local-hd", locality = SourceLocality.DEVICE)
        val server = candidate("server-uhd", VideoCodec.HEVC, 2160, DynamicRange.HDR10, locality = SourceLocality.REMOTE)
        val tv = VersionSelector.select(listOf(local, server), Devices.tv4kHdr)
        assertEquals(server.version, tv.best?.version, "a picture this display can show in full is worth streaming")
        assertEquals(SelectionCriterion.RESOLUTION, tv.decidedBy)
    }

    @Test
    fun bitDepthAndCodecAreNotQualityInThemselves() {
        // The same 1080p SDR picture as 8-bit H.264 locally and as 10-bit HEVC remotely, on a device that plays both.
        val local = candidate("h264-8bit", locality = SourceLocality.DEVICE)
        val server = candidate("hevc-10bit", VideoCodec.HEVC, bitDepth = 10, locality = SourceLocality.REMOTE)
        val selection = VersionSelector.select(listOf(server, local), Devices.tv4kHdr)
        assertEquals(local.version, selection.best?.version)
        assertEquals(SelectionCriterion.LOCALITY, selection.decidedBy)
    }

    @Test
    fun aViewerPartWayThroughOneCutStaysOnIt() {
        val theatrical = candidate("theatrical-uhd", VideoCodec.HEVC, 2160, minutes = 120)
        val extended = candidate("extended-hd", minutes = 137, edition = "Extended Edition", locality = SourceLocality.REMOTE)
        // With nothing in progress the better picture wins.
        assertEquals(theatrical.version, VersionSelector.select(listOf(theatrical, extended), Devices.tv4kHdr).best?.version)

        val progress = WatchRules.onStop(null, item, 60 * 60_000L, 137 * 60_000L, nowMs = 1, version = extended.version)
        val selection = VersionSelector.select(listOf(theatrical, extended), Devices.tv4kHdr, resuming = progress)
        assertEquals(extended.version, selection.best?.version)
        assertEquals(SelectionCriterion.CONTINUITY, selection.decidedBy)

        // Another copy of the cut in progress is as good as the one that was playing: the usual criteria choose.
        val extendedLocal = candidate("extended-local", minutes = 137, locality = SourceLocality.DEVICE)
        val among = VersionSelector.select(listOf(theatrical, extended, extendedLocal), Devices.tv4kHdr, resuming = progress)
        assertEquals(extendedLocal.version, among.best?.version)
        assertEquals(SelectionCriterion.LOCALITY, among.decidedBy)
    }

    @Test
    fun continuityNeverOutranksBeingAbleToPlay() {
        val theatrical = candidate("theatrical", minutes = 120)
        val extended = candidate("extended", VideoCodec.HEVC, 2160, minutes = 137, edition = "Extended Edition")
        val away = candidate("extended-away", minutes = 137, edition = "Extended Edition", availability = Availability.UNAVAILABLE, locality = SourceLocality.REMOTE)
        val progress = WatchRules.onStop(null, item, 60 * 60_000L, 137 * 60_000L, nowMs = 1, version = extended.version)
        // The phone cannot decode the extended copy, and the other one is on a source that is away.
        val selection = VersionSelector.select(listOf(extended, away, theatrical), Devices.oldPhone, resuming = progress)
        assertEquals(theatrical.version, selection.best?.version)
    }
}
