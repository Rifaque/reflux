package dev.reflux.library

import dev.reflux.core.model.MediaId
import dev.reflux.core.model.ProfileId
import dev.reflux.core.profile.PinCheck
import dev.reflux.core.profile.PinPolicy
import dev.reflux.core.source.ConnectionKind
import dev.reflux.core.source.ConnectionRequest
import dev.reflux.core.source.SourceUserState
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MINUTE = 60_000L

/** Profiles and account isolation (M8; docs/WATCH_STATE.md, "Profiles"). */
class ProfilesTest {
    private var clock = 1_790_000_000_000L
    private val file = Files.createTempDirectory("reflux-profiles").resolve("library.db")
    private val library = Library(LibraryDatabase.open(file)) { clock }
    private val registry = AccountConnector.registry
    private val connections = SourceConnections(library, registry, listOf(AccountConnector))

    @BeforeTest
    fun reset() = AccountServers.reset()

    private suspend fun signIn(account: String) =
        assertIs<ConnectResult.Connected>(connections.connect(ConnectionKind.SERVER, ConnectionRequest("acct://home", account, "pw"))).sourceId

    private suspend fun scanAll() = library.sources().forEach { record -> registry.resolve(record.id)?.let { library.scan(it) } }

    private fun titles() = library.movies().map { it.item.title }.toSet()

    @Test
    fun anotherViewerSignedInToTheSameServerSeesNothingOfTheFirst() = runTest {
        // Alex watches on their own account: the server knows a place in Heat, and Primer is only on Alex's account.
        AccountServers.movie("alex", "heat", "Heat", 1995, SourceUserState(false, 40 * MINUTE, 0, clock - 1_000, favorite = true))
        AccountServers.movie("alex", "primer", "Primer", 2004)
        AccountServers.episode("alex", "sev1", "Severance", 1, 1)
        AccountServers.movie("sam", "heat", "Heat", 1995)
        AccountServers.movie("sam", "alien", "Alien", 1979)
        val main = library.activeProfile()
        signIn("alex")
        scanAll()
        val heat = library.movies().single { it.item.title == "Heat" }
        assertEquals(40 * MINUTE, heat.watchState?.positionMs)
        assertTrue(heat.favorite)
        library.createCollection("Weekend").let { library.addToCollection(it.id, heat.item.id) }
        library.setPreferredVersion(heat.item.id, library.versions(heat.item.id).single().version.id)
        assertEquals(listOf("Heat"), library.continueWatching().map { it.item.title })

        // Sam gets a profile of their own and signs in to the same server, once, without typing it again.
        val sam = library.createProfile("Sam")
        library.switchProfile(sam)
        assertEquals(listOf("acct://home"), library.endpoints().map { it.address }, "the server's address is known to every profile")
        signIn("sam")
        scanAll()

        assertEquals(setOf("Heat", "Alien"), titles(), "nothing that is only on Alex's account")
        assertTrue(library.shows().isEmpty(), "Alex's shows neither")
        val samsHeat = library.movies().single { it.item.title == "Heat" }
        assertNull(samsHeat.watchState, "Alex's place in it is Alex's")
        assertFalse(samsHeat.favorite)
        assertTrue(library.continueWatching().isEmpty())
        assertTrue(library.favorites().isEmpty())
        assertTrue(library.userCollections().isEmpty())
        assertTrue(library.pinnedVersions(samsHeat.item.id).isEmpty())
        assertEquals(listOf(samsHeat.item.id), library.versions(samsHeat.item.id).map { it.version.itemId }, "only Sam's own copy")
        assertTrue(library.versions(samsHeat.item.id).all { it.version.location.path.startsWith("sam/") })
        assertEquals(listOf("Home server"), library.userSources().map { it.displayName })
        assertTrue(library.userSources().all { it.profileId == sam })
        // Sam's account was never told anything of Alex's viewing.
        assertTrue(AccountServers.told["sam"].orEmpty().isEmpty(), "${AccountServers.told["sam"]}")

        // Sam plays Heat on their own account: Alex's state is untouched, Alex's server hears nothing.
        val reporter = SyncingWatchReporter(library, library.versions(samsHeat.item.id).single().version, registry.resolve(library.userSources().single().id), backgroundScope)
        reporter.stopped(samsHeat.item.id, 10 * MINUTE, 100 * MINUTE)
        assertEquals(10 * MINUTE, library.watchState(samsHeat.item.id)?.positionMs)
        assertEquals(40 * MINUTE, library.watchState(heat.item.id, main)?.positionMs)
        assertTrue(AccountServers.told["alex"].orEmpty().isEmpty(), "${AccountServers.told["alex"]}")

        // Back to Alex: everything is as it was.
        library.switchProfile(main)
        assertEquals(setOf("Heat", "Primer"), titles())
        assertEquals(40 * MINUTE, library.movies().single { it.item.title == "Heat" }.watchState?.positionMs)
        assertEquals(1, library.favorites().size)
        assertEquals(1, library.userCollections().size)
    }

