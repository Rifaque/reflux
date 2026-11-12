package dev.reflux.library

import dev.reflux.core.identify.ParsedKind
import dev.reflux.core.identify.ParsedMedia
import dev.reflux.core.model.Availability
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.ProfileId
import dev.reflux.core.model.StableIds
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.source.CatalogEntry
import dev.reflux.core.source.CatalogSource
import dev.reflux.core.source.CatalogVersion
import dev.reflux.core.source.ConnectionKind
import dev.reflux.core.source.ConnectionRequest
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.core.source.SourceCapability
import dev.reflux.core.source.SourceDescriptor
import dev.reflux.core.source.SourceLocality
import dev.reflux.core.source.SourceUserState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** A shelf every profile shares: a folder or a share, nobody's account. */
private class SharedShelf : CatalogSource {
    override val descriptor = SourceDescriptor(StableIds.sourceId("shelf", "shared"), "shelf", "Shared shelf", SourceLocality.DEVICE, setOf(SourceCapability.PROVIDES_CATALOG))
    override suspend fun availability() = Availability.AVAILABLE
    override suspend fun playbackTarget(path: String) = PlaybackTarget("file:///shared/$path")
    override fun catalog(): Flow<CatalogEntry> = listOf(
        CatalogEntry(ParsedMedia(ParsedKind.MOVIE, "Heat", 1995), listOf(CatalogVersion("shared/heat", 1, 1, StreamInfo(Container.MATROSKA, durationMs = 6_000_000)))),
        CatalogEntry(ParsedMedia(ParsedKind.MOVIE, "Arrival", 2016), listOf(CatalogVersion("shared/arrival", 1, 1, StreamInfo(Container.MATROSKA, durationMs = 6_000_000)))),
    ).asFlow()
}

/**
 * The broad isolation property of M8 (docs/WATCH_STATE.md, "Profiles"): with two profiles that each have their own
 * account on one server, and a shelf both share, no read the library offers ever gives one profile a title that is
 * only on the other's account, or anything of the other's viewing. It sweeps every read model rather than each
 * screen; [everyQueryIsBoundToTheActiveProfile] holds the SQL itself to the same rule, so a new query cannot quietly
 * skip it.
 */
class ProfileIsolationTest {
    private var clock = 1_790_000_000_000L
    private val library = Library(testDatabase()) { clock }
    private val registry = SourceRegistry(
        mapOf(
            "acct" to { record -> AccountConnector.describe(record.config).let { AccountServer(it.address, it.username!!) } },
            "shelf" to { _ -> SharedShelf() },
        ),
    )
    private val connections = SourceConnections(library, registry, listOf(AccountConnector))

    @BeforeTest
    fun reset() = AccountServers.reset()

    /** Titles only on Alex's account, and only on Sam's. */
    private val alexOnly = setOf("Primer", "Severance", "Coherence")
    private val samOnly = setOf("Alien", "Dark")

