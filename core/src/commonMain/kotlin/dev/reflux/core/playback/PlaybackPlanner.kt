package dev.reflux.core.playback

import dev.reflux.core.model.ExternalSubtitle
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.StreamInfoOrigin
import dev.reflux.core.source.SourceCapability

/** Which audio track is wanted. [Explicit] is a viewer's choice, by position in `StreamInfo.audio`. */
sealed interface AudioChoice {
    data object Automatic : AudioChoice
    data class Explicit(val index: Int) : AudioChoice
}

/** Which subtitle is wanted. [Explicit] is a viewer's choice, by position among embedded or external tracks. */
sealed interface SubtitleChoice {
    data object Off : SubtitleChoice
    data object Automatic : SubtitleChoice
    data class Explicit(val index: Int, val external: Boolean = false) : SubtitleChoice
}

/**
 * What the planner is asked about. Facts are the [version]'s, the source is only what it declares, and the client is
 * passed beside this: the four kinds of thing in docs/PLAYBACK.md stay apart.
 */
data class PlaybackRequirements(
    val version: MediaVersion,
    val preferences: TrackPreferences,
    /** What the source holding [version] declares. A source with no `SERVER_` capability converts nothing. */
    val source: Set<SourceCapability> = emptySet(),
    val audio: AudioChoice = AudioChoice.Automatic,
    val subtitle: SubtitleChoice = SubtitleChoice.Automatic,
)

enum class PlanOutcome {
    /** Picture and, where the media has it, sound. Check [PlannedPlayback.unverified] for what rests on unknowns. */
    PLAYS,

    /** The picture plays and the wanted audio cannot. Said, never hidden behind another language. */
    PLAYS_WITHOUT_AUDIO,

    /** Nothing this client and source can do plays this version. */
    CANNOT_PLAY,
}

enum class VideoPlan {
    ORIGINAL_HARDWARE_DECODE,
    ORIGINAL_SOFTWARE_DECODE,

    /** The original, by a decoder that cannot be named yet: which one is the engine's to find out. */
    ORIGINAL_DECODER_UNDECIDED,
    TRANSCODE,
    NONE,
}

enum class AudioPlan { ORIGINAL_LOCAL_DECODE, ORIGINAL_PASSTHROUGH, CONVERT, NONE }

enum class SubtitlePlan {
    /** In the delivered stream, drawn by this client. */
    LOCAL_RENDER,

    /** A file beside the stream, drawn by this client: a sidecar, or a track the source extracts. */
    EXTERNAL,

    /** The source converts it to a text format this client draws. */
    CONVERT,

    /** The source draws it into the picture. Costs a video transcode. */
    BURN_IN,
    NONE,
}

/** What becomes of the media's dynamic range. */
enum class RangePlan { PRESERVED, BASE_LAYER, TONE_MAPPED, NOT_ADAPTED, CONVERTED }

/** The parts of a plan that can rest on something unknown. */
enum class PlanPart { CONTAINER, VIDEO, AUDIO, SUBTITLES, DISPLAY }

/** Why a plan is what it is. Stable names, for tests and diagnostics; the viewer's words are elsewhere. */
enum class PlanReason {
    CONTAINER_NOT_DEMUXABLE,
    NO_REMUX_AVAILABLE,
    VIDEO_NOT_DECODABLE,
    NO_TRANSCODE_AVAILABLE,
    VIDEO_SOFTWARE_DECODE,
    VIDEO_PAST_DECLARED_LIMIT,
    AUDIO_NOT_PLAYABLE,
    AUDIO_PASSTHROUGH,
    AUDIO_UNAVAILABLE,
    AUDIO_OTHER_TRACK_SAME_LANGUAGE,
    AUDIO_OTHER_LANGUAGE,
    AUDIO_LOSES_ATMOS,
    AUDIO_DOWNMIXED,
    SUBTITLE_NOT_IN_STREAM,
    SUBTITLE_NOT_DRAWABLE,
    SUBTITLE_BURNED_IN,
    SUBTITLE_UNAVAILABLE,
    SEEKING_LIMITED,
    RANGE_NOT_SHOWN,
    RANGE_AT_RISK_IN_TRANSCODE,
}

