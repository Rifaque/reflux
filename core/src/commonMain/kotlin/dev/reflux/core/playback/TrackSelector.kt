package dev.reflux.core.playback

/** How subtitles are chosen when playback starts. [AUTO] is the default and should rarely need changing. */
enum class SubtitleMode {
    /** Subtitles when the audio is in a language the user does not prefer; forced subtitles otherwise. */
    AUTO,
    ALWAYS,
    FORCED_ONLY,
    OFF,
}

data class TrackPreferences(
    /** Preferred languages in order, as ISO 639 codes (typically from the device locale). */
    val languages: List<String>,
    val subtitleMode: SubtitleMode = SubtitleMode.AUTO,
)

/**
 * Deterministic default track selection, identical across playback engines.
 *
 * Audio: the first preferred language, else the track the file marks as default, else the first track.
 * Subtitles follow [SubtitleMode]; full subtitles prefer non-SDH tracks.
 */
object TrackSelector {
    fun selectAudio(tracks: List<PlayerTrack>, preferences: TrackPreferences): PlayerTrack? {
        val audio = tracks.filter { it.type == TrackType.AUDIO }
        if (audio.isEmpty()) return null
        for (language in preferences.languages) {
            val matches = audio.filter { sameLanguage(it.language, language) }
            if (matches.isNotEmpty()) return matches.firstOrNull { it.default } ?: matches.maxBy { it.channels ?: 0 }
        }
        return audio.firstOrNull { it.default } ?: audio.first()
    }

    fun selectSubtitle(tracks: List<PlayerTrack>, audio: PlayerTrack?, preferences: TrackPreferences): PlayerTrack? {
        val subtitles = tracks.filter { it.type == TrackType.SUBTITLE }
        if (subtitles.isEmpty()) return null
        val preferred = preferences.languages
        return when (preferences.subtitleMode) {
            SubtitleMode.OFF -> null
            SubtitleMode.ALWAYS -> fullSubtitle(subtitles, preferred)
            SubtitleMode.FORCED_ONLY -> forcedSubtitle(subtitles, audio?.language ?: preferred.firstOrNull())
            SubtitleMode.AUTO -> {
                val audioLanguage = audio?.language
                val understood = audioLanguage == null || preferred.any { sameLanguage(audioLanguage, it) }
                when {
                    !understood -> fullSubtitle(subtitles, preferred) ?: forcedSubtitle(subtitles, preferred.firstOrNull())
                    audioLanguage != null -> forcedSubtitle(subtitles, audioLanguage)
                    // Unknown audio language: only what the file itself asks for.
                    else -> forcedSubtitle(subtitles, preferred.firstOrNull()) ?: subtitles.firstOrNull { it.default && it.forced }
                }
            }
        }
    }

    private fun fullSubtitle(subtitles: List<PlayerTrack>, languages: List<String>): PlayerTrack? {
        for (language in languages) {
            val matches = subtitles.filter { sameLanguage(it.language, language) && !it.forced }
            if (matches.isNotEmpty()) {
                return matches.sortedWith(compareBy({ it.hearingImpaired }, { !it.default }, { it.external })).first()
            }
        }
        return null
    }

    private fun forcedSubtitle(subtitles: List<PlayerTrack>, language: String?): PlayerTrack? =
        subtitles.filter { it.forced && (language == null || it.language == null || sameLanguage(it.language, language)) }
            .sortedWith(compareBy({ it.language == null }, { !it.default }))
            .firstOrNull()

    /** Compares ISO 639-1 and 639-2 codes (`en`, `eng`, `en-US`). */
    fun sameLanguage(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        return normalize(a) == normalize(b)
    }

    private fun normalize(code: String): String {
        val base = code.lowercase().substringBefore('-').substringBefore('_')
        return iso6392To1[base] ?: base
    }

    private val iso6392To1 = mapOf(
        "eng" to "en", "fre" to "fr", "fra" to "fr", "ger" to "de", "deu" to "de", "spa" to "es", "ita" to "it",
        "por" to "pt", "dut" to "nl", "nld" to "nl", "jpn" to "ja", "kor" to "ko", "chi" to "zh", "zho" to "zh",
        "rus" to "ru", "ara" to "ar", "hin" to "hi", "swe" to "sv", "nor" to "no", "nob" to "no", "dan" to "da",
        "fin" to "fi", "pol" to "pl", "tur" to "tr", "gre" to "el", "ell" to "el", "heb" to "he", "cze" to "cs",
        "ces" to "cs", "hun" to "hu", "ind" to "id", "tha" to "th", "vie" to "vi", "ukr" to "uk", "rum" to "ro",
        "ron" to "ro", "may" to "ms", "msa" to "ms", "per" to "fa", "fas" to "fa", "ben" to "bn", "tam" to "ta",
        "tel" to "te", "cat" to "ca", "hrv" to "hr", "srp" to "sr", "slo" to "sk", "slk" to "sk", "slv" to "sl",
        "bul" to "bg", "lit" to "lt", "lav" to "lv", "est" to "et", "ice" to "is", "isl" to "is",
    )
}
