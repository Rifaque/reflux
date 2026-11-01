package dev.reflux.core.playback

import dev.reflux.core.model.ExternalSubtitle
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaLocation
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.SourceId
import dev.reflux.core.model.StreamInfoOrigin
import dev.reflux.core.model.VersionId
import dev.reflux.core.playback.Support.SUPPORTED
import dev.reflux.core.playback.Support.UNKNOWN
import dev.reflux.core.playback.Support.UNSUPPORTED
import dev.reflux.core.source.SourceCapability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackPlannerTest {
    // Sources, as each adapter declares itself ---------------------------------------------------------------------

    private val local = setOf(SourceCapability.ENUMERATE_FILES, SourceCapability.CHANGE_NOTIFICATIONS)
    private val smb = setOf(SourceCapability.ENUMERATE_FILES, SourceCapability.DOWNLOAD)
    private val webDav = setOf(SourceCapability.ENUMERATE_FILES, SourceCapability.DOWNLOAD)
    private val jellyfin = setOf(
        SourceCapability.PROVIDES_CATALOG, SourceCapability.WATCH_STATE_SYNC, SourceCapability.DOWNLOAD, SourceCapability.SERVER_REMUX,
        SourceCapability.SERVER_AUDIO_CONVERSION, SourceCapability.SERVER_TRANSCODE, SourceCapability.SERVER_SUBTITLE_EXTRACTION,
        SourceCapability.SERVER_SUBTITLE_CONVERSION, SourceCapability.SERVER_BURN_IN,
    )

    // Media ----------------------------------------------------------------------------------------------------------

    private val hevc = VideoStream(VideoCodec.HEVC, 1920, 1080, 10, frameRate = 23.976, profile = VideoProfile.MAIN_10, level = 120)
    private val h264 = VideoStream(VideoCodec.H264, 1920, 1080, 8, frameRate = 23.976, profile = VideoProfile.HIGH, level = 41)
    private val eac3 = AudioStream(AudioCodec.EAC3, 6, "en", default = true, sampleRate = 48_000)
    private val aac = AudioStream(AudioCodec.AAC, 2, "en", default = true, sampleRate = 48_000)
    private val dts = AudioStream(AudioCodec.DTS, 6, "en", default = true, sampleRate = 48_000)
    private val pgs = SubtitleStream(SubtitleFormat.PGS, "en")
    private val srt = SubtitleStream(SubtitleFormat.SRT, "en")

    private fun file(video: VideoStream? = hevc, audio: List<AudioStream> = listOf(eac3), subtitles: List<SubtitleStream> = emptyList(), container: Container = Container.MATROSKA) =
        StreamInfo(container, video, audio, subtitles)

    private fun version(stream: StreamInfo, external: List<ExternalSubtitle> = emptyList(), origin: StreamInfoOrigin = StreamInfoOrigin.PROBE) =
        MediaVersion(VersionId("v"), MediaId("m"), MediaLocation(SourceId("s"), "film.mkv"), 1, 0, stream, origin, externalSubtitles = external)

    private fun sidecar(format: SubtitleFormat) = ExternalSubtitle(MediaLocation(SourceId("s"), "film.en.srt"), format, "en")

    // Clients --------------------------------------------------------------------------------------------------------

    private val decoders = listOf(
        VideoDecoderSupport(VideoCodec.HEVC, 3840, 2160, 10, true, frameAndDepthOnly = true),
        VideoDecoderSupport(VideoCodec.H264, 3840, 2160, 8, true, frameAndDepthOnly = true),
    )
    private val text = mapOf(
        SubtitleFormat.SRT to SubtitleRenderer(SUPPORTED, SUPPORTED, SubtitleFidelity.FULL),
        SubtitleFormat.ASS to SubtitleRenderer(SUPPORTED, SUPPORTED, SubtitleFidelity.PLAIN),
    )
    private val client = DeviceCapabilities(
        videoDecoders = decoders,
        audioDecoders = setOf(AudioCodec.AAC, AudioCodec.AC3, AudioCodec.EAC3),
        maxOutputChannels = 8,
        display = DisplayCapabilities(3840, 2160),
        containers = setOf(Container.MATROSKA, Container.MP4),
        subtitleFormats = emptySet(),
        subtitles = SubtitleCapabilities(text),
    )
    private fun drawing(format: SubtitleFormat, embedded: Support, external: Support) =
        client.copy(subtitles = SubtitleCapabilities(text + (format to SubtitleRenderer(embedded, external))))

    private val preferences = TrackPreferences(listOf("en"))

    private fun plan(
        stream: StreamInfo,
        device: DeviceCapabilities = client,
        source: Set<SourceCapability> = local,
        audio: AudioChoice = AudioChoice.Automatic,
        subtitle: SubtitleChoice = SubtitleChoice.Off,
        external: List<ExternalSubtitle> = emptyList(),
        language: List<String> = listOf("en"),
        origin: StreamInfoOrigin = StreamInfoOrigin.PROBE,
    ) = PlaybackPlanner.plan(PlaybackRequirements(version(stream, external, origin), preferences.copy(languages = language), source, audio, subtitle), device)

    // Delivery -------------------------------------------------------------------------------------------------------

    @Test
    fun whatTheClientCanTakeIsPlayedAsItIs() {
        val plan = plan(file())
        assertEquals(PlanOutcome.PLAYS, plan.outcome)
        assertEquals(DeliveryMode.DIRECT_PLAY, plan.delivery)
        assertEquals(VideoPlan.ORIGINAL_HARDWARE_DECODE, plan.video)
        assertEquals(AudioPlan.ORIGINAL_LOCAL_DECODE, plan.audio)
        assertEquals(emptySet(), plan.unverified)
    }

    @Test
    fun aServerThatCouldConvertDoesNotWhenNothingNeedsIt() {
        assertEquals(plan(file()), plan(file(), source = jellyfin))
    }

    @Test
    fun aContainerAloneIsRemuxedAndTheStreamsKept() {
        val plan = plan(file(container = Container.AVI), source = jellyfin)
        assertEquals(DeliveryMode.REMUX, plan.delivery)
        assertEquals(VideoPlan.ORIGINAL_HARDWARE_DECODE, plan.video)
        assertEquals(AudioPlan.ORIGINAL_LOCAL_DECODE, plan.audio)
        assertEquals(listOf(PlanReason.CONTAINER_NOT_DEMUXABLE), plan.reasons)
    }

    @Test
    fun aContainerNobodyCanRemuxCannotPlay() {
        val plan = plan(file(container = Container.AVI), source = local)
        assertEquals(PlanOutcome.CANNOT_PLAY, plan.outcome)
        assertEquals(listOf(PlanReason.CONTAINER_NOT_DEMUXABLE, PlanReason.NO_REMUX_AVAILABLE), plan.reasons)
    }

    @Test
    fun audioThatCannotPlayConvertsOnlyTheAudio() {
        val plan = plan(file(audio = listOf(dts)), source = jellyfin)
        assertEquals(DeliveryMode.AUDIO_CONVERSION, plan.delivery)
        assertEquals(VideoPlan.ORIGINAL_HARDWARE_DECODE, plan.video, "video is never transcoded for audio")
        assertEquals(AudioPlan.CONVERT, plan.audio)
        assertEquals(listOf(PlanReason.AUDIO_NOT_PLAYABLE), plan.reasons)
    }

    @Test
    fun videoNobodyCanDecodeIsTranscodedAndTheAudioKept() {
        val plan = plan(file(video = hevc.copy(codec = VideoCodec.AV1)), source = jellyfin)
        assertEquals(DeliveryMode.VIDEO_TRANSCODE, plan.delivery)
        assertEquals(VideoPlan.TRANSCODE, plan.video)
        assertEquals(AudioPlan.ORIGINAL_LOCAL_DECODE, plan.audio)
        assertEquals(listOf(PlanReason.VIDEO_NOT_DECODABLE), plan.reasons)
    }

    @Test
    fun aTranscodeCoversAContainerProblemToo() {
        val plan = plan(file(video = hevc.copy(codec = VideoCodec.AV1), container = Container.AVI), source = jellyfin)
        assertEquals(DeliveryMode.VIDEO_TRANSCODE, plan.delivery)
        assertTrue(PlanReason.CONTAINER_NOT_DEMUXABLE in plan.reasons && PlanReason.VIDEO_NOT_DECODABLE in plan.reasons)
    }

    @Test
    fun transcodingTheVideoCanLeaveConvertingTheAudioToAnotherAbility() {
        val video = hevc.copy(codec = VideoCodec.AV1)
        val noAudio = jellyfin - SourceCapability.SERVER_AUDIO_CONVERSION
        val plan = plan(file(video = video, audio = listOf(dts)), source = noAudio)
        assertEquals(VideoPlan.TRANSCODE, plan.video)
        assertEquals(PlanOutcome.PLAYS_WITHOUT_AUDIO, plan.outcome)
    }

    // Video ----------------------------------------------------------------------------------------------------------

    private fun soft(vararg decoders: VideoDecoderSupport) = client.copy(videoDecoders = decoders.toList())
    private val softwareHevc = VideoDecoderSupport(VideoCodec.HEVC, 8192, 4320, 12, false, frameAndDepthOnly = true)

    @Test
    fun softwareDecodingIsStillTheOriginal() {
        val plan = plan(file(), soft(softwareHevc), jellyfin)
        assertEquals(DeliveryMode.DIRECT_PLAY, plan.delivery, "hardware is no condition of Direct Play")
        assertEquals(VideoPlan.ORIGINAL_SOFTWARE_DECODE, plan.video)
        assertEquals(listOf(PlanReason.VIDEO_SOFTWARE_DECODE), plan.reasons)
    }

    @Test
    fun softwareDecodingAvoidsAServerTranscode() {
        val tooSmall = VideoDecoderSupport(VideoCodec.HEVC, 1280, 720, 10, true, frameAndDepthOnly = true)
        val plan = plan(file(), soft(tooSmall, softwareHevc), jellyfin)
        assertEquals(VideoPlan.ORIGINAL_SOFTWARE_DECODE, plan.video)
        assertEquals(DeliveryMode.DIRECT_PLAY, plan.delivery)
    }

    @Test
    fun neitherDecoderMeansTheServerOrNothing() {
        val eightBit = VideoDecoderSupport(VideoCodec.HEVC, 3840, 2160, 8, true, frameAndDepthOnly = true)
        assertEquals(VideoPlan.TRANSCODE, plan(file(), soft(eightBit), jellyfin).video)
        val none = plan(file(), soft(eightBit), smb)
        assertEquals(PlanOutcome.CANNOT_PLAY, none.outcome)
        assertEquals(listOf(PlanReason.VIDEO_NOT_DECODABLE, PlanReason.NO_TRANSCODE_AVAILABLE), none.reasons)
    }

    @Test
    fun aDecoderNotYetKnownToBeHardwareIsNotCalledEither() {
        val maybe = VideoDecoderSupport(VideoCodec.HEVC, 3840, 2160, 10, null)
        val plan = plan(file(), soft(maybe), jellyfin)
        assertEquals(VideoPlan.ORIGINAL_DECODER_UNDECIDED, plan.video)
        assertEquals(DeliveryMode.DIRECT_PLAY, plan.delivery, "unknown is tried, not converted")
        assertEquals(setOf(PlanPart.VIDEO), plan.unverified)
    }

    @Test
    fun softwareForCertainWithHardwareUnknownNeedsNoVerifying() {
        val plan = plan(file(), soft(VideoDecoderSupport(VideoCodec.HEVC, 3840, 2160, 10, null), softwareHevc))
        assertEquals(VideoPlan.ORIGINAL_DECODER_UNDECIDED, plan.video)
        assertEquals(emptySet(), plan.unverified, "it decodes either way")
    }

    @Test
    fun aDeclaredLevelIsTriedAndNamedNotTranscodedAround() {
        val declared = VideoDecoderSupport(VideoCodec.HEVC, 3840, 2160, 10, true, mapOf(VideoProfile.MAIN_10 to 93), listOf(FrameRateLimit(1920, 1080, 60.0)))
        val plan = plan(file(), soft(declared), jellyfin)
        assertEquals(DeliveryMode.DIRECT_PLAY, plan.delivery)
        assertEquals(listOf(PlanReason.VIDEO_PAST_DECLARED_LIMIT), plan.reasons)
        assertEquals(setOf(PlanPart.VIDEO), plan.unverified)
    }

    @Test
    fun aFileDescribedByNothingIsTried() {
        val plan = plan(StreamInfo(), origin = StreamInfoOrigin.FILENAME_HINTS)
        assertEquals(PlanOutcome.PLAYS, plan.outcome)
        assertEquals(DeliveryMode.DIRECT_PLAY, plan.delivery)
        assertEquals(setOf(PlanPart.CONTAINER, PlanPart.VIDEO, PlanPart.AUDIO), plan.unverified)
    }

    // Audio ----------------------------------------------------------------------------------------------------------

    private val french = AudioStream(AudioCodec.AC3, 6, "fr", sampleRate = 48_000)
    private val frenchDefaultDts = listOf(dts, french)

    @Test
    fun aSelectedTrackThatPlaysIsUsed() {
        val plan = plan(file(audio = listOf(eac3, french)), audio = AudioChoice.Explicit(1))
        assertEquals(1, plan.audioIndex)
        assertEquals(AudioPlan.ORIGINAL_LOCAL_DECODE, plan.audio)
    }

    @Test
    fun aSelectedTrackThatCannotPlayIsConvertedRatherThanSwapped() {
        val plan = plan(file(audio = frenchDefaultDts), source = jellyfin, audio = AudioChoice.Explicit(0))
        assertEquals(0, plan.audioIndex)
        assertEquals(AudioPlan.CONVERT, plan.audio)
        assertEquals(DeliveryMode.AUDIO_CONVERSION, plan.delivery)
    }

    @Test
    fun aSelectedTrackNothingCanPlayIsUnavailableAndNotSilentlyAnotherLanguage() {
        val plan = plan(file(audio = frenchDefaultDts), source = smb, audio = AudioChoice.Explicit(0))
        assertEquals(PlanOutcome.PLAYS_WITHOUT_AUDIO, plan.outcome)
        assertEquals(AudioPlan.NONE, plan.audio)
        assertEquals(0, plan.audioIndex, "the plan still names the track that was asked for")
        assertEquals(listOf(PlanReason.AUDIO_UNAVAILABLE), plan.reasons)
    }

    @Test
    fun aSelectedTrackThatIsGoneIsUnavailable() {
        assertEquals(PlanOutcome.PLAYS_WITHOUT_AUDIO, plan(file(), audio = AudioChoice.Explicit(7)).outcome)
    }

    @Test
    fun automaticallyAnotherTrackOfTheLanguageIsBetterThanAConversion() {
        val tracks = listOf(dts, AudioStream(AudioCodec.AC3, 6, "en"))
        val plan = plan(file(audio = tracks), source = jellyfin)
        assertEquals(1, plan.audioIndex)
        assertEquals(DeliveryMode.DIRECT_PLAY, plan.delivery)
        assertEquals(listOf(PlanReason.AUDIO_OTHER_TRACK_SAME_LANGUAGE), plan.reasons)
    }

    @Test
    fun automaticallyAConversionIsBetterThanAnotherLanguage() {
        val plan = plan(file(audio = frenchDefaultDts), source = jellyfin)
        assertEquals(0, plan.audioIndex)
        assertEquals(AudioPlan.CONVERT, plan.audio)
    }

    @Test
    fun automaticallyAnotherLanguageIsChosenOnlyWhereNothingElsePlaysAndIsSaid() {
        val plan = plan(file(audio = frenchDefaultDts), source = local)
        assertEquals(1, plan.audioIndex)
        assertEquals(AudioPlan.ORIGINAL_LOCAL_DECODE, plan.audio)
        assertEquals(listOf(PlanReason.AUDIO_OTHER_LANGUAGE), plan.reasons)
    }

    @Test
    fun automaticallyNothingPlayableIsUnavailable() {
        val plan = plan(file(audio = listOf(dts, AudioStream(AudioCodec.TRUEHD, 8, "fr"))), source = local)
        assertEquals(PlanOutcome.PLAYS_WITHOUT_AUDIO, plan.outcome)
        assertEquals(listOf(PlanReason.AUDIO_UNAVAILABLE), plan.reasons)
    }

    @Test
    fun automaticSelectionIsTrackSelectionsOwnWhereTheTrackPlays() {
        val plan = plan(file(audio = listOf(french, eac3)), language = listOf("en"))
        assertEquals(1, plan.audioIndex)
        assertEquals(0, plan(file(audio = listOf(french, eac3)), language = listOf("fr")).audioIndex)
    }

    @Test
    fun passthroughIsPreferredWhereItIsThere() {
        val receiver = client.copy(audioPassthrough = setOf(AudioCodec.EAC3), atmosPassthrough = SUPPORTED)
        val plan = plan(file(audio = listOf(eac3.copy(atmos = true))), receiver)
        assertEquals(AudioPlan.ORIGINAL_PASSTHROUGH, plan.audio)
        assertEquals(listOf(PlanReason.AUDIO_PASSTHROUGH), plan.reasons)
    }

    @Test
    fun passthroughAloneCarriesWhatNoDecoderTakes() {
        val receiver = client.copy(audioPassthrough = setOf(AudioCodec.DTS))
        assertEquals(AudioPlan.ORIGINAL_PASSTHROUGH, plan(file(audio = listOf(dts)), receiver, jellyfin).audio)
    }

    @Test
    fun withoutPassthroughADecodeSaysWhatIsLost() {
        val stereo = client.copy(maxOutputChannels = 2)
        val plan = plan(file(audio = listOf(eac3.copy(atmos = true))), stereo)
        assertEquals(AudioPlan.ORIGINAL_LOCAL_DECODE, plan.audio)
        assertEquals(listOf(PlanReason.AUDIO_LOSES_ATMOS, PlanReason.AUDIO_DOWNMIXED), plan.reasons)
        assertEquals(DeliveryMode.DIRECT_PLAY, plan.delivery, "and neither is a reason to convert anything")
    }

    @Test
    fun audioThatMayPlayThroughPassthroughIsTriedNotConverted() {
        val unsure = client.copy(audioPassthroughKnown = false)
        val plan = plan(file(audio = listOf(dts)), unsure, jellyfin)
        assertEquals(DeliveryMode.DIRECT_PLAY, plan.delivery)
        assertEquals(AudioPlan.ORIGINAL_PASSTHROUGH, plan.audio)
        assertEquals(setOf(PlanPart.AUDIO), plan.unverified)
    }

    @Test
    fun audioOfNoNamedCodecIsTried() {
        val plan = plan(file(audio = listOf(AudioStream())), source = jellyfin)
        assertEquals(DeliveryMode.DIRECT_PLAY, plan.delivery)
        assertEquals(setOf(PlanPart.AUDIO), plan.unverified)
    }

    @Test
    fun automaticallyATrackThatCertainlyPlaysBeatsOneOfTheSameLanguageThatMight() {
        val tracks = listOf(AudioStream(language = "en", default = true), AudioStream(AudioCodec.AC3, 6, "en"))
        val plan = plan(file(audio = tracks))
        assertEquals(1, plan.audioIndex)
        assertEquals(emptySet(), plan.unverified)
    }

    // Subtitles ------------------------------------------------------------------------------------------------------

    private val chosen = SubtitleChoice.Explicit(0)

    @Test
    fun aTextSubtitleInTheFileIsDrawnHere() {
        val plan = plan(file(subtitles = listOf(srt)), subtitle = chosen)
        assertEquals(SubtitlePlan.LOCAL_RENDER, plan.subtitles)
        assertEquals(0, plan.subtitleIndex)
    }

    @Test
    fun aBitmapSubtitleInTheFileIsDrawnWhereTheRendererIsKnown() {
        val plan = plan(file(subtitles = listOf(pgs)), drawing(SubtitleFormat.PGS, SUPPORTED, UNKNOWN), subtitle = chosen)
        assertEquals(SubtitlePlan.LOCAL_RENDER, plan.subtitles)
        assertEquals(emptySet(), plan.unverified)
    }

    @Test
    fun aFileBesideTheVideoIsExternal() {
        val plan = plan(file(), subtitle = SubtitleChoice.Explicit(0, external = true), external = listOf(sidecar(SubtitleFormat.SRT)))
        assertEquals(SubtitlePlan.EXTERNAL, plan.subtitles)
        assertTrue(plan.subtitleExternal)
    }

    @Test
    fun aServerExtractsWhatThisClientDrawsOnlyAsAFile() {
        val plan = plan(file(subtitles = listOf(pgs)), drawing(SubtitleFormat.PGS, UNSUPPORTED, SUPPORTED), jellyfin, subtitle = chosen)
        assertEquals(SubtitlePlan.EXTERNAL, plan.subtitles)
        assertEquals(DeliveryMode.DIRECT_PLAY, plan.delivery)
        assertEquals(listOf(PlanReason.SUBTITLE_NOT_DRAWABLE), plan.reasons)
    }

    @Test
    fun aTextFormatThisClientCannotDrawIsConverted() {
        val ttml = SubtitleStream(SubtitleFormat.TTML, "en")
        val plan = plan(file(subtitles = listOf(ttml)), drawing(SubtitleFormat.TTML, UNSUPPORTED, UNSUPPORTED), jellyfin, subtitle = chosen)
        assertEquals(SubtitlePlan.CONVERT, plan.subtitles)
        assertEquals(DeliveryMode.DIRECT_PLAY, plan.delivery)
    }

    @Test
    fun aBitmapSubtitleIsNeverConvertedToText() {
        val plan = plan(file(subtitles = listOf(pgs)), drawing(SubtitleFormat.PGS, UNSUPPORTED, UNSUPPORTED), jellyfin - SourceCapability.SERVER_BURN_IN, subtitle = chosen)
        assertEquals(SubtitlePlan.NONE, plan.subtitles)
        assertTrue(PlanReason.SUBTITLE_UNAVAILABLE in plan.reasons)
    }

    @Test
    fun burningInIsForATrackTheViewerChoseThatNothingElseCanShow() {
        val plan = plan(file(subtitles = listOf(pgs)), drawing(SubtitleFormat.PGS, UNSUPPORTED, UNSUPPORTED), jellyfin, subtitle = chosen)
        assertEquals(SubtitlePlan.BURN_IN, plan.subtitles)
        assertEquals(VideoPlan.TRANSCODE, plan.video)
        assertEquals(DeliveryMode.VIDEO_TRANSCODE, plan.delivery)
        assertTrue(PlanReason.SUBTITLE_BURNED_IN in plan.reasons)
    }

    @Test
    fun aTrackChosenAutomaticallyIsNotWorthTranscodingFor() {
        val forced = SubtitleStream(SubtitleFormat.PGS, "en", forced = true)
        val plan = plan(file(subtitles = listOf(forced)), drawing(SubtitleFormat.PGS, UNSUPPORTED, UNSUPPORTED), jellyfin, subtitle = SubtitleChoice.Automatic)
        assertEquals(SubtitlePlan.NONE, plan.subtitles)
        assertEquals(DeliveryMode.DIRECT_PLAY, plan.delivery)
        assertTrue(PlanReason.SUBTITLE_UNAVAILABLE in plan.reasons)
    }

    @Test
    fun anUnknownRendererIsTriedAndNeverBurnedIn() {
        val plan = plan(file(subtitles = listOf(pgs)), drawing(SubtitleFormat.PGS, UNKNOWN, UNKNOWN), jellyfin, subtitle = chosen)
        assertEquals(SubtitlePlan.LOCAL_RENDER, plan.subtitles)
        assertEquals(setOf(PlanPart.SUBTITLES), plan.unverified)
        assertEquals(VideoPlan.ORIGINAL_HARDWARE_DECODE, plan.video)
    }

    @Test
    fun pgsIsNotClaimedForBeingInTheFile() {
        // No word on the format at all: it is tried, and the plan says that is all it is.
        val plan = plan(file(subtitles = listOf(pgs)), subtitle = chosen)
        assertEquals(setOf(PlanPart.SUBTITLES), plan.unverified)
    }

    @Test
    fun aSubtitleNothingCanShowIsSaid() {
        val plan = plan(file(subtitles = listOf(pgs)), drawing(SubtitleFormat.PGS, UNSUPPORTED, UNSUPPORTED), smb, subtitle = chosen)
        assertEquals(SubtitlePlan.NONE, plan.subtitles)
        assertEquals(listOf(PlanReason.SUBTITLE_NOT_DRAWABLE, PlanReason.SUBTITLE_UNAVAILABLE), plan.reasons)
        assertEquals(PlanOutcome.PLAYS, plan.outcome, "a subtitle does not stop a film")
    }

    @Test
    fun aSidecarNothingCanDrawIsSaid() {
        val plan = plan(file(), drawing(SubtitleFormat.VOBSUB, UNSUPPORTED, UNSUPPORTED), local, subtitle = SubtitleChoice.Explicit(0, external = true), external = listOf(sidecar(SubtitleFormat.VOBSUB)))
        assertEquals(SubtitlePlan.NONE, plan.subtitles)
        assertTrue(PlanReason.SUBTITLE_UNAVAILABLE in plan.reasons)
    }

    @Test
    fun aSubtitleThatIsGoneIsSaid() {
        val plan = plan(file(), subtitle = SubtitleChoice.Explicit(3))
        assertEquals(SubtitlePlan.NONE, plan.subtitles)
        assertEquals(listOf(PlanReason.SUBTITLE_UNAVAILABLE), plan.reasons)
    }

    @Test
    fun subtitlesOffAreOff() {
        val plan = plan(file(subtitles = listOf(srt)), subtitle = SubtitleChoice.Off)
        assertEquals(SubtitlePlan.NONE, plan.subtitles)
        assertNull(plan.subtitleIndex)
        assertEquals(emptyList(), plan.reasons)
    }

    @Test
    fun automaticSubtitlesFollowTrackSelection() {
        val forced = SubtitleStream(SubtitleFormat.SRT, "en", forced = true)
        assertEquals(SubtitlePlan.LOCAL_RENDER, plan(file(subtitles = listOf(forced)), subtitle = SubtitleChoice.Automatic).subtitles)
        val auto = PlaybackPlanner.plan(
            PlaybackRequirements(version(file(subtitles = listOf(srt))), TrackPreferences(listOf("en")), local), client,
        )
        assertEquals(SubtitlePlan.NONE, auto.subtitles, "a viewer who understands the audio needs no full subtitles")
        val file = PlaybackPlanner.plan(
            PlaybackRequirements(version(file(audio = listOf(french)), listOf(sidecar(SubtitleFormat.SRT))), TrackPreferences(listOf("en")), local), client,
        )
        assertEquals(SubtitlePlan.EXTERNAL, file.subtitles, "audio in another language, and the file beside the video")
    }

    // Combinations ---------------------------------------------------------------------------------------------------

    @Test
    fun hevcMainTenWithEac3OnAClientThatDecodesBoth() {
        val plan = plan(file(), client, jellyfin)
        assertEquals(DeliveryMode.DIRECT_PLAY, plan.delivery)
        assertEquals(VideoPlan.ORIGINAL_HARDWARE_DECODE, plan.video)
        assertEquals(AudioPlan.ORIGINAL_LOCAL_DECODE, plan.audio)
    }

    @Test
    fun hevcWithUnsupportedAudioKeepsTheHevc() {
        val plan = plan(file(audio = listOf(dts)), client, jellyfin)
        assertEquals(DeliveryMode.AUDIO_CONVERSION, plan.delivery)
        assertEquals(VideoPlan.ORIGINAL_HARDWARE_DECODE, plan.video)
    }

    @Test
    fun hevcWithPgsOnTheOriginalDrawsItHere() {
        val plan = plan(file(subtitles = listOf(pgs)), drawing(SubtitleFormat.PGS, SUPPORTED, UNSUPPORTED), jellyfin, subtitle = chosen)
        assertEquals(DeliveryMode.DIRECT_PLAY, plan.delivery)
        assertEquals(SubtitlePlan.LOCAL_RENDER, plan.subtitles)
    }

    @Test
    fun aConvertedStreamCarriesNoEmbeddedSubtitleSoPgsTravelsBesideIt() {
        // The live finding: this is what stays an audio-only problem.
        val plan = plan(file(audio = listOf(dts), subtitles = listOf(pgs)), drawing(SubtitleFormat.PGS, SUPPORTED, SUPPORTED), jellyfin, subtitle = chosen)
        assertEquals(DeliveryMode.AUDIO_CONVERSION, plan.delivery)
        assertEquals(VideoPlan.ORIGINAL_HARDWARE_DECODE, plan.video)
        assertEquals(SubtitlePlan.EXTERNAL, plan.subtitles)
        assertEquals(listOf(PlanReason.AUDIO_NOT_PLAYABLE, PlanReason.SUBTITLE_NOT_IN_STREAM), plan.reasons)
    }

    @Test
    fun whereAConvertedStreamLeavesAChosenPgsNoOtherWayItIsTheViewersBurnIn() {
        val plan = plan(file(audio = listOf(dts), subtitles = listOf(pgs)), drawing(SubtitleFormat.PGS, SUPPORTED, UNSUPPORTED), jellyfin, subtitle = chosen)
        assertEquals(SubtitlePlan.BURN_IN, plan.subtitles)
        assertEquals(DeliveryMode.VIDEO_TRANSCODE, plan.delivery)
        assertEquals(AudioPlan.CONVERT, plan.audio)
        val automatic = plan(file(audio = listOf(dts), subtitles = listOf(pgs.copy(forced = true))), drawing(SubtitleFormat.PGS, SUPPORTED, UNSUPPORTED), jellyfin, subtitle = SubtitleChoice.Automatic)
        assertEquals(DeliveryMode.AUDIO_CONVERSION, automatic.delivery, "and nobody else's")
    }

    @Test
    fun h264WithAacIsTheEasyCase() {
        val plan = plan(file(video = h264, audio = listOf(aac), container = Container.MP4), source = webDav)
        assertEquals(PlanOutcome.PLAYS, plan.outcome)
        assertEquals(DeliveryMode.DIRECT_PLAY, plan.delivery)
        assertEquals(emptyList(), plan.reasons)
    }

    // Range ----------------------------------------------------------------------------------------------------------

    private val hdr = hevc.copy(dynamicRange = DynamicRange.HDR10)

    @Test
    fun aRangeTheDisplayShowsIsPreserved() {
        val display = client.copy(display = DisplayCapabilities(3840, 2160, setOf(DynamicRange.SDR, DynamicRange.HDR10)))
        assertEquals(RangePlan.PRESERVED, plan(file(video = hdr), display).range)
    }

    @Test
    fun aRangeIsAdaptedHereAndNeverConvertedForTheDisplay() {
        val mapped = client.copy(display = DisplayCapabilities(1920, 1080, setOf(DynamicRange.SDR), canToneMap = true))
        val plan = plan(file(video = hdr), mapped, jellyfin)
        assertEquals(RangePlan.TONE_MAPPED, plan.range)
        assertEquals(DeliveryMode.DIRECT_PLAY, plan.delivery)
        val neither = plan(file(video = hdr), client, jellyfin)
        assertEquals(RangePlan.NOT_ADAPTED, neither.range)
        assertEquals(DeliveryMode.DIRECT_PLAY, neither.delivery, "a server would not make it better")
        assertTrue(PlanReason.RANGE_NOT_SHOWN in neither.reasons)
    }

    @Test
    fun dolbyVisionFallsBackToItsBaseLayer() {
        val video = hevc.copy(dynamicRange = DynamicRange.DOLBY_VISION, dolbyVisionProfile = 8)
        val device = client.copy(display = DisplayCapabilities(3840, 2160, setOf(DynamicRange.SDR, DynamicRange.HDR10, DynamicRange.DOLBY_VISION)), dolbyVisionProfiles = setOf(5))
        assertEquals(RangePlan.BASE_LAYER, plan(file(video = video), device).range)
    }

    @Test
    fun aDisplayThatCouldNotBeAskedIsAssumedAndNamed() {
        val unasked = client.copy(display = DisplayCapabilities(1920, 1080, dynamicRangesKnown = false))
        val plan = plan(file(video = hdr), unasked)
        assertEquals(RangePlan.PRESERVED, plan.range)
        assertEquals(setOf(PlanPart.DISPLAY), plan.unverified)
    }

    @Test
    fun aTranscodeSaysTheRangeIsAtRisk() {
        val plan = plan(file(video = hdr.copy(codec = VideoCodec.AV1)), source = jellyfin)
        assertEquals(RangePlan.CONVERTED, plan.range)
        assertTrue(PlanReason.RANGE_AT_RISK_IN_TRANSCODE in plan.reasons)
    }

    @Test
    fun aContainerThatCannotSeekIsSaidAndStillPlays() {
        val device = client.copy(containerSeek = mapOf(Container.MATROSKA to UNSUPPORTED))
        val plan = plan(file(), device, jellyfin)
        assertEquals(DeliveryMode.DIRECT_PLAY, plan.delivery)
        assertEquals(listOf(PlanReason.SEEKING_LIMITED), plan.reasons)
    }

    // Sources --------------------------------------------------------------------------------------------------------

    @Test
    fun aSourceWithNoServerDescribesClientHandlingOnly() {
        for (source in listOf(local, smb, webDav)) {
            val fine = plan(file(), source = source)
            assertEquals(DeliveryMode.DIRECT_PLAY, fine.delivery, "$source")
            assertEquals(PlanOutcome.PLAYS_WITHOUT_AUDIO, plan(file(audio = listOf(dts)), source = source).outcome, "$source")
            assertEquals(PlanOutcome.CANNOT_PLAY, plan(file(container = Container.AVI), source = source).outcome, "$source")
            assertEquals(PlanOutcome.CANNOT_PLAY, plan(file(video = hevc.copy(codec = VideoCodec.AV1)), source = source).outcome, "$source")
            val everything = listOf(plan(file(audio = listOf(dts), subtitles = listOf(pgs)), source = source, subtitle = chosen))
            assertTrue(everything.none { it.delivery != DeliveryMode.DIRECT_PLAY }, "$source never plans a conversion")
        }
    }

    @Test
    fun jellyfinDescribesWhatTheServerWouldDo() {
        assertEquals(DeliveryMode.REMUX, plan(file(container = Container.AVI), source = jellyfin).delivery)
        assertEquals(DeliveryMode.AUDIO_CONVERSION, plan(file(audio = listOf(dts)), source = jellyfin).delivery)
        assertEquals(DeliveryMode.VIDEO_TRANSCODE, plan(file(video = hevc.copy(codec = VideoCodec.AV1)), source = jellyfin).delivery)
    }

    @Test
    fun aServerCanDeclareOnlySomeOfIt() {
        val onlyAudio = setOf(SourceCapability.SERVER_AUDIO_CONVERSION)
        assertEquals(DeliveryMode.AUDIO_CONVERSION, plan(file(audio = listOf(dts)), source = onlyAudio).delivery)
        assertEquals(PlanOutcome.CANNOT_PLAY, plan(file(container = Container.AVI), source = onlyAudio).outcome)
    }

    // One answer to "can this play" ----------------------------------------------------------------------------------

    @Test
    fun theSameQuestionGetsTheSameAnswerFromTheAssessorAndThePlanner() {
        val devices = listOf(client, Devices.tv4kHdr, Devices.laptop1080, Devices.oldPhone, client.copy(audioPassthroughKnown = false), client.copy(videoDecoders = emptyList()))
        val videos = listOf(hevc, h264, null, hevc.copy(codec = VideoCodec.AV1), hevc.copy(width = 7680, height = 4320), h264.copy(bitDepth = 10), VideoStream(), hevc.copy(dynamicRange = DynamicRange.DOLBY_VISION, dolbyVisionProfile = 5))
        val audios = listOf(emptyList(), listOf(eac3), listOf(dts), listOf(AudioStream()), frenchDefaultDts)
        val containers = listOf(Container.MATROSKA, Container.AVI, Container.UNKNOWN)
        for (device in devices) for (video in videos) for (audio in audios) for (container in containers) {
            val stream = StreamInfo(container, video, audio)
            val assessed = PlaybackAssessor.assess(stream, device)
            val planned = plan(stream, device, local)
            assertEquals(assessed.verdict == PlaybackVerdict.UNSUPPORTED, planned.outcome == PlanOutcome.CANNOT_PLAY, "$stream on $device")
        }
    }

    @Test
    fun plansAreDeterministic() {
        val stream = file(audio = frenchDefaultDts, subtitles = listOf(pgs, srt))
        assertEquals(plan(stream, source = jellyfin, subtitle = chosen), plan(stream, source = jellyfin, subtitle = chosen))
        assertFalse(plan(stream, source = jellyfin, subtitle = chosen) == plan(stream, source = local, subtitle = chosen))
    }
}