    @Test
    fun aPlaybackWritesToTheProfileItStartedInWhateverIsActiveLater() = runTest(UnconfinedTestDispatcher()) {
        AccountServers.movie("alex", "heat", "Heat", 1995)
        val main = library.activeProfile()
        val alex = signIn("alex")
        scanAll()
        val heat = library.movies().single()
        val reporter = SyncingWatchReporter(library, library.versions(heat.item.id).single().version, registry.resolve(alex), this)
        assertEquals(main, reporter.profile)
        reporter.progress(heat.item.id, 5 * MINUTE, 100 * MINUTE)

        val sam = library.createProfile("Sam")
        library.switchProfile(sam)
        reporter.progress(heat.item.id, 12 * MINUTE, 100 * MINUTE)
        reporter.stopped(heat.item.id, 15 * MINUTE, 100 * MINUTE)

        assertEquals(15 * MINUTE, library.watchState(heat.item.id, main)?.positionMs, "the playback stayed Alex's")
        assertNull(library.watchState(heat.item.id, sam), "and never became Sam's")
        val told: List<String> = AccountServers.told["alex"].orEmpty()
        assertEquals(listOf("progress alex/heat", "progress alex/heat", "stopped alex/heat"), told, "Alex's own account hears it")
    }

    @Test
    fun clearingLocalWatchHistoryIsLocalAndIsNotBroughtBack() = runTest {
        AccountServers.movie("alex", "heat", "Heat", 1995, SourceUserState(false, 40 * MINUTE, 0, clock - 10_000, favorite = false))
        AccountServers.movie("alex", "alien", "Alien", 1979, SourceUserState(true, 0, 1, clock - 10_000, favorite = false))
        signIn("alex")
        scanAll()
        assertEquals(2, library.movies().count { it.watchState != null })

        clock += 1_000
        library.clearWatchHistory(library.activeProfile())
        assertTrue(library.movies().all { it.watchState == null })
        assertTrue(library.continueWatching().isEmpty())

        // The server's unchanged records are not news: nothing comes back, and the server is told nothing.
        clock += 1_000
        scanAll()
        assertTrue(library.movies().all { it.watchState == null }, "${library.movies().map { it.watchState }}")
        assertTrue(AccountServers.told["alex"].orEmpty().isEmpty(), "clearing never changes the server: ${AccountServers.told["alex"]}")

        // Watching on the server after the clearing is news as usual.
        clock += 1_000
        AccountServers.setState("alex", "heat", SourceUserState(false, 70 * MINUTE, 0, clock, favorite = false))
        scanAll()
        assertEquals(70 * MINUTE, library.movies().single { it.item.title == "Heat" }.watchState?.positionMs)
        assertNull(library.movies().single { it.item.title == "Alien" }.watchState, "what did not change stays cleared")
    }

    @Test
    fun clearingOneProfileLeavesAnotherAlone() = runTest {
        AccountServers.movie("alex", "heat", "Heat", 1995)
        signIn("alex")
        scanAll()
        val heat = library.movies().single().item.id
        library.stopped(heat, 30 * MINUTE, 100 * MINUTE, null)
        val main = library.activeProfile()
        val sam = library.createProfile("Sam")
        library.stopped(heat, 20 * MINUTE, 100 * MINUTE, null, sam)
        library.clearWatchHistory(sam)
        assertEquals(30 * MINUTE, library.watchState(heat, main)?.positionMs)
        assertNull(library.watchState(heat, sam))
    }

    @Test
    fun anAccountIsOneProfilesAndCannotBeSignedInToFromAnother() = runTest {
        AccountServers.movie("alex", "heat", "Heat", 1995)
        val alex = signIn("alex")
        library.switchProfile(library.createProfile("Sam"))
        val refused = connections.connect(ConnectionKind.SERVER, ConnectionRequest("acct://home", "alex", "pw"))
        assertEquals(ConnectResult.InAnotherProfile("Main"), refused)
        assertEquals(ProfileId.DEFAULT, library.source(alex)?.profileId, "it stays where it was")
        assertTrue(library.userSources().isEmpty())
    }

