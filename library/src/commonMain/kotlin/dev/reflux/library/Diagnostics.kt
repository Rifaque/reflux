package dev.reflux.library

import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.Availability
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.Season

/** A season with episodes the provider lists as aired but the library does not have. */
data class MissingEpisodes(val season: Season, val showTitle: String, val missing: List<Int>)

/** Library health, for a diagnostics view. Every list is actionable (Identify, reconnect, pick a version). */
data class LibraryDiagnostics(
    /** Files whose identification is weak: candidates for "Identify". */
    val lowConfidence: List<VersionInfo>,
    /** Works the metadata provider could not match confidently. */
    val unmatched: List<LibraryEntry>,
    /** Works with more than one version (quality upgrades, duplicates, editions). */
    val multipleVersions: List<LibraryEntry>,
    val missingEpisodes: List<MissingEpisodes>,
    val missingArtwork: List<LibraryEntry>,
    val unavailableSources: List<SourceRecord>,
)

fun Library.diagnostics(): LibraryDiagnostics {
    val works = movies() + shows()
    val unmatchedIds = queries.worksWithoutMatch().executeAsList().map { it.id }.toSet()
    val multiple = queries.worksWithSeveralVersions().executeAsList().map { it.item_id }
    return LibraryDiagnostics(
        lowConfidence = lowConfidence(),
        unmatched = works.filter { it.item.id.value in unmatchedIds },
        multipleVersions = entries(itemsByIds(multiple)),
        missingEpisodes = missingEpisodes(),
        missingArtwork = works.filter { ArtworkKind.POSTER !in it.artwork },
        unavailableSources = sources().filter { it.availability == Availability.UNAVAILABLE },
    )
}

private fun Library.missingEpisodes(): List<MissingEpisodes> =
    queries.seasonEpisodeCounts().executeAsList().mapNotNull { row ->
        val number = row.season_number?.toInt() ?: return@mapNotNull null
        if (number == 0) return@mapNotNull null // specials are rarely complete and that is fine
        val present = queries.presentEpisodesOfSeason(row.id).executeAsList()
            .flatMap { episode ->
                val first = episode.episode_number?.toInt() ?: return@flatMap emptyList()
                (first..(episode.episode_number_end?.toInt() ?: first)).toList()
            }.toSet()
        if (present.isEmpty()) return@mapNotNull null
        val missing = (1..row.episode_count!!.toInt()).filter { it !in present }
        if (missing.isEmpty()) return@mapNotNull null
        val season = item(MediaId(row.id)) as? Season ?: return@mapNotNull null
        MissingEpisodes(season, item(season.showId)?.title.orEmpty(), missing)
    }
