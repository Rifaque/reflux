package dev.reflux.ui.state

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.reflux.core.metadata.MetadataCandidate
import dev.reflux.core.metadata.MetadataUnavailableException
import dev.reflux.core.metadata.ProviderRef
import dev.reflux.core.metadata.ScoredCandidate
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.Movie
import dev.reflux.core.model.Show
import dev.reflux.library.Library
import dev.reflux.library.identificationStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

/** One thing a work could be, with what tells it apart from the others. */
@Immutable
data class IdentifyCandidate(
    val candidate: MetadataCandidate,
    val title: String,
    val year: Int?,
    /** Its title in its own language, when that is not the title shown. */
    val originalTitle: String?,
    val overview: String?,
    /** A small poster: enough to recognize it, never the full-size artwork. */
    val poster: ArtworkLocator?,
    /** Whether the work is matched to this one already. */
    val current: Boolean,
)

/** What a search for the Identify page came to. */
sealed interface IdentifyResults {
    data object Searching : IdentifyResults
    data class Found(val candidates: List<IdentifyCandidate>) : IdentifyResults
    data object NothingFound : IdentifyResults
    /** The provider could not be reached (offline, refused): the library is unchanged and it can be tried again. */
    data object Unreachable : IdentifyResults
}

/** The movie or show an Identify page is about, as it is now. */
@Immutable
internal data class IdentifyTarget(
    val title: String,
    val year: Int?,
    val show: Boolean,
    val corrected: Boolean,
    val match: ProviderRef?,
    /** The latest year a query can mean ([Library.latestReleaseYear]). */
    val latestYear: Int,
)

internal fun Library.identifyTarget(itemId: MediaId): IdentifyTarget? {
    val entry = entry(itemId) ?: return null
    val status = identificationStatus(itemId) ?: return null
    val year = when (val item = entry.item) {
        is Movie -> item.year
        is Show -> item.year
        else -> return null
    }
    return IdentifyTarget(cards(listOf(entry)).single().title, year, entry.item is Show, status.corrected, status.match, latestReleaseYear())
}

/**
 * What a typed query asks for: a title, and a year when it ends with one ("Dune 1984", "Dune (1984)"). A number
 * later than [latestYear] is part of the title ("Blade Runner 2049"), as in file names.
 */
internal object IdentifyQuery {
    private val trailingYear = Regex("""^(.*?)[\s.(\[]+(\d{4})[)\]]?\s*$""")

    fun parse(text: String, latestYear: Int): Pair<String, Int?> {
        val trimmed = text.trim()
        val match = trailingYear.matchEntire(trimmed) ?: return trimmed to null
        val title = match.groupValues[1].trim()
        val year = match.groupValues[2].toInt()
        return if (title.isEmpty() || year !in 1888..latestYear) trimmed to null else title to year
    }

    /** How the work's own title is offered to search at first: "Dune (1984)". */
    fun of(title: String, year: Int?): String = if (year == null) title else "$title ($year)"
}

/**
 * Telling Reflux what a movie or show is: the query, what it found, and choosing. A query is searched when the
 * user asks, not as they type, and never twice unchanged; what the provider returned for one stays for the
 * page's life. Choosing applies the match (DECISIONS.md, 2026-10-18, "Identify").
 */
@Stable
class IdentifySession internal constructor(
    private val app: AppState,
    val itemId: MediaId,
    /** The work as it is shown now. */
    val title: String,
    val show: Boolean,
    /** Whether the user identified it before, which can be undone. */
    val corrected: Boolean,
    private val match: ProviderRef?,
    year: Int?,
    private val latestYear: Int,
) {
    /** The title part of the query as it stands, for saying what was searched. */
    val asked: String get() = IdentifyQuery.parse(query, latestYear).first

    var query by mutableStateOf(IdentifyQuery.of(title, year))

    var results: IdentifyResults? by mutableStateOf(null)
        private set

    /** Whether a choice or an undo is being applied. */
    var applying by mutableStateOf(false)
        private set

    private var searched: Pair<String, Int?>? = null
    private val found = mutableMapOf<Pair<String, Int?>, List<IdentifyCandidate>>()
    private var searching: Job? = null

    /** Searches for [query]; does nothing when it is empty or what is shown already answers it. */
    fun search(): Job? {
        val question = IdentifyQuery.parse(query, latestYear)
        if (question.first.isBlank()) return null
        if (question == searched && results != IdentifyResults.Unreachable) return null
        searched = question
        found[question]?.let { show(it); return null }
        searching?.cancel()
        results = IdentifyResults.Searching
        return app.attempt(Problems.IDENTIFY_SEARCH) {
            try {
                val candidates = app.identifyCandidates(itemId, question.first, question.second).take(MAX_RESULTS).map(::candidate)
                found[question] = candidates
                show(candidates)
            } catch (e: CancellationException) {
                throw e
            } catch (_: MetadataUnavailableException) {
                results = IdentifyResults.Unreachable
            } catch (e: Exception) {
                results = IdentifyResults.Unreachable
                throw e
            }
        }.also { searching = it }
    }

    /** Makes the work [choice], and opens it. */
    fun choose(choice: IdentifyCandidate): Job = change { app.identify(itemId, choice.candidate) }

    /** Gives the work back to Reflux's own identification, and opens what it is then. */
    fun undo(): Job = change { app.undoIdentification(itemId) }

    private fun change(action: suspend () -> MediaId?): Job = app.attempt(Problems.IDENTIFY_APPLY) {
        if (applying) return@attempt
        applying = true
        try {
            app.identified(itemId, action())
        } finally {
            applying = false
        }
    }

    private fun show(candidates: List<IdentifyCandidate>) {
        results = if (candidates.isEmpty()) IdentifyResults.NothingFound else IdentifyResults.Found(candidates)
    }

    private fun candidate(scored: ScoredCandidate): IdentifyCandidate {
        val candidate = scored.candidate
        return IdentifyCandidate(
            candidate = candidate,
            title = candidate.title,
            year = candidate.year,
            originalTitle = candidate.originalTitle?.takeIf { it.isNotBlank() && !it.equals(candidate.title, ignoreCase = true) },
            overview = candidate.overview?.takeIf { it.isNotBlank() },
            poster = candidate.posterUrl?.let { ArtworkLocator.Remote(it) },
            current = candidate.ref == match,
        )
    }

    private companion object {
        /** More than this and the right one was not found: a better query is the way, not scrolling. */
        const val MAX_RESULTS = 12
    }
}