    private suspend fun world(): Pair<ProfileId, ProfileId> {
        val shelf = SharedShelf()
        library.addSource(shelf, "")
        registry.register(shelf)
        AccountServers.movie("alex", "heat", "Heat", 1995, SourceUserState(false, 30 * 60_000, 0, clock - 5_000, favorite = true))
        AccountServers.movie("alex", "primer", "Primer", 2004, SourceUserState(false, 20 * 60_000, 0, clock - 4_000, favorite = true))
        AccountServers.movie("alex", "coherence", "Coherence", 2013)
        AccountServers.episode("alex", "sev1", "Severance", 1, 1)
        AccountServers.episode("alex", "sev2", "Severance", 1, 2)
        AccountServers.movie("sam", "alien", "Alien", 1979, SourceUserState(true, 0, 1, clock - 3_000, favorite = true))
        AccountServers.episode("sam", "dark1", "Dark", 1, 1)
        val alex = library.activeProfile()
        connections.connect(ConnectionKind.SERVER, ConnectionRequest("acct://home", "alex", "pw"))
        val sam = library.createProfile("Sam")
        library.switchProfile(sam)
        connections.connect(ConnectionKind.SERVER, ConnectionRequest("acct://home", "sam", "pw"))
        for (record in library.sources()) registry.resolve(record.id)?.let { library.scan(it) }
        // Each profile's own viewing of things both can see, and of its own.
        for ((profile, title) in listOf(alex to "Primer", sam to "Alien")) {
            library.switchProfile(profile)
            val shared = library.movies().first { it.item.title == "Arrival" }.item.id
            library.stopped(shared, (if (profile == alex) 11 else 22) * 60_000L, 100 * 60_000L, null)
            val own = library.movies().first { it.item.title == title }.item.id
            library.setFavorite(own, true)
            library.createCollection("$title list").let { library.addToCollection(it.id, own) }
            library.setPreferredVersion(own, library.versions(own).first().version.id)
        }
        return alex to sam
    }

    @Test
    fun noReadGivesAProfileAnotherProfilesTitlesOrViewing() = runTest {
        val (alex, sam) = world()
        for ((me, other, mine, theirs) in listOf(Quad(alex, sam, alexOnly, samOnly), Quad(sam, alex, samOnly, alexOnly))) {
            library.switchProfile(me)
            val seen = everything()
            val leaked = seen.titles intersect theirs
            if (leaked.isNotEmpty()) fail("profile ${me.value} saw ${other.value}'s titles $leaked in ${seen.where(leaked)}")
            assertTrue(mine.all { it in seen.titles }, "${me.value} sees its own: ${mine - seen.titles}")
            // Viewing: what Arrival says is this profile's own place in it.
            val arrival = library.movies().single { it.item.title == "Arrival" }
            assertEquals((if (me == alex) 11 else 22) * 60_000L, arrival.watchState?.positionMs)
            assertTrue(library.watchState(arrival.item.id, other)?.positionMs != arrival.watchState?.positionMs)
            // Heat is on the shared shelf and on Alex's account: the same work, but only Alex sees Alex's copy and place.
            val heat = library.movies().single { it.item.title == "Heat" }
            val heatCopies = library.versions(heat.item.id).map { it.version.location.path }
            if (me == alex) assertTrue("alex/heat" in heatCopies) else {
                assertEquals(listOf("shared/heat"), heatCopies)
                assertEquals(null, heat.watchState, "Alex's place in Heat is not Sam's")
                assertEquals(false, heat.favorite)
            }
            assertTrue(library.userSources().all { it.profileId == null || it.profileId == me })
        }
    }

    private data class Quad(val me: ProfileId, val other: ProfileId, val mine: Set<String>, val theirs: Set<String>)

    /** Every title each read model gives the active profile, by where it came from. */
    private class Seen(val byRead: Map<String, Set<String>>) {
        val titles: Set<String> get() = byRead.values.flatten().toSet()
        fun where(titles: Set<String>) = byRead.filterValues { it.any(titles::contains) }.keys
    }