    @Test
    fun aPinIsCheckedSlowsGuessersDownAndIsNeverKept() {
        val key = MemoryPinKey()
        val main = library.activeProfile()
        assertFalse(library.setPin(main, "12a4", key))
        assertTrue(library.setPin(main, "2580", key))
        assertTrue(library.profile(main)!!.hasPin)
        assertEquals(PinCheck.Accepted, library.checkPin(main, "2580", key))
        repeat(PinPolicy.FREE_ATTEMPTS - 1) { assertEquals(PinCheck.Rejected(null), library.checkPin(main, "0000", key)) }
        val locked = assertIs<PinCheck.Rejected>(library.checkPin(main, "0000", key))
        assertEquals(clock + PinPolicy.FIRST_LOCK_MS, locked.lockedUntilEpochMs)
        // Waiting is kept: a library opened again on the same file still waits, and even the right PIN is not tried.
        val reopened = Library(LibraryDatabase.open(file)) { clock }
        assertEquals(PinCheck.Locked(clock + PinPolicy.FIRST_LOCK_MS), reopened.checkPin(main, "2580", key))
        clock += PinPolicy.FIRST_LOCK_MS
        assertEquals(PinCheck.Accepted, reopened.checkPin(main, "2580", key))
        assertEquals(PinCheck.Rejected(null), reopened.checkPin(main, "0001", key), "the count started again")
        // The PIN itself is nowhere in the database.
        assertFalse("2580" in String(Files.readAllBytes(file), Charsets.ISO_8859_1))
        library.removePin(main)
        assertFalse(library.profile(main)!!.hasPin)
        assertEquals(PinCheck.Accepted, library.checkPin(main, "", key))
    }

    @Test
    fun deletingAProfileRemovesWhatWasItsOwnAndNothingElse() = runTest {
        AccountServers.movie("alex", "heat", "Heat", 1995)
        val main = library.activeProfile()
        signIn("alex")
        scanAll()
        val heat = library.movies().single().item.id
        library.stopped(heat, 30 * MINUTE, 100 * MINUTE, null)
        val sam = library.createProfile("Sam")
        library.switchProfile(sam)
        library.stopped(heat, 10 * MINUTE, 100 * MINUTE, null)
        library.setFavorite(heat, true)
        library.createCollection("Sam's")

        library.deleteProfile(sam)
        assertEquals(main, library.activeProfile(), "the active profile that went is replaced")
        assertEquals(listOf("Main"), library.profiles().map { it.name })
        assertEquals(30 * MINUTE, library.watchState(heat)?.positionMs)
        assertTrue(library.favorites().isEmpty())
        assertTrue(library.queries.allFavorites(sam.value).executeAsList().isEmpty())
        assertTrue(library.queries.userCollections(sam.value).executeAsList().isEmpty())
        assertNull(library.watchState(heat, sam))
        library.deleteProfile(main)
        assertEquals(1, library.profiles().size, "the last profile stays")
    }

    @Test
    fun aProfilesAccountsMustBeRemovedBeforeIt() = runTest {
        val sam = library.createProfile("Sam")
        library.switchProfile(sam)
        AccountServers.movie("sam", "alien", "Alien", 1979)
        val account = signIn("sam")
        assertEquals(listOf(account), library.accountSourcesOf(sam).map { it.id })
        assertTrue(runCatching { library.deleteProfile(sam) }.isFailure, "its server must be signed out of first")
        connections.remove(account).await()
        library.deleteProfile(sam)
        assertNull(library.profile(sam))
    }

    @Test
    fun aNewLibraryHasOneInvisibleProfile() {
        assertEquals(listOf(Profile(ProfileId.DEFAULT, "Main", active = true, hasPin = false, lockedUntilEpochMs = null)), library.profiles())
        val renamed = assertNotNull(library.profile(ProfileId.DEFAULT)).let { library.renameProfile(it.id, "  Alex  "); library.profile(it.id)!! }
        assertEquals("Alex", renamed.name)
    }

