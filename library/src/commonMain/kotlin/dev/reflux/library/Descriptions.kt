package dev.reflux.library

import dev.reflux.core.metadata.Authority

import dev.reflux.core.metadata.CreditRole
import dev.reflux.core.metadata.Description
import dev.reflux.core.metadata.DescriptionLayer
import dev.reflux.core.metadata.MatchEvidence
import dev.reflux.core.metadata.MetadataField
import dev.reflux.core.metadata.MetadataResolver
import dev.reflux.core.metadata.ProviderRef
import dev.reflux.core.metadata.Provenance
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.CalendarDate
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaLocation
import dev.reflux.core.model.SourceId
import dev.reflux.library.db.Metadata
import dev.reflux.library.db.Source_metadata

/**
 * Someone in a work's credits as a details page shows them, with their picture wherever it lives: a provider's
 * address, or a path in the source that described them.
 */
data class CastMember(val name: String, val role: CreditRole, val character: String?, val picture: ArtworkLocator?, val provenance: Provenance)

/** Where each source stands when sources disagree: the oldest first (core MetadataResolver). */
internal fun Library.sourceRanks(): Map<String, Int> = sourceOrderCache
    ?: queries.sourceOrder().executeAsList().withIndex().associate { (index, id) -> id to index }.also { sourceOrderCache = it }

/**
 * The descriptions of [ids] as shown: each work's provider entry and its sources' own descriptions, resolved
 * field by field (core MetadataResolver). Credits are left out here; [Library.cast] resolves them for one work.
 */
internal fun Library.resolvedMetadata(ids: List<String>, chunk: Int = 500): Map<String, ItemMetadata> {
    if (ids.isEmpty()) return emptyMap()
    val provider = ids.chunked(chunk).flatMap { queries.metadataOfItems(it).executeAsList() }.associateBy { it.item_id }
    val sources = ids.chunked(chunk).flatMap { queries.sourceMetadataOfItems(it).executeAsList() }.groupBy { it.item_id }
    if (provider.isEmpty() && sources.isEmpty()) return emptyMap()
    val ranks = sourceRanks()
    return (provider.keys + sources.keys).associateWith { id ->
        val row = provider[id]
        val layers = listOfNotNull(row?.layer()) + sources[id].orEmpty().map { it.layer(ranks) }
        val resolved = MetadataResolver.resolve(layers)
        val d = resolved.description
        ItemMetadata(
            ref = row?.provider_ref?.let(ProviderRef::parse),
            title = d.title,
            originalTitle = d.originalTitle,
            overview = d.overview,
            tagline = d.tagline,
            genres = d.genres,
            runtimeMinutes = d.runtimeMinutes,
            releaseDate = d.releaseDate,
            rating = d.rating,
            contentRating = d.contentRating,
            externalIds = row?.external_ids?.let(::decodePairs).orEmpty(),
            endDate = d.endDate,
            studios = d.studios,
            evidence = row?.evidence(),
            provenance = resolved.provenance,
        )
    }
}

/**
 * Who is in [itemId], directors and creators first, as its provider entry and its sources list them: one
 * witness's list whole, by the same precedence as every other field (core MetadataResolver).
 */
fun Library.cast(itemId: MediaId): List<CastMember> {
    val provider = queries.metadataOf(itemId.value).executeAsOneOrNull()
    val providerCredits = credits(itemId)
    val ranks = sourceRanks()
    val layers = buildList {
        if (provider != null && providerCredits.isNotEmpty()) add(DescriptionLayer(provider.provenance(), Description(credits = providerCredits)))
        queries.sourceMetadataOfItems(listOf(itemId.value)).executeAsList().forEach { row ->
            val credits = row.credits.lines().mapNotNull(ScanPlanner::decodeCredit)
            if (credits.isNotEmpty()) add(DescriptionLayer(Provenance(Authority.SOURCE, ScanPlanner.sourceArtworkOrigin(SourceId(row.source_id))), Description(credits = credits), ranks[row.source_id] ?: Int.MAX_VALUE))
        }
    }
    val resolved = MetadataResolver.resolve(layers)
    val provenance = resolved.provenance[MetadataField.CREDITS] ?: return emptyList()
    val sourceId = provenance.origin.takeIf { it.startsWith(ScanPlanner.SOURCE_ARTWORK_PREFIX) }?.removePrefix(ScanPlanner.SOURCE_ARTWORK_PREFIX)
    return resolved.description.credits.map { credit ->
        // A source names a picture by a path in it, or (Plex's people) by an address of its own.
        val picture = credit.profileUrl?.let {
            if (sourceId != null && "://" !in it) ArtworkLocator.SourceFile(MediaLocation(SourceId(sourceId), it)) else ArtworkLocator.Remote(it)
        }
        CastMember(credit.name, credit.role, credit.character, picture, provenance)
    }
}

/** A provider row's authority: the user's when they chose the entry (or the show it belongs to), else the provider's. */
private fun Metadata.provenance(): Provenance {
    val provider = provider_ref.substringBefore(':')
    return Provenance(if (evidence() == MatchEvidence.USER) Authority.USER else Authority.PROVIDER, provider)
}

internal fun Metadata.evidence(): MatchEvidence? = match_evidence?.let { enumOrNull<MatchEvidence>(it) }

private fun Metadata.layer(): DescriptionLayer = DescriptionLayer(
    provenance(),
    Description(
        title = title,
        originalTitle = original_title,
        overview = overview,
        tagline = tagline,
        genres = genres.lines().filter { it.isNotBlank() },
        runtimeMinutes = runtime_minutes?.toInt(),
        releaseDate = release_date?.let(CalendarDate::parse),
        endDate = end_date?.let(CalendarDate::parse),
        rating = rating,
        contentRating = content_rating,
        studios = studios.lines().filter { it.isNotBlank() },
    ),
)

private fun Source_metadata.layer(ranks: Map<String, Int>): DescriptionLayer = DescriptionLayer(
    Provenance(Authority.SOURCE, ScanPlanner.sourceArtworkOrigin(SourceId(source_id))),
    Description(
        title = title,
        originalTitle = original_title,
        overview = overview,
        tagline = tagline,
        genres = genres.lines().filter { it.isNotBlank() },
        runtimeMinutes = runtime_minutes?.toInt(),
        releaseDate = release_date?.let(CalendarDate::parse),
        endDate = end_date?.let(CalendarDate::parse),
        rating = rating,
        contentRating = content_rating,
        studios = studios.lines().filter { it.isNotBlank() },
    ),
    order = ranks[source_id] ?: Int.MAX_VALUE,
)

/** A stored picture as an offer to the artwork precedence: a source's own, or a provider's. */
internal fun dev.reflux.library.db.Artwork.offer(ranks: Map<String, Int>): dev.reflux.core.metadata.ArtworkOffer? {
    val kind = enumOrNull<dev.reflux.core.model.ArtworkKind>(kind) ?: return null
    val ofSource = origin.startsWith(ScanPlanner.SOURCE_ARTWORK_PREFIX)
    val locator = if (source_id != null) ArtworkLocator.SourceFile(MediaLocation(SourceId(source_id), locator)) else ArtworkLocator.Remote(locator)
    val authority = if (ofSource) Authority.SOURCE else Authority.PROVIDER
    val order = if (ofSource) ranks[origin.removePrefix(ScanPlanner.SOURCE_ARTWORK_PREFIX)] ?: Int.MAX_VALUE else 0
    return dev.reflux.core.metadata.ArtworkOffer(kind, locator, Provenance(authority, origin), order)
}