    private fun everything(): Seen {
        val reads = linkedMapOf<String, Set<String>>()
        fun titlesOf(entries: List<LibraryEntry>) = entries.map { showTitle(it) }.toSet()
        reads["movies"] = titlesOf(library.movies())
        reads["shows"] = titlesOf(library.shows())
        reads["recentlyAdded"] = titlesOf(library.recentlyAdded(100))
        reads["continueWatching"] = titlesOf(library.continueWatching(100))
        reads["continuations"] = library.continuations(100).flatMap { listOfNotNull(showTitle(it.entry), it.show?.item?.title) }.toSet()
        reads["watchNext"] = WatchNext.programs(library.continuations(100)).map { it.title }.toSet()
        reads["favorites"] = titlesOf(library.favorites())
        reads["collections"] = library.userCollections().flatMap { titlesOf(library.collectionEntries(it)) }.toSet()
        reads["franchises"] = library.franchises().flatMap { titlesOf(library.collectionEntries(it)) }.toSet()
        for (query in listOf("severance", "primer", "alien", "dark", "coherence", "heat", "s01e01")) {
            reads["search:$query"] = titlesOf(library.search(query))
        }
        for (query in listOf("movies I haven't watched", "shows", "episodes", "watched movies", "favorites")) {
            reads["smartSearch:$query"] = titlesOf(library.smartSearch(query).results)
        }
        val diagnostics = library.diagnostics()
        reads["diagnostics.lowConfidence"] = diagnostics.lowConfidence.mapNotNull { library.item(it.version.itemId)?.title }.toSet()
        reads["diagnostics.unmatched"] = titlesOf(diagnostics.unmatched)
        reads["diagnostics.multipleVersions"] = titlesOf(diagnostics.multipleVersions)
        reads["diagnostics.missingEpisodes"] = diagnostics.missingEpisodes.map { it.showTitle }.toSet()
        reads["downloads"] = library.visibleDownloads().mapNotNull { library.item(it.itemId)?.title }.toSet()
        for (record in library.userSources()) {
            reads["source:${record.displayName}:movies"] = titlesOf(library.movies(LibraryScope.OfSource(record.id)))
            reads["source:${record.displayName}:shows"] = titlesOf(library.shows(LibraryScope.OfSource(record.id)))
        }
        for (show in library.shows()) {
            val detail = library.showDetail(show.item.id) ?: continue
            reads["showDetail:${show.item.title}"] = detail.seasons.flatMap { season -> season.episodes.map { showTitle(it) } }.toSet() + setOfNotNull(detail.show.item.title)
            reads["episodeVersionChoices:${show.item.title}"] = library.episodeVersionChoices(show.item.id).keys.mapNotNull { library.item(it)?.let(::titleOf) }.toSet()
        }
        return Seen(reads)
    }

    private fun showTitle(entry: LibraryEntry): String = titleOf(entry.item)

    private fun titleOf(item: dev.reflux.core.model.MediaItem): String = when (item) {
        is dev.reflux.core.model.Episode -> library.item(item.showId)?.title ?: item.title
        is dev.reflux.core.model.Season -> library.item(item.showId)?.title ?: item.title
        else -> item.title
    }

    /**
     * Every query in the schema that reads versions (what a work has, and so what a profile may see) is bound to the
     * active profile's view (`visible`, or `included`, which requires it), and every query that reads viewer state
     * names the profile, unless it is one of the few that act for the whole installation and are listed here with
     * the reason. A new query that reads either without being bound fails here until it is bound or listed.
     */
    @Test
    fun everyQueryIsBoundToTheActiveProfile() {
        val sq = Path.of("src/commonMain/sqldelight/dev/reflux/library/db/Library.sq")
        val queries = labeled(Files.readString(sq))
        val unboundVersionReads = queries.filter { (name, sql) ->
            readsVersions(sql) && "visible" !in sql && "included" !in sql && name !in INSTALLATION_VERSION_READS
        }.keys
        assertTrue(unboundVersionReads.isEmpty(), "queries reading versions without the profile's view: $unboundVersionReads")
        val unboundViewerReads = queries.filter { (name, sql) ->
            VIEWER_STATE.any { Regex("""\b$it\b""").containsMatchIn(sql) } && "profile_id" !in sql && name !in INSTALLATION_VIEWER_STATE
        }.keys
        assertTrue(unboundViewerReads.isEmpty(), "queries reading or writing viewer state without a profile: $unboundViewerReads")
        // The lists stay true: every name in them is a query that still exists.
        assertTrue((INSTALLATION_VERSION_READS + INSTALLATION_VIEWER_STATE).all { it in queries }, "${(INSTALLATION_VERSION_READS + INSTALLATION_VIEWER_STATE) - queries.keys}")
    }