    @Test
    fun aForgottenPinResetsOnlyThatProfileAndTouchesNoServer() = runTest {
        val key = MemoryPinKey()
        AccountServers.movie("alex", "heat", "Heat", 1995, SourceUserState(false, 40 * MINUTE, 0, clock - 1_000, favorite = true))
        AccountServers.movie("sam", "alien", "Alien", 1979)
        val main = library.activeProfile()
        val alex = signIn("alex")
        scanAll()
        val heat = library.movies().single().item.id
        library.createCollection("Weekend").let { library.addToCollection(it.id, heat) }
        library.setPreferredVersion(heat, library.versions(heat).single().version.id)
        library.setProfileSetting("subtitles", "en")
        library.setPin(main, "2580", key)
        // Another viewer, with their own account and their own history.
        val sam = library.createProfile("Sam")
        library.switchProfile(sam)
        val samsAccount = signIn("sam")
        scanAll()
        val alien = library.movies().single { it.item.title == "Alien" }.item.id
        library.stopped(alien, 20 * MINUTE, 100 * MINUTE, null)
        library.setFavorite(alien, true)
        val addresses = library.endpoints()
        AccountServers.told.clear()

        // Main's PIN is forgotten: a wrong one is refused, and the reset needs its accounts gone first.
        assertEquals(PinCheck.Rejected(null), library.checkPin(main, "0000", key))
        assertTrue(runCatching { library.resetProfile(main) }.isFailure, "its server is signed out of first")
        connections.remove(alex).await()
        library.resetProfile(main)
        library.keepEndpoints(addresses)

        val reset = library.profile(main)!!
        assertEquals("Main", reset.name, "the profile stays")
        assertFalse(reset.hasPin)
        assertNull(reset.lockedUntilEpochMs)
        assertEquals(PinCheck.Accepted, library.checkPin(main, "anything", key), "it opens without a PIN")
        assertNull(library.watchState(heat, main))
        assertTrue(library.queries.allFavorites(main.value).executeAsList().isEmpty())
        assertTrue(library.queries.userCollections(main.value).executeAsList().isEmpty())
        assertNull(library.queries.preferredVersion(main.value, heat.value).executeAsOneOrNull())
        assertNull(library.queries.profileSettingValue(main.value, "subtitles").executeAsOneOrNull())
        assertTrue(library.accountSourcesOf(main).isEmpty())
        assertEquals(listOf("acct://home"), library.endpoints().map { it.address }, "the server's address is still known")
        // No server was told anything about anyone's viewing.
        assertTrue(AccountServers.told.values.all { it.isEmpty() }, "${AccountServers.told}")
        // Sam is exactly as before.
        assertEquals(sam, library.activeProfile())
        assertEquals(20 * MINUTE, library.watchState(alien, sam)?.positionMs)
        assertEquals(listOf("Alien"), library.favorites().map { it.item.title })
        assertEquals(listOf(samsAccount), library.accountSourcesOf(sam).map { it.id })
        // A new PIN can be set on the profile that was reset, and a library opened again finds it unlocked until then.
        assertFalse(Library(LibraryDatabase.open(file)) { clock }.profile(main)!!.hasPin)
        assertTrue(library.setPin(main, "1111", key))
        assertEquals(PinCheck.Accepted, library.checkPin(main, "1111", key))
    }

    @Test
    fun aSharedServersRecordsAreComparedWithNobodyOnceItsProfileIsGone() = runTest {
        // A server everyone shares, whose watch records were compared with the profile that added it.
        val sam = library.createProfile("Sam")
        library.switchProfile(sam)
        val shared = AccountServer("acct://open", "anyone")
        AccountServers.movie("anyone", "heat", "Heat", 1995, SourceUserState(true, 0, 1, clock - 1_000, favorite = false))
        library.addSource(shared, AccountConnector.config("acct://open", "anyone"))
        assertEquals(sam, library.syncProfileOf(shared.descriptor.id))
        library.scan(shared)
        library.switchProfile(ProfileId.DEFAULT)
        library.deleteProfile(sam)
        assertNull(library.syncProfileOf(shared.descriptor.id), "it names nobody, rather than a profile that is gone")
        // And nobody inherits the server's record of the viewer who is gone.
        library.scan(shared)
        assertNull(library.movies().single { it.item.title == "Heat" }.watchState)
    }

    @Test
    fun onlyWhatAProfileCanSeeCountsAsSeen() = runTest {
        AccountServers.movie("alex", "heat", "Heat", 1995)
        AccountServers.episode("alex", "sev1", "Severance", 1, 1)
        signIn("alex")
        scanAll()
        val heat = library.movies().single().item.id
        val show = library.shows().single().item.id
        assertTrue(library.seenByActiveProfile(heat) && library.seenByActiveProfile(show))
        library.switchProfile(library.createProfile("Sam"))
        assertFalse(library.seenByActiveProfile(heat), "another profile's film")
        assertFalse(library.seenByActiveProfile(show), "another profile's show")
        assertFalse(library.seenByActiveProfile(MediaId("nothing")))
    }
}