/**
 * What Reflux should do with one version on this client: a decision, not a record. What really happened is
 * [PlaybackEvidence]; [delivery] uses its words so that the two can be compared. Nothing executes this yet.
 *
 * Original delivery says nothing about decoding: [video] answers that separately.
 *
 * @property delivery [DeliveryMode.UNKNOWN] only when [outcome] is [PlanOutcome.CANNOT_PLAY].
 * @property audioIndex the chosen track's position in `StreamInfo.audio`.
 * @property subtitleIndex the chosen track's position among embedded or external tracks, per [subtitleExternal].
 * @property unverified what this plan assumes rather than knows: trying it is the way to find out.
 */
data class PlannedPlayback(
    val outcome: PlanOutcome,
    val delivery: DeliveryMode,
    val video: VideoPlan,
    val audio: AudioPlan,
    val subtitles: SubtitlePlan,
    val range: RangePlan,
    val audioIndex: Int? = null,
    val subtitleIndex: Int? = null,
    val subtitleExternal: Boolean = false,
    val reasons: List<PlanReason> = emptyList(),
    val unverified: Set<PlanPart> = emptySet(),
)

/**
 * The least destructive viable way to play a version. Pure and deterministic; the order of its rules is in
 * docs/PLAYBACK.md, section 7. Unknown never causes a conversion and never blocks: it is tried, and named in
 * [PlannedPlayback.unverified]. Whether something is playable at all comes from [PlaybackAssessor], the one answer
 * to that question; the planner decides what to do about it.
 */
object PlaybackPlanner {
    private val VIDEO_BLOCKS = setOf(
        PlaybackIssue.VIDEO_CODEC_UNSUPPORTED, PlaybackIssue.VIDEO_RESOLUTION_UNSUPPORTED,
        PlaybackIssue.VIDEO_BIT_DEPTH_UNSUPPORTED, PlaybackIssue.DOLBY_VISION_UNSUPPORTED,
    )

    fun plan(requirements: PlaybackRequirements, device: DeviceCapabilities): PlannedPlayback =
        Planning(requirements, device).plan()

    /**
     * The assessment of what will really play: the audio track this plan chose, not whether any track could. Being
     * left with no sound, or with another language, is a compromise of this version for this viewer.
     */
    fun assess(requirements: PlaybackRequirements, device: DeviceCapabilities): PlaybackAssessment {
        val plan = plan(requirements, device)
        val stream = requirements.version.stream
        val assessed = PlaybackAssessor.assess(stream, device, plan.audioIndex)
        val extra = listOfNotNull(
            PlaybackIssue.NO_PLAYABLE_AUDIO.takeIf { plan.outcome == PlanOutcome.PLAYS_WITHOUT_AUDIO },
            PlaybackIssue.AUDIO_OTHER_LANGUAGE.takeIf { PlanReason.AUDIO_OTHER_LANGUAGE in plan.reasons },
        )
        return PlaybackAssessment((assessed.issues + extra).distinct())
    }

    private class Planning(private val requirements: PlaybackRequirements, private val device: DeviceCapabilities) {
        private val stream = requirements.version.stream
        private val source = requirements.source
        private val assessment = PlaybackAssessor.assess(stream, device)
        private val reasons = mutableListOf<PlanReason>()
        private val unverified = mutableSetOf<PlanPart>()

        fun plan(): PlannedPlayback {
            val videoBlocked = assessment.issues.any { it in VIDEO_BLOCKS }
            if (videoBlocked && SourceCapability.SERVER_TRANSCODE !in source) {
                return cannotPlay(PlanReason.VIDEO_NOT_DECODABLE, PlanReason.NO_TRANSCODE_AVAILABLE)
            }
            val containerBlocked = PlaybackIssue.CONTAINER_UNSUPPORTED in assessment.issues
            if (containerBlocked && !videoBlocked && SourceCapability.SERVER_REMUX !in source) {
                return cannotPlay(PlanReason.CONTAINER_NOT_DEMUXABLE, PlanReason.NO_REMUX_AVAILABLE)
            }
            if (containerBlocked) reasons += PlanReason.CONTAINER_NOT_DEMUXABLE
            if (stream.container != Container.UNKNOWN && !containerBlocked) {
                if (Capabilities.container(stream.container, device).seek == Support.UNSUPPORTED) reasons += PlanReason.SEEKING_LIMITED
            } else if (stream.container == Container.UNKNOWN) {
                unverified += PlanPart.CONTAINER
            }

            var video = if (videoBlocked) VideoPlan.TRANSCODE.also { reasons += PlanReason.VIDEO_NOT_DECODABLE } else planVideo()
            val audio = AudioPlanning(stream.audio).plan()
            var delivery = when {
                video == VideoPlan.TRANSCODE -> DeliveryMode.VIDEO_TRANSCODE
                audio.plan == AudioPlan.CONVERT -> DeliveryMode.AUDIO_CONVERSION
                containerBlocked -> DeliveryMode.REMUX
                else -> DeliveryMode.DIRECT_PLAY
            }
            val subtitle = planSubtitle(audio.index, delivery)
            if (subtitle.plan == SubtitlePlan.BURN_IN) {
                video = VideoPlan.TRANSCODE
                delivery = DeliveryMode.VIDEO_TRANSCODE
            }
            return PlannedPlayback(
                outcome = if (audio.unavailable) PlanOutcome.PLAYS_WITHOUT_AUDIO else PlanOutcome.PLAYS,
                delivery = delivery,
                video = video,
                audio = audio.plan,
                subtitles = subtitle.plan,
                range = planRange(video),
                audioIndex = audio.index,
                subtitleIndex = subtitle.index,
                subtitleExternal = subtitle.external,
                reasons = reasons.distinct(),
                unverified = unverified.toSet(),
            )
        }

