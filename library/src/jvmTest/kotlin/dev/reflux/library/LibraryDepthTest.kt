package dev.reflux.library

import dev.reflux.core.identify.ParsedKind
import dev.reflux.core.identify.ParsedMedia
import dev.reflux.core.metadata.Authority
import dev.reflux.core.metadata.Credit
import dev.reflux.core.metadata.CreditRole
import dev.reflux.core.metadata.Description
import dev.reflux.core.metadata.EpisodeMetadata
import dev.reflux.core.metadata.MatchEvidence
import dev.reflux.core.metadata.MetadataCandidate
import dev.reflux.core.metadata.MetadataField
import dev.reflux.core.metadata.MetadataKind
import dev.reflux.core.metadata.ProviderRef
import dev.reflux.core.metadata.RemoteArtwork
import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.Availability
import dev.reflux.core.model.CalendarDate
import dev.reflux.core.model.MediaLocation
import dev.reflux.core.model.StableIds
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.source.CatalogEntry
import dev.reflux.core.source.CatalogSeason
import dev.reflux.core.source.CatalogSource
import dev.reflux.core.source.CatalogVersion
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.core.source.SourceCapability
import dev.reflux.core.source.SourceDescriptor
import dev.reflux.core.source.SourceLocality
import dev.reflux.core.source.SourceUnavailableException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A media server whose catalog describes its works (docs/METADATA.md, "Provenance and precedence"). */
private class DescribingServer(name: String) : CatalogSource {
    var entries = mutableListOf<CatalogEntry>()
    var reachable = true
    override val descriptor = SourceDescriptor(
        StableIds.sourceId("server", "https://$name.example"), "server", name, SourceLocality.LOCAL_NETWORK, setOf(SourceCapability.PROVIDES_CATALOG),
    )
    override suspend fun availability() = if (reachable) Availability.AVAILABLE else Availability.UNAVAILABLE
    override suspend fun playbackTarget(path: String) = PlaybackTarget("https://media.example/$path")
    override fun catalog(): Flow<CatalogEntry> = if (reachable) entries.toList().asFlow() else flow { throw SourceUnavailableException(descriptor.id) }

    fun movie(id: String, title: String, year: Int, description: Description?, tmdb: String? = null, poster: Boolean = true) {
        entries += CatalogEntry(
            identity = ParsedMedia(ParsedKind.MOVIE, title, year, externalIds = listOfNotNull(tmdb?.let { "tmdb" to it }).toMap()),
            versions = listOf(CatalogVersion("items/$id", 1_000_000_000, 1, StreamInfo(container = Container.MATROSKA, durationMs = 6_000_000))),
            artwork = if (poster) mapOf(ArtworkKind.POSTER to "items/$id/poster") else emptyMap(),
            description = description,
        )
    }

    fun episode(id: String, show: String, season: Int, episode: Int, description: Description?, showDescription: Description?, seasonOf: CatalogSeason?, still: Boolean = true) {
        entries += CatalogEntry(
            identity = ParsedMedia(ParsedKind.EPISODE, show, 2022, season = season, episode = episode, episodeTitle = description?.title),
            versions = listOf(CatalogVersion("items/$id", 1_000_000_000, 1, StreamInfo(container = Container.MATROSKA))),
            artwork = if (still) mapOf(ArtworkKind.THUMBNAIL to "items/$id/still") else emptyMap(),
            showArtwork = mapOf(ArtworkKind.POSTER to "items/show/poster"),
            description = description,
            showDescription = showDescription,
            season = seasonOf,
        )
    }
}

class LibraryDepthTest {
    private var clock = 1_790_000_000_000L
    private val library = Library(testDatabase()) { clock }
    private val jellyfin = DescribingServer("jellyfin")
    private val plex = DescribingServer("plex")
    private val provider = FakeMetadataProvider()

