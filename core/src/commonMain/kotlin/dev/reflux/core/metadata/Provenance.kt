package dev.reflux.core.metadata

import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.CalendarDate

/**
 * What a work is described as by one witness: a source (a media server's own description), or a metadata
 * provider. Every field may be missing; a missing field says nothing and never erases another witness's.
 *
 * The same shape serves movies, shows, seasons, and episodes. A credit's [Credit.profileUrl] is, for a source's
 * description, a path inside that source (resolved through it); for a provider's, an address.
 */
data class Description(
    val title: String? = null,
    val originalTitle: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    val genres: List<String> = emptyList(),
    val runtimeMinutes: Int? = null,
    /** First release, or a show's first air date, or an episode's air date. */
    val releaseDate: CalendarDate? = null,
    /** A show's last air date, where it has ended or has aired more than once. */
    val endDate: CalendarDate? = null,
    /** Community rating on a 0–10 scale. */
    val rating: Double? = null,
    val contentRating: String? = null,
    /** A show's networks, or a film's studios, most important first. */
    val studios: List<String> = emptyList(),
    val credits: List<Credit> = emptyList(),
) {
    val empty: Boolean
        get() = title == null && originalTitle == null && overview == null && tagline == null && genres.isEmpty() &&
            runtimeMinutes == null && releaseDate == null && endDate == null && rating == null && contentRating == null &&
            studios.isEmpty() && credits.isEmpty()
}

/**
 * Who is answerable for a value, strongest first. The order is the general one; each field's own policy
 * ([MetadataResolver]) decides where a provider stands against a source.
 */
enum class Authority {
    /** The user said what this is (Identify): the provider entry they chose describes it. */
    USER,

    /** A source's own description: a media server's metadata, or artwork found in the source. */
    SOURCE,

    /** Artwork embedded in a media file or kept beside it locally. Not read yet; reserved for its place. */
    LOCAL,

    /** A metadata provider matched automatically. */
    PROVIDER,

    /** Borrowed from the parent: a show's backdrop for an episode, its poster for a season. */
    INHERITED,

    /** Nothing better: the file's own name, or a generated treatment. */
    FALLBACK,
}

/** Where one value came from: its [authority] and the witness that gave it (`source:<id>`, `tmdb`, `file name`). */
data class Provenance(val authority: Authority, val origin: String) {
    override fun toString(): String = "${authority.name.lowercase()} ($origin)"
}

/** One witness's description and who it is. [order] breaks ties between witnesses of the same authority. */
data class DescriptionLayer(val provenance: Provenance, val description: Description, val order: Int = 0)

/** The fields a description resolves, each with its own provenance. */
enum class MetadataField { TITLE, ORIGINAL_TITLE, OVERVIEW, TAGLINE, GENRES, RUNTIME, RELEASE_DATE, END_DATE, RATING, CONTENT_RATING, STUDIOS, CREDITS }

/** A work's description as Reflux shows it, and where each value in it came from. */
data class ResolvedDescription(val description: Description, val provenance: Map<MetadataField, Provenance>)

/**
 * Field-level, deterministic resolution of a work's description from its witnesses (docs/METADATA.md,
 * "Provenance and precedence").
 *
 * - **Names** ([MetadataField.TITLE], [MetadataField.ORIGINAL_TITLE]) follow identity: the user's choice, then a
 *   provider, then a source. A provider's name is the same whichever sources are connected and is in the
 *   viewer's language, so the unified title does not change as servers come and go.
 * - **Everything else** follows the source first, as the product principle has it: the user's choice, then the
 *   sources, then a provider filling what they leave out. A provider matched automatically never replaces what a
 *   source says; a provider entry the user chose does, because a source's description may be of what the user
 *   corrected.
 *
 * Witnesses of one authority are taken in [DescriptionLayer.order] (the library's source order: the oldest
 * source first), never in the order they were read, so a scan order cannot change the result. Lists (genres,
 * studios, credits) are taken whole from one witness, never mixed.
 */