        private fun cannotPlay(vararg why: PlanReason) = PlannedPlayback(
            PlanOutcome.CANNOT_PLAY, DeliveryMode.UNKNOWN, VideoPlan.NONE, AudioPlan.NONE, SubtitlePlan.NONE, RangePlan.PRESERVED, reasons = why.toList(),
        )

        private fun planVideo(): VideoPlan {
            val video = stream.video
            if (video == null) {
                unverified += PlanPart.VIDEO
                return VideoPlan.ORIGINAL_DECODER_UNDECIDED
            }
            val answer = Capabilities.video(video, device)
            return when {
                answer.hardware == Support.SUPPORTED -> VideoPlan.ORIGINAL_HARDWARE_DECODE
                answer.software == Support.SUPPORTED && answer.hardware == Support.UNSUPPORTED -> {
                    reasons += PlanReason.VIDEO_SOFTWARE_DECODE
                    VideoPlan.ORIGINAL_SOFTWARE_DECODE
                }
                // Decodable for certain, and the engine will find out whether hardware takes it.
                answer.software == Support.SUPPORTED -> VideoPlan.ORIGINAL_DECODER_UNDECIDED
                else -> {
                    // Not refused by the assessor, so past a declared level or rate at most: engines try those.
                    if (answer.decode == Support.UNSUPPORTED) reasons += PlanReason.VIDEO_PAST_DECLARED_LIMIT
                    unverified += PlanPart.VIDEO
                    VideoPlan.ORIGINAL_DECODER_UNDECIDED
                }
            }
        }

        private class ChosenAudio(val plan: AudioPlan, val index: Int?, val unavailable: Boolean = false)