    private val fallout = Description(
        title = "Fallout", overview = "From the Jellyfin server.", genres = listOf("Action"), rating = 7.1,
        credits = listOf(Credit("Tom Cruise", CreditRole.ACTOR, "Ethan Hunt", "items/person1/picture")),
    )

    init {
        library.addSource(jellyfin, "jellyfin")
        clock += 1_000 // the Plex server is added later: it is weighed second where the two disagree
        library.addSource(plex, "plex")
    }

    private fun movieId() = library.movies().single().item.id

    @Test
    fun aServersOwnDescriptionIsShownWithoutAProvider() = runTest {
        jellyfin.movie("m1", "Fallout", 2018, fallout)
        library.scan(jellyfin)
        val metadata = library.metadata(movieId())!!
        assertEquals("From the Jellyfin server.", metadata.overview)
        assertEquals(listOf("Action"), metadata.genres)
        assertNull(metadata.ref, "described, not matched")
        assertEquals(Authority.SOURCE, metadata.provenance[MetadataField.OVERVIEW]?.authority)
        val cast = library.cast(movieId()).single()
        assertEquals("Ethan Hunt", cast.character)
        assertEquals(ArtworkLocator.SourceFile(MediaLocation(jellyfin.descriptor.id, "items/person1/picture")), cast.picture, "read through the server")
    }

    @Test
    fun aProviderFillsWhatTheServerLeavesOutAndNamesTheWork() = runTest {
        val ref = provider.movie("353081", "Mission: Impossible - Fallout", 2018)
        provider.works[ref] = provider.works[ref]!!.copy(runtimeMinutes = 147, tagline = "Some missions are not a choice", credits = listOf(Credit("Henry Cavill", CreditRole.ACTOR)))
        jellyfin.movie("m1", "Fallout", 2018, fallout, tmdb = "353081")
        library.scan(jellyfin)
        library.refreshMetadata(provider, "en-US")
        val metadata = library.metadata(movieId())!!
        assertEquals("Mission: Impossible - Fallout", metadata.title, "the provider's name, the same whichever server is connected")
        assertEquals("From the Jellyfin server.", metadata.overview, "the server's description first")
        assertEquals(147, metadata.runtimeMinutes, "what the server left out")
        assertEquals(Authority.PROVIDER, metadata.provenance[MetadataField.RUNTIME]?.authority)
        assertEquals(MatchEvidence.PROVIDER_ID, metadata.evidence)
        assertEquals("Mission: Impossible - Fallout", library.movies().single().item.title)
        assertEquals(listOf("Tom Cruise"), library.cast(movieId()).map { it.name }, "the server's cast, as one list")
    }

    @Test
    fun twoServersResolveTheSameWhicheverIsScannedFirst() = runTest {
        jellyfin.movie("m1", "Fallout", 2018, fallout)
        plex.movie("p1", "Fallout", 2018, Description(title = "Fallout", overview = "From the Plex server.", tagline = "Plex's tagline"))
        library.scan(plex)
        library.scan(jellyfin)
        val first = library.metadata(movieId())!!
        val firstArt = library.movies().single().artwork
        library.scan(plex)
        assertEquals(first, library.metadata(movieId()))
        assertEquals("From the Jellyfin server.", first.overview, "the server added first is weighed first")
        assertEquals("Plex's tagline", first.tagline, "what it lacks, the other gives")
        assertEquals(ArtworkLocator.SourceFile(MediaLocation(jellyfin.descriptor.id, "items/m1/poster")), firstArt[ArtworkKind.POSTER])
        assertEquals(firstArt, library.movies().single().artwork)
        assertEquals(1, library.movies().size, "one work, two versions")
        assertEquals(2, library.versions(movieId()).size)
    }

    @Test
    fun aServerAwayKeepsWhatItSaid() = runTest {
        jellyfin.movie("m1", "Fallout", 2018, fallout)
        library.scan(jellyfin)
        jellyfin.reachable = false
        library.scan(jellyfin)
        assertEquals("From the Jellyfin server.", library.metadata(movieId())!!.overview)
        assertEquals(1, library.cast(movieId()).size)
    }

