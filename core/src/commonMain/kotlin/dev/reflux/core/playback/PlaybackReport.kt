package dev.reflux.core.playback

/**
 * Why a playback is what it is, in a viewer's words: what was decided, what the server and the engine then did, and
 * whether the two agree (docs/PLAYBACK.md, section 9). It reads the plan, the evidence, and the engine, and decides
 * nothing. It names formats and decoders, never an address, a key, or a token.
 */
object PlaybackReport {
    data class Line(val heading: String, val text: String)

    fun of(plan: PlannedPlayback, comparison: PlaybackComparison?, state: PlayerState): List<Line> = buildList {
        val reasons = plan.reasons.toSet()
        add(Line("How it plays", delivery(plan, reasons)))
        video(plan, state)?.let { add(Line("Picture", it)) }
        add(Line("Sound", audio(plan, reasons, state)))
        subtitles(plan, reasons)?.let { add(Line("Subtitles", it)) }
        range(plan)?.let { add(Line("Colour", it)) }
        if (PlanReason.ORIGINAL_FAILED in reasons) add(Line("Planned again", replanned(reasons)))
        if (PlanReason.NEGOTIATION_FAILED in reasons) add(Line("Server", "The server did not agree to convert it, so this device plays what it can alone."))
        if (PlanReason.VIDEO_PAST_DECLARED_LIMIT in reasons) add(Line("Decoder", "Beyond what this device's decoder promises, so it is tried."))
        if (plan.outcome != PlanOutcome.CANNOT_PLAY) {
            add(Line("What the server did", server(comparison)))
            add(Line("Planned against actual", agreement(comparison)))
        }
    }

    /** The report as one line, for the diagnostics log. */
    fun summary(lines: List<Line>): String = lines.joinToString("; ") { "${it.heading}: ${it.text}" }

    private fun delivery(plan: PlannedPlayback, reasons: Set<PlanReason>): String = when {
        plan.outcome == PlanOutcome.CANNOT_PLAY -> "It can't be played here: " + when {
            PlanReason.VIDEO_NOT_DECODABLE in reasons -> "this device can't decode its picture, and its source can't convert it."
            PlanReason.CONTAINER_NOT_DEMUXABLE in reasons -> "this device can't open its file type, and its source can't change it."
            else -> "nothing here can play it."
        }
        plan.delivery == DeliveryMode.DIRECT_PLAY -> "The original file, as it is."
        plan.delivery == DeliveryMode.REMUX -> "The server repackages the file without changing the picture or sound, because " +
            if (PlanReason.SEEKING_LIMITED in reasons) "this device can't skip through it as it is." else "this device can't open its file type."
        plan.delivery == DeliveryMode.AUDIO_CONVERSION -> "The server converts the sound; the picture is the original."
        plan.delivery == DeliveryMode.VIDEO_TRANSCODE -> "The server converts the picture, because " + when {
            PlanReason.SUBTITLE_BURNED_IN in reasons && PlanReason.VIDEO_NOT_DECODABLE !in reasons -> "the subtitles you chose have to be drawn into it."
            PlanReason.ORIGINAL_FAILED in reasons -> "the original picture did not play here."
            else -> "this device can't decode the original."
        }
        else -> "Unknown."
    }

    private fun video(plan: PlannedPlayback, state: PlayerState): String? {
        val decoder = state.videoDecoder
        return when {
            plan.video == VideoPlan.NONE -> null
            plan.video == VideoPlan.TRANSCODE -> "Converted by the server" + decoder?.let { ", decoded ${how(it)}" }.orEmpty() + "."
            decoder != null -> "The original, decoded ${how(decoder)}."
            else -> "The original; the decoder is not known yet."
        }
    }

    private fun how(decoder: DecoderInUse) = when (decoder.hardware) {
        true -> "in hardware"
        false -> "in software"
        null -> "by an unknown decoder"
    } + decoder.name?.let { " ($it)" }.orEmpty()

