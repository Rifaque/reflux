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
                video = VideoStream(codec, height * 16 / 9, height, if (range == DynamicRange.SDR) 8 else 10, range),
                audio = listOf(audio),
            ),
            streamOrigin = StreamInfoOrigin.FILENAME_HINTS,
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
}