    @Test
    fun theUsersIdentificationOutranksTheServerAndSurvivesRescans() = runTest {
        // The server matched the film to the wrong entry; the user says what it is.
        val wrong = provider.movie("1", "Fallout", 1997)
        val right = provider.movie("353081", "Mission: Impossible - Fallout", 2018)
        provider.works[right] = provider.works[right]!!.copy(overview = "The chosen entry.")
        jellyfin.movie("m1", "Fallout", 2018, fallout, tmdb = "1")
        library.scan(jellyfin)
        library.refreshMetadata(provider, "en-US")
        assertEquals(wrong, library.metadata(movieId())!!.ref)
        val id = library.identifyAs(movieId(), MetadataCandidate(right, "Mission: Impossible - Fallout", year = 2018), provider, "en-US")
        val metadata = library.metadata(id)!!
        assertEquals(right, metadata.ref)
        assertEquals("The chosen entry.", metadata.overview, "the user's choice outranks the server's description")
        assertEquals(MatchEvidence.USER, metadata.evidence)

        // A rescan, a refresh, and unification keep it: the server's wrong ID no longer counts.
        library.scan(jellyfin)
        library.refreshMetadata(provider, "en-US")
        assertEquals(MetadataRefresh.REFRESHED, library.refreshDetails(library.movies().single().item.id, provider, "en-US"))
        plex.movie("p1", "Fallout", 1997, null, tmdb = "1")
        library.scan(plex)
        library.unifyWorks()
        assertEquals(2, library.movies().size, "the corrected film is not merged back with the one it was mistaken for")
        val corrected = library.movies().single { it.metadata?.ref == right }
        assertEquals("The chosen entry.", corrected.metadata!!.overview)
    }

    @Test
    fun aRefreshReadsTheKnownEntryAgainAndNeverSearches() = runTest {
        provider.movie("353081", "Mission: Impossible - Fallout", 2018)
        jellyfin.movie("m1", "Mission: Impossible - Fallout", 2018, null)
        library.scan(jellyfin)
        library.refreshMetadata(provider, "en-US")
        assertEquals(MatchEvidence.TITLE_YEAR, library.metadata(movieId())!!.evidence)
        // A better-looking candidate appears; a refresh does not go looking for it.
        provider.movie("999", "Mission: Impossible - Fallout", 2018, popularity = 99.0)
        provider.calls.clear()
        assertEquals(MetadataRefresh.REFRESHED, library.refreshDetails(movieId(), provider, "en-US"))
        assertTrue(provider.calls.none { it.startsWith("search") }, provider.calls.toString())
        assertEquals("353081", library.metadata(movieId())!!.ref!!.id)
    }

    @Test
    fun aServersProviderIdReplacesAWeakerGuess() = runTest {
        provider.movie("5", "Heat", 1995)
        val right = provider.movie("949", "Heat", 1995)
        jellyfin.movie("m1", "Heat", 1995, null)
        library.scan(jellyfin)
        provider.popularity[ProviderRef("tmdb", MetadataKind.MOVIE, "5")] = 1000.0
        provider.works.remove(right)
        library.refreshMetadata(provider, "en-US")
        provider.movie("949", "Heat", 1995)
        val guessed = library.metadata(movieId())?.ref
        jellyfin.entries.clear()
        jellyfin.movie("m1", "Heat", 1995, null, tmdb = "949")
        library.scan(jellyfin)
        library.refreshDetails(movieId(), provider, "en-US")
        assertEquals("949", library.metadata(movieId())!!.ref!!.id, "was ${guessed?.id}")
        assertEquals(MatchEvidence.PROVIDER_ID, library.metadata(movieId())!!.evidence)
    }

