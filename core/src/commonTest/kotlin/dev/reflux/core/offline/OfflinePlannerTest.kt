package dev.reflux.core.offline

import dev.reflux.core.model.Availability
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaLocation
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.SourceId
import dev.reflux.core.model.StreamInfoOrigin
import dev.reflux.core.model.VersionId
import dev.reflux.core.model.WatchState
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.AudioStream
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.Devices
import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoStream
import dev.reflux.core.source.SourceLocality
import dev.reflux.core.versions.VersionCandidate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val GB = 1024L * 1024 * 1024
private const val MINUTE = 60_000L

/**
 * What Reflux proposes to download: the version this device plays best, in the room there is, and never
 * something the user did not ask for in place of something they did.
 */
class OfflinePlannerTest {
    private val film = MediaId("film")
    private val server = SourceId("server")

    private fun version(
        id: String,
        height: Int = 1080,
        codec: VideoCodec = VideoCodec.H264,
        bitDepth: Int = 8,
        range: DynamicRange = DynamicRange.SDR,
        bytes: Long = 8 * GB,
        edition: String? = null,
        minutes: Long = 120,
        item: MediaId = film,
        source: SourceId = server,
        bitrate: Long? = null,
    ) = MediaVersion(
        id = VersionId(id),
        itemId = item,
        location = MediaLocation(source, "items/$id"),
        sizeBytes = bytes,
        modifiedAtEpochMs = 1,
        stream = StreamInfo(
            container = Container.MATROSKA,
            video = VideoStream(codec, height * 16 / 9, height, bitDepth, range),
            audio = listOf(AudioStream(AudioCodec.AAC, 2, "eng")),
            durationMs = minutes * MINUTE,
            bitrateBps = bitrate,
        ),
        streamOrigin = StreamInfoOrigin.SOURCE,
        edition = edition,
    )

    private fun remote(version: MediaVersion, availability: Availability = Availability.AVAILABLE) = VersionCandidate(version, availability, SourceLocality.LOCAL_NETWORK)
    private fun onDevice(version: MediaVersion, availability: Availability = Availability.AVAILABLE) = VersionCandidate(version, availability, SourceLocality.DEVICE)

    private fun request(
        vararg versions: VersionCandidate,
        pinned: String? = null,
        state: WatchState? = null,
        downloadable: Set<VersionId> = versions.map { it.version.id }.toSet(),
        pending: Boolean = false,
        reason: OfflineReason = OfflineReason.CHOSEN,
    ) = OfflineRequest(versions.first().version.itemId, reason, versions.toList(), downloadable, pinned?.let(::VersionId), state, pending)

    /** Room for [usable] bytes of downloads, after the reserve. */
    private fun room(usable: Long) = StorageSnapshot(freeBytes = usable + 500L * 1024 * 1024)

    private fun plan(request: OfflineRequest, usable: Long = 100 * GB, device: dev.reflux.core.playback.DeviceCapabilities = Devices.laptop1080) =
        OfflinePlanner.plan(listOf(request), device, room(usable))

    @Test
    fun theVersionThisDevicePlaysBestIsTakenNotTheLargest() {
        val uhd = version("uhd", 2160, VideoCodec.HEVC, 10, DynamicRange.HDR10, bytes = 40 * GB)
        val hd = version("hd", 1080, VideoCodec.H264, 8, bytes = 8 * GB)
        // A 1080p SDR screen with software HEVC shows nothing more of the 4K file, and plays it worse.
        assertEquals("hd", plan(request(remote(uhd), remote(hd)), device = Devices.laptop1080).downloads.single().version.id.value)
        // The same two on a device that shows 4K HDR: the other way round.
        assertEquals("uhd", plan(request(remote(uhd), remote(hd)), device = Devices.tv4kHdr).downloads.single().version.id.value)
        // And a phone that cannot decode HEVC at all is never handed it, however much room there is.
        assertEquals("hd", plan(request(remote(uhd), remote(hd)), device = Devices.oldPhone).downloads.single().version.id.value)
        assertEquals(OfflineSkipReason.NOT_PLAYABLE_HERE, plan(request(remote(uhd)), device = Devices.oldPhone).skipped.single().reason)
    }

