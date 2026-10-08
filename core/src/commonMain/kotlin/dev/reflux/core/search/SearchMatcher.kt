package dev.reflux.core.search

import dev.reflux.core.identify.TitleText
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaKind

/** Something that can be found by search. */
data class SearchDocument(
    val id: MediaId,
    val kind: MediaKind,
    val title: String,
    val year: Int? = null,
    /** Alternative titles (original-language titles, show title for an episode). */
    val aliases: List<String> = emptyList(),
)

data class SearchHit(val document: SearchDocument, val score: Int)

/**
 * Deterministic title search: accent-, case-, and punctuation-insensitive, prefix-matching, and ranked.
 *
 * Every query word must match a title word by prefix or match the year. Exact titles rank above prefixes,
 * prefixes above in-order matches, and works above episodes. Natural-language search builds on this later.
 */
object SearchMatcher {
    fun search(query: String, documents: Iterable<SearchDocument>, limit: Int = 50): List<SearchHit> {
        val queryKey = TitleText.key(query)
        val queryTokens = queryKey.split(' ').filter { it.isNotEmpty() }
        if (queryTokens.isEmpty()) return emptyList()
        return documents
            .mapNotNull { doc -> score(doc, queryKey, queryTokens)?.let { SearchHit(doc, it) } }
            .sortedWith(
                compareByDescending<SearchHit> { it.score }
                    .thenBy { TitleText.sortKey(it.document.title) }
                    .thenBy { it.document.year ?: Int.MAX_VALUE }
                    .thenBy { it.document.id.value },
            )
            .take(limit)
    }

    private fun score(doc: SearchDocument, queryKey: String, queryTokens: List<String>): Int? {
        val kindBonus = when (doc.kind) {
            MediaKind.MOVIE, MediaKind.SHOW -> 10
            MediaKind.SEASON -> 5
            MediaKind.EPISODE -> 0
        }
        return (listOf(doc.title) + doc.aliases).mapIndexedNotNull { index, title ->
            scoreTitle(TitleText.key(title), doc.year, queryKey, queryTokens)?.let { it - if (index > 0) 5 else 0 }
        }.maxOrNull()?.plus(kindBonus)
    }

    private fun scoreTitle(titleKey: String, year: Int?, queryKey: String, queryTokens: List<String>): Int? {
        val titleTokens = titleKey.split(' ').filter { it.isNotEmpty() }
        val yearText = year?.toString()
        var yearMatched = false
        val positions = mutableListOf<Int>()
        for (token in queryTokens) {
            val position = titleTokens.indexOfFirst { it.startsWith(token) }
            when {
                position >= 0 -> positions += position
                token == yearText -> yearMatched = true
                else -> return null
            }
        }
        val yearBonus = if (yearMatched) 5 else 0
        val base = when {
            titleKey == queryKey -> 100
            titleKey.startsWith(queryKey) -> 80
            positions.isNotEmpty() && positions == positions.sorted() && positions.first() == 0 -> 70
            positions == positions.sorted() -> 60
            else -> 40
        }
        return base + yearBonus
    }
}