    @Test
    fun aFailedRefreshKeepsEverythingAndTouchesNoWatchState() = runTest {
        val ref = provider.movie("353081", "Mission: Impossible - Fallout", 2018, artwork = arrayOf(RemoteArtwork(ArtworkKind.BACKDROP, "https://img/backdrop.jpg")))
        jellyfin.movie("m1", "Fallout", 2018, fallout, tmdb = ref.id)
        library.scan(jellyfin)
        library.refreshMetadata(provider, "en-US")
        library.stopped(movieId(), 1_000_000, 6_000_000)
        val before = library.metadata(movieId())
        val artBefore = library.movies().single().artwork
        provider.offline = true
        assertEquals(MetadataRefresh.UNREACHABLE, library.refreshDetails(movieId(), provider, "en-US"))
        assertEquals(before, library.metadata(movieId()))
        assertEquals(artBefore, library.movies().single().artwork)
        assertEquals(1_000_000, library.watchState(movieId())!!.positionMs)
        // An entry the provider no longer has keeps what was known of it.
        provider.offline = false
        provider.works.remove(ref)
        library.refreshDetails(movieId(), provider, "en-US")
        assertEquals(before, library.metadata(movieId()))
    }

    @Test
    fun seasonsAndEpisodesAreDescribedAndPicturedByTheServer() = runTest {
        val showDescription = Description(title = "Severance", overview = "Work and life, severed.", studios = listOf("Apple TV+"), endDate = CalendarDate(2025, 3, 21))
        val season = CatalogSeason(title = "Season One", overview = "Mark leads a team.", airDate = CalendarDate(2022, 2, 18), artwork = mapOf(ArtworkKind.POSTER to "items/s1/poster"))
        jellyfin.episode("e1", "Severance", 1, 1, Description(title = "Good News About Hell", overview = "Helly wakes.", rating = 8.0), showDescription, season)
        jellyfin.episode("e2", "Severance", 1, 2, Description(title = "Half Loop"), showDescription, season, still = false)
        library.scan(jellyfin)
        val show = library.shows().single()
        assertEquals(listOf("Apple TV+"), show.metadata!!.studios)
        assertEquals(CalendarDate(2025, 3, 21), show.metadata!!.endDate)
        val detail = library.showDetail(show.item.id)!!.seasons.single()
        assertEquals("Season One", detail.entry.metadata!!.title)
        assertEquals("Mark leads a team.", detail.entry.metadata!!.overview)
        assertEquals(ArtworkLocator.SourceFile(MediaLocation(jellyfin.descriptor.id, "items/s1/poster")), detail.entry.artwork[ArtworkKind.POSTER])
        val (first, second) = detail.episodes
        assertEquals("Helly wakes.", first.metadata!!.overview)
        assertEquals(8.0, first.metadata!!.rating)
        assertEquals(ArtworkLocator.SourceFile(MediaLocation(jellyfin.descriptor.id, "items/e1/still")), first.artwork[ArtworkKind.THUMBNAIL])

        // The provider fills the still the server lacks; the server's own still stays first.
        val ref = provider.show("95396", "Severance", 2022, mapOf(1 to 2))
        provider.seasons[ref to 1] = provider.seasons[ref to 1]!!.copy(
            airDate = CalendarDate(2022, 2, 18),
            episodes = listOf(
                EpisodeMetadata(1, 1, "Good News About Hell", stillUrl = "https://img/1.jpg", guestStars = listOf(Credit("Guest", CreditRole.ACTOR))),
                EpisodeMetadata(1, 2, "Half Loop", stillUrl = "https://img/2.jpg", rating = 7.5),
            ),
        )
        library.refreshMetadata(provider, "en-US")
        val after = library.showDetail(show.item.id)!!.seasons.single().episodes
        assertEquals(ArtworkLocator.SourceFile(MediaLocation(jellyfin.descriptor.id, "items/e1/still")), after[0].artwork[ArtworkKind.THUMBNAIL])
        assertEquals(ArtworkLocator.Remote("https://img/2.jpg"), after[1].artwork[ArtworkKind.THUMBNAIL])
        assertEquals(7.5, after[1].metadata!!.rating)
        assertEquals(listOf("Guest"), library.cast(after[0].item.id).map { it.name }, "an episode's guest cast")
        // A season refresh reads only that season.
        provider.calls.clear()
        assertEquals(MetadataRefresh.REFRESHED, library.refreshDetails(after[0].item.id, provider, "en-US"))
        assertEquals(listOf("season:95396:1"), provider.calls)
    }