    @Test
    fun amongVersionsThisDeviceShowsEquallyTheSmallestIsKept() {
        // A 10-bit encode and an 8-bit one of the same 1080p picture, on a phone that shows 8 bits of either
        // and decodes only one of them: bit depth is not "better", it is a property.
        val fat = version("remux", bytes = 30 * GB)
        val lean = version("encode", bytes = 6 * GB)
        val download = plan(request(remote(fat), remote(lean))).downloads.single()
        assertEquals("encode", download.version.id.value)
        assertFalse(download.madeToFit, "it is the first choice, not a compromise")
        assertEquals(6 * GB, download.bytes)
        assertTrue(download.sizeKnown)
    }

    @Test
    fun whenTheFirstChoiceDoesNotFitTheBestThatDoesIsTaken() {
        val hd = version("hd", 1080, bytes = 8 * GB)
        val sd = version("sd", 720, bytes = 2 * GB)
        val fits = plan(request(remote(hd), remote(sd)), usable = 4 * GB)
        assertEquals("sd", fits.downloads.single().version.id.value)
        assertTrue(fits.downloads.single().madeToFit)
        assertEquals(2 * GB, fits.requiredBytes)

        val nothing = plan(request(remote(hd), remote(sd)), usable = 1 * GB)
        assertTrue(nothing.empty)
        assertEquals(OfflineSkipReason.NOT_ENOUGH_STORAGE, nothing.skipped.single().reason)
    }

    @Test
    fun aPinnedVersionIsDownloadedOrNothingIs() {
        val uhd = version("uhd", 2160, VideoCodec.HEVC, 10, DynamicRange.HDR10, bytes = 40 * GB)
        val hd = version("hd", 1080, bytes = 8 * GB)
        // The user chose the 4K file for this work. That is what is kept, although it is not what Reflux would pick here.
        assertEquals("uhd", plan(request(remote(uhd), remote(hd), pinned = "uhd")).downloads.single().version.id.value)
        // Without the room for it, nothing is put in its place behind the user's back.
        val tight = plan(request(remote(uhd), remote(hd), pinned = "uhd"), usable = 10 * GB)
        assertTrue(tight.empty)
        assertEquals(OfflineSkipReason.NOT_ENOUGH_STORAGE, tight.skipped.single().reason)
        // A pin on something that cannot be had right now is not a reason to keep nothing.
        assertEquals("hd", plan(request(remote(uhd, Availability.UNAVAILABLE), remote(hd), pinned = "uhd")).downloads.single().version.id.value)
    }

    @Test
    fun whatIsAlreadyOnTheDeviceIsNotDownloadedAgain() {
        val local = version("local", source = SourceId("folder"))
        val served = version("served")
        assertEquals(OfflineSkipReason.ALREADY_ON_DEVICE, plan(request(onDevice(local), remote(served))).skipped.single().reason)
        // A file on a drive that is not connected is not "on the device" for someone about to leave the house.
        assertEquals("served", plan(request(onDevice(local, Availability.UNAVAILABLE), remote(served))).downloads.single().version.id.value)
        // A download already on its way is not queued twice.
        assertEquals(OfflineSkipReason.ALREADY_DOWNLOADING, plan(request(remote(served), pending = true)).skipped.single().reason)
    }

    @Test
    fun whyNothingCanBeDownloadedIsSaid() {
        val served = version("served")
        assertEquals(OfflineSkipReason.NOT_DOWNLOADABLE, plan(request(remote(served), downloadable = emptySet())).skipped.single().reason)
        assertEquals(OfflineSkipReason.SOURCE_UNAVAILABLE, plan(request(remote(served, Availability.UNAVAILABLE))).skipped.single().reason)
    }

    @Test
    fun theCutBeingWatchedIsTheCutThatIsKept() {
        val extended = version("extended", bytes = 12 * GB, edition = "Extended Edition", minutes = 154)
        val theatrical = version("theatrical", bytes = 6 * GB, minutes = 137)
        val midway = WatchState(film, positionMs = 100 * MINUTE, durationMs = 154 * MINUTE, cut = "extended", versionId = VersionId("extended"))

        assertEquals("extended", plan(request(remote(extended), remote(theatrical), state = midway)).downloads.single().version.id.value)
        // The shorter cut is not a smaller copy of the longer one: it is not offered in its place.
        val tight = plan(request(remote(extended), remote(theatrical), state = midway), usable = 8 * GB)
        assertEquals(OfflineSkipReason.NOT_ENOUGH_STORAGE, tight.skipped.single().reason)
        // Nor does having the other cut on the device mean this one is already here.
        val other = version("local-theatrical", minutes = 137, source = SourceId("folder"))
        assertEquals("extended", plan(request(onDevice(other), remote(extended), state = midway)).downloads.single().version.id.value)
        // Someone who has not started gets the cut that would play if they pressed Play now: which cut to
        // watch is not decided by which is smaller.
        assertEquals("extended", plan(request(remote(theatrical), remote(extended))).downloads.single().version.id.value)
    }

