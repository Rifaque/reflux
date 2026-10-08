package dev.reflux.core.metadata

import dev.reflux.core.identify.TitleText

data class ScoredCandidate(val candidate: MetadataCandidate, val score: Double)

/** The matcher's verdict for a query. */
sealed interface MatchResult {
    data class Accepted(val candidate: MetadataCandidate, val score: Double) : MatchResult

    /** Plausible candidates exist but none is clearly right; the user can pick one in the Identify flow. */
    data class Ambiguous(val candidates: List<ScoredCandidate>) : MatchResult

    data object NoMatch : MatchResult
}

/**
 * Deterministic, explainable matching of provider candidates to a parsed work.
 *
 * Score = title similarity (0–1, exact normalized match = 1) adjusted by year agreement. A candidate is
 * accepted when it is strong on its own and clearly ahead of the runner-up. Popularity only breaks exact ties
 * when the year is unknown, and only when it is decisive (`Dune` → the 2021 film); the Identify flow
 * remains one action away for the rare wrong guess.
 */
object MetadataMatcher {
    const val ACCEPT_SCORE = 0.80
    const val MIN_MARGIN = 0.10
    private const val DECISIVE_POPULARITY = 2.0
    private const val PLAUSIBLE_SCORE = 0.45

    fun rank(query: MetadataQuery, candidates: List<MetadataCandidate>): List<ScoredCandidate> =
        candidates.map { ScoredCandidate(it, score(query, it)) }
            .sortedWith(compareByDescending<ScoredCandidate> { it.score }.thenByDescending { it.candidate.popularity })

    fun match(query: MetadataQuery, candidates: List<MetadataCandidate>): MatchResult {
        val ranked = rank(query, candidates).filter { it.score >= PLAUSIBLE_SCORE }
        val best = ranked.firstOrNull() ?: return MatchResult.NoMatch
        val runnerUp = ranked.getOrNull(1)
        val clearlyAhead = runnerUp == null || best.score - runnerUp.score >= MIN_MARGIN ||
            (best.score == runnerUp.score && best.candidate.popularity >= runnerUp.candidate.popularity * DECISIVE_POPULARITY)
        return if (best.score >= ACCEPT_SCORE && clearlyAhead) {
            MatchResult.Accepted(best.candidate, best.score)
        } else {
            MatchResult.Ambiguous(ranked.take(10))
        }
    }

    fun score(query: MetadataQuery, candidate: MetadataCandidate): Double {
        val similarity = maxOf(
            titleSimilarity(query.title, candidate.title),
            candidate.originalTitle?.let { titleSimilarity(query.title, it) } ?: 0.0,
        )
        val yearAdjustment = when {
            query.year == null || candidate.year == null -> 0.0
            query.year == candidate.year -> 0.15
            kotlin.math.abs(query.year - candidate.year) == 1 -> 0.05 // festival vs. theatrical release years
            else -> -0.35
        }
        return (similarity * 0.85 + yearAdjustment).coerceIn(0.0, 1.0)
    }

    /**
     * 1.0 for equal normalized titles; otherwise a Dice coefficient over word tokens, ignoring articles.
     * A trailing qualifier such as `(US)` or `(2005)` is also tried without it.
     */
    fun titleSimilarity(a: String, b: String): Double =
        maxOf(rawSimilarity(a, b), rawSimilarity(stripQualifier(a), stripQualifier(b)))

    private fun stripQualifier(title: String): String = title.replace(Regex("""\s*\([^)]*\)\s*$"""), "").ifBlank { title }

    private fun rawSimilarity(a: String, b: String): Double {
        val keyA = TitleText.key(a)
        val keyB = TitleText.key(b)
        if (keyA.isEmpty() || keyB.isEmpty()) return 0.0
        if (keyA == keyB) return 1.0
        if (TitleText.sortKey(a) == TitleText.sortKey(b)) return 0.95
        val tokensA = keyA.split(' ').filter { it !in articles }.toSet()
        val tokensB = keyB.split(' ').filter { it !in articles }.toSet()
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0
        val common = tokensA.intersect(tokensB).size
        return 2.0 * common / (tokensA.size + tokensB.size) * 0.9
    }

    private val articles = setOf("the", "a", "an")
}
