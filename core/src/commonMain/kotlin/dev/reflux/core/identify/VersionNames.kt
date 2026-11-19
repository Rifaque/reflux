package dev.reflux.core.identify

/**
 * What a server's own name for one of a work's versions says about it (B-024). A server names versions after their
 * files ("Heat 4K", "2160p HDR", "Director's Cut", "1080p - Extended"). Only a cut or an edition is information the
 * stream does not already carry, and only that is kept: a resolution, a range, a codec, or a source named there is
 * the file name's claim, and the probed stream is the authority on those, so it is never shown beside it as if it
 * were a second fact (`1440p · Dolby Vision · 2160p HDR`).
 */
object VersionNames {
    /**
     * The edition in [name], a server's name for a version of the work called [title]: a known edition phrase
     * ("Director's Cut", "Extended"), or whatever the name says beyond the title and its quality words ("Fan Edit"),
     * or null when it says nothing more.
     */
    fun edition(name: String?, title: String?): String? {
        val text = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        ReleaseTokens.findEdition(text)?.let { return ReleaseTokens.editionLabel(it) }
        var rest = text
        title?.trim()?.takeIf { it.isNotEmpty() }?.let { rest = rest.replace(Regex("""(?<![\p{L}\p{N}])${Regex.escape(it)}(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE), " ") }
        rest = ReleaseTokens.withoutQuality(rest)
        // Separators, brackets, and a year are not an edition either.
        val words = rest.replace(Regex("""[\[\](){}._,:;|/\\+-]"""), " ").replace(Regex("""(?<![\p{N}])(?:18|19|20)\d{2}(?![\p{N}])"""), " ").trim()
            .split(Regex("\\s+")).filter { it.isNotEmpty() }
        return words.joinToString(" ").takeIf { words.isNotEmpty() }
    }
}
