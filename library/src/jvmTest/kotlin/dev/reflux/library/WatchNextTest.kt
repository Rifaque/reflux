package dev.reflux.library

import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.Availability
import dev.reflux.core.model.Episode
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.Movie
import dev.reflux.sources.local.LocalFolderSource
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.moveTo
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MINUTE = 60_000L

/**
 * The launcher's "continue" row follows Reflux's canonical watch state: what is on it is derived from
 * Continue Watching every time, and the rows already published are only compared with that.
 */
class WatchNextTest {
    private val root: Path = Files.createTempDirectory("reflux-watch-next")
    private val other: Path = Files.createTempDirectory("reflux-watch-next-other")
    private var clock = 1_790_000_000_000L
    private val library = Library(testDatabase()) { clock }
    private val source = LocalFolderSource(root, "Media")

    init {
        library.addSource(source, root.toString())
    }

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
        other.toFile().deleteRecursively()
    }

    private fun file(base: Path, relative: String): Path =
        base.resolve(relative).also { it.parent.createDirectories(); it.writeBytes(ByteArray(10)) }

    private suspend fun scanned(vararg files: String) {
        files.forEach { file(root, it) }
        library.scan(source)
    }

    private val heat get() = library.movies().single { it.item.title == "Heat" }.item.id
    private val episodes: List<MediaId>
        get() = library.showDetail(library.shows().single().item.id)!!.seasons.flatMap { it.episodes }.map { it.item.id }

    private fun programs() = WatchNext.programs(library.continuations())

    /** The launcher after applying [changes], as a list of rows, so that a test can run several rounds. */
    private class Launcher {
        val rows = mutableListOf<PublishedProgram>()
        var dismissed = emptyMap<String, Long>()
        private var next = 1L

        fun apply(changes: List<WatchNextChange>) = changes.forEach { change ->
            when (change) {
                is WatchNextChange.Add -> rows += PublishedProgram(next++, change.program.key, change.program.fingerprint, browsable = true)
                is WatchNextChange.Update -> rows.replaceAll { if (it.rowId == change.rowId) it.copy(fingerprint = change.program.fingerprint) else it }
                is WatchNextChange.Remove -> rows.removeAll { it.rowId == change.rowId }
            }
        }

        fun dismiss(key: String) = rows.replaceAll { if (it.key == key) it.copy(browsable = false) else it }
        val visible get() = rows.filter { it.browsable }.map { it.key }
    }

    private fun Launcher.sync(): List<WatchNextChange> {
        val plan = WatchNext.plan(programs(), rows, dismissed)
        dismissed = plan.dismissed
        apply(plan.changes)
        return plan.changes
    }

    // Eligibility and the two kinds -------------------------------------------------------------------

    @Test
    fun aFilmPartWayThroughContinuesFromItsPlace() = runTest {
        scanned("Movies/Heat (1995)/Heat (1995).mkv")
        library.stopped(heat, 40 * MINUTE, 170 * MINUTE)

        val program = programs().single()
        assertEquals(WatchNextType.CONTINUE, program.type)
        assertEquals(WatchNextProgram.Kind.MOVIE, program.kind)
        assertEquals("Heat", program.title)
        assertEquals(1995, program.year)
        assertEquals(40 * MINUTE, program.positionMs)
        assertEquals(170 * MINUTE, program.durationMs)
        assertEquals(clock, program.lastEngagedAtEpochMs)
    }

    @Test
    fun aFilmNotReallyStartedOrFinishedIsNotThere() = runTest {
        scanned("Movies/Heat (1995)/Heat (1995).mkv", "Movies/Alien (1979)/Alien (1979).mkv")
        val alien = library.movies().single { it.item.title == "Alien" }.item.id
        library.stopped(heat, 30_000, 170 * MINUTE) // under a minute: nothing to continue
        library.stopped(alien, 112 * MINUTE, 117 * MINUTE) // watched to the end

        assertTrue(programs().isEmpty())
        assertEquals(true, library.watchState(alien)?.completed)
    }

    @Test
    fun aFinishedEpisodeOffersTheNextOneUnderTheShowsName() = runTest {
        scanned("TV/Breaking Bad/Season 1/Breaking.Bad.S01E01.Pilot.mkv", "TV/Breaking Bad/Season 1/Breaking.Bad.S01E02.mkv")
        library.stopped(episodes[0], 46 * MINUTE, 47 * MINUTE)

        val program = programs().single()
        assertEquals(episodes[1], program.itemId)
        assertEquals(WatchNextType.NEXT, program.type)
        assertEquals(WatchNextProgram.Kind.EPISODE, program.kind)
        assertEquals("Breaking Bad", program.title)
        assertEquals(1, program.seasonNumber)
        assertEquals(2, program.episodeNumber)
        assertNull(program.positionMs, "the next episode starts at the beginning")
        assertNull(program.durationMs)
        assertEquals(clock, program.lastEngagedAtEpochMs, "the show was last engaged with when its pilot was")
    }

    @Test
    fun anEpisodePartWayThroughContinues() = runTest {
        scanned("TV/Breaking Bad/Season 1/Breaking.Bad.S01E01.Pilot.mkv", "TV/Breaking Bad/Season 1/Breaking.Bad.S01E02.mkv")
        library.stopped(episodes[0], 20 * MINUTE, 47 * MINUTE)

        val program = programs().single()
        assertEquals(episodes[0], program.itemId)
        assertEquals(WatchNextType.CONTINUE, program.type)
        assertEquals(20 * MINUTE, program.positionMs)
    }

    @Test
    fun anEpisodeBarelyBegunIsTheNextOneNotAPlaceToContinue() = runTest {
        scanned("TV/Breaking Bad/Season 1/Breaking.Bad.S01E01.Pilot.mkv", "TV/Breaking Bad/Season 1/Breaking.Bad.S01E02.mkv")
        library.stopped(episodes[0], 46 * MINUTE, 47 * MINUTE)
        clock += MINUTE
        library.stopped(episodes[1], 20_000, 47 * MINUTE)

        val program = programs().single()
        assertEquals(episodes[1], program.itemId)
        assertEquals(WatchNextType.NEXT, program.type)
        assertNull(program.positionMs)
    }

    @Test
    fun aFinishedShowLeavesTheRow() = runTest {
        scanned("TV/Breaking Bad/Season 1/Breaking.Bad.S01E01.Pilot.mkv", "TV/Breaking Bad/Season 1/Breaking.Bad.S01E02.mkv")
        library.stopped(episodes[0], 46 * MINUTE, 47 * MINUTE)
        library.stopped(episodes[1], 46 * MINUTE, 47 * MINUTE)
        assertTrue(programs().isEmpty())
    }

    // Following the canonical state ------------------------------------------------------------------

    @Test
    fun theRowIsContinueWatchingInTheSameOrder() = runTest {
        scanned(
            "Movies/Heat (1995)/Heat (1995).mkv",
            "Movies/Alien (1979)/Alien (1979).mkv",
            "TV/Breaking Bad/Season 1/Breaking.Bad.S01E01.Pilot.mkv",
            "TV/Breaking Bad/Season 1/Breaking.Bad.S01E02.mkv",
        )
        val alien = library.movies().single { it.item.title == "Alien" }.item.id
        library.stopped(heat, 40 * MINUTE, 170 * MINUTE)
        clock += MINUTE
        library.stopped(episodes[0], 46 * MINUTE, 47 * MINUTE)
        clock += MINUTE
        library.stopped(alien, 10 * MINUTE, 117 * MINUTE)

        assertEquals(listOf(alien, episodes[1], heat), programs().map { it.itemId })
        assertEquals(library.continueWatching().map { it.item.id }, programs().map { it.itemId })
        assertEquals(programs().map { it.lastEngagedAtEpochMs }.sortedDescending(), programs().map { it.lastEngagedAtEpochMs })
    }

    @Test
    fun theRowHoldsAtMostTheLimit() = runTest {
        val titles = (1..WatchNext.LIMIT + 3).map { "Film $it (${1990 + it})" }
        scanned(*titles.map { "Movies/$it/$it.mkv" }.toTypedArray())
        for (movie in library.movies()) {
            clock += MINUTE
            library.stopped(movie.item.id, 30 * MINUTE, 100 * MINUTE)
        }
        val programs = programs()
        assertEquals(WatchNext.LIMIT, programs.size)
        assertEquals(clock, programs.first().lastEngagedAtEpochMs, "the most recent are kept")
    }

    @Test
    fun watchingOnUpdatesTheFilmInPlace() = runTest {
        scanned("Movies/Heat (1995)/Heat (1995).mkv")
        val launcher = Launcher()
        library.stopped(heat, 40 * MINUTE, 170 * MINUTE)
        assertIsAdd(launcher.sync().single())

        assertTrue(launcher.sync().isEmpty(), "nothing changed, so nothing is written")

        clock += MINUTE
        library.stopped(heat, 80 * MINUTE, 170 * MINUTE)
        val update = launcher.sync().single()
        assertTrue(update is WatchNextChange.Update && update.program.positionMs == 80 * MINUTE)
        assertEquals(1, launcher.rows.size)

        clock += MINUTE
        library.stopped(heat, 168 * MINUTE, 170 * MINUTE) // finished
        assertTrue(launcher.sync().single() is WatchNextChange.Remove)
        assertTrue(launcher.rows.isEmpty())
    }

    @Test
    fun theNextEpisodeTakesTheFinishedOnesPlace() = runTest {
        scanned(
            "TV/Breaking Bad/Season 1/Breaking.Bad.S01E01.Pilot.mkv",
            "TV/Breaking Bad/Season 1/Breaking.Bad.S01E02.mkv",
            "TV/Breaking Bad/Season 2/Breaking.Bad.S02E01.mkv",
        )
        val launcher = Launcher()
        library.stopped(episodes[0], 20 * MINUTE, 47 * MINUTE)
        launcher.sync()
        assertEquals(listOf(episodes[0].value), launcher.visible)

        clock += MINUTE
        library.stopped(episodes[0], 46 * MINUTE, 47 * MINUTE)
        launcher.sync()
        assertEquals(listOf(episodes[1].value), launcher.visible, "one episode per show, the next one")

        clock += MINUTE
        library.stopped(episodes[1], 46 * MINUTE, 47 * MINUTE)
        launcher.sync()
        assertEquals(listOf(episodes[2].value), launcher.visible, "into the next season")

        clock += MINUTE
        library.stopped(episodes[2], 46 * MINUTE, 47 * MINUTE)
        launcher.sync()
        assertTrue(launcher.rows.isEmpty())
    }

    @Test
    fun aRewatchIsContinuedLikeAnyOtherPlayback() = runTest {
        scanned("Movies/Heat (1995)/Heat (1995).mkv")
        val launcher = Launcher()
        library.setWatched(heat, true)
        launcher.sync()
        assertTrue(launcher.rows.isEmpty(), "a watched film is not offered")

        clock += MINUTE
        library.stopped(heat, 50 * MINUTE, 170 * MINUTE)
        launcher.sync()
        assertEquals(listOf(heat.value), launcher.visible)
        assertEquals(true, library.watchState(heat)?.completed, "rewatching does not un-watch it")
        assertEquals(50 * MINUTE, programs().single().positionMs)
    }

    @Test
    fun markingWatchedInReflux() = runTest {
        scanned("Movies/Heat (1995)/Heat (1995).mkv")
        val launcher = Launcher()
        library.stopped(heat, 40 * MINUTE, 170 * MINUTE)
        launcher.sync()
        library.setWatched(heat, true)
        launcher.sync()
        assertTrue(launcher.rows.isEmpty())
    }

    @Test
    fun theLauncherNeverChangesWatchState() = runTest {
        scanned("Movies/Heat (1995)/Heat (1995).mkv")
        library.stopped(heat, 40 * MINUTE, 170 * MINUTE)
        val before = library.watchState(heat)
        val launcher = Launcher()
        // A row from an older playback, with a position the launcher still shows: replaced, never believed.
        launcher.rows += PublishedProgram(7, heat.value, programs().single().copy(positionMs = 90 * MINUTE).fingerprint, browsable = true)
        val update = launcher.sync().single()
        assertTrue(update is WatchNextChange.Update && update.rowId == 7L && update.program.positionMs == 40 * MINUTE)
        launcher.dismiss(heat.value)
        launcher.sync()
        assertEquals(before, library.watchState(heat))
        assertEquals(false, library.watchState(heat)?.completed)
    }

    // Sources ---------------------------------------------------------------------------------------

    @Test
    fun aSourceThatIsAwayKeepsItsWorksOnTheRow() = runTest {
        scanned("Movies/Heat (1995)/Heat (1995).mkv")
        library.stopped(heat, 40 * MINUTE, 170 * MINUTE)
        val launcher = Launcher()
        launcher.sync()

        val unplugged = root.resolveSibling(root.fileName.toString() + "-unplugged")
        root.moveTo(unplugged)
        try {
            library.scan(source)
            assertEquals(Availability.UNAVAILABLE, library.movies().single().availability)
            assertTrue(launcher.sync().isEmpty(), "a drive unplugged or a server asleep is not the end of the film")
            assertEquals(listOf(heat.value), launcher.visible)
        } finally {
            unplugged.moveTo(root)
        }
    }

    @Test
    fun aRemovedSourceTakesItsWorksOffTheRow() = runTest {
        scanned("Movies/Heat (1995)/Heat (1995).mkv")
        library.stopped(heat, 40 * MINUTE, 170 * MINUTE)
        val launcher = Launcher()
        launcher.sync()

        library.removeSource(source.descriptor.id)
        assertTrue(launcher.sync().single() is WatchNextChange.Remove)
        assertTrue(launcher.rows.isEmpty())
    }

    @Test
    fun oneWorkAcrossSourcesIsOneProgramWithOnePlace() = runTest {
        scanned("Movies/Heat (1995)/Heat (1995).mkv")
        val second = LocalFolderSource(other, "Backup")
        library.addSource(second, other.toString())
        file(other, "Heat (1995).mp4")
        library.scan(second)
        assertEquals(2, library.versions(heat).size, "one work, two versions")

        library.stopped(heat, 40 * MINUTE, 170 * MINUTE)
        val launcher = Launcher()
        launcher.sync()
        assertEquals(listOf(heat.value), launcher.visible)

        // The copy that was played goes; the work, its place, and its program stay.
        library.removeSource(source.descriptor.id)
        assertTrue(launcher.sync().isEmpty())
        assertEquals(40 * MINUTE, programs().single().positionMs)
        assertEquals(listOf(heat.value), launcher.visible)
    }

    // The launcher's own rows ------------------------------------------------------------------------

    @Test
    fun whatTheUserRemovedFromTheLauncherIsDeletedAndStaysOffUntilPlayedAgain() = runTest {
        scanned("Movies/Heat (1995)/Heat (1995).mkv")
        library.stopped(heat, 40 * MINUTE, 170 * MINUTE)
        val launcher = Launcher()
        launcher.sync()
        launcher.dismiss(heat.value)

        assertTrue(launcher.sync().single() is WatchNextChange.Remove, "the hidden row is deleted, as the platform expects")
        assertTrue(launcher.rows.isEmpty())
        assertTrue(launcher.sync().isEmpty(), "and not added back")
        assertEquals(40 * MINUTE, library.watchState(heat)?.positionMs, "removing it from the launcher changes nothing in Reflux")
        assertEquals(listOf(heat), library.continueWatching().map { it.item.id }, "Continue Watching in Reflux keeps it")

        clock += MINUTE
        library.stopped(heat, 60 * MINUTE, 170 * MINUTE) // played again, in Reflux
        assertIsAdd(launcher.sync().single())
        assertEquals(listOf(heat.value), launcher.visible)
    }

    @Test
    fun aDismissalIsForgottenOnceTheWorkIsNotToBeContinued() = runTest {
        scanned("Movies/Heat (1995)/Heat (1995).mkv")
        library.stopped(heat, 40 * MINUTE, 170 * MINUTE)
        val launcher = Launcher()
        launcher.sync()
        launcher.dismiss(heat.value)
        launcher.sync()
        assertEquals(setOf(heat.value), launcher.dismissed.keys)

        library.setWatched(heat, true)
        launcher.sync()
        assertTrue(launcher.dismissed.isEmpty())
    }

    @Test
    fun aDismissedShowComesBackWithTheNextEpisodeWatched() = runTest {
        scanned(
            "TV/Breaking Bad/Season 1/Breaking.Bad.S01E01.Pilot.mkv",
            "TV/Breaking Bad/Season 1/Breaking.Bad.S01E02.mkv",
            "TV/Breaking Bad/Season 1/Breaking.Bad.S01E03.mkv",
        )
        library.stopped(episodes[0], 46 * MINUTE, 47 * MINUTE)
        val launcher = Launcher()
        launcher.sync()
        launcher.dismiss(episodes[1].value)
        launcher.sync()
        assertTrue(launcher.rows.isEmpty())

        clock += MINUTE
        library.stopped(episodes[1], 46 * MINUTE, 47 * MINUTE)
        launcher.sync()
        assertEquals(listOf(episodes[2].value), launcher.visible)
    }

    @Test
    fun staleDuplicateAndUnknownRowsAreRemoved() {
        val program = WatchNextProgram(MediaId("m1"), WatchNextType.CONTINUE, WatchNextProgram.Kind.MOVIE, "Heat", positionMs = MINUTE * 40, durationMs = MINUTE * 170, lastEngagedAtEpochMs = 1)
        val published = listOf(
            PublishedProgram(1, "gone", "x", browsable = true),
            PublishedProgram(2, "m1", program.fingerprint, browsable = true),
            PublishedProgram(3, "m1", program.fingerprint, browsable = true),
            PublishedProgram(4, null, null, browsable = true),
        )
        assertEquals(
            listOf(WatchNextChange.Remove(1), WatchNextChange.Remove(3), WatchNextChange.Remove(4)),
            WatchNext.plan(listOf(program), published).changes,
        )
    }

    @Test
    fun aHiddenDuplicateMeansTheUserRemovedIt() {
        val program = WatchNextProgram(MediaId("m1"), WatchNextType.CONTINUE, WatchNextProgram.Kind.MOVIE, "Heat", lastEngagedAtEpochMs = 5)
        val published = listOf(PublishedProgram(1, "m1", "old", browsable = false), PublishedProgram(2, "m1", program.fingerprint, browsable = true))
        val plan = WatchNext.plan(listOf(program), published)
        assertEquals(listOf(WatchNextChange.Remove(1), WatchNextChange.Remove(2)), plan.changes)
        assertEquals(mapOf("m1" to 5L), plan.dismissed)
    }

    @Test
    fun aRowIsComparedByWhatThePlatformStores() {
        val program = WatchNextProgram(MediaId("m1"), WatchNextType.CONTINUE, WatchNextProgram.Kind.MOVIE, "Heat", lastEngagedAtEpochMs = 5)
        val published = listOf(PublishedProgram(1, "m1", program.fingerprint, browsable = true))
        assertTrue(WatchNext.plan(listOf(program), published).changes.isEmpty())
        // The same program, now with artwork the platform can show: rewritten.
        assertEquals(listOf<WatchNextChange>(WatchNextChange.Update(1, program)), WatchNext.plan(listOf(program), published) { it.fingerprint + "content://art" }.changes)
    }

    @Test
    fun aWideImageIsPreferredForTheCard() = runTest {
        scanned("Movies/Heat (1995)/Heat (1995).mkv")
        library.stopped(heat, 40 * MINUTE, 170 * MINUTE)
        val entry = library.entry(heat)!!
        fun program(vararg art: Pair<ArtworkKind, String>) =
            WatchNext.program(Continuation(entry.copy(artwork = art.associate { (kind, url) -> kind to ArtworkLocator.Remote(url) }), null, clock))!!.artwork

        assertNull(program())
        assertEquals(WatchNextArtwork(ArtworkLocator.Remote("p"), WatchNextArtwork.Shape.POSTER), program(ArtworkKind.POSTER to "p"))
        assertEquals(WatchNextArtwork(ArtworkLocator.Remote("b"), WatchNextArtwork.Shape.WIDE), program(ArtworkKind.POSTER to "p", ArtworkKind.BACKDROP to "b"))
        assertTrue(entry.item is Movie)
    }

    @Test
    fun anEpisodeCardUsesItsStillThenItsShowsBackdrop() = runTest {
        scanned("TV/Breaking Bad/Season 1/Breaking.Bad.S01E01.Pilot.mkv")
        library.stopped(episodes[0], 20 * MINUTE, 47 * MINUTE)
        val continuation = library.continuations().single()
        assertTrue(continuation.entry.item is Episode)
        val show = continuation.show!!.copy(artwork = mapOf(ArtworkKind.BACKDROP to ArtworkLocator.Remote("show"), ArtworkKind.POSTER to ArtworkLocator.Remote("poster")))
        val bare = continuation.copy(entry = continuation.entry.copy(artwork = emptyMap()), show = show)
        assertEquals(ArtworkLocator.Remote("show"), WatchNext.program(bare)!!.artwork?.locator)
        val still = bare.copy(entry = bare.entry.copy(artwork = mapOf(ArtworkKind.THUMBNAIL to ArtworkLocator.Remote("still"))))
        assertEquals(ArtworkLocator.Remote("still"), WatchNext.program(still)!!.artwork?.locator)
    }

    @Test
    fun theRowIsTheActiveProfilesAndAProfileWithAPinShowsNothingThere() = runTest {
        scanned("Movies/Heat (1995)/Heat (1995).mkv")
        val main = library.activeProfile()
        library.stopped(heat, 40 * MINUTE, 170 * MINUTE)
        assertEquals(listOf(main), WatchNext.programs(library).map { it.profile }, "each card names the profile it opens in")

        // Another viewer's row has none of it.
        val sam = library.createProfile("Sam")
        library.switchProfile(sam)
        assertTrue(WatchNext.programs(library).isEmpty())
        library.stopped(heat, 10 * MINUTE, 170 * MINUTE)
        assertEquals(listOf(sam), WatchNext.programs(library).map { it.profile })

        // A profile with a PIN keeps its viewing off the launcher, which is outside the lock.
        library.setPin(sam, "1357", MemoryPinKey())
        assertTrue(WatchNext.programs(library).isEmpty())
        library.removePin(sam)
        assertEquals(1, WatchNext.programs(library).size)
        // The same work in two profiles is two different cards: the row is rewritten when the profile changes.
        library.switchProfile(main)
        assertTrue(WatchNext.programs(library).single().fingerprint != WatchNext.programs(library).single().copy(profile = sam).fingerprint)
    }

    private fun assertIsAdd(change: WatchNextChange) = assertTrue(change is WatchNextChange.Add, "expected an addition, was $change")
}
