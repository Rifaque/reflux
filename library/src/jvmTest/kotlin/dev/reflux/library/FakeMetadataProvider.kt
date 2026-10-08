package dev.reflux.library

import dev.reflux.core.metadata.EpisodeMetadata
import dev.reflux.core.metadata.MetadataCandidate
import dev.reflux.core.metadata.MetadataKind
import dev.reflux.core.metadata.MetadataProvider
import dev.reflux.core.metadata.MetadataQuery
import dev.reflux.core.metadata.MetadataUnavailableException
import dev.reflux.core.metadata.ProviderRef
import dev.reflux.core.metadata.SeasonMetadata
import dev.reflux.core.metadata.WorkMetadata

/** An in-memory provider catalog for tests. */
class FakeMetadataProvider : MetadataProvider {
    override val id: String = "tmdb"
    val works = mutableMapOf<ProviderRef, WorkMetadata>()
    val popularity = mutableMapOf<ProviderRef, Double>()
    val seasons = mutableMapOf<Pair<ProviderRef, Int>, SeasonMetadata>()
    val externalIds = mutableMapOf<Pair<String, String>, ProviderRef>()
    var offline = false
    val calls = mutableListOf<String>()

    fun movie(id: String, title: String, year: Int, popularity: Double = 10.0, original: String? = null, vararg artwork: dev.reflux.core.metadata.RemoteArtwork): ProviderRef {
        val ref = ProviderRef(this.id, MetadataKind.MOVIE, id)
        works[ref] = WorkMetadata(
            ref, title, originalTitle = original, overview = "About $title",
            releaseDate = dev.reflux.core.model.CalendarDate(year, 1, 1), artwork = artwork.toList(),
        )
        this.popularity[ref] = popularity
        return ref
    }

    fun show(id: String, title: String, year: Int, seasonEpisodes: Map<Int, Int>): ProviderRef {
        val ref = ProviderRef(this.id, MetadataKind.SHOW, id)
        works[ref] = WorkMetadata(ref, title, releaseDate = dev.reflux.core.model.CalendarDate(year, 1, 1))
        popularity[ref] = 10.0
        for ((season, count) in seasonEpisodes) {
            seasons[ref to season] = SeasonMetadata(
                number = season,
                title = "Season $season",
                posterUrl = "https://img/$id/s$season.jpg",
                episodes = (1..count).map { EpisodeMetadata(season, it, "$title S${season}E$it", stillUrl = "https://img/$id/$season/$it.jpg") },
            )
        }
        return ref
    }

    private fun checkOnline() {
        if (offline) throw MetadataUnavailableException("offline")
    }

    override suspend fun search(query: MetadataQuery): List<MetadataCandidate> {
        checkOnline()
        calls += "search:${query.title}"
        val kind = query.kind
        return works.values.filter { it.ref.kind == kind }.map {
            MetadataCandidate(it.ref, it.title, it.originalTitle, it.releaseDate?.year, popularity = popularity[it.ref] ?: 0.0)
        }
    }

    override suspend fun findByExternalId(kind: MetadataKind, source: String, id: String): ProviderRef? {
        checkOnline()
        calls += "find:$source:$id"
        return externalIds[source to id]
    }

    override suspend fun details(ref: ProviderRef, language: String): WorkMetadata? {
        checkOnline()
        calls += "details:${ref.id}"
        return works[ref]
    }

    override suspend fun season(ref: ProviderRef, seasonNumber: Int, language: String): SeasonMetadata? {
        checkOnline()
        calls += "season:${ref.id}:$seasonNumber"
        return seasons[ref to seasonNumber]
    }
}
