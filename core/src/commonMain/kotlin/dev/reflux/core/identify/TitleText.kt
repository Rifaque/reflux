package dev.reflux.core.identify

/** Title normalization shared by identification, grouping, and search. */
object TitleText {
    private val smallWords = setOf(
        "a", "an", "and", "as", "at", "but", "by", "for", "from", "in", "into", "of", "on", "or", "the", "to", "vs", "with",
    )

    /**
     * A comparison key: case-, accent-, and punctuation-insensitive.
     *
     * `"Amélie"` and `"amelie"` share a key, as do `"Marvel's Agents of S.H.I.E.L.D."` and `"Marvels Agents of SHIELD"`.
     */
    fun key(title: String): String {
        val folded = StringBuilder(title.length)
        for (ch in title.lowercase()) folded.append(foldAccent(ch))
        return folded.toString()
            .replace("&", " and ")
            .replace(Regex("""['’`]"""), "")
            // Join dotted acronyms (s.h.i.e.l.d) before treating dots as separators.
            .replace(Regex("""(?<![a-z0-9])[a-z0-9](?:\.[a-z0-9])+(?![a-z0-9])""")) { it.value.replace(".", "") }
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()
    }

    /** Tokens of [key], used for search matching. */
    fun keyTokens(title: String): List<String> = key(title).split(' ').filter { it.isNotEmpty() }

    /** A key for alphabetical ordering that ignores leading English articles. */
    fun sortKey(title: String): String = key(title).removePrefix("the ").removePrefix("a ").removePrefix("an ")

    /**
     * Turns a raw title fragment into a display title.
     *
     * Release names lose their casing ("the dark knight") or shout ("BREAKING BAD"); such titles are
     * title-cased. Mixed-case titles are kept as written because they are usually curated by a human.
     */
    fun display(raw: String): String {
        val collapsed = raw.replace(Regex("""\s+"""), " ").trim()
        val letters = collapsed.filter { it.isLetter() }
        if (letters.length < 2) return collapsed
        val words = collapsed.split(' ')
        val lowerOnly = letters == letters.lowercase()
        // A single upper-case word is usually deliberate (WALL·E, M*A*S*H); several are usually shouting.
        val shouting = letters == letters.uppercase() && words.size > 1
        if (!lowerOnly && !shouting) return collapsed
        return words.mapIndexed { index, word ->
            val lower = word.lowercase()
            when {
                index > 0 && lower in smallWords -> lower
                isRomanNumeral(lower) -> word.uppercase()
                else -> lower.replaceFirstChar { it.uppercaseChar() }
            }
        }.joinToString(" ")
    }

    private fun isRomanNumeral(word: String): Boolean =
        word.length in 2..4 && word.all { it in "ivx" } && word != "vi" // "vi" is rare; avoid mangling words

    private fun foldAccent(ch: Char): String = when (ch) {
        'à', 'á', 'â', 'ã', 'ä', 'å', 'ā', 'ă', 'ą' -> "a"
        'æ' -> "ae"
        'ç', 'ć', 'č' -> "c"
        'ď', 'đ' -> "d"
        'è', 'é', 'ê', 'ë', 'ē', 'ė', 'ę', 'ě' -> "e"
        'ğ' -> "g"
        'ì', 'í', 'î', 'ï', 'ī', 'į', 'ı' -> "i"
        'ł', 'ľ' -> "l"
        'ñ', 'ń', 'ň' -> "n"
        'ò', 'ó', 'ô', 'õ', 'ö', 'ø', 'ō', 'ő' -> "o"
        'œ' -> "oe"
        'ř' -> "r"
        'ś', 'š', 'ş', 'ș' -> "s"
        'ß' -> "ss"
        'ť', 'ț' -> "t"
        'ù', 'ú', 'û', 'ü', 'ū', 'ů', 'ű' -> "u"
        'ý', 'ÿ' -> "y"
        'ź', 'ż', 'ž' -> "z"
        else -> ch.toString()
    }
}
