package dev.reflux.core.playback

/** Where what happened differs from what was planned. */
enum class PlanMismatch { DELIVERY, VIDEO, AUDIO, SUBTITLES, AUDIO_TRACK, DECODER }

/**
 * What was planned beside what happened. Two kinds of not matching are kept apart: [mismatches] are shown to be
 * different, and [unconfirmed] could not be shown either way. A plan is met when there are no mismatches; it is
 * proven only when nothing is unconfirmed as well.
 *
 * @param evidence what the source says; null for a source that keeps no account, which delivers its files.
 */
data class PlaybackComparison(
    val planned: PlannedPlayback,
    val evidence: PlaybackEvidence?,
    val decoder: DecoderInUse?,
    val mismatches: Set<PlanMismatch>,
    val unconfirmed: Set<PlanPart>,
) {
    /** One line for the diagnostics log. */
    fun summary(): String = buildString {
        append("planned ").append(planned.delivery)
        append(", video ").append(planned.video)
        append(", audio ").append(planned.audio).append(planned.audioIndex?.let { " (track $it)" }.orEmpty())
        append(", subtitles ").append(planned.subtitles)
        append("; actual ").append(evidence?.delivery ?: "not reported")
        append(", video ").append(evidence?.video ?: "not reported")
        append(", audio ").append(evidence?.audio ?: "not reported")
        append(", decoder ").append(decoder?.let { "${it.name} (${if (it.hardware == true) "hardware" else if (it.hardware == false) "software" else "unknown"})" } ?: "not reported")
        if (mismatches.isNotEmpty()) append("; MISMATCH ").append(mismatches.joinToString(" "))
        if (unconfirmed.isNotEmpty()) append("; unconfirmed ").append(unconfirmed.joinToString(" "))
    }

    companion object {
        fun of(planned: PlannedPlayback, evidence: PlaybackEvidence?, state: PlayerState): PlaybackComparison {
            val mismatches = mutableSetOf<PlanMismatch>()
            val unconfirmed = mutableSetOf<PlanPart>()
            if (evidence != null) {
                if (evidence.delivery == DeliveryMode.UNKNOWN) unconfirmed += PlanPart.CONTAINER
                else if (evidence.delivery != planned.delivery) mismatches += PlanMismatch.DELIVERY
                check(expected(planned.video), evidence.video, PlanMismatch.VIDEO, PlanPart.VIDEO, mismatches, unconfirmed)
                if (planned.audio != AudioPlan.NONE) {
                    check(if (planned.audio == AudioPlan.CONVERT) StreamFate.CONVERTED else StreamFate.PRESERVED, evidence.audio, PlanMismatch.AUDIO, PlanPart.AUDIO, mismatches, unconfirmed)
                }
                val burned = evidence.subtitleTriggeredConversion || evidence.subtitles.any { it.handling == SubtitleHandling.SERVER_BURN_IN }
                if (burned != (planned.subtitles == SubtitlePlan.BURN_IN)) mismatches += PlanMismatch.SUBTITLES
            }
            val decoder = state.videoDecoder
            when (planned.video) {
                VideoPlan.ORIGINAL_HARDWARE_DECODE -> if (decoder?.hardware == false) mismatches += PlanMismatch.DECODER
                VideoPlan.ORIGINAL_SOFTWARE_DECODE -> if (decoder?.hardware == true) mismatches += PlanMismatch.DECODER
                else -> Unit
            }
            // Only the original has the file's tracks to tell apart: a converted stream carries the one it was given.
            if (planned.delivery == DeliveryMode.DIRECT_PLAY && planned.audio != AudioPlan.NONE && planned.audioIndex != null) {
                val playing = state.audioTracks.firstOrNull { it.id == state.selectedAudioId }?.position
                if (playing != null && playing != planned.audioIndex) mismatches += PlanMismatch.AUDIO_TRACK
            }
            return PlaybackComparison(planned, evidence, decoder, mismatches, unconfirmed)
        }

        private fun expected(video: VideoPlan): StreamFate? = when (video) {
            VideoPlan.TRANSCODE -> StreamFate.CONVERTED
            VideoPlan.NONE -> null
            else -> StreamFate.PRESERVED
        }

        private fun check(expected: StreamFate?, actual: StreamFate, mismatch: PlanMismatch, part: PlanPart, mismatches: MutableSet<PlanMismatch>, unconfirmed: MutableSet<PlanPart>) {
            when {
                expected == null -> Unit
                actual == StreamFate.UNKNOWN -> unconfirmed += part
                actual != expected -> mismatches += mismatch
            }
        }
    }
}