    @Test
    fun staleMetadataIsReadAgainAFewAtATime() = runTest {
        repeat(3) { index -> provider.movie("${index + 1}", "Film $index", 2000 + index); jellyfin.movie("m$index", "Film $index", 2000 + index, null, tmdb = "${index + 1}") }
        library.scan(jellyfin)
        library.refreshMetadata(provider, "en-US")
        provider.calls.clear()
        assertEquals(0, library.refreshStaleMetadata(provider, "en-US"), "nothing is stale yet")
        clock += 91L * 24 * 3_600_000
        assertEquals(2, library.refreshStaleMetadata(provider, "en-US", limit = 2))
        assertEquals(2, provider.calls.count { it.startsWith("details") })
        assertEquals(1, library.refreshStaleMetadata(provider, "en-US", limit = 2), "the rest next time")
        provider.offline = true
        clock += 91L * 24 * 3_600_000
        assertNull(library.refreshStaleMetadata(provider, "en-US"))
        assertEquals(3, library.movies().count { it.metadata?.overview != null }, "nothing is lost while the provider is away")
    }

    @Test
    fun turningOnlineLookupsOffAsksTheProviderNothing() = runTest {
        provider.movie("353081", "Mission: Impossible - Fallout", 2018)
        jellyfin.movie("m1", "Fallout", 2018, fallout, tmdb = "353081")
        val registry = SourceRegistry(emptyMap()).apply { register(jellyfin) }
        val pipeline = LibraryPipeline(library, registry, metadata = provider)
        assertTrue(library.onlineMetadata)
        library.setOnlineMetadata(false)
        assertFalse(pipeline.canIdentify)
        assertTrue(pipeline.hasProvider)
        pipeline.refresh()
        assertTrue(provider.calls.isEmpty(), provider.calls.toString())
        assertEquals("From the Jellyfin server.", library.metadata(movieId())!!.overview, "what the server says still shows")
        assertNull(pipeline.refreshDetails(movieId()))
        library.setOnlineMetadata(true)
        pipeline.refresh()
        assertEquals("Mission: Impossible - Fallout", library.metadata(movieId())!!.title)
    }

    @Test
    fun aRemovedServerTakesItsDescriptionsAndAnotherSourceRemains() = runTest {
        jellyfin.movie("m1", "Fallout", 2018, fallout)
        plex.movie("p1", "Fallout", 2018, Description(overview = "From the Plex server."))
        library.scan(jellyfin)
        library.scan(plex)
        library.removeSource(jellyfin.descriptor.id)
        assertEquals("From the Plex server.", library.metadata(movieId())!!.overview)
        assertTrue(library.cast(movieId()).isEmpty())
    }

    @Test
    fun anOfflineReidentificationKeepsTheServersDescription() = runTest {
        jellyfin.movie("m1", "Fallout", 2018, fallout)
        library.scan(jellyfin)
        jellyfin.reachable = false
        // Identify re-plans the server's works from what it last said, without asking it.
        val ref = provider.movie("353081", "Mission: Impossible - Fallout", 2018)
        provider.works[ref] = provider.works[ref]!!.copy(overview = null)
        val id = library.identifyAs(movieId(), MetadataCandidate(ref, "Mission: Impossible - Fallout", year = 2018), provider, "en-US")
        assertEquals("From the Jellyfin server.", library.metadata(id)!!.overview, "the chosen entry has none; the server's stays")
        assertNotNull(library.entry(id))
    }
}