    private fun audio(plan: PlannedPlayback, reasons: Set<PlanReason>, state: PlayerState): String {
        val track = state.audioTracks.firstOrNull { it.id == state.selectedAudioId }
        val what = track?.let { listOfNotNull(it.language, it.channels?.let { c -> "$c channels" }).joinToString(", ") }?.takeIf { it.isNotEmpty() }?.let { " ($it)" }.orEmpty()
        val base = when {
            plan.outcome == PlanOutcome.PLAYS_WITHOUT_AUDIO || plan.audio == AudioPlan.NONE && state.audioUnavailable -> "None: no audio track can play here."
            plan.audio == AudioPlan.NONE -> "None."
            plan.audio == AudioPlan.ORIGINAL_PASSTHROUGH -> "The original, sent on to the receiver$what."
            plan.audio == AudioPlan.CONVERT -> "Converted by the server, as this device can't decode the original$what."
            else -> "The original, decoded here$what."
        }
        val notes = listOfNotNull(
            "Your language can't play here, so another one does.".takeIf { PlanReason.AUDIO_OTHER_LANGUAGE in reasons },
            "Another track in your language plays instead.".takeIf { PlanReason.AUDIO_OTHER_TRACK_SAME_LANGUAGE in reasons },
            "Mixed down to the channels this device has.".takeIf { PlanReason.AUDIO_DOWNMIXED in reasons },
            "Atmos plays as its surround mix.".takeIf { PlanReason.AUDIO_LOSES_ATMOS in reasons },
        )
        return (listOf(base) + notes).joinToString(" ")
    }

    private fun subtitles(plan: PlannedPlayback, reasons: Set<PlanReason>): String? = when (plan.subtitles) {
        SubtitlePlan.LOCAL_RENDER -> "Drawn from the file."
        SubtitlePlan.EXTERNAL -> "Delivered beside the stream and drawn here."
        SubtitlePlan.CONVERT -> "Converted to text by the server."
        SubtitlePlan.BURN_IN -> "Drawn into the picture by the server, as this device can't draw them."
        SubtitlePlan.NONE -> "The ones you chose can't be shown here.".takeIf { PlanReason.SUBTITLE_UNAVAILABLE in reasons }
    }

    private fun range(plan: PlannedPlayback): String? = when (plan.range) {
        RangePlan.PRESERVED -> null
        RangePlan.BASE_LAYER -> "Dolby Vision plays as its HDR base."
        RangePlan.TONE_MAPPED -> "HDR, shown as SDR on this screen."
        RangePlan.NOT_ADAPTED -> "HDR this screen can't show; colours may look washed out."
        RangePlan.CONVERTED -> "The server's conversion may not keep HDR."
    }

    private fun replanned(reasons: Set<PlanReason>) = "Once, because " + when {
        PlanReason.SEEKING_LIMITED in reasons -> "this device couldn't move to the place you wanted in the original."
        PlanReason.AUDIO_NOT_PLAYABLE in reasons -> "the original sound did not play here."
        else -> "the original did not play here."
    }

    private fun server(comparison: PlaybackComparison?): String {
        val evidence = comparison?.evidence ?: return if (comparison == null) "Not asked yet." else "This source keeps no account; it delivers the file."
        return when (evidence.delivery) {
            DeliveryMode.DIRECT_PLAY -> "Sent the original file."
            DeliveryMode.REMUX -> "Repackaged it, picture and sound copied."
            DeliveryMode.AUDIO_CONVERSION -> "Converted the sound, kept the picture."
            DeliveryMode.VIDEO_TRANSCODE -> "Converted the picture" + if (evidence.audio == StreamFate.PRESERVED) ", kept the sound." else " and the sound."
            DeliveryMode.UNKNOWN -> "It didn't say. A repackaging that only copies can be over before it is asked."
        }
    }

    private fun agreement(comparison: PlaybackComparison?): String = when {
        comparison == null -> "Not compared yet."
        comparison.mismatches.isNotEmpty() -> "Different from the plan: " + comparison.mismatches.joinToString(", ") { it.name.lowercase().replace('_', ' ') } + "."
        comparison.unconfirmed.isNotEmpty() -> "Nothing differs; not confirmed: " + comparison.unconfirmed.joinToString(", ") { it.name.lowercase() } + "."
        else -> "As planned."
    }
}
