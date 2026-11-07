package dev.reflux.core.metadata

import dev.reflux.core.identify.IdentityOverride
import dev.reflux.core.identify.ParsedKind
import dev.reflux.core.identify.ParsedMedia
import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.CalendarDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProvenanceTest {
    private val jellyfin = Provenance(Authority.SOURCE, "source:jellyfin")
    private val plex = Provenance(Authority.SOURCE, "source:plex")
    private val tmdb = Provenance(Authority.PROVIDER, "tmdb")
    private val chosen = Provenance(Authority.USER, "tmdb")

    private val serverA = Description(title = "Fallout", overview = "Ethan Hunt, from server A.", genres = listOf("Action"), rating = 7.1)
    private val serverB = Description(title = "M:I Fallout", overview = "From server B.", tagline = "Some missions are not a choice.", genres = listOf("Thriller", "Action"))
    private val provider = Description(
        title = "Mission: Impossible – Fallout", overview = "From the provider.", tagline = "Provider tagline",
        runtimeMinutes = 147, releaseDate = CalendarDate(2018, 7, 13), rating = 7.4, contentRating = "PG-13",
        credits = listOf(Credit("Tom Cruise", CreditRole.ACTOR, "Ethan Hunt")),
    )

    @Test
    fun aSourceDescribesFirstAndAProviderFillsWhatItLeavesOut() {
        val resolved = MetadataResolver.resolve(listOf(DescriptionLayer(tmdb, provider), DescriptionLayer(jellyfin, serverA)))
        val description = resolved.description
        assertEquals("Ethan Hunt, from server A.", description.overview)
        assertEquals(jellyfin, resolved.provenance[MetadataField.OVERVIEW])
        assertEquals(listOf("Action"), description.genres)
        assertEquals(7.1, description.rating)
        // What the server left out comes from the provider, said so.
        assertEquals(147, description.runtimeMinutes)
        assertEquals("Provider tagline", description.tagline)
        assertEquals(tmdb, resolved.provenance[MetadataField.RUNTIME])
        assertEquals(1, description.credits.size)
    }

    @Test
    fun theNameFollowsIdentityNotWhicheverServerIsConnected() {
        val resolved = MetadataResolver.resolve(listOf(DescriptionLayer(jellyfin, serverA), DescriptionLayer(tmdb, provider)))
        assertEquals("Mission: Impossible – Fallout", resolved.description.title)
        assertEquals(tmdb, resolved.provenance[MetadataField.TITLE])
        // Without a provider, the source names it.
        assertEquals("Fallout", MetadataResolver.resolve(listOf(DescriptionLayer(jellyfin, serverA))).description.title)
    }

    @Test
    fun theUsersChoiceOutranksEverySourceForEveryField() {
        val resolved = MetadataResolver.resolve(listOf(DescriptionLayer(jellyfin, serverA), DescriptionLayer(chosen, provider)))
        assertEquals("From the provider.", resolved.description.overview)
        assertEquals(chosen, resolved.provenance[MetadataField.OVERVIEW])
        assertEquals(7.4, resolved.description.rating)
        // A field the chosen entry lacks is still filled by the source.
        val sparse = MetadataResolver.resolve(listOf(DescriptionLayer(jellyfin, serverA), DescriptionLayer(chosen, Description(title = "Fallout (chosen)"))))
        assertEquals("Ethan Hunt, from server A.", sparse.description.overview)
    }

    @Test
    fun twoServersResolveTheSameWayWhateverOrderTheyAreReadIn() {
        val a = DescriptionLayer(jellyfin, serverA, order = 0)
        val b = DescriptionLayer(plex, serverB, order = 1)
        val one = MetadataResolver.resolve(listOf(a, b))
        val other = MetadataResolver.resolve(listOf(b, a))
        assertEquals(one, other)
        assertEquals("Ethan Hunt, from server A.", one.description.overview)
        // A list is one witness's, never a mixture.
        assertEquals(listOf("Action"), one.description.genres)
        // What the older server lacks, the newer one gives.
        assertEquals("Some missions are not a choice.", one.description.tagline)
        assertEquals(plex, one.provenance[MetadataField.TAGLINE])
    }

    @Test
    fun blankAndZeroValuesAreNotValues() {
        val blank = Description(overview = "  ", rating = 0.0, runtimeMinutes = 0)
        val resolved = MetadataResolver.resolve(listOf(DescriptionLayer(jellyfin, blank), DescriptionLayer(tmdb, provider)))
        assertEquals("From the provider.", resolved.description.overview)
        assertEquals(7.4, resolved.description.rating)
        assertEquals(147, resolved.description.runtimeMinutes)
        assertNull(MetadataResolver.resolve(emptyList()).description.overview)
        assertTrue(MetadataResolver.resolve(emptyList()).provenance.isEmpty())
    }

    @Test
    fun artworkPrefersTheUserThenSourcesInOrderThenTheProvider() {
        fun offer(url: String, provenance: Provenance, order: Int = 0) = ArtworkOffer(ArtworkKind.POSTER, ArtworkLocator.Remote(url), provenance, order)
        val chosenOffers = listOf(offer("tmdb", tmdb), offer("plex", plex, 1), offer("jellyfin", jellyfin, 0))
        assertEquals("jellyfin", (ArtworkPrecedence.choose(chosenOffers)[ArtworkKind.POSTER]!!.locator as ArtworkLocator.Remote).url)
        assertEquals(ArtworkPrecedence.choose(chosenOffers), ArtworkPrecedence.choose(chosenOffers.reversed()))
        assertEquals("tmdb", (ArtworkPrecedence.choose(listOf(offer("tmdb", tmdb)))[ArtworkKind.POSTER]!!.locator as ArtworkLocator.Remote).url)
        val user = offer("mine", Provenance(Authority.USER, "user"))
        assertEquals(user, ArtworkPrecedence.choose(chosenOffers + user)[ArtworkKind.POSTER])
        // A fallback is never chosen as a work's own picture.
        assertTrue(ArtworkPrecedence.choose(listOf(offer("generated", Provenance(Authority.FALLBACK, "generated")))).isEmpty())
    }

    @Test
    fun anIdentificationDropsTheIdsItCorrects() {
        val parsed = ParsedMedia(kind = ParsedKind.MOVIE, title = "Dune", year = 1984, externalIds = mapOf("tmdb" to "841"))
        val corrected = IdentityOverride(ParsedKind.MOVIE, "Dune", 2021).applyTo(parsed)
        assertTrue(corrected.externalIds.isEmpty())
    }
}
