package dev.reflux.library

import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.Availability
import dev.reflux.core.model.StableIds
import dev.reflux.core.source.FileEnumeratingSource
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.core.source.SourceCapability
import dev.reflux.core.source.SourceDescriptor
import dev.reflux.core.source.SourceFile
import dev.reflux.core.source.SourceLocality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A share whose folders can stop answering one at a time, as a connection lost partway through a scan does. */
private class FlakyShare : FileEnumeratingSource {
    val files = linkedSetOf<String>()
    val unreadable = mutableSetOf<String>()

    override val descriptor = SourceDescriptor(
        StableIds.sourceId("share", "nas"), "share", "NAS", SourceLocality.LOCAL_NETWORK, setOf(SourceCapability.ENUMERATE_FILES),
    )

    override suspend fun availability() = Availability.AVAILABLE
    override suspend fun playbackTarget(path: String) = PlaybackTarget("smb://nas/$path")

    /** What the adapters list: every file outside an unreadable folder, and each unreadable folder as such. */
    override fun files(): Flow<SourceFile> = (
        unreadable.map { SourceFile.folderNotRead(it) } +
            files.filter { path -> unreadable.none { path.startsWith("$it/") } }.map { SourceFile(it, 100, 1) }
        ).asFlow()
}

/**
 * A folder a scan could not list is not a folder whose files were deleted: what Reflux knew below it stays,
 * with its subtitles, artwork, and history, until a scan can look again. Everything else is settled as usual.
 */
class PartialListingTest {
    private var clock = 1_790_000_000_000L
    private val library = Library(testDatabase()) { clock }
    private val share = FlakyShare()

    init {
        library.addSource(share, "smb://nas/media")
        share.files += listOf(
            "Shows/Severance/poster.jpg",
            "Shows/Severance/Season 1/Severance.S01E01.mkv",
            "Shows/Severance/Season 1/Severance.S01E01.en.srt",
            "Shows/Severance/Season 1/Severance.S01E02.mkv",
            "Movies/Heat (1995)/Heat (1995).mkv",
            "Movies/Arrival (2016)/Arrival (2016).mkv",
        )
    }

    private fun show() = library.shows().singleOrNull()
    private fun episodes() = library.showDetail(show()!!.item.id)!!.seasons.flatMap { it.episodes }
    private fun movieTitles() = library.movies().map { it.item.title }.sorted()

    @Test
    fun whatLiesBelowAnUnreadFolderStays() = runTest {
        library.scan(share)
        val first = episodes().first().item.id
        library.stopped(first, 20 * 60_000L, 50 * 60_000L, library.versions(first).single().version)
        assertNotNull(show()!!.artwork[ArtworkKind.POSTER])

        // The next scan cannot list the shows folder (the connection dropped there); meanwhile a film was deleted.
        share.unreadable += "Shows"
        share.files -= "Movies/Arrival (2016)/Arrival (2016).mkv"
        clock += 60_000
        library.scan(share)

        assertEquals(2, episodes().size, "the show was not seen, which says nothing about it")
        val episode = library.versions(first).single().version
        assertEquals(listOf("Shows/Severance/Season 1/Severance.S01E01.en.srt"), episode.externalSubtitles.map { it.location.path })
        assertNotNull(show()!!.artwork[ArtworkKind.POSTER], "nor about its artwork")
        assertEquals(20 * 60_000L, library.watchState(first)?.positionMs)
        assertEquals(listOf("Heat"), movieTitles(), "what was seen to be gone is gone")

        // Readable again, with an episode deleted meanwhile: now that is seen, and settled.
        share.unreadable.clear()
        share.files -= "Shows/Severance/Season 1/Severance.S01E02.mkv"
        clock += 60_000
        library.scan(share)
        assertEquals(listOf(first), episodes().map { it.item.id })
        assertEquals(20 * 60_000L, library.watchState(first)?.positionMs)
    }

    @Test
    fun aFolderDeletedAltogetherIsStillRemoved() = runTest {
        library.scan(share)
        // Not unreadable: gone from its parent's listing.
        share.files.removeAll { it.startsWith("Shows/") }
        clock += 60_000
        library.scan(share)
        assertNull(show())
        assertEquals(listOf("Arrival", "Heat"), movieTitles())
        assertTrue(library.movies().all { library.versions(it.item.id).isNotEmpty() })
    }
}