    @Test
    fun aPlanSpendsTheRoomThereIsInOrderAndNeverTheReserve() {
        val works = (1..4).map { index ->
            val item = MediaId("work$index")
            OfflineRequest(item, if (index == 1) OfflineReason.IN_PROGRESS else OfflineReason.NEXT_EPISODE, listOf(remote(version("v$index", bytes = 3 * GB, item = item))), setOf(VersionId("v$index")))
        }
        // 64 GB device with 12 GB free, 2 GB of which is already promised to a download under way.
        val storage = StorageSnapshot(freeBytes = 12 * GB, totalBytes = 64 * GB, committedBytes = 2 * GB)
        val policy = StoragePolicy()
        assertEquals((3.2 * GB).toLong(), policy.reserveBytes(storage), "5% of the device, which is more than the minimum")
        val plan = OfflinePlanner.plan(works, Devices.laptop1080, storage, policy)
        assertEquals(listOf("work1", "work2"), plan.downloads.map { it.itemId.value }, "what matters most first, while there is room")
        assertEquals(listOf(OfflineReason.IN_PROGRESS, OfflineReason.NEXT_EPISODE), plan.downloads.map { it.reason })
        assertEquals(listOf("work3", "work4"), plan.skipped.map { it.itemId.value })
        assertTrue(plan.skipped.all { it.reason == OfflineSkipReason.NOT_ENOUGH_STORAGE })
        assertEquals(6 * GB, plan.requiredBytes)
        assertEquals(12 * GB - (3.2 * GB).toLong() - 2 * GB, plan.usableBytes)
        assertEquals(12 * GB, plan.freeBytes)
        assertTrue(plan.requiredBytes <= plan.usableBytes)

        // A small device keeps the minimum free, and a full one offers nothing rather than a negative number.
        assertEquals(500L * 1024 * 1024, policy.reserveBytes(StorageSnapshot(freeBytes = GB, totalBytes = 4 * GB)))
        assertEquals(0, policy.usableBytes(StorageSnapshot(freeBytes = 100L * 1024 * 1024, totalBytes = 4 * GB)))
        // The same work asked for twice is one download.
        assertEquals(1, OfflinePlanner.plan(listOf(works[0], works[0]), Devices.laptop1080, room(100 * GB)).downloads.size)
    }

    @Test
    fun aFileOfUnknownSizeIsEstimatedAndSaidToBe() {
        val known = OfflinePlanner.sizeOf(version("a", bytes = 5 * GB))
        assertEquals(5 * GB to true, known)
        // Two hours at 8 Mbit/s is 7.2 GB.
        assertEquals(7_200_000_000L to false, OfflinePlanner.sizeOf(version("b", bytes = 0, bitrate = 8_000_000)))
        val guessed = OfflinePlanner.sizeOf(version("c", bytes = 0))
        assertFalse(guessed.second)
        assertTrue(guessed.first in 3 * GB..6 * GB, "about 2 GB an hour: ${guessed.first}")
        val download = plan(request(remote(version("c", bytes = 0)))).downloads.single()
        assertFalse(download.sizeKnown)
        assertEquals(guessed.first, download.bytes)
    }

    @Test
    fun onlyTheSubtitlesTheViewerReadsComeAlong() {
        data class Sub(val name: String, val language: String?)
        val all = listOf(Sub("english", "eng"), Sub("english forced", "en"), Sub("french", "fre"), Sub("german", "de"), Sub("unlabelled", null), Sub("us", "en-US"))
        assertEquals(listOf("english", "english forced", "unlabelled", "us"), OfflineAssets.subtitles(all, Sub::language, listOf("en")).map { it.name })
        assertEquals(listOf("english", "english forced", "french", "unlabelled", "us"), OfflineAssets.subtitles(all, Sub::language, listOf("fr-FR", "en")).map { it.name })
        assertEquals(all, OfflineAssets.subtitles(all, Sub::language, emptyList()), "with no preference known, nothing is left out")
    }
}
