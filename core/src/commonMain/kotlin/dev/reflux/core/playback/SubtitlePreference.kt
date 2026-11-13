package dev.reflux.core.playback

import dev.reflux.core.model.ExternalSubtitle

/**
 * What a viewer chose for a work's subtitles, kept as a description of the track and never as its position: another
 * version of the work, or the next episode, numbers its tracks differently (docs/WATCH_STATE.md, "Profiles").
 *
 * Stored per profile and per work as text ([encode], [decode]); the planner matches it against each version's tracks.
 */
data class SubtitlePreference(
    /** The viewer turned subtitles off. */
    val off: Boolean,
    val language: String? = null,
    val forced: Boolean = false,
    val hearingImpaired: Boolean = false,
) {
    /** What the planner is asked for. */
    fun choice(): SubtitleChoice = if (off) SubtitleChoice.Off else SubtitleChoice.Preferred(language, forced, hearingImpaired)

    fun encode(): String = if (off) "off" else "on;lang=${language ?: "_"};forced=${if (forced) 1 else 0};sdh=${if (hearingImpaired) 1 else 0}"

    companion object {
        val OFF = SubtitlePreference(off = true)

        /** The preference [text] says, or null when it is not one (an unknown or damaged value is forgotten, not guessed at). */
        fun decode(text: String?): SubtitlePreference? {
            if (text == null) return null
            if (text == "off") return OFF
            val parts = text.split(';')
            if (parts.firstOrNull() != "on") return null
            val fields = parts.drop(1).mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            val language = fields["lang"] ?: return null
            return SubtitlePreference(false, language.takeIf { it != "_" && it.isNotBlank() }, fields["forced"] == "1", fields["sdh"] == "1")
        }

        /** The key of a work's preference among a profile's settings: for an episode the show's, so a choice carries through the show. */
        fun key(workId: String): String = "subtitles.work:$workId"
    }
}

/** Finds the track a [SubtitleChoice.Preferred] describes among one version's tracks. */
internal object SubtitleMatch {
    /**
     * Embedded tracks first, then files. Among tracks in the language, the one that agrees on forced and on hearing
     * impaired, then the one that agrees on forced, then the first. A preference with no language matches only tracks
     * with none. No track in the language is no match.
     */
    fun find(choice: SubtitleChoice.Preferred, embedded: List<SubtitleStream>, external: List<ExternalSubtitle>): Pair<Int, Boolean>? {
        class Candidate(val index: Int, val external: Boolean, val language: String?, val forced: Boolean, val sdh: Boolean)
        val tracks = embedded.mapIndexed { i, t -> Candidate(i, false, t.language, t.forced, t.hearingImpaired) } +
            external.mapIndexed { i, t -> Candidate(i, true, t.language, t.forced, t.hearingImpaired) }
        val inLanguage = tracks.filter { track ->
            if (choice.language == null) track.language == null else TrackSelector.sameLanguage(track.language, choice.language)
        }
        val best = inLanguage.firstOrNull { it.forced == choice.forced && it.sdh == choice.hearingImpaired }
            ?: inLanguage.firstOrNull { it.forced == choice.forced }
            ?: inLanguage.firstOrNull()
        return best?.let { it.index to it.external }
    }
}