        private inner class AudioPlanning(private val tracks: List<AudioStream>) {
            private val answers = tracks.map { Capabilities.audio(it, device) }
            private val canConvert = SourceCapability.SERVER_AUDIO_CONVERSION in source

            fun plan(): ChosenAudio {
                if (tracks.isEmpty()) {
                    // A name-only description lists no tracks for a file that may well have them.
                    if (requirements.version.streamOrigin == StreamInfoOrigin.FILENAME_HINTS) unverified += PlanPart.AUDIO
                    return ChosenAudio(AudioPlan.NONE, null)
                }
                return when (val choice = requirements.audio) {
                    is AudioChoice.Explicit -> explicit(choice.index)
                    AudioChoice.Automatic -> automatic()
                }
            }

            /** The viewer's track or none: another language is never put in its place. */
            private fun explicit(index: Int): ChosenAudio {
                val answer = answers.getOrNull(index) ?: return unavailable()
                return when {
                    answer.plays != Support.UNSUPPORTED -> keep(index)
                    canConvert -> convert(index)
                    else -> unavailable(index)
                }
            }

            /**
             * In order: the track selection would take; another of its language that certainly plays; that track
             * again if it is unknown; that track converted; and only then another language, said in the plan.
             */
            private fun automatic(): ChosenAudio {
                val wanted = selected(tracks.indices.toList())
                if (answers[wanted].plays == Support.SUPPORTED) return keep(wanted)
                val language = tracks[wanted].language
                val sameLanguage = tracks.indices.filter { it != wanted && answers[it].plays == Support.SUPPORTED && TrackSelector.sameLanguage(tracks[it].language, language) }
                if (sameLanguage.isNotEmpty()) {
                    reasons += PlanReason.AUDIO_OTHER_TRACK_SAME_LANGUAGE
                    return keep(selected(sameLanguage))
                }
                if (answers[wanted].plays == Support.UNKNOWN) return keep(wanted)
                if (canConvert) return convert(wanted)
                val playable = tracks.indices.filter { answers[it].plays != Support.UNSUPPORTED }
                if (playable.isEmpty()) return unavailable(wanted)
                reasons += PlanReason.AUDIO_OTHER_LANGUAGE
                return keep(selected(playable))
            }

            /** [TrackSelector]'s choice among [among]: the same rule the engines' tracks go through. */
            private fun selected(among: List<Int>): Int {
                val candidates = among.map { index ->
                    val track = tracks[index]
                    PlayerTrack(index.toString(), TrackType.AUDIO, track.language, channels = track.channels, default = track.default)
                }
                return TrackSelector.selectAudio(candidates, requirements.preferences)!!.id.toInt()
            }

            private fun keep(index: Int): ChosenAudio {
                val answer = answers[index]
                if (answer.plays == Support.UNKNOWN) unverified += PlanPart.AUDIO
                if (tracks[index].atmos && answer.atmos == Support.UNSUPPORTED) reasons += PlanReason.AUDIO_LOSES_ATMOS
                if (answer.channels == Support.UNSUPPORTED) reasons += PlanReason.AUDIO_DOWNMIXED
                return when {
                    answer.passthrough == Support.SUPPORTED -> {
                        reasons += PlanReason.AUDIO_PASSTHROUGH
                        ChosenAudio(AudioPlan.ORIGINAL_PASSTHROUGH, index)
                    }
                    answer.decode != Support.UNSUPPORTED -> ChosenAudio(AudioPlan.ORIGINAL_LOCAL_DECODE, index)
                    else -> ChosenAudio(AudioPlan.ORIGINAL_PASSTHROUGH, index)
                }
            }

            private fun convert(index: Int): ChosenAudio {
                reasons += PlanReason.AUDIO_NOT_PLAYABLE
                return ChosenAudio(AudioPlan.CONVERT, index)
            }

            private fun unavailable(index: Int? = null): ChosenAudio {
                reasons += PlanReason.AUDIO_UNAVAILABLE
                return ChosenAudio(AudioPlan.NONE, index, unavailable = true)
            }
        }

        private class ChosenSubtitle(val plan: SubtitlePlan, val index: Int? = null, val external: Boolean = false)

        private fun planSubtitle(audioIndex: Int?, delivery: DeliveryMode): ChosenSubtitle {
            val embedded = stream.subtitles
            val external = requirements.version.externalSubtitles
            val explicit = requirements.subtitle is SubtitleChoice.Explicit
            val (index, isExternal) = when (val choice = requirements.subtitle) {
                SubtitleChoice.Off -> return ChosenSubtitle(SubtitlePlan.NONE)
                is SubtitleChoice.Explicit -> choice.index to choice.external
                SubtitleChoice.Automatic -> automaticSubtitle(audioIndex, embedded, external) ?: return ChosenSubtitle(SubtitlePlan.NONE)
            }
            val format = (if (isExternal) external.getOrNull(index)?.format else embedded.getOrNull(index)?.format)
            if (format == null) {
                reasons += PlanReason.SUBTITLE_UNAVAILABLE
                return ChosenSubtitle(SubtitlePlan.NONE)
            }
            return routeSubtitle(format, index, isExternal, delivery, explicit)
        }

        /** [TrackSelector]'s choice, as the engines' tracks would get it; embedded tracks first, then files. */
        private fun automaticSubtitle(audioIndex: Int?, embedded: List<SubtitleStream>, external: List<ExternalSubtitle>): Pair<Int, Boolean>? {
            val tracks = embedded.mapIndexed { index, track ->
                PlayerTrack("e$index", TrackType.SUBTITLE, track.language, forced = track.forced, default = track.default, hearingImpaired = track.hearingImpaired)
            } + external.mapIndexed { index, track ->
                PlayerTrack("x$index", TrackType.SUBTITLE, track.language, forced = track.forced, hearingImpaired = track.hearingImpaired, external = true)
            }
            val audio = audioIndex?.let { stream.audio[it] }?.let { PlayerTrack("a", TrackType.AUDIO, it.language) }
            val chosen = TrackSelector.selectSubtitle(tracks, audio, requirements.preferences) ?: return null
            return chosen.id.drop(1).toInt() to chosen.external
        }