object MetadataResolver {
    private val names = listOf(Authority.USER, Authority.PROVIDER, Authority.SOURCE, Authority.LOCAL)
    private val descriptive = listOf(Authority.USER, Authority.SOURCE, Authority.LOCAL, Authority.PROVIDER)

    fun policy(field: MetadataField): List<Authority> = when (field) {
        MetadataField.TITLE, MetadataField.ORIGINAL_TITLE -> names
        else -> descriptive
    }

    fun resolve(layers: List<DescriptionLayer>): ResolvedDescription {
        val provenance = mutableMapOf<MetadataField, Provenance>()
        fun <T : Any> pick(field: MetadataField, value: (Description) -> T?): T? {
            val order = policy(field)
            val ranked = layers.filter { it.provenance.authority in order }
                .sortedWith(compareBy({ order.indexOf(it.provenance.authority) }, { it.order }, { it.provenance.origin }))
            for (layer in ranked) {
                val found = value(layer.description) ?: continue
                provenance[field] = layer.provenance
                return found
            }
            return null
        }
        fun text(field: MetadataField, value: (Description) -> String?) = pick(field) { value(it)?.takeIf(String::isNotBlank) }
        fun <T> list(field: MetadataField, value: (Description) -> List<T>) = pick(field) { value(it).takeIf(List<T>::isNotEmpty) }.orEmpty()
        val resolved = Description(
            title = text(MetadataField.TITLE) { it.title },
            originalTitle = text(MetadataField.ORIGINAL_TITLE) { it.originalTitle },
            overview = text(MetadataField.OVERVIEW) { it.overview },
            tagline = text(MetadataField.TAGLINE) { it.tagline },
            genres = list(MetadataField.GENRES) { it.genres },
            runtimeMinutes = pick(MetadataField.RUNTIME) { it.runtimeMinutes?.takeIf { minutes -> minutes > 0 } },
            releaseDate = pick(MetadataField.RELEASE_DATE) { it.releaseDate },
            endDate = pick(MetadataField.END_DATE) { it.endDate },
            rating = pick(MetadataField.RATING) { it.rating?.takeIf { rating -> rating > 0 } },
            contentRating = text(MetadataField.CONTENT_RATING) { it.contentRating },
            studios = list(MetadataField.STUDIOS) { it.studios },
            credits = list(MetadataField.CREDITS) { it.credits },
        )
        return ResolvedDescription(resolved, provenance)
    }
}

/** How strongly a work's provider entry is known, strongest first (docs/METADATA.md, "Matching evidence"). */
enum class MatchEvidence {
    /** Chosen by the user in the Identify flow. */
    USER,

    /** The provider's own ID in a file or folder name (`{tmdb-603}`), or reported by a server. */
    PROVIDER_ID,

    /** Another catalog's ID (IMDb, TVDB) resolved through the provider. */
    EXTERNAL_ID,

    /** A title search, accepted because title and year agree and nothing else comes close. */
    TITLE_YEAR,

    /** A title search without a year, accepted only where the title alone was decisive. */
    TITLE,
}

/** A picture a witness offers for one kind of artwork. [order] breaks ties within an authority. */
data class ArtworkOffer(val kind: ArtworkKind, val locator: ArtworkLocator, val provenance: Provenance, val order: Int = 0)

/**
 * Which picture of each kind a work shows (docs/METADATA.md, "Artwork precedence"): the user's choice, then the
 * sources' own (the oldest source first), then artwork embedded or kept locally, then a provider's. Borrowing
 * from the parent and the generated treatment come after, where a picture is drawn ([Authority.INHERITED],
 * [Authority.FALLBACK]). The cache is how a chosen picture is delivered, never a reason to choose one.
 */
object ArtworkPrecedence {
    private val order = listOf(Authority.USER, Authority.SOURCE, Authority.LOCAL, Authority.PROVIDER, Authority.INHERITED)

    fun choose(offers: List<ArtworkOffer>): Map<ArtworkKind, ArtworkOffer> =
        offers.filter { it.provenance.authority in order }
            .sortedWith(compareBy({ order.indexOf(it.provenance.authority) }, { it.order }, { it.provenance.origin }))
            .groupBy { it.kind }
            .mapValues { it.value.first() }
}