    /** The labeled statements of a `.sq` file, by label. */
    private fun labeled(text: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        var name: String? = null
        val body = StringBuilder()
        fun flush() {
            name?.let { result[it] = body.toString() }
            name = null
            body.clear()
        }
        for (line in text.lines()) {
            val label = Regex("""^([a-zA-Z][a-zA-Z0-9]*):\s*$""").find(line)
            when {
                label != null -> { flush(); name = label.groupValues[1] }
                line.isBlank() || line.startsWith("CREATE ") -> flush()
                name != null && !line.trimStart().startsWith("--") -> body.append(line).append('\n')
            }
        }
        flush()
        return result
    }

    private fun readsVersions(sql: String): Boolean =
        Regex("""\b(FROM|JOIN)\s+version\b""").containsMatchIn(sql) && Regex("""^\s*(SELECT|WITH)""", RegexOption.IGNORE_CASE).containsMatchIn(sql.trimStart()) ||
            Regex("""EXISTS\s*\(\s*SELECT\s+1\s+FROM\s+version""").containsMatchIn(sql) && sql.trimStart().startsWith("SELECT")

    private companion object {
        /** Viewer-state tables (docs/WATCH_STATE.md, "Profiles"). */
        val VIEWER_STATE = listOf("watch_state", "watch_sync", "favorite", "version_preference", "user_collection", "user_collection_item", "profile_setting")

        /**
         * Reads of versions that are about the installation, not what a viewer sees: scanning, identifying, probing,
         * keeping copies current, and counts for a source a profile already sees. Each says why.
         */
        val INSTALLATION_VERSION_READS = setOf(
            "sourceContents", // counts for one source, shown only for sources the profile sees (visibleSources)
            "sectionContents", // the same, for one library of such a source
            "yearHintsExcludingSource", // identification: years other sources know for a title (media truth)
            "anyPlayables", // the change signal every screen reloads on; returns nothing shown
            "versionsAwaitingProbe", // probing reads every source's files, whoever's they are
            "versionsOfSource", // scanning and re-identifying one source
            "versionById", // one version by its ID, which only a read the profile sees hands out
            "catalogDescriptionsOfSource", // re-identifying one source offline
            "worksNeedingMetadata", // looking up metadata is media truth, for the whole installation
            "showsWithEpisodesNeedingMetadata", // the same
            "unificationCandidates", // two works that are one work are one for everyone
            "sourcesOfWork", // the same: which sources a work's identity comes from
            "staleMetadata", // refreshing metadata, for the whole installation
        )

        /** Statements on viewer state that act for every profile at once, and why. */
        val INSTALLATION_VIEWER_STATE = setOf(
            "deleteOrphanPlayables", // a work is kept while anyone's history or favorite refers to it
            "deleteEmptySeasons", // the same, for a season someone favorited
            "deleteEmptyShows", // the same, for a show
            "deleteWatchSyncOfSource", // a removed source's records go with it, whoever's account it was
            "deleteFavoriteEverywhere", // two works became one: the merged one keeps each profile's favorite
            "moveFavorite", // the same move, profile by profile (it copies profile_id)
            "moveCollectionMembership", // the same, for collections
            "deleteCollectionMembershipOf", // the same
            "movePinsWithTheirVersions", // a pin follows its version to the work it now belongs to, in every profile
            "watchStatesOfItemEverywhere", // two works became one: each profile's state is merged on its own
            "preferencesOfItemEverywhere", // the same, for pins
            "renameUserCollection", // by the collection's own ID, which only its profile's list hands out
            "deleteUserCollection", // the same
            "deleteUserCollectionItems", // the same
            "userCollectionById", // the same
            "removeFromUserCollection", // the same
            "userCollectionItemIds", // the same
            "addToUserCollection", // takes the profile from the collection itself
        )
    }
}