        /**
         * In order: what is in the stream and drawable; a file beside it; the source's extraction; the source's
         * conversion to text; a try at what is unknown; and burning in only for a track the viewer chose, which
         * nothing else can show and which is known not to be drawable. Unknown never burns in.
         */
        private fun routeSubtitle(format: SubtitleFormat, index: Int, isExternal: Boolean, delivery: DeliveryMode, explicit: Boolean): ChosenSubtitle {
            fun chosen(plan: SubtitlePlan, external: Boolean = isExternal) = ChosenSubtitle(plan, index, external)
            val beside = Capabilities.subtitle(format, external = true, device).render
            if (isExternal) {
                if (beside == Support.SUPPORTED) return chosen(SubtitlePlan.EXTERNAL)
                if (beside == Support.UNKNOWN) {
                    unverified += PlanPart.SUBTITLES
                    return chosen(SubtitlePlan.EXTERNAL)
                }
            }
            // Only the original file carries embedded tracks: a remuxed or converted stream has none.
            val inStream = !isExternal && delivery == DeliveryMode.DIRECT_PLAY
            val here = if (inStream) Capabilities.subtitle(format, external = false, device).render else Support.UNSUPPORTED
            if (here == Support.SUPPORTED) return chosen(SubtitlePlan.LOCAL_RENDER)
            if (!isExternal && !inStream) reasons += PlanReason.SUBTITLE_NOT_IN_STREAM
            if (here == Support.UNSUPPORTED && (inStream || isExternal)) reasons += PlanReason.SUBTITLE_NOT_DRAWABLE

            val extraction = SourceCapability.SERVER_SUBTITLE_EXTRACTION in source
            if (!isExternal && extraction && beside == Support.SUPPORTED) return chosen(SubtitlePlan.EXTERNAL)
            val text = !format.bitmap && format != SubtitleFormat.UNKNOWN
            val drawsConverted = TEXT_TARGETS.any { Capabilities.subtitle(it, external = true, device).render == Support.SUPPORTED }
            if (text && drawsConverted && SourceCapability.SERVER_SUBTITLE_CONVERSION in source) return chosen(SubtitlePlan.CONVERT)
            if (here == Support.UNKNOWN) {
                unverified += PlanPart.SUBTITLES
                return chosen(SubtitlePlan.LOCAL_RENDER)
            }
            if (!isExternal && extraction && beside == Support.UNKNOWN) {
                unverified += PlanPart.SUBTITLES
                return chosen(SubtitlePlan.EXTERNAL)
            }
            if (explicit && SourceCapability.SERVER_BURN_IN in source) {
                reasons += PlanReason.SUBTITLE_BURNED_IN
                return chosen(SubtitlePlan.BURN_IN)
            }
            reasons += PlanReason.SUBTITLE_UNAVAILABLE
            return ChosenSubtitle(SubtitlePlan.NONE)
        }

        private fun planRange(plan: VideoPlan): RangePlan {
            val video = stream.video ?: return RangePlan.PRESERVED
            if (video.dynamicRange == DynamicRange.SDR) return RangePlan.PRESERVED
            if (plan == VideoPlan.TRANSCODE) {
                reasons += PlanReason.RANGE_AT_RISK_IN_TRANSCODE
                return RangePlan.CONVERTED
            }
            val answer = Capabilities.display(video, device)
            return when {
                answer.show == Support.SUPPORTED -> RangePlan.PRESERVED
                answer.show == Support.UNKNOWN -> {
                    unverified += PlanPart.DISPLAY
                    RangePlan.PRESERVED
                }
                video.dynamicRange == DynamicRange.DOLBY_VISION && video.hasCompatibleBaseLayer && DynamicRange.HDR10 in device.display.dynamicRanges -> RangePlan.BASE_LAYER
                video.dynamicRange == DynamicRange.HDR10_PLUS && DynamicRange.HDR10 in device.display.dynamicRanges -> RangePlan.BASE_LAYER
                answer.toneMap == Support.SUPPORTED -> RangePlan.TONE_MAPPED
                else -> {
                    reasons += PlanReason.RANGE_NOT_SHOWN
                    RangePlan.NOT_ADAPTED
                }
            }
        }
    }

    /** Text formats a source can convert to. */
    private val TEXT_TARGETS = listOf(SubtitleFormat.WEBVTT, SubtitleFormat.SRT)
}
